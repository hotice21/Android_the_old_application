<script setup>
import { ref, onMounted } from "vue"
import { request } from "../../../api/request"

const items = ref([])
const loading = ref(true)
const error = ref("")

function formatTime(isoStr) {
  if (!isoStr) return ""
  const d = new Date(isoStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`
}

function goBack() {
  uni.navigateBack({ fail: () => uni.reLaunch({ url: "/pages/mine/index" }) })
}

function openDetail(item) {
  uni.navigateTo({ url: `/pages/detail/index?id=${item.activityId}` })
}

async function loadList() {
  loading.value = true
  error.value = ""
  try {
    const data = await request({ path: "/api/v1/users/me/participations" })
    items.value = data?.items || Array.isArray(data) ? (Array.isArray(data) ? data : []) : []
    if (data?.items) items.value = data.items
  } catch (err) {
    error.value = err?.message || "加载失败"
  } finally {
    loading.value = false
  }
}

onMounted(loadList)
</script>

<template>
  <view class="page">
    <!-- 自定义导航栏 -->
    <view class="nav">
      <view class="nav__back" @tap="goBack">
        <text class="nav__back-icon">‹</text>
      </view>
      <text class="nav__title">我报名的活动</text>
    </view>

    <scroll-view class="content" scroll-y>
      <view v-if="loading" class="state">
        <text class="state__text">加载中...</text>
      </view>

      <view v-else-if="error" class="state">
        <text class="state__text">{{ error }}</text>
        <view class="state__btn" @tap="loadList">重新加载</view>
      </view>

      <view v-else-if="items.length === 0" class="state">
        <view class="state__empty-icon"><text class="state__empty-glyph">🎯</text></view>
        <text class="state__text">还没报名活动</text>
      </view>

      <view v-else class="list">
        <view
          v-for="item in items"
          :key="item.activityId"
          class="card"
          @tap="openDetail(item)"
        >
          <view class="card__photo" />
          <view class="card__body">
            <text class="card__title">{{ item.title || "活动" }}</text>
            <view class="card__meta">
              <text class="card__meta-icon">🕒</text>
              <text class="card__meta-text">{{ formatTime(item.startsAt) }}</text>
            </view>
            <view class="card__meta">
              <text class="card__meta-icon">📍</text>
              <text class="card__meta-text">{{ item.placeName || "地点待定" }}</text>
            </view>
            <view class="card__footer">
              <text class="card__joined">{{ item.participantCount || 0 }} 人已报名</text>
              <text class="card__arrow">›</text>
            </view>
          </view>
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
  gap: 14px;
}

.card {
  display: flex;
  flex-direction: row;
  background: #ffffff;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
}

.card__photo {
  width: 110px;
  height: 110px;
  flex-shrink: 0;
  background: linear-gradient(135deg, #ffb149 0%, #f56551 100%);
}

.card__body {
  flex: 1;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.card__title {
  font-size: 17px;
  font-weight: 600;
  color: #1a1a1a;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-height: 22px;
}

.card__meta {
  display: flex;
  align-items: center;
  gap: 4px;
}

.card__meta-icon {
  font-size: 13px;
  color: #999999;
}

.card__meta-text {
  font-size: 13px;
  color: #666666;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
}

.card__joined {
  font-size: 13px;
  color: #f56551;
  font-weight: 500;
}

.card__arrow {
  font-size: 22px;
  color: #c0c4cc;
}

.bottom-space {
  height: 32px;
}
</style>
