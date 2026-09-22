import { request } from "./request"

/**
 * 活动模块接口
 * TODO: 后端 M2 模块实现后启用
 */

/**
 * 分页获取活动列表
 * @param {Object} params
 * @param {number} [params.page] - 当前页码
 * @param {number} [params.pageSize] - 每页数量，默认 10
 * @param {'official'|'partner'} [params.type] - 活动类型
 * @param {string} [params.category] - 活动分类
 * @param {'latest'|'hottest'} [params.sort] - 排序方式
 */
export function getActivities(params = {}) {
  const query = new URLSearchParams()
  if (params.page) query.set("page", String(params.page))
  if (params.pageSize) query.set("pageSize", String(params.pageSize))
  if (params.type) query.set("type", params.type)
  if (params.category) query.set("category", params.category)
  if (params.sort) query.set("sort", params.sort)

  const qs = query.toString()
  return request({
    path: `/api/v1/activities${qs ? "?" + qs : ""}`,
  })
}

/**
 * 获取活动详情
 * @param {string} id - 活动 ID
 */
export function getActivityDetail(id) {
  return request({ path: `/api/v1/activities/${id}` })
}

/**
 * 切换活动收藏状态
 * @param {string} id - 活动 ID
 */
export function toggleActivityFavorite(id) {
  return request({
    path: `/api/v1/activities/${id}/favorite`,
    method: "POST",
  })
}

/**
 * 报名活动
 * @param {string} id - 活动 ID
 * @param {Object} [data]
 * @param {number} [data.count] - 报名人数，默认 1
 */
export function enrollActivity(id, data) {
  return request({
    path: `/api/v1/activities/${id}/enroll`,
    method: "POST",
    data,
  })
}

/**
 * 取消报名
 * @param {string} id - 活动 ID
 */
export function cancelEnrollment(id) {
  return request({
    path: `/api/v1/activities/${id}/enroll`,
    method: "DELETE",
  })
}

/**
 * 获取活动参与者列表
 * @param {string} id - 活动 ID
 * @param {Object} [params]
 * @param {number} [params.page]
 * @param {number} [params.pageSize]
 */
export function getActivityParticipants(id, params = {}) {
  const query = new URLSearchParams()
  if (params.page) query.set("page", String(params.page))
  if (params.pageSize) query.set("pageSize", String(params.pageSize))

  const qs = query.toString()
  return request({
    path: `/api/v1/activities/${id}/participants${qs ? "?" + qs : ""}`,
  })
}
