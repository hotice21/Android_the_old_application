<script setup>
import { computed, ref } from "vue"
import { onShow } from "@dcloudio/uni-app"
import BottomNavigation from "../../components/BottomNavigation.vue"
import { downloadOwnedFile } from "../../api/file"
import { getMyProfile } from "../../api/profile"
import { formatApiError } from "../../api/request"
import { PROFILE_MOCK_ENABLED } from "../../config/runtime"
import { mockProfile } from "../../mock/profile"
import { getProfile, saveProfile } from "../../stores/session"

const tabs = ["动态", "活动"]
const activeTab = ref("动态")
const profile = ref(null)
const loading = ref(true)
const downloadedAvatar = ref("")
const loadError = ref("")
let avatarLoadVersion = 0

const profileAvatar = computed(() =>
  downloadedAvatar.value ||
    (typeof profile.value?.avatar === "string" ? profile.value.avatar : "") ||
    "/static/personal_picture.png",
)
const profileTags = computed(() => {
  if (Array.isArray(profile.value?.interestTags)) {
    return profile.value.interestTags.map(tag => tag.name)
  }
  return Array.isArray(profile.value?.tags) ? profile.value.tags : []
})

const menuItems = [
  { label: "我的活动", icon: "/static/eligo/icons/calender.svg", page: "" },
  { label: "我的动态", icon: "/static/eligo/icons/tab-feed.svg", page: "" },
  { label: "我的收藏", icon: "/static/eligo/icons/heart.svg", page: "" },
  { label: "我发布的", icon: "/static/eligo/icons/nav-publish.svg", page: "" },
]

async function fetchProfile() {
  loading.value = true
  loadError.value = ""
  if (PROFILE_MOCK_ENABLED) {
    profile.value = mockProfile
    await refreshAvatar(mockProfile)
    loading.value = false
    return
  }

  await loadLocalProfile()
  try {
    const data = await getMyProfile()
    if (data) {
      profile.value = data
      saveProfile(data)
      await refreshAvatar(data)
    }
  } catch (error) {
    loadError.value = formatApiError(error)
  } finally {
    loading.value = false
  }
}

async function loadLocalProfile() {
  const cached = getProfile()
  if (cached) {
    profile.value = cached
    await refreshAvatar(cached)
  } else {
    profile.value = null
    downloadedAvatar.value = ""
  }
}

async function refreshAvatar(value) {
  const loadVersion = ++avatarLoadVersion
  downloadedAvatar.value = ""
  const avatarUrl = value?.avatar?.url
  if (!avatarUrl) return

  try {
    const path = await downloadOwnedFile(avatarUrl)
    if (loadVersion === avatarLoadVersion) {
      downloadedAvatar.value = path
    }
  } catch (error) {
    console.error("头像下载失败", error)
  }
}

function onMenuTap(item) {
  if (item.page) {
    uni.navigateTo({ url: item.page })
  } else {
    uni.showToast({ title: `${item.label}功能开发中`, icon: "none" })
  }
}

function editProfile() {
  uni.navigateTo({ url: "/pages/profile-edit/index" })
}

function openSettings() {
  uni.showToast({ title: "设置功能开发中", icon: "none" })
}

function onWorkbenchTap(type) {
  const labels = {
    activity: '我的活动',
    connecting: '联系客服',
    security: '账号安全',
    mission: '任务中心',
    moment: '我的动态',
    vertification: '实名认证',
    community: '社区',
    clubs: '俱乐部',
  }
  uni.showToast({ title: `${labels[type]}功能开发中`, icon: "none" })
}

onShow(() => {
  fetchProfile()
})
</script>

