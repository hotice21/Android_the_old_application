import { request } from "./request"

export function getMyProfile() {
  return request({ path: "/api/v1/users/me/profile" })
}

export function updateMyProfile(data) {
  return request({
    path: "/api/v1/users/me/profile",
    method: "PUT",
    data,
  })
}

export function updateAvatar(fileId) {
  return request({
    path: "/api/v1/users/me/avatar",
    method: "PUT",
    data: { fileId },
  })
}

export function updateNickname(nickname) {
  return request({
    path: "/api/v1/users/me/nickname",
    method: "PATCH",
    data: { nickname },
  })
}

export function getInterestTags() {
  return request({ path: "/api/v1/interest-tags" })
}

export function updateInterests(interestTagIds) {
  return request({
    path: "/api/v1/users/me/interests",
    method: "PUT",
    data: { interestTagIds },
  })
}
