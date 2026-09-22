package com.eligo.server.profile.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("user_profiles")
class UserProfileEntity {
    @TableId(value = "user_id", type = IdType.INPUT)
    var userId: Long? = null
    var nickname: String? = null
    var avatarFileId: Long? = null
    var birthDateCiphertext: ByteArray? = null
    var emailCiphertext: ByteArray? = null
    var emailLookupHash: ByteArray? = null
    var genderCode: Int? = null
    var provinceCode: String? = null
    var provinceName: String? = null
    var cityCode: String? = null
    var cityName: String? = null
    var districtCode: String? = null
    var districtName: String? = null
    var bio: String? = null
    var completedAt: LocalDateTime? = null
    var nicknameChangedAt: LocalDateTime? = null
    var createdAt: LocalDateTime? = null
    var updatedAt: LocalDateTime? = null
    var version: Int? = null
}
