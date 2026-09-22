package com.eligo.server.activity.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.activity.entity.ActivityTopicRelationEntity
import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

@Mapper
interface ActivityTopicRelationMapper : BaseMapper<ActivityTopicRelationEntity> {

    @Delete("DELETE FROM activity_topic_relations WHERE activity_id=#{activityId}")
    fun deleteByActivityId(@Param("activityId") activityId: Long): Int

    @Select("""
            SELECT r.activity_id, t.display_name, r.sort_order
            FROM activity_topic_relations r
            JOIN activity_topics t ON t.id=r.topic_id
            WHERE r.activity_id=#{activityId}
            ORDER BY r.sort_order,r.id
            """)
    fun findByActivityId(@Param("activityId") activityId: Long): List<ActivityTopicRow>

    @Select("""
            <script>
            SELECT r.activity_id, t.display_name, r.sort_order
            FROM activity_topic_relations r
            JOIN activity_topics t ON t.id=r.topic_id
            WHERE r.activity_id IN
            <foreach collection="activityIds" item="activityId" open="(" separator="," close=")">
                #{activityId}
            </foreach>
            ORDER BY r.activity_id,r.sort_order,r.id
            </script>
            """)
    fun findByActivityIds(
        @Param("activityIds") activityIds: List<Long>
    ): List<ActivityTopicRow>
}
