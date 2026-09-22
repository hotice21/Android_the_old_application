package com.eligo.server.recommendation.service

import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.follow.mapper.OrganizationFollowMapper
import com.eligo.server.follow.mapper.UserFollowMapper
import com.eligo.server.post.service.PostReadService
import com.eligo.server.post.vo.PublicPostView
import com.eligo.server.profile.entity.InterestTagEntity
import com.eligo.server.profile.entity.UserProfileEntity
import com.eligo.server.profile.mapper.InterestTagMapper
import com.eligo.server.profile.mapper.UserProfileMapper
import com.eligo.server.recommendation.RecommendationCursorCodec
import com.eligo.server.recommendation.RecommendationDependencyException
import com.eligo.server.recommendation.RecommendationProperties
import com.eligo.server.recommendation.RecommendationRanker
import com.eligo.server.recommendation.RecommendationTelemetry
import com.eligo.server.recommendation.RecommendationTextBuilder
import com.eligo.server.recommendation.client.EmbeddingClient
import com.eligo.server.recommendation.client.VectorStoreClient
import com.eligo.server.recommendation.mapper.RecommendationCandidateRow
import com.eligo.server.recommendation.mapper.RecommendationIndexedGeneration
import com.eligo.server.recommendation.mapper.RecommendationQueryMapper
import com.eligo.server.security.UserPrincipal
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

