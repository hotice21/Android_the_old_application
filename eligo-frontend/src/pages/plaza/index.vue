<script setup>
import { ref } from "vue"
import ActivityCard from "../../components/ActivityCard.vue"
import BottomNavigation from "../../components/BottomNavigation.vue"
import DeviceStatus from "../../components/DeviceStatus.vue"
import { activityFeed, followings } from "../../mock/events"
import { requireLogin } from "../../stores/entry"

const tabs = ["关注", "推荐", "附近活动", "徒步", "运动"]
const activeTab = ref("关注")

function openEvent(item) {
  uni.navigateTo({ url: `/pages/detail/index?id=${item.id}` })
}

function joinEvent(item) {
  if (!requireLogin()) return
  openEvent(item || activityFeed[0])
}

function showDemo(name) {
  if (!requireLogin()) return
  uni.showToast({ title: `${name}（Demo）`, icon: "none" })
}

function selectTab(tab) {
  if (!requireLogin()) return
  activeTab.value = tab
}
</script>

<template>
  <view class="plaza">
    <view class="plaza__header">
      <view class="tools">
        <view class="weather-location">
          <text class="weather-location__degree">36°C</text>
          <text class="weather-location__weather">🌤️</text>
          <view class="weather-location__divider" />
          <text class="weather-location__name">旺角, 香港</text>
          <text class="weather-location__chevron">›</text>
        </view>

        <button class="map-button" aria-label="活动地图" @tap="showDemo('活动地图')">
          <image
            class="map-button__art"
            src="/static/eligo/icons/map-location.svg"
            mode="aspectFit"
          />
        </button>

        <button class="search-button" aria-label="搜索" @tap="showDemo('搜索')">
          <image
            class="search-button__icon"
            src="/static/eligo/icons/search.svg"
            mode="aspectFit"
          />
        </button>
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
              <image
                v-if="activeTab === tab"
                class="tab__underline"
                src="/static/eligo/icons/tab-underline.svg"
                mode="aspectFit"
              />
            </button>
          </view>
        </scroll-view>
        <button class="filter" aria-label="筛选" @tap="showDemo('筛选')">
          <image src="/static/eligo/icons/filter.svg" mode="aspectFit" />
        </button>
      </view>

      <scroll-view class="followings" scroll-x :show-scrollbar="false">
        <view class="followings__inner">
          <view
            v-for="person in followings"
            :key="person.id"
            class="following"
            :class="{ 'following--official': person.official }"
          >
            <view
              class="following__avatar-wrap"
              :class="{
                'following__avatar-wrap--official': person.official,
                'following__avatar-wrap--ring': person.followed,
              }"
            >
              <view v-if="person.official" class="following__official">Eligo</view>
              <view
                v-else
                class="following__avatar following__avatar--placeholder"
              />
              <view
                v-if="!person.followed"
                class="following__add"
                :class="{ 'following__add--official': person.official }"
              >
                +
              </view>
            </view>
            <text class="following__name">{{ person.name }}</text>
          </view>
        </view>
      </scroll-view>
    </view>

    <scroll-view class="feed" scroll-y :show-scrollbar="false">
      <view class="feed__inner">
        <ActivityCard
          v-for="item in activityFeed"
          :key="item.id"
          :item="item"
          @open="openEvent"
          @join="joinEvent"
        />
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

