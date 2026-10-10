<script setup>
function goBack() {
  uni.navigateBack({ fail: () => uni.reLaunch({ url: "/pages/mine/index" }) })
}

function logout() {
  uni.showModal({
    title: "提示",
    content: "确定要退出登录吗？",
    success(res) {
      if (res.confirm) {
        uni.clearStorageSync()
        uni.reLaunch({ url: "/pages/onboarding/index" })
      }
    },
  })
}

function showAbout() {
  uni.showToast({ title: "Eligo v1.0.0", icon: "none" })
}

function clearCache() {
  uni.showToast({ title: "缓存已清除", icon: "success" })
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
      <view class="card">
        <view class="entry" @tap="clearCache">
          <text class="entry__icon">🧹</text>
          <text class="entry__label">清除缓存</text>
          <text class="entry__arrow">›</text>
        </view>
        <view class="divider" />
        <view class="entry" @tap="showAbout">
          <text class="entry__icon">ℹ️</text>
          <text class="entry__label">关于我们</text>
          <text class="entry__arrow">›</text>
        </view>
      </view>

      <view class="card">
        <view class="entry entry--danger" @tap="logout">
          <text class="entry__icon">🚪</text>
          <text class="entry__label">退出登录</text>
          <text class="entry__arrow">›</text>
        </view>
      </view>

      <view class="footer-tip">
        <text class="footer-tip__text">© Eligo {{ new Date().getFullYear() }}</text>
      </view>
    </scroll-view>
  </view>
</template>

<style scoped>
.page {
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
}

.entry__label {
  flex: 1;
  font-size: 16px;
  color: #1a1a1a;
}

.entry--danger .entry__label {
  color: #d9402a;
  font-weight: 500;
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
</style>
