<script setup>
import { onBeforeUnmount, onMounted } from "vue"

let timer

function continueFlow() {
  clearTimeout(timer)
  uni.redirectTo({ url: "/pages/brand/index" })
}

onMounted(() => {
  timer = setTimeout(continueFlow, 900)
})

onBeforeUnmount(() => clearTimeout(timer))
</script>

<template>
  <view
    class="launch"
    aria-label="Eligo 启动页"
    role="button"
    @tap="continueFlow"
  >
    <view class="launch__content">
      <view class="launch__logo-card" />
      <text class="launch__wordmark">Eligo</text>
    </view>
  </view>
</template>

<style scoped>
.launch {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ffffff;
}

.launch__content {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.launch__logo-card {
  width: 160px;
  height: 160px;
  border-radius: 36px;
  background: linear-gradient(135deg, #ffb347 0%, #ff7a00 100%);
  box-shadow: 0 12px 32px rgba(255, 122, 0, 0.25);
}

.launch__wordmark {
  margin-top: 32px;
  color: #1a1a1a;
  font-family: Roboto, sans-serif;
  font-size: 56px;
  font-weight: 700;
  line-height: 64px;
  text-align: center;
}
</style>
