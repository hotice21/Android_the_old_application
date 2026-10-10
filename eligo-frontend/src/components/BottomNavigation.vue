<script setup>
import { ref, computed } from "vue"
import { requireLogin } from "../stores/entry"

const emit = defineEmits(["plus"])

const props = defineProps({
  current: {
    type: String,
    default: "plaza",
  },
})

// 4 个 tab + 中间凸起的发布按钮
// glyph 对应页面传入的 current prop
const items = [
  { label: "活动", glyph: "plaza", page: "/pages/plaza/index" },
  { label: "聊天", glyph: "chat", page: "" },
  // 中间留空给 plus button
  { label: "我的", glyph: "profile", page: "/pages/mine/index" },
]

function onTabTap(item) {
  if (!item.glyph || item.glyph === props.current) return
  if (!requireLogin()) return
  if (item.page) {
    uni.reLaunch({ url: item.page })
  } else {
    uni.showToast({ title: `${item.label}功能开发中`, icon: "none" })
  }
}

function onPlusTap() {
  if (!requireLogin()) return
  emit("plus")
}
</script>

<template>
  <view class="bottom-nav">
    <!-- 左：活动 -->
    <view
      class="bottom-nav__item"
      :class="{ 'bottom-nav__item--active': props.current === 'plaza' }"
      @tap="onTabTap(items[0])"
    >
      <text class="bottom-nav__emoji">{{ props.current === 'plaza' ? '🎯' : '🎯' }}</text>
      <text class="bottom-nav__label">活动</text>
    </view>

    <!-- 中左：聊天 -->
    <view
      class="bottom-nav__item"
      :class="{ 'bottom-nav__item--active': props.current === 'chat' }"
      @tap="onTabTap(items[1])"
    >
      <text class="bottom-nav__emoji">{{ props.current === 'chat' ? '💬' : '💭' }}</text>
      <text class="bottom-nav__label">聊天</text>
    </view>

    <!-- 中间：发布按钮（凸起） -->
    <view class="bottom-nav__center">
      <button class="bottom-nav__plus" aria-label="发布活动或动态" @tap="onPlusTap">
        <text class="bottom-nav__plus-icon">+</text>
      </button>
      <text class="bottom-nav__center-label">发帖</text>
    </view>

    <!-- 中右：占位（让 plus 居中） -->
    <view class="bottom-nav__placeholder" />

    <!-- 右：我的 -->
    <view
      class="bottom-nav__item bottom-nav__item--right"
      :class="{ 'bottom-nav__item--active': props.current === 'profile' }"
      @tap="onTabTap(items[2])"
    >
      <text class="bottom-nav__emoji">{{ props.current === 'profile' ? '👤' : '👤' }}</text>
      <text class="bottom-nav__label">我的</text>
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
  color: #f56551;
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

.bottom-nav__center {
  position: relative;
  flex: 1 1 0;
  min-width: 60px;
  height: 80px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.bottom-nav__placeholder {
  flex: 1 1 0;
  min-width: 60px;
  height: 80px;
}

.bottom-nav__plus {
  position: absolute;
  top: -16px;
  left: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: linear-gradient(140deg, #f56551, #ffb149);
  box-shadow: 0 8px 18px rgba(245, 101, 81, 0.3);
  transform: translateX(-50%);
  box-sizing: border-box;
}

.bottom-nav__plus::after {
  border: 0;
}

.bottom-nav__plus-icon {
  font-size: 32px;
  color: #ffffff;
  font-weight: 300;
  line-height: 1;
}

.bottom-nav__center-label {
  position: absolute;
  bottom: 10px;
  font-size: 13px;
  font-weight: 500;
  color: #999999;
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
