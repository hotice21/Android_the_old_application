package com.eligo.server.account.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.account.entity.UserDataRequestEntity
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update
import java.time.LocalDateTime
import java.util.Optional

@Mapper
interface UserDataRequestMapper : BaseMapper<UserDataRequestEntity> {
    @Select("""
        SELECT * FROM user_data_requests
        WHERE user_id=#{userId} AND request_type=#{requestType}
          AND status IN (1,2,3,6)
        LIMIT 1
        """)
    fun findActiveByUserIdAndType(
        @Param("userId") userId: Long,
        @Param("requestType") requestType: Int
    ): Optional<UserDataRequestEntity>

    @Select("""
        SELECT * FROM user_data_requests
        WHERE user_id=#{userId} AND request_type=#{requestType}
          AND status IN (1,2,3,6)
        LIMIT 1 FOR UPDATE
        """)
    fun lockActiveByUserIdAndType(
        @Param("userId") userId: Long,
        @Param("requestType") requestType: Int
    ): Optional<UserDataRequestEntity>

    @Select("SELECT * FROM user_data_requests WHERE id=#{requestId} LIMIT 1 FOR UPDATE")
    fun lockById(@Param("requestId") requestId: Long): Optional<UserDataRequestEntity>

    @Select("""
        SELECT * FROM user_data_requests
        WHERE id=#{requestId} AND user_id=#{userId} AND request_type=#{requestType}
        LIMIT 1
        """)
    fun findOwnedByIdAndType(
        @Param("requestId") requestId: Long,
        @Param("userId") userId: Long,
        @Param("requestType") requestType: Int
    ): Optional<UserDataRequestEntity>

    @Select("""
        SELECT id FROM user_data_requests
        WHERE request_type=2 AND status IN (1,6) AND execute_after<=#{now}
        ORDER BY execute_after,id LIMIT #{limit}
        """)
    fun findDueExportIds(
        @Param("now") now: LocalDateTime?,
        @Param("limit") limit: Int
    ): List<Long>

    @Select("""
        SELECT id FROM user_data_requests
        WHERE request_type=1 AND status IN (2,6) AND execute_after<=#{now}
        ORDER BY execute_after,id LIMIT #{limit}
        """)
    fun findDueDeactivationIds(
        @Param("now") now: LocalDateTime?,
        @Param("limit") limit: Int
    ): List<Long>

    @Update("""
        UPDATE user_data_requests
        SET status=5,processed_at=#{now},version=version+1,updated_at=#{now}
        WHERE id=#{requestId} AND status=2 AND version=#{version}
        """)
    fun markCancelled(
        @Param("requestId") requestId: Long,
        @Param("version") version: Int,
        @Param("now") now: LocalDateTime?
    ): Int

    @Update("""
        UPDATE user_data_requests
        SET status=3,failure_code=NULL,last_error_summary=NULL,
            version=version+1,updated_at=#{now}
        WHERE id=#{requestId} AND status=#{fromStatus}
          AND execute_after<=#{now} AND version=#{version}
        """)
    fun markProcessing(
        @Param("requestId") requestId: Long,
        @Param("fromStatus") fromStatus: Int,
        @Param("version") version: Int,
        @Param("now") now: LocalDateTime?
    ): Int

    @Update("""
        UPDATE user_data_requests
        SET status=6,retry_count=retry_count+1,failure_code=#{failureCode},
            last_error_summary=#{errorSummary},processed_at=#{now},
            version=version+1,updated_at=#{now}
        WHERE id=#{requestId} AND status=#{fromStatus} AND version=#{version}
        """)
    fun markFailed(
        @Param("requestId") requestId: Long,
        @Param("fromStatus") fromStatus: Int,
        @Param("version") version: Int,
        @Param("failureCode") failureCode: String?,
        @Param("errorSummary") errorSummary: String?,
        @Param("now") now: LocalDateTime?
    ): Int

    @Update("""
        UPDATE user_data_requests
        SET status=4,processed_at=#{now},version=version+1,updated_at=#{now}
        WHERE id=#{requestId} AND status=3 AND version=#{version}
        """)
    fun markCompleted(
        @Param("requestId") requestId: Long,
        @Param("version") version: Int,
        @Param("now") now: LocalDateTime?
    ): Int

    @Update("""
        UPDATE user_data_requests
        SET status=4,result_file_id=#{fileId},result_expires_at=#{expiresAt},
            processed_at=#{now},version=version+1,updated_at=#{now}
        WHERE id=#{requestId} AND status=3 AND version=#{version}
        """)
    fun markExportCompleted(
        @Param("requestId") requestId: Long,
        @Param("version") version: Int,
        @Param("fileId") fileId: Long,
        @Param("expiresAt") expiresAt: LocalDateTime?,
        @Param("now") now: LocalDateTime?
    ): Int
}
