<script setup>
import { ref, computed, nextTick } from "vue"
import { onLoad } from "@dcloudio/uni-app"
import { getConversationMessages, sendMessage } from "../../api/chat"
import { getMyProfile } from "../../api/profile"

const convId = ref("")
const peerUserId = ref("")
const messages = ref([])
const inputMode = ref("text") // "text" | "voice"
const textContent = ref("")
const sending = ref(false)
const loading = ref(false)
const myUserId = ref(null)

const canSend = computed(() => inputMode.value === "text" && textContent.value.trim().length > 0)

const peerName = computed(() => peerUserId.value ? `用户${peerUserId.value}` : "聊天")

function formatTime(iso) {
  if (!iso) return ""
  const d = new Date(iso)
  const hh = String(d.getHours()).padStart(2, "0")
  const mm = String(d.getMinutes()).padStart(2, "0")
  return `${hh}:${mm}`
}

function goBack() {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
    return
  }
  uni.reLaunch({ url: "/pages/chat/index" })
}

function switchMode() {
  inputMode.value = inputMode.value === "text" ? "voice" : "text"
}

async function loadMessages() {
  if (!convId.value) return
  loading.value = true
  try {
    const data = await getConversationMessages(convId.value, null, 50)
    const items = data?.items || data || []
    messages.value = items.map((m) => ({
      id: m.id,
      senderUserId: m.senderUserId,
      content: m.content,
      messageType: m.messageType,
      createdAt: m.createdAt,
      timeText: formatTime(m.createdAt),
      mine: Number(m.senderUserId) === Number(myUserId.value),
    }))
    await nextTick()
  } catch (e) {
    console.error("加载消息失败", e)
    uni.showToast({ title: "加载失败", icon: "none" })
  } finally {
    loading.value = false
  }
}

async function doSend() {
  if (!canSend.value || sending.value) return
  const body = textContent.value.trim()
  sending.value = true
  // 先本地塞一条气泡，提高体验
  const localId = "local-" + Date.now()
  messages.value.push({
    id: localId,
    senderUserId: myUserId.value,
    content: body,
    messageType: 1,
    createdAt: new Date().toISOString(),
    timeText: formatTime(new Date().toISOString()),
    mine: true,
  })
  textContent.value = ""
  await nextTick()

  try {
    await sendMessage(convId.value, { content: body, messageType: 1 })
  } catch (e) {
    // 失败时把那条本地消息标记一下
    const idx = messages.value.findIndex((m) => m.id === localId)
    if (idx >= 0) {
      messages.value[idx].content = body + "（发送失败）"
    }
    const msg = e?.message || "发送失败"
    uni.showToast({ title: msg, icon: "none" })
  } finally {
    sending.value = false
  }
}

function onVoiceTap() {
  uni.showToast({ title: "语音功能（开发中）", icon: "none" })
}

onLoad(async (options) => {
  if (options?.convId) {
    convId.value = String(options.convId)
  }
  if (options?.peerUserId) {
    peerUserId.value = String(options.peerUserId)
  }
  try {
    const profile = await getMyProfile()
    myUserId.value = profile?.userId ? Number(profile.userId) : null
  } catch {
    myUserId.value = null
  }
  if (convId.value) {
    await loadMessages()
  }
})
</script>

