package com.eligo.server.post.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.post.entity.PostLikeEntity
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

@Mapper
interface PostLikeMapper : BaseMapper<PostLikeEntity> {

    @Select("SELECT COUNT(*) FROM post_likes WHERE user_id = #{userId} AND post_id = #{postId}")
    fun countByUserAndPost(@Param("userId") userId: Long, @Param("postId") postId: Long): Long
}
