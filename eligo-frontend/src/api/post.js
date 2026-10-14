import { request, upload } from "./request"

/**
 * 创建我自己的帖子
 * POST /api/v1/users/me/posts
 * 注意：后端需要 Idempotency-Key header 防重放
 */
export function createPost(data) {
  const idempotencyKey = `post-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
  return request({
    path: "/api/v1/users/me/posts",
    method: "POST",
    data,
    headers: {
      "Idempotency-Key": idempotencyKey,
    },
  })
}

/**
 * 上传图片到文件服务
 * POST /api/v1/files/images（multipart/form-data）
 * @param {string} filePath - 本地文件路径
 * @param {string} purpose - 用途：AVATAR / POST / ACTIVITY_COVER 等
 * @returns {Promise<{fileId: string, url: string, ...}>}
 */
export function uploadImage(filePath, purpose = "POST") {
  return upload({
    path: "/api/v1/files/images",
    filePath,
    name: "file",
    formData: { purpose },
  })
}
