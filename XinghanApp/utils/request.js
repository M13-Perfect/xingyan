// utils/request.js
const BASE_URL = 'http://localhost:8080'; // 记得依然替换成你的实际IP

export const request = (options) => {
  return new Promise((resolve, reject) => {
    // 获取本地 token
    const token = uni.getStorageSync('token');
    const header = options.header || {};
    
    if (token) {
      header['Authorization'] = `Bearer ${token}`;
    }

    uni.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data || {},
      header: header,
      success: (res) => {
        const data = res.data;
        // HTTP 状态码 200 且业务 code 也是 200
        if (res.statusCode === 200 && data.code === 200) {
          resolve(data.data);
        } else if (data.code === 401) {
          // 加上这一句：拦截器先关掉可能存在的 loading
          uni.hideLoading(); 
          uni.removeStorageSync('token');
          uni.showToast({ title: '登录已过期，请重新登录', icon: 'none' });
          setTimeout(() => {
            uni.reLaunch({ url: '/pages/login/login' });
          }, 1500);
          reject(data.msg || '未授权');
        } else {
          // 加上这一句：拦截器先关掉可能存在的 loading
          uni.hideLoading();
          uni.showToast({ title: data.msg || '请求失败', icon: 'none' });
          reject(data.msg || 'Error');
        }
      },
      fail: (err) => {
        // 加上这一句：网络不通时也先关 loading
        uni.hideLoading();
        uni.showToast({ title: '网络异常，请稍后再试', icon: 'none' });
        reject(err);
      }
    });
  });
};

export default request;