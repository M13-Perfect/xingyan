<template>
  <div class="app-container">
    <div v-if="!isLoggedIn" class="login-wrapper">
      <div class="login-box">
        <h1 class="mega-title">序界客户回访管理系统</h1>
        <h3 class="sub-title">序界数据终端</h3>
        <div class="input-group">
          <input v-model="loginForm.username" placeholder="请输入您的账号" class="heavy-input text-black">
          <input v-model="loginForm.password" type="password" placeholder="请输入您的密码" class="heavy-input text-black" @keyup.enter="handleLogin">
        </div>
        <button @click="handleLogin" class="btn-login-action">安全登录</button>
      </div>
    </div>

    <div v-else class="dashboard">

      <header class="top-taskbar">
        <div class="taskbar-left">
          <button class="hamburger-btn" @click="toggleSidebar" title="展开/收起菜单">☰</button>
          <span class="brand-text">序界客户回访系统</span>
        </div>
        <div class="taskbar-right">
          <span class="user-name">{{ user.username }}</span>
          <span class="role-tag hide-on-mobile">{{ user.role === 'admin' ? '管理员' : '专员' }}</span>
          <button class="btn-logout-icon" @click="logout" title="退出登录">🚪</button>
        </div>
      </header>

      <div class="sidebar-overlay" v-if="isMobile && isSidebarOpen" @click="toggleSidebar"></div>
      <aside class="sidebar-drawer" :class="{ 'drawer-open': isSidebarOpen }">
        <div class="menu">
          <button :class="{active: view === 'work'}" @click="switchView('work')">📝 工作台</button>
          <button v-if="user.role === 'admin'" :class="{active: view === 'users'}" @click="switchView('users')">👥 员工管理</button>
        </div>
      </aside>

      <main class="main-body" :class="{ 'body-shifted': isSidebarOpen && !isMobile }">

        <div class="notify-bar" v-if="pendingCount > 0">
          🔔 紧急通知：还有 <strong class="pulse-text">{{ pendingCount }}</strong> 条待处理数据，请及时跟进！
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
              <button @click="doSearch" class="btn-search">精准查询</button>
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
              <button @click="submitAdd" class="btn-add-fluid span-full-width">提交录入</button>
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
                <th>下次提醒</th>
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
                </td>
                <td :class="{'text-danger font-bold': isOverdue(item.nextSurveyDate)}">
                  📅 {{ stripTime(item.nextSurveyDate) || '未设置' }}
                </td>
                <td @click.stop>
                  <button v-if="item.status==='未处理'" @click="processTask(item.id)" class="btn-action-small">✅ 完成处理</button>
                  <span v-else class="text-muted">已归档</span>
                </td>
              </tr>
              <tr v-if="surveys.length === 0">
                <td colspan="6" class="empty-state">当前列表暂无数据</td>
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
              <input v-model="userForm.password" placeholder="设置初始密码" class="text-black">
              <button @click="submitAddUser" class="btn-add-fluid dark">确认开通</button>
            </div>
          </div>
          <div class="table-container">
            <table class="modern-table">
              <thead><tr><th>员工账号</th><th>权限角色</th></tr></thead>
              <tbody>
              <tr v-for="u in usersList" :key="u.id">
                <td class="bold text-black">{{ u.username }}</td>
                <td><span class="role-badge">业务专员</span></td>
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
    </div>

    <div class="modal-overlay" v-if="selectedSurvey" @click.self="closeModal">
      <div class="modal-content">

        <header class="modal-header">
          <h3 class="modal-title">📄 客户档案详情</h3>
          <button class="btn-close" @click="closeModal" title="关闭">✖</button>
        </header>

        <div class="modal-body">
          <div class="modal-grid">
            <div class="m-item"><span class="lbl">称呼:</span> <span class="val highlight-text">{{ selectedSurvey.name }}</span></div>
            <div class="m-item"><span class="lbl">状态:</span>
              <span :class="['status-badge', selectedSurvey.status === '已处理' ? 'done' : 'todo']">{{ selectedSurvey.status }}</span>
            </div>
            <div class="m-item"><span class="lbl">电话:</span> <span class="val text-black">{{ selectedSurvey.phone || '无' }}</span></div>
            <div class="m-item"><span class="lbl">微信:</span> <span class="val text-black">{{ selectedSurvey.wechat || '无' }}</span></div>
            <div class="m-item"><span class="lbl">城市:</span> <span class="val text-black">{{ selectedSurvey.city }}</span></div>
            <div class="m-item"><span class="lbl">项目:</span> <span class="val text-black">{{ selectedSurvey.project }}</span></div>
            <div class="m-item"><span class="lbl">预算:</span> <span class="val text-orange font-bold">{{ selectedSurvey.budget }}</span></div>

            <div class="m-item full-width mt-2 remarks-box">
              <div class="remarks-header">
                <span class="lbl block">需求备注 (可直接编辑):</span>
                <button @click="saveRemarks" class="btn-save-remarks">💾 保存修改</button>
              </div>
              <textarea v-model="selectedSurvey.remarks" class="text-area-sim text-black" placeholder="在这里输入或修改客户的备注信息..."></textarea>
            </div>

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
              <button @click="deleteSingle(selectedSurvey.id)" class="btn-del-modal">🗑️ 永久删除</button>
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
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import axios from 'axios'

