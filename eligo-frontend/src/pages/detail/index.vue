<script setup>
import { ref, onMounted, computed } from "vue"
import { onLoad } from "@dcloudio/uni-app"
import ActivityCard from "../../components/ActivityCard.vue"
import {
  getActivityDetail,
  joinActivity,
  cancelJoinActivity,
  getActivityFavoriteStatus,
  favoriteActivity,
  unfavoriteActivity,
  getActivities,
} from "../../api/activity"
import { requireLogin } from "../../stores/entry"

const activityId = ref(null)
const detail = ref(null)
const loading = ref(true)
const favorited = ref(false)
const participating = ref(false)
const joining = ref(false)
const recommendations = ref([])

onLoad((options) => {
  if (options?.id) {
    activityId.value = String(options.id)
    loadAll()
  }
})

async function loadAll() {
  if (!activityId.value) return
  loading.value = true
  try {
    // request.js 的 unwrap 已经返回 body.data，所以这些直接就是后端 Result.data
    const [detailRes, favRes, listRes] = await Promise.all([
      getActivityDetail(activityId.value).catch(() => null),
      getActivityFavoriteStatus(activityId.value).catch(() => null),
      getActivities({ limit: 3 }).catch(() => null),
    ])
    // detailRes 直接就是详情对象：{activityId, title, owner, startsAt, endsAt, description, participantCount, capacity, placeName, registrationStatus, categoryCode}
    detail.value = detailRes || null
    // favRes 直接就是 {favorited: boolean}
    favorited.value = !!favRes?.favorited
    // detail 里没有 myRegistration 字段，我们用 registrationStatus 判断
    participating.value = detail?.participationStatus === "JOINED"
      || detail?.registrationStatus === "JOINED"
    // listRes 就是 {items, nextCursor, hasMore}
    recommendations.value = (listRes?.items || [])
      .filter((i) => String(i.activityId) !== activityId.value)
      .slice(0, 2)
      .map(mapActivity)
  } catch (e) {
    console.error("加载详情失败", e)
    uni.showToast({ title: "加载失败", icon: "none" })
  } finally {
    loading.value = false
  }
}

function mapActivity(item) {
  const startsAt = item.startsAt ? new Date(item.startsAt) : null
  const weekday = startsAt
    ? ["周日", "周一", "周二", "周三", "周四", "周五", "周六"][startsAt.getDay()]
    : ""
  const hh = startsAt ? String(startsAt.getHours()).padStart(2, "0") : ""
  const mm = startsAt ? String(startsAt.getMinutes()).padStart(2, "0") : ""
  return {
    id: item.activityId,
    title: item.title,
    organizer: item.owner?.displayName || "",
    distance: "待定",
    registrations: item.participantCount || 0,
    cardTime: `${hh}:${mm}`,
    datePrefix: weekday,
    dateLabel: startsAt ? `${startsAt.getMonth() + 1}/${startsAt.getDate()}` : "",
  }
}

/** 格式化时间 */
const formattedStartsAt = computed(() => {
  const t = detail.value?.startsAt
  if (!t) return { time: "--:--", date: "--/--", weekday: "" }
  const d = new Date(t)
  return {
    time: `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`,
    date: `${d.getMonth() + 1}-${d.getDate()}`,
    weekday: ["周日", "周一", "周二", "周三", "周四", "周五", "周六"][d.getDay()],
  }
})

function goBack() {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
    return
  }
  uni.reLaunch({ url: "/pages/plaza/index" })
}

function showDemo(name) {
  if (!requireLogin()) return
  uni.showToast({ title: `${name}（开发中）`, icon: "none" })
}

async function toggleFavorite() {
  if (!requireLogin()) return
  try {
    if (favorited.value) {
      await unfavoriteActivity(activityId.value)
      favorited.value = false
      uni.showToast({ title: "已取消收藏", icon: "none" })
    } else {
      await favoriteActivity(activityId.value)
      favorited.value = true
      uni.showToast({ title: "已收藏", icon: "none" })
    }
  } catch (e) {
    uni.showToast({ title: "操作失败", icon: "none" })
  }
}

