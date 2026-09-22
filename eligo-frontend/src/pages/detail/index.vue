<script setup>
import { ref } from "vue"
import ActivityCard from "../../components/ActivityCard.vue"
import { activityFeed, primaryEvent } from "../../mock/events"
import { requireLogin } from "../../stores/entry"

const favorite = ref(false)
const recommendations = [activityFeed[1], activityFeed[2]]

const introduction =
  "谁懂啊！去牛背山爬山还能薅免费奖牌，粉红兔子 IP 款 + 云海星途限定款全都有✨\n\n" +
  "日常免费粉红兔子奖牌\n" +
  "活动每天都能冲！直接去牛背山景区扫码打卡，跟着线下指引开启挑战就能领。每日奖牌限量，先到先得。⚠️线下开启挑战赛记得勾选保险哦。\n\n" +
  "「云海星途」OPPO 限定奖牌领取四步走\n" +
  "1️⃣ 下单 1 元挑战补给包，走完指定打卡路线\n" +
  "2️⃣ 上山顶 3666 云社，免费体验 OPPO 旅拍神器，拍雪山云海大片\n" +
  "3️⃣ 按活动要求发一篇牛背山打卡小红书笔记\n" +
  "4️⃣ 回到现场核验内容，直接免费抱走限定奖牌\n\n" +
  "周末去牛背山看云海的姐妹别错过！爬山 + 拍照 + 拿奖牌一站式搞定，风景好看周边还香哭😭\n\n" +
  "活动须知\n" +
  "• 请提前 15 分钟到达集合点，领队会统一核对名单并说明当天路线。\n" +
  "• 山区早晚温差较大，建议携带防风外套、雨具、保温水杯和便携能量食品。\n" +
  "• 全程请穿防滑运动鞋，遵循领队安排，不单独离队或进入未开放区域。\n" +
  "• 如遇大雾、强降雨等天气，活动将以安全为先调整路线或时间，并及时通知。\n\n" +
  "费用包含往返接送、领队服务、午餐及基础活动保险；个人消费和未注明项目需自行承担。"

function goBack() {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
    return
  }
  uni.reLaunch({ url: "/pages/plaza/index" })
}

function showDemo(name) {
  if (!requireLogin()) return
  uni.showToast({ title: `${name}（Demo）`, icon: "none" })
}

function toggleFavorite() {
  if (!requireLogin()) return
  favorite.value = !favorite.value
  uni.showToast({
    title: favorite.value ? "已收藏" : "已取消收藏",
    icon: "none",
  })
}

function joinEvent() {
  if (!requireLogin()) return
  uni.showToast({ title: "报名成功", icon: "success" })
}

function openRecommendation(item) {
  uni.navigateTo({ url: `/pages/detail/index?id=${item.id}` })
}
</script>

