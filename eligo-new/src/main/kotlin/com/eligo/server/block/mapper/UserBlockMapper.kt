package com.eligo.server.block.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.block.entity.UserBlockEntity
import org.apache.ibatis.annotations.Mapper

@Mapper
interface UserBlockMapper : BaseMapper<UserBlockEntity>
