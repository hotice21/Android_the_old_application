<script setup>
defineProps({
  item: {
    type: Object,
    required: true,
  },
})

const emit = defineEmits(["open", "join"])
</script>

<template>
  <view class="activity-card" @tap="emit('open', item)">
    <!-- Photo area on top -->
    <view class="activity-card__photo">
      <view
        class="activity-card__image placeholder-image placeholder-image--warm"
      />

      <!-- Date badge overlaid on photo -->
      <view class="date-badge">
        <template v-if="item.datePrefix">
          <text class="date-badge__large">{{ item.datePrefix }}</text>
          <view class="date-badge__underline" />
          <text class="date-badge__meta">
            {{ item.dateLabel ? `${item.dateLabel} ${item.cardTime}` : item.cardTime }}
          </text>
        </template>
        <template v-else>
          <text class="date-badge__large">{{ item.cardTime }}</text>
          <view class="date-badge__underline" />
          <text class="date-badge__label">{{ item.dateLabel }}</text>
        </template>
      </view>

      <!-- Free + distance tags overlaid on photo -->
      <view class="photo-tags">
        <text class="photo-tags__free">免费</text>
        <text class="photo-tags__distance">{{ item.distance }}</text>
      </view>
    </view>

    <!-- Body section below -->
    <view class="activity-card__body">
      <view class="organizer">
        <view class="organizer__avatar placeholder-image placeholder-image--cool" />
        <text class="organizer__verified">✓</text>
        <text class="organizer__name">{{ item.organizer }}</text>
      </view>

      <text class="activity-card__title">{{ item.title }}</text>

      <view class="activity-card__place">
        <text class="activity-card__place-text">深圳公园 · {{ item.distance }}</text>
        <text class="activity-card__chevron">›</text>
      </view>

      <view class="activity-card__footer">
        <view class="joined">
          <view class="joined__avatars">
            <view class="placeholder-image placeholder-image--soft" />
            <view class="placeholder-image placeholder-image--fresh" />
            <view class="placeholder-image placeholder-image--warm" />
            <view class="placeholder-image placeholder-image--cool" />
          </view>
          <text class="joined__text">{{ item.registrations }}人报名</text>
        </view>
        <button class="join-btn" @tap.stop="emit('join', item)">报名</button>
      </view>
    </view>
  </view>
</template>

<style scoped>
.activity-card {
  display: flex;
  flex-direction: column;
  width: 100%;
  background: #ffffff;
  border: 2px solid #d0d4da;
  border-radius: 20px;
  overflow: hidden;
  font-family: "PingFang SC", sans-serif;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

/* Placeholder image variants */
.placeholder-image {
  display: block;
  background: linear-gradient(135deg, #e8ebef 0%, #d8dde3 100%);
}

.placeholder-image--warm {
  background: linear-gradient(135deg, #ffb149 0%, #f56551 100%);
}

.placeholder-image--cool {
  background: linear-gradient(135deg, #4a90e2 0%, #357abd 100%);
}

.placeholder-image--soft {
  background: linear-gradient(135deg, #c39bd3 0%, #a569bd 100%);
}

.placeholder-image--fresh {
  background: linear-gradient(135deg, #48c9b0 0%, #26a69a 100%);
}

/* Photo area */
.activity-card__photo {
  position: relative;
  width: 100%;
  height: 200px;
  overflow: hidden;
}

.activity-card__image {
  width: 100%;
  height: 100%;
}

/* Date badge overlaid on photo */
.date-badge {
  position: absolute;
  top: 16px;
  left: 16px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 12px 16px;
  background: rgba(0, 0, 0, 0.6);
  border-radius: 12px;
}

.date-badge__large {
  color: #ffffff;
  font-size: 24px;
  line-height: 28px;
  font-weight: 700;
  white-space: nowrap;
}

.date-badge__underline {
  width: 24px;
  height: 2px;
  margin: 6px 0;
  background: #ffffff;
}

.date-badge__meta,
.date-badge__label {
  color: #ffffff;
  font-size: 13px;
  line-height: 16px;
  font-weight: 600;
  white-space: nowrap;
}

/* Photo tags */
.photo-tags {
  position: absolute;
  left: 16px;
  bottom: 16px;
  display: flex;
  gap: 8px;
}

.photo-tags__free {
  padding: 8px 14px;
  color: #f56551;
  background: #ffffff;
  border-radius: 10px;
  font-size: 13px;
  line-height: 16px;
  font-weight: 700;
}

.photo-tags__distance {
  padding: 8px 14px;
  color: #ffffff;
  background: rgba(0, 0, 0, 0.7);
  border-radius: 10px;
  font-size: 13px;
  line-height: 16px;
  font-weight: 600;
}

/* Body section */
.activity-card__body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px 20px 24px;
}

.organizer {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 48px;
}

.organizer__avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 2px solid #ffffff;
  flex-shrink: 0;
}

.organizer__verified {
  position: absolute;
  left: 32px;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: 2px solid #ffffff;
  border-radius: 50%;
  color: #ffffff;
  background: #f56551;
  font-size: 12px;
  line-height: 14px;
  font-weight: 700;
}

.organizer__name {
  margin-left: 14px;
  overflow: hidden;
  color: #2b2f36;
  font-size: 15px;
  line-height: 20px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.activity-card__title {
  display: -webkit-box;
  overflow: hidden;
  color: #1a1d21;
  font-size: 24px;
  line-height: 32px;
  font-weight: 700;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.activity-card__place {
  display: flex;
  align-items: center;
  min-height: 56px;
}

.activity-card__place-text {
  color: #2b2f36;
  font-size: 18px;
  line-height: 24px;
  font-weight: 500;
}

.activity-card__chevron {
  margin-left: 8px;
  color: #2b2f36;
  font-size: 24px;
  line-height: 24px;
  font-weight: 600;
}

/* Footer */
.activity-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 4px;
}

.joined {
  display: flex;
  align-items: center;
  min-width: 0;
}

.joined__avatars {
  display: flex;
  flex-shrink: 0;
}

.joined__avatars .placeholder-image {
  width: 36px;
  height: 36px;
  margin-left: -10px;
  border: 2px solid #ffffff;
  border-radius: 50%;
}

.joined__avatars .placeholder-image:first-child {
  margin-left: 0;
}

.joined__text {
  margin-left: 14px;
  color: #2b2f36;
  font-size: 13px;
  line-height: 18px;
  font-weight: 600;
  white-space: nowrap;
}

.join-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 120px;
  min-height: 56px;
  padding: 0 28px;
  border: none;
  border-radius: 16px;
  color: #ffffff;
  background: linear-gradient(140deg, #f56551 0%, #ffb149 100%);
  font-family: "PingFang SC", sans-serif;
  font-size: 18px;
  line-height: 24px;
  font-weight: 700;
  flex-shrink: 0;
}
</style>
