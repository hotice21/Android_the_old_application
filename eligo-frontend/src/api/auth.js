import { APP_VERSION } from "../config/runtime"
import {
  clearSession,
  getOrCreateInstallationId,
  saveSession,
} from "../stores/session"
import { resetEntryGuideSeen } from "../stores/entry"
import { request } from "./request"

function getWechatCode() {
  return new Promise((resolve, reject) => {
    uni.login({
      provider: "weixin",
      timeout: 10000,
      success(result) {
        if (result.code) {
          resolve(result.code)
          return
        }
        reject(new Error("微信未返回登录凭证"))
      },
      fail(error) {
        reject(new Error(error?.errMsg || "无法获取微信登录凭证"))
      },
    })
  })
}

function buildLoginRequest(wechatCode) {
  const system = uni.getSystemInfoSync()
  const deviceName = String(system.model || system.brand || "WeChat device")
  const osVersion = String(system.system || system.osVersion || "")
  const regionCode = system.language ? String(system.language) : undefined

  return {
    wechatCode,
    installationId: getOrCreateInstallationId(),
    deviceName: deviceName.slice(0, 64),
    platform: "WECHAT_MINIPROGRAM",
    osVersion: osVersion.slice(0, 32),
    appVersion: APP_VERSION,
    regionCode: regionCode?.slice(0, 32),
  }
}

export function wechatLogin(data) {
  return request({
    path: "/api/v1/auth/wechat-login",
    method: "POST",
    data,
    auth: false,
  })
}

export async function loginWithWechat() {
  const code = await getWechatCode()
  const session = await wechatLogin(buildLoginRequest(code))
  return saveSession(session)
}

export async function logout() {
  try {
    await request({
      path: "/api/v1/auth/logout",
      method: "POST",
    })
  } finally {
    clearSession()
    resetEntryGuideSeen()
  }
}
