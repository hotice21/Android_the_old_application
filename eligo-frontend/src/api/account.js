import { request } from "./request"

export function bindPhone(phoneCode) {
  return request({
    path: "/api/v1/account/phone-binding",
    method: "PUT",
    data: { phoneCode },
  })
}
