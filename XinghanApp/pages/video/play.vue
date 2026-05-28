<template>
  <view class="play-container">
    <global-nav /> 

    <view class="video-wrapper">
      <video 
        id="myVideo" 
        class="video-player" 
        :src="playUrl" 
        :title="videoTitle"
        autoplay 
        controls
      ></video>
    </view>

    <view class="control-panel">
      <text class="playing-title">{{ videoTitle }} - 第 {{ currentEp }} 集</text>
      
      <view class="section">
        <text class="section-title">选择播放源</text>
        <scroll-view scroll-x class="source-scroll" :show-scrollbar="false">
          <view class="source-list">
            <view class="source-item" 
                  :class="{ active: currentSource == index }" 
                  v-for="(source, index) in sources" :key="index" 
                  @click="changeSource(index)">
              {{ source.name }}
            </view>
          </view>
        </scroll-view>
      </view>

      <view class="section">
        <text class="section-title">选集</text>
        <view class="episode-grid">
          <view class="ep-item" 
                :class="{ active: currentEp == ep }" 
                v-for="ep in 40" :key="ep" 
                @click="changeEpisode(ep)">
            {{ ep < 10 ? '0' + ep : ep }}
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import GlobalNav from '@/components/global-nav/global-nav.vue';

const videoId = ref('');
const videoTitle = ref('星瀚热播剧');
const playUrl = ref('');
const currentEp = ref(1);
const currentSource = ref(0);

// 后台配置的播放源（假数据，后续对接后台接口）
const sources = ref([
  { name: 'MT源 (极速)', url: 'https://media.w3.org/2010/05/sintel/trailer.mp4' },
  { name: 'BD源 (高清)', url: 'https://vjs.zencdn.net/v/oceans.mp4' },
  { name: '直连备用源', url: 'https://media.w3.org/2010/05/sintel/trailer.mp4' }
]);

onLoad((options) => {
  videoId.value = options.id || '1';
  currentEp.value = parseInt(options.ep) || 1;
  currentSource.value = parseInt(options.source) || 0;
  
  // 初始化加载视频
  loadVideoResource();
});

// 核心逻辑：加载视频 URL
const loadVideoResource = () => {
  // 真实项目中这里会发请求：request(`/app/video/getUrl?id=${videoId.value}&ep=${currentEp.value}&source=${currentSource.value}`)
  // 现在我们根据当前选择的源，赋予不同的测试视频链接
  playUrl.value = sources.value[currentSource.value].url;
  uni.showToast({ title: `已切换至第 ${currentEp.value} 集`, icon: 'none' });
};

const changeSource = (index) => {
  if (currentSource.value === index) return;
  currentSource.value = index;
  loadVideoResource();
};

const changeEpisode = (ep) => {
  if (currentEp.value === ep) return;
  currentEp.value = ep;
  loadVideoResource();
};
</script>

<style scoped>
.play-container { min-height: 100vh; background-color: #111114; padding-top: 100rpx; }
.video-wrapper { width: 100%; height: 450rpx; background-color: #000; box-shadow: 0 10rpx 30rpx rgba(0,0,0,0.5); }
.video-player { width: 100%; height: 100%; }

.control-panel { padding: 40rpx; color: #fff; }
.playing-title { font-size: 40rpx; font-weight: bold; margin-bottom: 40rpx; display: block; color: #00d26a; }

.section { margin-bottom: 50rpx; }
.section-title { font-size: 30rpx; font-weight: bold; margin-bottom: 20rpx; display: block; color: #ccc; }

.source-scroll { white-space: nowrap; }
.source-list { display: inline-flex; }
.source-item { background-color: #1c1c23; color: #888; padding: 12rpx 30rpx; border-radius: 8rpx; margin-right: 20rpx; font-size: 26rpx; border: 1px solid transparent; transition: all 0.2s; }
.source-item.active { background-color: rgba(0, 210, 106, 0.1); color: #00d26a; border-color: #00d26a; font-weight: bold; }

.episode-grid { display: grid; grid-template-columns: repeat(8, 1fr); gap: 16rpx; }
.ep-item { background-color: #1c1c23; color: #bbb; text-align: center; padding: 20rpx 0; font-size: 26rpx; border-radius: 8rpx; cursor: pointer; transition: all 0.2s; }
.ep-item.active { background-color: #00d26a; color: #fff; font-weight: bold; }
</style>