package com.eligo.server.activity.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.activity.entity.ActivityTopicEntity
import java.util.Optional
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

@Mapper
interface ActivityTopicMapper : BaseMapper<ActivityTopicEntity> {

    @Select("SELECT * FROM activity_topics WHERE normalized_name=#{normalizedName}")
    fun findByNormalizedName(
        @Param("normalizedName") normalizedName: String
    ): Optional<ActivityTopicEntity>

    @Select("""
            SELECT *
              FROM activity_topics
             WHERE normalized_name=#{normalizedName}
             FOR UPDATE
            """)
    fun lockByNormalizedName(
        @Param("normalizedName") normalizedName: String
    ): Optional<ActivityTopicEntity>
}
