<script setup>
import { computed, ref, onMounted } from "vue"
import { bindPhone } from "../../api/account"
import { downloadOwnedFile, uploadImage } from "../../api/file"
import { getMyProfile, updateMyProfile, updateAvatar, updateNickname, updateInterests, getInterestTags } from "../../api/profile"
import { formatApiError } from "../../api/request"
import { PROFILE_MOCK_ENABLED } from "../../config/runtime"
import { getProfile, saveProfile } from "../../stores/session"
import { mockProfile, mockInterestTags } from "../../mock/profile"

const loading = ref(false)
const saving = ref(false)
const bindingPhone = ref(false)
const profileReady = ref(false)
const loadError = ref("")

// 表单字段
const userId = ref("")
const nickname = ref("")
const gender = ref("")
const birthDate = ref("")
const provinceCode = ref("")
const cityCode = ref("")
const districtCode = ref("")
const provinceName = ref("")
const cityName = ref("")
const districtName = ref("")
const bio = ref("")
const email = ref("")
const avatar = ref("")
const pendingAvatarPath = ref("")
const pendingAvatarFileId = ref("")
const selectedTags = ref([])
const allTags = ref([])
const phoneBound = ref(false)
const maskedPhone = ref("")
const profileCompleted = ref(false)
const completedAt = ref("")
const nextNicknameChangeAt = ref("")
const nicknameChangeable = ref(true)
const originalNickname = ref("")
const originalTagIds = ref([])

const regionDisplay = computed(() =>
  [provinceName.value, cityName.value, districtName.value]
    .filter(Boolean)
    .join(" "),
)

// 性别选项
const genderOptions = [
  { label: "男", value: "MALE" },
  { label: "女", value: "FEMALE" },
  { label: "其他", value: "OTHER_OR_UNDISCLOSED" },
]

onMounted(() => {
  loadPageData()
})

async function loadPageData() {
  loading.value = true
  loadError.value = ""
  profileReady.value = false
  allTags.value = []

  if (PROFILE_MOCK_ENABLED) {
    await applyProfile(mockProfile)
    allTags.value = mockInterestTags
    profileReady.value = true
    loading.value = false
    return
  }

  const cachedProfile = getProfile()
  if (cachedProfile) {
    await applyProfile(cachedProfile)
  }

  const errors = []
  try {
    const data = await getMyProfile()
    if (data) {
      await applyProfile(data)
      saveProfile(data)
      profileReady.value = true
    } else {
      errors.push("后端未返回个人资料")
    }
  } catch (error) {
    errors.push(formatApiError(error))
  }

  try {
    const tags = await getInterestTags()
    if (tags?.items) {
      allTags.value = tags.items
    }
  } catch (error) {
    errors.push(formatApiError(error))
  }

  loadError.value = errors.filter(Boolean).join("\n")
  loading.value = false
}

function fillForm(data, { keepPendingAvatar = false } = {}) {
  userId.value = data.userId || ""
  nickname.value = data.nickname || ""
  originalNickname.value = data.nickname || ""
  gender.value = data.gender || ""
  birthDate.value = data.birthDate || ""
  bio.value = data.bio || ""
  email.value = data.email || ""
  if (!keepPendingAvatar || !pendingAvatarPath.value) {
    avatar.value = typeof data.avatar === "string" ? data.avatar : ""
    pendingAvatarPath.value = ""
    pendingAvatarFileId.value = ""
  }
  if (data.region) {
    provinceCode.value = data.region.provinceCode || ""
    cityCode.value = data.region.cityCode || ""
    districtCode.value = data.region.districtCode || ""
    provinceName.value = data.region.provinceName || ""
    cityName.value = data.region.cityName || ""
    districtName.value = data.region.districtName || ""
  } else {
    provinceCode.value = ""
    cityCode.value = ""
    districtCode.value = ""
    provinceName.value = ""
    cityName.value = ""
    districtName.value = ""
  }
  selectedTags.value = (data.interestTags || []).map(t => t.interestTagId)
  originalTagIds.value = [...selectedTags.value]
  if (data.phoneBinding) {
    phoneBound.value = data.phoneBinding.bound || false
    maskedPhone.value = data.phoneBinding.maskedPhone || ""
  }
  profileCompleted.value = data.profileCompleted || false
  completedAt.value = data.completedAt || ""
  if (data.nextNicknameChangeAt) {
    nextNicknameChangeAt.value = data.nextNicknameChangeAt
    nicknameChangeable.value = new Date(data.nextNicknameChangeAt) <= new Date()
  } else {
    nicknameChangeable.value = true
  }
}

