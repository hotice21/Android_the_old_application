<script setup>
import { computed, ref } from "vue"
import { onLoad } from "@dcloudio/uni-app"
import {
  consentAgreement,
  getAgreement,
  getCurrentAgreements,
} from "../../api/agreement"
import { loginWithWechat } from "../../api/auth"
import { getMyProfile } from "../../api/profile"
import { formatApiError } from "../../api/request"
import { ping } from "../../api/system"
import { saveProfile } from "../../stores/session"
import {
  markEntryGuideSeen,
  normalizeGuestPageUrl,
  normalizePageUrl,
} from "../../stores/entry"

const current = ref(0)
const agreed = ref(false)
const busy = ref(false)
const integrationStep = ref("")
const agreementDetail = ref(null)
const agreementNeedsConsent = ref(false)
const returnUrl = ref("/pages/plaza/index")
let resolveAgreementDecision = null

onLoad((options) => {
  returnUrl.value = normalizePageUrl(options?.redirect)
})

const loginLabel = computed(() =>
  busy.value ? integrationStep.value || "正在连接..." : "微信登录/注册",
)

const slides = [
  {
    id: "playmates",
    lead: "寻找",
    accent: "游玩搭子",
    description: "发布线下结伴招募，邀约同龄同好，\n线下出行不愁无伴",
    image: "/static/eligo/onboarding-illustration-1.png",
    imageClass: "illustration--one",
  },
  {
    id: "events",
    lead: "订阅",
    accent: "精品活动",
    description: "精选平台官方活动，一键在线报名，省\n心参与线下聚会",
    image: "/static/eligo/onboarding-illustration-2.png",
    imageClass: "illustration--two",
  },
  {
    id: "moments",
    lead: "分享",
    accent: "美好日常",
    description: "关注同好动态，分享线下活动体验，\n记录每一次真实相遇",
    image: "/static/eligo/onboarding-illustration-3.png",
    imageClass: "illustration--three",
  },
]

function handleChange(event) {
  current.value = event.detail.current
}

function toggleAgreement() {
  if (busy.value) return
  agreed.value = !agreed.value
}

function openAgreement(detail, needsConsent = false) {
  agreementDetail.value = detail
  agreementNeedsConsent.value = needsConsent
  if (!needsConsent) return Promise.resolve(true)

  return new Promise((resolve) => {
    resolveAgreementDecision = resolve
  })
}

function closeAgreement(accepted = false) {
  agreementDetail.value = null
  agreementNeedsConsent.value = false
  if (resolveAgreementDecision) {
    resolveAgreementDecision(accepted)
    resolveAgreementDecision = null
  }
}

async function showPolicy(name, type) {
  if (!type) {
    uni.showToast({ title: `${name}内容待产品提供`, icon: "none" })
    return
  }

  uni.showLoading({ title: "加载协议..." })
  try {
    const result = await getCurrentAgreements(type)
    const summary = result?.items?.[0]
    if (!summary) {
      throw new Error(`${name}尚未配置`)
    }
    openAgreement(await getAgreement(summary.agreementId))
  } catch (error) {
    uni.showModal({
      title: "协议加载失败",
      content: formatApiError(error),
      showCancel: false,
    })
  } finally {
    uni.hideLoading()
  }
}

function browseAsGuest() {
  markEntryGuideSeen()
  uni.reLaunch({ url: normalizeGuestPageUrl(returnUrl.value) })
}

async function handleLogin() {
  if (busy.value) return
  if (!agreed.value) {
    uni.showToast({ title: "请先阅读并同意相关协议", icon: "none" })
    return
  }

  busy.value = true
  try {
    integrationStep.value = "正在检查服务..."
    const health = await ping()
    if (health?.status !== "UP") {
      throw new Error("后端服务当前不可用")
    }

    integrationStep.value = "正在微信登录..."
    const session = await loginWithWechat()

    integrationStep.value = "正在确认协议..."
    for (const agreementId of session.pendingAgreementIds || []) {
      const detail = await getAgreement(agreementId)
      const accepted = await openAgreement(detail, true)
      if (!accepted) {
        const cancelled = new Error("你尚未同意最新协议，登录流程已暂停")
        cancelled.code = "AGREEMENT_CANCELLED"
        throw cancelled
      }
      await consentAgreement(agreementId)
    }

    integrationStep.value = "正在读取资料..."
    const profile = await getMyProfile()
    saveProfile(profile)
    markEntryGuideSeen()

    uni.showToast({
      title: profile.profileCompleted ? "登录成功" : "登录成功，资料待完善",
      icon: "none",
      duration: 1200,
    })
    setTimeout(() => {
      uni.reLaunch({ url: returnUrl.value })
    }, 500)
  } catch (error) {
    if (error?.code === "AGREEMENT_CANCELLED") {
      uni.showToast({ title: error.message, icon: "none" })
      return
    }
    uni.showModal({
      title: "联调未完成",
      content: formatApiError(error),
      showCancel: false,
    })
  } finally {
    busy.value = false
    integrationStep.value = ""
  }
}
</script>

