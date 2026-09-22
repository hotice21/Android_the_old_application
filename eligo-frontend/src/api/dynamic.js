import { request } from "./request"

/**
 * 社区动态模块接口
 * TODO: 后端 M4 模块实现后启用
 */

/**
 * 分页获取动态列表
 * @param {Object} [params]
 * @param {number} [params.page] - 当前页码
 * @param {number} [params.pageSize] - 每页数量
 * @param {'recommend'|'following'} [params.type] - 推荐流 / 关注流
 */
export function getDynamics(params = {}) {
  const query = new URLSearchParams()
  if (params.page) query.set("page", String(params.page))
  if (params.pageSize) query.set("pageSize", String(params.pageSize))
  if (params.type) query.set("type", params.type)

  const qs = query.toString()
  return request({
    path: `/api/v1/dynamics${qs ? "?" + qs : ""}`,
  })
}

/**
 * 获取动态详情
 * @param {string} id - 动态 ID
 */
export function getDynamicDetail(id) {
  return request({ path: `/api/v1/dynamics/${id}` })
}

/**
 * 发布动态
 * @param {Object} data
 * @param {string} data.content - 动态文字正文
 * @param {string[]} data.images - 配图 URL 数组（1-9 张）
 * @param {string} [data.activityId] - 关联活动 ID（可选）
 */
export function publishDynamic(data) {
  return request({
    path: "/api/v1/dynamics",
    method: "POST",
    data,
  })
}

/**
 * 切换动态点赞状态
 * @param {string} id - 动态 ID
 */
export function toggleDynamicLike(id) {
  return request({
    path: `/api/v1/dynamics/${id}/like`,
    method: "POST",
  })
}

/**
 * 切换动态收藏状态
 * @param {string} id - 动态 ID
 */
export function toggleDynamicFavorite(id) {
  return request({
    path: `/api/v1/dynamics/${id}/favorite`,
    method: "POST",
  })
}

/**
 * 获取动态评论列表
 * @param {string} id - 动态 ID
 * @param {Object} [params]
 * @param {number} [params.page]
 * @param {number} [params.pageSize]
 */
export function getDynamicComments(id, params = {}) {
  const query = new URLSearchParams()
  if (params.page) query.set("page", String(params.page))
  if (params.pageSize) query.set("pageSize", String(params.pageSize))

  const qs = query.toString()
  return request({
    path: `/api/v1/dynamics/${id}/comments${qs ? "?" + qs : ""}`,
  })
}

/**
 * 发布评论
 * @param {string} id - 动态 ID
 * @param {string} content - 评论正文
 */
export function publishComment(id, content) {
  return request({
    path: `/api/v1/dynamics/${id}/comments`,
    method: "POST",
    data: { content },
  })
}

/**
 * 删除动态
 * @param {string} id - 动态 ID
 */
export function deleteDynamic(id) {
  return request({
    path: `/api/v1/dynamics/${id}`,
    method: "DELETE",
  })
}
