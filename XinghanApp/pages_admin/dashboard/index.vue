<template>
  <view class="admin-dashboard">
    <view class="admin-header">
      <view class="header-left">
        <text class="logo-text">星瀚影视 - 管理控制台</text>
      </view>
      <view class="header-right">
        <text class="admin-name">管理员: {{ adminName }}</text>
        <view class="logout-tag" @click="handleAdminLogout">退出登录</view>
      </view>
    </view>

    <scroll-view scroll-y class="dashboard-content">
      
      <view class="section-title">核心数据概览</view>
      <view class="data-grid">
        <view class="data-card" v-for="(item, index) in dataBoard" :key="index">
          <text class="data-title">{{ item.title }}</text>
          <text class="data-value" :style="{ color: item.color }">{{ item.value }}</text>
        </view>
      </view>

      <view class="section-title mt-40">快捷入口</view>
      <view class="menu-grid">
        <view class="menu-item" v-for="(menu, index) in menus" :key="index" @click="handleMenuClick(menu.name)">
          <view class="menu-icon-box" :style="{ background: menu.bg }">
            <text class="menu-icon">{{ menu.icon }}</text>
          </view>
          <text class="menu-name">{{ menu.name }}</text>
        </view>
      </view>

    </scroll-view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';

const adminName = ref('Admin');

// 假装从接口获取看板数据
const dataBoard = ref([
  { title: '总用户数', value: '12,450', color: '#1890ff' },
  { title: '总视频数', value: '8,320', color: '#722ed1' },
  { title: '今日新增视频', value: '45', color: '#52c41a' },
  { title: '今日活跃用户', value: '3,120', color: '#faad14' }
]);

const menus = ref([
  { name: '视频管理', icon: '🎬', bg: 'rgba(24, 144, 255, 0.1)' },
  { name: '用户管理', icon: '👥', bg: 'rgba(114, 46, 209, 0.1)' },
  { name: '分类管理', icon: '📁', bg: 'rgba(82, 196, 26, 0.1)' },
  { name: '系统设置', icon: '⚙️', bg: 'rgba(250, 173, 20, 0.1)' }
]);

onMounted(() => {
  const userInfo = uni.getStorageSync('userInfo');
  if (userInfo && userInfo.username) {
    adminName.value = userInfo.username;
  }
});

const handleMenuClick = (name) => {
  uni.showToast({
    title: `[${name}] 模块开发中`,
    icon: 'none'
  });
};

const handleAdminLogout = () => {
  uni.showModal({
    title: '退出系统',
    content: '确定要退出管理控制台吗？',
    confirmColor: '#1890ff',
    success: (res) => {
      if (res.confirm) {
        uni.removeStorageSync('token');
        uni.removeStorageSync('userInfo');
        uni.reLaunch({
          url: '/pages/login/login'
        });
      }
    }
  });
};
</script>

<style scoped>
.admin-dashboard {
  min-height: 100vh;
  background-color: #141414; /* B端极深灰 */
  display: flex;
  flex-direction: column;
}

/* Header 样式 */
.admin-header {
  height: 120rpx;
  background-color: #1e1e2d; /* 沉稳深蓝/灰 */
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 40rpx;
  box-shadow: 0 4rpx 12rpx rgba(0,0,0,0.3);
  z-index: 10;
}
.logo-text {
  color: #fff;
  font-size: 34rpx;
  font-weight: bold;
  letter-spacing: 2rpx;
}
.header-right {
  display: flex;
  align-items: center;
}
.admin-name {
  color: #a1a1aa;
  font-size: 26rpx;
  margin-right: 30rpx;
}
.logout-tag {
  background-color: rgba(245, 34, 45, 0.15);
  color: #ff4d4f;
  border: 1px solid rgba(245, 34, 45, 0.3);
  padding: 8rpx 20rpx;
  border-radius: 8rpx;
  font-size: 24rpx;
  cursor: pointer;
}

/* 内容区 */
.dashboard-content {
  flex: 1;
  padding: 40rpx;
  box-sizing: border-box;
}
.section-title {
  color: #e5e5e5;
  font-size: 32rpx;
  font-weight: bold;
  margin-bottom: 30rpx;
  border-left: 8rpx solid #1890ff;
  padding-left: 16rpx;
}
.mt-40 {
  margin-top: 60rpx;
}

/* Data Cards 样式 */
.data-grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
}
.data-card {
  width: 48%; /* 2x2 排列 */
  background-color: #1e1e2d;
  border-radius: 16rpx;
  padding: 40rpx 30rpx;
  margin-bottom: 30rpx;
  box-sizing: border-box;
  box-shadow: 0 8rpx 24rpx rgba(0,0,0,0.2);
  display: flex;
  flex-direction: column;
}
.data-title {
  color: #8c8c93;
  font-size: 28rpx;
  margin-bottom: 20rpx;
}
.data-value {
  font-size: 56rpx;
  font-weight: bold;
  font-family: 'Courier New', Courier, monospace; /* 数字强化 */
}

/* Menu Grid 宫格样式 */
.menu-grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
}
.menu-item {
  width: 22%;
  background-color: #1e1e2d;
  border-radius: 16rpx;
  padding: 40rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 4rpx 16rpx rgba(0,0,0,0.15);
  transition: transform 0.2s;
}
.menu-item:active {
  transform: scale(0.96);
}
.menu-icon-box {
  width: 80rpx;
  height: 80rpx;
  border-radius: 20rpx;
  display: flex;
  justify-content: center;
  align-items: center;
  margin-bottom: 20rpx;
}
.menu-icon {
  font-size: 40rpx;
}
.menu-name {
  color: #d1d1d6;
  font-size: 26rpx;
}
</style>