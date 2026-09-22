package com.eligo.server.profile.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.profile.entity.UserInterestTagEntity
import java.time.LocalDateTime
import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Select

@Mapper
interface UserInterestTagMapper : BaseMapper<UserInterestTagEntity> {

    @Select("SELECT * FROM user_interest_tags WHERE user_id=#{userId} ORDER BY selected_at,id")
    fun findAllByUserId(userId: Long): List<UserInterestTagEntity>

    @Delete("DELETE FROM user_interest_tags WHERE user_id=#{userId}")
    fun deleteByUserId(userId: Long): Int

    @Select(
        "SELECT COUNT(*) FROM user_interest_tags uit JOIN interest_tags t " +
            "ON t.id=uit.interest_tag_id WHERE uit.user_id=#{userId} " +
            "AND t.status=1"
    )
    fun countEnabledByUserId(userId: Long): Int

    fun insertSelection(userId: Long, tagId: Long, selectedAt: LocalDateTime?): Int {
        val entity = UserInterestTagEntity()
        entity.userId = userId
        entity.interestTagId = tagId
        entity.selectedAt = selectedAt
        return insert(entity)
    }
}