<template>
  <view class="page">
    <view class="topbar">
      <view class="topbar__back" @tap="goBack">
        <text class="topbar__icon">‹</text>
      </view>
      <text class="topbar__title">{{ peerName }}</text>
      <view class="topbar__right" />
    </view>

    <scroll-view
      class="message-list"
      scroll-y
      :show-scrollbar="false"
      :scroll-into-view="messages.length ? 'msg-' + messages[messages.length - 1].id : ''"
      scroll-with-animation
    >
      <view v-if="loading && messages.length === 0" class="message-list__loading">
        <text>加载中...</text>
      </view>
      <view v-else-if="messages.length === 0" class="message-list__empty">
        <text>暂无消息，发送第一条吧~</text>
      </view>
      <view
        v-for="(m, idx) in messages"
        :key="m.id"
        :id="'msg-' + m.id"
        class="msg-row"
        :class="{ 'msg-row--mine': m.mine }"
      >
        <view v-if="!m.mine" class="msg-avatar">
          <text>对</text>
        </view>
        <view class="msg-bubble" :class="{ 'msg-bubble--mine': m.mine }">
          <text class="msg-content">{{ m.content }}</text>
        </view>
        <view v-if="m.mine" class="msg-avatar msg-avatar--mine">
          <text>我</text>
        </view>
        <view class="msg-time" :class="{ 'msg-time--mine': m.mine }">
          <text>{{ m.timeText }}</text>
        </view>
      </view>
    </scroll-view>

    <view class="inputbar">
      <view class="inputbar__left" @tap="switchMode">
        <text class="inputbar__icon">{{ inputMode === "text" ? "🎤" : "⌨️" }}</text>
      </view>

      <view v-if="inputMode === 'text'" class="inputbar__center">
        <textarea
          v-model="textContent"
          class="inputbar__textarea"
          placeholder="说点什么..."
          placeholder-class="inputbar__placeholder"
          auto-height
          :maxlength="500"
          confirm-type="send"
          @confirm="doSend"
        />
      </view>
      <view v-else class="inputbar__center inputbar__voice" @tap="onVoiceTap">
        <text>按住说话</text>
      </view>

      <view class="inputbar__right">
        <button
          v-if="inputMode === 'text'"
          class="send-btn"
          :class="{ 'send-btn--active': canSend }"
          :disabled="!canSend || sending"
          @tap="doSend"
        >
          {{ sending ? "..." : "发送" }}
        </button>
        <view v-else class="send-btn send-btn--voice" @tap="onVoiceTap">
          <text>🎙️</text>
        </view>
      </view>
    </view>
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
.topbar__right {
  width: 44px;
}

.message-list {
  flex: 1;
  padding: 12px 12px 16px;
  box-sizing: border-box;
}
.message-list__loading,
.message-list__empty {
  padding: 60px 0;
  text-align: center;
  color: #999;
  font-size: 14px;
}

.msg-row {
  display: flex;
  align-items: flex-start;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.msg-row--mine {
  flex-direction: row-reverse;
}

.msg-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #e3e6ea;
  color: #7a8290;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-right: 8px;
}
.msg-row--mine .msg-avatar {
  margin-right: 0;
  margin-left: 8px;
  background: linear-gradient(135deg, #ffb347, #ff7a00);
  color: #fff;
}

.msg-bubble {
  max-width: 70%;
  padding: 10px 14px;
  background: #ffffff;
  border-radius: 16px;
  color: #1a1a1a;
  font-size: 15px;
  line-height: 22px;
  word-break: break-all;
  border-top-left-radius: 4px;
  margin-top: 2px;
}
.msg-row--mine .msg-bubble {
  background: linear-gradient(140deg, #ffb149, #f56551);
  color: #ffffff;
  border-top-left-radius: 16px;
  border-top-right-radius: 4px;
}

.msg-content {
  white-space: pre-wrap;
}

.msg-time {
  width: 100%;
  padding-left: 44px;
  margin-top: 4px;
  font-size: 11px;
  color: #a7adb6;
}
.msg-time--mine {
  padding-left: 0;
  padding-right: 44px;
  text-align: right;
}

.inputbar {
  display: flex;
  align-items: flex-end;
  padding: 8px 10px;
  padding-bottom: calc(8px + env(safe-area-inset-bottom));
  background: #ffffff;
  border-top: 1px solid #eee;
  gap: 8px;
}
.inputbar__left {
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.inputbar__icon {
  font-size: 22px;
}

.inputbar__center {
  flex: 1;
  min-height: 38px;
  background: #f2f3f5;
  border-radius: 19px;
  padding: 8px 14px;
  box-sizing: border-box;
}
.inputbar__textarea {
  width: 100%;
  font-size: 15px;
  color: #1a1a1a;
  line-height: 22px;
  min-height: 22px;
  max-height: 120px;
}
.inputbar__placeholder {
  color: #a7adb6;
}
.inputbar__voice {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  color: #4a5568;
  background: #f2f3f5;
}

.inputbar__right {
  flex-shrink: 0;
}
.send-btn {
  height: 38px;
  line-height: 38px;
  padding: 0 16px;
  border-radius: 19px;
  background: #d5d8de;
  color: #ffffff;
  font-size: 14px;
  font-weight: 600;
  border: 0;
  margin: 0;
}
.send-btn::after { border: 0; }
.send-btn--active {
  background: linear-gradient(140deg, #f56551, #ffb149);
}
.send-btn--voice {
  padding: 0 12px;
  background: #f2f3f5;
  color: #4a5568;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
