import { request } from "./request"

export function getCurrentAgreements(type) {
  const query = type ? `?type=${encodeURIComponent(type)}` : ""
  return request({
    path: `/api/v1/agreements/current${query}`,
    auth: false,
  })
}

export function getAgreement(agreementId) {
  return request({
    path: `/api/v1/agreements/${agreementId}`,
    auth: false,
  })
}

export function consentAgreement(agreementId) {
  return request({
    path: `/api/v1/agreements/${agreementId}/consents`,
    method: "POST",
  })
}
