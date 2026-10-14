<script setup>
import { ref, computed, nextTick, onBeforeUnmount } from "vue"
import { onLoad } from "@dcloudio/uni-app"
import { getConversationMessages, sendMessage } from "../../api/chat"
import { getMyProfile } from "../../api/profile"
import { upload } from "../../api/request"

const convId = ref("")
const peerUserId = ref("")
const messages = ref([])
const inputMode = ref("text") // "text" | "voice"
const textContent = ref("")
const sending = ref(false)
const loading = ref(false)
const myUserId = ref(null)

// ======== 录音状态 ========
const recording = ref(false)
const recordSeconds = ref(0)
const recordTempPath = ref("")
const recorderManager = uni.getRecorderManager()

let recordTimer = null

recorderManager.onStart(() => {
  recording.value = true
  recordSeconds.value = 0
  recordTempPath.value = ""
  recordTimer = setInterval(() => {
    recordSeconds.value++
    if (recordSeconds.value >= 60) {
      // 最长 60 秒自动停止
      recorderManager.stop()
    }
  }, 1000)
})

recorderManager.onStop((res) => {
  clearInterval(recordTimer)
  recordTimer = null
  recording.value = false
  recordTempPath.value = res.tempFilePath || ""
  const duration = recordSeconds.value
  recordSeconds.value = 0
  if (recordTempPath.value && duration >= 1) {
    uploadAndSendVoice(recordTempPath.value, duration)
  } else {
    uni.showToast({ title: "录音太短", icon: "none" })
  }
})

recorderManager.onError((err) => {
  clearInterval(recordTimer)
  recordTimer = null
  recording.value = false
  uni.showToast({ title: "录音失败", icon: "none" })
})

onBeforeUnmount(() => {
  if (recordTimer) clearInterval(recordTimer)
  recorderManager.stop()
})

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
  if (recording.value) return
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
      voiceFileId: m.voiceFileId,
      voiceDurationSeconds: m.voiceDurationSeconds,
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

/** 上传语音文件 + 发送语音消息 */
async function uploadAndSendVoice(tempPath, durationSeconds) {
  if (!convId.value || sending.value) return
  sending.value = true

  // 先本地塞一条语音气泡
  const localId = "local-voice-" + Date.now()
  messages.value.push({
    id: localId,
    senderUserId: myUserId.value,
    content: "",
    messageType: 3,
    voiceFileId: null,
    voiceDurationSeconds: durationSeconds,
    createdAt: new Date().toISOString(),
    timeText: formatTime(new Date().toISOString()),
    mine: true,
    uploading: true,
  })
  await nextTick()

  try {
    // 1) 上传文件
    const uploadRes = await upload({
      path: "/api/v1/files/images",
      filePath: tempPath,
      name: "file",
      formData: { purpose: "VOICE" },
    })
    const voiceFileId = uploadRes?.fileId

    // 2) 发送消息
    await sendMessage(convId.value, {
      messageType: 3,
      voiceFileId,
      voiceDurationSeconds: durationSeconds,
    })
    // 更新本地气泡
    const idx = messages.value.findIndex((m) => m.id === localId)
    if (idx >= 0) messages.value[idx].uploading = false
  } catch (e) {
    const idx = messages.value.findIndex((m) => m.id === localId)
    if (idx >= 0) {
      messages.value[idx].uploading = false
      messages.value[idx].uploadFailed = true
    }
    uni.showToast({ title: "语音发送失败", icon: "none" })
  } finally {
    sending.value = false
  }
}

