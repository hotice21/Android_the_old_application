package com.eligo.server.security

import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Profile("!test")
class DatabaseAccountRestrictionReader(
    private val jdbcTemplate: JdbcTemplate
) : AccountRestrictionReader {

    @Transactional(readOnly = true)
    override fun read(userId: Long): AccountRestrictionReader.State {
        val accountStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM users WHERE id = ?", Int::class.javaObjectType, userId
        ) ?: throw BusinessException(AccountUserFileErrorCode.LOGIN_SESSION_INVALID)
        val profileCompleted = jdbcTemplate.queryForObject(
            """
            SELECT EXISTS(
                SELECT 1 FROM user_profiles
                WHERE user_id = ? AND completed_at IS NOT NULL
            )
            """,
            Boolean::class.javaObjectType,
            userId
        )
        val pendingAgreementIds = jdbcTemplate.queryForList(
            """
            SELECT a.id
            FROM agreements a
            LEFT JOIN agreement_consents c
              ON c.agreement_id = a.id AND c.user_id = ?
            WHERE a.status = 3
              AND a.effective_at <= UTC_TIMESTAMP(3)
              AND a.requires_reconsent = 1
              AND c.id IS NULL
            ORDER BY a.agreement_type ASC, a.effective_at DESC, a.id DESC
            """,
            Long::class.javaObjectType,
            userId
        )
        return AccountRestrictionReader.State(
            accountStatus,
            profileCompleted == true,
            pendingAgreementIds.toList()
        )
    }

    @Transactional(readOnly = true)
    override fun canDownloadExport(userId: Long, fileId: Long): Boolean {
        val allowed = jdbcTemplate.queryForObject(
            """
            SELECT EXISTS(
                SELECT 1
                FROM user_data_requests
                WHERE user_id = ?
                  AND request_type = 2
                  AND status = 4
                  AND result_file_id = ?
                  AND result_expires_at > UTC_TIMESTAMP(3)
            )
            """,
            Boolean::class.javaObjectType,
            userId,
            fileId
        )
        return allowed == true
    }
}
