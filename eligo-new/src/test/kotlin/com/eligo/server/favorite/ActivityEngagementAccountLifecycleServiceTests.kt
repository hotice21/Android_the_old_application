package com.eligo.server.favorite

import com.eligo.server.comment.mapper.ActivityCommentMapper
import com.eligo.server.favorite.mapper.ActivityFavoriteMapper
import com.eligo.server.favorite.service.ActivityEngagementAccountLifecycleService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

class ActivityEngagementAccountLifecycleServiceTests {

    @Test
    fun deactivationDeletesOnlyFavoritesCreatedByThatUser() {
        val favorites = mock(ActivityFavoriteMapper::class.java)
        val comments = mock(ActivityCommentMapper::class.java)
        val service = ActivityEngagementAccountLifecycleService(favorites, comments)

        service.applyDeactivation(202L, LocalDateTime.parse("2026-08-22T08:00:00"))

        verify(favorites).deleteByUserId(202L)
        verify(comments).softDeleteByAuthor(202L, LocalDateTime.parse("2026-08-22T08:00:00"))
        val transactional = ActivityEngagementAccountLifecycleService::class.java
            .getMethod("applyDeactivation", Long::class.javaPrimitiveType, LocalDateTime::class.java)
            .getAnnotation(Transactional::class.java)
        assertThat(transactional.propagation).isEqualTo(Propagation.MANDATORY)
    }
}
