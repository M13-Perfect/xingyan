<template>
  <div class="app-container">
    <div v-if="!isLoggedIn" class="login-wrapper">
      <div class="login-box">
        <h1 class="mega-title">序界回访管理系统</h1>
        <h3 class="sub-title">序界数据终端</h3>
        <div class="input-group">
          <input v-model="loginForm.username" placeholder="请输入您的账号" class="heavy-input text-black">
          <input v-model="loginForm.password" type="password" placeholder="请输入您的密码" class="heavy-input text-black" @keyup.enter="handleLogin">
        </div>
        <div class="login-actions">
          <button @click="handleLogin" class="btn-login-action">安全登录</button>
          <button type="button" class="btn-forgot" @click="handleForgotPassword">忘记密码</button>
        </div>
      </div>
    </div>

    <div v-else class="dashboard">

      <header class="top-taskbar">
        <div class="taskbar-left">
          <div class="menu-trigger-wrap">
            <button class="hamburger-btn" @click.stop="toggleMenuPanel" title="展开/收起菜单">☰</button>
            <div class="menu-popover" v-if="isMenuOpen" @click.stop>
              <button :class="{active: view === 'work'}" @click="switchView('work')">📝 工作台</button>
              <button v-if="user.role === 'admin'" :class="{active: view === 'users'}" @click="switchView('users')">👥 员工管理</button>
            </div>
          </div>
          <span class="brand-text">序界客户回访系统</span>
        </div>
        <div class="taskbar-right">
          <span class="user-name">{{ user.username }}</span>
          <span class="role-tag hide-on-mobile">{{ user.role === 'admin' ? '管理员' : '专员' }}</span>
          <button class="btn-notice-icon" @click="toggleNoticeCenter" title="通知中心">
            <span>🔔</span>
            <span v-if="noticeUnreadCount > 0" class="notice-dot">{{ noticeUnreadCount > 99 ? '99+' : noticeUnreadCount }}</span>
          </button>
          <button class="btn-logout-icon" @click="logout" title="退出登录">退出</button>
        </div>
      </header>

      <main class="main-body">

        <div class="notify-bar" v-if="tickerMessages.length > 0">
          <span class="notify-label">通知</span>
          <div class="notify-marquee">
            <div class="notify-track">
              <span v-for="(message, idx) in tickerMessages" :key="`ticker-a-${idx}`" class="notify-item">{{ message }}</span>
              <span v-for="(message, idx) in tickerMessages" :key="`ticker-b-${idx}`" class="notify-item">{{ message }}</span>
            </div>
          </div>
        </div>

        <div v-if="view === 'work'" class="view-content">

          <header class="page-header">
            <h2 class="white-text">数据流转中心</h2>
            <div class="header-tools">
              <button @click="refreshAll" class="btn-refresh">🔄 刷新数据</button>
              <div class="tab-group-container">
                <div class="tab-group">
                  <button :class="{active: listStatus==='未处理'}" @click="listStatus='未处理';doSearch()">待处理</button>
                  <button :class="{active: listStatus==='已处理'}" @click="listStatus='已处理';doSearch()">已处理</button>
                  <button v-if="user.role === 'admin'" :class="{active: listStatus==='全部'}" @click="listStatus='全部';doSearch()">全量库</button>
                </div>
              </div>
            </div>
          </header>

          <div class="search-filter-bar">
            <div class="search-inputs">
              <input v-model="searchQuery.keyword" placeholder="🔍 搜索称呼 / 电话 / 微信 / 项目..." class="search-input text-black" @keyup.enter="doSearch">
              <input v-model="searchQuery.city" placeholder="🏙️ 按城市筛选..." class="search-input text-black" @keyup.enter="doSearch">
            </div>
            <div class="search-actions">
              <button @click="doSearch" class="btn-search">查询</button>
              <button @click="resetSearch" class="btn-reset">重置</button>
            </div>
          </div>

          <div class="form-card">
            <h4 class="form-title"> 录入新回访记录</h4>
            <div class="form-grid-fluid">
              <input v-model="form.name" placeholder="称呼 (必填)" class="text-black">
              <input v-model="form.phone" placeholder="电话 (不可重复)" class="text-black">
              <input v-model="form.wechat" placeholder="微信 (不可重复)" class="text-black">
              <input v-model="form.city" placeholder="城市" class="text-black">
              <input v-model="form.project" placeholder="项目" class="text-black">
              <input v-model="form.budget" placeholder="预算" class="text-black">
              <input v-model="form.remarks" placeholder="需求备注" class="text-black remarks-input span-full-width">
              <button @click="submitAdd" class="btn-add-fluid span-full-width">录入</button>
            </div>
          </div>

          <div class="table-container">
            <table class="modern-table">
              <thead>
              <tr>
                <th>状态</th>
                <th>客户称呼</th>
                <th>联系方式</th>
                <th>城市 / 项目</th>
                <th>快速操作</th>
              </tr>
              </thead>
              <tbody>
              <tr v-for="item in surveys" :key="item.id" @click="openModal(item)" class="clickable-row">
                <td>
                  <span :class="['status-badge', item.status === '已处理' ? 'done' : 'todo']">{{ item.status }}</span>
                  <div v-if="isOverdue(item.nextSurveyDate) && item.status==='未处理'" class="red-tag mt-1">需复访</div>
                </td>
                <td class="bold highlight-text">{{ item.name }}</td>
                <td>
                  <div class="contact-line">📞 <span class="text-black">{{ item.phone || '无' }}</span></div>
                  <div class="contact-line">💬 <span class="text-black">{{ item.wechat || '无' }}</span></div>
                </td>
                <td>
                  <div class="text-black">🏙️ {{ item.city || '未知' }}</div>
                  <div class="text-orange font-bold">📦 {{ item.project || '无' }}</div>
                  <div class="remark-inline" :class="{ empty: !item.remarks }">
                    <span class="remark-inline-label">备注：</span>
                    <span class="remark-inline-text">{{ buildRemarkPreview(item.remarks) }}</span>
                    <div v-if="item.remarks" class="remark-hover-card">{{ item.remarks }}</div>
                  </div>
                </td>
                <td @click.stop>
                  <button v-if="item.status==='未处理'" @click="processTask(item.id)" class="btn-action-small">✅ 完成处理</button>
                  <span v-else class="text-muted">已归档</span>
                </td>
              </tr>
              <tr v-if="surveys.length === 0">
                <td colspan="5" class="empty-state">当前列表暂无数据</td>
              </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div v-if="view === 'users'" class="view-content">
          <header class="page-header"><h2 class="white-text">员工账号中心</h2></header>
          <div class="form-card user-form-bg">
            <h4 class="form-title"> 开通新员工</h4>
            <div class="form-grid-fluid">
              <input v-model="userForm.username" placeholder="设置账号 (英数)" class="text-black">
              <input v-model="userForm.password" type="password" placeholder="设置初始密码" class="text-black">
              <button @click="submitAddUser" class="btn-add-fluid dark">确认开通</button>
            </div>
          </div>
          <div class="table-container">
            <table class="modern-table">
              <thead>
              <tr>
                <th>员工账号</th>
                <th>权限角色</th>
                <th>新密码</th>
                <th>操作</th>
              </tr>
              </thead>
              <tbody>
              <tr v-for="u in usersList" :key="u.id">
                <td class="bold text-black">{{ u.username }}</td>
                <td><span class="role-badge">{{ u.role === 'admin' ? '管理员' : '业务专员' }}</span></td>
                <td>
                  <input
                    v-model="userPasswordDraft[u.id]"
                    class="user-password-input text-black"
                    type="password"
                    placeholder="输入 6 位以上新密码"
                  >
                </td>
                <td class="user-actions">
                  <button @click="updateStaffPassword(u)" class="btn-inline">修改密码</button>
                  <button @click="deleteStaffUser(u)" class="btn-inline-danger">删除员工</button>
                </td>
              </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="pager-fluid" v-if="view === 'work'">
          <button :disabled="page <= 1" @click="page--;fetchData()">上一页</button>
          <span class="pager-txt">第 {{ page }} / {{ totalPages }} 页</span>
          <button :disabled="page >= totalPages" @click="page++;fetchData()">下一页</button>
        </div>
      </main>

      <div class="modal-overlay notice-center-overlay" v-if="isNoticeCenterOpen" @click.self="closeNoticeCenter">
        <div class="notice-center-panel">
          <header class="notice-center-header">
            <h3 class="notice-center-title">通知中心</h3>
            <button class="btn-close" @click="closeNoticeCenter" title="关闭">✖</button>
          </header>

          <div class="notice-center-body">
            <div class="notice-filter-tabs">
              <button :class="{ active: noticeStatus === 'UNREAD' }" @click="switchNoticeStatus('UNREAD')">未读</button>
              <button :class="{ active: noticeStatus === 'READ' }" @click="switchNoticeStatus('READ')">已读</button>
              <button :class="{ active: noticeStatus === 'ALL' }" @click="switchNoticeStatus('ALL')">全部</button>
            </div>

            <div class="notice-batch-tools">
              <label class="notice-check-all">
                <input type="checkbox" :checked="isAllCurrentNoticesSelected" @change="toggleSelectAllNotices">
                <span>全选</span>
              </label>
              <span class="notice-selected-count">已选 {{ selectedNoticeIds.length }} 条</span>
              <button class="notice-action-btn" @click="markSelectedNoticesRead" :disabled="selectedNoticeIds.length === 0">标记已读</button>
              <button class="notice-action-btn danger" @click="deleteSelectedNotices" :disabled="selectedNoticeIds.length === 0">删除</button>
            </div>

            <div class="notice-list" v-if="noticeList.length > 0">
              <div class="notice-row" v-for="item in noticeList" :key="item.id">
                <label class="notice-row-check">
                  <input type="checkbox" :checked="selectedNoticeIds.includes(item.id)" @change="toggleNoticeSelection(item.id)">
                </label>
                <div class="notice-row-content">
                  <div class="notice-row-top">
                    <span :class="['notice-level', item.level ? item.level.toLowerCase() : 'info']">{{ item.level || 'INFO' }}</span>
                    <strong class="notice-row-title">{{ item.title }}</strong>
                    <span class="notice-row-time">{{ item.createdAt }}</span>
                  </div>
                  <p class="notice-row-text">{{ item.content }}</p>
                  <div class="notice-row-actions">
                    <span v-if="item.isRead" class="notice-read-flag">已读</span>
                    <button v-else class="notice-inline-btn" @click="markSingleNoticeRead(item.id)">标记已读</button>
                    <button class="notice-inline-btn danger" @click="deleteSingleNotice(item.id)">删除</button>
                  </div>
                </div>
              </div>
            </div>

            <div class="notice-empty" v-else>当前没有通知</div>

            <div class="pager-fluid notice-pager">
              <button :disabled="noticePage <= 1" @click="noticePage--;fetchNoticeList()">上一页</button>
              <span class="pager-txt">第 {{ noticePage }} / {{ noticeTotalPages }} 页</span>
              <button :disabled="noticePage >= noticeTotalPages" @click="noticePage++;fetchNoticeList()">下一页</button>
            </div>

            <div class="notice-create-box" v-if="user.role === 'admin'">
              <h4 class="notice-create-title">发布通知</h4>
              <div class="notice-create-grid">
                <input v-model="noticeForm.title" placeholder="通知标题（必填）" class="text-black">
                <select v-model="noticeForm.level" class="text-black">
                  <option value="INFO">INFO</option>
                  <option value="WARN">WARN</option>
                  <option value="ALERT">ALERT</option>
                </select>
                <textarea v-model="noticeForm.content" class="text-black" placeholder="通知内容（必填）"></textarea>
                <button class="btn-add-fluid" @click="publishNotice">发布通知</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="modal-overlay" v-if="selectedSurvey" @click.self="closeModal">
      <div class="modal-content">

        <header class="modal-header">
          <h3 class="modal-title">📄 客户档案详情</h3>
          <button class="btn-close" @click="closeModal" title="关闭">✖</button>
        </header>

        <div class="modal-body">
          <div class="modal-grid">
            <div class="m-item full-width remarks-box focus-remarks">
              <div class="remarks-header">
                <span class="lbl block">核心备注 (可直接编辑):</span>
                <button @click="saveRemarks" class="btn-save-remarks">💾 保存修改</button>
              </div>
              <textarea v-model="selectedSurvey.remarks" class="text-area-sim text-black" placeholder="先记录客户核心诉求、顾虑点、推进障碍与下一步动作..."></textarea>
            </div>

            <div class="m-item focus-card">
              <span class="lbl">项目:</span>
              <span class="val focus-val">{{ selectedSurvey.project || '未填写' }}</span>
            </div>
            <div class="m-item focus-card budget-card">
              <span class="lbl">预算:</span>
              <span class="val focus-val">{{ selectedSurvey.budget || '未填写' }}</span>
            </div>

            <div class="m-item"><span class="lbl">称呼:</span> <span class="val highlight-text">{{ selectedSurvey.name }}</span></div>
            <div class="m-item"><span class="lbl">状态:</span>
              <span :class="['status-badge', selectedSurvey.status === '已处理' ? 'done' : 'todo']">{{ selectedSurvey.status }}</span>
            </div>
            <div class="m-item"><span class="lbl">电话:</span> <span class="val text-black">{{ selectedSurvey.phone || '无' }}</span></div>
            <div class="m-item"><span class="lbl">微信:</span> <span class="val text-black">{{ selectedSurvey.wechat || '无' }}</span></div>
            <div class="m-item"><span class="lbl">城市:</span> <span class="val text-black">{{ selectedSurvey.city || '未填写' }}</span></div>

            <div class="m-item full-width reminder-row-modal">
              <span class="lbl text-danger">⏱️ 设置下次提醒:</span>
              <input type="date" :min="todayDate" @change="e => updateDate(selectedSurvey.id, e.target.value)" :value="stripTime(selectedSurvey.nextSurveyDate)" class="date-picker text-black">
            </div>
          </div>

          <div class="admin-panel-fluid mt-3" v-if="user.role === 'admin'">
            <div class="admin-info">👤 录入人: <b class="text-black">{{ selectedSurvey.owner }}</b> &nbsp;|&nbsp; 🕒 {{ selectedSurvey.createTime }}</div>
            <div class="admin-controls-fluid mt-2">
              <div class="share-group">
                <label>可见权限:</label>
                <select v-model="selectedSurvey.visibility" class="share-select text-black">
                  <option value="PRIVATE">私有(仅录入人)</option>
                  <option value="PUBLIC">公开(全体可见)</option>
                  <option value="CUSTOM">指定员工</option>
                </select>
                <input v-if="selectedSurvey.visibility === 'CUSTOM'" v-model="selectedSurvey.sharedUsers" placeholder="账号,逗号分隔" class="share-input text-black">
                <button @click="updateShare(selectedSurvey)" class="btn-save-share">保存权限</button>
              </div>
              <button @click="deleteSingle(selectedSurvey.id)" class="btn-del-modal">删除</button>
            </div>
          </div>
        </div>

        <footer class="modal-footer" v-if="selectedSurvey.status==='未处理'">
          <button @click="processTask(selectedSurvey.id); closeModal()" class="btn-action w-full">✅ 标记为完成处理</button>
        </footer>
      </div>
    </div>

  </div>
