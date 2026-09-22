package com.eligo.server.comment.service

import com.eligo.server.comment.dto.ActivityCommentCreateRequest
import com.eligo.server.comment.vo.ActivityCommentView
import com.eligo.server.common.api.CursorPage
import com.eligo.server.security.UserPrincipal

interface ActivityCommentService {
    fun create(
        principal: UserPrincipal,
        activityId: Long,
        request: ActivityCommentCreateRequest,
        idempotencyKey: String
    ): ActivityCommentCreateOutcome

    fun list(activityId: Long, cursor: String?, limit: Int): CursorPage<ActivityCommentView>

    fun delete(principal: UserPrincipal, activityId: Long, commentId: Long): ActivityCommentView
}
