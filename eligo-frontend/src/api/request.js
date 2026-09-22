import { API_BASE_URL } from "../config/runtime"
import {
  clearSession,
  getAccessToken,
  getOrCreateInstallationId,
  getRefreshToken,
  saveSession,
} from "../stores/session"

export class ApiError extends Error {
  constructor(message, options = {}) {
    super(message)
    this.name = "ApiError"
    this.code = options.code ?? -1
    this.httpStatus = options.httpStatus ?? 0
    this.requestId = options.requestId || ""
    this.details = options.details ?? null
  }
}

function joinUrl(path) {
  if (/^https?:\/\//.test(path)) {
    return path
  }
  return `${API_BASE_URL}${path}`
}

function send(options) {
  return new Promise((resolve, reject) => {
    uni.request({
      ...options,
      success: resolve,
      fail(error) {
        reject(
          new ApiError(error?.errMsg || "无法连接后端服务", {
            code: "NETWORK_ERROR",
          }),
        )
      },
    })
  })
}

function sendUpload(options) {
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      ...options,
      success(response) {
        let data = response.data
        if (typeof data === "string") {
          try {
            data = JSON.parse(data)
          } catch {
            // 交给统一响应校验生成 INVALID_RESPONSE。
          }
        }
        resolve({ ...response, data })
      },
      fail(error) {
        reject(
          new ApiError(error?.errMsg || "无法上传文件", {
            code: "NETWORK_ERROR",
          }),
        )
      },
    })
  })
}

function sendDownload(options) {
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      ...options,
      success: resolve,
      fail(error) {
        reject(
          new ApiError(error?.errMsg || "无法下载文件", {
            code: "NETWORK_ERROR",
          }),
        )
      },
    })
  })
}

function toApiError(response) {
  const body = response?.data
  const isEnvelope = body && typeof body === "object" && "code" in body
  return new ApiError(
    (isEnvelope && body.message) || `请求失败（HTTP ${response?.statusCode || 0}）`,
    {
      code: isEnvelope ? body.code : "HTTP_ERROR",
      httpStatus: response?.statusCode,
      requestId: isEnvelope ? body.requestId : "",
      details: isEnvelope ? body.data : body,
    },
  )
}

function unwrap(response) {
  if (response.statusCode < 200 || response.statusCode >= 300) {
    throw toApiError(response)
  }

  const body = response.data
  if (!body || typeof body !== "object" || !("code" in body)) {
    throw new ApiError("后端响应不符合统一 Result 格式", {
      code: "INVALID_RESPONSE",
      httpStatus: response.statusCode,
      details: body,
    })
  }

  if (body.code !== 0) {
    throw toApiError(response)
  }

  return body.data
}

function buildHeaders(
  auth,
  headers = {},
  accessToken = getAccessToken(),
  contentType = "application/json",
) {
  const result = {
    Accept: "application/json",
    ...headers,
  }
  if (contentType) {
    result["Content-Type"] = contentType
  }
  if (auth && accessToken) {
    result.Authorization = `Bearer ${accessToken}`
  }
  return result
}

let refreshPromise = null

async function refreshLoginSession() {
  if (refreshPromise) {
    return refreshPromise
  }

  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    clearSession()
    throw new ApiError("登录状态已失效，请重新登录", {
      code: 10100,
      httpStatus: 401,
    })
  }

  refreshPromise = send({
    url: joinUrl("/api/v1/auth/refresh"),
    method: "POST",
    header: buildHeaders(false),
    data: {
      refreshToken,
      installationId: getOrCreateInstallationId(),
    },
  })
    .then(unwrap)
    .then(saveSession)
    .catch((error) => {
      clearSession()
      throw error
    })
    .finally(() => {
      refreshPromise = null
    })

  return refreshPromise
}

export async function request({
  path,
  method = "GET",
  data,
  auth = true,
  headers,
  retryOnUnauthorized = true,
}) {
  const requestAccessToken = auth ? getAccessToken() : ""
  const response = await send({
    url: joinUrl(path),
    method,
    data,
    header: buildHeaders(auth, headers, requestAccessToken),
  })

  if (
    auth &&
    retryOnUnauthorized &&
    response.statusCode === 401 &&
    getRefreshToken()
  ) {
    const currentAccessToken = getAccessToken()
    if (
      requestAccessToken &&
      currentAccessToken &&
      currentAccessToken !== requestAccessToken
    ) {
      return request({
        path,
        method,
        data,
        auth,
        headers,
        retryOnUnauthorized: false,
      })
    }

    await refreshLoginSession()
    return request({
      path,
      method,
      data,
      auth,
      headers,
      retryOnUnauthorized: false,
    })
  }

  return unwrap(response)
}

export async function upload({
  path,
  filePath,
  name = "file",
  formData,
  auth = true,
  headers,
  retryOnUnauthorized = true,
}) {
  const requestAccessToken = auth ? getAccessToken() : ""
  const response = await sendUpload({
    url: joinUrl(path),
    filePath,
    name,
    formData,
    header: buildHeaders(auth, headers, requestAccessToken, ""),
  })

  if (
    auth &&
    retryOnUnauthorized &&
    response.statusCode === 401 &&
    getRefreshToken()
  ) {
    const currentAccessToken = getAccessToken()
    if (
      requestAccessToken &&
      currentAccessToken &&
      currentAccessToken !== requestAccessToken
    ) {
      return upload({
        path,
        filePath,
        name,
        formData,
        auth,
        headers,
        retryOnUnauthorized: false,
      })
    }

    await refreshLoginSession()
    return upload({
      path,
      filePath,
      name,
      formData,
      auth,
      headers,
      retryOnUnauthorized: false,
    })
  }

  return unwrap(response)
}

export async function download({
  path,
  auth = true,
  headers,
  retryOnUnauthorized = true,
}) {
  const requestAccessToken = auth ? getAccessToken() : ""
  const response = await sendDownload({
    url: joinUrl(path),
    header: buildHeaders(auth, headers, requestAccessToken, ""),
  })

  if (
    auth &&
    retryOnUnauthorized &&
    response.statusCode === 401 &&
    getRefreshToken()
  ) {
    const currentAccessToken = getAccessToken()
    if (
      requestAccessToken &&
      currentAccessToken &&
      currentAccessToken !== requestAccessToken
    ) {
      return download({
        path,
        auth,
        headers,
        retryOnUnauthorized: false,
      })
    }

    await refreshLoginSession()
    return download({
      path,
      auth,
      headers,
      retryOnUnauthorized: false,
    })
  }

  if (response.statusCode < 200 || response.statusCode >= 300 || !response.tempFilePath) {
    throw new ApiError(`文件下载失败（HTTP ${response.statusCode || 0}）`, {
      code: "HTTP_ERROR",
      httpStatus: response.statusCode,
    })
  }

  return response.tempFilePath
}

export function formatApiError(error) {
  if (!(error instanceof ApiError)) {
    return error?.message || "发生未知错误"
  }

  const code = error.code !== -1 ? `错误码：${error.code}` : ""
  const requestId = error.requestId ? `请求ID：${error.requestId}` : ""
  return [error.message, code, requestId].filter(Boolean).join("\n")
}