</template>

<script setup>
import { computed, ref, reactive, onMounted, onUnmounted } from 'vue'
import axios from 'axios'

const API = '/api'
const isLoggedIn = ref(false)
const user = reactive({ username: '', role: '' })
const loginForm = reactive({ username: '', password: '' })
const view = ref('work')
const textEncoder = new TextEncoder()

const isMobile = ref(false)
const isMenuOpen = ref(false)

const selectedSurvey = ref(null)
const openModal = (item) => { selectedSurvey.value = { ...item } }
const closeModal = () => { selectedSurvey.value = null }

const searchQuery = reactive({ keyword: '', city: '' })

const listStatus = ref('未处理')
const surveys = ref([])
const pendingCount = ref(0)
const form = ref({ name: '', phone: '', wechat: '', city: '', project: '', budget: '', remarks: '', remindDays: 3 })
const page = ref(1); const totalPages = ref(1); const totalCount = ref(0)
const todayDate = new Date().toISOString().split('T')[0]
const usersList = ref([])
const userForm = ref({ username: '', password: '', role: 'staff' })
const userPasswordDraft = reactive({})

const noticeUnreadCount = ref(0)
const tickerMessages = ref([])
const isNoticeCenterOpen = ref(false)
const noticeStatus = ref('UNREAD')
const noticePage = ref(1)
const noticeTotalPages = ref(1)
const noticeList = ref([])
const noticeTickerList = ref([])
const selectedNoticeIds = ref([])
const noticeForm = reactive({ title: '', content: '', level: 'INFO' })
const isAllCurrentNoticesSelected = computed(
  () => noticeList.value.length > 0 && noticeList.value.every(item => selectedNoticeIds.value.includes(item.id))
)

