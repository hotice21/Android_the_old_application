package com.eligo.server.profile.controller

import com.eligo.server.common.api.Result
import com.eligo.server.profile.dto.UpdateAvatarRequest
import com.eligo.server.profile.dto.UpdateInterestsRequest
import com.eligo.server.profile.dto.UpdateNicknameRequest
import com.eligo.server.profile.dto.UpdateProfileRequest
import com.eligo.server.profile.service.ProfileService
import com.eligo.server.profile.vo.InterestTagItemsView
import com.eligo.server.profile.vo.MyProfileView
import com.eligo.server.profile.vo.NicknameChangeView
import com.eligo.server.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ProfileController(private val service: ProfileService) {

    @GetMapping("/api/v1/users/me/profile")
    fun get(@AuthenticationPrincipal principal: UserPrincipal): Result<MyProfileView?> =
        Result.success(service.getMyProfile(principal))

    @PutMapping("/api/v1/users/me/profile")
    fun update(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody request: UpdateProfileRequest
    ): Result<MyProfileView?> =
        Result.success(service.updateProfile(principal, request))

    @PutMapping("/api/v1/users/me/avatar")
    fun avatar(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody request: UpdateAvatarRequest
    ): Result<MyProfileView?> =
        Result.success(service.updateAvatar(principal, request))

    @PatchMapping("/api/v1/users/me/nickname")
    fun nickname(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody request: UpdateNicknameRequest
    ): Result<NicknameChangeView?> =
        Result.success(service.updateNickname(principal, request.nickname))

    @GetMapping("/api/v1/interest-tags")
    fun interests(): Result<InterestTagItemsView?> =
        Result.success(InterestTagItemsView(service.listEnabledInterests()))

    @PutMapping("/api/v1/users/me/interests")
    fun replace(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody request: UpdateInterestsRequest
    ): Result<MyProfileView?> =
        Result.success(service.replaceInterests(principal, request.interestTagIds))
}
