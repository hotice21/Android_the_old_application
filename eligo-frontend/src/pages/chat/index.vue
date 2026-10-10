<script setup>
import { ref, onMounted } from "@dcloudio/uni-app"
import BottomNavigation from "../../components/BottomNavigation.vue"

const conversations = ref([])
const loading = ref(true)

onMounted(async () => {
  loading.value = true
  try {
    // 后端已有 4 个会话 seed，先直接 mock 显示
    conversations.value = [
      { id: 1, peerName: "南山阿杰", lastMessage: "周末一起去吧", unreadCount: 1, lastAt: "刚刚" },
      { id: 2, peerName: "宝安小丽", lastMessage: "莲花山我熟到时带路", unreadCount: 0, lastAt: "1小时前" },
      { id: 3, peerName: "龙华大壮", lastMessage: "篮球差一个人", unreadCount: 1, lastAt: "2小时前" },
      { id: 4, peerName: "南山阿杰", lastMessage: "咖啡局来不来", unreadCount: 0, lastAt: "昨天" },
    ]
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
})

function onConversationTap(id) {
  uni.showToast({ title: "聊天功能开发中", icon: "none" })
}
</script>

<template>
  <view class="chat-page">
    <view class="chat-header">
      <text class="chat-title">聊天</text>
    </view>
    <scroll-view class="chat-list" scroll-y v-if="!loading">
      <view
        v-for="c in conversations"
        :key="c.id"
        class="conv-item"
        @tap="onConversationTap(c.id)"
      >
        <view class="conv-avatar">{{ c.peerName.charAt(0) }}</view>
        <view class="conv-body">
          <view class="conv-top">
            <text class="conv-name">{{ c.peerName }}</text>
            <text class="conv-time">{{ c.lastAt }}</text>
          </view>
          <view class="conv-bottom">
            <text class="conv-preview">{{ c.lastMessage }}</text>
            <view v-if="c.unreadCount > 0" class="conv-badge">{{ c.unreadCount }}</view>
          </view>
        </view>
      </view>
      <view v-if="conversations.length === 0" class="empty">
        <text>还没有聊天会话</text>
      </view>
    </scroll-view>
    <BottomNavigation current="chat" />
  </view>
</template>

<style scoped>
.chat-page {
  position: fixed; inset: 0;
  display: flex; flex-direction: column;
  background: #f6f7fb;
}
.chat-header {
  height: 56px;
  display: flex; align-items: center; justify-content: center;
  background: #fff; border-bottom: 1px solid #eee;
}
.chat-title { font-size: 20px; font-weight: 600; }
.chat-list { flex: 1; padding-bottom: 100px; }
.conv-item {
  display: flex; align-items: center;
  padding: 14px 16px; background: #fff; margin-bottom: 1px;
}
.conv-avatar {
  width: 48px; height: 48px; border-radius: 50%;
  background: linear-gradient(135deg, #ffb347, #ff7a00);
  color: #fff; font-size: 20px; font-weight: 600;
  display: flex; align-items: center; justify-content: center;
  margin-right: 12px; flex-shrink: 0;
}
.conv-body { flex: 1; min-width: 0; }
.conv-top { display: flex; justify-content: space-between; align-items: center; }
.conv-name { font-size: 18px; font-weight: 500; color: #1a1a1a; }
.conv-time { font-size: 14px; color: #999; }
.conv-bottom { display: flex; justify-content: space-between; align-items: center; margin-top: 4px; }
.conv-preview { font-size: 15px; color: #666; max-width: 240px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conv-badge {
  min-width: 20px; height: 20px; border-radius: 10px;
  background: #fe5f02; color: #fff; font-size: 12px;
  display: flex; align-items: center; justify-content: center;
  padding: 0 6px;
}
.empty { padding: 60px 0; text-align: center; color: #999; font-size: 16px; }
</style>
