package com.eligo.server.post

import java.util.function.Function

import com.eligo.server.follow.mapper.OrganizationFollowMapper
import com.eligo.server.follow.mapper.UserFollowMapper
import com.eligo.server.post.entity.PostEntity
import com.eligo.server.post.entity.PostStatusEventEntity
import com.eligo.server.post.mapper.PostMapper
import com.eligo.server.post.mapper.PostStatusEventMapper
import com.eligo.server.post.service.PostAccountLifecycleService
import com.eligo.server.recommendation.service.RecommendationIndexTaskWriter
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

class PostAccountLifecycleServiceTests {

    @Test
    fun deactivationDeletesSocialGraphAndClosesOnlyPersonalPosts() {
        val userFollows = mock<UserFollowMapper>()
        val organizationFollows = mock<OrganizationFollowMapper>()
        val posts = mock<PostMapper>()
        val events = mock<PostStatusEventMapper>()
        val recommendationTasks = mock<RecommendationIndexTaskWriter>()
        val service = PostAccountLifecycleService(
            userFollows, organizationFollows, posts, events, recommendationTasks)
        val now = LocalDateTime.parse("2026-08-16T08:00:00")
        val draft = post(7001L, PostEntity.STATUS_DRAFT)
        val published = post(7002L, PostEntity.STATUS_PUBLISHED)
        val hidden = post(7003L, PostEntity.STATUS_HIDDEN)
        whenever(posts.lockPersonalPostsForDeactivation(202L))
            .thenReturn(listOf(draft, published, hidden))
        whenever(posts.softDeleteById(7001L, PostEntity.STATUS_DRAFT, now)).thenReturn(1)
        whenever(posts.hidePublishedById(7002L, now)).thenReturn(1)

        service.applyDeactivation(202L, now)

        verify(userFollows).deleteAllForUser(202L)
        verify(organizationFollows).deleteAllByFollower(202L)
        verify(posts).softDeleteById(7001L, PostEntity.STATUS_DRAFT, now)
        verify(posts).hidePublishedById(7002L, now)
        verify(recommendationTasks).enqueueDelete(7002L)
        val captured = ArgumentCaptor.forClass(PostStatusEventEntity::class.java)
        verify(events, times(2)).insert(captured.capture())
        assertThat(captured.allValues)
            .allSatisfy { event ->
                assertThat(event.actorType)
                    .isEqualTo(PostStatusEventEntity.ACTOR_SYSTEM)
                assertThat(event.actorUserId).isNull()
                assertThat(event.reasonCode).isEqualTo("ACCOUNT_DEACTIVATED")
            }
        assertThat(captured.allValues)
            .extracting(Function { it.toStatus })
            .containsExactly(PostEntity.STATUS_DELETED, PostEntity.STATUS_HIDDEN)

        val transactional = PostAccountLifecycleService::class.java
            .getMethod("applyDeactivation", java.lang.Long.TYPE, LocalDateTime::class.java)
            .getAnnotation(Transactional::class.java)
        assertThat(transactional.propagation).isEqualTo(Propagation.MANDATORY)
    }

    private fun post(id: Long, status: Int): PostEntity {
        val post = PostEntity()
        post.id = id
        post.authorUserId = 202L
        post.status = status
        return post
    }
}
