<template>
  <view class="forgot-container">
    <view class="header">
      <text class="title">重置密码</text>
      <text class="subtitle">请输入您的注册账号进行安全验证</text>
    </view>
    <view class="form-box">
      <view class="input-group">
        <input class="input-item" type="text" v-model="form.account" placeholder="请输入用户名/邮箱" placeholder-class="ph-color"/>
      </view>
      <view class="input-group code-group">
        <input class="input-item" type="number" v-model="form.code" placeholder="验证码" placeholder-class="ph-color"/>
        <text class="code-btn" :class="{ disabled: countdown > 0 }" @click="sendCode">
          {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
        </text>
      </view>
      <view class="input-group">
        <input class="input-item" type="password" v-model="form.newPassword" placeholder="请输入新密码" placeholder-class="ph-color"/>
      </view>
      <view class="input-group">
        <input class="input-item" type="password" v-model="form.confirmPassword" placeholder="请确认新密码" placeholder-class="ph-color"/>
      </view>
      <button class="submit-btn" @click="handleReset">确 认 重 置</button>
    </view>
  </view>
</template>
<script setup>
import { ref, reactive } from 'vue';
// import request from '@/utils/request.js'; // 待后端提供对应接口后启用
const form = reactive({
  account: '',
  code: '',
  newPassword: '',
  confirmPassword: ''
});
const countdown = ref(0);
let timer = null;
const sendCode = () => {
  if (!form.account) return uni.showToast({ title: '请输入账号', icon: 'none' });
  if (countdown.value > 0) return;
  // 模拟发送验证码接口
  uni.showToast({ title: '验证码已发送', icon: 'success' });
  countdown.value = 60;
  timer = setInterval(() => {
    countdown.value--;
    if (countdown.value <= 0) {
      clearInterval(timer);
    }
  }, 1000);
};
const handleReset = async () => {
  if (!form.account || !form.code || !form.newPassword) {
    return uni.showToast({ title: '请完整填写表单', icon: 'none' });
  }
  if (form.newPassword !== form.confirmPassword) {
    return uni.showToast({ title: '两次密码输入不一致', icon: 'none' });
  }

  uni.showLoading({ title: '处理中...' });
  setTimeout(() => {
    uni.hideLoading();
    uni.showToast({ title: '密码重置成功', icon: 'success' });
    setTimeout(() => {
      uni.navigateBack();
    }, 1500);
  }, 1000);
  
  /* 预留后端接口对接
  try {
    await request({
      url: '/app/reset-password',
      method: 'POST',
      data: form
    });
    // 成功后返回
  } catch(e) {}
  */
};
</script>

<style scoped>
.forgot-container {
  min-height: 100vh;
  background-color: #111114;
  padding: 60rpx;
  color: #fff;
}
.header {
  margin-bottom: 80rpx;
}
.title {
  font-size: 48rpx;
  font-weight: bold;
  display: block;
  margin-bottom: 20rpx;
}
.subtitle {
  font-size: 28rpx;
  color: #888;
}
.input-group {
  margin-bottom: 40rpx;
  background-color: #1c1c23;
  border-radius: 16rpx;
}
.code-group {
  display: flex;
  align-items: center;
}
.input-item {
  flex: 1;
  height: 100rpx;
  padding: 0 30rpx;
  color: #fff;
  font-size: 30rpx;
}
.code-btn {
  padding: 0 30rpx;
  color: #ff4d4f;
  font-size: 28rpx;
  border-left: 1px solid #333;
}
.code-btn.disabled {
  color: #666;
}
.ph-color {
  color: #555;
}
.submit-btn {
  background: linear-gradient(90deg, #ff4d4f, #ff7a45);
  color: #fff;
  border-radius: 50rpx;
  font-size: 32rpx;
  height: 90rpx;
  line-height: 90rpx;
  margin-top: 60rpx;
}
.submit-btn::after {
  border: none;
}
</style>