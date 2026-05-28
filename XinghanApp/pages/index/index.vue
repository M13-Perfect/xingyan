<template>
  <view class="container">
    <global-nav /> <swiper class="banner-swiper" circular autoplay indicator-dots indicator-active-color="#00d26a">
      <swiper-item v-for="(item, index) in banners" :key="index">
        <image class="banner-img" :src="item.pic" mode="aspectFill"></image>
        <view class="banner-title-mask"><text class="banner-title">{{ item.title }}</text></view>
      </swiper-item>
    </swiper>
    <view class="category-block" v-for="section in homeData" :key="section.categoryId">
      <view class="section-header">
        <text class="section-title">{{ section.categoryName }}</text>
        <view class="more-btn" @click="goCategoryFilter(section.categoryId)">
          <text>查看更多</text> <text class="arrow">></text>
        </view>
      </view>
      <view class="video-grid">
        <view class="video-card" v-for="video in section.videos" :key="video.id" @click="goDetail(video.id)">
          <view class="poster-wrap">
            <image class="poster" :src="video.posterUrl || '/static/default-poster.png'" mode="aspectFill"></image>
            <view class="tag-status">{{ video.updateStatus || '已完结' }}</view>
            <view class="score-tag" v-if="video.score">豆瓣 {{ video.score }}分</view>
          </view>
          <view class="video-info">
            <text class="video-title">{{ video.title }}</text>
            <text class="video-sub">{{ video.subCategory || '精彩推荐' }}</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>
<script setup>
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import request from '@/utils/request.js';

// 模拟轮播数据
const banners = ref([
  { title: '阿凡达：水之道', pic: 'https://images.unsplash.com/photo-1626814026160-2237a95fc5a0?auto=format&fit=crop&w=800&q=80' }
]);
const homeData = ref([]);

onLoad(() => {
  loadHomeData();
});

const loadHomeData = async () => {
  homeData.value = [
    {
      categoryId: 1,
      categoryName: '近期热门电影',
      videos: [
        { id: 1, title: '逐玉', posterUrl: '', updateStatus: '全40集', score: '7.9', subCategory: '古装 / 爱情' },
        { id: 2, title: '流浪地球2', posterUrl: '', updateStatus: 'HD', score: '8.3', subCategory: '科幻 / 灾难' }
      ]
    },
    {
      categoryId: 2,
      categoryName: '热播电视剧',
      videos: [] // 数据同上
    }
  ];
};
const goDetail = (id) => {
  uni.navigateTo({ url: `/pages/video/detail?id=${id}` });
};
const goCategoryFilter = (categoryId) => {
  uni.navigateTo({ url: `/pages/video/category?id=${categoryId}` });
};
</script>

<style scoped>
.container { min-height: 100vh; background-color: #111114; color: #fff; padding-top: 100rpx; padding-bottom: 40rpx;}
.banner-swiper { width: 100%; height: 450rpx; position: relative;}
.banner-img { width: 100%; height: 100%; }
.banner-title-mask { position: absolute; bottom: 0; left: 0; width: 100%; padding: 40rpx 20rpx 20rpx; background: linear-gradient(to top, rgba(17,17,20,1), transparent); }
.banner-title { font-size: 36rpx; font-weight: bold; }

.category-block { padding: 40rpx 30rpx 0; }
.section-header { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 30rpx; }
.section-title { font-size: 40rpx; font-weight: bold; color: #fff; }
.more-btn { font-size: 26rpx; color: #888; display: flex; align-items: center; cursor: pointer; transition: color 0.3s;}
.more-btn:hover { color: #00d26a; }

.video-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 30rpx; }
@media (min-width: 768px) { .video-grid { grid-template-columns: repeat(4, 1fr); } } /* PC端适配4列 */

.video-card { background-color: #1a1a20; border-radius: 12rpx; overflow: hidden; cursor: pointer; transition: transform 0.2s;}
.video-card:hover { transform: translateY(-10rpx); }
.poster-wrap { position: relative; width: 100%; aspect-ratio: 2 / 3; }
.poster { width: 100%; height: 100%; }
.tag-status { position: absolute; right: 10rpx; top: 10rpx; background: #00d26a; color: #fff; font-size: 20rpx; padding: 4rpx 10rpx; border-radius: 6rpx; font-weight: bold;}
.score-tag { position: absolute; left: 10rpx; bottom: 10rpx; background: rgba(0,0,0,0.7); color: #f39c12; font-size: 22rpx; padding: 4rpx 10rpx; border-radius: 6rpx; }
.video-info { padding: 20rpx; }
.video-title { font-size: 30rpx; color: #e5e5e5; display: block; margin-bottom: 8rpx; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;}
.video-sub { font-size: 24rpx; color: #666; }
</style>