const base64ToArrayBuffer = (base64) => {
  const binary = atob(base64)
  const len = binary.length
  const bytes = new Uint8Array(len)
  for (let i = 0; i < len; i++) bytes[i] = binary.charCodeAt(i)
  return bytes.buffer
}

const arrayBufferToBase64 = (buffer) => {
  const bytes = new Uint8Array(buffer)
  let binary = ''
  const chunkSize = 0x8000
  for (let i = 0; i < bytes.length; i += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunkSize))
  }
  return btoa(binary)
}

const encryptPassword = async (plainText) => {
  if (!window.crypto?.subtle) {
    throw new Error('当前浏览器不支持 WebCrypto，无法加密密码')
  }
  const keyRes = await axios.get(`${API}/security/public-key`)
  const keyBuffer = base64ToArrayBuffer(keyRes.data.publicKey)
  const publicKey = await window.crypto.subtle.importKey(
    'spki',
    keyBuffer,
    { name: 'RSA-OAEP', hash: 'SHA-256' },
    false,
    ['encrypt']
  )
  const encrypted = await window.crypto.subtle.encrypt(
    { name: 'RSA-OAEP' },
    publicKey,
    textEncoder.encode(plainText)
  )
  return arrayBufferToBase64(encrypted)
}

const checkDevice = () => {
  const ua = navigator.userAgent
  const isMobileUA = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(ua)
  const isSmallScreen = window.innerWidth <= 1024
  isMobile.value = isMobileUA || isSmallScreen
}
const toggleMenuPanel = () => { isMenuOpen.value = !isMenuOpen.value }
const switchView = (v) => {
  view.value = v
  page.value = 1
  isMenuOpen.value = false
  if(v === 'users') fetchUsers(); else fetchData()
}

const handleGlobalClick = (event) => {
  if (!isMenuOpen.value) return
  if (event.target?.closest('.menu-trigger-wrap')) return
  isMenuOpen.value = false
}

const getRequestErrorMessage = (error, fallback = '服务器网络异常，请稍后重试') => {
  if (axios.isAxiosError(error)) {
    if (error.response) {
      const status = error.response.status
      if (status >= 500) return `登录失败：后端服务异常（HTTP ${status}）`
      if (status === 401 || status === 403) return '登录失败：账号或密码错误'
      return `登录失败：请求异常（HTTP ${status}）`
    }
    if (error.request) return '登录失败：无法连接服务器，请确认后端服务已启动'
  }
  return fallback
}

const getApiErrorMessage = (error, fallback) => {
  if (axios.isAxiosError(error)) {
    if (typeof error.response?.data === 'string') return error.response.data
    if (error.response?.status) return `${fallback}（HTTP ${error.response.status}）`
    if (error.request) return `${fallback}（无法连接后端）`
  }
  return fallback
}

const handleLogin = async () => {
  if(!loginForm.username) return alert('请输入账号');
  if(!loginForm.password) return alert('请输入密码');
  try {
    const encryptedPassword = await encryptPassword(loginForm.password)
    const res = await axios.post(`${API}/login`, {
      username: loginForm.username,
      password: encryptedPassword
    })
    if(res.data.success) {
      isLoggedIn.value = true; user.username = res.data.username; user.role = res.data.role
      loginForm.password = ''
      view.value = 'work'; listStatus.value = '未处理';
      checkDevice();
      await fetchPendingCount();
      await refreshNoticeData();
      alert(`登录成功！\n目前有 ${pendingCount.value} 条待处理数据。`);
      fetchData()
    } else { alert(res.data.message) }
  } catch (e) { alert(getRequestErrorMessage(e)) }
}

const handleForgotPassword = () => {
  alert('请联系系统管理员进行密码重置。')
}

const fetchPendingCount = async () => {
  const res = await axios.get(`${API}/surveys/pending-count`, { params: { username: user.username, role: user.role } })
  pendingCount.value = res.data
  rebuildTickerMessages()
}

