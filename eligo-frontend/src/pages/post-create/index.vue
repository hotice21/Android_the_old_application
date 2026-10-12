<script setup>
import { ref, computed } from "vue"
import { createPost } from "../../api/post"

const title = ref("")
const content = ref("")
const selectedCategory = ref("")
const images = ref([])
const publishing = ref(false)
const inputMode = ref("text") // "text" | "voice"

// 老年活动室的 tag
const categories = [
  { code: "square_dance", label: "💃 广场舞" },
  { code: "tai_chi", label: "🧘 太极拳" },
  { code: "band", label: "🎵 乐队" },
  { code: "calligraphy", label: "✍️ 书法" },
  { code: "chess", label: "♟️ 象棋" },
]

const canPublish = computed(() =>
  title.value.trim().length > 0 && content.value.trim().length > 0 && !publishing.value
)

function goBack() {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
  } else {
    uni.reLaunch({ url: "/pages/dynamic/index" })
  }
}

function chooseCategory(code) {
  selectedCategory.value = selectedCategory.value === code ? "" : code
}

function switchInputMode() {
  inputMode.value = inputMode.value === "text" ? "voice" : "text"
}

function chooseImages() {
  uni.chooseImage({
    count: 9 - images.value.length,
    sizeType: ["compressed"],
    sourceType: ["album", "camera"],
    success(res) {
      images.value = images.value.concat(res.tempFilePaths || [])
    },
    fail(err) {
      if (err?.errMsg?.indexOf("cancel") === -1) {
        uni.showToast({ title: "选择图片失败", icon: "none" })
      }
    },
  })
}

function removeImage(index) {
  images.value.splice(index, 1)
}

async function doPublish() {
  if (!canPublish.value) return
  publishing.value = true
  try {
    await createPost({
      title: title.value.trim(),
      content: content.value.trim(),
      categoryCode: selectedCategory.value || undefined,
    })
    uni.showToast({ title: "发布成功 🎉", icon: "success" })
    setTimeout(() => goBack(), 700)
  } catch (e) {
    const msg = e?.message || "发布失败，请稍后重试"
    uni.showToast({ title: msg, icon: "none" })
  } finally {
    publishing.value = false
  }
}
</script>

<template>
  <view class="page">
    <!-- 顶部栏 -->
    <view class="topbar">
      <view class="topbar__back" @tap="goBack">
        <text class="topbar__icon">‹</text>
      </view>
      <text class="topbar__title">发布动态</text>
      <button
        class="topbar__publish"
        :class="{ 'topbar__publish--disabled': !canPublish }"
        :disabled="!canPublish"
        @tap="doPublish"
      >
        {{ publishing ? "发布中..." : "发布" }}
      </button>
    </view>

    <scroll-view class="content" scroll-y :show-scrollbar="false">
      <!-- 标题输入框（新增） -->
      <view class="section">
        <input
          v-model="title"
          class="section__title-input"
          maxlength="30"
          placeholder="📌 给帖子起个标题..."
          placeholder-class="section__placeholder"
        />
      </view>

      <!-- 正文输入区 -->
      <view class="section">
        <textarea
          v-model="content"
          class="section__textarea"
          maxlength="500"
          placeholder="分享你的想法、活动或心情..."
          placeholder-class="section__placeholder"
          auto-height
        />
        <text class="section__count">{{ content.length }}/500</text>
      </view>

      <!-- 图片区（6列，比原来3列缩小一半） -->
      <view class="section section--image">
        <view class="image-grid">
          <view
            v-for="(img, idx) in images"
            :key="idx"
            class="image-item"
          >
            <image :src="img" mode="aspectFill" class="image-item__img" />
            <view class="image-item__del" @tap="removeImage(idx)">
              <text>×</text>
            </view>
          </view>
          <view v-if="images.length < 9" class="image-add" @tap="chooseImages">
            <text class="image-add__plus">+</text>
          </view>
        </view>
      </view>

      <!-- 老年活动 tag -->
      <view class="section section--tags">
        <text class="section__label">选择活动分类</text>
        <view class="tags">
          <view
            v-for="cat in categories"
            :key="cat.code"
            class="tag"
            :class="{ 'tag--active': selectedCategory === cat.code }"
            @tap="chooseCategory(cat.code)"
          >
            {{ cat.label }}
          </view>
        </view>
      </view>
    </scroll-view>

    <!-- 底部输入区：语音 / 文字 切换 -->
    <view class="bottom-input">
      <view class="mode-switch" @tap="switchInputMode">
        <text v-if="inputMode === 'text'" class="mode-icon">🎤</text>
        <text v-else class="mode-icon">⌨️</text>
      </view>

      <view v-if="inputMode === 'text'" class="bottom-input__text">
        <input
          v-model="content"
          class="bottom-input__input"
          maxlength="200"
          placeholder="在这里输入或继续编辑正文..."
          placeholder-class="bottom-input__placeholder"
          confirm-type="send"
          @confirm="doPublish"
        />
      </view>
      <view v-else class="bottom-input__voice" @tap="uni.showToast({title:'语音功能开发中',icon:'none'})">
        <text class="voice-hint">🎤 按住说话（点击占位）</text>
      </view>

      <button
        class="bottom-input__send"
        :class="{ 'bottom-input__send--disabled': !canPublish }"
        :disabled="!canPublish"
        @tap="doPublish"
      >
        发送
      </button>
    </view>
  </view>
