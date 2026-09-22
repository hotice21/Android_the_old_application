package com.eligo.server.activity.service

import com.eligo.server.activity.entity.ActivityEntity
import com.eligo.server.activity.mapper.ActivityMapper
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.file.service.FileService
import com.eligo.server.organization.mapper.OrganizationMapper
import com.eligo.server.participation.service.ParticipationReadService
import com.eligo.server.security.UserPrincipal
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class ActivityContactAccessService(
    private val activities: ActivityMapper,
    private val organizations: OrganizationMapper,
    private val participations: ParticipationReadService,
    private val files: FileService
) {

    @Transactional(readOnly = true)
    fun openOrganizerWechatQr(
        principal: UserPrincipal?,
        activityId: Long
    ): FileService.FileContent {
        if (principal == null) {
            throw BusinessException(CommonErrorCode.AUTHENTICATION_REQUIRED)
        }
        if (activityId <= 0) {
            throw BusinessException(CommonErrorCode.VALIDATION_FAILED)
        }
        val activity = activities.selectById(activityId)
        if (activity == null
            || activity.status == null
            || (activity.status != PUBLISHED
                && activity.status != CANCELLED
                && activity.status != ENDED)
            || activity.organizerWechatQrFileId == null
        ) {
            throw notFound()
        }
        if (!isOwner(principal.userId, activity)
            && !participations.findStatus(activityId, principal.userId)
                .filter { "ACTIVE" == it }
                .isPresent
        ) {
            throw notFound()
        }
        return files.openAuthorizedActivityContactContent(
            activity.organizerWechatQrFileId!!
        )
    }

    private fun isOwner(userId: Long, activity: ActivityEntity): Boolean {
        if (activity.ownerUserId == userId) {
            return true
        }
        return activity.ownerOrganizationId != null
            && organizations.findActiveOwnedByUserId(userId).any { item ->
                item.id == activity.ownerOrganizationId
            }
    }

    private fun notFound(): BusinessException =
        BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)

    companion object {
        private const val PUBLISHED = 2
        private const val CANCELLED = 3
        private const val ENDED = 4
    }
}
