<script setup>
import { requireLogin } from "../stores/entry"

const props = defineProps({
  current: {
    type: String,
    default: "plaza",
  },
})

// 4 个平 tab：发帖 → 活动 → 聊天 → 我的
const items = [
  { label: "发帖", glyph: "post", page: "/pages/dynamic/index" },
  { label: "活动", glyph: "plaza", page: "/pages/plaza/index" },
  { label: "聊天", glyph: "chat", page: "/pages/chat/index" },
  { label: "我的", glyph: "profile", page: "/pages/mine/index" },
]

const glyphEmoji = {
  post: "📝",
  plaza: "🎯",
  chat: "💬",
  profile: "👤",
}

function onTabTap(item) {
  if (!item.glyph || item.glyph === props.current) return
  if (!requireLogin()) return
  if (item.page) {
    uni.reLaunch({ url: item.page })
  } else {
    uni.showToast({ title: `${item.label}功能开发中`, icon: "none" })
  }
}
</script>

<template>
  <view class="bottom-nav">
    <view
      v-for="item in items"
      :key="item.glyph"
      class="bottom-nav__item"
      :class="{ 'bottom-nav__item--active': props.current === item.glyph }"
      @tap="onTabTap(item)"
    >
      <text class="bottom-nav__emoji">{{ glyphEmoji[item.glyph] }}</text>
      <text class="bottom-nav__label">{{ item.label }}</text>
    </view>
    <view class="home-indicator" />
  </view>
</template>

<style scoped>
.bottom-nav {
  position: fixed;
  z-index: 60;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  align-items: flex-end;
  box-sizing: border-box;
  height: 100px;
  padding: 8px 0 0;
  background: #ffffff;
  font-family: "PingFang SC", sans-serif;
}

.bottom-nav__item {
  position: relative;
  flex: 1 1 0;
  min-width: 60px;
  height: 80px;
  padding: 12px 6px;
  display: flex;
  flex-direction: column;
  align-items: center;
  color: #999999;
  transition: color 0.2s;
}

.bottom-nav__item--active {
  color: #fe5f02;
}

.bottom-nav__emoji {
  font-size: 26px;
  line-height: 26px;
  margin-bottom: 4px;
}

.bottom-nav__label {
  font-size: 13px;
  font-weight: 500;
}

.home-indicator {
  position: absolute;
  bottom: 8px;
  left: 50%;
  width: 134px;
  height: 5px;
  border-radius: 3px;
  background: #000000;
  transform: translateX(-50%);
}
</style>
