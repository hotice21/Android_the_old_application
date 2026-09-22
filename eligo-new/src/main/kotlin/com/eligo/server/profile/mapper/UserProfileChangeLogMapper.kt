package com.eligo.server.profile.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.profile.entity.UserProfileChangeLogEntity
import org.apache.ibatis.annotations.Mapper

@Mapper
interface UserProfileChangeLogMapper : BaseMapper<UserProfileChangeLogEntity>
