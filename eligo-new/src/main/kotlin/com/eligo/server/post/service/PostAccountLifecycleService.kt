package com.eligo.server.post.service

import com.eligo.server.follow.mapper.OrganizationFollowMapper
import com.eligo.server.follow.mapper.UserFollowMapper
import com.eligo.server.post.entity.PostEntity
import com.eligo.server.post.entity.PostStatusEventEntity
import com.eligo.server.post.mapper.PostMapper
import com.eligo.server.post.mapper.PostStatusEventMapper
import com.eligo.server.recommendation.service.RecommendationIndexTaskWriter
import java.time.LocalDateTime
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class PostAccountLifecycleService(
    private val userFollows: UserFollowMapper,
    private val organizationFollows: OrganizationFollowMapper,
    private val posts: PostMapper,
    private val events: PostStatusEventMapper,
    private val recommendationTasks: RecommendationIndexTaskWriter = RecommendationIndexTaskWriter.noop()
) {

    @Transactional(propagation = Propagation.MANDATORY)
    fun applyDeactivation(userId: Long, now: LocalDateTime) {
        userFollows.deleteAllForUser(userId)
        organizationFollows.deleteAllByFollower(userId)
        for (post in posts.lockPersonalPostsForDeactivation(userId)) {
            if (post.status == PostEntity.STATUS_DRAFT) {
                requireChanged(posts.softDeleteById(post.id!!, PostEntity.STATUS_DRAFT, now))
                appendEvent(post.id!!, PostEntity.STATUS_DRAFT, PostEntity.STATUS_DELETED, now)
            } else if (post.status == PostEntity.STATUS_PUBLISHED) {
                requireChanged(posts.hidePublishedById(post.id!!, now))
                appendEvent(post.id!!, PostEntity.STATUS_PUBLISHED, PostEntity.STATUS_HIDDEN, now)
                recommendationTasks.enqueueDelete(post.id!!)
            }
        }
    }

    private fun requireChanged(changed: Int) {
        if (changed != 1) {
            throw IllegalStateException("账号注销时动态状态并发冲突")
        }
    }

    private fun appendEvent(
        postId: Long,
        fromStatus: Int,
        toStatus: Int,
        now: LocalDateTime
    ) {
        val event = PostStatusEventEntity()
        event.postId = postId
        event.fromStatus = fromStatus
        event.toStatus = toStatus
        event.actorType = PostStatusEventEntity.ACTOR_SYSTEM
        event.reasonCode = "ACCOUNT_DEACTIVATED"
        event.createdAt = now
        events.insert(event)
    }
}
