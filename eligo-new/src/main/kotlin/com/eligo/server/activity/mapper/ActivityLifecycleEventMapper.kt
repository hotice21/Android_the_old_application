package com.eligo.server.activity.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.activity.entity.ActivityLifecycleEventEntity
import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface ActivityLifecycleEventMapper : BaseMapper<ActivityLifecycleEventEntity> {

    @Delete("DELETE FROM activity_lifecycle_events WHERE activity_id=#{activityId}")
    fun deleteByActivityId(@Param("activityId") activityId: Long): Int
}
