<template>
  <view class="detail-container" v-if="video">
    <global-nav :isTransparent="true" />

    <view class="backdrop-wrapper">
      <image class="backdrop-img" :src="video.posterUrl || '/static/default-poster.png'" mode="aspectFill"></image>
      <view class="backdrop-mask"></view>
    </view>

    <view class="info-section">
      <text class="title">{{ video.title || '加载中...' }}</text>
      <view class="tags-row">
        <text class="tag year">{{ video.publishYear || '2026' }}</text>
        <text class="tag type">电视剧</text>
        <text class="score">⭐ 7.7</text>
        <text class="tag-text">{{ video.region || '中国大陆' }}</text>
        <text class="tag-text">全40集</text>
      </view>

      <view class="meta-info">
        <text class="meta-line">导演: 曾庆杰</text>
        <text class="meta-line truncate-2">演员: 田曦薇, 张凌赫, 任豪, 孔雪儿</text>
        <view class="desc-box">
          <text class="meta-line truncate-2">描述: 该剧改编自热门小说，讲述了跌宕起伏的江湖爱情故事...</text>
        </view>
      </view>

      <view class="action-row">
        <view class="play-btn" @click="handlePlay">
          <text class="icon">▶</text> <text>立即播放</text>
        </view>
        <view class="btn-normal">
          <text class="icon">☆</text> <text>收藏</text>
        </view>
      </view>
    </view>

    <view class="player-section">
      <scroll-view scroll-x class="source-scroll" :show-scrollbar="false">
        <view class="source-list">
          <view class="source-item" :class="{ active: currentSource === index }" v-for="(source, index) in sources" :key="index" @click="currentSource = index">
            <text class="source-name">@ {{ source.name }}</text>
          </view>
        </view>
      </scroll-view>

      <<view class="episode-grid">
        <view class="ep-item" :class="{ active: currentEp === ep }" v-for="ep in (video.episodeCount || 1)" :key="ep" @click="selectEpisode(ep)">
          {{ ep < 10 ? '0' + ep : ep }}
        </view>
      </view>
    </view>
  </view>
  <view class="recommend-section">
        <view class="section-title">猜你喜欢</view>
        <view class="recommend-grid">
          <view class="rec-card" v-for="rec in recommends" :key="rec.id" @click="goDetail(rec.id)">
            <image class="rec-poster" :src="rec.posterUrl || '/static/default-poster.png'" mode="aspectFill"></image>
            <text class="rec-title">{{ rec.title }}</text>
          </view>
        </view>
      </view>
</template>

<script setup>
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import GlobalNav from '@/components/global-nav/global-nav.vue';
import request from '@/utils/request.js';

const video = ref({ id: '1', title: '测试影片' }); // 默认占位
const sources = ref([{ name: 'MT源' }, { name: 'BD源' }, { name: '高清直连' }]);
const currentSource = ref(0);
const currentEp = ref(1);

onLoad((options) => {
  if (options.id) {
    fetchVideoDetail(options.id);
  }
});

const fetchVideoDetail = async (id) => {
  try {
    const res = await request({ url: `/app/video/detail/${id}`, method: 'GET' });
    if(res) video.value = res;
  } catch (e) { console.error('获取详情失败', e); }
};

// 核心修复：点击播放按钮或选集时的跳转逻辑
const handlePlay = () => {
  const token = uni.getStorageSync('token');
  if (!token) {
    uni.showToast({ title: '请先登录后观看', icon: 'none' });
    setTimeout(() => { uni.navigateTo({ url: '/pages/login/login' }); }, 1000);
    return;
  }
  
  // 确保参数存在，跳转到播放页
  const vid = video.value.id || 1;
  uni.navigateTo({ 
    url: `/pages/video/play?id=${vid}&ep=${currentEp.value}&source=${currentSource.value}` 
  });
};

const selectEpisode = (ep) => {
  currentEp.value = ep;
  handlePlay(); // 点集数直接触发跳转播放
};
</script>

<style scoped>
.detail-container { min-height: 100vh; background-color: #111114; color: #fff; padding-bottom: 60rpx; position: relative; overflow-x: hidden; }
.backdrop-wrapper { position: absolute; top: 0; left: 0; width: 100vw; height: 800rpx; z-index: 0; }
.backdrop-img { width: 100%; height: 100%; opacity: 0.5; display: block; }
.backdrop-mask { position: absolute; top: 0; left: 0; width: 100%; height: 100%; background: linear-gradient(to right, #111114 10%, rgba(17,17,20,0.4) 100%), linear-gradient(to top, #111114 0%, rgba(17,17,20,0) 100%); }
.info-section { position: relative; z-index: 10; padding: 80rpx 40rpx 40rpx; margin-top: 100rpx; }
.title { font-size: 64rpx; font-weight: bold; margin-bottom: 30rpx; display: block; }
.tags-row { display: flex; align-items: center; flex-wrap: wrap; margin-bottom: 30rpx; font-size: 24rpx; }
.tag { padding: 4rpx 12rpx; border-radius: 6rpx; margin-right: 16rpx; font-weight: bold; }
.tag.year { background-color: #f39c12; color: #fff; }
.tag.type { background-color: #00d26a; color: #fff; }
.score { color: #00d26a; font-weight: bold; margin-right: 20rpx; font-size: 32rpx;}
.tag-text { color: #999; margin-right: 20rpx; }
.meta-info { margin-bottom: 40rpx; }
.meta-line { color: #bbb; font-size: 26rpx; line-height: 1.8; display: block; }
.truncate-2 { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; }
.action-row { display: flex; align-items: center; }
.play-btn { background-color: #00d26a; color: #fff; font-size: 30rpx; font-weight: bold; padding: 18rpx 40rpx; border-radius: 12rpx; display: flex; align-items: center; margin-right: 30rpx; }
.play-btn .icon { margin-right: 10rpx; }
.btn-normal { background-color: rgba(255,255,255,0.1); color: #e5e5e5; font-size: 28rpx; padding: 18rpx 30rpx; border-radius: 12rpx; display: flex; align-items: center; margin-right: 20rpx; }
.player-section { position: relative; z-index: 10; padding: 0 40rpx; margin-top: 20rpx; }
.source-scroll { white-space: nowrap; margin-bottom: 30rpx; }
.source-list { display: inline-flex; }
.source-item { margin-right: 30rpx; color: #888; font-size: 26rpx; padding-bottom: 10rpx; }
.source-item.active { color: #00d26a; font-weight: bold; }
.episode-grid { display: grid; grid-template-columns: repeat(8, 1fr); gap: 16rpx; }
.ep-item { background-color: rgba(255,255,255,0.05); color: #bbb; text-align: center; padding: 16rpx 0; font-size: 26rpx; border-radius: 8rpx; cursor: pointer; }
.ep-item.active { background-color: #00d26a; color: #fff; font-weight: bold; }
</style>