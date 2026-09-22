package com.eligo.server.profile.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.profile.entity.UserProfileEntity
import java.time.LocalDateTime
import java.util.Optional
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update

@Mapper
interface UserProfileMapper : BaseMapper<UserProfileEntity> {

    @Select("""
        SELECT EXISTS(
            SELECT 1
            FROM users u
            JOIN user_profiles p ON p.user_id=u.id
            WHERE u.id=#{userId}
              AND u.status=1
              AND p.completed_at IS NOT NULL
        )
        """)
    fun existsActiveCompletedUser(@Param("userId") userId: Long): Boolean

    @Select("SELECT * FROM user_profiles WHERE user_id=#{userId}")
    fun findByUserId(@Param("userId") userId: Long): Optional<UserProfileEntity>

    @Select("SELECT * FROM user_profiles WHERE user_id=#{userId} FOR UPDATE")
    fun lockByUserId(@Param("userId") userId: Long): Optional<UserProfileEntity>

    @Select(
        "SELECT user_id FROM user_profiles " +
            "WHERE email_lookup_hash=#{emailLookupHash} LIMIT 1"
    )
    fun findUserIdByEmailLookupHash(
        @Param("emailLookupHash") emailLookupHash: ByteArray?
    ): Optional<Long>

    @Update(
        "UPDATE user_profiles SET nickname=#{nickname}," +
            "birth_date_ciphertext=#{birth}," +
            "email_ciphertext=#{emailCiphertext}," +
            "email_lookup_hash=#{emailLookupHash},gender_code=#{gender}," +
            "province_code=#{provinceCode},province_name=#{provinceName}," +
            "city_code=#{cityCode},city_name=#{cityName}," +
            "district_code=#{districtCode},district_name=#{districtName}," +
            "bio=#{bio},version=version+1,updated_at=UTC_TIMESTAMP(3) " +
            "WHERE user_id=#{userId} AND version=#{version}"
    )
    fun updateBasicProfile(
        @Param("userId") userId: Long,
        @Param("nickname") nickname: String?,
        @Param("birth") birth: ByteArray?,
        @Param("emailCiphertext") emailCiphertext: ByteArray?,
        @Param("emailLookupHash") emailLookupHash: ByteArray?,
        @Param("gender") gender: Int,
        @Param("provinceCode") provinceCode: String?,
        @Param("provinceName") provinceName: String?,
        @Param("cityCode") cityCode: String?,
        @Param("cityName") cityName: String?,
        @Param("districtCode") districtCode: String?,
        @Param("districtName") districtName: String?,
        @Param("bio") bio: String?,
        @Param("version") version: Int
    ): Int

    @Update(
        "UPDATE user_profiles SET nickname=#{nickname},version=version+1," +
            "updated_at=UTC_TIMESTAMP(3) WHERE user_id=#{userId} " +
            "AND nickname IS NULL AND version=#{version}"
    )
    fun setFirstNickname(
        @Param("userId") userId: Long,
        @Param("nickname") nickname: String?,
        @Param("version") version: Int
    ): Int

    @Update(
        "UPDATE user_profiles SET nickname=#{nickname},nickname_changed_at=#{now}," +
            "version=version+1,updated_at=UTC_TIMESTAMP(3) " +
            "WHERE user_id=#{userId} AND version=#{version} " +
            "AND (nickname_changed_at IS NULL OR nickname_changed_at<=#{cutoff})"
    )
    fun updateNicknameWithLimit(
        @Param("userId") userId: Long,
        @Param("nickname") nickname: String?,
        @Param("now") now: LocalDateTime?,
        @Param("cutoff") cutoff: LocalDateTime?,
        @Param("version") version: Int
    ): Int

    @Update(
        "UPDATE user_profiles SET completed_at=#{completedAt},version=version+1," +
            "updated_at=UTC_TIMESTAMP(3) " +
            "WHERE user_id=#{userId} AND version=#{version}"
    )
    fun updateCompletedAt(
        @Param("userId") userId: Long,
        @Param("completedAt") completedAt: LocalDateTime?,
        @Param("version") version: Int
    ): Int

    @Update("UPDATE user_profiles SET avatar_file_id=#{fileId},version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE user_id=#{userId} AND version=#{version}")
    fun updateAvatarFileId(
        @Param("userId") userId: Long,
        @Param("fileId") fileId: Long,
        @Param("version") version: Int
    ): Int

    @Update("""
        UPDATE user_profiles
        SET nickname=NULL,avatar_file_id=NULL,birth_date_ciphertext=NULL,
            email_ciphertext=NULL,email_lookup_hash=NULL,gender_code=NULL,
            province_code=NULL,province_name=NULL,city_code=NULL,city_name=NULL,
            district_code=NULL,district_name=NULL,bio=NULL,completed_at=NULL,
            nickname_changed_at=NULL,version=version+1,updated_at=#{now}
        WHERE user_id=#{userId}
        """)
    fun anonymizeByUserId(
        @Param("userId") userId: Long,
        @Param("now") now: LocalDateTime?
    ): Int
}
