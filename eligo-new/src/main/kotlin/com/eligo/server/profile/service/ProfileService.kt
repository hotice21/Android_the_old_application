package com.eligo.server.profile.service

import com.eligo.server.profile.dto.UpdateAvatarRequest
import com.eligo.server.profile.dto.UpdateProfileRequest
import com.eligo.server.profile.vo.InterestTagView
import com.eligo.server.profile.vo.MyProfileView
import com.eligo.server.profile.vo.NicknameChangeView
import com.eligo.server.security.UserPrincipal

interface ProfileService : ProfileCompletionReader, ProfileGenderReader {

    fun getMyProfile(principal: UserPrincipal): MyProfileView

    fun updateProfile(principal: UserPrincipal, request: UpdateProfileRequest): MyProfileView

    fun updateAvatar(principal: UserPrincipal, request: UpdateAvatarRequest): MyProfileView {
        throw UnsupportedOperationException("Avatar update is unavailable")
    }

    fun updateNickname(principal: UserPrincipal, nickname: String?): NicknameChangeView

    fun listEnabledInterests(): List<InterestTagView>

    fun replaceInterests(principal: UserPrincipal, interestTagIds: List<Long>?): MyProfileView

    fun recalculateCompletion(userId: Long): Boolean
}
