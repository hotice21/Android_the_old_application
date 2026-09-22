package com.eligo.server.recommendation.mapper

import java.time.LocalDateTime
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update

@Mapper
interface RecommendationIndexJobMapper {

    companion object {
        const val ACTION_UPSERT = 1
        const val ACTION_DELETE = 2
        const val STATUS_PENDING = 1
        const val STATUS_PROCESSING = 2
        const val STATUS_SUCCEEDED = 3
        const val STATUS_FAILED = 4
    }

    @Insert("""
        INSERT INTO post_recommendation_index_jobs(
            post_id,generation,desired_action,content_fingerprint,task_status,
            attempt_count,next_attempt_at,processing_started_at,indexed_model_version,
            last_error_code,last_error_summary,created_at,updated_at)
        VALUES(
            #{postId},1,#{action},NULL,1,0,#{now},NULL,NULL,NULL,NULL,#{now},#{now})
        ON DUPLICATE KEY UPDATE
            generation=post_recommendation_index_jobs.generation+1,
            desired_action=VALUES(desired_action),
            content_fingerprint=NULL,
            attempt_count=0,
            next_attempt_at=VALUES(next_attempt_at),
            processing_started_at=IF(
                post_recommendation_index_jobs.task_status=2,
                post_recommendation_index_jobs.processing_started_at,
                NULL),
            indexed_model_version=NULL,
            last_error_code=NULL,
            last_error_summary=NULL,
            updated_at=VALUES(updated_at),
            task_status=IF(post_recommendation_index_jobs.task_status=2,2,1)
        """)
    fun enqueue(
        @Param("postId") postId: Long,
        @Param("action") action: Int,
        @Param("now") now: LocalDateTime
    ): Int

    @Update("""
        UPDATE post_recommendation_index_jobs
           SET task_status=1,
               processing_started_at=NULL,
               updated_at=#{now}
         WHERE task_status=2
           AND processing_started_at < #{leaseCutoff}
        """)
    fun recoverExpiredProcessing(
        @Param("leaseCutoff") leaseCutoff: LocalDateTime,
        @Param("now") now: LocalDateTime
    ): Int

    @Select("""
        SELECT post_id,generation,desired_action,content_fingerprint,task_status,
               attempt_count,next_attempt_at,processing_started_at,indexed_model_version,
               last_error_code,last_error_summary,created_at,updated_at
          FROM post_recommendation_index_jobs
         WHERE task_status=1
           AND next_attempt_at <= #{now}
         ORDER BY next_attempt_at,post_id
         LIMIT #{limit}
         FOR UPDATE SKIP LOCKED
        """)
    fun findDueForUpdate(
        @Param("now") now: LocalDateTime,
        @Param("limit") limit: Int
    ): List<RecommendationIndexJobEntity>

    @Update("""
        UPDATE post_recommendation_index_jobs
           SET task_status=2,
               attempt_count=attempt_count+1,
               processing_started_at=#{now},
               updated_at=#{now}
         WHERE post_id=#{postId}
           AND generation=#{generation}
           AND task_status=1
        """)
    fun markProcessing(
        @Param("postId") postId: Long,
        @Param("generation") generation: Long,
        @Param("now") now: LocalDateTime
    ): Int

    @Update("""
        UPDATE post_recommendation_index_jobs
           SET task_status=3,
               content_fingerprint=#{fingerprint},
               indexed_model_version=#{indexIdentity},
               processing_started_at=NULL,
               last_error_code=NULL,
               last_error_summary=NULL,
               updated_at=#{now}
         WHERE post_id=#{postId}
           AND generation=#{generation}
           AND task_status=2
        """)
    fun markSucceeded(
        @Param("postId") postId: Long,
        @Param("generation") generation: Long,
        @Param("fingerprint") fingerprint: String?,
        @Param("indexIdentity") indexIdentity: String,
        @Param("now") now: LocalDateTime
    ): Int

    @Update("""
        UPDATE post_recommendation_index_jobs
           SET task_status=#{status},
               next_attempt_at=#{nextAttemptAt},
               processing_started_at=NULL,
               last_error_code=#{errorCode},
               last_error_summary=#{errorSummary},
               updated_at=#{now}
         WHERE post_id=#{postId}
           AND generation=#{generation}
           AND task_status=2
        """)
    fun markFailedOrRetry(
        @Param("postId") postId: Long,
        @Param("generation") generation: Long,
        @Param("status") status: Int,
        @Param("nextAttemptAt") nextAttemptAt: LocalDateTime,
        @Param("errorCode") errorCode: String,
        @Param("errorSummary") errorSummary: String,
        @Param("now") now: LocalDateTime
    ): Int

    @Update("""
        UPDATE post_recommendation_index_jobs
           SET content_fingerprint=NULL,
               task_status=1,
               attempt_count=0,
               next_attempt_at=#{now},
               processing_started_at=NULL,
               indexed_model_version=NULL,
               last_error_code=NULL,
               last_error_summary=NULL,
               updated_at=#{now}
         WHERE post_id=#{postId}
           AND generation > #{staleGeneration}
           AND task_status IN(2,3,4)
        """)
    fun requeueCurrentDesired(
        @Param("postId") postId: Long,
        @Param("staleGeneration") staleGeneration: Long,
        @Param("now") now: LocalDateTime
    ): Int

    @Select("""
        SELECT p.id AS post_id,
               CASE WHEN j.task_status=4 THEN j.desired_action ELSE 1 END
                   AS desired_action
          FROM posts p
          LEFT JOIN post_recommendation_index_jobs j
            ON j.post_id=p.id
         WHERE (
                j.task_status=4
                AND j.updated_at <= #{failedBefore}
           )
            OR (
                p.status=2
                AND p.visibility=1
                AND (
                    j.post_id IS NULL
                    OR (
                        j.task_status=3
                        AND (
                            j.indexed_model_version IS NULL
                            OR j.indexed_model_version <> #{indexIdentity}
                        )
                    )
                )
           )
         ORDER BY (j.task_status=4) DESC,
                  COALESCE(p.published_at,p.updated_at) DESC,
                  p.id DESC
         LIMIT #{limit}
        """)
    fun findBackfillCandidates(
        @Param("indexIdentity") indexIdentity: String,
        @Param("failedBefore") failedBefore: LocalDateTime,
        @Param("limit") limit: Int
    ): List<RecommendationBackfillCandidate>

    @Update("""
        UPDATE post_recommendation_index_jobs j
        JOIN posts p ON p.id=j.post_id
           SET j.generation=j.generation+1,
               j.desired_action=1,
               j.content_fingerprint=NULL,
               j.attempt_count=0,
               j.next_attempt_at=UTC_TIMESTAMP(3),
               j.processing_started_at=IF(
                   j.task_status=2,j.processing_started_at,NULL),
               j.indexed_model_version=NULL,
               j.last_error_code=NULL,
               j.last_error_summary=NULL,
               j.updated_at=UTC_TIMESTAMP(3),
               j.task_status=IF(j.task_status=2,2,1)
         WHERE j.task_status IN(2,3)
           AND p.status=2
           AND p.visibility=1
        """)
    fun requeueCurrentPublicForCollectionRecovery(): Int
}
