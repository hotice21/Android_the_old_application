package com.eligo.server.profile.service

import com.eligo.server.account.service.PhoneBindingReader
import com.eligo.server.account.vo.PhoneBindingView
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.common.web.RequestIdContext
import com.eligo.server.profile.dto.UpdateAvatarRequest
import com.eligo.server.profile.dto.UpdateProfileRequest
import com.eligo.server.file.entity.FileObjectEntity
import com.eligo.server.file.service.FileService
import com.eligo.server.profile.entity.InterestTagEntity
import com.eligo.server.profile.entity.UserProfileChangeLogEntity
import com.eligo.server.profile.entity.UserProfileEntity
import com.eligo.server.profile.mapper.InterestTagMapper
import com.eligo.server.profile.mapper.UserInterestTagMapper
import com.eligo.server.profile.mapper.UserProfileChangeLogMapper
import com.eligo.server.profile.mapper.UserProfileMapper
import com.eligo.server.profile.vo.InterestTagView
import com.eligo.server.profile.vo.MyProfileView
import com.eligo.server.profile.vo.NicknameChangeView
import com.eligo.server.security.SensitiveDataCodec
import com.eligo.server.security.UserPrincipal
import java.nio.charset.StandardCharsets
import java.sql.SQLException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import org.springframework.context.annotation.Profile
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class DefaultProfileService(
    private val profiles: UserProfileMapper,
    private val tags: InterestTagMapper,
    private val userTags: UserInterestTagMapper,
    private val logs: UserProfileChangeLogMapper,
    private val codec: SensitiveDataCodec,
    private val regions: RegionCatalog,
    private val nicknameContentValidator: NicknameContentValidator,
    private val phoneBindingReader: PhoneBindingReader,
    private val profileCompletionUpdater: ProfileCompletionUpdater,
    private val fileService: FileService? = null,
    private val clock: Clock = Clock.systemUTC()
) : ProfileService {

    override fun getMyProfile(principal: UserPrincipal): MyProfileView {
        return view(principal, load(principal.userId))
    }

    @Transactional
    override fun updateProfile(
        principal: UserPrincipal,
        request: UpdateProfileRequest
    ): MyProfileView {
        val current = lock(principal.userId)
        var nickname = current.nickname
        if (nickname == null) {
            nickname = validateAndNormalizeNickname(request.nickname)
        } else if (request.nickname != null) {
            val requestedNickname = validateAndNormalizeNickname(request.nickname)
            if (nickname != requestedNickname) {
                throw BusinessException(CommonErrorCode.CONFLICT)
            }
        }

        val region = regions.resolve(
            request.provinceCode, request.cityCode, request.districtCode
        )
        val bio = trimNullable(request.bio)
        if (bio != null && bio.length > MAX_BIO_LENGTH) {
            throw invalid()
        }
        val gender = genderCode(request.gender)
        val birth = codec.encrypt(request.birthDate.toString())
            .toByteArray(StandardCharsets.UTF_8)
        val email = normalizeEmail(request.email)
        var emailCiphertext: ByteArray? = null
        var emailLookupHash: ByteArray? = null
        if (email != null) {
            emailCiphertext = codec.encrypt(email).toByteArray(StandardCharsets.UTF_8)
            emailLookupHash = codec.lookupHash(email)
            profiles.findUserIdByEmailLookupHash(emailLookupHash)
                .filter { it != principal.userId }
                .ifPresent { throw emailAlreadyUsed() }
        }

        val updated = try {
            profiles.updateBasicProfile(
                principal.userId,
                nickname,
                birth,
                emailCiphertext,
                emailLookupHash,
                gender,
                region.provinceCode,
                region.provinceName,
                region.cityCode,
                region.cityName,
                region.districtCode,
                region.districtName,
                bio,
                current.version!!
            )
        } catch (exception: DuplicateKeyException) {
            if (isEmailUniqueConflict(exception)) {
                throw emailAlreadyUsed()
            }
            throw exception
        }
        if (updated != 1) {
            throw BusinessException(CommonErrorCode.CONFLICT)
        }

        recalculateCompletion(principal.userId)
        return view(principal, load(principal.userId))
    }

    @Transactional
    override fun updateAvatar(
        principal: UserPrincipal,
        request: UpdateAvatarRequest
    ): MyProfileView {
        val fileId = try {
            request.fileId!!.toLong()
        } catch (exception: NumberFormatException) {
            throw invalid()
        }
        if (fileService == null) {
            throw IllegalStateException("文件服务未配置")
        }
        val file = fileService.requireUsableAvatar(principal.userId, fileId)
        val profile = lock(principal.userId)
        fileService.activateAvatar(file)
        if (profiles.updateAvatarFileId(
                principal.userId, fileId, profile.version!!
            ) != 1
        ) {
            throw BusinessException(CommonErrorCode.CONFLICT)
        }
        recalculateCompletion(principal.userId)
        return view(principal, load(principal.userId))
    }

    @Transactional
    override fun updateNickname(
        principal: UserPrincipal,
        rawNickname: String?
    ): NicknameChangeView {
        val nickname = validateAndNormalizeNickname(rawNickname)
        val current = lock(principal.userId)
        if (current.nickname == nickname) {
            return nicknameView(current)
        }

        if (current.nickname == null) {
            if (profiles.setFirstNickname(
                    principal.userId, nickname, current.version!!
                ) != 1
            ) {
                throw BusinessException(CommonErrorCode.CONFLICT)
            }
        } else {
            updateExistingNickname(principal, nickname, current)
        }

        recalculateCompletion(principal.userId)
        return nicknameView(load(principal.userId))
    }

    private fun updateExistingNickname(
        principal: UserPrincipal,
        nickname: String,
        current: UserProfileEntity
    ) {
        val now = now()
        val cutoff = now.minusDays(30)
        if (current.nicknameChangedAt != null &&
            current.nicknameChangedAt!!.isAfter(cutoff)
        ) {
            throw frequent()
        }
        if (profiles.updateNicknameWithLimit(
                principal.userId,
                nickname,
                now,
                cutoff,
                current.version!!
            ) != 1
        ) {
            throw frequent()
        }

        val log = UserProfileChangeLogEntity()
        log.userId = principal.userId
        log.fieldCode = "NICKNAME"
        log.oldValueCiphertext = current.nickname?.let {
            codec.encrypt(it).toByteArray(StandardCharsets.UTF_8)
        }
        log.newValueCiphertext = codec.encrypt(nickname)
            .toByteArray(StandardCharsets.UTF_8)
        log.sourceType = 1
        log.actorUserId = principal.userId
        log.requestId = RequestIdContext.current()
        log.createdAt = now
        logs.insert(log)
    }

    override fun listEnabledInterests(): List<InterestTagView> {
        return tags.findAllEnabled().map { tagView(it) }
    }

    @Transactional
    override fun replaceInterests(
        principal: UserPrincipal,
        ids: List<Long>?
    ): MyProfileView {
        lock(principal.userId)
        if (ids == null) {
            throw invalid()
        }

        val uniqueIds = ids.filterNotNull().distinct().sorted()
        if (uniqueIds.isEmpty() ||
            uniqueIds.size > MAX_INTERESTS ||
            tags.findEnabledByIds(uniqueIds).size != uniqueIds.size
        ) {
            throw invalid()
        }

        userTags.deleteByUserId(principal.userId)
        val now = now()
        uniqueIds.forEach { id ->
            userTags.insertSelection(principal.userId, id, now)
        }
        recalculateCompletion(principal.userId)
        return view(principal, load(principal.userId))
    }

    override fun recalculateCompletion(userId: Long): Boolean {
        return profileCompletionUpdater.recalculate(userId)
    }

    override fun isCompleted(userId: Long): Boolean {
        return profiles.findByUserId(userId)
            .map { it.completedAt != null }
            .orElse(false)
    }

    override fun genderCode(userId: Long): Int? {
        return profiles.findByUserId(userId)
            .map { it.genderCode }
            .orElse(null)
    }

    private fun view(principal: UserPrincipal, profile: UserProfileEntity): MyProfileView {
        val birthDate = profile.birthDateCiphertext?.let {
            LocalDate.parse(codec.decrypt(String(it, StandardCharsets.UTF_8)))
        }
        val region = profile.provinceCode?.let {
            MyProfileView.RegionView(
                profile.provinceCode,
                profile.provinceName,
                profile.cityCode,
                profile.cityName,
                profile.districtCode,
                profile.districtName
            )
        }
        val avatar = profile.avatarFileId?.let { fileId ->
            val avatarUrl = fileService?.getOwned(principal, fileId)?.url
            MyProfileView.AvatarView(fileId.toString(), avatarUrl)
        }
        val email = profile.emailCiphertext?.let {
            codec.decrypt(String(it, StandardCharsets.UTF_8))
        }
        val phoneBinding: PhoneBindingView =
            phoneBindingReader.current(profile.userId!!)
        return MyProfileView(
            profile.userId.toString(),
            profile.nickname,
            avatar,
            birthDate,
            genderName(profile.genderCode),
            region,
            email,
            profile.bio,
            tags.findSelectedByUserId(profile.userId!!).map { tagView(it) },
            MyProfileView.PhoneBindingView(
                phoneBinding.bound, phoneBinding.maskedPhone
            ),
            profile.completedAt != null,
            instant(profile.completedAt),
            profile.nicknameChangedAt?.let { instant(it.plusDays(30)) }
        )
    }

    private fun tagView(tag: InterestTagEntity): InterestTagView {
        return InterestTagView(
            tag.id.toString(),
            tag.tagCode,
            tag.tagName,
            tag.sortOrder ?: 0
        )
    }

    private fun nicknameView(profile: UserProfileEntity): NicknameChangeView {
        return NicknameChangeView(
            profile.nickname,
            instant(profile.nicknameChangedAt),
            profile.nicknameChangedAt?.let { instant(it.plusDays(30)) }
        )
    }

    private fun load(userId: Long): UserProfileEntity {
        return profiles.findByUserId(userId)
            .orElseThrow { BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND) }
    }

    private fun lock(userId: Long): UserProfileEntity {
        return profiles.lockByUserId(userId)
            .orElseThrow { BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND) }
    }

    private fun validateAndNormalizeNickname(rawNickname: String?): String {
        val nickname = trimNullable(rawNickname)
        if (nickname == null ||
            nickname.length < MIN_NICKNAME_LENGTH ||
            nickname.length > MAX_NICKNAME_LENGTH ||
            nickname.codePoints().anyMatch { isForbiddenNicknameCodePoint(it) }
        ) {
            throw invalid()
        }
        nicknameContentValidator.validate(nickname)
        return nickname
    }

    private fun isForbiddenNicknameCodePoint(codePoint: Int): Boolean {
        return Character.isISOControl(codePoint) ||
            Character.getType(codePoint) == Character.FORMAT.toInt()
    }

    private fun normalizeEmail(rawEmail: String?): String? {
        val email = trimNullable(rawEmail) ?: return null
        if (email.length > MAX_EMAIL_LENGTH) {
            throw invalid()
        }
        val normalized = email.lowercase(Locale.ROOT)
        val separator = normalized.indexOf('@')
        if (separator <= 0 ||
            separator != normalized.lastIndexOf('@') ||
            separator == normalized.length - 1
        ) {
            throw invalid()
        }
        return normalized
    }

    private fun trimNullable(value: String?): String? {
        if (value == null) {
            return null
        }
        val stripped = value.trim()
        return stripped.ifEmpty { null }
    }

    private fun genderCode(gender: String?): Int = when (gender) {
        "MALE" -> 1
        "FEMALE" -> 2
        "OTHER_OR_UNDISCLOSED" -> 3
        else -> throw invalid()
    }

    private fun genderName(gender: Int?): String? {
        if (gender == null) {
            return null
        }
        return when (gender) {
            1 -> "MALE"
            2 -> "FEMALE"
            3 -> "OTHER_OR_UNDISCLOSED"
            else -> throw IllegalStateException("性别编码无效")
        }
    }

    private fun now(): LocalDateTime {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
    }

    private fun instant(value: LocalDateTime?): Instant? {
        return value?.toInstant(ZoneOffset.UTC)
    }

    private fun invalid(): BusinessException {
        return BusinessException(CommonErrorCode.VALIDATION_FAILED)
    }

    private fun isEmailUniqueConflict(exception: DuplicateKeyException): Boolean {
        var current: Throwable? = exception
        while (current != null) {
            val message = current.message
            if (current is SQLException &&
                current.errorCode == 1062 &&
                current.sqlState == "23000" &&
                !message.isNullOrEmpty() &&
                message.contains(EMAIL_UNIQUE_CONSTRAINT)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun emailAlreadyUsed(): BusinessException {
        return BusinessException(AccountUserFileErrorCode.EMAIL_ALREADY_USED)
    }

    private fun frequent(): BusinessException {
        return BusinessException(
            AccountUserFileErrorCode.NICKNAME_UPDATE_TOO_FREQUENT
        )
    }

    companion object {
        private const val MIN_NICKNAME_LENGTH = 2
        private const val MAX_NICKNAME_LENGTH = 20
        private const val MAX_BIO_LENGTH = 200
        private const val MAX_EMAIL_LENGTH = 254
        private const val MAX_INTERESTS = 20
        private const val EMAIL_UNIQUE_CONSTRAINT = "uk_profile_email_lookup_hash"
    }
}
