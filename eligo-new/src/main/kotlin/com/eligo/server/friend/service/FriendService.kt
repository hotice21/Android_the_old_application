package com.eligo.server.friend.service

import com.eligo.server.follow.entity.UserFollowEntity

interface FriendService {
    fun sendFriendRequest(myUserId: Long, targetUserId: Long): UserFollowEntity
    fun acceptFriendRequest(myUserId: Long, requestId: Long): UserFollowEntity
    fun rejectFriendRequest(myUserId: Long, requestId: Long): UserFollowEntity
    fun cancelFriendRequest(myUserId: Long, requestId: Long)
    fun unfriend(myUserId: Long, friendUserId: Long)
    fun getMyFriends(myUserId: Long): List<UserFollowEntity>
    fun getPendingRequests(myUserId: Long): List<UserFollowEntity>
    fun isFriend(myUserId: Long, otherUserId: Long): Boolean
    fun updateRemark(myUserId: Long, friendUserId: Long, remarkName: String?)
}
