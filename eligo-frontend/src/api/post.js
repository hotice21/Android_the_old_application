import { request } from "./request"

/** 创建帖子 */
export function createPost(data) {
  return request({ path: "/api/v1/posts", method: "POST", data })
}
