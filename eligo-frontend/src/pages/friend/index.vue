<script setup>
import { onMounted, ref } from "vue"
import BottomNavigation from "../../components/BottomNavigation.vue"
import { mockNotifications, mockFollowings, mockFollowers } from "../../mock/friend"

const tabs = ["系统通知", "关注", "粉丝"]
const activeTab = ref("系统通知")
const followings = ref([])
const followers = ref([])
const loading = ref(false)

const notifications = ref(mockNotifications)

async function fetchFollowings() {
  // M4 模块未实现，直接使用 mock 数据
  followings.value = mockFollowings
}

async function fetchFollowers() {
  // M4 模块未实现，直接使用 mock 数据
  followers.value = mockFollowers
}

function onTabChange(tab) {
  activeTab.value = tab
  if (tab === "关注") fetchFollowings()
  if (tab === "粉丝") fetchFollowers()
}

function onNotificationTap(item) {
  item.read = true
  uni.showToast({ title: item.title, icon: "none" })
}

function onUserTap(user) {
  uni.showToast({ title: `查看 ${user.name} 的主页`, icon: "none" })
}

onMounted(() => {
  fetchFollowings()
  fetchFollowers()
})
</script>

<template>
  <view class="friend">
    <view class="friend__header">
      <text class="friend__title">消息</text>
    </view>

    <view class="friend__tabs">
      <button
        v-for="tab in tabs"
        :key="tab"
        class="friend__tab"
        :class="{ 'friend__tab--active': activeTab === tab }"
        @tap="onTabChange(tab)"
      >
        <text>{{ tab }}</text>
      </button>
    </view>

    <scroll-view class="friend__content" scroll-y :show-scrollbar="false">
      <!-- 系统通知 -->
      <view v-if="activeTab === '系统通知'" class="friend__list">
        <view
          v-for="item in notifications"
          :key="item.id"
          class="notification-item"
          :class="{ 'notification-item--unread': !item.read }"
          @tap="onNotificationTap(item)"
        >
          <view class="notification-item__dot" v-if="!item.read" />
          <view class="notification-item__body">
            <text class="notification-item__title">{{ item.title }}</text>
            <text class="notification-item__content">{{ item.content }}</text>
            <text class="notification-item__time">{{ item.time }}</text>
          </view>
        </view>
        <view v-if="notifications.length === 0" class="friend__empty">
          <text class="friend__empty-text">暂无通知</text>
        </view>
      </view>

      <!-- 关注列表 -->
      <view v-if="activeTab === '关注'" class="friend__list">
        <view
          v-for="(user, index) in followings"
          :key="user.id"
          class="user-item"
          :class="'user-item--' + (index % 6)"
          @tap="onUserTap(user)"
        >
          <view class="user-item__avatar">
            <text class="user-item__avatar-text">{{ user.name.charAt(0) }}</text>
          </view>
          <view class="user-item__info">
            <view class="user-item__name-row">
              <text class="user-item__name">{{ user.name }}</text>
              <view v-if="user.isOfficial" class="user-item__badge">官方</view>
            </view>
          </view>
          <text class="user-item__arrow">›</text>
        </view>
        <view v-if="followings.length === 0" class="friend__empty">
          <text class="friend__empty-text">暂无关注</text>
        </view>
      </view>

      <!-- 粉丝列表 -->
      <view v-if="activeTab === '粉丝'" class="friend__list">
        <view
          v-for="(user, index) in followers"
          :key="user.id"
          class="user-item"
          :class="'user-item--' + (index % 6)"
          @tap="onUserTap(user)"
        >
          <view class="user-item__avatar">
            <text class="user-item__avatar-text">{{ user.name.charAt(0) }}</text>
          </view>
          <view class="user-item__info">
            <text class="user-item__name">{{ user.name }}</text>
          </view>
          <button class="user-item__follow-btn" @tap.stop="() => {}">
            <text>回关</text>
          </button>
        </view>
        <view v-if="followers.length === 0" class="friend__empty">
          <text class="friend__empty-text">暂无粉丝</text>
        </view>
      </view>
    </scroll-view>

    <BottomNavigation current="feed" />
  </view>
</template>

<style scoped>
.friend {
  position: fixed;
  inset: 0;
  display: flex;
  flex-direction: column;
  background: #f6f7fb;
}

.friend__header {
  padding: 56px 20px 16px;
  background: #ffffff;
}

.friend__title {
  font-size: 26px;
  font-weight: 700;
  color: #111111;
}

.friend__tabs {
  display: flex;
  flex-direction: row;
  gap: 24px;
  padding: 0 20px 16px;
  background: #ffffff;
}

.friend__tab {
  font-size: 20px;
  color: #5a6473;
  background: none;
  border: none;
  padding: 0 4px;
  line-height: 1.2;
  min-height: 48px;
  display: flex;
  align-items: center;
}

.friend__tab--active {
  color: #111111;
  font-weight: 700;
}

.friend__content {
  flex: 1;
  padding-bottom: 83px;
}

.friend__list {
  display: flex;
  flex-direction: column;
}

.friend__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: 120px;
}

.friend__empty-text {
  font-size: 16px;
  color: #6b7280;
}

/* 通知样式 */
.notification-item {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  gap: 12px;
  padding: 20px;
  background: #ffffff;
  border-bottom: 1px solid #eef0f3;
  min-height: 48px;
}

.notification-item--unread {
  background: #fefcf5;
}

.notification-item__dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #f56551;
  margin-top: 8px;
  flex-shrink: 0;
}

.notification-item__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.notification-item__title {
  font-size: 19px;
  font-weight: 600;
  color: #111111;
}

.notification-item__content {
  font-size: 17px;
  color: #4a5568;
  line-height: 24px;
}

.notification-item__time {
  font-size: 15px;
  color: #6b7280;
  margin-top: 4px;
}

/* 用户列表样式 */
.user-item {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 16px 20px;
  background: #ffffff;
  border-bottom: 1px solid #eef0f3;
}

.user-item__avatar {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  margin-right: 14px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-item__avatar-text {
  font-size: 24px;
  font-weight: 600;
  color: #ffffff;
}

.user-item--0 .user-item__avatar {
  background: #ff6b6b;
}
.user-item--1 .user-item__avatar {
  background: #4ecdc4;
}
.user-item--2 .user-item__avatar {
  background: #45b7d1;
}
.user-item--3 .user-item__avatar {
  background: #f9a826;
}
.user-item--4 .user-item__avatar {
  background: #a78bfa;
}
.user-item--5 .user-item__avatar {
  background: #51cf66;
}

.user-item__info {
  flex: 1;
}

.user-item__name-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
}

.user-item__name {
  font-size: 19px;
  font-weight: 600;
  color: #111111;
}

.user-item__badge {
  font-size: 14px;
  color: #ffffff;
  background: linear-gradient(140deg, #f56551, #ffb149);
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 500;
}

.user-item__arrow {
  font-size: 22px;
  color: #9ca3af;
}

.user-item__follow-btn {
  padding: 0 24px;
  min-height: 48px;
  border-radius: 24px;
  background: linear-gradient(140deg, #f56551, #ffb149);
  border: none;
  font-size: 16px;
  color: #ffffff;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
