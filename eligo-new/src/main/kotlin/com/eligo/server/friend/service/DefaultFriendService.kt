package com.eligo.server.friend.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.follow.entity.UserFollowEntity
import com.eligo.server.follow.mapper.UserFollowMapper
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DefaultFriendService(
    private val userFollows: UserFollowMapper
) : FriendService {

    @Transactional
    override fun sendFriendRequest(
        myUserId: Long,
        targetUserId: Long
    ): UserFollowEntity {
        if (myUserId == targetUserId) {
            throw BusinessException(CommonErrorCode.VALIDATION_FAILED)
        }
        // 已存在关系，直接返回
        val existing = userFollows.selectOne(
            QueryWrapper<UserFollowEntity>()
                .eq("follower_user_id", myUserId)
                .eq("followed_user_id", targetUserId)
        )
        if (existing != null) {
            if (existing.status == UserFollowEntity.STATUS_ACCEPTED) {
                return existing
            }
            // 如果已经是 pending，直接返回
            return existing
        }
        // 反向（对方曾请求过我）且我未处理 → 直接接受对方的请求
        val reverse = userFollows.selectOne(
            QueryWrapper<UserFollowEntity>()
                .eq("follower_user_id", targetUserId)
                .eq("followed_user_id", myUserId)
                .eq("status", UserFollowEntity.STATUS_PENDING)
        )
        if (reverse != null) {
            // 直接互相成为好友
            val now = LocalDateTime.now()
            reverse.status = UserFollowEntity.STATUS_ACCEPTED
            reverse.acceptedAt = now
            userFollows.updateById(reverse)
            val reciprocal = UserFollowEntity().apply {
                this.followerUserId = myUserId
                this.followedUserId = targetUserId
                this.status = UserFollowEntity.STATUS_ACCEPTED
                this.followedAt = now
                this.acceptedAt = now
            }
            userFollows.insert(reciprocal)
            return reciprocal
        }
        val now = LocalDateTime.now()
        val entity = UserFollowEntity().apply {
            this.followerUserId = myUserId
            this.followedUserId = targetUserId
            this.status = UserFollowEntity.STATUS_PENDING
            this.followedAt = now
        }
        userFollows.insert(entity)
        return entity
    }

    @Transactional
    override fun acceptFriendRequest(
        myUserId: Long,
        requestId: Long
    ): UserFollowEntity {
        val request = userFollows.selectById(requestId)
            ?: throw BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)
        if (request.followedUserId != myUserId) {
            throw BusinessException(CommonErrorCode.ACCESS_DENIED)
        }
        if (request.status != UserFollowEntity.STATUS_PENDING) {
            throw BusinessException(CommonErrorCode.CONFLICT)
        }
        val now = LocalDateTime.now()
        request.status = UserFollowEntity.STATUS_ACCEPTED
        request.acceptedAt = now
        userFollows.updateById(request)

        // 反向（我 follow 对方）若不存在则自动创建
        val reciprocal = userFollows.selectOne(
            QueryWrapper<UserFollowEntity>()
                .eq("follower_user_id", myUserId)
                .eq("followed_user_id", request.followerUserId)
        )
        if (reciprocal == null) {
            userFollows.insert(
                UserFollowEntity().apply {
                    this.followerUserId = myUserId
                    this.followedUserId = request.followerUserId
                    this.status = UserFollowEntity.STATUS_ACCEPTED
                    this.followedAt = now
                    this.acceptedAt = now
                }
            )
        } else if (reciprocal.status == UserFollowEntity.STATUS_PENDING) {
            reciprocal.status = UserFollowEntity.STATUS_ACCEPTED
            reciprocal.acceptedAt = now
            userFollows.updateById(reciprocal)
        }
        return request
    }

    @Transactional
    override fun rejectFriendRequest(
        myUserId: Long,
        requestId: Long
    ): UserFollowEntity {
        val request = userFollows.selectById(requestId)
            ?: throw BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)
        if (request.followedUserId != myUserId) {
            throw BusinessException(CommonErrorCode.ACCESS_DENIED)
        }
        if (request.status != UserFollowEntity.STATUS_PENDING) {
            throw BusinessException(CommonErrorCode.CONFLICT)
        }
        request.status = UserFollowEntity.STATUS_REJECTED
        request.rejectedAt = LocalDateTime.now()
        userFollows.updateById(request)
        return request
    }

    @Transactional
    override fun cancelFriendRequest(myUserId: Long, requestId: Long) {
        val deleted = userFollows.delete(
            QueryWrapper<UserFollowEntity>()
                .eq("id", requestId)
                .eq("follower_user_id", myUserId)
                .eq("status", UserFollowEntity.STATUS_PENDING)
        )
        if (deleted <= 0) {
            throw BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)
        }
    }

    @Transactional
    override fun unfriend(myUserId: Long, friendUserId: Long) {
        // 双向删除
        userFollows.delete(
            QueryWrapper<UserFollowEntity>()
                .eq("follower_user_id", myUserId)
                .eq("followed_user_id", friendUserId)
                .eq("status", UserFollowEntity.STATUS_ACCEPTED)
        )
        userFollows.delete(
            QueryWrapper<UserFollowEntity>()
                .eq("follower_user_id", friendUserId)
                .eq("followed_user_id", myUserId)
                .eq("status", UserFollowEntity.STATUS_ACCEPTED)
        )
    }

    override fun getMyFriends(myUserId: Long): List<UserFollowEntity> {
        return userFollows.selectList(
            QueryWrapper<UserFollowEntity>()
                .eq("follower_user_id", myUserId)
                .eq("status", UserFollowEntity.STATUS_ACCEPTED)
                .orderByDesc("accepted_at")
        )
    }

    override fun getPendingRequests(myUserId: Long): List<UserFollowEntity> {
        return userFollows.selectList(
            QueryWrapper<UserFollowEntity>()
                .eq("followed_user_id", myUserId)
                .eq("status", UserFollowEntity.STATUS_PENDING)
                .orderByDesc("followed_at")
        )
    }

    override fun isFriend(myUserId: Long, otherUserId: Long): Boolean {
        // 双向任一为 accepted 即视为好友
        val count = userFollows.selectCount(
            QueryWrapper<UserFollowEntity>()
                .eq("status", UserFollowEntity.STATUS_ACCEPTED)
                .and { wrapper ->
                    wrapper.eq("follower_user_id", myUserId)
                        .eq("followed_user_id", otherUserId)
                        .or()
                        .eq("follower_user_id", otherUserId)
                        .eq("followed_user_id", myUserId)
                }
        )
        return count > 0
    }

    @Transactional
    override fun updateRemark(
        myUserId: Long,
        friendUserId: Long,
        remarkName: String?
    ) {
        // 在我 follow 对方的那条记录上设置备注
        val relation = userFollows.selectOne(
            QueryWrapper<UserFollowEntity>()
                .eq("follower_user_id", myUserId)
                .eq("followed_user_id", friendUserId)
        )
        if (relation == null) {
            throw BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)
        }
        relation.remarkName = remarkName
        userFollows.updateById(relation)
    }
}
