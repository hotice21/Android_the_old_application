package com.eligo.server.post.service

interface PostLikeService {
    /** 点赞，返回 true 表示新建，false 表示已存在 */
    fun like(userId: Long, postId: Long): Boolean

    /** 取消点赞 */
    fun unlike(userId: Long, postId: Long)

    /** 当前用户是否已点赞 */
    fun isLikedByMe(userId: Long, postId: Long): Boolean
}
