package com.eligo.server.contentfilter.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.eligo.server.contentfilter.entity.SensitiveWordEntity
import org.apache.ibatis.annotations.Mapper

@Mapper
interface SensitiveWordMapper : BaseMapper<SensitiveWordEntity>