async function applyProfile(data, options) {
  fillForm(data, options)
  if (options?.keepPendingAvatar && pendingAvatarPath.value) return

  const avatarUrl = data.avatar?.url
  if (!avatarUrl) return

  try {
    avatar.value = await downloadOwnedFile(avatarUrl)
  } catch (error) {
    console.error("头像下载失败", error)
  }
}

function onGenderChange(e) {
  gender.value = genderOptions[e.detail.value].value
}

function onDateChange(e) {
  birthDate.value = e.detail.value
}

function onRegionChange(e) {
  const names = e.detail?.value
  const codes = e.detail?.code
  if (!Array.isArray(names) || names.length !== 3 || !Array.isArray(codes) || codes.length !== 3) {
    uni.showToast({ title: "未获取到地区编码，请重试", icon: "none" })
    return
  }

  ;[provinceName.value, cityName.value, districtName.value] = names
  // 微信地区选择器返回六位国标码，后端目录使用省 2 位、市 4 位、区县 6 位。
  const [rawProvinceCode, rawCityCode, rawDistrictCode] = codes.map(String)
  provinceCode.value = rawProvinceCode.slice(0, 2)
  cityCode.value = rawCityCode.slice(0, 4)
  districtCode.value = rawDistrictCode.slice(0, 6)
}

function toggleTag(tagId) {
  const idx = selectedTags.value.indexOf(tagId)
  if (idx >= 0) {
    selectedTags.value.splice(idx, 1)
  } else {
    if (selectedTags.value.length >= 20) {
      uni.showToast({ title: "最多选择20个标签", icon: "none" })
      return
    }
    selectedTags.value.push(tagId)
  }
}

async function chooseAvatar() {
  uni.chooseImage({
    count: 1,
    sizeType: ["compressed"],
    sourceType: ["album", "camera"],
    success: (res) => {
      const tempPath = res.tempFilePaths[0]
      avatar.value = tempPath
      pendingAvatarPath.value = tempPath
      pendingAvatarFileId.value = ""
      uni.showToast({ title: "头像已选择，保存时上传", icon: "none" })
    },
  })
}

function describePhoneAuthorizationFailure(errMsg) {
  const normalized = String(errMsg || "").toLowerCase()
  if (normalized.includes("user deny") || normalized.includes("user cancel")) {
    return "你已取消手机号授权。"
  }
  if (normalized.includes("privacy")) {
    return "当前小程序的隐私保护指引尚未配置、声明或同意。"
  }
  if (normalized.includes("no permission") || normalized.includes("permission denied")) {
    return "当前 AppID 没有手机号授权权限，请检查小程序后台的接口权限。"
  }
  if (normalized.includes("not support") || normalized.includes("unsupported")) {
    return "当前运行环境或基础库不支持手机号授权。"
  }
  return "微信未返回手机号授权凭证，请检查当前 AppID、接口权限和基础库。"
}

function showPhoneAuthorizationFailure(errMsg) {
  const detail = String(errMsg || "").trim() || "getPhoneNumber 未返回 code 或 errMsg"
  console.error("[profile-edit] getPhoneNumber 授权失败", detail)
  uni.showModal({
    title: "手机号授权失败",
    content: `${describePhoneAuthorizationFailure(detail)}\n\n微信返回：${detail}`,
    showCancel: false,
  })
}