async function joinEvent() {
  if (!requireLogin()) return
  if (joining.value) return
  joining.value = true
  try {
    if (participating.value) {
      await cancelJoinActivity(activityId.value)
      participating.value = false
      uni.showToast({ title: "已取消报名", icon: "none" })
    } else {
      await joinActivity(activityId.value)
      participating.value = true
      uni.showToast({ title: "报名成功", icon: "success" })
    }
  } catch (e) {
    // e 是 ApiError，直接取 message
    const msg = e?.message || "操作失败"
    uni.showToast({ title: msg, icon: "none" })
  } finally {
    joining.value = false
  }
}

function openRecommendation(item) {
  uni.navigateTo({ url: `/pages/detail/index?id=${item.id}` })
}
</script>

<template>
  <view class="detail">
    <scroll-view class="detail__scroll" scroll-y :show-scrollbar="false">
      <view class="detail__content">
        <view class="hero">
          <view class="hero__gradient" />
          <view class="hero__top-shade" />

          <button class="nav-button nav-button--back" aria-label="返回" @tap="goBack">
            <text class="nav-button__icon">‹</text>
          </button>
          <button class="nav-button nav-button--share" aria-label="分享" @tap="showDemo('分享')">
            <text class="nav-button__icon">↗</text>
          </button>
          <button
            class="nav-button nav-button--heart"
            :class="{ 'nav-button--favorite': favorited }"
            aria-label="收藏"
            @tap="toggleFavorite"
          >
            <text class="nav-button__icon">{{ favorited ? '❤️' : '🤍' }}</text>
          </button>

          <view class="hero__dots" aria-hidden="true">
            <view class="hero__dot hero__dot--active" />
            <view v-for="index in 5" :key="index" class="hero__dot" />
          </view>
        </view>

        <view class="product-panel">
          <text class="event-title">{{ detail?.title || "加载中..." }}</text>

          <view class="tags tags--warm">
            <text class="tag tag--warm">{{ detail?.owner?.ownerType === "ORGANIZATION" ? "主办方认证" : "个人活动" }}</text>
            <text v-if="detail?.registrationStatus" class="tag tag--warm">
              {{ detail.registrationStatus === "OPEN" ? "报名中" : "已截止" }}
            </text>
          </view>
          <view class="tags tags--safe">
            <text class="tag tag--safe">名额 {{ detail?.capacity ?? "--" }}</text>
            <text class="tag tag--safe">已报 {{ detail?.participantCount ?? 0 }} 人</text>
          </view>
        </view>

        <view class="organizer">
          <view class="organizer__identity">
            <view class="organizer__avatar">
              <text class="organizer__avatar-text">{{ (detail?.owner?.displayName || '?').charAt(0) }}</text>
            </view>
            <text class="organizer__name">{{ detail?.owner?.displayName || "未知组织者" }}</text>
          </view>
          <button class="organizer__follow" aria-label="关注主办方" @tap="showDemo('关注')">
            <text class="organizer__follow-icon">+</text>
          </button>
        </view>

        <view class="schedule">
          <view class="schedule__time">
            <view class="schedule__time-row">
              <text class="schedule__time-value">{{ formattedStartsAt.time }}</text>
              <text class="schedule__start-label">开始</text>
            </view>
            <view class="schedule__date-row">
              <text class="schedule__date">{{ formattedStartsAt.date }}</text>
              <text class="schedule__weekday">{{ formattedStartsAt.weekday }}</text>
            </view>
          </view>

          <view class="schedule__divider" />

          <view class="schedule__weather">
            <view class="schedule__weather-row">
              <text class="schedule__weather-low">容量</text>
              <text class="schedule__slash">/</text>
              <text class="schedule__temperature">{{ detail?.capacity ?? "--" }}</text>
            </view>
            <text class="schedule__weather-label">已报 {{ detail?.participantCount ?? 0 }} 人</text>
          </view>
        </view>

        <view class="map-card">
          <view class="map-card__header">
            <text class="map-card__icon">📍</text>
            <text class="map-card__title">集合地点</text>
          </view>
          <text class="map-card__address">
            {{ detail?.placeName ? detail.placeName + " · " : "" }}{{ detail?.addressDetail || "待定" }}
          </text>
        </view>

        <view class="introduction">
          <text class="section-title">活动介绍</text>
          <text class="introduction__copy">{{ detail?.description || "暂无介绍" }}</text>
          <text v-if="detail?.signupDetails" class="section-title" style="margin-top:16px">报名须知</text>
          <text v-if="detail?.signupDetails" class="introduction__copy">{{ detail.signupDetails }}</text>
        </view>

        <view class="recommend-heading">
          <view class="recommend-heading__line recommend-heading__line--left" />
          <text>猜你喜欢</text>
          <view class="recommend-heading__line recommend-heading__line--right" />
        </view>

        <view class="recommendations">
          <ActivityCard
            v-for="item in recommendations"
            :key="item.id"
            :item="item"
            @open="openRecommendation"
            @join="showDemo('报名')"
          />
        </view>
      </view>
    </scroll-view>

    <view class="action-bar">
      <button class="action" @tap="toggleFavorite">
        <text class="action__icon">{{ favorited ? '❤️' : '🤍' }}</text>
        <text class="action__label">收藏</text>
      </button>
      <button class="action" @tap="showDemo('联系主办方')">
        <text class="action__icon">💬</text>
        <text class="action__label">联系</text>
      </button>
      <button
        class="join-button"
        :class="{ 'join-button--cancel': participating }"
        :disabled="joining"
        @tap="joinEvent"
      >
        {{ joining ? "处理中..." : (participating ? "取消报名" : "立即加入") }}
      </button>
    </view>
  </view>