const rebuildTickerMessages = () => {
  const messages = []
  if (pendingCount.value > 0) {
    messages.push(`待处理数据 ${pendingCount.value} 条，请及时跟进`)
  }
  noticeTickerList.value.forEach(item => {
    messages.push(`${item.title}：${item.content}`)
  })
  tickerMessages.value = messages
}

const fetchNoticeUnreadCount = async () => {
  if (!user.username) return
  const res = await axios.get(`${API}/notices/unread-count`, { params: { username: user.username } })
  noticeUnreadCount.value = res.data.count || 0
}

const fetchNoticeList = async () => {
  if (!user.username) return
  const res = await axios.get(`${API}/notices`, {
    params: {
      username: user.username,
      status: noticeStatus.value,
      page: noticePage.value,
      size: 8
    }
  })
  noticeList.value = res.data.data || []
  noticeTotalPages.value = res.data.pages || 1
  selectedNoticeIds.value = selectedNoticeIds.value.filter(id => noticeList.value.some(item => item.id === id))
}

const fetchNoticeTicker = async () => {
  if (!user.username) return
  const res = await axios.get(`${API}/notices`, {
    params: {
      username: user.username,
      status: 'UNREAD',
      page: 1,
      size: 3
    }
  })
  noticeTickerList.value = res.data.data || []
  rebuildTickerMessages()
}

const refreshNoticeData = async () => {
  await fetchNoticeUnreadCount()
  await fetchNoticeTicker()
  if (isNoticeCenterOpen.value) {
    await fetchNoticeList()
  }
}

const toggleNoticeCenter = async () => {
  if (!isNoticeCenterOpen.value) {
    isNoticeCenterOpen.value = true
    noticeStatus.value = 'UNREAD'
    noticePage.value = 1
    selectedNoticeIds.value = []
    await fetchNoticeList()
    return
  }
  isNoticeCenterOpen.value = false
}

const closeNoticeCenter = () => {
  isNoticeCenterOpen.value = false
}

const switchNoticeStatus = async (status) => {
  noticeStatus.value = status
  noticePage.value = 1
  selectedNoticeIds.value = []
  await fetchNoticeList()
}

const toggleNoticeSelection = (id) => {
  if (selectedNoticeIds.value.includes(id)) {
    selectedNoticeIds.value = selectedNoticeIds.value.filter(itemId => itemId !== id)
  } else {
    selectedNoticeIds.value = [...selectedNoticeIds.value, id]
  }
}

const toggleSelectAllNotices = () => {
  if (isAllCurrentNoticesSelected.value) {
    selectedNoticeIds.value = []
    return
  }
  selectedNoticeIds.value = noticeList.value.map(item => item.id)
}

const markSelectedNoticesRead = async () => {
  if (selectedNoticeIds.value.length === 0) return
  await axios.put(`${API}/notices/read`, {
    username: user.username,
    ids: selectedNoticeIds.value
  })
  selectedNoticeIds.value = []
  await refreshNoticeData()
}

const deleteSelectedNotices = async () => {
  if (selectedNoticeIds.value.length === 0) return
  await axios.delete(`${API}/notices`, {
    data: {
      username: user.username,
      ids: selectedNoticeIds.value
    }
  })
  selectedNoticeIds.value = []
  await refreshNoticeData()
}

const markSingleNoticeRead = async (id) => {
  await axios.put(`${API}/notices/read`, {
    username: user.username,
    ids: [id]
  })
  await refreshNoticeData()
}

const deleteSingleNotice = async (id) => {
  await axios.delete(`${API}/notices`, {
    data: {
      username: user.username,
      ids: [id]
    }
  })
  await refreshNoticeData()
}

const publishNotice = async () => {
  if (user.role !== 'admin') return alert('仅管理员可操作')
  if (!noticeForm.title.trim()) return alert('请填写通知标题')
  if (!noticeForm.content.trim()) return alert('请填写通知内容')
  const res = await axios.post(`${API}/notices`, {
    operatorUsername: user.username,
    title: noticeForm.title,
    content: noticeForm.content,
    level: noticeForm.level
  })
  if (!res.data.success) return alert(res.data.message || '通知发布失败')
  noticeForm.title = ''
  noticeForm.content = ''
  noticeForm.level = 'INFO'
  await refreshNoticeData()
}

const fetchData = async () => {
  const res = await axios.get(`${API}/surveys`, {
    params: {
      username: user.username,
      role: user.role,
      status: listStatus.value === '全部' ? '' : listStatus.value,
      keyword: searchQuery.keyword,
      city: searchQuery.city,
      page: page.value,
      size: 20
    }
  })
  surveys.value = res.data.data; totalPages.value = res.data.pages; totalCount.value = res.data.total
}

const doSearch = () => { page.value = 1; fetchData(); }
const resetSearch = () => { searchQuery.keyword = ''; searchQuery.city = ''; doSearch(); }

const refreshAll = () => { fetchData(); fetchPendingCount(); refreshNoticeData(); }

const submitAdd = async () => {
  if(!form.value.name) return alert('请填写称呼');
  if(!confirm(`确认将客户 [${form.value.name}] 的信息录入系统吗？`)) return;

  form.value.owner = user.username;
  const res = await axios.post(`${API}/surveys`, form.value)
  if(res.data.success) {
    alert('录入成功！');
    form.value = { name: '', phone: '', wechat: '', city: '', project: '', budget: '', remarks: '', remindDays: 3 };
    refreshAll()
  } else { alert(res.data.message); }
}

const processTask = async (id) => {
  if(confirm('⚠️ 确认跟进并完成此数据？\n完成后该记录将流转至“已处理”列表。')) {
    await axios.put(`${API}/surveys/${id}/process`);
    refreshAll()
  }
}

const saveRemarks = async () => {
  if(!selectedSurvey.value) return;
  await axios.put(`${API}/surveys/${selectedSurvey.value.id}/remarks`, { remarks: selectedSurvey.value.remarks });
  alert('备注信息已成功保存！');
  fetchData();
}

const updateDate = async (id, newDate) => {
  await axios.put(`${API}/surveys/${id}/date`, { date: newDate });
  if(selectedSurvey.value) selectedSurvey.value.nextSurveyDate = newDate;
  fetchData();
}

const updateShare = async (item) => {
  if(!confirm('确认修改该数据的可见权限吗？')) return;
  await axios.put(`${API}/surveys/${item.id}/share`, { visibility: item.visibility, sharedUsers: item.sharedUsers || '' });
  alert('可见性设置成功');
  fetchData();
}

const deleteSingle = async (id) => {
  if(confirm('⛔ 危险操作：确定删除？删除后无法恢复！')) {
    await axios.delete(`${API}/surveys/${id}`);
    closeModal();
    refreshAll()
  }
}

const fetchUsers = async () => {
  if (user.role !== 'admin') return;
  try {
    const res = await axios.get(`${API}/users`, { params: { operatorUsername: user.username } });
    usersList.value = res.data;
    res.data.forEach((u) => {
      if (!(u.id in userPasswordDraft)) userPasswordDraft[u.id] = '';
    });
  } catch (e) {
    alert(getApiErrorMessage(e, '员工列表加载失败'));
  }
}