<template>
  <view class="mine">
    <view v-if="PROFILE_MOCK_ENABLED" class="mine__data-notice">资料演示模式</view>
    <view v-else-if="loadError" class="mine__data-notice mine__data-notice--error" @tap="fetchProfile">
      资料加载失败，点击重试
    </view>
    <!-- 顶部个人信息区域 -->
    <view class="mine__header">
      <view class="mine__header-bg" />
      <view class="mine__profile">
        <image
          v-if="profileAvatar && profileAvatar !== '/static/personal_picture.png'"
          class="mine__avatar"
          :src="profileAvatar"
          mode="aspectFill"
        />
        <view v-else class="mine__avatar mine__avatar--placeholder">
          <text class="mine__avatar-placeholder-text">{{ (profile?.nickname || '用').charAt(0) }}</text>
        </view>
        <view class="mine__info">
          <view class="mine__name-row">
            <text class="mine__name">{{ profile?.nickname || '用户' }}</text>
            <image v-if="profile?.level" class="mine__level-icon" src="/static/vip_log.svg" mode="aspectFit" />
          </view>
          <text v-if="profile?.levelLabel" class="mine__level-label">{{ profile.levelLabel }}</text>
          <image class="mine__honor-badge" src="/static/new_friends.svg" mode="aspectFit" />
          <!-- 标签 -->
          <view v-if="profileTags.length" class="mine__tags">
            <view v-for="tag in profileTags" :key="tag" class="mine__tag">
              <text>{{ tag }}</text>
            </view>
          </view>
        </view>
        <image class="mine__edit-icon" src="/static/editing.svg" mode="aspectFit" @tap="editProfile" />
      </view>
    </view>

    <!-- 统计数据卡片 -->
    <view class="mine__stats-card">
      <view class="mine__stats">
        <view class="mine__stat">
          <text class="mine__stat-num">{{ profile?.following || 0 }}</text>
          <text class="mine__stat-label">关注</text>
        </view>
        <view class="mine__stat-divider" />
        <view class="mine__stat">
          <text class="mine__stat-num">{{ profile?.followers || 0 }}</text>
          <text class="mine__stat-label">被关注</text>
        </view>
        <view class="mine__stat-divider" />
        <view class="mine__stat">
          <text class="mine__stat-num">{{ profile?.likes || 0 }}</text>
          <text class="mine__stat-label">赞与收藏</text>
        </view>
      </view>
    </view>

    <!-- 功能工作台卡片 -->
    <view class="mine__workbench-card">
      <text class="mine__workbench-title">我的服务</text>
      <view class="mine__workbench">
        <view class="mine__workbench-grid">
          <view class="mine__workbench-item" @tap="onWorkbenchTap('mission')">
            <image class="mine__workbench-icon" src="/static/mission_center.svg" mode="aspectFit" />
          </view>
          <view class="mine__workbench-item" @tap="onWorkbenchTap('activity')">
            <image class="mine__workbench-icon" src="/static/activity.svg" mode="aspectFit" />
          </view>
          <view class="mine__workbench-item" @tap="onWorkbenchTap('moment')">
            <image class="mine__workbench-icon" src="/static/my_moment.svg" mode="aspectFit" />
          </view>
          <view class="mine__workbench-item" @tap="onWorkbenchTap('vertification')">
            <image class="mine__workbench-icon" src="/static/vertification.svg" mode="aspectFit" />
          </view>
          <view class="mine__workbench-item" @tap="onWorkbenchTap('clubs')">
            <image class="mine__workbench-icon" src="/static/clubs.svg" mode="aspectFit" />
          </view>
          <view class="mine__workbench-item" @tap="onWorkbenchTap('security')">
            <image class="mine__workbench-icon" src="/static/security.svg" mode="aspectFit" />
          </view>
          <view class="mine__workbench-item" @tap="onWorkbenchTap('connecting')">
            <image class="mine__workbench-icon" src="/static/connecting.svg" mode="aspectFit" />
          </view>
          <view class="mine__workbench-item" @tap="onWorkbenchTap('community')">
            <image class="mine__workbench-icon" src="/static/community.svg" mode="aspectFit" />
          </view>
        </view>
      </view>
    </view>

    <!-- Tab 切换 -->
    <view class="mine__tabs">
      <view
        v-for="tab in tabs"
        :key="tab"
        class="mine__tab"
        :class="{ 'mine__tab--active': activeTab === tab }"
        @tap="activeTab = tab"
      >
        <text class="mine__tab-text">{{ tab }}</text>
        <view v-if="activeTab === tab" class="mine__tab-indicator" />
      </view>
    </view>

    <!-- Tab 内容区 -->
    <scroll-view class="mine__content" scroll-y :show-scrollbar="false">
      <!-- 介绍 -->
      <view v-if="activeTab === '介绍'" class="mine__about">
        <text class="mine__about-text">{{ profile?.about || '这个人很懒，什么都没写~' }}</text>

        <!-- 功能菜单 -->
        <view class="mine__menu">
          <view
            v-for="item in menuItems"
            :key="item.label"
            class="mine__menu-item"
            @tap="onMenuTap(item)"
          >
            <image class="mine__menu-icon" :src="item.icon" mode="aspectFit" />
            <text class="mine__menu-label">{{ item.label }}</text>
            <text class="mine__menu-arrow">›</text>
          </view>
        </view>

        <!-- 设置入口 -->
        <view class="mine__settings" @tap="openSettings">
          <image class="mine__menu-icon" src="/static/eligo/icons/filter.svg" mode="aspectFit" />
          <text class="mine__menu-label">设置</text>
          <text class="mine__menu-arrow">›</text>
        </view>
      </view>

      <!-- 动态 -->
      <view v-if="activeTab === '动态'" class="mine__placeholder">
        <text class="mine__placeholder-text">暂无动态</text>
      </view>

      <!-- 相册 -->
      <view v-if="activeTab === '相册'" class="mine__placeholder">
        <text class="mine__placeholder-text">暂无相册</text>
      </view>

      <!-- 评价 -->
      <view v-if="activeTab === '评价'" class="mine__placeholder">
        <text class="mine__placeholder-text">暂无评价</text>
      </view>
    </scroll-view>

    <BottomNavigation current="profile" />
  </view>
</template>

<style scoped>
.mine {
  position: fixed;
  inset: 0;
  display: flex;
  flex-direction: column;
  background: #f6f7fb;
}

