const SESSION_STORAGE_KEY = "eligo.session.v1"
const INSTALLATION_STORAGE_KEY = "eligo.installation-id.v1"
const PROFILE_STORAGE_KEY = "eligo.profile.v1"

function createInstallationId() {
  const timestamp = Date.now().toString(36)
  const random = Array.from({ length: 4 }, () =>
    Math.random().toString(36).slice(2, 10),
  ).join("")
  return `eligo-${timestamp}-${random}`
}

export function getSession() {
  const value = uni.getStorageSync(SESSION_STORAGE_KEY)
  return value && typeof value === "object" ? value : null
}

export function saveSession(session) {
  uni.setStorageSync(SESSION_STORAGE_KEY, session)
  return session
}

export function mergeSession(patch) {
  return saveSession({ ...(getSession() || {}), ...patch })
}

export function clearSession() {
  uni.removeStorageSync(SESSION_STORAGE_KEY)
  uni.removeStorageSync(PROFILE_STORAGE_KEY)
}

export function getAccessToken() {
  return getSession()?.accessToken || ""
}

export function getRefreshToken() {
  return getSession()?.refreshToken || ""
}

export function getOrCreateInstallationId() {
  const existing = uni.getStorageSync(INSTALLATION_STORAGE_KEY)
  if (typeof existing === "string" && existing.length >= 16) {
    return existing
  }

  const installationId = createInstallationId()
  uni.setStorageSync(INSTALLATION_STORAGE_KEY, installationId)
  return installationId
}

export function saveProfile(profile) {
  uni.setStorageSync(PROFILE_STORAGE_KEY, profile)
  return profile
}

export function getProfile() {
  const value = uni.getStorageSync(PROFILE_STORAGE_KEY)
  return value && typeof value === "object" ? value : null
}
