<script setup>
defineProps({
  text: {
    type: String,
    default: "加载中...",
  },
  size: {
    type: String,
    default: "medium",
    validator: (v) => ["small", "medium", "large"].includes(v),
  },
})
</script>

<template>
  <view class="loading" :class="`loading--${size}`">
    <view class="loading__spinner" />
    <text v-if="text" class="loading__text">{{ text }}</text>
  </view>
</template>

<style scoped>
.loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.loading--small {
  padding: 20px;
}

.loading--large {
  padding: 80px;
}

.loading__spinner {
  width: 36px;
  height: 36px;
  border: 4px solid #e8e8ec;
  border-top-color: #fe5f02;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.loading--small .loading__spinner {
  width: 24px;
  height: 24px;
  border-width: 3px;
}

.loading--large .loading__spinner {
  width: 48px;
  height: 48px;
  border-width: 5px;
}

.loading__text {
  margin-top: 16px;
  font-size: 17px;
  color: #666666;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
