<script setup>
import { onMounted, ref } from "vue"
import { activityFeed } from "../../mock/events"

const searchQuery = ref("")
const activeFilter = ref("")
const filters = ["夏日徒步", "运动", "博物馆", "搭子团"]
const activities = ref([])
const loading = ref(false)

const markers = ref([])
const latitude = ref(22.5431)
const longitude = ref(114.0579)

function buildMarkers(list) {
  return list
    .filter((item) => item.latitude && item.longitude)
    .map((item, index) => ({
      id: index,
      latitude: item.latitude || 22.5431,
      longitude: item.longitude || 114.0579,
      title: item.title,
      width: 32,
      height: 32,
      iconPath: "/static/eligo/icons/location-pin.svg",
    }))
}

async function fetchActivities() {
  loading.value = true
  try {
    // M2 活动模块未实现，直接使用 mock 数据
    const mockData = activityFeed.map((item, i) => ({
      ...item,
      latitude: 22.5431 + (i * 0.005),
      longitude: 114.0579 + (i * 0.003),
      rating: 4.8,
      price: i % 2 === 0 ? 56 : null,
    }))
    activities.value = mockData
    markers.value = buildMarkers(mockData)
  } finally {
    loading.value = false
  }
}

function onFilterTap(tag) {
  activeFilter.value = activeFilter.value === tag ? "" : tag
  fetchActivities()
}

function openDetail(item) {
  uni.navigateTo({ url: `/pages/detail/index?id=${item.id}` })
}

function goBack() {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
    return
  }
  uni.reLaunch({ url: "/pages/plaza/index" })
}

function onMarkerTap(e) {
  const markerId = e.detail?.markerId ?? e.markerId
  if (markerId !== undefined && activities.value[markerId]) {
    openDetail(activities.value[markerId])
  }
}

onMounted(() => {
  fetchActivities()
})
</script>

<template>
  <view class="map-page">
    <view class="map-page__header">
      <button class="map-page__back" @tap="goBack">
        <image
          src="/static/eligo/icons/arrow-left.svg"
          class="map-page__back-icon"
          mode="aspectFit"
        />
      </button>
      <view class="map-page__search">
        <image
          src="/static/eligo/icons/search.svg"
          class="map-page__search-icon"
          mode="aspectFit"
        />
        <input
          v-model="searchQuery"
          class="map-page__search-input"
          placeholder="搜索活动或地点"
          type="text"
        />
      </view>
    </view>

    <view class="map-page__filters">
      <button
        v-for="tag in filters"
        :key="tag"
        class="map-page__filter-tag"
        :class="{ 'map-page__filter-tag--active': activeFilter === tag }"
        @tap="onFilterTap(tag)"
      >
        <text>{{ tag }}</text>
      </button>
    </view>

    <view class="map-page__map">
      <map
        class="map-page__map-view"
        :latitude="latitude"
        :longitude="longitude"
        :scale="13"
        :markers="markers"
        show-location
        @markertap="onMarkerTap"
      />
    </view>

    <view class="map-page__list">
      <scroll-view class="map-page__list-scroll" scroll-y :show-scrollbar="false">
        <view v-if="loading" class="map-page__loading">
          <text class="map-page__loading-text">加载中...</text>
        </view>
        <view v-else-if="activities.length === 0" class="map-page__empty">
          <text class="map-page__empty-text">附近暂无活动</text>
        </view>
        <view
          v-for="(item, idx) in activities"
          :key="item.id"
          class="map-page__activity-item"
          :class="'map-page__activity-item--' + (idx % 4)"
          @tap="openDetail(item)"
        >
          <view class="map-page__activity-placeholder">
            <text class="map-page__activity-placeholder-text">{{ (item.title || '').charAt(0) }}</text>
          </view>
          <view class="map-page__activity-info">
            <text class="map-page__activity-title">{{ item.title }}</text>
            <view class="map-page__activity-meta">
              <text class="map-page__activity-date">{{ item.dateLabel || item.date || '' }}</text>
              <text class="map-page__activity-location">{{ item.location || '' }}</text>
            </view>
            <view class="map-page__activity-bottom">
              <text v-if="item.price" class="map-page__activity-price">¥{{ item.price }}</text>
              <text v-else class="map-page__activity-free">免费</text>
              <text v-if="item.rating" class="map-page__activity-rating">★ {{ item.rating }}</text>
            </view>
          </view>
        </view>
      </scroll-view>
    </view>
  </view>
