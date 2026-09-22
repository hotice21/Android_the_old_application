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

const items = [
  { label: "广场", icon: "/static/eligo/icons/tab-plaza-inactive.svg", activeIcon: "/static/eligo/icons/tab-plaza.svg", glyph: "plaza", page: "/pages/plaza/index" },
  { label: "发现", icon: "/static/eligo/icons/tab-discover.svg", activeIcon: "/static/eligo/icons/tab-discover-active.svg", glyph: "discover", page: "/pages/dynamic/index" },
  { label: "", icon: "", activeIcon: "", glyph: "", page: "" },
  { label: "动态", icon: "/static/eligo/icons/tab-feed.svg", activeIcon: "/static/eligo/icons/tab-feed-active.svg", glyph: "feed", page: "/pages/friend/index" },
  { label: "我的", icon: "/static/eligo/icons/tab-profile.svg", activeIcon: "/static/eligo/icons/tab-profile-active.svg", glyph: "profile", page: "/pages/mine/index" },
]

function onTabTap(item) {
  if (!item.glyph || item.glyph === props.current) return
  if (!requireLogin()) return
  if (item.page) {
    uni.reLaunch({ url: item.page })
  }
}

function onPlusTap() {
  if (!requireLogin()) return
  emit("plus")
}
</script>

<template>
  <view class="bottom-nav">
    <view
      v-for="item in items"
      :key="item.label || 'plus'"
      class="bottom-nav__item"
      :class="{ 'bottom-nav__item--active': item.glyph === current }"
      @tap="onTabTap(item)"
    >
      <template v-if="item.glyph">
        <image
          class="bottom-nav__icon"
          :class="[`bottom-nav__icon--${item.glyph}`, { 'bottom-nav__icon--active': item.glyph === current }]"
          :src="item.glyph === current ? item.activeIcon : item.icon"
          mode="aspectFit"
        />
        <text>{{ item.label }}</text>
      </template>
    </view>
    <button class="bottom-nav__plus" aria-label="发布活动或动态" @tap="onPlusTap">
      <image src="/static/eligo/icons/add.svg" mode="aspectFit" />
    </button>
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
  padding: 8px 6px;
  color: #888888;
  font-size: 16px;
  line-height: 16px;
}

.bottom-nav__item text {
  position: absolute;
  top: 46px;
  left: 50%;
  width: 60px;
  height: 20px;
  color: #888888;
  font-size: 16px;
  font-weight: 700;
  line-height: 20px;
  text-align: center;
  transform: translateX(-50%);
}

.bottom-nav__item--active text {
  color: #111111;
}

.bottom-nav__icon {
  position: absolute;
  top: 8px;
  left: 50%;
  width: 32px;
  height: 32px;
  transform: translateX(-50%);
}

.bottom-nav__icon--active {
  /* 选中态：直接用 active SVG，无需 filter */
}

.bottom-nav__icon--discover {
  width: 33.77px;
  height: 32.49px;
}

.bottom-nav__plus {
  position: absolute;
  top: -14px;
  left: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 24px;
  background: linear-gradient(140deg, #f56551, #ffb149);
  box-shadow: 0 8px 18px rgba(245, 101, 81, 0.3);
  transform: translateX(-50%);
}

.bottom-nav__plus image {
  width: 21px;
  height: 21px;
  filter: invert(1);
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
