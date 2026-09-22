<script setup>
import { onBeforeUnmount, onMounted } from "vue"
import { resolveEntryPage } from "../../stores/entry"

let timer

function continueFlow() {
  clearTimeout(timer)
  uni.redirectTo({ url: resolveEntryPage() })
}

onMounted(() => {
  timer = setTimeout(continueFlow, 1400)
})

onBeforeUnmount(() => clearTimeout(timer))
</script>

<template>
  <view
    class="brand"
    aria-label="发现更多志同道合的朋友"
    role="button"
    @tap="continueFlow"
  >
    <view class="brand__content">
      <view class="brand__art" />
      <text class="brand__wordmark">Eligo</text>
    </view>
    <view class="brand__home-indicator" />
  </view>
</template>

<style scoped>
.brand {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ffffff;
}

.brand__content {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.brand__art {
  width: 220px;
  height: 220px;
  border-radius: 40px;
  background: linear-gradient(135deg, #ffb347 0%, #ff7a00 100%);
  box-shadow: 0 12px 32px rgba(255, 122, 0, 0.25);
}

.brand__wordmark {
  margin-top: 32px;
  color: #1a1a1a;
  font-family: Roboto, sans-serif;
  font-size: 56px;
  font-weight: 700;
  line-height: 64px;
  text-align: center;
}

.brand__home-indicator {
  position: absolute;
  bottom: 20px;
  left: 50%;
  width: 145px;
  height: 5px;
  border-radius: 3px;
  background: #000000;
  transform: translateX(-50%);
}
</style>
