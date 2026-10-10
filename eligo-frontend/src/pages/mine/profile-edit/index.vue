<script setup>
import { ref, onMounted, computed } from "vue"
import { request } from "../../../api/request"

const loading = ref(true)
const saving = ref(false)

const nickname = ref("")
const gender = ref("")
const phone = ref("")
const bio = ref("")
const avatar = ref("")

const genderOptions = [
  { label: "男", value: "MALE" },
  { label: "女", value: "FEMALE" },
  { label: "保密", value: "" },
]

function formatTime(isoStr) {
  if (!isoStr) return ""
  const d = new Date(isoStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`
}

function goBack() {
  uni.navigateBack({ fail: () => uni.reLaunch({ url: "/pages/mine/index" }) })
}

function onGenderChange(e) {
  gender.value = genderOptions[e.detail.value].value
}

const phoneError = computed(() => {
  if (!phone.value) return ""
  const reg = /^1[3-9]\d{9}$/
  if (!reg.test(phone.value)) return "手机号格式不正确"
  return ""
})

function filterPhoneInput(e) {
  const val = (e.detail?.value || "").replace(/\D/g, "").slice(0, 11)
  phone.value = val
}

async function loadProfile() {
  loading.value = true
  try {
    const data = await request({ path: "/api/v1/users/me/profile" })
    nickname.value = data?.nickname || ""
    gender.value = data?.gender || ""
    bio.value = data?.bio || ""
    avatar.value = data?.avatar?.url || data?.avatar || ""
  } catch (err) {
    uni.showToast({ title: err?.message || "加载失败", icon: "none" })
  } finally {
    loading.value = false
  }
}

async function onSave() {
  if (saving.value || loading.value) return
  if (!nickname.value.trim()) {
    uni.showToast({ title: "请输入昵称", icon: "none" })
    return
  }
  if (phone.value && phoneError.value) {
    uni.showToast({ title: phoneError.value, icon: "none" })
    return
  }

  saving.value = true
  try {
    await request({
      path: "/api/v1/users/me/profile",
      method: "PUT",
      data: {
        nickname: nickname.value.trim(),
        gender: gender.value || null,
        phone: phone.value || null,
        bio: bio.value.trim() || null,
      },
    })
    uni.showToast({ title: "保存成功", icon: "success" })
    setTimeout(() => uni.navigateBack(), 800)
  } catch (err) {
    uni.showToast({ title: err?.message || "保存失败", icon: "none" })
  } finally {
    saving.value = false
  }
}

onMounted(loadProfile)
</script>

<template>
  <view class="page">
    <view class="nav">
      <view class="nav__back" @tap="goBack">
        <text class="nav__back-icon">‹</text>
      </view>
      <text class="nav__title">资料编辑</text>
      <view class="nav__save" @tap="onSave">
        <text class="nav__save-text">{{ saving ? "保存中" : "保存" }}</text>
      </view>
    </view>

    <scroll-view class="content" scroll-y>
      <!-- 头像 -->
      <view class="section">
        <view class="avatar-row">
          <view class="avatar">
            <text class="avatar__text">{{ (nickname || "U").charAt(0) }}</text>
          </view>
          <text class="avatar__hint">点击更换头像</text>
        </view>
      </view>

      <!-- 基本信息 -->
      <view class="section">
        <view class="field">
          <text class="field__label">昵称</text>
          <input
            class="field__input"
            v-model="nickname"
            placeholder="请输入昵称"
            maxlength="20"
          />
        </view>
        <view class="divider" />
        <picker :range="genderOptions" range-key="label" @change="onGenderChange">
          <view class="field">
            <text class="field__label">性别</text>
            <view class="field__picker">
              <text class="field__picker-text">
                {{ genderOptions.find((g) => g.value === gender)?.label || "请选择" }}
              </text>
              <text class="field__arrow">›</text>
            </view>
          </view>
        </picker>
        <view class="divider" />
        <view class="field field--column">
          <view class="field__row">
            <text class="field__label">手机号</text>
            <input
              class="field__input"
              type="number"
              inputmode="numeric"
              v-model="phone"
              placeholder="请输入手机号"
              maxlength="11"
              @input="filterPhoneInput"
            />
          </view>
          <text v-if="phoneError" class="field__error">{{ phoneError }}</text>
        </view>
      </view>

      <!-- 简介 -->
      <view class="section">
        <view class="field field--column">
          <text class="field__label">个人简介</text>
          <textarea
            class="textarea"
            v-model="bio"
            placeholder="介绍一下自己吧（选填）"
            maxlength="200"
            :auto-height="true"
          />
        </view>
      </view>

      <view class="bottom-space" />
    </scroll-view>
  </view>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f6f7fb;
  font-family: "PingFang SC", sans-serif;
}

.nav {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 50px 16px 14px;
  background: #ffffff;
  border-bottom: 1px solid #f0f1f4;
}

.nav__back {
  position: absolute;
  left: 16px;
  top: 50px;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav__back-icon {
  font-size: 28px;
  color: #ffffff;
  line-height: 1;
  margin-top: -2px;
}

.nav__title {
  font-size: 20px;
  font-weight: 600;
  color: #111111;
}

.nav__save {
  position: absolute;
  right: 16px;
  top: 50px;
  padding: 8px 20px;
  border-radius: 18px;
  background: linear-gradient(135deg, #ffb149, #f56551);
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav__save-text {
  font-size: 15px;
  color: #ffffff;
  font-weight: 600;
}

.content {
  flex: 1;
  height: 0;
  padding: 16px;
}

.section {
  background: #ffffff;
  border-radius: 14px;
  padding: 0 16px;
  margin-bottom: 14px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.avatar-row {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px 0;
  gap: 10px;
}

.avatar {
  width: 84px;
  height: 84px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ffb149 0%, #f56551 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar__text {
  font-size: 34px;
  font-weight: 600;
  color: #ffffff;
}

.avatar__hint {
  font-size: 13px;
  color: #999999;
}

.field {
  display: flex;
  flex-direction: row;
  align-items: center;
  min-height: 52px;
}

.field--column {
  flex-direction: column;
  align-items: flex-start;
  padding: 14px 0;
  gap: 6px;
}

.field__row {
  display: flex;
  flex-direction: row;
  align-items: center;
  width: 100%;
  min-height: 48px;
}

.field__label {
  font-size: 16px;
  color: #1a1a1a;
  font-weight: 500;
  min-width: 80px;
}

.field__input {
  flex: 1;
  font-size: 16px;
  color: #1a1a1a;
  text-align: right;
}

.field__picker {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 4px;
}

.field__picker-text {
  font-size: 16px;
  color: #4a5568;
}

.field__arrow {
  font-size: 22px;
  color: #c0c4cc;
}

.field__error {
  font-size: 13px;
  color: #d9402a;
  margin-top: 2px;
}

.divider {
  height: 1px;
  background: #f0f1f4;
}

.textarea {
  width: 100%;
  min-height: 90px;
  font-size: 15px;
  color: #1a1a1a;
  line-height: 22px;
  padding: 12px;
  background: #f6f7fb;
  border-radius: 10px;
  box-sizing: border-box;
}

.bottom-space {
  height: 40px;
}
</style>