</template>

<style scoped>
.map-page {
  position: fixed;
  inset: 0;
  display: flex;
  flex-direction: column;
  background: #f6f7fb;
}

.map-page__header {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 12px;
  padding: 56px 16px 14px;
  background: #ffffff;
}

.map-page__back {
  width: 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: none;
  padding: 0;
}

.map-page__back-icon {
  width: 26px;
  height: 26px;
}

.map-page__search {
  flex: 1;
  min-height: 48px;
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 10px;
  background: #f5f5f5;
  border-radius: 24px;
  padding: 10px 16px;
}

.map-page__search-icon {
  width: 20px;
  height: 20px;
  opacity: 0.6;
}

.map-page__search-input {
  flex: 1;
  font-size: 18px;
  color: #1a1a1a;
}

.map-page__filters {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 10px;
  padding: 12px 16px;
  background: #ffffff;
}

.map-page__filter-tag {
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 8px 18px;
  border-radius: 22px;
  background: #f0f1f4;
  border: none;
  font-size: 17px;
  color: #2a2a2a;
}

.map-page__filter-tag--active {
  background: #191919;
  color: #ffffff;
}

.map-page__map {
  flex: 1;
  min-height: 240px;
}

.map-page__map-view {
  width: 100%;
  height: 100%;
}

.map-page__list {
  background: #ffffff;
  border-radius: 20px 20px 0 0;
  max-height: 48%;
  overflow: hidden;
  box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.06);
}

.map-page__list-scroll {
  max-height: 100%;
  padding: 16px 18px;
}

.map-page__loading,
.map-page__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 0;
}

.map-page__loading-text,
.map-page__empty-text {
  font-size: 17px;
  color: #5a5a5a;
}

.map-page__activity-item {
  display: flex;
  flex-direction: row;
  align-items: stretch;
  gap: 14px;
  min-height: 88px;
  padding: 16px 0;
  border-bottom: 1px solid #eceef2;
}

.map-page__activity-item:last-child {
  border-bottom: none;
}

.map-page__activity-placeholder {
  width: 100px;
  height: 100px;
  border-radius: 12px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #6ec1e4 0%, #3a8ec9 100%);
}

.map-page__activity-item--0 .map-page__activity-placeholder {
  background: linear-gradient(135deg, #6ec1e4 0%, #3a8ec9 100%);
}

.map-page__activity-item--1 .map-page__activity-placeholder {
  background: linear-gradient(135deg, #ffb147 0%, #f5853f 100%);
}

.map-page__activity-item--2 .map-page__activity-placeholder {
  background: linear-gradient(135deg, #7ed957 0%, #4caf50 100%);
}

.map-page__activity-item--3 .map-page__activity-placeholder {
  background: linear-gradient(135deg, #b06ab3 0%, #7e57c2 100%);
}

.map-page__activity-placeholder-text {
  font-size: 34px;
  font-weight: 700;
  color: #ffffff;
  opacity: 0.95;
}

.map-page__activity-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 4px 0;
}

.map-page__activity-title {
  font-size: 18px;
  font-weight: 600;
  color: #111111;
  line-height: 26px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.map-page__activity-meta {
  display: flex;
  flex-direction: row;
  gap: 10px;
  margin-top: 6px;
}

.map-page__activity-date,
.map-page__activity-location {
  font-size: 16px;
  color: #3a3a3a;
}

.map-page__activity-bottom {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 10px;
  margin-top: 6px;
}

.map-page__activity-price {
  font-size: 18px;
  font-weight: 700;
  color: #e54b2e;
}

.map-page__activity-free {
  font-size: 17px;
  font-weight: 600;
  color: #2e8b3d;
}

.map-page__activity-rating {
  font-size: 16px;
  font-weight: 500;
  color: #c77800;
}
</style>
