package com.eligo.server.location.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.math.BigDecimal
import java.time.LocalDateTime

@TableName("user_locations")
class UserLocationEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var userId: Long? = null
    var provinceName: String? = null
    var cityName: String? = null
    var districtName: String? = null
    var streetName: String? = null
    var latitude: BigDecimal? = null
    var longitude: BigDecimal? = null
    var locationSource: Int? = null
    var updatedAt: LocalDateTime? = null
    var createdAt: LocalDateTime? = null
}
