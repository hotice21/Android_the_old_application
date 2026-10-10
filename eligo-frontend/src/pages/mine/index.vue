<script setup>
import { ref } from "vue"
import { onShow } from "@dcloudio/uni-app"
import BottomNavigation from "../../components/BottomNavigation.vue"
import { getMyProfile } from "../../api/profile"

const profile = ref(null)
const loading = ref(true)

/** 性别显示映射 */
function genderLabel(gender) {
  if (gender === "MALE") return "男"
  if (gender === "FEMALE") return "女"
  return ""
}

async function fetchProfile() {
  loading.value = true
  try {
    profile.value = await getMyProfile()
  } catch (error) {
    console.error("加载profile失败", error)
    uni.showToast({ title: "加载失败", icon: "none" })
  } finally {
    loading.value = false
  }
}

function editProfile() {
  uni.navigateTo({ url: "/pages/profile-edit/index" })
}

function openFriends() {
  uni.navigateTo({ url: "/pages/mine/friends/index" })
}

function openParticipations() {
  uni.navigateTo({ url: "/pages/mine/participations/index" })
}

function openFavorites() {
  uni.navigateTo({ url: "/pages/mine/favorites/index" })
}

function openAccountSettings() {
  uni.navigateTo({ url: "/pages/mine/account-settings/index" })
}

function logout() {
  uni.showModal({
    title: "提示",
    content: "确定要退出登录吗？",
    success(res) {
      if (res.confirm) {
        uni.removeStorageSync("eligo.access-token")
        uni.removeStorageSync("eligo.entry-guide-seen.v1")
        uni.reLaunch({ url: "/pages/onboarding/index" })
      }
    },
  })
}

onShow(() => {
  fetchProfile()
})
</script>

<template>
  <view class="mine">
    <!-- 顶部个人信息区 -->
    <view class="mine__header">
      <view class="mine__header-bg" />
      <view class="mine__profile">
        <!-- 头像：用昵称首字做占位 -->
        <view class="mine__avatar mine__avatar--placeholder">
          <text class="mine__avatar-text">{{ (profile?.nickname || '用').charAt(0) }}</text>
        </view>
        <view class="mine__info">
          <view class="mine__name-row">
            <text class="mine__name">{{ profile?.nickname || '用户' }}</text>
            <text v-if="profile?.gender" class="mine__gender">{{ genderLabel(profile.gender) }}</text>
          </view>
          <text v-if="profile?.bio" class="mine__bio">{{ profile.bio }}</text>
          <text v-if="!profile?.bio" class="mine__bio mine__bio--empty">这个人很懒，什么都没写~</text>
        </view>
        <view class="mine__edit-btn" @tap="editProfile">
          <text>编辑资料</text>
        </view>
      </view>
    </view>

    <!-- 中间入口卡片 -->
    <view class="mine__card">
      <view class="mine__entry" @tap="openParticipations">
        <text class="mine__entry-icon">🎯</text>
        <text class="mine__entry-label">我报名的活动</text>
        <text class="mine__entry-arrow">›</text>
      </view>
      <view class="mine__divider" />
      <view class="mine__entry" @tap="openFavorites">
        <text class="mine__entry-icon">⭐</text>
        <text class="mine__entry-label">我收藏的活动</text>
        <text class="mine__entry-arrow">›</text>
      </view>
      <view class="mine__divider" />
      <view class="mine__entry" @tap="openFriends">
        <text class="mine__entry-icon">👥</text>
        <text class="mine__entry-label">我的好友</text>
        <text class="mine__entry-arrow">›</text>
      </view>
    </view>

    <!-- 底部设置项 -->
    <view class="mine__card mine__card--settings">
      <view class="mine__entry" @tap="openAccountSettings">
        <text class="mine__entry-icon">⚙️</text>
        <text class="mine__entry-label">账号设置</text>
        <text class="mine__entry-arrow">›</text>
      </view>
      <view class="mine__divider" />
      <view class="mine__entry mine__entry--danger" @tap="logout">
        <text class="mine__entry-icon">🚪</text>
        <text class="mine__entry-label">退出登录</text>
        <text class="mine__entry-arrow">›</text>
      </view>
    </view>

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
  overflow-y: auto;
}

/* 顶部 */
.mine__header {
  position: relative;
  padding: 60px 16px 28px;
  overflow: hidden;
}

.mine__header-bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 200px;
  width: 100%;
  background: linear-gradient(135deg, #ffb149 0%, #f56551 100%);
  mask-image: linear-gradient(to bottom, rgba(0,0,0,1) 60%, rgba(0,0,0,0) 100%);
  -webkit-mask-image: linear-gradient(to bottom, rgba(0,0,0,1) 60%, rgba(0,0,0,0) 100%);
}

.mine__profile {
  position: relative;
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 14px;
}

.mine__avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
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

.mine__avatar-text {
  font-size: 30px;
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

.mine__gender {
  font-size: 13px;
  padding: 2px 8px;
  border-radius: 10px;
  background: rgba(255,255,255,0.3);
  color: #ffffff;
}

.mine__bio {
  font-size: 14px;
  color: rgba(0,0,0,0.7);
  line-height: 20px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.mine__bio--empty {
  color: rgba(0,0,0,0.4);
  font-style: italic;
}

.mine__edit-btn {
  padding: 6px 16px;
  border-radius: 16px;
  background: rgba(255,255,255,0.25);
  border: 1px solid rgba(255,255,255,0.6);
  flex-shrink: 0;
}

.mine__edit-btn text {
  font-size: 13px;
  color: #ffffff;
  font-weight: 500;
}

/* 卡片通用 */
.mine__card {
  margin: 16px 16px 0;
  background: #ffffff;
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0,0,0,0.05);
}

.mine__card--settings {
  margin-bottom: 120px;
}

.mine__entry {
  display: flex;
  flex-direction: row;
  align-items: center;
  min-height: 54px;
  padding: 14px 16px;
  background: #ffffff;
}

.mine__entry-icon {
  font-size: 22px;
  margin-right: 12px;
}

.mine__entry-label {
  flex: 1;
  font-size: 16px;
  color: #1a1a1a;
}

.mine__entry--danger .mine__entry-label {
  color: #d9402a;
}

.mine__entry-arrow {
  font-size: 22px;
  color: #c0c4cc;
}

.mine__divider {
  height: 1px;
  margin-left: 50px;
  background: #f0f1f4;
}
</style>
