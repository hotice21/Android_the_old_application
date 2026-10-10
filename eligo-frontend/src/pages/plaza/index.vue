<script setup>
import { ref, onMounted } from "vue"
import ActivityCard from "../../components/ActivityCard.vue"
import BottomNavigation from "../../components/BottomNavigation.vue"
import { getActivities } from "../../api/activity"
import { requireLogin } from "../../stores/entry"

const tabs = ["推荐", "徒步", "运动"]
const activeTab = ref("推荐")
const activities = ref([])
const loading = ref(false)
const nextCursor = ref(null)
const hasMore = ref(true)

/** 把 API 活动项映射成 ActivityCard 需要的字段 */
function mapActivity(item) {
  const startsAt = item.startsAt ? new Date(item.startsAt) : null
  const weekday = startsAt
    ? ["周日", "周一", "周二", "周三", "周四", "周五", "周六"][startsAt.getDay()]
    : ""
  const hh = startsAt
    ? String(startsAt.getHours()).padStart(2, "0")
    : ""
  const mm = startsAt
    ? String(startsAt.getMinutes()).padStart(2, "0")
    : ""

  return {
    id: item.activityId,
    title: item.title,
    organizer: item.owner?.displayName || "未知组织者",
    distance: item.distanceMeters != null
      ? item.distanceMeters >= 1000
        ? (item.distanceMeters / 1000).toFixed(1) + "km"
        : item.distanceMeters + "m"
      : "待定",
    registrations: item.participantCount || 0,
    cardTime: `${hh}:${mm}`,
    datePrefix: weekday,
    dateLabel: startsAt ? `${startsAt.getMonth() + 1}/${startsAt.getDate()}` : "",
    placeName: item.placeName || "",
    addressDetail: item.addressDetail || "",
    categoryCode: item.categoryCode,
  }
}

async function loadActivities(reset = false) {
  if (loading.value) return
  if (!reset && !hasMore.value) return
  loading.value = true
  try {
    const params = { limit: 20 }
    if (!reset && nextCursor.value) params.cursor = nextCursor.value
    const res = await getActivities(params)
    // request.js unwrap 已经返回 body.data，所以 res 直接是 {items, nextCursor, hasMore}
    const items = (res?.items || []).map(mapActivity)
    if (reset) {
      activities.value = items
    } else {
      activities.value = [...activities.value, ...items]
    }
    nextCursor.value = res?.nextCursor || null
    hasMore.value = !!res?.hasMore
  } catch (e) {
    console.error("加载活动列表失败", e)
    uni.showToast({ title: "加载失败，请下拉刷新", icon: "none" })
  } finally {
    loading.value = false
  }
}

function openEvent(item) {
  uni.navigateTo({ url: `/pages/detail/index?id=${item.id}` })
}

function joinEvent(item) {
  if (!requireLogin()) return
  openEvent(item)
}

function showDemo(name) {
  if (!requireLogin()) return
  uni.showToast({ title: `${name}（开发中）`, icon: "none" })
}

function selectTab(tab) {
  if (!requireLogin()) return
  if (tab === activeTab.value) return
  activeTab.value = tab
  loadActivities(true)
}

onMounted(() => {
  loadActivities(true)
})
</script>

<template>
  <view class="plaza">
    <view class="plaza__header">
      <view class="plaza__title-row">
        <text class="plaza__title">活动广场</text>
      </view>

      <view class="tabs-row">
        <scroll-view class="tabs" scroll-x :show-scrollbar="false">
          <view class="tabs__inner">
            <button
              v-for="tab in tabs"
              :key="tab"
              class="tab"
              :class="{ 'tab--active': activeTab === tab }"
              @tap="selectTab(tab)"
            >
              <text>{{ tab }}</text>
            </button>
          </view>
        </scroll-view>
      </view>
    </view>

    <scroll-view class="feed" scroll-y :show-scrollbar="false" @scrolltolower="loadActivities">
      <view class="feed__inner">
        <ActivityCard
          v-for="item in activities"
          :key="item.id"
          :item="item"
          @open="openEvent"
          @join="joinEvent"
        />
        <view v-if="loading" class="feed__loading">加载中...</view>
        <view v-else-if="!hasMore && activities.length > 0" class="feed__end">—— 没有更多了 ——</view>
        <view v-else-if="activities.length === 0" class="feed__empty">暂无活动</view>
      </view>
    </scroll-view>

    <BottomNavigation current="plaza" @plus="showDemo('发布')" />
  </view>
</template>

<style scoped>
.plaza {
  position: fixed;
  inset: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: #f6f7fb;
  font-family: "PingFang SC", sans-serif;
}

.plaza__header {
  position: relative;
  z-index: 20;
  flex: 0 0 auto;
  padding: 16px 16px 12px;
  background: #ffffff;
  overflow: hidden;
}

.plaza__title-row {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}

.plaza__title {
  font-size: 24px;
  font-weight: 700;
  color: #111111;
}

.tabs-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tabs {
  flex: 1 1 auto;
  height: 44px;
  white-space: nowrap;
  overflow: visible;
}

.tabs__inner {
  display: flex;
  align-items: center;
  height: 44px;
}

.tab {
  position: relative;
  flex: 0 0 auto;
  min-height: 44px;
  margin-right: 28px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: visible;
  border: 0;
  border-radius: 0;
  color: #666666;
  background: transparent;
  box-shadow: none;
  font-size: 18px;
  line-height: 1;
  font-weight: 400;
}

.tab::after {
  border: 0;
}

.tab--active {
  color: #111111;
  font-size: 22px;
  font-weight: 700;
}

.feed {
  flex: 1 1 auto;
  padding-bottom: 90px;
}

.feed__inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 4px 16px 18px;
}

.feed__loading,
.feed__end,
.feed__empty {
  width: 100%;
  padding: 24px;
  color: #888;
  font-size: 14px;
  text-align: center;
}
</style>
