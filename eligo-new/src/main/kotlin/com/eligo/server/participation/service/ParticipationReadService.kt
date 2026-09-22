package com.eligo.server.participation.service

import com.eligo.server.common.api.CursorPage
import com.eligo.server.participation.vo.ActivityParticipantSummaryView
import com.eligo.server.participation.vo.MyParticipationView
import com.eligo.server.security.UserPrincipal
import java.util.Optional

interface ParticipationReadService {

    fun listParticipants(
        principal: UserPrincipal?,
        activityId: Long,
        cursor: String?,
        limit: Int
    ): CursorPage<ActivityParticipantSummaryView>

    fun listMyParticipations(
        principal: UserPrincipal?,
        cursor: String?,
        limit: Int,
        status: String?,
        keyword: String?
    ): CursorPage<MyParticipationView>

    fun findStatus(activityId: Long, userId: Long): Optional<String>
}
