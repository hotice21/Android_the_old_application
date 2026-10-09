package com.eligo.server.location.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.location.entity.UserLocationEntity
import org.apache.ibatis.annotations.Mapper

@Mapper
interface UserLocationMapper : BaseMapper<UserLocationEntity>