const submitAddUser = async () => {
  if (user.role !== 'admin') return alert('仅管理员可操作');
  if(!userForm.value.username) return alert('请填写账号');
  if(!userForm.value.password) return alert('请填写初始密码');
  if(!confirm(`确认开通名为 [${userForm.value.username}] 的新员工账号吗？`)) return;
  try {
    const encryptedPassword = await encryptPassword(userForm.value.password);
    const res = await axios.post(`${API}/users`, {
      username: userForm.value.username,
      password: encryptedPassword,
      role: userForm.value.role,
      operatorUsername: user.username
    });
    alert(res.data);
    userForm.value = { username: '', password: '', role: 'staff' };
    userForm.value.password = '';
    fetchUsers();
  } catch (e) {
    alert(getApiErrorMessage(e, '员工开通失败，请稍后重试'));
  }
}

const updateStaffPassword = async (staff) => {
  if (user.role !== 'admin') return alert('仅管理员可操作');
  const newPassword = userPasswordDraft[staff.id];
  if (!newPassword) return alert('请输入新密码');
  if (newPassword.length < 6) return alert('新密码至少 6 位');
  if (!confirm(`确认修改员工 [${staff.username}] 的密码吗？`)) return;

  try {
    const encryptedPassword = await encryptPassword(newPassword);
    const res = await axios.put(`${API}/users/${staff.id}/password`, {
      password: encryptedPassword,
      operatorUsername: user.username
    });
    alert(res.data);
    userPasswordDraft[staff.id] = '';
  } catch (e) {
    alert(getApiErrorMessage(e, '员工密码修改失败，请稍后重试'));
  }
}

const deleteStaffUser = async (staff) => {
  if (user.role !== 'admin') return alert('仅管理员可操作');
  if (!confirm(`危险操作：确认删除员工账号 [${staff.username}] 吗？`)) return;
  try {
    const res = await axios.delete(`${API}/users/${staff.id}`, {
      params: { operatorUsername: user.username }
    });
    alert(res.data);
    delete userPasswordDraft[staff.id];
    fetchUsers();
  } catch (e) {
    alert(getApiErrorMessage(e, '员工删除失败，请稍后重试'));
  }
}

const buildRemarkPreview = (remarks) => {
  const text = (remarks || '').trim()
  if (!text) return '无备注'
  const chars = Array.from(text)
  return chars.length > 15 ? `${chars.slice(0, 15).join('')}...` : text
}

const stripTime = (d) => d ? d.split(' ')[0] : ''
const isOverdue = (d) => d && new Date(d) < new Date()
const logout = () => {
  if(confirm('确定要安全退出系统吗？')) {
    isLoggedIn.value = false
    loginForm.password = ''
    isMenuOpen.value = false
    isNoticeCenterOpen.value = false
    noticeList.value = []
    noticeTickerList.value = []
    tickerMessages.value = []
    noticeUnreadCount.value = 0
    selectedNoticeIds.value = []
  }
}

onMounted(() => {
  checkDevice()
  window.addEventListener('resize', checkDevice)
  window.addEventListener('click', handleGlobalClick)
  if(isLoggedIn.value) refreshAll()
})
onUnmounted(() => {
  window.removeEventListener('resize', checkDevice)
  window.removeEventListener('click', handleGlobalClick)
})
</script>

<style>
:root {
  --bg-main: #f4f7fc;
  --bg-card: #ffffff;
  --bg-soft: #eff4fb;
  --ink-main: #142235;
  --ink-sub: #63758d;
  --line: #dbe4ef;
  --brand: #1d4ed8;
  --brand-strong: #1e3a8a;
  --accent: #0e7490;
  --good: #0f766e;
  --warn: #b45309;
  --danger: #be123c;
  --shadow: 0 16px 38px rgba(15, 23, 42, 0.12);
}
</style>

<style scoped>
* {
  box-sizing: border-box;
}

.text-black {
  color: #111827 !important;
}

.app-container {
  min-height: 100vh;
  color: var(--ink-main);
  font-family: "Avenir Next", "SF Pro Text", "PingFang SC", "Noto Sans SC", "Microsoft YaHei", sans-serif;
  background:
    radial-gradient(1100px 380px at 8% -10%, rgba(29, 78, 216, 0.14), transparent 62%),
    radial-gradient(880px 320px at 94% 0%, rgba(14, 116, 144, 0.1), transparent 56%),
    var(--bg-main);
  margin: 0;
  overflow-x: hidden;
}

.login-wrapper {
  min-height: 100vh;
  width: 100%;
  padding: clamp(14px, 4vw, 28px);
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #0f172a 0%, #172554 48%, #0a3255 100%);
}

.login-box {
  width: clamp(290px, 84vw, 440px);
  border-radius: 24px;
  padding: clamp(24px, 5vw, 38px) clamp(18px, 5vw, 34px);
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(255, 255, 255, 0.7);
  box-shadow: 0 26px 56px rgba(15, 23, 42, 0.35);
  animation: rise-in 0.48s ease both;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.input-group {
  width: 100%;
}

.login-actions {
  width: 100%;
  margin-top: 2px;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
}

.mega-title {
  margin: 0;
  font-size: clamp(24px, 4.8vw, 36px);
  line-height: 1.16;
  letter-spacing: 0.01em;
  color: #0f172a;
  text-align: center;
}

.sub-title {
  margin: 12px 0 24px;
  color: var(--ink-sub);
  font-size: 14px;
  letter-spacing: 0.08em;
  text-align: center;
}

.heavy-input {
  width: 100%;
  border: 1px solid #d2dce8;
  border-radius: 12px;
  padding: 12px 14px;
  margin-bottom: 12px;
  font-size: 14px;
  color: #0f172a;
  background: #fff;
  transition: all 0.2s ease;
}

.heavy-input:focus,
.search-input:focus,
.form-grid-fluid input:focus,
.text-area-sim:focus,
.share-select:focus,
.share-input:focus,
.date-picker:focus {
  outline: none;
  border-color: var(--brand);
  box-shadow: 0 0 0 4px rgba(29, 78, 216, 0.12);
}

.btn-login-action,
.btn-refresh,
.btn-search,
.btn-add-fluid,
.btn-save-share,
.btn-action,
.btn-action-small,
.btn-save-remarks {
  border: 0;
  border-radius: 12px;
  cursor: pointer;
  transition: transform 0.16s ease, box-shadow 0.2s ease, filter 0.2s ease;
}

.btn-login-action,
.btn-search,
.btn-add-fluid,
.btn-save-share,
.btn-action {
  color: #fff;
  background: linear-gradient(135deg, var(--brand), var(--brand-strong));
  box-shadow: 0 12px 24px rgba(29, 78, 216, 0.26);
}

.btn-login-action:hover,
.btn-search:hover,
.btn-add-fluid:hover,
.btn-save-share:hover,
.btn-action:hover {
  transform: translateY(-1px);
  filter: brightness(1.02);
}

.btn-login-action {
  width: 100%;
  padding: 12px 14px;
  font-size: 15px;
  font-weight: 700;
  border-radius: 12px;
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 55%, #1e40af 100%);
  box-shadow: 0 12px 24px rgba(37, 99, 235, 0.34);
}

.btn-login-action:hover {
  transform: translateY(-1px);
  filter: brightness(1.05);
}

.btn-login-action:active {
  transform: translateY(0);
  box-shadow: 0 6px 14px rgba(37, 99, 235, 0.28);
}

.btn-forgot {
  align-self: flex-end;
  border: 0;
  background: transparent;
  color: #1e40af;
  font-size: 13px;
  font-weight: 600;
  padding: 4px 2px 0;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 3px;
  transition: color 0.2s ease;
}

.btn-forgot:hover {
  color: #1d4ed8;
}

.dashboard {
  min-height: 100vh;
}

.top-taskbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 66px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  z-index: 30;
  background: rgba(15, 23, 42, 0.88);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(148, 163, 184, 0.25);
}

