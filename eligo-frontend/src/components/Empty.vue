<script setup>
defineProps({
  text: {
    type: String,
    default: "暂无内容",
  },
  icon: {
    type: String,
    default: "",
  },
})
</script>

<template>
  <view class="empty">
    <image
      v-if="icon"
      class="empty__icon"
      :src="icon"
      mode="aspectFit"
    />
    <view v-else class="empty__placeholder-icon">
      <text class="empty__placeholder-glyph">∅</text>
    </view>
    <text class="empty__text">{{ text }}</text>
    <slot />
  </view>
</template>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 32px;
}

.empty__icon {
  width: 96px;
  height: 96px;
  margin-bottom: 20px;
  opacity: 0.4;
}

.empty__placeholder-icon {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: #eeeef0;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
}

.empty__placeholder-glyph {
  font-size: 36px;
  color: #bbbbbb;
}

.empty__text {
  font-size: 17px;
  color: #999999;
  text-align: center;
  line-height: 26px;
}
</style>
