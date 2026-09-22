package com.eligo.server.profile.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.profile.entity.InterestTagEntity
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

@Mapper
interface InterestTagMapper : BaseMapper<InterestTagEntity> {

    @Select("SELECT * FROM interest_tags WHERE status=1 ORDER BY sort_order,id")
    fun findAllEnabled(): List<InterestTagEntity>

    @Select(
        "<script>SELECT * FROM interest_tags WHERE status=1 AND id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}</foreach> ORDER BY sort_order,id</script>"
    )
    fun findEnabledByIds(@Param("ids") ids: List<Long>): List<InterestTagEntity>

    @Select(
        "SELECT t.* FROM interest_tags t JOIN user_interest_tags uit " +
            "ON uit.interest_tag_id=t.id WHERE uit.user_id=#{userId} " +
            "ORDER BY t.sort_order,t.id"
    )
    fun findSelectedByUserId(userId: Long): List<InterestTagEntity>
}
