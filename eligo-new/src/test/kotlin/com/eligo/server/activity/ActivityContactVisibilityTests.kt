package com.eligo.server.activity

import com.eligo.server.activity.mapper.ActivityPublicRow
import com.eligo.server.activity.mapper.ActivityReadMapper
import com.eligo.server.activity.service.DefaultActivityReadService
import com.eligo.server.activity.vo.PublicActivityDetailView
import com.eligo.server.organization.mapper.OrganizationMapper
import com.eligo.server.participation.service.ParticipationReadService
import com.eligo.server.security.SensitiveDataCodec
import com.eligo.server.security.UserPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional

class ActivityContactVisibilityTests {

    private val mapper = mock(ActivityReadMapper::class.java)
    private val organizations = mock(OrganizationMapper::class.java)
    private val participations = mock(ParticipationReadService::class.java)
    private val codec = mock(SensitiveDataCodec::class.java)
    private lateinit var service: DefaultActivityReadService

    @BeforeEach
    fun setUp() {
        service = DefaultActivityReadService(
            mapper,
            null,
            organizations,
            participations,
            codec,
            null,
            Clock.fixed(NOW, ZoneOffset.UTC)
        )
        `when`(mapper.findPublicById(1L)).thenReturn(row())
        `when`(mapper.findPublicMedia(1L)).thenReturn(listOf())
        `when`(codec.decrypt("phone-cipher")).thenReturn("13800000000")
        `when`(codec.decrypt("wechat-cipher")).thenReturn("organizer-wechat")
    }

    @Test
    fun activeParticipantCanSeeOrganizerContactsAfterSuccessfulJoin() {
        `when`(participations.findStatus(1L, 22L)).thenReturn(Optional.of("ACTIVE"))

        val result = service.getPublicActivity(
            UserPrincipal(22L, "session-participant"), 1L
        )

        assertThat(result.registrationGender).isEqualTo("FEMALE")
        assertThat(result.organizerPhone).isEqualTo("13800000000")
        assertThat(result.organizerWechat).isEqualTo("organizer-wechat")
        assertThat(result.organizerWechatQr).isNotNull()
        assertThat(result.organizerWechatQr!!.url)
            .isEqualTo("/api/v1/activities/1/organizer/wechat-qr")
        assertThat(result.refundPolicy).isNull()
    }

    @Test
    fun nonParticipantCannotSeeOrganizerContacts() {
        `when`(participations.findStatus(1L, 22L)).thenReturn(Optional.of("CANCELLED"))

        val result = service.getPublicActivity(
            UserPrincipal(22L, "session-viewer"), 1L
        )

        assertThat(result.organizerPhone).isNull()
        assertThat(result.organizerWechat).isNull()
        assertThat(result.organizerWechatQr).isNull()
    }

    private fun row(): ActivityPublicRow {
        val now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
        return ActivityPublicRow(
            1L,
            2,
            "测试活动",
            "HIKING",
            11L,
            "USER",
            21L,
            "小明",
            12L,
            now.minusHours(1),
            now.plusHours(1),
            now.plusHours(2),
            now.plusHours(3),
            "440305",
            "深圳市南山区测试地址",
            null,
            null,
            capacity = 20,
            participantCount = 2,
            description = "活动介绍",
            signupDetails = "报名说明",
            organizerMessage = "组织者留言",
            publishedAt = now,
            createdAt = now.minusHours(2),
            updatedAt = now,
            registrationGender = 3,
            organizerPhoneCiphertext = "phone-cipher".toByteArray(),
            organizerWechatCiphertext = "wechat-cipher".toByteArray(),
            organizerWechatQrFileId = 9001L,
            refundPolicy = null
        )
    }

    companion object {
        private val NOW = Instant.parse("2026-08-18T00:00:00Z")
    }
}
