<script setup>
import { onMounted, ref, computed } from "vue"
import BottomNavigation from "../../components/BottomNavigation.vue"
import { getRecommendedFeed, getFollowingFeed, toggleDynamicLike } from "../../api/dynamic"

const categories = [
  { code: "all", label: "全部" },
  { code: "coffee", label: "☕ 咖啡" },
  { code: "hiking", label: "🥾 徒步" },
  { code: "sports", label: "⚽ 运动" },
]
const activeCategory = ref("all")
const dynamics = ref([])
const loading = ref(false)
const refreshing = ref(false)
const now = ref(new Date())

// 每分钟更新一次系统时间
setInterval(() => { now.value = new Date() }, 60000)

const currentTime = computed(() => {
  const d = now.value
  const hh = String(d.getHours()).padStart(2, "0")
  const mm = String(d.getMinutes()).padStart(2, "0")
  const weekday = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"][d.getDay()]
  return `${d.getMonth() + 1}月${d.getDate()}日 ${weekday} ${hh}:${mm}`
})

/** 格式化 ISO 时间戳为易读格式 */
function formatTime(iso) {
  if (!iso) return ""
  const d = new Date(iso)
  if (isNaN(d.getTime())) return iso
  const now = new Date()
  const diffMs = now - d
  const diffMin = Math.floor(diffMs / 60000)
  const diffHr = diffMin / 60
  const diffDay = diffHr / 24
  if (diffMin < 1) return "刚刚"
  if (diffMin < 60) return diffMin + "分钟前"
  if (diffHr < 24) return Math.floor(diffHr) + "小时前"
  if (diffDay < 7) return Math.floor(diffDay) + "天前"
  // 同年显示月日，跨年显示完整日期
  if (d.getFullYear() === now.getFullYear()) {
    return `${d.getMonth() + 1}月${d.getDate()}日 ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`
  }
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`
}

function extractItems(data) {
  if (!data) return []
  if (Array.isArray(data)) return data
  if (Array.isArray(data.items)) return data.items
  if (Array.isArray(data.list)) return data.list
  return []
}

function normalizeDynamic(d) {
  const user = d.author || {}
  const authorName = user.nickname || user.displayName || user.name || "港漂小明"
  return {
    id: d.postId || d.dynamicId || d.id,
    content: d.content || d.text || "",
    images: Array.isArray(d.media) ? d.media : (Array.isArray(d.images) ? d.images : []),
    rawTime: d.publishedAt || d.createdAt || d.timeLabel || "",
    time: formatTime(d.publishedAt || d.createdAt || d.timeLabel || ""),
    isLiked: Boolean(d.liked || d.isLiked),
    likes: d.likeCount ?? d.likes ?? 0,
    comments: d.commentCount ?? d.comments ?? 0,
    user: {
      name: authorName,
      isOfficial: Boolean(user.verified || user.isOfficial),
    },
  }
}

async function fetchDynamics(reset = false) {
  loading.value = true
  try {
    // 先尝试推荐 feed，失败回退 posts 列表
    let raw
    try {
      raw = await getRecommendedFeed({ limit: 20 })
    } catch {
      raw = await getFollowingFeed({ limit: 20 })
    }
    dynamics.value = extractItems(raw).map(normalizeDynamic)
  } catch (e) {
    console.error("加载动态失败", e)
    uni.showToast({ title: "加载失败", icon: "none" })
    dynamics.value = []
  } finally {
    loading.value = false
  }
}

async function onRefresh() {
  refreshing.value = true
  await fetchDynamics(true)
  refreshing.value = false
}

function onCategoryChange(code) {
  activeCategory.value = code
}

async function onLike(item) {
  const previousLiked = item.isLiked
  const previousLikes = item.likes
  try {
    const result = await toggleDynamicLike(item.id)
    // result 可能是 boolean 或 { liked: boolean }
    const newLiked = typeof result === "boolean" ? result : !previousLiked
    item.isLiked = newLiked
    item.likes = Math.max(0, (item.likes || 0) + (newLiked ? 1 : -1))
  } catch (e) {
    console.error("点赞失败", e)
    item.isLiked = previousLiked
    item.likes = previousLikes
    uni.showToast({ title: "操作失败", icon: "none" })
  }
}

function onComment(item) {
  uni.showToast({ title: "评论开发中", icon: "none" })
}

function onShare(item) {
  uni.showToast({ title: "分享开发中", icon: "none" })
}

function goCreate() {
  uni.navigateTo({ url: "/pages/post-create/index" })
}

onMounted(() => {
  fetchDynamics()
})
</script>

<template>
  <view class="dynamic">
    <!-- 顶部：系统时间 -->
    <view class="dynamic__header">
      <view class="dynamic__time-row">
        <text class="dynamic__clock">🕐</text>
        <text class="dynamic__time">{{ currentTime }}</text>
      </view>
    </view>

    <!-- 分类筛选 -->
    <view class="dynamic__categories">
      <button
        v-for="cat in categories"
        :key="cat.code"
        class="dynamic__cat"
        :class="{ 'dynamic__cat--active': activeCategory === cat.code }"
        @tap="onCategoryChange(cat.code)"
      >
        <text>{{ cat.label }}</text>
      </button>
    </view>

    <scroll-view
      class="dynamic__content"
      scroll-y
      :show-scrollbar="false"
      refresher-enabled
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
    >
      <view v-if="loading && dynamics.length === 0" class="dynamic__loading">
        <text class="dynamic__loading-text">加载中...</text>
      </view>

      <view v-else-if="dynamics.length === 0" class="dynamic__empty">
        <text class="dynamic__empty-text">暂无动态，快来发布吧！</text>
      </view>

      <view v-else class="dynamic__list">
        <view
          v-for="(item, index) in dynamics"
          :key="item.id"
          class="dynamic-card"
        >
          <view class="dynamic-card__header">
            <view class="dynamic-card__avatar" :class="'dynamic-card__avatar--' + ((index % 6) + 1)">
              <text class="dynamic-card__avatar-text">{{ item.user.name.charAt(0) }}</text>
            </view>
            <view class="dynamic-card__user">
              <view class="dynamic-card__name-row">
                <text class="dynamic-card__name">{{ item.user.name }}</text>
                <view v-if="item.user.isOfficial" class="dynamic-card__badge">官方</view>
              </view>
              <text class="dynamic-card__time">{{ item.time }}</text>
            </view>
          </view>

          <text class="dynamic-card__content">{{ item.content }}</text>

          <view
            v-if="item.images && item.images.length"
            class="dynamic-card__images"
          >
            <view
              v-for="(img, idx) in item.images.slice(0, 4)"
              :key="idx"
              class="dynamic-card__image"
              :class="'dynamic-card__image--' + (idx % 5)"
            />
          </view>

          <view class="dynamic-card__actions">
            <button
              class="dynamic-card__action"
              :class="{ 'dynamic-card__action--liked': item.isLiked }"
              @tap="onLike(item)"
            >
              <text class="action-icon">{{ item.isLiked ? '❤️' : '🤍' }}</text>
              <text class="action-count">{{ item.likes }}</text>
            </button>
            <button class="dynamic-card__action" @tap="onComment(item)">
              <text class="action-icon">💬</text>
              <text class="action-count">{{ item.comments }}</text>
            </button>
            <button class="dynamic-card__action" @tap="onShare(item)">
              <text class="action-icon">↗</text>
              <text class="action-count">分享</text>
            </button>
          </view>
        </view>
      </view>
    </scroll-view>

    <!-- 浮动 + 号创建按钮 -->
    <view class="fab" @tap="goCreate">
      <text class="fab__icon">+</text>
    </view>

    <BottomNavigation current="post" />
  </view>
</template>

<style scoped>
.dynamic {
  position: fixed;
  inset: 0;
  display: flex;
  flex-direction: column;
  background: #f6f7fb;
}

/* 系统时间 header */
.dynamic__header {
  padding: 48px 20px 12px;
  background: #ffffff;
}

.dynamic__time-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dynamic__clock {
  font-size: 20px;
}

.dynamic__time {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
}

/* 分类筛选 */
.dynamic__categories {
  display: flex;
  flex-direction: row;
  gap: 10px;
  padding: 8px 20px 14px;
  background: #ffffff;
  border-bottom: 1px solid #eef0f3;
}

.dynamic__cat {
  font-size: 16px;
  color: #6b7280;
  background: #f3f4f6;
  border: none;
  padding: 6px 16px;
  border-radius: 20px;
  line-height: 1.4;
  min-height: 36px;
  display: flex;
  align-items: center;
  font-weight: 500;
}

.dynamic__cat::after { border: none; }

.dynamic__cat--active {
  color: #ffffff;
  background: linear-gradient(140deg, #f56551, #ffb149);
  font-weight: 600;
}

.dynamic__content {
  flex: 1;
  padding-bottom: 100px;
}

.dynamic__loading,
.dynamic__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: 120px;
}

.dynamic__loading-text,
.dynamic__empty-text {
  font-size: 16px;
  color: #6b7280;
}

.dynamic__list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 10px 0;
}

.dynamic-card {
  background: #ffffff;
  padding: 20px;
  margin: 0 12px;
  border-radius: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}

.dynamic-card__header {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-bottom: 14px;
}

.dynamic-card__avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  margin-right: 12px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.dynamic-card__avatar--1 { background: #ff6b6b; }
.dynamic-card__avatar--2 { background: #4ecdc4; }
.dynamic-card__avatar--3 { background: #45b7d1; }
.dynamic-card__avatar--4 { background: #f9a826; }
.dynamic-card__avatar--5 { background: #a78bfa; }
.dynamic-card__avatar--6 { background: #51cf66; }

.dynamic-card__avatar-text {
  font-size: 20px;
  font-weight: 600;
  color: #ffffff;
}

.dynamic-card__user {
  flex: 1;
}

.dynamic-card__name-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 6px;
}

.dynamic-card__name {
  font-size: 17px;
  font-weight: 600;
  color: #111111;
}

.dynamic-card__badge {
  font-size: 11px;
  color: #ffffff;
  background: linear-gradient(140deg, #f56551, #ffb149);
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 500;
}

.dynamic-card__time {
  font-size: 14px;
  color: #999;
  margin-top: 4px;
}

.dynamic-card__content {
  font-size: 17px;
  color: #1a1a1a;
  line-height: 26px;
  margin-bottom: 14px;
}

.dynamic-card__images {
  display: flex;
  flex-direction: row;
  gap: 8px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.dynamic-card__image {
  width: 140px;
  height: 140px;
  border-radius: 12px;
}

.dynamic-card__image--0 { background: linear-gradient(135deg, #ff6b6b, #feca57); }
.dynamic-card__image--1 { background: linear-gradient(135deg, #4ecdc4, #45b7d1); }
.dynamic-card__image--2 { background: linear-gradient(135deg, #a78bfa, #ff8cc8); }
.dynamic-card__image--3 { background: linear-gradient(135deg, #51cf66, #4ecdc4); }
.dynamic-card__image--4 { background: linear-gradient(135deg, #ff8cc8, #a78bfa); }

.dynamic-card__actions {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 24px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f2;
}

.dynamic-card__action {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 6px;
  background: none;
  border: none;
  padding: 0 6px;
  min-height: 44px;
  font-size: 15px;
  color: #4a5568;
  font-weight: 500;
  line-height: 1;
}

.dynamic-card__action::after { border: none; }

.dynamic-card__action--liked {
  color: #f56551;
}

.action-icon {
  font-size: 18px;
}

.action-count {
  font-size: 14px;
}

/* 浮动 + 号按钮 */
.fab {
  position: fixed;
  right: 20px;
  bottom: 110px;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(140deg, #f56551, #ffb149);
  box-shadow: 0 6px 16px rgba(245, 101, 81, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.fab__icon {
  font-size: 36px;
  font-weight: 300;
  color: #ffffff;
  line-height: 1;
  margin-top: -4px;
}
</style>
