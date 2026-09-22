package com.eligo.server.file.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.file.entity.FileObjectEntity
import java.time.LocalDateTime
import java.util.Optional
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update

@Mapper
interface FileObjectMapper : BaseMapper<FileObjectEntity> {
    @Select("SELECT * FROM file_objects WHERE id=#{fileId}")
    fun findById(@Param("fileId") fileId: Long): Optional<FileObjectEntity>

    @Select("SELECT * FROM file_objects WHERE id=#{fileId} FOR UPDATE")
    fun lockById(@Param("fileId") fileId: Long): Optional<FileObjectEntity>

    @Update(
        "UPDATE file_objects SET lifecycle_status=2,activated_at=#{now},expires_at=NULL,version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=#{fileId} AND lifecycle_status=1 AND version=#{version}"
    )
    fun activate(
        @Param("fileId") fileId: Long,
        @Param("now") now: LocalDateTime,
        @Param("version") version: Int
    ): Int

    @Update(
        "UPDATE file_objects SET lifecycle_status=3,deleted_at=#{now},version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=#{fileId} AND lifecycle_status=1 AND version=#{version}"
    )
    fun markDeleted(
        @Param("fileId") fileId: Long,
        @Param("now") now: LocalDateTime,
        @Param("version") version: Int
    ): Int

    @Select(
        """
            SELECT * FROM file_objects WHERE uploader_type=1 AND uploader_id=#{userId}
              AND access_level=1 AND scan_status=2 AND lifecycle_status=2
              AND (purpose IS NULL OR purpose<>'POST')
            ORDER BY id ASC LIMIT #{limit}
            """
    )
    fun findExportableByUserId(
        @Param("userId") userId: Long,
        @Param("limit") limit: Int
    ): List<FileObjectEntity>

    @Select(
        """
            SELECT f.*
            FROM file_objects f
            JOIN post_media pm ON pm.file_id=f.id
            JOIN posts p ON p.id=pm.post_id
            WHERE p.author_user_id=#{userId}
              AND f.access_level=1
              AND f.scan_status=2
              AND f.lifecycle_status=2
            ORDER BY f.id ASC
            LIMIT #{limit}
            """
    )
    fun findExportablePersonalPostMedia(
        @Param("userId") userId: Long,
        @Param("limit") limit: Int
    ): List<FileObjectEntity>

    @Select(
        """
            SELECT * FROM file_objects
            WHERE lifecycle_status=1 AND expires_at IS NOT NULL AND expires_at<=#{now}
            ORDER BY expires_at,id LIMIT #{limit}
            """
    )
    fun findExpiredTemporaryFiles(
        @Param("now") now: LocalDateTime,
        @Param("limit") limit: Int
    ): List<FileObjectEntity>

    @Select(
        """
            SELECT EXISTS(
                SELECT 1 FROM activities a
                WHERE a.cover_file_id=#{fileId} AND a.status IN (2, 3, 4)
                UNION ALL
                SELECT 1 FROM activity_media m
                JOIN activities a ON a.id=m.activity_id
                WHERE m.file_id=#{fileId} AND a.status IN (2, 3, 4)
            )
            """
    )
    fun existsPublicActivityReference(@Param("fileId") fileId: Long): Boolean

    @Select(
        """
            SELECT EXISTS(
                SELECT 1 FROM activities a
                WHERE a.organizer_wechat_qr_file_id=#{fileId}
                  AND a.status IN (2, 3, 4)
            )
            """
    )
    fun existsActivityContactQrReference(@Param("fileId") fileId: Long): Boolean

    @Update(
        """
            UPDATE file_objects
            SET lifecycle_status=3,deleted_at=#{now},version=version+1,updated_at=#{now}
            WHERE id=#{fileId} AND lifecycle_status=1 AND expires_at<=#{now}
              AND version=#{version}
            """
    )
    fun markExpiredDeleted(
        @Param("fileId") fileId: Long,
        @Param("version") version: Int,
        @Param("now") now: LocalDateTime
    ): Int

    @Select(
        """
            SELECT f.* FROM file_objects f
            WHERE f.purpose='ACTIVITY'
              AND f.lifecycle_status=2
              AND NOT EXISTS (
                  SELECT 1 FROM activities a WHERE a.cover_file_id=f.id
              )
              AND NOT EXISTS (
                  SELECT 1 FROM activity_media m WHERE m.file_id=f.id
              )
            ORDER BY f.updated_at, f.id
            LIMIT #{limit}
            """
    )
    fun findOrphanedActiveActivityFiles(@Param("limit") limit: Int): List<FileObjectEntity>

    @Update(
        """
            UPDATE file_objects
            SET lifecycle_status=3,deleted_at=#{now},version=version+1,updated_at=#{now}
            WHERE id=#{fileId} AND lifecycle_status=2 AND version=#{version}
              AND NOT EXISTS (
                  SELECT 1 FROM activities a WHERE a.cover_file_id=#{fileId}
              )
              AND NOT EXISTS (
                  SELECT 1 FROM activity_media m WHERE m.file_id=#{fileId}
              )
            """
    )
    fun markOrphanedDeleted(
        @Param("fileId") fileId: Long,
        @Param("version") version: Int,
        @Param("now") now: LocalDateTime
    ): Int

    @Select(
        """
            SELECT f.* FROM file_objects f
            WHERE f.purpose='ACTIVITY_CONTACT_QR'
              AND f.lifecycle_status=2
              AND NOT EXISTS (
                  SELECT 1 FROM activities a
                  WHERE a.organizer_wechat_qr_file_id=f.id
              )
            ORDER BY f.updated_at, f.id
            LIMIT #{limit}
            """
    )
    fun findOrphanedActiveActivityContactQrFiles(@Param("limit") limit: Int): List<FileObjectEntity>

    @Select(
        """
            SELECT f.* FROM file_objects f
            WHERE f.purpose='POST'
              AND f.lifecycle_status=2
              AND NOT EXISTS (
                  SELECT 1 FROM post_media m WHERE m.file_id=f.id
              )
            ORDER BY f.updated_at, f.id
            LIMIT #{limit}
            """
    )
    fun findOrphanedActivePostFiles(@Param("limit") limit: Int): List<FileObjectEntity>

    @Update(
        """
            UPDATE file_objects
            SET lifecycle_status=3,deleted_at=#{now},version=version+1,updated_at=#{now}
            WHERE id=#{fileId} AND lifecycle_status=2 AND version=#{version}
              AND NOT EXISTS (
                  SELECT 1 FROM activities a
                  WHERE a.organizer_wechat_qr_file_id=#{fileId}
              )
            """
    )
    fun markOrphanedActivityContactQrDeleted(
        @Param("fileId") fileId: Long,
        @Param("version") version: Int,
        @Param("now") now: LocalDateTime
    ): Int

    @Update(
        """
            UPDATE file_objects
            SET lifecycle_status=3,deleted_at=#{now},version=version+1,updated_at=#{now}
            WHERE id=#{fileId} AND purpose='POST'
              AND lifecycle_status=2 AND version=#{version}
              AND NOT EXISTS (
                  SELECT 1 FROM post_media m WHERE m.file_id=#{fileId}
              )
            """
    )
    fun markOrphanedPostDeleted(
        @Param("fileId") fileId: Long,
        @Param("version") version: Int,
        @Param("now") now: LocalDateTime
    ): Int

    @Select("SELECT COUNT(*) FROM file_objects WHERE object_key=#{objectKey} AND lifecycle_status<>3")
    fun countLiveByObjectKey(@Param("objectKey") objectKey: String): Int
}