.taskbar-left,
.taskbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.menu-trigger-wrap {
  position: relative;
}

.hamburger-btn {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  border: 1px solid rgba(148, 163, 184, 0.42);
  background: rgba(30, 41, 59, 0.62);
  color: #cbd5e1;
  cursor: pointer;
  font-size: 18px;
  box-shadow: 0 4px 10px rgba(15, 23, 42, 0.24);
}

.hamburger-btn:hover {
  filter: brightness(1.02);
  transform: translateY(-1px);
}

.menu-popover {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  min-width: 176px;
  border-radius: 12px;
  border: 1px solid rgba(148, 163, 184, 0.32);
  background: rgba(15, 23, 42, 0.95);
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.26);
  padding: 8px;
  display: grid;
  gap: 6px;
  z-index: 40;
  transform-origin: top left;
  animation: menu-drop-in 0.18s ease-out both;
}

.menu-popover button {
  width: 100%;
  border: 0;
  border-radius: 10px;
  text-align: left;
  color: #d5deec;
  background: transparent;
  font-size: 14px;
  font-weight: 600;
  padding: 10px 12px;
  cursor: pointer;
}

.menu-popover button:hover,
.menu-popover button.active {
  color: #fff;
  background: rgba(29, 78, 216, 0.32);
}

.brand-text {
  color: #f8fafc;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.01em;
}

.user-name {
  color: #f8fafc;
  font-weight: 700;
  font-size: 13px;
}

.role-tag {
  font-size: 11px;
  color: #cffafe;
  background: rgba(14, 116, 144, 0.25);
  border: 1px solid rgba(14, 116, 144, 0.4);
  border-radius: 999px;
  padding: 3px 9px;
}

.btn-notice-icon {
  position: relative;
  width: 36px;
  height: 36px;
  border: 1px solid rgba(125, 211, 252, 0.42);
  background: rgba(14, 116, 144, 0.28);
  color: #ecfeff;
  border-radius: 10px;
  font-size: 16px;
  cursor: pointer;
}

.notice-dot {
  position: absolute;
  top: -6px;
  right: -6px;
  min-width: 18px;
  height: 18px;
  border-radius: 999px;
  background: #ef4444;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  padding: 0 5px;
}

.btn-logout-icon {
  border: 1px solid rgba(248, 113, 113, 0.38);
  background: rgba(185, 28, 28, 0.25);
  color: #fee2e2;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.02em;
  padding: 7px 12px;
  cursor: pointer;
}

.main-body {
  margin-top: 66px;
  padding: 16px;
  width: 100%;
}

.notify-bar {
  margin-bottom: 14px;
  border: 1px solid rgba(59, 130, 246, 0.32);
  background: linear-gradient(90deg, rgba(219, 234, 254, 0.96), rgba(239, 246, 255, 0.96));
  border-radius: 12px;
  padding: 10px 12px;
  display: flex;
  align-items: center;
  gap: 12px;
  overflow: hidden;
  animation: rise-in 0.45s ease both;
}

.notify-label {
  flex: 0 0 auto;
  border-radius: 999px;
  background: #dbeafe;
  color: #1d4ed8;
  border: 1px solid rgba(59, 130, 246, 0.3);
  font-size: 12px;
  font-weight: 700;
  padding: 4px 9px;
}

.notify-marquee {
  flex: 1;
  overflow: hidden;
}

.notify-track {
  display: inline-flex;
  align-items: center;
  gap: 34px;
  white-space: nowrap;
  min-width: max-content;
  animation: notice-marquee 24s linear infinite;
}

.notify-item {
  color: #1e3a8a;
  font-size: 13px;
  font-weight: 600;
}

@keyframes notice-marquee {
  from {
    transform: translateX(0);
  }
  to {
    transform: translateX(-50%);
  }
}

.view-content {
  display: grid;
  gap: 12px;
}

