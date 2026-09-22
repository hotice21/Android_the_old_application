package com.eligo.server.organization.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

@TableName("organizations")
class OrganizationEntity {
    @TableId(value = "id", type = IdType.INPUT)
    var id: Long? = null
    var name: String? = null
    var avatarFileId: Long? = null
    var summary: String? = null
    var provinceCode: String? = null
    var provinceName: String? = null
    var cityCode: String? = null
    var cityName: String? = null
    var districtCode: String? = null
    var districtName: String? = null
    var addressDetail: String? = null
    var status: Int? = null
}