<template>
  <view class="detail">
    <scroll-view class="detail__scroll" scroll-y :show-scrollbar="false">
      <view class="detail__content">
        <view class="hero">
          <view class="hero__gradient" />
          <view class="hero__top-shade" />

          <button class="nav-button nav-button--back" aria-label="返回" @tap="goBack">
            <image src="/static/eligo/icons/arrow-left.svg" mode="aspectFit" />
          </button>
          <button class="nav-button nav-button--share" aria-label="分享" @tap="showDemo('分享')">
            <image src="/static/eligo/icons/share.svg" mode="aspectFit" />
          </button>
          <button
            class="nav-button nav-button--heart"
            :class="{ 'nav-button--favorite': favorite }"
            aria-label="收藏"
            @tap="toggleFavorite"
          >
            <image src="/static/eligo/icons/heart.svg" mode="aspectFit" />
          </button>

          <view class="hero__dots" aria-hidden="true">
            <view class="hero__dot hero__dot--active" />
            <view v-for="index in 5" :key="index" class="hero__dot" />
          </view>
        </view>

        <view class="subsidy">
          <view class="subsidy__accent" />
          <view class="subsidy__flare" />
          <image
            class="subsidy__copy"
            src="/static/eligo/subsidy-label.svg"
            mode="scaleToFill"
          />
        </view>

        <view class="product-panel">
          <view class="price">
            <text class="price__symbol">¥</text>
            <text class="price__value">{{ primaryEvent.price }}</text>
            <text class="price__old">¥108</text>
          </view>
          <text class="followers">{{ primaryEvent.followers }}人关注</text>
          <text class="event-title">{{ primaryEvent.title }}</text>

          <view class="tags tags--warm">
            <text class="tag tag--warm">官方认证</text>
            <text class="tag tag--warm">补贴团</text>
          </view>
          <view class="tags tags--safe">
            <text class="tag tag--safe">安心退</text>
            <text class="tag tag--safe">含午餐</text>
            <text class="tag tag--safe tag--wide">大巴接送</text>
          </view>
        </view>

        <view class="organizer">
          <view class="organizer__identity">
            <view class="organizer__avatar" />
            <view class="organizer__verified">✓</view>
            <text class="organizer__name">Debeme口腔集团&amp;大鹏半岛旅游局</text>
          </view>
          <button class="organizer__follow" aria-label="关注主办方" @tap="showDemo('关注')">
            <view class="organizer__follow-horizontal" />
            <view class="organizer__follow-vertical" />
          </button>
        </view>

        <view class="schedule">
          <view class="schedule__time">
            <view class="schedule__time-row">
              <text class="schedule__time-value">15:30</text>
              <text class="schedule__start-label">开始</text>
            </view>
            <view class="schedule__date-row">
              <text class="schedule__date">07-10</text>
              <text class="schedule__weekday">周日</text>
            </view>
          </view>

          <view class="schedule__divider" />

          <view class="schedule__weather">
            <view class="schedule__weather-row">
              <text class="schedule__weather-low">36</text>
              <text class="schedule__slash">/</text>
              <text class="schedule__temperature">38°C</text>
            </view>
            <text class="schedule__weather-label">小雨轉晴</text>
          </view>

          <view class="weather-art" aria-hidden="true">
            <view class="weather-art__sun" />
            <view class="weather-art__cloud" />
            <view class="weather-art__rain weather-art__rain--one" />
            <view class="weather-art__rain weather-art__rain--two" />
          </view>
        </view>

        <view class="map-card" @tap="showDemo('查看路线')">
          <view class="map-card__header">
            <image class="map-card__icon" src="/static/eligo/icons/location.svg" mode="aspectFit" />
            <text class="map-card__title">集合地点</text>
          </view>
          <text class="map-card__address">{{ primaryEvent.location }}</text>
          <view class="map-card__footer">
            <text class="map-card__distance">距离您 {{ primaryEvent.distance }}　{{ primaryEvent.travelTime }}</text>
            <view class="map-card__route">
              <text>查看路线</text>
              <text class="map-card__chevron">›</text>
            </view>
          </view>
        </view>

        <view class="introduction">
          <text class="section-title">活动介绍</text>
          <text class="introduction__copy">{{ introduction }}</text>
        </view>

        <view class="recommend-heading">
          <view class="recommend-heading__line recommend-heading__line--left" />
          <text>猜你喜欢</text>
          <view class="recommend-heading__line recommend-heading__line--right" />
        </view>

        <view class="recommendations">
          <ActivityCard
            v-for="item in recommendations"
            :key="item.id"
            :item="item"
            @open="openRecommendation"
            @join="showDemo('报名')"
          />
        </view>
      </view>
    </scroll-view>

    <view class="action-bar">
      <view class="action-bar__tools">
        <button class="action" @tap="showDemo('关注')">
          <image src="/static/eligo/icons/heart.svg" mode="aspectFit" />
          <text>关注</text>
        </button>
        <button class="action" @tap="showDemo('发动态')">
          <image src="/static/eligo/icons/share.svg" mode="aspectFit" />
          <text>发动态</text>
        </button>
        <button class="action" @tap="showDemo('电话')">
          <image src="/static/eligo/icons/call.svg" mode="aspectFit" />
          <text>电话</text>
        </button>
      </view>
      <button class="join-button" @tap="joinEvent">立即加入</button>
      <view class="home-indicator" />
    </view>
  </view>
