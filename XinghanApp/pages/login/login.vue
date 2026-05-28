<template>
  <view class="login-container">
    <view class="nav-header">
      <view class="close-btn" @click="handleClose">
        <text class="close-icon">×</text>
      </view>
    </view>
    <view class="logo-box">
      <image class="logo" src="/static/logo.png" mode="aspectFit"></image>
      <text class="app-name">星瀚影视</text>
    </view>
    <view class="form-box">
      <view class="tabs">
        <text class="tab" :class="{ active: isLogin }" @click="isLogin = true">登录</text>
        <text class="tab" :class="{ active: !isLogin }" @click="isLogin = false">注册</text>
      </view>
      <view class="input-group">
        <input class="input-item" type="text" v-model="formData.username" placeholder="请输入用户名/手机号" placeholder-class="ph-color"/>
      </view>
      <view class="input-group">
        <input class="input-item" type="password" v-model="formData.password" placeholder="请输入密码" placeholder-class="ph-color"/>
      </view>
      <view class="input-group" v-if="!isLogin">
        <input class="input-item" type="text" v-model="formData.inviteCode" placeholder="邀请码 (选填)" placeholder-class="ph-color"/>
      </view>
      <view class="action-row" v-if="isLogin">
        <text class="forgot-pwd" @click="goForgot">忘记密码？</text>
      </view>
      <button class="submit-btn" @click="handleSubmit">
        {{ isLogin ? '立 即 登 录' : '注 册 并 登 录' }}
      </button>
    </view>
  </view>
</template>
<script setup>
import { ref, reactive } from 'vue';
import request from '@/utils/request.js';
const isLogin = ref(true);
const formData = reactive({
  username: '',
  password: '',
  inviteCode: ''
});
// 关闭页面/游客模式
const handleClose = () => {
  const pages = getCurrentPages();
  if (pages.length > 1) {
    uni.navigateBack(); 
  } else {
    uni.reLaunch({ url: '/pages/index/index' });
  }
};
const handleSubmit = async () => {
  if (!formData.username || !formData.password) {
    return uni.showToast({ title: '账号和密码不能为空', icon: 'none' });
  }
  // 防止连点
  uni.showLoading({ title: '请稍候...', mask: true });
  try {
    if (isLogin.value) {
      // 执行登录
      const res = await request({
        url: '/app/login',
        method: 'POST',
        data: { username: formData.username, password: formData.password }
      });
      uni.hideLoading();
      // 存储TOKEN
      uni.setStorageSync('token', res.token);
      uni.setStorageSync('userInfo', res);
      
      uni.showToast({ title: '登录成功', icon: 'success' });
      setTimeout(() => {
        uni.reLaunch({ url: '/pages/index/index' });
      }, 1000);
    } else {
      // 执行注册
      await request({
        url: '/app/register',
        method: 'POST',
        data: formData
      });
      uni.hideLoading();
      uni.showToast({ title: '注册成功，请登录', icon: 'success' });
      isLogin.value = true; 
    }
  } catch (err) {
  }
};

const goForgot = () => {
  uni.navigateTo({ url: '/pages/login/forgot_password' });
};
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  background-color: #111114;
  padding: 0 60rpx;
  box-sizing: border-box;
  position: relative;
}
.nav-header {
  height: 88rpx;
  padding-top: var(--status-bar-height);
  display: flex;
  align-items: center;
}
.close-btn {
  width: 60rpx;
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: flex-start;
}
.close-icon {
  font-size: 56rpx;
  color: #999;
  font-weight: 300;
}
.logo-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: 40rpx; 
  margin-bottom: 80rpx;
}
.logo {
  width: 160rpx;
  height: 160rpx;
  border-radius: 32rpx;
}
.app-name {
  color: #fff;
  font-size: 40rpx;
  font-weight: bold;
  margin-top: 20rpx;
}
.tabs {
  display: flex;
  justify-content: center;
  margin-bottom: 60rpx;
}
.tab {
  font-size: 32rpx;
  color: #666;
  margin: 0 40rpx;
  padding-bottom: 10rpx;
  transition: all 0.3s;
}
.tab.active {
  color: #ff4d4f;
  font-weight: bold;
  border-bottom: 4rpx solid #ff4d4f;
}
.input-group {
  margin-bottom: 40rpx;
  background-color: #1c1c23;
  border-radius: 16rpx;
}
.input-item {
  height: 100rpx;
  padding: 0 30rpx;
  color: #fff;
  font-size: 30rpx;
}
.ph-color {
  color: #555;
}
.action-row {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 40rpx;
}
.forgot-pwd {
  color: #999;
  font-size: 26rpx;
}
.submit-btn {
  background: linear-gradient(90deg, #ff4d4f, #ff7a45);
  color: #fff;
  border-radius: 50rpx;
  font-size: 32rpx;
  height: 90rpx;
  line-height: 90rpx;
  margin-top: 20rpx;
}
.submit-btn::after {
  border: none;
}
</style>