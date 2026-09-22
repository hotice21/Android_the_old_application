package com.eligo.server.favorite.service

import com.eligo.server.comment.mapper.ActivityCommentMapper
import com.eligo.server.favorite.mapper.ActivityFavoriteMapper
import java.time.LocalDateTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class ActivityEngagementAccountLifecycleService @Autowired constructor(
    private val favorites: ActivityFavoriteMapper,
    private val comments: ActivityCommentMapper?
) {
    constructor(favorites: ActivityFavoriteMapper) : this(favorites, null)

    @Transactional(propagation = Propagation.MANDATORY)
    fun applyDeactivation(userId: Long, now: LocalDateTime?) {
        favorites.deleteByUserId(userId)
        comments?.softDeleteByAuthor(userId, now)
    }
}
