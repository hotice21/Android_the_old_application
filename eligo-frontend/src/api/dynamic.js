import { request } from "./request"

/** 分页获取公开动态列表（新到旧） */
export function getDynamics(params = {}) {
  const query = new URLSearchParams()
  if (params.cursor) query.set("cursor", params.cursor)
  if (params.limit) query.set("limit", String(params.limit))
  const qs = query.toString()
  return request({
    path: `/api/v1/posts${qs ? "?" + qs : ""}`,
  })
}

/** 推荐动态流 */
export function getRecommendedFeed(params = {}) {
  const query = new URLSearchParams()
  if (params.cursor) query.set("cursor", params.cursor)
  if (params.limit) query.set("limit", String(params.limit))
  const qs = query.toString()
  return request({
    path: `/api/v1/feed/recommended${qs ? "?" + qs : ""}`,
  })
}

/** 关注动态流 */
export function getFollowingFeed(params = {}) {
  const query = new URLSearchParams()
  if (params.cursor) query.set("cursor", params.cursor)
  if (params.limit) query.set("limit", String(params.limit))
  const qs = query.toString()
  return request({
    path: `/api/v1/feed/following${qs ? "?" + qs : ""}`,
  })
}

/** 获取动态详情 */
export function getDynamicDetail(id) {
  return request({ path: `/api/v1/posts/${id}` })
}

/** 切换动态点赞（有则取消，无则点赞） */
export async function toggleDynamicLike(id) {
  const current = await request({ path: `/api/v1/posts/${id}/like` })
  const liked = current?.liked === true
  if (liked) {
    await request({
      path: `/api/v1/posts/${id}/like`,
      method: "DELETE",
    })
    return false
  } else {
    await request({
      path: `/api/v1/posts/${id}/like`,
      method: "POST",
    })
    return true
  }
}
