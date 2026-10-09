package com.eligo.server.block.service

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper
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
        // 已存在则直接返回
        val existing = userBlocks.selectOne(
            LambdaQueryWrapper<UserBlockEntity>()
                .eq(UserBlockEntity::blockerUserId, myUserId)
                .eq(UserBlockEntity::blockedUserId, targetUserId)
        )
        if (existing != null) {
            return existing
        }
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
            LambdaQueryWrapper<UserBlockEntity>()
                .eq(UserBlockEntity::blockerUserId, myUserId)
                .eq(UserBlockEntity::blockedUserId, targetUserId)
        )
    }

    override fun isBlockedByUser(myUserId: Long, targetUserId: Long): Boolean {
        // targetUserId 是否拉黑了我
        return userBlocks.selectCount(
            LambdaQueryWrapper<UserBlockEntity>()
                .eq(UserBlockEntity::blockerUserId, targetUserId)
                .eq(UserBlockEntity::blockedUserId, myUserId)
        ) > 0
    }

    override fun isBlockingUser(myUserId: Long, targetUserId: Long): Boolean {
        // 我是否拉黑了 targetUserId
        return userBlocks.selectCount(
            LambdaQueryWrapper<UserBlockEntity>()
                .eq(UserBlockEntity::blockerUserId, myUserId)
                .eq(UserBlockEntity::blockedUserId, targetUserId)
        ) > 0
    }

    override fun getMyBlockedList(myUserId: Long): List<UserBlockEntity> {
        return userBlocks.selectList(
            LambdaQueryWrapper<UserBlockEntity>()
                .eq(UserBlockEntity::blockerUserId, myUserId)
                .orderByDesc(UserBlockEntity::blockedAt)
        )
    }
}
