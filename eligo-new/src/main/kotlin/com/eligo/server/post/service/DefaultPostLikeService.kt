package com.eligo.server.post.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.post.entity.PostEntity
import com.eligo.server.post.entity.PostLikeEntity
import com.eligo.server.post.mapper.PostLikeMapper
import com.eligo.server.post.mapper.PostMapper
import java.time.LocalDateTime
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class DefaultPostLikeService(
    private val posts: PostMapper,
    private val likes: PostLikeMapper
) : PostLikeService {

    @Transactional
    override fun like(userId: Long, postId: Long): Boolean {
        requirePublishedPost(postId)
        val now = LocalDateTime.now()

        // 检查是否已点赞
        val existing = likes.selectOne(
            QueryWrapper<PostLikeEntity>()
                .eq("user_id", userId)
                .eq("post_id", postId)
        )
        if (existing != null) {
            return false
        }

        // 插入点赞记录
        val entity = PostLikeEntity()
        entity.userId = userId
        entity.postId = postId
        entity.likedAt = now
        likes.insert(entity)

        // 原子递增 like_count
        posts.updateLikeCount(postId, 1, now)
        return true
    }

    @Transactional
    override fun unlike(userId: Long, postId: Long) {
        requirePublishedPost(postId)
        val now = LocalDateTime.now()

        val deleted = likes.delete(
            QueryWrapper<PostLikeEntity>()
                .eq("user_id", userId)
                .eq("post_id", postId)
        )
        if (deleted > 0) {
            posts.updateLikeCount(postId, -1, now)
        }
    }

    @Transactional(readOnly = true)
    override fun isLikedByMe(userId: Long, postId: Long): Boolean {
        if (userId <= 0 || postId <= 0) return false
        return likes.countByUserAndPost(userId, postId) > 0
    }

    private fun requirePublishedPost(postId: Long) {
        val post = posts.selectById(postId)
        if (post == null || post.status != PostEntity.STATUS_PUBLISHED) {
            throw BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)
        }
    }
}
