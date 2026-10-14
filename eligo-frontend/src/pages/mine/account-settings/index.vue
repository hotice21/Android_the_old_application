<script setup>
import { ref } from "vue"
import { request } from "../../../api/request"
import { clearSession, getAccessToken } from "../../../stores/session"

const loading = ref(false)

function goBack() {
  uni.navigateBack({ fail: () => uni.reLaunch({ url: "/pages/mine/index" }) })
}

/** 退出登录：先调后端 /auth/logout，再清本地 storage */
async function logout() {
  uni.showModal({
    title: "提示",
    content: "确定要退出登录吗？",
    confirmColor: "#d9402a",
    success: async (res) => {
      if (!res.confirm) return
      loading.value = true
      try {
        // 告诉后端让当前 token 失效
        if (getAccessToken()) {
          try {
            await request({
              path: "/api/v1/auth/logout",
              method: "POST",
            })
          } catch {
            // 后端可能已断网，忽略这个错误，本地照常清
          }
        }
        clearSession()
        uni.showToast({ title: "已退出登录", icon: "success" })
        setTimeout(() => {
          uni.reLaunch({ url: "/pages/onboarding/index" })
        }, 500)
      } finally {
        loading.value = false
      }
    },
  })
}

/** 清除本地 storage 里的所有 key（除了 installation-id） */
function clearCache() {
  uni.showModal({
    title: "提示",
    content: "确定清除本地缓存？不会清除登录状态",
    success: (res) => {
      if (!res.confirm) return
      try {
        const info = uni.getStorageInfoSync()
        const keepKeys = ["eligo.session.v1", "eligo.profile.v1", "eligo.installation-id.v1"]
        let cleared = 0
        for (const key of info.keys) {
          if (!keepKeys.includes(key)) {
            uni.removeStorageSync(key)
            cleared++
          }
        }
        uni.showToast({ title: `已清除 ${cleared} 项`, icon: "success" })
      } catch (e) {
        uni.showToast({ title: "清除失败", icon: "none" })
      }
    },
  })
}

/** 申请数据导出：调 POST /api/v1/account/data-exports */
async function exportMyData() {
  loading.value = true
  try {
    await request({
      path: "/api/v1/account/data-exports",
      method: "POST",
      data: { format: "JSON" },
    })
    uni.showToast({
      title: "导出申请已提交 ✅",
      icon: "none",
      duration: 2500,
    })
  } catch (e) {
    uni.showToast({ title: e?.message || "申请失败", icon: "none" })
  } finally {
    loading.value = false
  }
}

/** 注销账号：双重确认 + POST /api/v1/account/deactivation + 清本地 + 跳 onboarding */
async function deactivateAccount() {
  // 第一次确认
  const r1 = await new Promise((resolve) => {
    uni.showModal({
      title: "⚠️ 账号注销",
      content: "注销后你的所有活动、帖子、评论、报名记录将被标记为匿名数据且无法恢复。真的继续吗？",
      confirmText: "继续注销",
      confirmColor: "#d9402a",
      success: resolve,
      fail: () => resolve({ confirm: false }),
    })
  })
  if (!r1.confirm) return

  // 第二次确认（输入"注销"确认）
  const r2 = await new Promise((resolve) => {
    uni.showModal({
      title: "最后确认",
      editable: true,
      placeholderText: "请输入「注销」确认",
      confirmText: "立即注销",
      confirmColor: "#d9402a",
      success: resolve,
      fail: () => resolve({ confirm: false }),
    })
  })
  if (!r2.confirm) return
  if ((r2.content || "").trim() !== "注销") {
    uni.showToast({ title: "输入不正确", icon: "none" })
    return
  }

  loading.value = true
  try {
    await request({
      path: "/api/v1/account/deactivation",
      method: "POST",
      data: { reason: "USER_REQUEST" },
    })
    clearSession()
    uni.showToast({ title: "账号已注销", icon: "success" })
    setTimeout(() => {
      uni.reLaunch({ url: "/pages/onboarding/index" })
    }, 800)
  } catch (e) {
    uni.showToast({ title: e?.message || "操作失败", icon: "none" })
  } finally {
    loading.value = false
  }
}

function showAbout() {
  uni.showModal({
    title: "关于 Eligo",
    content: "Eligo 适老化活动社交平台 v1.0.0\n毕业设计作品\n\n后端：Spring Boot 4.1 + MyBatis-Plus\n前端：uni-app + Vue 3\n数据库：MySQL 8.4",
    showCancel: false,
    confirmText: "好的",
  })
}
</script>

