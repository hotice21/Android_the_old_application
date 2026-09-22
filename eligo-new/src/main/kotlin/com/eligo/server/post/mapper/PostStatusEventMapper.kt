package com.eligo.server.post.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.post.entity.PostStatusEventEntity
import org.apache.ibatis.annotations.Mapper

@Mapper
interface PostStatusEventMapper : BaseMapper<PostStatusEventEntity>