</template>

<style scoped>
.detail {
  position: fixed;
  inset: 0;
  display: flex;
  flex-direction: column;
  background: #f6f7fb;
  font-family: "PingFang SC", sans-serif;
}

.detail__scroll {
  flex: 1;
  background: #f6f7fb;
}

.detail__content {
  padding: 0 0 130px;
}

/* ---------- Hero (gradient placeholder) ---------- */
.hero {
  position: relative;
  width: 100%;
  height: 240px;
  overflow: hidden;
}

.hero__gradient {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, #4a7fa8 0%, #7fa6c4 35%, #d4b896 70%, #c89868 100%);
}

.hero__top-shade {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 120px;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.5), transparent);
}

.nav-button {
  position: absolute;
  top: 58px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  padding: 0;
  border-radius: 14px;
  background: rgba(0, 0, 0, 0.42);
}

.nav-button::after,
.organizer__follow::after,
.action::after,
.join-button::after {
  border: 0;
}

.nav-button image {
  width: 26px;
  height: 26px;
  filter: invert(1);
}

.nav-button--back { left: 16px; }
.nav-button--share { right: 80px; }
.nav-button--heart { right: 16px; }
.nav-button--favorite { background: rgba(245, 101, 81, 0.92); }

.hero__dots {
  position: absolute;
  bottom: 16px;
  left: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 6px;
}

.hero__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.7);
}

.hero__dot--active {
  width: 20px;
  border-radius: 3px;
  background: #ffffff;
}