<template>
  <view class="onboarding-page" :aria-label="`${slides[current].lead}${slides[current].accent}`">
    <swiper
      class="onboarding-swiper"
      :current="current"
      :duration="280"
      @change="handleChange"
    >
      <swiper-item v-for="slide in slides" :key="slide.id">
        <view class="slide">
          <view class="heading">
            <text class="heading__lead">{{ slide.lead }}</text>
            <text class="heading__accent">{{ slide.accent }}</text>
            <image class="heading__smile" src="/static/eligo/smile.svg" mode="widthFix" />
          </view>

          <text class="description">{{ slide.description }}</text>
          <view class="illustration" :class="slide.imageClass" />
        </view>
      </swiper-item>
    </swiper>

    <view class="pagination" aria-hidden="true">
      <view
        v-for="(_, index) in slides"
        :key="index"
        class="pagination__dot"
        :class="{ 'pagination__dot--active': current === index }"
      />
    </view>

    <button
      class="login-button"
      :disabled="busy"
      @tap="handleLogin"
    >
      {{ loginLabel }}
    </button>
    <button
      class="browse-button"
      :disabled="busy"
      @tap="browseAsGuest"
    >
      先逛逛
    </button>

    <view class="agreement">
      <button
        class="agreement__check"
        :class="{ 'agreement__check--active': agreed }"
        aria-label="同意协议"
        @tap="toggleAgreement"
      >
        <text v-if="agreed">✓</text>
      </button>
      <view class="agreement__copy">
        <text>我已阅读并同意</text>
        <text
          class="agreement__link"
          @tap="showPolicy('Eligo用户协议', 'USER_AGREEMENT')"
        >
          Eligo用户协议
        </text>
        <text>、</text>
        <text
          class="agreement__link"
          @tap="showPolicy('隐私政策', 'PRIVACY_POLICY')"
        >
          隐私政策
        </text>
        <text>及</text>
        <text
          class="agreement__link"
          @tap="showPolicy('中国移动认证服务条款')"
        >
          中国移动认证服务条款
        </text>
      </view>
    </view>

    <view v-if="agreementDetail" class="agreement-dialog">
      <view class="agreement-dialog__panel">
        <text class="agreement-dialog__title">{{ agreementDetail.title }}</text>
        <text class="agreement-dialog__version">
          版本 {{ agreementDetail.versionCode }}
        </text>
        <scroll-view class="agreement-dialog__content" scroll-y>
          <text>{{ agreementDetail.content }}</text>
        </scroll-view>
        <view class="agreement-dialog__actions">
          <button
            v-if="agreementNeedsConsent"
            class="agreement-dialog__button agreement-dialog__button--secondary"
            @tap="closeAgreement(false)"
          >
            暂不同意
          </button>
          <button
            class="agreement-dialog__button agreement-dialog__button--primary"
            @tap="closeAgreement(true)"
          >
            {{ agreementNeedsConsent ? "同意并继续" : "我知道了" }}
          </button>
        </view>
      </view>
    </view>

    <view class="home-indicator" />
  </view>
</template>

<style scoped>
.onboarding-page {
  position: fixed;
  inset: 0;
  display: flex;
  flex-direction: column;
  min-height: 730px;
  overflow: hidden;
  background: #ffffff;
}

.onboarding-swiper {
  flex: 1;
  width: 100%;
  min-height: 0;
}

.slide {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  height: 100%;
  padding: 78px 32px 0;
  box-sizing: border-box;
}

.heading {
  position: relative;
  z-index: 2;
  display: flex;
  justify-content: center;
  align-items: center;
  height: 60px;
  font-size: 36px;
  line-height: 50px;
  font-weight: 400;
  letter-spacing: -0.8px;
}

