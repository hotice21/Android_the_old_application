<script setup>
defineProps({
  light: {
    type: Boolean,
    default: false,
  },
})
</script>

<template>
  <view class="status" :class="{ 'status--light': light }" aria-hidden="true">
    <text class="status__time">9:41</text>
    <view class="status__notch" />
    <view class="status__symbols">
      <view class="signal"><i /><i /><i /><i /></view>
      <view class="wifi"><i /></view>
      <view class="battery"><view class="battery__fill" /></view>
    </view>
  </view>
</template>

<style scoped>
.status {
  position: absolute;
  z-index: 80;
  top: 0;
  left: 0;
  right: 0;
  height: 47px;
  color: #080808;
  pointer-events: none;
}

.status--light { color: #ffffff; }

.status__time {
  position: absolute;
  left: 52px;
  top: 17px;
  font-family: "PingFang SC", sans-serif;
  font-size: 16px;
  line-height: 20px;
  font-weight: 700;
  letter-spacing: -0.2px;
}

.status__notch {
  position: absolute;
  top: 12px;
  left: 50%;
  width: 125px;
  height: 35px;
  border-radius: 20px;
  background: #000000;
  transform: translateX(-50%);
}

.status__symbols {
  position: absolute;
  top: 20px;
  right: 17px;
  display: flex;
  align-items: center;
  height: 12px;
  gap: 8px;
}

.signal {
  display: flex;
  align-items: flex-end;
  width: 17px;
  height: 11px;
  gap: 2px;
}

.signal i {
  display: block;
  width: 3px;
  border-radius: 2px;
  background: currentColor;
}

.signal i:nth-child(1) { height: 4px; }
.signal i:nth-child(2) { height: 6px; }
.signal i:nth-child(3) { height: 8px; }
.signal i:nth-child(4) { height: 11px; }

.wifi {
  position: relative;
  width: 17px;
  height: 11px;
  overflow: hidden;
}

.wifi::before,
.wifi::after,
.wifi i {
  content: "";
  position: absolute;
  left: 50%;
  border: 2px solid currentColor;
  border-color: currentColor transparent transparent transparent;
  border-radius: 50%;
  transform: translateX(-50%);
}

.wifi::before { top: 0; width: 17px; height: 15px; }
.wifi::after { top: 4px; width: 10px; height: 9px; }
.wifi i { top: 8px; width: 3px; height: 3px; border-width: 2px; }

.battery {
  position: relative;
  width: 22px;
  height: 10px;
  padding: 1px;
  border: 1px solid currentColor;
  border-radius: 3px;
  opacity: 0.95;
}

.battery::after {
  content: "";
  position: absolute;
  top: 2px;
  right: -3px;
  width: 2px;
  height: 5px;
  border-radius: 0 2px 2px 0;
  background: currentColor;
}

.battery__fill {
  width: 17px;
  height: 6px;
  border-radius: 1px;
  background: currentColor;
}
</style>
