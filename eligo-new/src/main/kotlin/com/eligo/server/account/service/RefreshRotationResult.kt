package com.eligo.server.account.service

import com.eligo.server.account.entity.UserEntity
import com.eligo.server.account.entity.UserLoginSessionEntity
import com.eligo.server.security.TokenPair

data class RefreshRotationResult(
    val status: Status,
    val user: UserEntity?,
    val session: UserLoginSessionEntity?,
    val tokenPair: TokenPair?
) {
    enum class Status {
        SUCCESS,
        STALE_OR_INVALID
    }

    companion object {
        @JvmStatic
        fun success(
            user: UserEntity,
            session: UserLoginSessionEntity,
            tokenPair: TokenPair
        ): RefreshRotationResult = RefreshRotationResult(Status.SUCCESS, user, session, tokenPair)

        @JvmStatic
        fun staleOrInvalid(): RefreshRotationResult =
            RefreshRotationResult(Status.STALE_OR_INVALID, null, null, null)
    }
}
