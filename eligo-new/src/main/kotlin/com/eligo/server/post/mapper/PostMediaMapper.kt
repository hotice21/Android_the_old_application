package com.eligo.server.post.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.post.entity.PostMediaEntity
import java.util.Optional
import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

@Mapper
interface PostMediaMapper : BaseMapper<PostMediaEntity> {

    @Select("SELECT * FROM post_media WHERE post_id=#{postId} ORDER BY sort_order,id")
    fun findByPostId(@Param("postId") postId: Long): List<PostMediaEntity>

    @Select("SELECT post_id FROM post_media WHERE file_id=#{fileId}")
    fun findPostIdByFileId(@Param("fileId") fileId: Long): Optional<Long>

    @Delete("DELETE FROM post_media WHERE post_id=#{postId}")
    fun deleteByPostId(@Param("postId") postId: Long): Int
}
