import { getAccessToken } from "./session"

const ENTRY_GUIDE_SEEN_KEY = "eligo.entry-guide-seen.v1"
const PLAZA_PAGE = "/pages/plaza/index"
const ONBOARDING_PAGE = "/pages/onboarding/index"
const INTERNAL_PAGE_PATTERN = /^\/pages\/[a-z0-9-]+\/index(?:\?[^#]*)?$/i

export function hasSeenEntryGuide() {
  return uni.getStorageSync(ENTRY_GUIDE_SEEN_KEY) === true
}

export function markEntryGuideSeen() {
  uni.setStorageSync(ENTRY_GUIDE_SEEN_KEY, true)
}

export function resetEntryGuideSeen() {
  uni.removeStorageSync(ENTRY_GUIDE_SEEN_KEY)
}

// 测试阶段开关：.env 中设置 VITE_DEV_SKIP_LOGIN=true 后，
// 前端所有登录拦截放行（后端请同步关闭校验），便于手动测试。
function isDevLoginSkipped() {
  return import.meta.env.VITE_DEV_SKIP_LOGIN === "true"
}

export function isLoggedIn() {
  return Boolean(getAccessToken()) || isDevLoginSkipped()
}

export function resolveEntryPage() {
  return isLoggedIn() || hasSeenEntryGuide() ? PLAZA_PAGE : ONBOARDING_PAGE
}

export function normalizePageUrl(value, fallback = PLAZA_PAGE) {
  if (typeof value !== "string" || !value) return fallback

  let decoded = value
  try {
    decoded = decodeURIComponent(value)
  } catch {
    return fallback
  }

  if (!INTERNAL_PAGE_PATTERN.test(decoded) || decoded.startsWith(ONBOARDING_PAGE)) {
    return fallback
  }
  return decoded
}

export function normalizeGuestPageUrl(value) {
  const pageUrl = normalizePageUrl(value)
  return pageUrl === PLAZA_PAGE || pageUrl.startsWith("/pages/detail/index")
    ? pageUrl
    : PLAZA_PAGE
}

export function getCurrentPageUrl(fallback = PLAZA_PAGE) {
  const pages = getCurrentPages()
  const current = pages[pages.length - 1]
  if (!current?.route) return fallback

  const query = Object.entries(current.options || {})
    .filter(([, value]) => value !== undefined && value !== null)
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
    .join("&")
  return normalizePageUrl(`/${current.route}${query ? `?${query}` : ""}`, fallback)
}

export function requireLogin(returnUrl = getCurrentPageUrl()) {
  if (isLoggedIn()) return true

  const safeReturnUrl = normalizePageUrl(returnUrl)
  uni.reLaunch({
    url: `${ONBOARDING_PAGE}?redirect=${encodeURIComponent(safeReturnUrl)}`,
  })
  return false
}
