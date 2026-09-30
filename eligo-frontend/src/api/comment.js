import { request } from "./request"

/**
 * 活动评论接口（对应 ActivityCommentController）
 */

/**
 * 获取活动评论列表（CursorPage 格式）
 * @param {string|number} activityId
 */
export function getActivityComments(activityId, params = {}) {
  const query = new URLSearchParams()
  if (params.cursor) query.set("cursor", params.cursor)
  if (params.limit) query.set("limit", String(params.limit))
  const qs = query.toString()
  return request({
    path: `/api/v1/activities/${activityId}/comments${qs ? "?" + qs : ""}`,
    auth: false,
  })
}

/**
 * 发表评论
 * @param {string|number} activityId
 * @param {{ content: string, parentCommentId?: number }} data
 */
export function postComment(activityId, data) {
  return request({
    path: `/api/v1/activities/${activityId}/comments`,
    method: "POST",
    data,
    headers: {
      // 后端要求幂等键
      "Idempotency-Key": `comment-${activityId}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`,
    },
  })
}

/**
 * 删除自己的评论
 */
export function deleteComment(activityId, commentId) {
  return request({
    path: `/api/v1/activities/${activityId}/comments/${commentId}`,
    method: "DELETE",
  })
}