async function onGetPhoneNumber(e) {
  if (bindingPhone.value) return
  if (PROFILE_MOCK_ENABLED) {
    uni.showToast({ title: "演示模式不会绑定手机号", icon: "none" })
    return
  }
  if (!profileReady.value) {
    uni.showToast({ title: "资料尚未加载，请重试", icon: "none" })
    return
  }

  const phoneCode = e.detail?.code
  if (!phoneCode) {
    showPhoneAuthorizationFailure(e.detail?.errMsg)
    return
  }

  bindingPhone.value = true
  try {
    let binding
    try {
      binding = await bindPhone(phoneCode)
    } catch (error) {
      const errorDetail = formatApiError(error)
      console.error("[profile-edit] 手机号绑定接口失败", errorDetail)
      uni.showModal({
        title: "手机号绑定失败",
        content: errorDetail,
        showCancel: false,
      })
      return
    }

    phoneBound.value = Boolean(binding?.bound)
    maskedPhone.value = binding?.maskedPhone || ""

    try {
      const latestProfile = await getMyProfile()
      if (!latestProfile) {
        throw new Error("后端未返回个人资料")
      }
      phoneBound.value = Boolean(latestProfile.phoneBinding?.bound)
      maskedPhone.value = latestProfile.phoneBinding?.maskedPhone || ""
      profileCompleted.value = Boolean(latestProfile.profileCompleted)
      completedAt.value = latestProfile.completedAt || ""
      try {
        saveProfile(latestProfile)
      } catch (cacheError) {
        console.warn("[profile-edit] 手机号绑定后的资料缓存更新失败", cacheError?.message || cacheError)
      }
    } catch (error) {
      const errorDetail = formatApiError(error)
      console.error("[profile-edit] 手机号已绑定，但资料状态刷新失败", errorDetail)
      uni.showModal({
        title: phoneBound.value ? "手机号已绑定" : "绑定请求已完成",
        content: `绑定接口已成功，但资料状态刷新失败。请重新进入页面确认。\n${errorDetail}`,
        showCancel: false,
      })
      return
    }

    uni.showToast({ title: phoneBound.value ? "手机号绑定成功" : "绑定状态待刷新", icon: "success" })
  } finally {
    bindingPhone.value = false
  }
}

function createSaveContext(normalizedNickname) {
  const hadNickname = Boolean(originalNickname.value)
  const profileData = {
    gender: gender.value,
    birthDate: birthDate.value,
    provinceCode: provinceCode.value,
    cityCode: cityCode.value,
    districtCode: districtCode.value,
    email: email.value.trim() || null,
    bio: bio.value.trim() || null,
  }
  if (!hadNickname) {
    profileData.nickname = normalizedNickname
  }

  return {
    normalizedNickname,
    hadNickname,
    nicknameChanged: hadNickname && normalizedNickname !== originalNickname.value,
    profileData,
    selectedTagIds: [...new Set(selectedTags.value)],
    avatarPath: pendingAvatarPath.value,
    avatarFileId: pendingAvatarFileId.value,
    completedStages: [],
    currentStage: "基础资料",
    latestProfile: null,
  }
}

function saveBasicProfileStage(context) {
  return updateMyProfile(context.profileData).then((latestProfile) => {
    context.latestProfile = latestProfile
    context.completedStages.push("基础资料")
    saveProfile(latestProfile)
    if (!context.hadNickname) {
      originalNickname.value = latestProfile?.nickname || context.normalizedNickname
    }
    return context
  })
}

function saveInterestsStage(context) {
  if (context.selectedTagIds.length === 0) return context

  context.currentStage = "兴趣标签"
  return updateInterests(context.selectedTagIds).then((latestProfile) => {
    context.latestProfile = latestProfile
    context.completedStages.push("兴趣标签")
    originalTagIds.value = [...context.selectedTagIds]
    saveProfile(latestProfile)
    return context
  })
}

function saveNicknameStage(context) {
  if (!context.nicknameChanged) return context

  context.currentStage = "昵称"
  return updateNickname(context.normalizedNickname).then((nicknameResult) => {
    context.completedStages.push("昵称")
    originalNickname.value = nicknameResult?.nickname || context.normalizedNickname
    nextNicknameChangeAt.value = nicknameResult?.nextNicknameChangeAt || ""
    nicknameChangeable.value = !nextNicknameChangeAt.value
    context.latestProfile = {
      ...context.latestProfile,
      nickname: originalNickname.value,
      nextNicknameChangeAt: nextNicknameChangeAt.value,
    }
    saveProfile(context.latestProfile)
    return context
  })
}

