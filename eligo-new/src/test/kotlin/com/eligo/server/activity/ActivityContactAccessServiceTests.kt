package com.eligo.server.activity

import java.util.function.Function

import com.eligo.server.activity.entity.ActivityEntity
import com.eligo.server.activity.mapper.ActivityMapper
import com.eligo.server.activity.service.ActivityContactAccessService
import com.eligo.server.common.error.BusinessException
import com.eligo.server.file.service.FileService
import com.eligo.server.organization.mapper.OrganizationMapper
import com.eligo.server.participation.service.ParticipationReadService
import com.eligo.server.security.UserPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.ByteArrayInputStream
import java.util.Optional

class ActivityContactAccessServiceTests {

    private val activities = mock(ActivityMapper::class.java)
    private val organizations = mock(OrganizationMapper::class.java)
    private val participations = mock(ParticipationReadService::class.java)
    private val files = mock(FileService::class.java)
    private lateinit var service: ActivityContactAccessService

    @BeforeEach
    fun setUp() {
        service = ActivityContactAccessService(
            activities, organizations, participations, files
        )
    }

    @Test
    fun activeParticipantCanReadQr() {
        val activity = activity(101L, 9001L)
        val content = FileService.FileContent(
            ByteArrayInputStream(byteArrayOf(1, 2, 3)),
            "image/png",
            "private, max-age=60"
        )
        `when`(activities.selectById(7001L)).thenReturn(activity)
        `when`(participations.findStatus(7001L, 202L))
            .thenReturn(Optional.of("ACTIVE"))
        `when`(files.openAuthorizedActivityContactContent(9001L)).thenReturn(content)

        val result = service.openOrganizerWechatQr(
            UserPrincipal(202L, "participant-session"), 7001L
        )

        assertThat(result).isSameAs(content)
        verify(files).openAuthorizedActivityContactContent(9001L)
    }

    @Test
    fun cancelledParticipantCannotReadQr() {
        `when`(activities.selectById(7001L)).thenReturn(activity(101L, 9001L))
        `when`(participations.findStatus(7001L, 202L))
            .thenReturn(Optional.of("CANCELLED"))

        assertThatThrownBy {
            service.openOrganizerWechatQr(
                UserPrincipal(202L, "cancelled-session"), 7001L
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode.code  })
            .isEqualTo(10006)

        verify(files, never()).openAuthorizedActivityContactContent(9001L)
    }

    @Test
    fun ownerCanReadQrWithoutParticipation() {
        `when`(activities.selectById(7001L)).thenReturn(activity(202L, 9001L))
        val content = FileService.FileContent(
            ByteArrayInputStream(byteArrayOf(9)),
            "image/png",
            "private, max-age=60"
        )
        `when`(files.openAuthorizedActivityContactContent(9001L)).thenReturn(content)

        val result = service.openOrganizerWechatQr(
            UserPrincipal(202L, "owner-session"), 7001L
        )

        assertThat(result).isSameAs(content)
        verify(participations, never()).findStatus(7001L, 202L)
    }

    private fun activity(ownerUserId: Long, qrFileId: Long): ActivityEntity {
        val activity = ActivityEntity()
        activity.id = 7001L
        activity.ownerUserId = ownerUserId
        activity.status = 2
        activity.organizerWechatQrFileId = qrFileId
        return activity
    }
}
