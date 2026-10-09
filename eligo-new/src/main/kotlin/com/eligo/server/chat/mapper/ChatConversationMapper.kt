package com.eligo.server.chat.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.chat.entity.ChatConversationEntity
import org.apache.ibatis.annotations.Mapper

@Mapper
interface ChatConversationMapper : BaseMapper<ChatConversationEntity>