const API = '/api'
const isLoggedIn = ref(false)
const user = reactive({ username: '', role: '' })
const loginForm = reactive({ username: '', password: '' })
const view = ref('work')

const isMobile = ref(false)
const isSidebarOpen = ref(true)

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

const checkDevice = () => {
  const ua = navigator.userAgent;
  const isMobileUA = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(ua);
  const isSmallScreen = window.innerWidth <= 1024;
  isMobile.value = isMobileUA || isSmallScreen;
  isSidebarOpen.value = !isMobile.value;
}
const toggleSidebar = () => { isSidebarOpen.value = !isSidebarOpen.value; }
const switchView = (v) => {
  view.value = v; page.value = 1;
  if (isMobile.value) isSidebarOpen.value = false;
  if(v === 'users') fetchUsers(); else fetchData();
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

const handleLogin = async () => {
  if(!loginForm.username) return alert('请输入账号');
  try {
    const res = await axios.post(`${API}/login`, loginForm)
    if(res.data.success) {
      isLoggedIn.value = true; user.username = res.data.username; user.role = res.data.role
      view.value = 'work'; listStatus.value = '未处理';
      checkDevice();
      await fetchPendingCount();
      alert(`登录成功！\n目前有 ${pendingCount.value} 条待处理数据。`);
      fetchData()
    } else { alert(res.data.message) }
  } catch (e) { alert(getRequestErrorMessage(e)) }
}

const fetchPendingCount = async () => {
  const res = await axios.get(`${API}/surveys/pending-count`, { params: { username: user.username, role: user.role } })
  pendingCount.value = res.data
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

const refreshAll = () => { fetchData(); fetchPendingCount(); }

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
  if(confirm('⛔ 危险操作：确定永久删除？删除后无法恢复！')) {
    await axios.delete(`${API}/surveys/${id}`);
    closeModal();
    refreshAll()
  }
}

const fetchUsers = async () => { const res = await axios.get(`${API}/users`); usersList.value = res.data }
const submitAddUser = async () => {
  if(!userForm.value.username) return alert('请填写账号');
  if(!confirm(`确认开通名为 [${userForm.value.username}] 的新员工账号吗？`)) return;
  const res = await axios.post(`${API}/users`, userForm.value); alert(res.data);
  userForm.value = { username: '', password: '', role: 'staff' }; fetchUsers()
}

const stripTime = (d) => d ? d.split(' ')[0] : ''
const isOverdue = (d) => d && new Date(d) < new Date()
const logout = () => { if(confirm('确定要安全退出系统吗？')) { isLoggedIn.value = false; loginForm.password = ''; } }

onMounted(() => {
  checkDevice();
  window.addEventListener('resize', checkDevice);
  if(isLoggedIn.value) refreshAll()
})
onUnmounted(() => { window.removeEventListener('resize', checkDevice); })
</script>

<style scoped>
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
  padding: 22px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #0f172a 0%, #172554 48%, #0a3255 100%);
}