</template>

<style scoped>
.detail {
  position: fixed;
  inset: 0;
  display: flex;
  flex-direction: column;
  background: #f6f7fb;
  font-family: "PingFang SC", sans-serif;
}

.detail__scroll {
  flex: 1;
  background: #f6f7fb;
}

.detail__content {
  padding: 0 0 130px;
}

/* ---------- Hero ---------- */
.hero {
  position: relative;
  width: 100%;
  height: 240px;
  overflow: hidden;
}

.hero__gradient {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, #4a7fa8 0%, #7fa6c4 35%, #d4b896 70%, #c89868 100%);
}

.hero__top-shade {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 120px;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.5), transparent);
}

.nav-button {
  position: absolute;
  top: 58px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.42);
  color: #ffffff;
  line-height: 1;
}

.nav-button::after { border: 0; }

.nav-button__icon {
  font-size: 20px;
}

.nav-button--back { left: 16px; }
.nav-button--share { right: 64px; }
.nav-button--heart { right: 16px; }
.nav-button--favorite { background: rgba(245, 101, 81, 0.92); }

.hero__dots {
  position: absolute;
  bottom: 16px;
  left: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 6px;
}

.hero__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.7);
}

.hero__dot--active {
  width: 20px;
  border-radius: 3px;
  background: #ffffff;
}

/* ---------- Product panel ---------- */
.product-panel {
  margin: -32px 16px 16px;
  padding: 20px 16px;
  background: #ffffff;
  border-radius: 12px;
  position: relative;
  z-index: 2;
}

.event-title {
  display: block;
  color: #1a1a1a;
  font-size: 22px;
  line-height: 30px;
  font-weight: 600;
}

.tags {
  display: flex;
  margin-top: 14px;
  gap: 8px;
}

.tags--safe { margin-top: 10px; }

.tag {
  box-sizing: border-box;
  padding: 4px 10px;
  border: 1px solid;
  border-radius: 6px;
  font-size: 13px;
  line-height: 16px;
  white-space: nowrap;
}

.tag--warm {
  border-color: #ffb7ad;
  color: #d9402a;
  background: #ffe7e3;
}

.tag--safe {
  border-color: #b8ebc8;
  color: #1a7a4a;
  background: #e1f6e7;
}

/* ---------- Organizer ---------- */
.organizer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 16px 16px;
  padding: 14px 16px;
  border-radius: 12px;
  background: #ffffff;
}

.organizer__identity {
  display: flex;
  align-items: center;
  min-width: 0;
  flex: 1;
}

.organizer__avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(140deg, #f56551, #ffb149);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.organizer__avatar-text {
  color: #ffffff;
  font-size: 16px;
  font-weight: 600;
}

