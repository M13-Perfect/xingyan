<template>
  <view class="user-container">
    <view class="header-card">
      <image class="avatar" src="/static/default-avatar.png" mode="aspectFill"></image>
      <view class="user-info">
        <text class="nickname">{{ userInfo.username || '未登录' }}</text>
        <text class="vip-status" v-if="userInfo.vipExpireTime">VIP 有效期至: {{ formatDate(userInfo.vipExpireTime) }}</text>
        <text class="vip-status inactive" v-else>未开通 VIP</text>
      </view>
    </view>
    <view class="menu-list">
      <view class="menu-item" @click="handleAction('观看历史')">
        <text>观看历史</text>
        <text class="arrow">></text>
      </view>
      <view class="menu-item" @click="handleAction('我的金币')">
        <text>我的金币 (0)</text>
        <text class="arrow">></text>
      </view>
      <view class="menu-item" @click="handleAction('设置')">
        <text>应用设置</text>
        <text class="arrow">></text>
      </view>
    </view>
    <button class="logout-btn" @click="handleLogout" v-if="userInfo.username">退出登录</button>
  </view>
</template>
<script setup>
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
const userInfo = ref({});
onShow(() => {
  const storedUser = uni.getStorageSync('userInfo');
  if (storedUser) {
    userInfo.value = storedUser;
  }
});
const formatDate = (dateString) => {
  if (!dateString) return '';
  return dateString.replace('T', ' ').substring(0, 10);
};
const handleAction = (name) => {
  uni.showToast({ title: `${name}模块开发中`, icon: 'none' });
};
const handleLogout = () => {
  uni.showModal({
    title: '提示',
    content: '确定退出登录吗？',
    success: (res) => {
      if (res.confirm) {
        uni.removeStorageSync('token');
        uni.removeStorageSync('userInfo');
        userInfo.value = {};
        uni.reLaunch({ url: '/pages/login/login' });
      }
    }
  });
};
</script>
<style scoped>
.user-container {
  min-height: 100vh;
  background-color: #111114;
  padding: 30rpx;
}
.header-card {
  display: flex;
  align-items: center;
  background: linear-gradient(135deg, #2a2a35, #1c1c23);
  padding: 40rpx 30rpx;
  border-radius: 20rpx;
  margin-bottom: 40rpx;
}
.avatar {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  margin-right: 30rpx;
  background-color: #333;
}
.user-info {
  display: flex;
  flex-direction: column;
}
.nickname {
  color: #fff;
  font-size: 36rpx;
  font-weight: bold;
  margin-bottom: 10rpx;
}
.vip-status {
  color: #fadb14;
  font-size: 24rpx;
}
.vip-status.inactive {
  color: #888;
}
.menu-list {
  background-color: #1c1c23;
  border-radius: 16rpx;
  padding: 0 30rpx;
}
.menu-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 36rpx 0;
  border-bottom: 1px solid #2a2a35;
  color: #e5e5e5;
  font-size: 30rpx;
}
.menu-item:last-child {
  border-bottom: none;
}
.arrow {
  color: #666;
}
.logout-btn {
  margin-top: 60rpx;
  background-color: #2a2a35;
  color: #ff4d4f;
  font-size: 32rpx;
  border-radius: 16rpx;
}
.logout-btn::after {
  border: none;
}
</style>