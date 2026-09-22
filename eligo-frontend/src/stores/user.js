import { reactive, computed } from "vue"
import { getMyProfile } from "../api/profile"
import { getSession, getProfile, saveProfile, clearSession } from "./session"

/**
 * 用户全局状态管理
 * 管理当前登录用户的资料、登录态、关注/粉丝数等
 */
const state = reactive({
  /** 用户资料 */
  profile: null,
  /** 是否已登录 */
  isLoggedIn: false,
  /** 是否正在加载 */
  loading: false,
})

/**
 * 初始化用户状态（应用启动时调用）
 */
function init() {
  const session = getSession()
  const cachedProfile = getProfile()

  state.isLoggedIn = !!(session && session.accessToken)
  state.profile = cachedProfile || null
}

/**
 * 从服务器刷新用户资料
 */
async function refreshProfile() {
  if (!state.isLoggedIn) return

  state.loading = true
  try {
    const data = await getMyProfile()
    if (data) {
      state.profile = data
      saveProfile(data)
    }
  } catch {
    // 使用本地缓存
  } finally {
    state.loading = false
  }
}

/**
 * 登录成功后更新状态
 * @param {Object} profile - 用户资料对象
 */
function onLoginSuccess(profile) {
  state.isLoggedIn = true
  if (profile) {
    state.profile = profile
    saveProfile(profile)
  }
}

/**
 * 登出
 */
function onLogout() {
  state.isLoggedIn = false
  state.profile = null
  clearSession()
}

/**
 * 局部更新资料
 * @param {Object} patch - 需更新的字段
 */
function updateProfile(patch) {
  if (state.profile) {
    state.profile = { ...state.profile, ...patch }
    saveProfile(state.profile)
  }
}

/** 计算属性：用户昵称 */
const nickname = computed(() => state.profile?.nickname || "用户")

/** 计算属性：用户头像 */
const avatar = computed(
  () => state.profile?.avatar || "/static/eligo/photos/avatar-placeholder.png",
)

export function useUserStore() {
  return {
    state,
    init,
    refreshProfile,
    onLoginSuccess,
    onLogout,
    updateProfile,
    nickname,
    avatar,
  }
}