function saveAvatarStage(context) {
  if (!context.avatarPath) return context

  let fileReady
  if (context.avatarFileId) {
    fileReady = Promise.resolve(context.avatarFileId)
  } else {
    context.currentStage = "头像上传"
    fileReady = uploadImage(context.avatarPath).then((uploadedFile) => {
      if (!uploadedFile?.fileId) {
        throw new Error("后端未返回头像文件ID")
      }
      context.avatarFileId = uploadedFile.fileId
      if (pendingAvatarPath.value === context.avatarPath) {
        pendingAvatarFileId.value = uploadedFile.fileId
      }
      context.completedStages.push("头像上传")
      return uploadedFile.fileId
    })
  }

  return fileReady
    .then((fileId) => {
      context.currentStage = "头像设置"
      return updateAvatar(fileId)
    })
    .then((latestProfile) => {
      context.latestProfile = latestProfile
      context.completedStages.push("头像设置")
      if (pendingAvatarPath.value === context.avatarPath) {
        pendingAvatarPath.value = ""
        pendingAvatarFileId.value = ""
      }
      saveProfile(latestProfile)
      return context
    })
}

function finishProfileSave(context) {
  if (!context.latestProfile) return context

  return applyProfile(context.latestProfile, { keepPendingAvatar: true }).then(() => {
    saveProfile(context.latestProfile)
    return context
  })
}

function showProfileSaveSuccess(context) {
  uni.showToast({
    title: context.latestProfile?.profileCompleted ? "保存成功" : "已保存，资料待完善",
    icon: context.latestProfile?.profileCompleted ? "success" : "none",
  })
  setTimeout(() => {
    uni.navigateBack()
  }, 1000)
}

function showProfileSaveFailure(context, error) {
  const savedSummary = context.completedStages.length
    ? `已保存：${context.completedStages.join("、")}。\n`
    : ""
  const errorDetail = formatApiError(error)
  console.error(`[profile-edit] ${context.currentStage}保存失败`, errorDetail)
  uni.showModal({
    title: context.completedStages.length ? "部分内容已保存" : "保存失败",
    content: `${savedSummary}失败：${context.currentStage}。\n${errorDetail}\n未保存内容仍保留在页面，可修正后重试。`,
    showCancel: false,
  })
}

function onSave() {
  if (saving.value || loading.value) return
  if (PROFILE_MOCK_ENABLED) {
    uni.showToast({ title: "演示模式不会提交资料", icon: "none" })
    return
  }
  if (!profileReady.value) {
    uni.showToast({ title: "资料尚未加载，请重试", icon: "none" })
    return
  }

  const normalizedNickname = nickname.value.trim()
  if (normalizedNickname.length < 2 || normalizedNickname.length > 20) {
    uni.showToast({ title: "昵称需为2至20个字符", icon: "none" })
    return
  }
  if (!gender.value) {
    uni.showToast({ title: "请选择性别", icon: "none" })
    return
  }
  if (!birthDate.value) {
    uni.showToast({ title: "请选择出生日期", icon: "none" })
    return
  }
  if (!provinceCode.value || !cityCode.value || !districtCode.value) {
    uni.showToast({ title: "请选择完整地区信息", icon: "none" })
    return
  }
  if (originalTagIds.value.length > 0 && selectedTags.value.length === 0) {
    uni.showToast({ title: "兴趣标签至少保留1个", icon: "none" })
    return
  }

  const context = createSaveContext(normalizedNickname)
  saving.value = true
  return saveBasicProfileStage(context)
    .then(saveInterestsStage)
    .then(saveNicknameStage)
    .then(saveAvatarStage)
    .then(finishProfileSave)
    .then(showProfileSaveSuccess)
    .catch((error) => showProfileSaveFailure(context, error))
    .finally(() => {
      saving.value = false
    })
}

function goBack() {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
  } else {
    uni.reLaunch({ url: '/pages/mine/index' })
  }
}

