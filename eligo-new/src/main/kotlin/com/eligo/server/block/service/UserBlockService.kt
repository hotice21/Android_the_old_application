package com.eligo.server.block.service

import com.eligo.server.block.entity.UserBlockEntity

interface UserBlockService {
    fun blockUser(myUserId: Long, targetUserId: Long, reason: String?): UserBlockEntity
    fun unblockUser(myUserId: Long, targetUserId: Long)
    fun isBlockedByUser(myUserId: Long, targetUserId: Long): Boolean
    fun isBlockingUser(myUserId: Long, targetUserId: Long): Boolean
    fun getMyBlockedList(myUserId: Long): List<UserBlockEntity>
}
