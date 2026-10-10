import { request } from "./request"

/** 获取我的所有会话（按最后消息时间倒序） */
export function getMyConversations() {
  return request({ path: "/api/v1/chat/conversations" })
}

/** 获取某个会话的消息列表 */
export function getConversationMessages(conversationId, cursor = null, limit = 50) {
  const query = new URLSearchParams()
  query.set("limit", String(limit))
  if (cursor) query.set("cursor", cursor)
  const qs = query.toString()
  return request({
    path: `/api/v1/chat/conversations/${conversationId}/messages${qs ? "?" + qs : ""}`,
  })
}

/** 发送消息 */
export function sendMessage(conversationId, { content, messageType = 1, fileId = null, voiceFileId = null, voiceDurationSeconds = null }) {
  return request({
    path: `/api/v1/chat/conversations/${conversationId}/messages`,
    method: "POST",
    data: { content, messageType, fileId, voiceFileId, voiceDurationSeconds },
  })
}
