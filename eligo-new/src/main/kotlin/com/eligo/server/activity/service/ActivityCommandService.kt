package com.eligo.server.activity.service

import com.eligo.server.activity.dto.OrganizationActivityCreateRequest
import com.eligo.server.activity.dto.OrganizationActivityUpdateRequest
import com.eligo.server.activity.dto.PersonalActivityCreateRequest
import com.eligo.server.activity.dto.PersonalActivityUpdateRequest
import com.eligo.server.activity.vo.ManagedActivityDetailView
import com.eligo.server.security.UserPrincipal

interface ActivityCommandService {

    fun createPersonal(
        principal: UserPrincipal?,
        request: PersonalActivityCreateRequest?,
        idempotencyKey: String
    ): ActivityCreateOutcome

    fun createOrganization(
        principal: UserPrincipal?,
        organizationId: Long,
        request: OrganizationActivityCreateRequest?,
        idempotencyKey: String
    ): ActivityCreateOutcome

    fun updatePersonal(
        principal: UserPrincipal?,
        activityId: Long,
        request: PersonalActivityUpdateRequest?
    ): ManagedActivityDetailView

    fun updateOrganization(
        principal: UserPrincipal?,
        organizationId: Long,
        activityId: Long,
        request: OrganizationActivityUpdateRequest?
    ): ManagedActivityDetailView

    fun publish(principal: UserPrincipal?, activityId: Long): ManagedActivityDetailView

    fun cancel(principal: UserPrincipal?, activityId: Long): ManagedActivityDetailView

    fun deleteDraft(principal: UserPrincipal?, activityId: Long)
}
