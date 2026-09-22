let fallbackApiBaseUrl = ""

// #ifdef MP-WEIXIN
fallbackApiBaseUrl = "http://127.0.0.1:8080"
// #endif

const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL || fallbackApiBaseUrl
const configuredProfileDataMode = import.meta.env.VITE_PROFILE_DATA_MODE

export const API_BASE_URL = configuredApiBaseUrl.replace(/\/+$/, "")
export const APP_VERSION = "1.0.0"
export const PROFILE_DATA_MODE = configuredProfileDataMode === "mock" ? "mock" : "api"
export const PROFILE_MOCK_ENABLED = PROFILE_DATA_MODE === "mock"