</template>

<style scoped>
.page {
  position: fixed;
  inset: 0 0 60px 0;
  display: flex;
  flex-direction: column;
  background: #f6f7fb;
  font-family: "PingFang SC", sans-serif;
}

/* ===== 顶部栏 ===== */
.topbar {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  border-bottom: 1px solid #eee;
  position: relative;
  flex-shrink: 0;
}
.topbar__back {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.topbar__icon { font-size: 28px; color: #1a1a1a; line-height: 1; }
.topbar__title {
  font-size: 18px; font-weight: 600; color: #1a1a1a;
  position: absolute; left: 50%; transform: translateX(-50%);
}
.topbar__publish {
  margin-right: 12px; padding: 0 16px; height: 32px; line-height: 32px;
  font-size: 15px; font-weight: 600; color: #ffffff;
  background: linear-gradient(140deg, #f56551, #ffb149);
  border-radius: 16px; border: 0;
}
.topbar__publish::after { border: 0; }
.topbar__publish--disabled { background: #d5d8de; }

.content {
  flex: 1;
  padding: 12px;
  box-sizing: border-box;
  padding-bottom: 72px; /* 给底部输入区留空间 */
}

/* ===== Section 通用 ===== */
.section {
  background: #ffffff;
  border-radius: 12px;
  padding: 14px 16px;
  margin-bottom: 10px;
}

.section__title-input {
  width: 100%;
  height: 44px;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
  box-sizing: border-box;
}

.section__textarea {
  width: 100%;
  min-height: 130px;
  font-size: 16px;
  color: #1a1a1a;
  line-height: 24px;
  box-sizing: border-box;
}
.section__placeholder { color: #b8bcc2; font-size: 16px; }
.section__count {
  display: block; text-align: right; margin-top: 4px;
  font-size: 12px; color: #b8bcc2;
}
.section__label {
  display: block; font-size: 15px; font-weight: 600;
  color: #1a1a1a; margin-bottom: 10px;
}

/* ===== 图片网格：6 列，比原来 3 列缩小一半 ===== */
.image-grid {
  display: flex; flex-wrap: wrap;
  gap: 6px;
}
.image-item {
  position: relative;
  /* 6 列 = 每张宽度是 1/6 容器宽，比原来 1/3 小一半 */
  width: calc((100% - 5 * 6px) / 6);
  padding-bottom: calc((100% - 5 * 6px) / 6);
  border-radius: 6px; overflow: hidden; background: #f0f2f5;
}
.image-item__img {
  position: absolute; inset: 0; width: 100%; height: 100%;
}
.image-item__del {
  position: absolute; top: 2px; right: 2px;
  width: 18px; height: 18px; border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff; font-size: 13px; line-height: 18px; text-align: center;
}
.image-add {
  width: calc((100% - 5 * 6px) / 6);
  padding-bottom: calc((100% - 5 * 6px) / 6);
  border-radius: 6px;
  border: 1px dashed #d0d4db;
  background: #fafbfc;
  display: flex; align-items: center; justify-content: center;
  position: relative;
}
.image-add__plus {
  position: absolute; top: 50%; left: 50%;
  transform: translate(-50%, -55%);
  font-size: 22px; color: #b8bcc2; line-height: 1;
}

/* ===== Tag（老年活动） ===== */
.tags {
  display: flex; flex-wrap: wrap; gap: 8px;
}
.tag {
  padding: 8px 14px; border-radius: 20px;
  background: #f2f3f5; font-size: 14px;
  color: #4a5568; border: 1px solid transparent;
}
.tag--active {
  background: linear-gradient(140deg, rgba(245, 101, 81, 0.12), rgba(255, 177, 73, 0.12));
  color: #f56551; border-color: #ffb7ad; font-weight: 600;
}

/* ===== 底部语音/文字 输入区 ===== */
.bottom-input {
  position: fixed; left: 0; right: 0; bottom: 0;
  height: 60px; padding: 8px 12px 8px;
  background: #ffffff; border-top: 1px solid #eee;
  display: flex; align-items: center; gap: 8px;
  z-index: 50;
}
.mode-switch {
  width: 40px; height: 40px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  border-radius: 50%;
  background: linear-gradient(140deg, #f56551, #ffb149);
}
.mode-icon { font-size: 20px; line-height: 1; }

.bottom-input__text {
  flex: 1; display: flex; align-items: center;
  height: 40px; background: #f2f3f5; border-radius: 20px; padding: 0 14px;
}
.bottom-input__input {
  flex: 1; font-size: 15px; color: #1a1a1a; background: transparent;
}
.bottom-input__placeholder { color: #b8bcc2; font-size: 15px; }

.bottom-input__voice {
  flex: 1;
  height: 40px; background: #f2f3f5; border-radius: 20px;
  display: flex; align-items: center; justify-content: center;
}
.voice-hint { font-size: 14px; color: #6b7280; }

.bottom-input__send {
  flex-shrink: 0; padding: 0 18px; height: 40px; line-height: 40px;
  font-size: 15px; font-weight: 600; color: #ffffff;
  background: linear-gradient(140deg, #f56551, #ffb149);
  border-radius: 20px; border: 0;
}
.bottom-input__send::after { border: 0; }
.bottom-input__send--disabled { background: #d5d8de; }
</style>
