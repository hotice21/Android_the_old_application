package com.eligo.server.chat.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.chat.entity.ChatMessageEntity
import org.apache.ibatis.annotations.Mapper

@Mapper
interface ChatMessageMapper : BaseMapper<ChatMessageEntity>