function formatTime(isoStr) {
  if (!isoStr) return ""
  const d = new Date(isoStr)
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`
}
</script>

<template>
  <view class="edit-profile">
    <!-- 顶部导航栏 -->
    <view class="edit-profile__nav">
      <view class="edit-profile__nav-back" @click.stop="goBack">
        <text class="edit-profile__nav-back-icon">←</text>
      </view>
      <text class="edit-profile__nav-title">编辑资料</text>
      <view
        class="edit-profile__nav-save"
        :class="{ 'edit-profile__nav-save--disabled': saving || loading || !profileReady || PROFILE_MOCK_ENABLED }"
        @click.stop="onSave"
      >
        <text class="edit-profile__nav-save-text">{{ saving ? '保存中...' : '保存' }}</text>
      </view>
    </view>

    <view v-if="PROFILE_MOCK_ENABLED" class="edit-profile__notice edit-profile__notice--demo">
      <text>当前为资料演示模式，所有操作均不会提交到后端。</text>
    </view>

    <view v-else-if="loadError" class="edit-profile__notice edit-profile__notice--error">
      <text class="edit-profile__notice-text">{{ loadError }}</text>
      <button class="edit-profile__retry" :disabled="loading" @tap="loadPageData">重新加载</button>
    </view>

    <scroll-view class="edit-profile__content" scroll-y>
      <!-- 头像 -->
      <view class="edit-profile__section">
        <view class="edit-profile__avatar-row" @tap="chooseAvatar">
          <text class="edit-profile__label">头像</text>
          <view class="edit-profile__avatar-wrap">
            <image
              v-if="avatar"
              class="edit-profile__avatar"
              :src="avatar"
              mode="aspectFill"
            />
            <view v-else class="edit-profile__avatar edit-profile__avatar--placeholder">
              <text class="edit-profile__avatar-placeholder-text">{{ (nickname || 'U').charAt(0) }}</text>
            </view>
            <text class="edit-profile__arrow">›</text>
          </view>
        </view>
      </view>

      <!-- 基本信息 -->
      <view class="edit-profile__section">
        <view class="edit-profile__field">
          <text class="edit-profile__label">用户ID</text>
          <text class="edit-profile__value-readonly">{{ userId || '—' }}</text>
        </view>

        <view class="edit-profile__divider" />

        <view class="edit-profile__field">
          <view class="edit-profile__label-row">
            <text class="edit-profile__required">*</text>
            <text class="edit-profile__label">昵称</text>
          </view>
          <input
            class="edit-profile__input"
            v-model="nickname"
            placeholder="请输入昵称（2-20字）"
            maxlength="20"
            :disabled="!nicknameChangeable"
          />
        </view>
        <view v-if="!nicknameChangeable" class="edit-profile__hint">
          <text class="edit-profile__hint-text">下次可修改时间：{{ formatTime(nextNicknameChangeAt) }}</text>
        </view>

        <view class="edit-profile__divider" />

        <picker :range="genderOptions" range-key="label" @change="onGenderChange">
          <view class="edit-profile__field">
            <view class="edit-profile__label-row">
              <text class="edit-profile__required">*</text>
              <text class="edit-profile__label">性别</text>
            </view>
            <view class="edit-profile__picker">
              <text class="edit-profile__picker-text">
                {{ genderOptions.find(g => g.value === gender)?.label || '请选择' }}
              </text>
              <text class="edit-profile__arrow">›</text>
            </view>
          </view>
        </picker>

        <view class="edit-profile__divider" />

        <picker mode="date" :value="birthDate" :end="new Date().toISOString().split('T')[0]" @change="onDateChange">
          <view class="edit-profile__field">
            <view class="edit-profile__label-row">
              <text class="edit-profile__required">*</text>
              <text class="edit-profile__label">生日</text>
            </view>
            <view class="edit-profile__picker">
              <text class="edit-profile__picker-text">
                {{ birthDate || '请选择出生日期' }}
              </text>
              <text class="edit-profile__arrow">›</text>
            </view>
          </view>
        </picker>

        <view class="edit-profile__divider" />

        <picker mode="region" :value="[provinceName, cityName, districtName]" @change="onRegionChange">
          <view class="edit-profile__field">
            <view class="edit-profile__label-row">
              <text class="edit-profile__required">*</text>
              <text class="edit-profile__label">地区</text>
            </view>
            <view class="edit-profile__picker">
              <text class="edit-profile__picker-text">{{ regionDisplay || '请选择省/市/区' }}</text>
              <text class="edit-profile__arrow">›</text>
            </view>
          </view>
        </picker>
      </view>

      <!-- 手机绑定 -->
      <view class="edit-profile__section">
        <view class="edit-profile__field">
          <text class="edit-profile__label">手机号</text>
          <view class="edit-profile__picker">
            <text class="edit-profile__picker-text" :class="{ 'edit-profile__picker-text--bound': phoneBound }">
              {{ phoneBound ? maskedPhone : '—' }}
            </text>
            <button
              class="edit-profile__phone-action"
              open-type="getPhoneNumber"
              :loading="bindingPhone"
              :disabled="bindingPhone"
              @getphonenumber="onGetPhoneNumber"
            >
              {{ phoneBound ? '更换' : '绑定' }}
            </button>
          </view>
        </view>

        <view class="edit-profile__divider" />

        <view class="edit-profile__field">
          <text class="edit-profile__label">绑定状态</text>
          <view class="edit-profile__bound-status">
            <text :class="phoneBound ? 'edit-profile__bound--yes' : 'edit-profile__bound--no'">
              {{ phoneBound ? '已绑定' : '未绑定' }}
            </text>
          </view>
        </view>

        <view class="edit-profile__divider" />

        <view class="edit-profile__field">
          <text class="edit-profile__label">邮箱</text>
          <input
            class="edit-profile__input"
            v-model="email"
            placeholder="请输入邮箱（选填）"
            type="text"
          />
        </view>
      </view>

      <!-- 账号状态 -->
      <view class="edit-profile__section">
        <view class="edit-profile__field">
          <text class="edit-profile__label">资料完善</text>
          <view class="edit-profile__bound-status">
            <text :class="profileCompleted ? 'edit-profile__bound--yes' : 'edit-profile__bound--no'">
              {{ profileCompleted ? '已完善' : '未完善' }}
            </text>
          </view>
        </view>

        <view class="edit-profile__divider" />

        <view class="edit-profile__field">
          <text class="edit-profile__label">完善时间</text>
          <text class="edit-profile__value-readonly">{{ completedAt ? formatTime(completedAt) : '—' }}</text>
        </view>

        <view class="edit-profile__divider" />

        <view class="edit-profile__field">
          <text class="edit-profile__label">昵称可改时间</text>
          <text class="edit-profile__value-readonly">{{ nextNicknameChangeAt ? formatTime(nextNicknameChangeAt) : '无限制' }}</text>
        </view>
      </view>

      <!-- 个人简介 -->
      <view class="edit-profile__section">
        <view class="edit-profile__field edit-profile__field--vertical">
          <text class="edit-profile__label">个人简介</text>
          <textarea
            class="edit-profile__textarea"
            v-model="bio"
            placeholder="介绍一下自己吧（最多200字）"
            maxlength="200"
            :auto-height="true"
          />
        </view>
      </view>

      <!-- 兴趣标签 -->
      <view class="edit-profile__section" v-if="allTags.length">
        <text class="edit-profile__section-title">兴趣标签（最多20个）</text>
        <view class="edit-profile__tags">
          <view
            v-for="tag in allTags"
            :key="tag.interestTagId"
            class="edit-profile__tag"
            :class="{ 'edit-profile__tag--selected': selectedTags.includes(tag.interestTagId) }"
            @tap="toggleTag(tag.interestTagId)"
          >
            <text>{{ tag.name }}</text>
          </view>
        </view>
      </view>

      <view class="edit-profile__bottom-space" />
    </scroll-view>
  </view>
</template>

<style scoped>
.edit-profile {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f6f7fb;
}

.edit-profile__nav {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 50px 16px 12px;
  background: #ffffff;
  position: relative;
  z-index: 100;
}

.edit-profile__nav-back {
  width: 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.edit-profile__nav-back-icon {
  font-size: 26px;
  color: #111111;
}

.edit-profile__nav-title {
  font-size: 22px;
  font-weight: 600;
  color: #111111;
}

.edit-profile__nav-save {
  min-height: 44px;
  padding: 8px 18px;
  border-radius: 16px;
  background: linear-gradient(135deg, #FFB149, #F56551);
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
}

.edit-profile__nav-save--disabled {
  opacity: 0.55;
}

.edit-profile__nav-save-text {
  font-size: 17px;
  color: #ffffff;
  font-weight: 600;
}

.edit-profile__content {
  flex: 1;
  height: 0;
}

.edit-profile__notice {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 12px 16px 0;
  padding: 12px 14px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 20px;
}

.edit-profile__notice--demo {
  background: #fff7e6;
  color: #8a4b00;
}

.edit-profile__notice--error {
  background: #fff1f0;
  color: #a8121f;
}

.edit-profile__notice-text {
  flex: 1;
  white-space: pre-wrap;
}

.edit-profile__retry {
  flex-shrink: 0;
  margin: 0;
  min-height: 40px;
  padding: 6px 14px;
  border: none;
  border-radius: 12px;
  background: #ffffff;
  color: #a8121f;
  font-size: 14px;
  line-height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.edit-profile__retry::after {
  border: none;
}

.edit-profile__section {
  background: #ffffff;
  border-radius: 12px;
  margin: 12px 16px 0;
  padding: 0 16px;
}

.edit-profile__section-title {
  font-size: 18px;
  font-weight: 600;
  color: #111111;
  padding: 16px 0 12px;
}

.edit-profile__avatar-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  min-height: 56px;
  padding: 16px 0;
  box-sizing: border-box;
}

.edit-profile__avatar-wrap {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
}

.edit-profile__avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: #f0f1f4;
}

.edit-profile__avatar--placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #FFB149 0%, #F56551 100%);
}

.edit-profile__avatar-placeholder-text {
  font-size: 30px;
  font-weight: 600;
  color: #ffffff;
}

.edit-profile__field {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  min-height: 56px;
  padding: 14px 0;
  box-sizing: border-box;
}

.edit-profile__field--vertical {
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
}

.edit-profile__label {
  font-size: 19px;
  color: #111111;
  font-weight: 500;
}

.edit-profile__label-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 2px;
}

.edit-profile__required {
  font-size: 19px;
  color: #f56551;
  font-weight: 500;
}

.edit-profile__input {
  flex: 1;
  text-align: right;
  font-size: 19px;
  color: #1a1a1a;
}

.edit-profile__picker {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 4px;
}

.edit-profile__picker-text {
  font-size: 19px;
  color: #4a5568;
}

.edit-profile__arrow {
  font-size: 22px;
  color: #9aa3ad;
}

.edit-profile__divider {
  height: 1px;
  background: #e3e6ea;
}

.edit-profile__textarea {
  width: 100%;
  min-height: 90px;
  font-size: 18px;
  color: #1a1a1a;
  line-height: 26px;
  padding: 12px;
  background: #f6f7fb;
  border-radius: 8px;
  box-sizing: border-box;
}

.edit-profile__tags {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 8px;
  padding-bottom: 16px;
}

.edit-profile__tag {
  min-height: 40px;
  display: flex;
  align-items: center;
  padding: 8px 16px;
  border-radius: 16px;
  background: #f5f5f5;
  font-size: 16px;
  color: #4a5568;
  box-sizing: border-box;
}

.edit-profile__tag--selected {
  background: rgba(245, 101, 81, 0.12);
  color: #d63620;
  border: 1px solid #F56551;
}

.edit-profile__bottom-space {
  height: 40px;
}

.edit-profile__hint {
  padding: 0 0 12px;
}

.edit-profile__hint-text {
  font-size: 15px;
  color: #d63620;
}

.edit-profile__picker-text--bound {
  color: #111111;
}

.edit-profile__phone-action {
  margin: 0 0 0 8px;
  min-height: 40px;
  padding: 6px 16px;
  border: none;
  border-radius: 14px;
  background: rgba(245, 101, 81, 0.12);
  color: #d63620;
  font-size: 16px;
  line-height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.edit-profile__phone-action::after {
  border: none;
}

.edit-profile__value-readonly {
  font-size: 17px;
  color: #4a5568;
}

.edit-profile__bound-status {
  display: flex;
  align-items: center;
}

.edit-profile__bound--yes {
  font-size: 16px;
  color: #2f8b5d;
  background: rgba(79, 192, 141, 0.14);
  padding: 4px 12px;
  border-radius: 10px;
}

.edit-profile__bound--no {
  font-size: 16px;
  color: #d63620;
  background: rgba(245, 101, 81, 0.12);
  padding: 4px 12px;
  border-radius: 10px;
}
</style>