.heading__lead { color: #1a1a1a; }
.heading__accent { color: #fe5f02; }

.heading__smile {
  position: absolute;
  top: 44px;
  left: 50%;
  width: 55px;
  height: auto;
  transform: translateX(-9px);
}

.description {
  margin-top: 28px;
  width: 100%;
  max-width: 320px;
  color: #4a4a4a;
  font-size: 20px;
  line-height: 30px;
  font-weight: 400;
  text-align: center;
  white-space: pre-line;
}

.illustration {
  margin-top: 36px;
  width: 280px;
  height: 300px;
  border-radius: 24px;
}

.illustration--one {
  background: linear-gradient(135deg, #ffb347 0%, #fe5f02 50%, #e63946 100%);
}

.illustration--two {
  background: linear-gradient(135deg, #4facfe 0%, #00c6fb 50%, #6a5acd 100%);
}

.illustration--three {
  background: linear-gradient(135deg, #56ab2f 0%, #a8e063 50%, #0ba360 100%);
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 10px;
  padding: 12px 0 8px;
}

.pagination__dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #c8c8c8;
}

.pagination__dot--active { background: #fe5f02; }

.login-button {
  margin: 8px 26px 0;
  height: 60px;
  border-radius: 30px;
  color: #ffffff;
  background: #fe5f02;
  font-size: 22px;
  line-height: 60px;
  font-weight: 400;
}

.login-button[disabled] {
  color: #ffffff;
  background: #ff9a60;
  opacity: 1;
}

.browse-button {
  margin: 8px auto 0;
  min-height: 44px;
  padding: 6px 24px;
  border: 0;
  border-radius: 0;
  color: #4a4a4a;
  background: transparent;
  box-shadow: none;
  font-size: 18px;
  line-height: 28px;
}

.browse-button[disabled] {
  color: #b7b7b7;
  opacity: 1;
}

.browse-button::after,
.login-button::after,
.agreement__check::after {
  border: 0;
}

.agreement {
  display: flex;
  align-items: flex-start;
  margin: 12px 40px 0;
  padding-bottom: 8px;
}

.agreement__check {
  display: flex;
  flex: 0 0 20px;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  margin-top: 2px;
  border: 1px solid #9aa0a6;
  border-radius: 50%;
  color: #ffffff;
  background: #ffffff;
  font-size: 13px;
}

.agreement__check--active {
  border-color: #fe5f02;
  background: #fe5f02;
}

.agreement__copy {
  flex: 1;
  margin-left: 10px;
  color: #4a4a4a;
  font-size: 15px;
  line-height: 22px;
}

.agreement__link { color: #007aff; }

.agreement-dialog {
  position: fixed;
  z-index: 100;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 28px;
  background: rgba(0, 0, 0, 0.48);
}

.agreement-dialog__panel {
  display: flex;
  width: 100%;
  max-height: 76vh;
  padding: 24px 20px 18px;
  border-radius: 18px;
  background: #ffffff;
  box-sizing: border-box;
  flex-direction: column;
}

.agreement-dialog__title {
  color: #1a1a1a;
  font-size: 24px;
  line-height: 32px;
  font-weight: 600;
  text-align: center;
}

.agreement-dialog__version {
  margin-top: 4px;
  color: #666666;
  font-size: 13px;
  text-align: center;
}

.agreement-dialog__content {
  height: 48vh;
  margin-top: 18px;
  color: #2a2a2a;
  font-size: 17px;
  line-height: 26px;
  white-space: pre-wrap;
}

.agreement-dialog__actions {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}

.agreement-dialog__button {
  flex: 1;
  height: 48px;
  padding: 0 12px;
  border-radius: 24px;
  font-size: 18px;
  line-height: 48px;
}

.agreement-dialog__button::after {
  border: 0;
}

.agreement-dialog__button--secondary {
  color: #4a4a4a;
  background: #f2f2f2;
}

.agreement-dialog__button--primary {
  color: #ffffff;
  background: #fe5f02;
}

.home-indicator {
  margin: 8px auto 16px;
  width: 145px;
  height: 5px;
  border-radius: 3px;
  background: #000000;
}

@media (max-height: 760px) {
  .onboarding-page {
    transform: scale(0.89);
    transform-origin: top center;
    width: 112.36%;
    left: -6.18%;
  }
}
</style>
