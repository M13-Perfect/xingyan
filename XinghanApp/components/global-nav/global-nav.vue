<template>
  <view class="nav-container" :class="{ 'transparent': isTransparent }">
    <view class="nav-left">
      <view class="logo-box" @click="goHome">
        <text class="logo-icon">🎬</text>
        <text class="logo-text">星瀚影视</text>
      </view>
      
      <view class="menu-list">
        <text class="menu-item active">首页</text>
        <text class="menu-item">短剧</text>
        <text class="menu-item">电影</text>
        <text class="menu-item">电视剧</text>
        <text class="menu-item">动漫</text>
        <text class="menu-item">综艺</text>
      </view>
    </view>

    <view class="nav-right">
      <view class="search-box">
        <input class="search-input" type="text" placeholder="榜上佳婿" placeholder-class="ph-color" />
        <text class="search-icon">🔍</text>
      </view>

      <view class="nav-icon-wrapper dropdown-wrap">
        <text class="nav-icon">🕒</text>
        <view class="dropdown-menu history-menu">
          <text class="dropdown-title">历史观看</text>
          <view class="history-item" v-if="isLogin">
            <text class="hi-name">逐玉 - 第1集</text>
            <text class="hi-time">刚刚</text>
          </view>
          <text class="empty-tip" v-else>登录后查看历史记录</text>
        </view>
      </view>

      <view class="nav-icon-wrapper dropdown-wrap">
        <text class="nav-icon">👤</text>
        <view class="dropdown-menu user-menu">
          <block v-if="isLogin">
            <view class="user-stat-row">
              <text class="stat-label">会员状态：</text>
              <text class="stat-val highlight">VIP有效</text>
            </view>
            <view class="user-stat-row">
              <text class="stat-label">金币余额：</text>
              <text class="stat-val">280 枚</text>
            </view>
            <button class="logout-btn" @click="handleLogout">退出登录</button>
          </block>
          <block v-else>
            <text class="empty-tip">您还未登录</text>
            <button class="login-action-btn" @click="goLogin">立 即 登 录</button>
          </block>
        </view>
      </view>

      <view class="app-btn">APP</view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';

// 接收外部传参，决定导航栏背景是否透明（详情页需要透明）
defineProps({
  isTransparent: { type: Boolean, default: false }
});

const isLogin = ref(false);

// 因为组件没有 onShow，我们用 onMounted 并在每次点击时通过暴露方法更新状态
onMounted(() => {
  checkLogin();
});

const checkLogin = () => {
  const token = uni.getStorageSync('token');
  isLogin.value = !!token;
};

const goHome = () => uni.reLaunch({ url: '/pages/index/index' });
const goLogin = () => uni.navigateTo({ url: '/pages/login/login' });

const handleLogout = () => {
  uni.removeStorageSync('token');
  uni.removeStorageSync('userInfo');
  isLogin.value = false;
  uni.showToast({ title: '已退出登录', icon: 'none' });
};
</script>

<style scoped>
.nav-container {
  height: 100rpx;
  background-color: #111114;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 40rpx;
  position: fixed; /* 固定在顶部 */
  top: 0;
  left: 0;
  right: 0;
  z-index: 999;
  transition: background-color 0.3s;
}
/* 详情页顶部的透明模式 */
.nav-container.transparent {
  background-color: transparent;
  background: linear-gradient(to bottom, rgba(17,17,20,0.8) 0%, rgba(17,17,20,0) 100%);
}

.nav-left, .nav-right { display: flex; align-items: center; }

.logo-box { display: flex; align-items: center; cursor: pointer; margin-right: 60rpx; }
.logo-icon { font-size: 40rpx; margin-right: 10rpx; color: #00d26a; }
.logo-text { font-size: 36rpx; font-weight: bold; color: #00d26a; }

.menu-list { display: flex; gap: 40rpx; }
.menu-item { color: #ccc; font-size: 28rpx; cursor: pointer; transition: color 0.3s; }
.menu-item:hover, .menu-item.active { color: #00d26a; font-weight: bold; }

/* 搜索框 */
.search-box {
  display: flex; align-items: center; background-color: #2a2a35;
  border-radius: 40rpx; padding: 10rpx 30rpx; margin-right: 30rpx; width: 300rpx;
}
.search-input { flex: 1; font-size: 26rpx; color: #fff; border: none; outline: none; background: transparent;}
.ph-color { color: #666; }

/* 图标与下拉菜单 */
.nav-icon-wrapper { position: relative; padding: 20rpx; cursor: pointer; margin-right: 10rpx;}
.nav-icon { font-size: 36rpx; color: #fff; }

.dropdown-menu {
  position: absolute; top: 80rpx; left: 50%; transform: translateX(-50%);
  background-color: #1c1c23; border: 1px solid #333; border-radius: 12rpx;
  padding: 20rpx; width: 260rpx; opacity: 0; visibility: hidden;
  transition: all 0.3s; box-shadow: 0 10rpx 30rpx rgba(0,0,0,0.5);
}
/* 核心悬浮逻辑 */
.dropdown-wrap:hover .dropdown-menu { opacity: 1; visibility: visible; top: 100%; }

.dropdown-title { display: block; color: #888; font-size: 24rpx; margin-bottom: 20rpx; border-bottom: 1px solid #333; padding-bottom: 10rpx;}
.history-item { display: flex; justify-content: space-between; font-size: 24rpx; color: #ccc;}
.empty-tip { color: #666; font-size: 24rpx; text-align: center; display: block; padding: 20rpx 0;}

.user-stat-row { display: flex; justify-content: space-between; font-size: 24rpx; color: #ccc; margin-bottom: 16rpx;}
.highlight { color: #00d26a; }
.login-action-btn { background-color: #00d26a; color: #fff; font-size: 26rpx; height: 60rpx; line-height: 60rpx; border-radius: 30rpx; margin-top: 20rpx;}
.logout-btn { background-color: #333; color: #ff4d4f; font-size: 24rpx; height: 50rpx; line-height: 50rpx; border-radius: 8rpx; margin-top: 20rpx;}

.app-btn { background-color: #00d26a; color: #fff; font-size: 26rpx; font-weight: bold; padding: 12rpx 30rpx; border-radius: 8rpx; cursor: pointer;}
</style>