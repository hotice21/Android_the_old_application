import { request } from "./request"

export function ping() {
  return request({
    path: "/api/v1/system/ping",
    auth: false,
  })
}
