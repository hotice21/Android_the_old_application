package com.eligo.server.block.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.eligo.server.block.entity.UserBlockEntity
import com.eligo.server.block.mapper.UserBlockMapper
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DefaultUserBlockService(
    private val userBlocks: UserBlockMapper
) : UserBlockService {

    @Transactional
    override fun blockUser(
        myUserId: Long,
        targetUserId: Long,
        reason: String?
    ): UserBlockEntity {
        if (myUserId == targetUserId) {
            throw BusinessException(CommonErrorCode.VALIDATION_FAILED)
        }
        val existing = userBlocks.selectOne(
            QueryWrapper<UserBlockEntity>()
                .eq("blocker_user_id", myUserId)
                .eq("blocked_user_id", targetUserId)
        )
        if (existing != null) return existing
        val entity = UserBlockEntity().apply {
            this.blockerUserId = myUserId
            this.blockedUserId = targetUserId
            this.blockedAt = LocalDateTime.now()
            this.reason = reason
        }
        userBlocks.insert(entity)
        return entity
    }

    @Transactional
    override fun unblockUser(myUserId: Long, targetUserId: Long) {
        userBlocks.delete(
            QueryWrapper<UserBlockEntity>()
                .eq("blocker_user_id", myUserId)
                .eq("blocked_user_id", targetUserId)
        )
    }

    override fun isBlockedByUser(myUserId: Long, targetUserId: Long): Boolean {
        return userBlocks.selectCount(
            QueryWrapper<UserBlockEntity>()
                .eq("blocker_user_id", targetUserId)
                .eq("blocked_user_id", myUserId)
        ) > 0
    }

    override fun isBlockingUser(myUserId: Long, targetUserId: Long): Boolean {
        return userBlocks.selectCount(
            QueryWrapper<UserBlockEntity>()
                .eq("blocker_user_id", myUserId)
                .eq("blocked_user_id", targetUserId)
        ) > 0
    }

    override fun getMyBlockedList(myUserId: Long): List<UserBlockEntity> {
        return userBlocks.selectList(
            QueryWrapper<UserBlockEntity>()
                .eq("blocker_user_id", myUserId)
                .orderByDesc("blocked_at")
        )
    }
}