@Service
@Profile("!test")
class DefaultRecommendedFeedService(
    private val queries: RecommendationQueryMapper,
    private val interestTags: InterestTagMapper,
    private val profiles: UserProfileMapper,
    private val userFollows: UserFollowMapper,
    private val organizationFollows: OrganizationFollowMapper,
    private val reads: PostReadService,
    private val embeddings: EmbeddingClient,
    private val vectors: VectorStoreClient,
    private val redis: StringRedisTemplate,
    private val properties: RecommendationProperties,
    private val cursors: RecommendationCursorCodec,
    private val telemetry: RecommendationTelemetry,
    private val clock: Clock = Clock.systemUTC()
) : RecommendedFeedService {

    private val textBuilder = RecommendationTextBuilder()
    private val ranker = RecommendationRanker()

    override fun list(
        principal: UserPrincipal?,
        cursor: String?,
        limit: Int
    ): CursorPage<PublicPostView> {
        requireLimit(limit)
        val viewerKey = viewerKey(principal)
        if (cursor != null) {
            val decoded = decode(cursor, viewerKey, limit)
            if (decoded is RecommendationCursorCodec.SnapshotCursor) {
                return pageFromSnapshot(principal, decoded)
            }
            val time = decoded as RecommendationCursorCodec.TimeCursor
            return fallbackTimePage(principal, time)
        }

        val snapshotAt = clock.instant()
        val snapshot = buildSnapshot(principal, snapshotAt)
        return try {
            val snapshotId = UUID.randomUUID().toString().replace("-", "")
            saveSnapshot(viewerKey, snapshotId, snapshot)
            pageFromSnapshot(
                principal,
                RecommendationCursorCodec.SnapshotCursor(
                    viewerKey,
                    snapshot.mode,
                    snapshotId,
                    limit,
                    0),
                snapshot)
        } catch (exception: DataAccessException) {
            telemetry.recordFeed(RecommendationTelemetry.FeedOutcome.REDIS_FALLBACK)
            log.warn("推荐快照写入失败，降级到公开动态时间倒序 errorType={}",
                exception.javaClass.simpleName)
            fallbackTimePage(principal, RecommendationCursorCodec.TimeCursor(
                viewerKey, limit, null, Long.MAX_VALUE))
        }
    }

    private fun buildSnapshot(principal: UserPrincipal?, snapshotAt: Instant): FeedSnapshot {
        if (principal != null && properties.enabled) {
            try {
                val profile = profiles.findByUserId(principal.userId)
                    .filter { it.completedAt != null }
                    .orElse(null)
                if (profile == null) {
                    telemetry.recordFeed(
                        RecommendationTelemetry.FeedOutcome.RULE_FALLBACK)
                    return FeedSnapshot(
                        "FALLBACK",
                        latestIds(principal.userId, emptyList()),
                        snapshotAt)
                }
                val tags = interestTags.findSelectedByUserId(principal.userId)
                    .filter { it.status == null || it.status == 1 }
                if (tags.isNotEmpty()) {
                    val queryText = textBuilder.userQueryText(tags)
                    val embeddingKey = EMBEDDING_KEY_PREFIX +
                        principal.userId + ":" +
                        textBuilder.interestCacheKey(tags, properties.indexIdentity())
                    var queryVector = loadEmbedding(embeddingKey)
                    if (queryVector == null) {
                        queryVector = embeddings.embed(queryText)
                        saveEmbedding(embeddingKey, queryVector)
                    }
                    val hits = vectors.search(
                        queryVector,
                        snapshotAt.minus(properties.lookbackDays.toLong(), ChronoUnit.DAYS),
                        properties.candidateLimit,
                        principal.userId)
                    val recalledPostIds = hits
                        .map { it.postId }
                        .filter { it > 0 }
                        .distinct()
                    val currentGenerations: Map<Long, Long> = if (recalledPostIds.isEmpty())
                        emptyMap()
                    else
                        queries.findSucceededIndexGenerations(recalledPostIds)
                            .associateBy({ it.postId }, { it.generation })
                    val currentHits = hits
                        .filter { currentGenerations[it.postId] != null }
                        .filter { currentGenerations[it.postId] == it.generation }
                    val hitIds = currentHits
                        .map { it.postId }
                        .distinct()
                    if (hitIds.isNotEmpty()) {
                        val viewerId = principal.userId
                        val candidates =
                            queries.findCurrentPublicByIds(hitIds, viewerId)
                        val scores: Map<Long, Double> = currentHits.associateBy(
                            { it.postId },
                            { it.score })
                        val candidateUserIds = candidates
                            .map { it.authorUserId }
                            .filterNotNull()
                        val candidateOrganizationIds = candidates
                            .map { it.authorOrganizationId }
                            .filterNotNull()
                        val followedUsers: Set<Long> = if (candidateUserIds.isEmpty())
                            emptySet()
                        else
                            LinkedHashSet(userFollows.findFollowedUserIds(
                                viewerId, candidateUserIds))
                        val followedOrganizations: Set<Long> = if (candidateOrganizationIds.isEmpty())
                            emptySet()
                        else
                            LinkedHashSet(organizationFollows.findFollowedOrganizationIds(
                                viewerId, candidateOrganizationIds))
                        val cityCode = profile.cityCode
                        val rankCandidates = candidates.map { candidate ->
                            RecommendationRanker.Candidate(
                                candidate.postId!!,
                                candidate.authorUserId,
                                candidate.authorOrganizationId,
                                instant(candidate.publishedAt),
                                candidate.activityRegionCode,
                                scores.getOrDefault(candidate.postId, -1.0))
                        }
                        val rankedIds = ArrayList(ranker.rank(
                            rankCandidates,
                            snapshotAt,
                            cityCode,
                            followedUsers,
                            followedOrganizations)
                            .map { it.postId })
                        telemetry.recordFeed(RecommendationTelemetry.FeedOutcome.VECTOR)
                        return FeedSnapshot(
                            "VECTOR",
                            appendLatestFill(principal.userId, rankedIds),
                            snapshotAt)
                    }
                }
            } catch (exception: RecommendationDependencyException) {
                telemetry.recordFeed(
                    RecommendationTelemetry.FeedOutcome.DEPENDENCY_FALLBACK)
                log.warn("推荐依赖不可用，降级到公开动态时间倒序 errorType={}",
                    exception.javaClass.simpleName)
                return FeedSnapshot(
                    "FALLBACK",
                    latestIds(principal.userId, emptyList()),
                    snapshotAt)
            }
        }
        telemetry.recordFeed(RecommendationTelemetry.FeedOutcome.RULE_FALLBACK)
        return FeedSnapshot(
            "FALLBACK",
            latestIds(principal?.userId, emptyList()),
            snapshotAt)
    }

    private fun appendLatestFill(viewerId: Long, rankedIds: List<Long>): List<Long> {
        if (rankedIds.size >= properties.candidateLimit) {
            return rankedIds.subList(0, properties.candidateLimit).toList()
        }
        val latest = queries.findLatestPublicPage(
            viewerId,
            null,
            null,
            rankedIds,
            properties.candidateLimit - rankedIds.size)
        val result = ArrayList(rankedIds)
        for (row in latest) {
            if (!result.contains(row.postId)) {
                result.add(row.postId!!)
            }
        }
        return result.toList()
    }

    private fun latestIds(viewerId: Long?, excluded: List<Long>): List<Long> {
        return queries.findLatestPublicPage(
            viewerId,
            null,
            null,
            excluded,
            properties.candidateLimit)
            .map { it.postId!! }
    }

    private fun pageFromSnapshot(
        principal: UserPrincipal?,
        cursor: RecommendationCursorCodec.SnapshotCursor
    ): CursorPage<PublicPostView> {
        return pageFromSnapshot(
            principal,
            cursor,
            loadSnapshot(cursor.viewerKey, cursor.snapshotId))
    }

    private fun pageFromSnapshot(
        principal: UserPrincipal?,
        cursor: RecommendationCursorCodec.SnapshotCursor,
        snapshot: FeedSnapshot
    ): CursorPage<PublicPostView> {
        val ids = snapshot.postIds
        if (cursor.offset >= ids.size) {
            return CursorPage(emptyList(), null, false)
        }
        val remainingIds = ids.subList(cursor.offset, ids.size)
        val viewerId = principal?.userId
        val current: Map<Long, RecommendationCandidateRow> = queries
            .findCurrentPublicByIds(remainingIds, viewerId)
            .associateBy { it.postId!! }
        val items = ArrayList<PublicPostView>()
        var scanned = 0
        var hasMore = false
        while (scanned < remainingIds.size) {
            val row = current[remainingIds[scanned]]
            scanned++
            if (row == null) {
                continue
            }
            val view: PublicPostView = try {
                reads.getPost(principal, row.postId!!)
            } catch (exception: BusinessException) {
                if (exception.errorCode == CommonErrorCode.RESOURCE_NOT_FOUND) {
                    continue
                }
                throw exception
            }
            items.add(view)
            if (items.size == cursor.limit) {
                hasMore = hasReadableItem(remainingIds, scanned, current, principal)
                break
            }
        }
        val nextOffset = cursor.offset + scanned
        val nextCursor = if (hasMore)
            cursors.encodeSnapshot(
                cursor.viewerKey,
                cursor.mode,
                cursor.snapshotId,
                cursor.limit,
                nextOffset)
        else
            null
        return CursorPage(items, nextCursor, hasMore)
    }

    private fun hasReadableItem(
        ids: List<Long>,
        offset: Int,
        current: Map<Long, RecommendationCandidateRow>,
        principal: UserPrincipal?
    ): Boolean {
        for (i in offset until ids.size) {
            val row = current[ids[i]] ?: continue
            try {
                reads.getPost(principal, row.postId!!)
                return true
            } catch (exception: BusinessException) {
                if (exception.errorCode != CommonErrorCode.RESOURCE_NOT_FOUND) {
                    throw exception
                }
            }
        }
        return false
    }

    private fun fallbackTimePage(
        principal: UserPrincipal?,
        cursor: RecommendationCursorCodec.TimeCursor
    ): CursorPage<PublicPostView> {
        val viewerId = principal?.userId
        var boundaryTime: LocalDateTime? = if (cursor.publishedAt == null)
            null
        else
            LocalDateTime.ofInstant(cursor.publishedAt!!, ZoneOffset.UTC)
        var boundaryId: Long? = if (cursor.publishedAt == null) null else cursor.postId
        val batchSize = cursor.limit + 1
        val scanLimit = maxOf(properties.candidateLimit, batchSize)
        var scanned = 0
        val readable = ArrayList<ReadablePost>()
        while (readable.size < batchSize && scanned < scanLimit) {
            val requested = minOf(batchSize, scanLimit - scanned)
            val rows = queries.findLatestPublicPage(
                viewerId,
                boundaryTime,
                boundaryId,
                emptyList(),
                requested)
            if (rows.isEmpty()) {
                break
            }
            for (row in rows) {
                scanned++
                boundaryTime = row.publishedAt
                boundaryId = row.postId
                try {
                    readable.add(ReadablePost(
                        row,
                        reads.getPost(principal, row.postId!!)))
                } catch (exception: BusinessException) {
                    if (exception.errorCode != CommonErrorCode.RESOURCE_NOT_FOUND) {
                        throw exception
                    }
                }
                if (readable.size == batchSize || scanned == scanLimit) {
                    break
                }
            }
            if (rows.size < requested) {
                break
            }
        }
        val hasMore = readable.size > cursor.limit
        val pageRows = readable.subList(
            0, minOf(cursor.limit, readable.size))
        val items = pageRows.map { it.view }
        val nextCursor = if (hasMore && pageRows.isNotEmpty())
            cursors.encodeTime(
                cursor.viewerKey,
                cursor.limit,
                instant(pageRows[pageRows.size - 1].row.publishedAt)!!,
                pageRows[pageRows.size - 1].row.postId!!)
        else
            null
        return CursorPage(items, nextCursor, hasMore)
    }

    private fun saveSnapshot(
        viewerKey: String,
        snapshotId: String,
        snapshot: FeedSnapshot
    ) {
        val value = snapshot.snapshotAt.toEpochMilli().toString() + "|" +
            snapshot.postIds.joinToString(",")
        redis.opsForValue().set(
            snapshotKey(viewerKey, snapshotId),
            value,
            properties.snapshotTtl)
    }

    private fun loadEmbedding(key: String): FloatArray? {
        val value: String? = try {
            redis.opsForValue().get(key)
        } catch (exception: DataAccessException) {
            return null
        }
        if (value == null || value.isBlank()) {
            return null
        }
        return try {
            val parts = value.split(",")
            if (parts.size != properties.vectorSize) {
                return null
            }
            val vector = FloatArray(parts.size)
            for (i in parts.indices) {
                vector[i] = parts[i].toFloat()
                if (!vector[i].isFinite()) {
                    return null
                }
            }
            vector
        } catch (exception: RuntimeException) {
            null
        }
    }

    private fun saveEmbedding(key: String, vector: FloatArray) {
        val value = toDoubleArray(vector).joinToString(",") { it.toString() }
        try {
            redis.opsForValue().set(key, value, properties.snapshotTtl)
        } catch (exception: DataAccessException) {
        }
    }

    private fun toDoubleArray(vector: FloatArray): DoubleArray {
        val result = DoubleArray(vector.size)
        for (i in vector.indices) {
            result[i] = vector[i].toDouble()
        }
        return result
    }

    private fun loadSnapshot(viewerKey: String, snapshotId: String): FeedSnapshot {
        val value: String = try {
            redis.opsForValue().get(snapshotKey(viewerKey, snapshotId))
        } catch (exception: DataAccessException) {
            throw validation()
        } ?: throw validation()
        return try {
            val parts = value.split("|", limit = 2)
            if (parts.size != 2) {
                throw IllegalArgumentException()
            }
            val snapshotAt = Instant.ofEpochMilli(parts[0].toLong())
            val ids = if (parts[1].isBlank())
                emptyList()
            else
                parts[1].split(",").map { it.toLong() }.filter { it > 0 }
            FeedSnapshot("SNAPSHOT", ids, snapshotAt)
        } catch (exception: RuntimeException) {
            throw validation()
        }
    }

    private fun snapshotKey(viewerKey: String, snapshotId: String): String {
        return SNAPSHOT_KEY_PREFIX + viewerKey + ":" + snapshotId
    }

    private fun decode(
        cursor: String,
        viewerKey: String,
        limit: Int
    ): RecommendationCursorCodec.Cursor {
        return try {
            cursors.decode(cursor, viewerKey, limit)
        } catch (exception: IllegalArgumentException) {
            throw validation()
        }
    }

    private fun viewerKey(principal: UserPrincipal?): String {
        return principal?.let { it.userId.toString() } ?: ANONYMOUS_VIEWER
    }

    private fun instant(value: LocalDateTime?): Instant? {
        return value?.toInstant(ZoneOffset.UTC)
    }

    private fun requireLimit(limit: Int) {
        if (limit < 1 || limit > 20) {
            throw validation()
        }
    }

    private fun validation(): BusinessException {
        return BusinessException(CommonErrorCode.VALIDATION_FAILED)
    }

    private class FeedSnapshot(
        mode: String,
        postIds: List<Long>,
        snapshotAt: Instant
    ) {
        val mode: String = mode
        val postIds: List<Long> = postIds.toList()
        val snapshotAt: Instant = snapshotAt
    }

    private data class ReadablePost(
        val row: RecommendationCandidateRow,
        val view: PublicPostView
    )

    companion object {
        private val log = LoggerFactory.getLogger(DefaultRecommendedFeedService::class.java)
        private const val SNAPSHOT_KEY_PREFIX = "eligo:recommendation:feed:v1:"
        private const val EMBEDDING_KEY_PREFIX = "eligo:recommendation:embedding:v1:"
        private const val ANONYMOUS_VIEWER = "anonymous"
    }
}
