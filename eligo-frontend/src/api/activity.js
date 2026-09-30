import { request } from "./request"

/**
 * 活动模块接口（对应后端 ActivityController + ParticipationController + ActivityFavoriteController）
 */

/**
 * 分页获取已发布活动列表（CursorPage 格式）
 * 返回 { items: [...], nextCursor, hasMore }
 * @param {Object} params
 * @param {string} [params.cursor] - 游标，首次不传
 * @param {number} [params.limit] - 每页数量，默认由后端决定
 * @param {'latest'|'hottest'} [params.sort] - 排序
 */
export function getActivities(params = {}) {
  const query = new URLSearchParams()
  if (params.cursor) query.set("cursor", params.cursor)
  if (params.limit) query.set("limit", String(params.limit))
  if (params.sort) query.set("sort", params.sort)
  const qs = query.toString()
  return request({
    path: `/api/v1/activities${qs ? "?" + qs : ""}`,
    auth: false, // 公开列表无需登录
  })
}

/**
 * 获取活动详情
 * @param {string|number} activityId
 */
export function getActivityDetail(activityId) {
  return request({ path: `/api/v1/activities/${activityId}` })
}

/**
 * 地图专用活动列表（带经纬度）
 * @param {Object} params
 * @param {number} params.latitude
 * @param {number} params.longitude
 * @param {number} [params.radiusKm] - 半径公里
 */
export function getMapActivities(params = {}) {
  const query = new URLSearchParams()
  if (params.latitude != null) query.set("latitude", String(params.latitude))
  if (params.longitude != null) query.set("longitude", String(params.longitude))
  if (params.radiusKm != null) query.set("radiusKm", String(params.radiusKm))
  const qs = query.toString()
  return request({
    path: `/api/v1/activities/map${qs ? "?" + qs : ""}`,
    auth: false,
  })
}

// ========== 报名（对应 ParticipationController） ==========

/**
 * 报名活动
 * PUT /api/v1/activities/{activityId}/participation
 */
export function joinActivity(activityId, data = {}) {
  return request({
    path: `/api/v1/activities/${activityId}/participation`,
    method: "PUT",
    data,
  })
}

/**
 * 取消报名
 * DELETE /api/v1/activities/{activityId}/participation
 */
export function cancelJoinActivity(activityId) {
  return request({
    path: `/api/v1/activities/${activityId}/participation`,
    method: "DELETE",
  })
}

/**
 * 获取活动参与者列表
 */
export function getActivityParticipants(activityId, params = {}) {
  const query = new URLSearchParams()
  if (params.cursor) query.set("cursor", params.cursor)
  if (params.limit) query.set("limit", String(params.limit))
  const qs = query.toString()
  return request({
    path: `/api/v1/activities/${activityId}/participants${qs ? "?" + qs : ""}`,
  })
}

/**
 * 获取我报名过的活动
 */
export function getMyParticipations(params = {}) {
  const query = new URLSearchParams()
  if (params.cursor) query.set("cursor", params.cursor)
  if (params.limit) query.set("limit", String(params.limit))
  const qs = query.toString()
  return request({
    path: `/api/v1/users/me/participations${qs ? "?" + qs : ""}`,
  })
}

// ========== 收藏（对应 ActivityFavoriteController） ==========

/**
 * 查询活动收藏状态
 * GET /api/v1/activities/{activityId}/favorite → { favorited: boolean, ... }
 */
export function getActivityFavoriteStatus(activityId) {
  return request({
    path: `/api/v1/activities/${activityId}/favorite`,
  })
}

/**
 * 添加收藏
 * PUT /api/v1/activities/{activityId}/favorite
 */
export function favoriteActivity(activityId) {
  return request({
    path: `/api/v1/activities/${activityId}/favorite`,
    method: "PUT",
  })
}

/**
 * 取消收藏
 * DELETE /api/v1/activities/{activityId}/favorite
 */
export function unfavoriteActivity(activityId) {
  return request({
    path: `/api/v1/activities/${activityId}/favorite`,
    method: "DELETE",
  })
}

/**
 * 我收藏的活动列表
 */
export function getMyFavoriteActivities(params = {}) {
  const query = new URLSearchParams()
  if (params.cursor) query.set("cursor", params.cursor)
  if (params.limit) query.set("limit", String(params.limit))
  const qs = query.toString()
  return request({
    path: `/api/v1/users/me/favorite-activities${qs ? "?" + qs : ""}`,
  })
}
