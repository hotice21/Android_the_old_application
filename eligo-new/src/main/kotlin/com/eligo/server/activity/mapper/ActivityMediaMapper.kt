package com.eligo.server.activity.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.activity.entity.ActivityMediaEntity
import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

@Mapper
interface ActivityMediaMapper : BaseMapper<ActivityMediaEntity> {

    @Select("SELECT * FROM activity_media WHERE activity_id=#{activityId} ORDER BY sort_order,id")
    fun findByActivityId(@Param("activityId") activityId: Long): List<ActivityMediaEntity>

    @Delete("DELETE FROM activity_media WHERE activity_id=#{activityId}")
    fun deleteByActivityId(@Param("activityId") activityId: Long): Int
}