async function doSend() {
  if (!canSend.value || sending.value) return
  const body = textContent.value.trim()
  sending.value = true
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

// ======== 录音按钮交互：按下开始，松开结束，超过上滑取消 ========
let startY = 0
let cancelling = false

function onVoiceTouchStart(e) {
  startY = e.touches?.[0]?.clientY || 0
  cancelling = false
  recordSeconds.value = 0
  recorderManager.start({
    duration: 60000,
    format: "mp3",
    sampleRate: 44100,
    numberOfChannels: 1,
    encodeBitRate: 192000,
  })
}

function onVoiceTouchMove(e) {
  const currentY = e.touches?.[0]?.clientY || 0
  const delta = startY - currentY
  cancelling = delta > 50
}

function onVoiceTouchEnd() {
  if (!recording.value) return
  if (cancelling) {
    recorderManager.stop()
    // 停止后 onStop 里会判断
    recordTempPath.value = "" // 空路径 → 不发送
    uni.showToast({ title: "已取消", icon: "none" })
    // 手动清理 timer（onStop 会清理，但可能因为太快没触发）
    if (recordTimer) { clearInterval(recordTimer); recordTimer = null; recording.value = false; recordSeconds.value = 0 }
  } else {
    recorderManager.stop()
  }
}

// 播放语音气泡
function playVoiceMessage(m) {
  // 简易实现：用 uni.createInnerAudioContext 播放后端文件
  // 后端 FileView 有 url 字段，但这里 voiceFileId 已知
  // 实际需要 GET /api/v1/files/{id}/content
  if (!m.mine && !m.voiceFileId) return
  const audio = uni.createInnerAudioContext()
  audio.src = `/api/v1/files/${m.voiceFileId}/content`
  audio.play()
}

onLoad(async (options) => {
  if (options?.convId) convId.value = String(options.convId)
  if (options?.peerUserId) peerUserId.value = String(options.peerUserId)
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
        v-for="(m) in messages"
        :key="m.id"
        :id="'msg-' + m.id"
        class="msg-row"
        :class="{ 'msg-row--mine': m.mine }"
      >
        <view v-if="!m.mine" class="msg-avatar">
          <text>对</text>
        </view>

        <!-- 语音气泡 -->
        <view v-if="m.messageType === 3" class="msg-voice-bubble" :class="{ 'msg-bubble--mine': m.mine }" @tap="playVoiceMessage(m)">
          <text class="voice-bubble__icon">🎤</text>
          <view class="voice-bubble__bars">
            <text v-for="i in 8" :key="i" class="voice-bar" :style="{ height: (4 + i * 2) + 'px' }" />
          </view>
          <text class="voice-bubble__duration">{{ m.voiceDurationSeconds }}″</text>
          <text v-if="m.uploading" class="voice-bubble__status">发送中...</text>
          <text v-if="m.uploadFailed" class="voice-bubble__status">失败</text>
        </view>

        <!-- 文本气泡 -->
        <view v-else class="msg-bubble" :class="{ 'msg-bubble--mine': m.mine }">
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

    <!-- 录音中浮层 -->
    <view v-if="recording" class="recording-mask">
      <view class="recording-box" :class="{ 'recording-box--cancel': cancelling }">
        <text class="recording-box__icon">{{ cancelling ? '✕' : '🎙️' }}</text>
        <text class="recording-box__text">{{ cancelling ? '松开手指取消发送' : '录音中... ' + recordSeconds + 's' }}</text>
      </view>
    </view>

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
      <view
        v-else
        class="inputbar__center inputbar__voice-btn"
        :class="{ 'inputbar__voice-btn--recording': recording, 'inputbar__voice-btn--cancel': cancelling }"
        @touchstart="onVoiceTouchStart"
        @touchmove="onVoiceTouchMove"
        @touchend="onVoiceTouchEnd"
        @touchcancel="onVoiceTouchEnd"
      >
        <text v-if="!recording">按住 说话</text>
        <text v-else>{{ cancelling ? '松开 取消' : recordSeconds + 's' }}</text>
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
        <view v-else class="send-btn send-btn--voice-disabled" />
      </view>
    </view>
  </view>
</template>

<style scoped>
.page { position: fixed; inset: 0; display: flex; flex-direction: column; background: #f6f7fb; font-family: "PingFang SC", sans-serif; }
.topbar { height: 56px; padding-top: env(safe-area-inset-top); display: flex; align-items: center; justify-content: space-between; background: #ffffff; border-bottom: 1px solid #eee; position: relative; }
.topbar__back { width: 44px; height: 44px; display: flex; align-items: center; justify-content: center; }
.topbar__icon { font-size: 28px; color: #1a1a1a; line-height: 1; }
.topbar__title { font-size: 18px; font-weight: 600; color: #1a1a1a; position: absolute; left: 50%; transform: translateX(-50%); }
.topbar__right { width: 44px; }
.message-list { flex: 1; padding: 12px 12px 16px; box-sizing: border-box; }
.message-list__loading, .message-list__empty { padding: 60px 0; text-align: center; color: #999; font-size: 14px; }
.msg-row { display: flex; align-items: flex-start; margin-bottom: 14px; flex-wrap: wrap; }
.msg-row--mine { flex-direction: row-reverse; }
.msg-avatar { width: 36px; height: 36px; border-radius: 50%; background: #e3e6ea; color: #7a8290; font-size: 13px; font-weight: 600; display: flex; align-items: center; justify-content: center; flex-shrink: 0; margin-right: 8px; }
.msg-row--mine .msg-avatar { margin-right: 0; margin-left: 8px; background: linear-gradient(135deg, #ffb347, #ff7a00); color: #fff; }
.msg-bubble { max-width: 70%; padding: 10px 14px; background: #ffffff; border-radius: 16px; color: #1a1a1a; font-size: 15px; line-height: 22px; word-break: break-all; border-top-left-radius: 4px; margin-top: 2px; }
.msg-row--mine .msg-bubble { background: linear-gradient(140deg, #ffb149, #f56551); color: #ffffff; border-top-left-radius: 16px; border-top-right-radius: 4px; }
.msg-content { white-space: pre-wrap; }
.msg-time { width: 100%; padding-left: 44px; margin-top: 4px; font-size: 11px; color: #a7adb6; }
.msg-time--mine { padding-left: 0; padding-right: 44px; text-align: right; }

/* 语音气泡 */
.msg-voice-bubble { display: flex; align-items: center; gap: 8px; padding: 12px 18px; background: #ffffff; border-radius: 16px; max-width: 200px; min-height: 40px; border-top-left-radius: 4px; margin-top: 2px; }
.msg-voice-bubble.msg-bubble--mine { background: linear-gradient(140deg, #ffb149, #f56551); color: #ffffff; border-top-left-radius: 16px; border-top-right-radius: 4px; }
.voice-bubble__icon { font-size: 18px; }
.voice-bubble__bars { display: flex; gap: 2px; align-items: center; height: 20px; }
.voice-bar { width: 3px; background: currentColor; border-radius: 2px; }
.voice-bubble__duration { font-size: 13px; font-weight: 500; }
.voice-bubble__status { font-size: 11px; opacity: 0.7; }

.inputbar { display: flex; align-items: flex-end; padding: 8px 10px; padding-bottom: calc(8px + env(safe-area-inset-bottom)); background: #ffffff; border-top: 1px solid #eee; gap: 8px; }
.inputbar__left { width: 38px; height: 38px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.inputbar__icon { font-size: 22px; }
.inputbar__center { flex: 1; min-height: 38px; background: #f2f3f5; border-radius: 19px; padding: 8px 14px; box-sizing: border-box; }
.inputbar__textarea { width: 100%; font-size: 15px; color: #1a1a1a; line-height: 22px; min-height: 22px; max-height: 120px; }
.inputbar__placeholder { color: #a7adb6; }
.inputbar__voice-btn { display: flex; align-items: center; justify-content: center; font-size: 15px; color: #4a5568; user-select: none; }
.inputbar__voice-btn--recording { background: #ffe0e0; color: #d9402a; }
.inputbar__voice-btn--cancel { background: #ffd0d0; color: #d9402a; }
.inputbar__right { flex-shrink: 0; }
.send-btn { height: 38px; line-height: 38px; padding: 0 16px; border-radius: 19px; background: #d5d8de; color: #ffffff; font-size: 14px; font-weight: 600; border: 0; margin: 0; }
.send-btn::after { border: 0; }
.send-btn--active { background: linear-gradient(140deg, #f56551, #ffb149); }
.send-btn--voice-disabled { height: 38px; padding: 0 12px; background: #d5d8de; border-radius: 19px; }

/* 录音中浮层 */
.recording-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.35); display: flex; align-items: center; justify-content: center; z-index: 300; }
.recording-box { width: 180px; height: 180px; border-radius: 16px; background: rgba(30,30,30,0.9); display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px; }
.recording-box--cancel { background: rgba(180,40,40,0.9); }
.recording-box__icon { font-size: 50px; }
.recording-box__text { color: #fff; font-size: 14px; }
</style>
