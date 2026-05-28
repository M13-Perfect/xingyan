<template>
  <view class="category-container">
    <global-nav />

    <view class="filter-panel">
      <view class="filter-row">
        <text class="filter-label">类型:</text>
        <view class="filter-items">
          <text class="f-item" :class="{ active: query.type === item }" 
                v-for="(item, index) in filters.types" :key="index"
                @click="updateFilter('type', item)">{{ item }}</text>
        </view>
      </view>

      <view class="filter-row">
        <text class="filter-label">地区:</text>
        <view class="filter-items">
          <text class="f-item" :class="{ active: query.region === item }" 
                v-for="(item, index) in filters.regions" :key="index"
                @click="updateFilter('region', item)">{{ item }}</text>
        </view>
      </view>

      <view class="filter-row">
        <text class="filter-label">年份:</text>
        <view class="filter-items">
          <text class="f-item" :class="{ active: query.year === item }" 
                v-for="(item, index) in filters.years" :key="index"
                @click="updateFilter('year', item)">{{ item }}</text>
        </view>
      </view>

      <view class="filter-row" style="margin-bottom: 0;">
        <text class="filter-label">排序:</text>
        <view class="filter-items">
          <text class="f-item" :class="{ active: query.sort === '综合' }" @click="updateFilter('sort', '综合')">综合</text>
          <text class="f-item" :class="{ active: query.sort === '最新' }" @click="updateFilter('sort', '最新')">最新</text>
          <text class="f-item" :class="{ active: query.sort === '最热' }" @click="updateFilter('sort', '最热')">最热</text>
          <text class="f-item" :class="{ active: query.sort === '评分' }" @click="updateFilter('sort', '评分')">评分</text>
        </view>
      </view>
    </view>

    <view class="video-grid">
      <view class="video-card" v-for="video in videoList" :key="video.id" @click="goDetail(video.id)">
        <view class="poster-wrap">
          <image class="poster" :src="video.posterUrl || '/static/default-poster.png'" mode="aspectFill"></image>
          <view class="tag-status">{{ video.updateStatus || '已完结' }}</view>
          <view class="score-tag" v-if="video.score">豆瓣 {{ video.score }}</view>
        </view>
        <view class="video-info">
          <text class="video-title">{{ video.title }}</text>
          <text class="video-sub">{{ video.subCategory || video.region }}</text>
        </view>
      </view>
    </view>

    <view class="loading-status">
      <text v-if="loading">努力加载中...</text>
      <text v-else-if="noMore && videoList.length > 0">已经到底啦</text>
      <text v-else-if="videoList.length === 0">暂无相关视频</text>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, watch } from 'vue';
import { onLoad, onReachBottom } from '@dcloudio/uni-app';
import request from '@/utils/request.js';

// 定义筛选维度的静态数据
const filters = reactive({
  types: ['全部', '剧情', '喜剧', '动作', '科幻', '爱情', '悬疑', '动画', '惊悚', '武侠'],
  regions: ['全部', '大陆', '日韩', '欧美', '港台', '泰国', '印度', '其他'],
  years: ['全部', '2026', '2025', '2024', '2023', '2022', '2021', '10年代', '更早']
});

// 当前选中的查询参数
const query = reactive({
  type: '全部',
  region: '全部',
  year: '全部',
  sort: '最热',
  page: 1,
  size: 16
});

const videoList = ref([]);
const loading = ref(false);
const noMore = ref(false);

// 监听查询参数的变化，任何筛选条件改变，都重置列表并重新请求
watch(() => [query.type, query.region, query.year, query.sort], () => {
  loadData(true);
});

onLoad((options) => {
  // 接收从首页点过来的大类 ID 或 类型名，初始化筛选面板
  if (options.type) query.type = options.type;
  loadData(true);
});

onReachBottom(() => {
  loadData();
});

// 更新筛选条件的方法
const updateFilter = (key, value) => {
  query[key] = value;
};

// 跳转到详情页
const goDetail = (id) => {
  uni.navigateTo({ url: `/pages/video/detail?id=${id}` });
};

// 核心修改点：对接后端真实接口，去掉假数据
const loadData = async (reset = false) => {
  if (loading.value || (noMore.value && !reset)) return;
  
  if (reset) {
    query.page = 1;
    noMore.value = false;
    videoList.value = [];
  }
  
  loading.value = true;
  
  try {
    // 真实接口调用：将“全部”转换为空字符串发给后端
    const res = await request({
      url: '/app/video/list',
      data: {
        type: query.type === '全部' ? '' : query.type,
        region: query.region === '全部' ? '' : query.region,
        year: query.year === '全部' ? '' : query.year,
        sort: query.sort,
        page: query.page,
        size: query.size
      }
    });
    
    // MyBatis-Plus 返回的 IPage 数据结构，列表内容在 records 数组中
    const records = res.records || [];
    
    // 如果返回的数据条数小于请求的 size，说明已经没有下一页了
    if (records.length < query.size) {
      noMore.value = true;
    }
    
    // 拼接数据
    videoList.value = reset ? records : [...videoList.value, ...records];
    query.page++;

  } catch (error) {
    console.error('加载检索视频失败', error);
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.category-container {
  min-height: 100vh;
  background-color: #111114;
  padding-top: 100rpx; 
  color: #fff;
}

/* 筛选面板样式 */
.filter-panel {
  background-color: #1a1a20;
  padding: 30rpx 40rpx;
  margin-bottom: 30rpx;
}

.filter-row {
  display: flex;
  align-items: flex-start;
  margin-bottom: 30rpx;
}

.filter-label {
  color: #888;
  font-size: 28rpx;
  width: 100rpx; 
  margin-top: 6rpx;
  font-weight: bold;
}

.filter-items {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx 20rpx;
}

.f-item {
  padding: 6rpx 24rpx;
  background-color: #2a2a35;
  color: #ccc;
  border-radius: 8rpx;
  font-size: 26rpx;
  cursor: pointer;
  transition: all 0.2s;
}

.f-item:hover {
  color: #00d26a;
  background-color: rgba(0, 210, 106, 0.1);
}

.f-item.active {
  background-color: #00d26a;
  color: #fff;
  font-weight: bold;
}

/* 视频网格 (复用全局风格) */
.video-grid {
  padding: 0 30rpx;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 30rpx;
}
@media (min-width: 768px) { 
  .video-grid { grid-template-columns: repeat(6, 1fr); } 
}

.video-card {
  background-color: #1a1a20;
  border-radius: 12rpx;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s;
}
.video-card:hover {
  transform: translateY(-8rpx);
}
.poster-wrap {
  position: relative;
  width: 100%;
  aspect-ratio: 2 / 3;
}
.poster { width: 100%; height: 100%; }
.tag-status {
  position: absolute; right: 10rpx; top: 10rpx;
  background: #00d26a; color: #fff;
  font-size: 20rpx; padding: 4rpx 10rpx; border-radius: 6rpx; font-weight: bold;
}
.score-tag {
  position: absolute; left: 10rpx; bottom: 10rpx;
  background: rgba(0,0,0,0.7); color: #f39c12;
  font-size: 22rpx; padding: 4rpx 10rpx; border-radius: 6rpx;
}
.video-info { padding: 20rpx; }
.video-title {
  font-size: 28rpx; color: #e5e5e5; display: block;
  margin-bottom: 8rpx; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.video-sub { font-size: 24rpx; color: #666; }

/* 底部状态 */
.loading-status {
  text-align: center;
  font-size: 24rpx;
  color: #666;
  padding: 40rpx 0;
}
</style>