<template>
  <view class="page">
    <view class="nav">
      <view class="nav__back" @tap="goBack">
        <text class="nav__back-icon">‹</text>
      </view>
      <text class="nav__title">账号设置</text>
    </view>

    <scroll-view class="content" scroll-y>
      <!-- 数据与隐私 -->
      <view class="card">
        <view class="card__title">数据与隐私</view>
        <view class="entry" @tap="exportMyData">
          <text class="entry__icon">📦</text>
          <view class="entry__body">
            <text class="entry__label">导出我的数据</text>
            <text class="entry__sub">下载你的活动/帖子/评论记录</text>
          </view>
          <text class="entry__arrow">›</text>
        </view>
        <view class="divider" />
        <view class="entry" @tap="clearCache">
          <text class="entry__icon">🧹</text>
          <view class="entry__body">
            <text class="entry__label">清除本地缓存</text>
            <text class="entry__sub">保留登录状态</text>
          </view>
          <text class="entry__arrow">›</text>
        </view>
      </view>

      <!-- 关于 -->
      <view class="card">
        <view class="card__title">其他</view>
        <view class="entry" @tap="showAbout">
          <text class="entry__icon">ℹ️</text>
          <text class="entry__label">关于 Eligo</text>
          <text class="entry__arrow">›</text>
        </view>
      </view>

      <!-- 危险操作区 -->
      <view class="card">
        <view class="entry entry--danger" @tap="logout">
          <text class="entry__icon">🚪</text>
          <text class="entry__label">退出登录</text>
          <text class="entry__arrow">›</text>
        </view>
        <view class="divider" />
        <view class="entry entry--danger entry--danger-strong" @tap="deactivateAccount">
          <text class="entry__icon">⚠️</text>
          <text class="entry__label">注销账号</text>
          <text class="entry__arrow">›</text>
        </view>
      </view>

      <view class="footer-tip">
        <text class="footer-tip__text">© Eligo {{ new Date().getFullYear() }}</text>
      </view>
    </scroll-view>

    <!-- loading 遮罩 -->
    <view v-if="loading" class="loading-mask">
      <view class="loading-mask__box">
        <text class="loading-mask__spinner">⏳</text>
      </view>
    </view>
  </view>
</template>

<style scoped>
.page {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f6f7fb;
  font-family: "PingFang SC", sans-serif;
}

.nav {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 50px 16px 14px;
  background: #ffffff;
  border-bottom: 1px solid #f0f1f4;
  flex-shrink: 0;
}

.nav__back {
  position: absolute;
  left: 16px;
  top: 50px;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav__back-icon {
  font-size: 28px;
  color: #ffffff;
  line-height: 1;
  margin-top: -2px;
}

.nav__title {
  font-size: 20px;
  font-weight: 600;
  color: #111111;
}

.content {
  flex: 1;
  height: 0;
  padding: 16px;
}

.card {
  background: #ffffff;
  border-radius: 14px;
  overflow: hidden;
  margin-bottom: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.card__title {
  padding: 14px 16px 0;
  font-size: 13px;
  color: #888;
  font-weight: 500;
}

.entry {
  display: flex;
  flex-direction: row;
  align-items: center;
  min-height: 54px;
  padding: 14px 16px;
}

.entry__icon {
  font-size: 22px;
  margin-right: 12px;
  flex-shrink: 0;
}

.entry__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.entry__label {
  font-size: 16px;
  color: #1a1a1a;
}

.entry__sub {
  font-size: 12px;
  color: #999;
}

.entry--danger .entry__label {
  color: #d9402a;
  font-weight: 500;
}

.entry--danger-strong .entry__label {
  color: #a01a1a;
  font-weight: 600;
}

.entry__arrow {
  font-size: 22px;
  color: #c0c4cc;
}

.divider {
  height: 1px;
  margin-left: 50px;
  background: #f0f1f4;
}

.footer-tip {
  margin-top: 40px;
  text-align: center;
}

.footer-tip__text {
  font-size: 13px;
  color: #bbbbbb;
}

.loading-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
}

.loading-mask__box {
  width: 80px;
  height: 80px;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.75);
  display: flex;
  align-items: center;
  justify-content: center;
}

.loading-mask__spinner {
  font-size: 32px;
}
</style>