.organizer__name {
  flex: 1;
  margin-left: 12px;
  overflow: hidden;
  color: #1f1f1f;
  font-size: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.organizer__follow {
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 50%;
  background: #ff5a3c;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
}

.organizer__follow::after { border: 0; }

.organizer__follow-icon {
  color: #ffffff;
  font-size: 18px;
  font-weight: 300;
  line-height: 1;
}

/* ---------- Schedule ---------- */
.schedule {
  display: flex;
  align-items: center;
  margin: 0 16px 16px;
  padding: 18px 16px;
  border-radius: 12px;
  background: #ffffff;
}

.schedule__time {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.schedule__time-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.schedule__time-value {
  color: #1a1a1a;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.6px;
  white-space: nowrap;
}

.schedule__start-label {
  color: #1a1a1a;
  font-size: 16px;
  font-weight: 600;
}

.schedule__date-row {
  margin-top: 8px;
  display: flex;
  gap: 10px;
}

.schedule__date,
.schedule__weekday {
  color: #4a4a4a;
  font-size: 15px;
}

.schedule__divider {
  width: 1px;
  height: 48px;
  margin: 0 22px;
  background: #e6e8ec;
  flex-shrink: 0;
}

.schedule__weather {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.schedule__weather-row {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.schedule__weather-low {
  color: #1a1a1a;
  font-size: 22px;
  font-weight: 700;
}

.schedule__slash,
.schedule__temperature {
  color: #1a1a1a;
  font-size: 15px;
  font-weight: 700;
}

.schedule__weather-label {
  margin-top: 6px;
  color: #4a4a4a;
  font-size: 14px;
}

/* ---------- Map card ---------- */
.map-card {
  margin: 0 16px 16px;
  padding: 18px 20px;
  border-radius: 12px;
  background: #ffffff;
}

.map-card__header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.map-card__icon {
  font-size: 20px;
}

.map-card__title {
  color: #1a1a1a;
  font-size: 18px;
  font-weight: 600;
}

.map-card__address {
  display: block;
  margin-top: 12px;
  color: #1f1f1f;
  font-size: 16px;
  line-height: 24px;
  font-weight: 500;
}

/* ---------- Introduction ---------- */
.introduction {
  margin: 0 16px 16px;
  padding: 18px 16px;
  border-radius: 12px;
  background: #ffffff;
}

.section-title {
  display: block;
  color: #1a1a1a;
  font-size: 18px;
  font-weight: 600;
}

.introduction__copy {
  display: block;
  margin-top: 12px;
  color: #1f1f1f;
  font-size: 16px;
  line-height: 26px;
  white-space: pre-line;
}

/* ---------- Recommend heading ---------- */
.recommend-heading {
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 24px 16px 12px;
  gap: 14px;
}

.recommend-heading text {
  color: #d9402a;
  font-size: 18px;
  font-weight: 600;
  text-align: center;
}

.recommend-heading__line {
  width: 60px;
  height: 2px;
  background: linear-gradient(90deg, #ffe7c8, #fcd2ca);
}

.recommendations {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin: 0 16px 16px;
}

/* ---------- Action bar ---------- */
.action-bar {
  position: fixed;
  z-index: 90;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  align-items: center;
  height: 70px;
  padding: 0 16px;
  background: #ffffff;
  box-shadow: 0 -3px 12px rgba(35, 42, 51, 0.08);
  padding-bottom: 16px;
}

.action-bar::after { border: 0; }

.action {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 50px;
  height: 50px;
  padding: 0;
  border: 0;
  background: transparent;
}

.action::after { border: 0; }

.action__icon {
  font-size: 22px;
  line-height: 1;
}

.action__label {
  font-size: 11px;
  color: #666666;
  margin-top: 2px;
}

.join-button {
  flex: 1;
  margin-left: 12px;
  height: 48px;
  border-radius: 24px;
  border: 0;
  color: #ffffff;
  background: linear-gradient(140deg, #f56551, #ffb149);
  font-size: 17px;
  font-weight: 600;
  line-height: 48px;
}

.join-button--cancel {
  background: #e3e6ea;
  color: #666666;
}

.join-button::after { border: 0; }
</style>
