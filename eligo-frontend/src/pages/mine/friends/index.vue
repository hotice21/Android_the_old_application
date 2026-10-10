<script setup>
import { ref, onMounted } from "vue"
import { request } from "../../../api/request"

const friends = ref([])
const loading = ref(true)
const error = ref("")

function goBack() {
  uni.navigateBack({ fail: () => uni.reLaunch({ url: "/pages/mine/index" }) })
}

function displayName(f) {
  return f.remarkName || f.nickname || f.userId || "用户"
}

function avatarChar(f) {
  const name = displayName(f)
  return name.charAt(0)
}

function openChat(f) {
  uni.navigateTo({ url: `/pages/chat-detail/index?convId=0&peerUserId=${f.userId}` })
}

function showAddFriend() {
  uni.showModal({
    title: "添加好友",
    editable: true,
    placeholderText: "请输入好友 userId",
    success(res) {
      if (res.confirm && res.content?.trim()) {
        uni.showToast({ title: "好友请求已发送（占位）", icon: "none" })
      }
    },
  })
}

async function loadFriends() {
  loading.value = true
  error.value = ""
  try {
    const data = await request({ path: "/api/v1/users/me/friends" })
    friends.value = Array.isArray(data) ? data : data?.items || []
  } catch (err) {
    error.value = err?.message || "加载失败"
  } finally {
    loading.value = false
  }
}

onMounted(loadFriends)
</script>

<template>
  <view class="page">
    <view class="nav">
      <view class="nav__back" @tap="goBack">
        <text class="nav__back-icon">‹</text>
      </view>
      <text class="nav__title">我的好友</text>
      <view class="nav__add" @tap="showAddFriend">
        <text class="nav__add-icon">+</text>
      </view>
    </view>

    <scroll-view class="content" scroll-y>
      <view v-if="loading" class="state">
        <text class="state__text">加载中...</text>
      </view>

      <view v-else-if="error" class="state">
        <text class="state__text">{{ error }}</text>
        <view class="state__btn" @tap="loadFriends">重新加载</view>
      </view>

      <view v-else-if="friends.length === 0" class="state">
        <view class="state__empty-icon"><text class="state__empty-glyph">👥</text></view>
        <text class="state__text">还没有好友</text>
      </view>

      <view v-else class="list">
        <view
          v-for="f in friends"
          :key="f.userId"
          class="friend-item"
          @tap="openChat(f)"
        >
          <view class="friend-item__avatar">
            <text class="friend-item__avatar-text">{{ avatarChar(f) }}</text>
          </view>
          <view class="friend-item__info">
            <text class="friend-item__name">{{ displayName(f) }}</text>
            <text v-if="f.remarkName" class="friend-item__sub">备注：{{ f.remarkName }}</text>
          </view>
          <text class="friend-item__arrow">›</text>
        </view>
      </view>
      <view class="bottom-space" />
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

.nav__add {
  position: absolute;
  right: 16px;
  top: 50px;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ffb149, #f56551);
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav__add-icon {
  font-size: 26px;
  color: #ffffff;
  line-height: 1;
  font-weight: 600;
}

.content {
  flex: 1;
  height: 0;
}

.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 100px 32px;
  gap: 12px;
}

.state__empty-icon {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: #eeeef0;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
}

.state__empty-glyph {
  font-size: 36px;
}

.state__text {
  font-size: 16px;
  color: #999999;
}

.state__btn {
  margin-top: 10px;
  padding: 8px 20px;
  border-radius: 16px;
  background: linear-gradient(135deg, #ffb149, #f56551);
  color: #ffffff;
  font-size: 14px;
}

.list {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.friend-item {
  display: flex;
  flex-direction: row;
  align-items: center;
  background: #ffffff;
  border-radius: 14px;
  padding: 14px 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.friend-item__avatar {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ffb149 0%, #f56551 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.friend-item__avatar-text {
  font-size: 22px;
  font-weight: 600;
  color: #ffffff;
}

.friend-item__info {
  flex: 1;
  margin-left: 14px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.friend-item__name {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
}

.friend-item__sub {
  font-size: 13px;
  color: #999999;
}

.friend-item__arrow {
  font-size: 22px;
  color: #c0c4cc;
}

.bottom-space {
  height: 32px;
}
</style>
