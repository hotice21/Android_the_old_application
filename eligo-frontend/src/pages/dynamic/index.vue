<script setup>
import { onMounted, ref } from "vue"
import BottomNavigation from "../../components/BottomNavigation.vue"
import { mockDynamics } from "../../mock/dynamic"

const tabs = ["推荐", "关注"]
const activeTab = ref("推荐")
const dynamics = ref([])
const loading = ref(false)
const refreshing = ref(false)

async function fetchDynamics(reset = false) {
  loading.value = true
  try {
    // M4 模块未实现，直接使用 mock 数据
    dynamics.value = mockDynamics
  } finally {
    loading.value = false
  }
}

async function onRefresh() {
  refreshing.value = true
  await fetchDynamics(true)
  refreshing.value = false
}

function onTabChange(tab) {
  activeTab.value = tab
  fetchDynamics(true)
}

async function onLike(item) {
  item.isLiked = !item.isLiked
  item.likes += item.isLiked ? 1 : -1
  // M4 模块未实现，仅本地状态切换
}

function onComment(item) {
  uni.showToast({ title: "评论功能开发中", icon: "none" })
}

function onShare(item) {
  uni.showToast({ title: "分享功能开发中", icon: "none" })
}

onMounted(() => {
  fetchDynamics()
})
</script>

<template>
  <view class="dynamic">
    <view class="dynamic__header">
      <text class="dynamic__title">发现</text>
    </view>

    <view class="dynamic__tabs">
      <button
        v-for="tab in tabs"
        :key="tab"
        class="dynamic__tab"
        :class="{ 'dynamic__tab--active': activeTab === tab }"
        @tap="onTabChange(tab)"
      >
        <text>{{ tab }}</text>
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
        <text class="dynamic__empty-text">暂无动态</text>
      </view>

      <view v-else class="dynamic__list">
        <view
          v-for="(item, index) in dynamics"
          :key="item.id"
          class="dynamic-card"
        >
          <view class="dynamic-card__header">
            <view class="dynamic-card__avatar">
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

          <scroll-view
            v-if="item.images && item.images.length"
            class="dynamic-card__images"
            scroll-x
            :show-scrollbar="false"
          >
            <view class="dynamic-card__images-inner">
              <view
                v-for="(img, idx) in item.images"
                :key="idx"
                class="dynamic-card__image"
                :class="'dynamic-card__image--' + (idx % 5)"
              />
            </view>
          </scroll-view>

          <view class="dynamic-card__actions">
            <button
              class="dynamic-card__action"
              :class="{ 'dynamic-card__action--liked': item.isLiked }"
              @tap="onLike(item)"
            >
              <image src="/static/eligo/icons/heart.svg" mode="aspectFit" class="dynamic-card__action-icon" />
              <text>{{ item.likes }}</text>
            </button>
            <button class="dynamic-card__action" @tap="onComment(item)">
              <image src="/static/eligo/icons/chat-bubble.svg" mode="aspectFit" class="dynamic-card__action-icon" />
              <text>{{ item.comments }}</text>
            </button>
            <button class="dynamic-card__action" @tap="onShare(item)">
              <image src="/static/eligo/icons/share.svg" mode="aspectFit" class="dynamic-card__action-icon" />
              <text>分享</text>
            </button>
          </view>
        </view>
      </view>
    </scroll-view>

    <BottomNavigation current="discover" />
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

.dynamic__header {
  padding: 56px 20px 16px;
  background: #ffffff;
}

.dynamic__title {
  font-size: 26px;
  font-weight: 700;
  color: #111111;
}

.dynamic__tabs {
  display: flex;
  flex-direction: row;
  gap: 24px;
  padding: 0 20px 16px;
  background: #ffffff;
}

.dynamic__tab {
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

.dynamic__tab--active {
  color: #111111;
  font-weight: 700;
}

.dynamic__content {
  flex: 1;
  padding-bottom: 83px;
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
  border-radius: 12px;
}

.dynamic-card__header {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-bottom: 14px;
}

.dynamic-card__avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  margin-right: 14px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.dynamic-card__avatar-text {
  font-size: 22px;
  font-weight: 600;
  color: #ffffff;
}

.dynamic-card:nth-child(6n+1) .dynamic-card__avatar {
  background: #ff6b6b;
}
.dynamic-card:nth-child(6n+2) .dynamic-card__avatar {
  background: #4ecdc4;
}
.dynamic-card:nth-child(6n+3) .dynamic-card__avatar {
  background: #45b7d1;
}
.dynamic-card:nth-child(6n+4) .dynamic-card__avatar {
  background: #f9a826;
}
.dynamic-card:nth-child(6n+5) .dynamic-card__avatar {
  background: #a78bfa;
}
.dynamic-card:nth-child(6n+6) .dynamic-card__avatar {
  background: #51cf66;
}

.dynamic-card__user {
  flex: 1;
}

.dynamic-card__name-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
}

.dynamic-card__name {
  font-size: 19px;
  font-weight: 600;
  color: #111111;
}

.dynamic-card__badge {
  font-size: 14px;
  color: #ffffff;
  background: linear-gradient(140deg, #f56551, #ffb149);
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 500;
}

.dynamic-card__time {
  font-size: 15px;
  color: #6b7280;
  margin-top: 4px;
}

.dynamic-card__content {
  font-size: 18px;
  color: #1a1a1a;
  line-height: 26px;
  margin-bottom: 14px;
}

.dynamic-card__images {
  margin-bottom: 14px;
}

.dynamic-card__images-inner {
  display: flex;
  flex-direction: row;
  gap: 10px;
}

.dynamic-card__image {
  width: 130px;
  height: 130px;
  border-radius: 10px;
  flex-shrink: 0;
}

.dynamic-card__image--0 {
  background: linear-gradient(135deg, #ff6b6b, #feca57);
}
.dynamic-card__image--1 {
  background: linear-gradient(135deg, #4ecdc4, #45b7d1);
}
.dynamic-card__image--2 {
  background: linear-gradient(135deg, #a78bfa, #ff8cc8);
}
.dynamic-card__image--3 {
  background: linear-gradient(135deg, #51cf66, #4ecdc4);
}
.dynamic-card__image--4 {
  background: linear-gradient(135deg, #ff8cc8, #a78bfa);
}

.dynamic-card__actions {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 28px;
  padding-top: 12px;
  border-top: 1px solid #eef0f3;
}

.dynamic-card__action {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 6px;
  background: none;
  border: none;
  padding: 0 8px;
  min-height: 48px;
  font-size: 16px;
  color: #4a5568;
  font-weight: 500;
}

.dynamic-card__action--liked {
  color: #f56551;
}

.dynamic-card__action-icon {
  width: 22px;
  height: 22px;
}
</style>