.page-header {
  border-radius: 16px;
  padding: 16px;
  background: linear-gradient(120deg, #0f172a, #1e3a8a 64%, #0b4e76);
  color: #fff;
  display: flex;
  flex-direction: column;
  gap: 12px;
  box-shadow: var(--shadow);
  animation: rise-in 0.5s ease both;
}

.white-text {
  margin: 0;
  color: #fff;
  font-size: clamp(23px, 3vw, 30px);
  letter-spacing: 0.01em;
}

.header-tools {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.btn-refresh {
  padding: 11px 14px;
  font-weight: 700;
  color: #0f172a;
  background: #e0f2fe;
  box-shadow: none;
}

.btn-refresh:hover {
  transform: translateY(-1px);
  background: #bae6fd;
}

.tab-group-container {
  flex: 1;
  min-width: 220px;
}

.tab-group {
  display: inline-flex;
  flex-wrap: wrap;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.16);
  padding: 3px;
}

.tab-group button {
  border: 0;
  padding: 8px 13px;
  border-radius: 9px;
  font-size: 13px;
  font-weight: 700;
  color: #dbeafe;
  background: transparent;
  cursor: pointer;
}

.tab-group button.active {
  color: #1e3a8a;
  background: #fff;
  box-shadow: 0 3px 8px rgba(15, 23, 42, 0.16);
}

.search-filter-bar,
.form-card,
.table-container {
  border-radius: 16px;
  border: 1px solid var(--line);
  background: var(--bg-card);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
  animation: rise-in 0.42s ease both;
}

.search-filter-bar {
  padding: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.search-inputs {
  display: flex;
  gap: 10px;
  flex: 1;
  min-width: 240px;
}

.search-input {
  flex: 1;
  border: 1px solid #d2dce8;
  border-radius: 12px;
  background: #fff;
  color: #0f172a;
  padding: 11px 12px;
  font-size: 14px;
}

.search-actions {
  display: flex;
  gap: 8px;
}

.btn-search {
  padding: 11px 18px;
  font-weight: 700;
  border: 1px solid #1d4ed8 !important;
  background: linear-gradient(135deg, #3b82f6, #1d4ed8) !important;
  color: #ffffff !important;
  box-shadow: 0 12px 22px rgba(37, 99, 235, 0.3) !important;
}

.btn-reset {
  border-radius: 12px;
  border: 1px solid rgba(37, 99, 235, 0.5);
  background: #dbeafe;
  color: #1e40af;
  font-weight: 700;
  padding: 11px 16px;
  cursor: pointer;
  transition: transform 0.16s ease, filter 0.2s ease;
}

.btn-reset:hover {
  transform: translateY(-1px);
  filter: brightness(1.04);
}

.form-card {
  padding: 16px;
}

.form-title {
  margin: 0 0 12px;
  font-size: 18px;
  color: #0f172a;
}

.form-grid-fluid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.form-grid-fluid input {
  border: 1px solid #d2dce8;
  border-radius: 12px;
  padding: 11px 12px;
  font-size: 14px;
  color: #0f172a;
  background: #fff;
}

.span-full-width {
  grid-column: 1 / -1;
}

.btn-add-fluid {
  padding: 11px 14px;
  font-weight: 700;
  border: 1px solid #1d4ed8 !important;
  background: linear-gradient(135deg, #3b82f6, #1d4ed8) !important;
  color: #ffffff !important;
  box-shadow: 0 12px 22px rgba(37, 99, 235, 0.3) !important;
}

.search-actions .btn-search,
.form-grid-fluid > .btn-add-fluid {
  border: 1px solid #1d4ed8 !important;
  background: linear-gradient(135deg, #3b82f6, #1d4ed8) !important;
  color: #ffffff !important;
  box-shadow: 0 12px 22px rgba(37, 99, 235, 0.3) !important;
}

.btn-add-fluid.dark {
  background: linear-gradient(130deg, #0f172a, #1e293b);
}

.table-container {
  overflow-x: auto;
}

.modern-table {
  width: 100%;
  min-width: 760px;
  border-collapse: collapse;
}

.modern-table th {
  background: var(--bg-soft);
  color: #334155;
  text-align: left;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
  padding: 14px;
  border-bottom: 1px solid #dde5ef;
}

.modern-table td {
  padding: 14px;
  border-bottom: 1px solid #edf2f7;
  color: #334155;
  font-size: 14px;
}

.role-badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 4px 10px;
  color: #0b4f63;
  background: #cffafe;
  font-size: 12px;
  font-weight: 700;
}

.user-password-input {
  width: 100%;
  min-width: 170px;
  border: 1px solid #d2dce8;
  border-radius: 10px;
  padding: 8px 10px;
  font-size: 13px;
  background: #fff;
}

.user-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.btn-inline,
.btn-inline-danger {
  border: 0;
  border-radius: 8px;
  padding: 7px 10px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.btn-inline {
  color: #1e3a8a;
  background: #dbeafe;
}

.btn-inline-danger {
  color: #991b1b;
  background: #fee2e2;
}

.clickable-row {
  cursor: pointer;
}

.clickable-row:hover {
  background: #f8fbff;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 700;
}

.status-badge.done {
  color: #065f46;
  background: #d1fae5;
}

.status-badge.todo {
  color: #92400e;
  background: #fef3c7;
}

.red-tag {
  margin-top: 6px;
  display: inline-block;
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 700;
  color: #991b1b;
  background: #fee2e2;
}

.highlight-text {
  color: #0f172a;
  font-weight: 700;
}

.contact-line {
  margin: 2px 0;
  color: #64748b;
  font-size: 13px;
}

.remark-inline {
  position: relative;
  margin-top: 6px;
  padding: 4px 8px;
  border-radius: 8px;
  border: 1px dashed #dbe4ef;
  background: #f8fafc;
  color: #475569;
  font-size: 12px;
  line-height: 1.4;
}

.remark-inline-label {
  color: #64748b;
  font-weight: 600;
}

.remark-inline-text {
  color: #1e293b;
  font-weight: 600;
}

.remark-inline.empty .remark-inline-text {
  color: #94a3b8;
  font-weight: 500;
}

.remark-hover-card {
  position: absolute;
  left: 0;
  bottom: calc(100% + 8px);
  width: min(360px, 58vw);
  max-width: 360px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #0f172a;
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.22);
  opacity: 0;
  transform: translateY(4px);
  pointer-events: none;
  transition: opacity 0.16s ease, transform 0.16s ease;
  white-space: normal;
  word-break: break-word;
  z-index: 35;
}

.remark-inline:hover .remark-hover-card {
  opacity: 1;
  transform: translateY(0);
}

.text-orange {
  color: var(--accent);
}

.text-danger {
  color: var(--danger);
}

.text-muted {
  color: #94a3b8;
}

.empty-state {
  text-align: center;
  color: #94a3b8;
  padding: 34px !important;
}

.btn-action-small {
  background: #dbeafe;
  color: #1e3a8a;
  font-weight: 700;
  padding: 8px 12px;
  box-shadow: none;
}

.btn-action-small:hover {
  background: #bfdbfe;
}

.notice-center-overlay {
  z-index: 85;
}

.notice-center-panel {
  width: min(860px, 100%);
  max-height: 92vh;
  border-radius: 18px;
  border: 1px solid #dbe4ef;
  background: #fff;
  box-shadow: 0 24px 56px rgba(15, 23, 42, 0.34);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.notice-center-header {
  padding: 14px 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
}

.notice-center-title {
  margin: 0;
  font-size: 18px;
  color: #0f172a;
}

.notice-center-body {
  padding: 14px 18px 18px;
  overflow-y: auto;
  display: grid;
  gap: 12px;
}

.notice-filter-tabs {
  display: inline-flex;
  border-radius: 12px;
  background: #eff6ff;
  padding: 4px;
  gap: 3px;
}

.notice-filter-tabs button {
  border: 0;
  border-radius: 9px;
  padding: 7px 12px;
  font-size: 12px;
  font-weight: 700;
  color: #1e3a8a;
  background: transparent;
  cursor: pointer;
}

.notice-filter-tabs button.active {
  background: #fff;
  box-shadow: 0 3px 8px rgba(15, 23, 42, 0.12);
}

.notice-batch-tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 10px 12px;
  background: #f8fafc;
}

.notice-check-all {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #334155;
}

.notice-selected-count {
  color: #475569;
  font-size: 13px;
}

.notice-action-btn {
  border: 0;
  border-radius: 10px;
  background: #dbeafe;
  color: #1e3a8a;
  font-size: 12px;
  font-weight: 700;
  padding: 7px 11px;
  cursor: pointer;
}

.notice-action-btn.danger {
  background: #fee2e2;
  color: #991b1b;
}

.notice-action-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.notice-list {
  display: grid;
  gap: 10px;
}

.notice-row {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 10px 12px;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  background: #fff;
}

.notice-row-check {
  padding-top: 2px;
}

.notice-row-content {
  flex: 1;
  min-width: 0;
}

.notice-row-top {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.notice-level {
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 700;
}

.notice-level.info {
  background: #dbeafe;
  color: #1d4ed8;
}

.notice-level.warn {
  background: #fef3c7;
  color: #b45309;
}

.notice-level.alert {
  background: #fee2e2;
  color: #be123c;
}

.notice-row-title {
  color: #0f172a;
  font-size: 14px;
}

.notice-row-time {
  margin-left: auto;
  color: #64748b;
  font-size: 12px;
}

.notice-row-text {
  margin: 6px 0;
  color: #334155;
  font-size: 13px;
  line-height: 1.45;
}

.notice-row-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.notice-inline-btn {
  border: 0;
  border-radius: 8px;
  background: #dbeafe;
  color: #1e3a8a;
  font-size: 12px;
  font-weight: 700;
  padding: 5px 9px;
  cursor: pointer;
}

.notice-inline-btn.danger {
  background: #fee2e2;
  color: #991b1b;
}

.notice-read-flag {
  font-size: 12px;
  color: #0f766e;
  font-weight: 700;
}

.notice-empty {
  text-align: center;
  color: #94a3b8;
  border: 1px dashed #cbd5e1;
  border-radius: 12px;
  padding: 24px 12px;
}

.notice-pager {
  margin: 4px 0 0;
}

.notice-create-box {
  border-radius: 14px;
  border: 1px solid #dbe4ef;
  background: #f8fafc;
  padding: 12px;
}

.notice-create-title {
  margin: 0 0 10px;
  color: #0f172a;
  font-size: 15px;
}

.notice-create-grid {
  display: grid;
  gap: 10px;
}

.notice-create-grid input,
.notice-create-grid select,
.notice-create-grid textarea {
  border: 1px solid #d2dce8;
  border-radius: 10px;
  background: #fff;
  padding: 9px 11px;
  font-size: 13px;
}

.notice-create-grid textarea {
  min-height: 88px;
  resize: vertical;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  padding: 16px;
  background: rgba(2, 8, 23, 0.62);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 80;
}

.modal-content {
  width: min(780px, 100%);
  max-height: 92vh;
  border-radius: 18px;
  border: 1px solid #dbe4ef;
  background: #fff;
  box-shadow: 0 24px 56px rgba(15, 23, 42, 0.34);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.modal-header {
  padding: 16px 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
}

.modal-title {
  margin: 0;
  color: #0f172a;
}

.btn-close {
  border: 0;
  background: #dbeafe;
  color: #1e3a8a;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  cursor: pointer;
  font-weight: 700;
}

.btn-close:hover {
  background: #bfdbfe;
}

.modal-body {
  padding: 16px 18px;
  overflow-y: auto;
}

.modal-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.m-item {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 10px 12px;
  background: #f8fafc;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.m-item.full-width {
  grid-column: 1 / -1;
  flex-direction: column;
  align-items: stretch;
}

.m-item .lbl {
  color: #64748b;
  font-size: 13px;
  min-width: 58px;
  font-weight: 600;
}

.m-item .val {
  color: #0f172a;
  font-weight: 600;
}

.focus-card {
  border-color: #bfdbfe;
  background: linear-gradient(135deg, #eff6ff, #f8fafc);
}

.focus-card .lbl {
  color: #1d4ed8;
  font-weight: 700;
}

.focus-card .focus-val {
  font-size: 16px;
  font-weight: 800;
  color: #0f172a;
}

.budget-card .focus-val {
  color: #b45309;
}

.focus-remarks {
  border-color: #bfdbfe;
  background: linear-gradient(180deg, #eff6ff 0%, #f8fafc 100%);
}

.focus-remarks .lbl {
  color: #1d4ed8;
  font-weight: 700;
}

.remarks-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.btn-save-remarks {
  padding: 7px 10px;
  font-size: 12px;
  font-weight: 700;
  background: #dbeafe;
  color: #1e3a8a;
  box-shadow: none;
}

.btn-save-remarks:hover {
  background: #bfdbfe;
}

.text-area-sim {
  width: 100%;
  min-height: 116px;
  border: 1px solid #d2dce8;
  border-radius: 12px;
  padding: 11px 12px;
  font-size: 14px;
  resize: vertical;
}

.reminder-row-modal {
  background: #eff6ff;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  padding: 10px 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.reminder-row-modal .lbl {
  color: #1d4ed8;
}

.date-picker {
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  padding: 8px 10px;
  background: #fff;
}

.admin-panel-fluid {
  margin-top: 12px;
  border-radius: 12px;
  border: 1px dashed #cbd5e1;
  background: #f8fafc;
  padding: 12px;
}

.admin-info {
  font-size: 13px;
  color: #334155;
  padding-bottom: 10px;
  border-bottom: 1px solid #e2e8f0;
}

.admin-controls-fluid {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.share-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.share-select,
.share-input {
  border: 1px solid #d2dce8;
  border-radius: 10px;
  padding: 8px 10px;
  font-size: 13px;
}

.btn-save-share {
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 700;
}

.btn-del-modal {
  border: 1px solid #fecaca;
  border-radius: 10px;
  background: #fee2e2;
  color: #991b1b;
  font-weight: 700;
  padding: 8px 12px;
  cursor: pointer;
}

.btn-del-modal:hover {
  background: #fecaca;
}

.modal-footer {
  padding: 14px 18px 18px;
  border-top: 1px solid #e2e8f0;
  background: #fff;
}

.btn-action {
  width: 100%;
  padding: 12px 14px;
  font-size: 14px;
  font-weight: 700;
}

.pager-fluid {
  margin: 14px 0 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.pager-fluid button {
  border: 1px solid rgba(37, 99, 235, 0.38);
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  color: #fff;
  font-weight: 700;
  border-radius: 10px;
  padding: 8px 13px;
  cursor: pointer;
  transition: transform 0.16s ease, filter 0.2s ease;
}

.pager-fluid button:hover:not(:disabled) {
  transform: translateY(-1px);
  filter: brightness(1.05);
}

.pager-fluid button:disabled {
  opacity: 0.55;
  filter: grayscale(0.35);
  cursor: not-allowed;
}

@keyframes rise-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes menu-drop-in {
  from {
    opacity: 0;
    transform: translateY(-6px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@media (min-width: 1025px) {
  .main-body {
    padding: 22px 30px;
  }

  .hide-on-mobile {
    display: inline-flex;
  }

  .page-header {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }
}

@media (max-width: 1024px) {
  .hide-on-mobile {
    display: none;
  }

  .top-taskbar {
    height: 60px;
  }

  .menu-popover {
    top: calc(100% + 6px);
    min-width: 160px;
  }

  .main-body {
    margin-top: 60px;
    padding: 14px;
  }

  .header-tools {
    flex-direction: column;
    align-items: stretch;
  }

  .search-inputs {
    min-width: 100%;
    flex-direction: column;
  }

  .search-actions {
    width: 100%;
  }

  .btn-search,
  .btn-reset {
    flex: 1;
  }

  .form-grid-fluid {
    grid-template-columns: 1fr;
  }

  .modal-grid {
    grid-template-columns: 1fr;
  }

  .admin-controls-fluid {
    flex-direction: column;
    align-items: stretch;
  }

  .share-group {
    flex-direction: column;
    align-items: stretch;
  }

  .notice-center-body {
    padding: 12px;
  }

  .notice-batch-tools {
    flex-direction: column;
    align-items: flex-start;
  }

  .notice-row-time {
    margin-left: 0;
    width: 100%;
  }

  .notice-row-actions {
    flex-wrap: wrap;
  }
}
</style>