.tools {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.weather-location {
  position: relative;
  flex: 1 1 auto;
  display: flex;
  align-items: center;
  height: 48px;
  padding: 0 16px;
  border-radius: 24px;
  background: linear-gradient(178deg, #e7e7ea 0%, #f2f3ff 100%);
}

.weather-location,
.weather-location text,
.map-button,
.map-button text {
  font-family: "PingFang SC", sans-serif;
}

.weather-location__degree {
  color: rgba(0, 0, 0, 0.9);
  font-family: "PingFang SC", sans-serif;
  font-size: 16px;
  line-height: 16px;
  font-weight: 600;
}

.weather-location__weather {
  margin-left: 8px;
  width: 22px;
  height: 22px;
  font-size: 16px;
  line-height: 22px;
  text-align: center;
}

.weather-location__divider {
  margin: 0 10px;
  width: 1px;
  height: 16px;
  background: #eceff3;
}

.weather-location__name {
  color: rgba(0, 0, 0, 0.9);
  font-family: "PingFang SC", sans-serif;
  font-size: 18px;
  line-height: 18px;
  font-weight: 600;
  letter-spacing: -0.3px;
  white-space: nowrap;
}

.weather-location__chevron {
  margin-left: auto;
  color: rgba(0, 0, 0, 0.9);
  font-size: 22px;
  line-height: 22px;
}

.map-button {
  flex: 0 0 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.map-button__art {
  display: block;
  width: 40px;
  height: 40px;
}

.search-button {
  flex: 0 0 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  padding: 0;
  border: 0;
  background: transparent;
  box-shadow: none;
}

.search-button__icon {
  display: block;
  width: 40px;
  height: 40px;
}

.tabs-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.tabs {
  flex: 1 1 auto;
  height: 48px;
  white-space: nowrap;
  overflow: visible;
}

.tabs__inner {
  display: flex;
  align-items: center;
  height: 48px;
  padding: 0 0 0 4px;
}

.tab {
  position: relative;
  flex: 0 0 auto;
  min-height: 48px;
  margin-right: 22px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: visible;
  border: 0;
  border-radius: 0;
  color: #333333;
  background: transparent;
  box-shadow: none;
  font-size: 22px;
  line-height: 1;
  font-weight: 400;
}

.tab::after,
.filter::after,
.search-button::after,
.map-button::after {
  border: 0;
}

.tab--active {
  color: #000000;
  font-size: 22px;
  font-weight: 600;
}

.tab__underline {
  position: absolute;
  z-index: 3;
  bottom: 4px;
  left: 50%;
  width: 28px;
  height: 10px;
  transform: translateX(-50%);
}

.filter {
  flex: 0 0 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  padding: 0;
  border: 0;
  background: transparent;
  box-shadow: none;
}

.filter image {
  width: 22px;
  height: 22px;
  opacity: 0.6;
}

.followings {
  height: 110px;
  white-space: nowrap;
}

.followings__inner {
  display: flex;
  height: 110px;
  padding: 0 0 0 16px;
}

.following {
  display: flex;
  flex: 0 0 72px;
  width: 72px;
  min-width: 72px;
  max-width: 72px;
  flex-direction: column;
  align-items: center;
  margin-right: 22px;
}

.following--official {
  align-items: flex-start;
  margin-right: 18px;
}

.following--official .following__name {
  width: 52px;
}

.following__avatar-wrap {
  position: relative;
  width: 72px;
  height: 72px;
  padding: 0;
  border-radius: 30px;
}

.following__avatar-wrap--ring {
  background: linear-gradient(180deg, #feac49 0%, #f56751 100%);
}

.following__avatar-wrap--ring::before {
  content: "";
  position: absolute;
  z-index: 1;
  inset: 3px;
  border-radius: 27px;
  background: #ffffff;
}

.following__avatar-wrap--official {
  width: 52px;
  height: 56px;
  margin-top: 4px;
}

.following__avatar,
.following__official {
  position: absolute;
  z-index: 2;
  top: 4px;
  left: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  overflow: hidden;
  border: 0;
  border-radius: 26px;
  background: #f0f1f4;
}

.following__avatar--placeholder {
  background: linear-gradient(135deg, #8e9eff 0%, #b5a8ff 100%);
}

.following__official {
  top: 0;
  left: 0;
  width: 52px;
  height: 52px;
  border-radius: 22px;
  color: #ffffff;
  background: linear-gradient(140deg, #f56551 0%, #ffb149 100%);
  font-family: "PingFang SC", sans-serif;
  font-size: 15px;
  font-weight: 600;
}

.following__add {
  position: absolute;
  right: -1px;
  bottom: -1px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: 2px solid #ffffff;
  border-radius: 50%;
  color: #ffffff;
  background: #ff5a3c;
  font-family: "PingFang SC", sans-serif;
  font-size: 14px;
  line-height: 12px;
  font-weight: 500;
  z-index: 3;
}

.following__add--official {
  right: 0;
  bottom: 0;
  background: #191919;
}

.following__name {
  width: 96px;
  margin-top: 10px;
  overflow: visible;
  color: rgba(0, 0, 0, 0.9);
  font-size: 15px;
  line-height: 16px;
  text-align: center;
  text-overflow: clip;
  white-space: nowrap;
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

@media (min-width: 390px) {
  .activity-card {
    width: calc(100vw - 32px);
  }
}
</style>