/* ---------- Subsidy (gradient + label, watermark removed) ---------- */
.subsidy {
  position: relative;
  margin: -28px 16px 16px;
  height: 100px;
  overflow: hidden;
  border-radius: 20px;
  background: linear-gradient(140deg, #ff355a, #fe5f3d);
}

.subsidy__accent {
  position: absolute;
  top: -20px;
  left: -20px;
  width: 90px;
  height: 48px;
  border-radius: 50%;
  background: #ffec6f;
  transform: rotate(-12deg);
}

.subsidy__flare {
  position: absolute;
  top: 8px;
  right: 25px;
  width: 37px;
  height: 51px;
  border-radius: 50% 50% 42% 58%;
  background: linear-gradient(145deg, #ffb149, rgba(255, 177, 73, 0));
  transform: rotate(18deg);
}

.subsidy__copy {
  position: absolute;
  top: 22px;
  left: 20px;
  width: 220px;
  height: 26px;
}

/* ---------- Product panel (price + title + tags) ---------- */
.product-panel {
  margin: 0 16px 16px;
  padding: 20px 16px;
  background: #ffffff;
  border-radius: 12px;
}

.price {
  display: flex;
  align-items: flex-end;
  color: #e5482d;
}

.price__symbol {
  font-family: "PingFang SC", sans-serif;
  font-size: 20px;
  line-height: 20px;
  font-weight: 400;
  margin-bottom: 2px;
}

.price__value {
  margin-left: 3px;
  font-family: "PingFang SC", sans-serif;
  font-size: 28px;
  line-height: 28px;
  font-weight: 600;
}

.price__old {
  margin: 0 0 2px 8px;
  color: #888888;
  font-family: "PingFang SC", sans-serif;
  font-size: 16px;
  line-height: 16px;
  text-decoration: line-through;
}

.followers {
  display: block;
  margin-top: 8px;
  color: #4a4a4a;
  font-family: "PingFang SC", sans-serif;
  font-size: 16px;
  line-height: 18px;
}

.event-title {
  display: block;
  margin-top: 12px;
  color: #1a1a1a;
  font-family: "PingFang SC", sans-serif;
  font-size: 26px;
  line-height: 36px;
  font-weight: 600;
}

.tags {
  display: flex;
  margin-top: 14px;
  gap: 8px;
}

.tags--safe { margin-top: 10px; }

.tag {
  box-sizing: border-box;
  padding: 5px 12px;
  border: 1px solid;
  border-radius: 6px;
  font-family: "PingFang SC", sans-serif;
  font-size: 15px;
  line-height: 18px;
  white-space: nowrap;
}

.tag--warm {
  border-color: #ffb7ad;
  color: #d9402a;
  background: #ffe7e3;
}

.tag--safe {
  border-color: #b8ebc8;
  color: #1a7a4a;
  background: #e1f6e7;
}

.tag--wide { min-width: 76px; text-align: center; }

/* ---------- Organizer ---------- */
.organizer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 16px 16px;
  padding: 16px;
  border-radius: 12px;
  background: #ffffff;
}

.organizer__identity {
  position: relative;
  display: flex;
  align-items: center;
  min-width: 0;
  flex: 1;
}

.organizer__avatar {
  width: 40px;
  height: 40px;
  border: 2px solid #ffffff;
  border-radius: 50%;
  background: linear-gradient(140deg, #f56551, #ffb149);
  flex-shrink: 0;
}

.organizer__verified {
  position: absolute;
  top: 26px;
  left: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border: 2px solid #ffffff;
  border-radius: 50%;
  color: #ffffff;
  background: #2ba471;
  font-size: 10px;
  line-height: 12px;
}

.organizer__name {
  flex: 1;
  margin-left: 14px;
  overflow: hidden;
  color: #1f1f1f;
  font-family: "PingFang SC", sans-serif;
  font-size: 16px;
  line-height: 22px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.organizer__follow {
  position: relative;
  display: block;
  box-sizing: border-box;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: #ff5a3c;
  flex-shrink: 0;
  margin-left: 12px;
}

.organizer__follow-horizontal,
.organizer__follow-vertical {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 16px;
  height: 3px;
  border-radius: 2px;
  background: #ffffff;
  transform: translate(-50%, -50%);
}

.organizer__follow-vertical {
  transform: translate(-50%, -50%) rotate(90deg);
}

/* ---------- Schedule ---------- */
.schedule {
  display: flex;
  align-items: center;
  margin: 0 16px 16px;
  padding: 18px 16px;
  border-radius: 12px;
  background: #ffffff;
}

.schedule__time {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.schedule__time-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.schedule__time-value {
  color: #1a1a1a;
  font-family: "PingFang SC", sans-serif;
  font-size: 28px;
  line-height: 30px;
  font-weight: 700;
  letter-spacing: -0.6px;
  white-space: nowrap;
}

.schedule__start-label {
  color: #1a1a1a;
  font-family: "PingFang SC", sans-serif;
  font-size: 16px;
  line-height: 18px;
  font-weight: 600;
}

.schedule__date-row {
  margin-top: 8px;
  display: flex;
  gap: 10px;
}

.schedule__date,
.schedule__weekday {
  color: #4a4a4a;
  font-family: "PingFang SC", sans-serif;
  font-size: 16px;
  line-height: 16px;
  font-weight: 400;
}

.schedule__divider {
  width: 1px;
  height: 48px;
  margin: 0 22px;
  background: #e6e8ec;
  flex-shrink: 0;
}

.schedule__weather {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.schedule__weather-row {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.schedule__weather-low {
  color: #1a1a1a;
  font-family: "PingFang SC", sans-serif;
  font-size: 24px;
  line-height: 26px;
  font-weight: 700;
}

.schedule__slash,
.schedule__temperature {
  color: #1a1a1a;
  font-family: "PingFang SC", sans-serif;
  font-size: 16px;
  line-height: 18px;
  font-weight: 700;
}

.schedule__weather-label {
  margin-top: 8px;
  color: #4a4a4a;
  font-family: "PingFang SC", sans-serif;
  font-size: 16px;
  line-height: 16px;
  font-weight: 400;
}

.weather-art {
  position: relative;
  margin-left: auto;
  width: 48px;
  height: 48px;
  flex-shrink: 0;
}

.weather-art__sun {
  position: absolute;
  top: 3px;
  right: 3px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #ffbd34;
}

.weather-art__cloud {
  position: absolute;
  top: 16px;
  left: 3px;
  width: 36px;
  height: 20px;
  border-radius: 12px;
  background: linear-gradient(140deg, #ffffff, #d9e5ff);
  box-shadow: 0 2px 5px rgba(80, 104, 156, 0.18);
}

.weather-art__rain {
  position: absolute;
  bottom: 2px;
  width: 3px;
  height: 8px;
  border-radius: 2px;
  background: #58b7ff;
  transform: rotate(15deg);
}

.weather-art__rain--one { left: 14px; }
.weather-art__rain--two { left: 28px; }

/* ---------- Map card (simplified) ---------- */
.map-card {
  margin: 0 16px 16px;
  padding: 20px;
  border-radius: 12px;
  background: #ffffff;
}

.map-card__header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.map-card__icon {
  width: 28px;
  height: 28px;
}

.map-card__title {
  color: #1a1a1a;
  font-family: "PingFang SC", sans-serif;
  font-size: 20px;
  line-height: 24px;
  font-weight: 600;
}

.map-card__address {
  display: block;
  margin-top: 14px;
  color: #1f1f1f;
  font-family: "PingFang SC", sans-serif;
  font-size: 16px;
  line-height: 24px;
  font-weight: 500;
}

.map-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 18px;
  padding: 14px 18px;
  border-radius: 14px;
  background: #f2f4f8;
}

.map-card__distance {
  color: #4a4a4a;
  font-family: "PingFang SC", sans-serif;
  font-size: 16px;
  line-height: 18px;
}

.map-card__route {
  display: flex;
  align-items: center;
  color: #d9402a;
  font-family: "PingFang SC", sans-serif;
  font-size: 16px;
  line-height: 18px;
  font-weight: 600;
}

.map-card__chevron {
  margin-left: 4px;
  font-size: 22px;
  line-height: 22px;
}

/* ---------- Introduction ---------- */
.introduction {
  margin: 0 16px 16px;
  padding: 20px 16px;
  border-radius: 12px;
  background: #ffffff;
}

.section-title {
  display: block;
  color: #1a1a1a;
  font-family: "PingFang SC", sans-serif;
  font-size: 20px;
  line-height: 24px;
  font-weight: 600;
}

.introduction__copy {
  display: block;
  margin-top: 16px;
  color: #1f1f1f;
  font-family: "PingFang SC", sans-serif;
  font-size: 18px;
  line-height: 30px;
  font-weight: 400;
  white-space: pre-line;
}

/* ---------- Recommend heading ---------- */
.recommend-heading {
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 28px 16px 16px;
  gap: 14px;
}

.recommend-heading text {
  color: #d9402a;
  font-family: "PingFang SC", sans-serif;
  font-size: 20px;
  line-height: 24px;
  font-weight: 600;
  text-align: center;
}

.recommend-heading__line {
  width: 60px;
  height: 2px;
  background: linear-gradient(90deg, #ffe7c8, #fcd2ca);
}

.recommendations {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin: 0 16px 16px;
}

/* ---------- Action bar ---------- */
.action-bar {
  position: fixed;
  z-index: 90;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  align-items: center;
  height: 110px;
  padding: 0 20px;
  background: #ffffff;
  box-shadow: 0 -3px 12px rgba(35, 42, 51, 0.08);
}

.action-bar__tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.action {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 56px;
  padding: 0;
  border: 0;
  border-radius: 8px;
  color: #1f1f1f;
  background: transparent;
  font-family: "PingFang SC", sans-serif;
  font-size: 15px;
  line-height: 15px;
}

.action image {
  width: 26px;
  height: 26px;
  margin-bottom: 6px;
}

.join-button {
  flex: 1;
  margin-left: 16px;
  height: 60px;
  border-radius: 30px;
  color: #ffffff;
  background: linear-gradient(140deg, #f56551, #ffb149);
  font-family: "PingFang SC", sans-serif;
  font-size: 20px;
  line-height: 60px;
  font-weight: 600;
}

.home-indicator {
  position: absolute;
  bottom: 8px;
  left: 50%;
  width: 134px;
  height: 5px;
  border-radius: 3px;
  background: #1a1a1a;
  transform: translateX(-50%);
}
</style>