.login-box {
  width: 100%;
  max-width: 440px;
  border-radius: 24px;
  padding: 38px 34px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(255, 255, 255, 0.7);
  box-shadow: 0 26px 56px rgba(15, 23, 42, 0.35);
  animation: rise-in 0.48s ease both;
}

.mega-title {
  margin: 0;
  font-size: clamp(28px, 5vw, 42px);
  line-height: 1.16;
  letter-spacing: 0.01em;
  color: #0f172a;
}

.sub-title {
  margin: 12px 0 24px;
  color: var(--ink-sub);
  font-size: 14px;
  letter-spacing: 0.08em;
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
  padding: 13px 14px;
  font-size: 16px;
  font-weight: 700;
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

.hamburger-btn {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  background: rgba(30, 41, 59, 0.78);
  color: #dbeafe;
  cursor: pointer;
  font-size: 18px;
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

.btn-logout-icon {
  border: 1px solid rgba(248, 113, 113, 0.34);
  background: rgba(185, 28, 28, 0.2);
  color: #fee2e2;
  border-radius: 10px;
  font-size: 14px;
  padding: 7px 10px;
  cursor: pointer;
}

.sidebar-overlay {
  position: fixed;
  inset: 0;
  background: rgba(2, 8, 23, 0.48);
  z-index: 20;
}

.sidebar-drawer {
  position: fixed;
  top: 66px;
  left: 0;
  width: 232px;
  height: calc(100vh - 66px);
  padding: 14px 10px;
  background: rgba(15, 23, 42, 0.95);
  border-right: 1px solid rgba(148, 163, 184, 0.18);
  z-index: 25;
  overflow-y: auto;
  transform: translateX(-100%);
  transition: transform 0.25s ease;
}

.sidebar-drawer.drawer-open {
  transform: translateX(0);
}

.menu {
  display: grid;
  gap: 8px;
}

.menu button {
  width: 100%;
  border: 0;
  border-radius: 10px;
  text-align: left;
  color: #d5deec;
  background: transparent;
  font-size: 15px;
  font-weight: 600;
  padding: 11px 13px;
  cursor: pointer;
}

.menu button:hover,
.menu button.active {
  color: #fff;
  background: rgba(29, 78, 216, 0.24);
}

.main-body {
  margin-top: 66px;
  padding: 16px;
  width: 100%;
  transition: margin-left 0.25s ease;
}

.body-shifted {
  margin-left: 232px;
  width: calc(100% - 232px);
}

.notify-bar {
  margin-bottom: 12px;
  border: 1px solid rgba(251, 146, 60, 0.45);
  background: linear-gradient(90deg, rgba(254, 243, 199, 0.88), rgba(255, 255, 255, 0.95));
  color: #9a3412;
  border-radius: 12px;
  padding: 12px 14px;
  animation: rise-in 0.45s ease both;
}

.pulse-text {
  color: #b45309;
  font-size: 17px;
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
}

.btn-reset {
  border-radius: 12px;
  border: 1px solid #d2dce8;
  background: #eef3fb;
  color: #1f3758;
  font-weight: 700;
  padding: 11px 16px;
  cursor: pointer;
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
  background: #e2e8f0;
  color: #334155;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  cursor: pointer;
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
  min-height: 92px;
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
  border: 1px solid #d2dce8;
  background: #fff;
  color: #0f172a;
  font-weight: 700;
  border-radius: 10px;
  padding: 8px 13px;
  cursor: pointer;
}

.pager-fluid button:disabled {
  opacity: 0.45;
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

  .sidebar-drawer {
    top: 60px;
    height: calc(100vh - 60px);
  }

  .main-body {
    margin-top: 60px;
    padding: 14px;
  }

  .body-shifted {
    margin-left: 0;
    width: 100%;
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
}
</style>

