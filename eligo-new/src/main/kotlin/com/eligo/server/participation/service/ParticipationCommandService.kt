package com.eligo.server.participation.service

import com.eligo.server.participation.vo.ActivityParticipationView
import com.eligo.server.security.UserPrincipal

interface ParticipationCommandService {

    fun join(principal: UserPrincipal?, activityId: Long): ActivityParticipationView

    fun cancel(principal: UserPrincipal?, activityId: Long): ActivityParticipationView

    fun remove(
        principal: UserPrincipal?,
        activityId: Long,
        userId: Long
    ): ActivityParticipationView
}
