<script setup>
import { ref, computed } from "vue"
import { onLoad } from "@dcloudio/uni-app"
import { createPost } from "../../api/post"

const content = ref("")
const title = ref("")
const selectedCategory = ref("")
const images = ref([])
const publishing = ref(false)

const categories = [
  { code: "coffee", label: "☕ 咖啡" },
  { code: "hiking", label: "🥾 户外" },
  { code: "sports", label: "⚽ 运动" },
]

const canPublish = computed(() => content.value.trim().length > 0 && !publishing.value)

function goBack() {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
    return
  }
  uni.reLaunch({ url: "/pages/plaza/index" })
}

function chooseCategory(code) {
  selectedCategory.value = selectedCategory.value === code ? "" : code
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
      content: content.value.trim(),
      title: title.value.trim() || undefined,
      categoryCode: selectedCategory.value || undefined,
    })
    uni.showToast({ title: "发布成功", icon: "success" })
    setTimeout(() => {
      const pages = getCurrentPages()
      if (pages.length > 1) {
        uni.navigateBack()
      } else {
        uni.reLaunch({ url: "/pages/dynamic/index" })
      }
    }, 600)
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
            <text class="image-add__label">添加图片</text>
          </view>
        </view>
      </view>

      <view class="section section--tags">
        <text class="section__label">选择分类</text>
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
  </view>
</template>

<style scoped>
.page {
  position: fixed;
  inset: 0;
  display: flex;
  flex-direction: column;
  background: #f6f7fb;
  font-family: "PingFang SC", sans-serif;
}

.topbar {
  height: 56px;
  padding-top: env(safe-area-inset-top);
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  border-bottom: 1px solid #eee;
  position: relative;
}
.topbar__back {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.topbar__icon {
  font-size: 28px;
  color: #1a1a1a;
  line-height: 1;
}
.topbar__title {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
}
.topbar__publish {
  margin-right: 12px;
  padding: 0 16px;
  height: 32px;
  line-height: 32px;
  font-size: 15px;
  font-weight: 600;
  color: #ffffff;
  background: linear-gradient(140deg, #f56551, #ffb149);
  border-radius: 16px;
  border: 0;
}
.topbar__publish::after { border: 0; }
.topbar__publish--disabled {
  background: #d5d8de;
  color: #ffffff;
}

.content {
  flex: 1;
  padding: 12px;
  box-sizing: border-box;
}

.section {
  background: #ffffff;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
}
.section__textarea {
  width: 100%;
  min-height: 160px;
  font-size: 16px;
  color: #1a1a1a;
  line-height: 24px;
  box-sizing: border-box;
}
.section__placeholder {
  color: #b8bcc2;
  font-size: 16px;
}
.section__count {
  display: block;
  text-align: right;
  margin-top: 4px;
  font-size: 12px;
  color: #b8bcc2;
}
.section__label {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 10px;
}

.image-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.image-item {
  position: relative;
  width: calc((100% - 20px) / 3);
  padding-bottom: calc((100% - 20px) / 3);
  border-radius: 8px;
  overflow: hidden;
  background: #f0f2f5;
}
.image-item__img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}
.image-item__del {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  font-size: 16px;
  line-height: 22px;
  text-align: center;
}
.image-add {
  width: calc((100% - 20px) / 3);
  padding-bottom: calc((100% - 20px) / 3);
  border-radius: 8px;
  border: 1px dashed #d0d4db;
  background: #fafbfc;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
}
.image-add__plus {
  position: absolute;
  top: 35%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 28px;
  color: #b8bcc2;
  line-height: 1;
}
.image-add__label {
  position: absolute;
  bottom: 25%;
  left: 50%;
  transform: translateX(-50%);
  font-size: 12px;
  color: #b8bcc2;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.tag {
  padding: 8px 16px;
  border-radius: 20px;
  background: #f2f3f5;
  font-size: 14px;
  color: #4a5568;
  border: 1px solid transparent;
}
.tag--active {
  background: linear-gradient(140deg, rgba(245, 101, 81, 0.12), rgba(255, 177, 73, 0.12));
  color: #f56551;
  border-color: #ffb7ad;
  font-weight: 600;
}
</style>
