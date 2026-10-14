<script setup>
import { ref, onMounted } from "vue"
import ActivityCard from "../../components/ActivityCard.vue"
import BottomNavigation from "../../components/BottomNavigation.vue"
import { getActivities } from "../../api/activity"
import { API_BASE_URL } from "../../config/runtime"

const activities = ref([])
const loading = ref(false)

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

  // 拼接封面图完整 URL（后端返回的是相对路径 /api/v1/files/{id}/content）
  const coverUrl = item.cover?.url
    ? (item.cover.url.startsWith("http") ? item.cover.url : `${API_BASE_URL}${item.cover.url}`)
    : null

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
    coverUrl,
  }
}

async function loadActivities() {
  loading.value = true
  try {
    const res = await getActivities({ limit: 20 })
    activities.value = (res?.items || []).map(mapActivity)
  } catch (e) {
    console.error("加载活动列表失败", e)
    uni.showToast({ title: "加载失败", icon: "none" })
  } finally {
    loading.value = false
  }
}

function openEvent(item) {
  uni.navigateTo({ url: `/pages/detail/index?id=${item.id}` })
}

function joinEvent(item) {
  openEvent(item)
}

onMounted(() => {
  loadActivities()
})
</script>

<template>
  <view class="plaza">
    <view class="plaza__header">
      <view class="plaza__title-row">
        <text class="plaza__title">活动广场</text>
      </view>
    </view>

    <scroll-view class="feed" scroll-y :show-scrollbar="false">
      <view class="feed__inner">
        <ActivityCard
          v-for="item in activities"
          :key="item.id"
          :item="item"
          @open="openEvent"
          @join="joinEvent"
        />
        <view v-if="loading" class="feed__loading">加载中...</view>
        <view v-else-if="activities.length === 0" class="feed__empty">暂无活动</view>
      </view>
    </scroll-view>

    <BottomNavigation current="plaza" />
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
.feed__empty {
  width: 100%;
  padding: 24px;
  color: #888;
  font-size: 14px;
  text-align: center;
}
</style>
