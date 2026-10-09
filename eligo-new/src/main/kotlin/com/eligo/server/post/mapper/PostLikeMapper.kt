package com.eligo.server.post.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.post.entity.PostLikeEntity
import org.apache.ibatis.annotations.Mapper

@Mapper
interface PostLikeMapper : BaseMapper<PostLikeEntity>
