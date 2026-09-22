import { request } from "./request"

/**
 * 社交关系模块接口（关注、粉丝、用户发现）
 * 注意：不含 IM 私信功能（产品规划明确不做）
 * TODO: 后端 M4 模块实现后启用
 */

/**
 * 关注用户或组织
 * @param {string} targetId - 被关注的用户/组织 ID
 */
export function followUser(targetId) {
  return request({
    path: `/api/v1/follows/${targetId}`,
    method: "POST",
  })
}

/**
 * 取消关注
 * @param {string} targetId - 被取消关注的用户/组织 ID
 */
export function unfollowUser(targetId) {
  return request({
    path: `/api/v1/follows/${targetId}`,
    method: "DELETE",
  })
}

/**
 * 获取我的关注列表
 * @param {Object} [params]
 * @param {number} [params.page]
 * @param {number} [params.pageSize]
 */
export function getMyFollowings(params = {}) {
  const query = new URLSearchParams()
  if (params.page) query.set("page", String(params.page))
  if (params.pageSize) query.set("pageSize", String(params.pageSize))

  const qs = query.toString()
  return request({
    path: `/api/v1/users/me/followings${qs ? "?" + qs : ""}`,
  })
}

/**
 * 获取我的粉丝列表
 * @param {Object} [params]
 * @param {number} [params.page]
 * @param {number} [params.pageSize]
 */
export function getMyFollowers(params = {}) {
  const query = new URLSearchParams()
  if (params.page) query.set("page", String(params.page))
  if (params.pageSize) query.set("pageSize", String(params.pageSize))

  const qs = query.toString()
  return request({
    path: `/api/v1/users/me/followers${qs ? "?" + qs : ""}`,
  })
}

/**
 * 获取指定用户的公开资料
 * @param {string} userId - 用户 ID
 */
export function getUserProfile(userId) {
  return request({
    path: `/api/v1/users/${userId}/profile`,
  })
}

/**
 * 获取指定用户的动态列表
 * @param {string} userId - 用户 ID
 * @param {Object} [params]
 * @param {number} [params.page]
 * @param {number} [params.pageSize]
 */
export function getUserDynamics(userId, params = {}) {
  const query = new URLSearchParams()
  if (params.page) query.set("page", String(params.page))
  if (params.pageSize) query.set("pageSize", String(params.pageSize))

  const qs = query.toString()
  return request({
    path: `/api/v1/users/${userId}/dynamics${qs ? "?" + qs : ""}`,
  })
}

/**
 * 发现页：获取推荐用户（同城/同好）
 * @param {Object} [params]
 * @param {string} [params.location] - 按地址筛选
 * @param {string[]} [params.interestTags] - 按兴趣标签筛选
 */
export function discoverUsers(params = {}) {
  const query = new URLSearchParams()
  if (params.location) query.set("location", params.location)
  if (params.interestTags && params.interestTags.length) {
    query.set("interestTags", params.interestTags.join(","))
  }

  const qs = query.toString()
  return request({
    path: `/api/v1/users/discover${qs ? "?" + qs : ""}`,
  })
}