.mine__data-notice {
  position: absolute;
  top: 44px;
  right: 16px;
  z-index: 20;
  padding: 6px 12px;
  border-radius: 12px;
  background: rgba(255, 247, 230, 0.96);
  color: #8a4b00;
  font-size: 13px;
}

.mine__data-notice--error {
  background: rgba(255, 241, 240, 0.96);
  color: #a8121f;
}

.mine__header {
  position: relative;
  padding: 48px 16px 20px;
  overflow: hidden;
}

.mine__header-bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 170px;
  width: 100%;
  background: linear-gradient(135deg, #ffb149 0%, #f56551 100%);
  mask-image: linear-gradient(to bottom, rgba(0,0,0,1) 60%, rgba(0,0,0,0) 100%);
  -webkit-mask-image: linear-gradient(to bottom, rgba(0,0,0,1) 60%, rgba(0,0,0,0) 100%);
}

.mine__profile {
  position: relative;
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  gap: 14px;
}

.mine__avatar {
  width: 80px;
  height: 80px;
  border-radius: 16px;
  border: 3px solid #ffffff;
  background: #f0f1f4;
  flex-shrink: 0;
  box-sizing: border-box;
}

.mine__avatar--placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #ffb149 0%, #f56551 100%);
}

.mine__avatar-placeholder-text {
  font-size: 34px;
  font-weight: 600;
  color: #ffffff;
}

.mine__info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.mine__name-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
}

.mine__name {
  font-size: 22px;
  font-weight: 600;
  color: #111111;
}

.mine__level-icon {
  width: 36px;
  height: 18px;
}

.mine__level-label {
  font-size: 15px;
  color: #4a5568;
}

.mine__honor-badge {
  width: 72px;
  height: 22px;
  margin-top: 4px;
}

.mine__edit-icon {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
}

.mine__stats-card {
  margin: 16px 16px 0;
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.06);
  overflow: hidden;
}

.mine__stats-card .mine__stats {
  margin-top: 0;
}

.mine__stats {
  position: relative;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  gap: 0;
  padding: 18px 0;
}

.mine__stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.mine__stat-num {
  font-size: 22px;
  font-weight: 600;
  color: #111111;
}

.mine__stat-label {
  font-size: 16px;
  color: #4a5568;
}

.mine__stat-divider {
  width: 1px;
  height: 28px;
  background: #e3e6ea;
}

.mine__workbench-card {
  margin: 20px 16px 0;
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.06);
  overflow: hidden;
}

.mine__workbench-card .mine__workbench {
  margin-top: 0;
}

.mine__workbench-title {
  font-size: 19px;
  font-weight: 600;
  color: #111111;
  padding: 20px 16px 0;
}

.mine__workbench {
  background: #ffffff;
  border-radius: 16px;
  padding: 20px 16px;
}

.mine__tags {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 6px;
}

.mine__tag {
  padding: 6px 12px;
  border-radius: 12px;
  background: #f5f5f5;
  font-size: 15px;
  color: #1a1a1a;
}

.mine__workbench-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  row-gap: 20px;
  column-gap: 8px;
}

.mine__workbench-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 72px;
}

.mine__workbench-icon {
  width: 72px;
  height: 63px;
}

.mine__tabs {
  display: flex;
  flex-direction: row;
  background: #ffffff;
  margin-top: 8px;
  padding: 0 16px;
}

.mine__tab {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14px 0 10px;
  background: none;
  border: none;
}

.mine__tab-text {
  font-size: 19px;
  color: #4a5568;
}

.mine__tab--active .mine__tab-text {
  color: #111111;
  font-weight: 600;
}

.mine__tab-indicator {
  width: 24px;
  height: 3px;
  border-radius: 2px;
  background: #f56551;
  margin-top: 6px;
}

.mine__content {
  flex: 1;
  padding-bottom: 83px;
}

.mine__about {
  padding: 16px;
}

.mine__about-text {
  font-size: 17px;
  color: #1a1a1a;
  line-height: 26px;
  margin-bottom: 20px;
}

.mine__menu {
  background: #ffffff;
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 12px;
}

.mine__menu-item,
.mine__settings {
  display: flex;
  flex-direction: row;
  align-items: center;
  min-height: 56px;
  padding: 14px 16px;
  background: #ffffff;
  border-bottom: 1px solid #f0f1f4;
  box-sizing: border-box;
}

.mine__menu-item:last-child {
  border-bottom: none;
}

.mine__settings {
  border-radius: 12px;
  border-bottom: none;
}

.mine__menu-icon {
  width: 22px;
  height: 22px;
  margin-right: 12px;
  opacity: 0.85;
}

.mine__menu-label {
  flex: 1;
  font-size: 19px;
  color: #1a1a1a;
}

.mine__menu-arrow {
  font-size: 22px;
  color: #9aa3ad;
}

.mine__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: 80px;
}

.mine__placeholder-text {
  font-size: 17px;
  color: #666666;
}
</style>
