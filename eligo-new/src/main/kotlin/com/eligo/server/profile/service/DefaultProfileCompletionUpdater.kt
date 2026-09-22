package com.eligo.server.profile.service

import com.eligo.server.account.mapper.UserPhoneBindingMapper
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.profile.mapper.UserInterestTagMapper
import com.eligo.server.profile.mapper.UserProfileMapper
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class DefaultProfileCompletionUpdater(
    private val profiles: UserProfileMapper,
    private val userTags: UserInterestTagMapper,
    private val phoneBindings: UserPhoneBindingMapper,
    private val clock: Clock = Clock.systemUTC()
) : ProfileCompletionUpdater {

    @Transactional
    override fun recalculate(userId: Long): Boolean {
        val profile = profiles.lockByUserId(userId)
            .orElseThrow { BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND) }
        val complete =
            !profile.nickname.isNullOrBlank() &&
                profile.birthDateCiphertext != null &&
                profile.genderCode != null &&
                !profile.provinceCode.isNullOrBlank() &&
                !profile.cityCode.isNullOrBlank() &&
                !profile.districtCode.isNullOrBlank() &&
                phoneBindings.findActiveByUserId(userId).isPresent &&
                userTags.countEnabledByUserId(userId) > 0
        if (complete != (profile.completedAt != null)) {
            val updated = profiles.updateCompletedAt(
                userId,
                if (complete) LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC) else null,
                profile.version!!
            )
            if (updated != 1) {
                throw BusinessException(CommonErrorCode.CONFLICT)
            }
        }
        return complete
    }
}
