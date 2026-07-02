<template>
  <div class="app-container">
    <div v-if="!isLoggedIn" class="login-wrapper">
      <section class="login-shell" aria-labelledby="login-title">
        <header class="login-header">
          <p class="login-kicker">序界数据终端</p>
          <h1 id="login-title" class="mega-title">序界回访管理系统</h1>
        </header>

        <form class="login-form" @submit.prevent="handleLogin">
          <label class="login-field" for="login-username">
            <span>账号</span>
            <input
              id="login-username"
              v-model.trim="loginForm.username"
              class="login-input text-black"
              autocomplete="username"
              placeholder="请输入账号"
            >
          </label>
          <label class="login-field" for="login-password">
            <span>密码</span>
            <div class="password-control">
              <input
                id="login-password"
                v-model="loginForm.password"
                class="login-input text-black"
                :type="isPasswordVisible ? 'text' : 'password'"
                autocomplete="current-password"
                placeholder="请输入密码"
              >
              <button
                type="button"
                class="password-toggle"
                :aria-label="isPasswordVisible ? '隐藏密码' : '显示密码'"
                @click="isPasswordVisible = !isPasswordVisible"
              >
                <svg
                  v-if="!isPasswordVisible"
                  class="password-toggle-icon"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path d="M3 12s3.4-6 9-6 9 6 9 6-3.4 6-9 6-9-6-9-6Z" />
                  <circle cx="12" cy="12" r="2.6" />
                </svg>
                <svg
                  v-else
                  class="password-toggle-icon"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path d="M4 13c2.1 2.7 4.8 4 8 4s5.9-1.3 8-4" />
                  <path d="M7.5 16.2 6 18" />
                  <path d="M12 17v2" />
                  <path d="m16.5 16.2 1.5 1.8" />
                </svg>
              </button>
            </div>
          </label>
          <div class="login-status" aria-live="polite">{{ loginStatus }}</div>
          <button type="submit" class="btn-login-action" :disabled="isLoginSubmitting" :aria-busy="isLoginSubmitting">
            {{ isLoginSubmitting ? '登录中...' : '登录' }}
          </button>
        </form>

        <footer class="login-footer">© 2026 序界回访管理系统 版权所有</footer>
      </section>
    </div>

    <div v-else class="dashboard">

      <header class="top-taskbar">
        <div class="taskbar-left">
          <div class="menu-trigger-wrap mobile-menu">
            <button class="hamburger-btn" @click.stop="toggleMenuPanel" title="展开/收起菜单">菜单</button>
            <div class="menu-popover" v-if="isMenuOpen" @click.stop>
              <button :class="{active: view === 'work'}" @click="switchView('work')">工作台</button>
              <button v-if="user.role === 'admin'" :class="{active: view === 'users'}" @click="switchView('users')">员工管理</button>
              <button v-if="user.role === 'admin'" type="button" @click="openSystemSettings">设置</button>
            </div>
          </div>
          <span class="brand-text">序界客户回访系统</span>
          <nav class="desktop-nav" aria-label="主导航">
            <button :class="{active: view === 'work'}" @click="switchView('work')">工作台</button>
            <button v-if="user.role === 'admin'" :class="{active: view === 'users'}" @click="switchView('users')">员工管理</button>
            <button v-if="user.role === 'admin'" type="button" @click="openSystemSettings">设置</button>
          </nav>
        </div>
        <div class="taskbar-right">
          <button
            class="avatar-btn"
            type="button"
            @click="openPersonalCenter"
            :title="`${myAccount.displayName || user.username}（${user.role === 'admin' ? '管理员' : '专员'}）· 个人中心`"
          >
            <span class="avatar-circle">{{ avatarInitial }}</span>
          </button>
          <el-badge :value="noticeUnreadCount > 99 ? '99+' : noticeUnreadCount" :hidden="noticeUnreadCount === 0" class="notice-badge">
            <el-button class="btn-notice-icon" @click="toggleNoticeCenter" title="通知中心">通知</el-button>
          </el-badge>
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
            <h2 class="white-text">工作台</h2>
            <div class="header-search-group">
              <div class="search-bar">
                <el-input
                  v-model="searchQuery.keyword"
                  class="search-input"
                  placeholder="搜索客户"
                  aria-label="搜索客户"
                  clearable
                  @keyup.enter="doSearch"
                />
                <el-button type="primary" @click="doSearch">搜索</el-button>
              </div>
              <el-button type="success" class="btn-create-record" @click="openSurveyForm">登记</el-button>
            </div>
            <div class="header-tools">
              <button @click="refreshAll" class="btn-refresh" title="刷新数据" aria-label="刷新数据">
                <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10"></polyline><polyline points="1 20 1 14 7 14"></polyline><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"></path></svg>
              </button>
              <div class="tab-group-container">
                <div class="tab-group">
                  <button :class="{active: !dueOnly && listStatus==='未处理'}" @click="selectListStatus('未处理')">待处理</button>
                  <button :class="{active: dueOnly}" @click="selectRevisitTab">待回访<span v-if="revisitDueCount > 0"> {{ revisitDueCount }}</span></button>
                  <button :class="{active: !dueOnly && listStatus==='已处理'}" @click="selectListStatus('已处理')">已处理</button>
                  <button v-if="user.role === 'admin'" :class="{active: !dueOnly && listStatus==='全部'}" @click="selectListStatus('全部')">全部</button>
                </div>
              </div>
            </div>
          </header>

          <button
            v-if="revisitTodayCount > 0 || revisitOverdueCount > 0"
            type="button"
            class="revisit-summary"
            :class="{ active: dueOnly }"
            @click="selectRevisitTab"
          >
            <span class="revisit-summary-today">今日待回访 {{ revisitTodayCount }}</span>
            <span class="revisit-summary-sep">·</span>
            <span class="revisit-summary-overdue">已逾期 {{ revisitOverdueCount }}</span>
          </button>
          <div v-else class="revisit-summary revisit-summary-empty">暂无待回访</div>

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
                  <el-tag :type="surveyStatusBadge(item).type" size="small">{{ surveyStatusBadge(item).text }}</el-tag>
                  <div v-if="revisitTag(item).type === 'today'" class="amber-tag mt-1">{{ revisitTag(item).text }}</div>
                  <div v-if="user.role === 'admin'" class="owner-nickname-line text-muted">录入：{{ item.ownerNickname || item.owner || '未知' }}</div>
                </td>
                <td class="bold highlight-text">{{ item.name }}</td>
                <td>
                  <div class="contact-line">
                    电话：<span class="text-black">{{ displayPhoneValue(item, revealedPhones, nowTick) }}</span>
                  </div>
                  <div class="contact-line">微信：<span class="text-black">{{ item.wechat || '无' }}</span></div>
                  <div class="contact-line">社交账号：<span class="text-black">{{ item.socialAccount || '无' }}</span></div>
                </td>
                <td>
                  <div class="text-black">城市：{{ item.city || '未知' }}</div>
                  <div class="text-orange font-bold">项目：{{ item.project || '无' }}</div>
                  <div class="remark-inline" :class="{ empty: !item.remarks }">
                    <span class="remark-inline-label">备注：</span>
                    <span class="remark-inline-text">{{ buildRemarkPreview(item.remarks) }}</span>
                    <div v-if="item.remarks" class="remark-hover-card">{{ item.remarks }}</div>
                  </div>
                </td>
                <td @click.stop>
                  <el-button v-if="item.status==='未处理'" size="small" type="primary" @click="processTask(item.id)">完成处理</el-button>
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
              <input v-model="userForm.username" placeholder="设置账号（登录用，仅限字母/数字/下划线/连字符）" class="text-black">
              <input v-model="userForm.nickname" placeholder="设置昵称（可选，默认与账号相同）" class="text-black">
              <input v-model="userForm.password" type="password" placeholder="设置初始密码" class="text-black">
              <button @click="submitAddUser" class="btn-add-fluid dark">确认开通</button>
            </div>
          </div>
          <div class="table-container">
            <table class="modern-table">
              <thead>
              <tr>
                <th>员工账号 / 昵称</th>
                <th>权限角色</th>
                <th>新密码</th>
                <th>操作</th>
                <th>未处理 / 已逾期</th>
              </tr>
              </thead>
              <tbody>
              <tr v-for="u in usersList" :key="u.id">
                <td>
                  <div class="bold text-black">{{ u.username }}</div>
                  <div v-if="u.nickname && u.nickname !== u.username" class="staff-nickname">{{ u.nickname }}</div>
                </td>
                <td><span class="role-badge">{{ u.role === 'admin' ? '管理员' : '业务专员' }}</span></td>
                <td>
                  <input
                    v-model="userPasswordDraft[u.id]"
                    class="user-password-input text-black"
                    type="password"
                    placeholder="6 位以上新密码"
                  >
                </td>
                <td class="user-actions">
                  <button @click="updateStaffPassword(u)" class="btn-inline">修改密码</button>
                  <button @click="deleteStaffUser(u)" class="btn-inline-danger">删除员工</button>
                </td>
                <td class="workload-cell">
                  <span class="count-pill">未处理 {{ u.pendingCount ?? 0 }}</span>
                  <span class="count-pill" :class="{ 'count-pill-danger': (u.overdueCount ?? 0) > 0 }">已逾期 {{ u.overdueCount ?? 0 }}</span>
                </td>
              </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="modal-overlay" v-if="isSystemSettingsOpen" @click.self="closeSystemSettings">
          <div class="modal-content system-settings-modal">
            <header class="modal-header">
              <h3 class="modal-title">系统设置</h3>
              <button class="btn-close" type="button" @click="closeSystemSettings" title="关闭">关闭</button>
            </header>
            <div class="form-grid-fluid">
              <div class="policy-field policy-toggle-field">
                <span>手机号隐私设置</span>
                <button
                  type="button"
                  class="privacy-toggle"
                  :class="isPhonePrivacyOn ? 'is-on' : 'is-off'"
                  role="switch"
                  :aria-checked="isPhonePrivacyOn"
                  @click="isPhonePrivacyOn = !isPhonePrivacyOn"
                >
                  <span class="privacy-toggle-knob"></span>
                  <span class="privacy-toggle-text">{{ isPhonePrivacyOn ? 'ON' : 'OFF' }}</span>
                </button>
              </div>
              <label class="policy-field">
                <span>单页显示订单数量</span>
                <input
                  v-model.number="systemSettingsForm.orderPageSize"
                  class="text-black"
                  type="number"
                  min="1"
                  max="100"
                >
              </label>
              <label class="policy-field">
                <span>回访时限（天）</span>
                <input
                  v-model.number="systemSettingsForm.revisitDeadlineDays"
                  class="text-black"
                  type="number"
                  min="1"
                  max="30"
                >
              </label>
            </div>
            <footer class="modal-footer">
              <button class="btn-inline" type="button" @click="closeSystemSettings">取消</button>
              <button class="btn-action" type="button" :disabled="isSystemSettingsSaving" @click="saveSystemSettings">保存</button>
            </footer>
          </div>
        </div>

        <div class="modal-overlay" v-if="isPersonalCenterOpen" @click.self="closePersonalCenter">
          <div class="modal-content personal-center-modal">
            <header class="modal-header">
              <h3 class="modal-title">个人中心</h3>
              <button class="btn-close" type="button" @click="closePersonalCenter" title="关闭">关闭</button>
            </header>
            <div class="form-grid-fluid">
              <label class="policy-field">
                <span>登录账号</span>
                <input :value="user.username" class="text-black" disabled>
              </label>
              <label class="policy-field">
                <span>昵称</span>
                <input v-model.trim="personalCenterForm.displayName" class="text-black" placeholder="设置昵称（支持中文）" maxlength="50">
              </label>
            </div>
            <div class="modal-status" v-if="personalCenterMessage">{{ personalCenterMessage }}</div>
            <footer class="modal-footer">
              <button class="btn-action" type="button" :disabled="isPersonalCenterSaving" @click="saveNickname">保存昵称</button>
            </footer>

            <hr class="modal-divider">

            <h4 class="form-title">修改密码</h4>
            <div class="form-grid-fluid">
              <input v-model="passwordChangeForm.currentPassword" type="password" placeholder="当前密码" class="text-black" autocomplete="current-password">
              <input v-model="passwordChangeForm.newPassword" type="password" placeholder="新密码（至少 6 位）" class="text-black" autocomplete="new-password">
              <input v-model="passwordChangeForm.confirmPassword" type="password" placeholder="确认新密码" class="text-black" autocomplete="new-password">
            </div>
            <p class="policy-warning">忘记当前密码请联系管理员，在"员工管理"里重置。</p>
            <div class="modal-status" v-if="passwordChangeMessage">{{ passwordChangeMessage }}</div>
            <footer class="modal-footer">
              <button class="btn-action" type="button" :disabled="isPasswordChangeSaving" @click="submitOwnPasswordChange">修改密码</button>
            </footer>
          </div>
        </div>

        <div class="pager-fluid" v-if="view === 'work'">
          <button :disabled="page <= 1" @click="page--;fetchData()">上一页</button>
          <span class="pager-txt">第 {{ page }} / {{ totalPages }} 页</span>
          <button :disabled="page >= totalPages" @click="page++;fetchData()">下一页</button>
        </div>
      </main>

      <div class="modal-overlay" v-if="isSurveyFormOpen" @click.self="closeSurveyForm">
        <div class="modal-content create-survey-modal">
          <header class="modal-header">
            <h3 class="modal-title">录入新回访记录</h3>
            <button class="btn-close" @click="closeSurveyForm" title="关闭">关闭</button>
          </header>

          <form class="modal-body form-grid-fluid create-survey-form" @submit.prevent="submitAdd">
            <input v-model="form.name" placeholder="称呼 (必填)" class="text-black">
            <input v-model="form.phone" placeholder="电话 (不可重复)" class="text-black">
            <input v-model="form.wechat" placeholder="微信 (不可重复)" class="text-black">
            <input v-model="form.city" placeholder="城市" class="text-black">
            <input v-model="form.socialAccount" placeholder="社交账号" class="text-black">
            <input v-model="form.project" placeholder="项目" class="text-black">
            <input v-model="form.budget" placeholder="预算" class="text-black">
            <input v-model="form.remarks" placeholder="需求备注" class="text-black remarks-input span-full-width">
            <button type="submit" class="btn-add-fluid span-full-width">录入</button>
          </form>
        </div>
      </div>

      <el-drawer v-model="isNoticeCenterOpen" title="通知中心" size="420px">
        <div class="notice-center-body">
          <el-radio-group v-model="noticeStatus" class="notice-filter-tabs" @change="switchNoticeStatus">
            <el-radio-button label="UNREAD">未读</el-radio-button>
            <el-radio-button label="READ">已读</el-radio-button>
            <el-radio-button label="ALL">全部</el-radio-button>
          </el-radio-group>

          <div class="notice-batch-tools">
            <el-checkbox :model-value="isAllCurrentNoticesSelected" @change="toggleSelectAllNotices">全选</el-checkbox>
            <span class="notice-selected-count">已选 {{ selectedNoticeIds.length }} 条</span>
            <el-button size="small" @click="markSelectedNoticesRead" :disabled="selectedNoticeIds.length === 0">标记已读</el-button>
            <el-button size="small" type="danger" @click="deleteSelectedNotices" :disabled="selectedNoticeIds.length === 0">删除</el-button>
          </div>

          <div class="notice-list" v-if="noticeList.length > 0">
            <div class="notice-row" v-for="item in noticeList" :key="item.id">
              <el-checkbox class="notice-row-check" :model-value="selectedNoticeIds.includes(item.id)" @change="toggleNoticeSelection(item.id)" />
              <div class="notice-row-content">
                <div class="notice-row-top">
                  <span :class="['notice-level', item.level ? item.level.toLowerCase() : 'info']">{{ item.level || 'INFO' }}</span>
                  <strong class="notice-row-title">{{ item.title }}</strong>
                  <span class="notice-row-time">{{ item.createdAt }}</span>
                </div>
                <p class="notice-row-text">{{ item.content }}</p>
                <div class="notice-row-actions">
                  <span v-if="item.isRead" class="notice-read-flag">已读</span>
                  <el-button v-else link type="primary" size="small" @click="markSingleNoticeRead(item.id)">标记已读</el-button>
                  <el-button link type="danger" size="small" @click="deleteSingleNotice(item.id)">删除</el-button>
                </div>
              </div>
            </div>
          </div>

          <div class="notice-empty" v-else>当前没有通知</div>

          <div class="pager-fluid notice-pager">
            <el-button size="small" :disabled="noticePage <= 1" @click="noticePage--;fetchNoticeList()">上一页</el-button>
            <span class="pager-txt">第 {{ noticePage }} / {{ noticeTotalPages }} 页</span>
            <el-button size="small" :disabled="noticePage >= noticeTotalPages" @click="noticePage++;fetchNoticeList()">下一页</el-button>
          </div>

          <div class="notice-create-box" v-if="user.role === 'admin'">
            <h4 class="notice-create-title">发布通知</h4>
            <div class="notice-create-grid">
              <el-input v-model="noticeForm.title" placeholder="通知标题（必填）" />
              <el-select v-model="noticeForm.level">
                <el-option label="INFO" value="INFO" />
                <el-option label="WARN" value="WARN" />
                <el-option label="ALERT" value="ALERT" />
              </el-select>
              <el-input v-model="noticeForm.content" type="textarea" placeholder="通知内容（必填）" />
              <el-button type="primary" @click="publishNotice">发布通知</el-button>
            </div>
          </div>
        </div>
      </el-drawer>
    </div>

    <div class="modal-overlay" v-if="selectedSurvey" @click.self="closeModal">
      <div class="modal-content">

        <header class="modal-header">
          <h3 class="modal-title">📄 客户档案详情</h3>
          <button class="btn-close" @click="closeModal" title="关闭">关闭</button>
        </header>

        <div class="modal-body">
          <div class="modal-grid">
            <div class="m-item full-width remarks-box focus-remarks">
              <div class="remarks-header">
                <span class="lbl block">备注：</span>
              </div>
              <textarea v-model="selectedSurvey.remarks" class="text-area-sim text-black" placeholder="先记录客户核心诉求、顾虑点、推进障碍与下一步动作..."></textarea>
            </div>

            <div class="m-item focus-card">
              <span class="lbl">项目:</span>
              <input v-model="selectedSurvey.project" class="val focus-val edit-input text-black" placeholder="未填写">
            </div>
            <div class="m-item focus-card budget-card">
              <span class="lbl">预算:</span>
              <input v-model="selectedSurvey.budget" class="val focus-val edit-input text-black" placeholder="未填写">
            </div>

            <div class="m-item"><span class="lbl">称呼:</span> <span class="val highlight-text">{{ selectedSurvey.name }}</span></div>
            <div class="m-item"><span class="lbl">状态:</span>
              <el-tag :type="surveyStatusBadge(selectedSurvey).type" size="small">{{ surveyStatusBadge(selectedSurvey).text }}</el-tag>
            </div>
            <div class="m-item phone-reveal-row">
              <span class="lbl">电话:</span>
              <span class="val text-black">{{ displayPhoneValue(selectedSurvey, revealedPhones, nowTick) }}</span>
              <button
                v-if="selectedSurvey.canRevealPhone"
                class="btn-eye"
                type="button"
                :disabled="isSelectedPhoneRevealBusy"
                @click.stop="revealSelectedPhone"
                aria-label="查看完整手机号"
                title="查看完整手机号"
              >
                <svg class="eye-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                  <path d="M2.25 12s3.75-6.75 9.75-6.75S21.75 12 21.75 12 18 18.75 12 18.75 2.25 12 2.25 12Z" />
                  <circle cx="12" cy="12" r="2.75" />
                </svg>
              </button>
            </div>
            <div class="m-item"><span class="lbl">微信:</span> <span class="val text-black">{{ selectedSurvey.wechat || '无' }}</span></div>
            <div class="m-item"><span class="lbl">城市:</span> <span class="val text-black">{{ selectedSurvey.city || '未填写' }}</span></div>
            <div class="m-item"><span class="lbl">社交账号:</span> <input v-model="selectedSurvey.socialAccount" class="val edit-input text-black" placeholder="未填写"></div>

            <div class="m-item full-width reminder-row-modal">
              <div class="reminder-copy">
                <span class="lbl">下次回访日期</span>
                <span class="reminder-help">到这天还没处理的客户，会自动进入「待回访」并在列表里标红。</span>
                <span v-if="reminderSaveMessage" :class="['reminder-status', reminderSaveState]" aria-live="polite">{{ reminderSaveMessage }}</span>
              </div>
              <input
                type="date"
                :min="todayDate"
                :disabled="reminderSaveState === 'saving'"
                @change="e => updateDate(selectedSurvey.id, e.target.value)"
                :value="stripTime(selectedSurvey.nextSurveyDate)"
                class="date-picker text-black"
              >
            </div>
          </div>

          <div class="admin-panel-fluid mt-3" v-if="user.role === 'admin'">
            <div class="admin-info">录入人: <b class="text-black">{{ selectedSurvey.ownerNickname || selectedSurvey.owner }}</b> &nbsp;|&nbsp; {{ selectedSurvey.createTime }}</div>
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

        <footer class="modal-footer modal-footer-right">
          <el-button type="primary" @click="saveSurveyDetail">保存</el-button>
        </footer>
      </div>
    </div>

  </div>
</template>

<script setup>
import { computed, ref, reactive, onMounted, onUnmounted } from 'vue'
import axios from 'axios'
import {
  buildCasdoorLoginQuery,
  buildDiscoveryUrl,
  buildTokenRequestBody,
  clearAuthTransaction,
  consumeAuthTransaction,
  createAuthTransaction,
  sanitizeReturnTo
} from './auth/oidc.js'
import { beginPhoneRevealRequest, clearPhoneRevealUiState, displayPhoneValue, finishPhoneRevealRequest } from './phonePrivacy.js'
import { stripTime, formatRevisitDate, revisitTag as revisitTagImpl, surveyStatusBadge as surveyStatusBadgeImpl } from './revisit.js'

const API = '/api'
const casdoorBaseUrl = import.meta.env.VITE_CASDOOR_ENDPOINT || import.meta.env.VITE_CASDOOR_BASE_URL || 'http://localhost:8000'
const casdoorApplicationName = import.meta.env.VITE_CASDOOR_APPLICATION_NAME || ''
const casdoorClientId = import.meta.env.VITE_CASDOOR_CLIENT_ID || 'xyyx-web'
const casdoorRedirectUri = import.meta.env.VITE_CASDOOR_REDIRECT_URI || `${window.location.origin}/auth/callback`
const casdoorScopes = import.meta.env.VITE_CASDOOR_SCOPES || 'openid profile email'
const PUBLIC_PATHS = new Set(['/login', '/auth/callback'])
const DISABLED_AUTH_PATHS = new Set(['/signup', '/register', '/forget', '/consent', '/prompt', '/account'])

const isLoggedIn = ref(false)
const user = reactive({ username: '', role: '' })
const loginForm = reactive({ username: '', password: '' })
const loginStatus = ref('')
const isLoginSubmitting = ref(false)
const isPasswordVisible = ref(false)
const view = ref('work')
const textEncoder = new TextEncoder()

// 认证改走后端下发的 HttpOnly Cookie（XYYX_AT），token 不再进入 JS。这里只对 /api 请求打开 withCredentials，
// 同源下 Cookie 本就自动携带，显式置真是为兼容跨源部署；不再手工注入 Authorization 头。
axios.interceptors.request.use((config) => {
  if (typeof config.url === 'string' && config.url.startsWith(API)) {
    config.withCredentials = true
  }
  return config
})

const isMenuOpen = ref(false)
const isSurveyFormOpen = ref(false)

const myAccount = reactive({ displayName: '', avatar: '' })
const avatarInitial = computed(() => {
  const source = myAccount.displayName || user.username || '?'
  return source.trim().charAt(0).toUpperCase()
})
const isPersonalCenterOpen = ref(false)
const isPersonalCenterSaving = ref(false)
const personalCenterMessage = ref('')
const personalCenterForm = reactive({ displayName: '' })
const isPasswordChangeSaving = ref(false)
const passwordChangeMessage = ref('')
const passwordChangeForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })

const fetchMyCasdoorAccount = async () => {
  // 前端已无 JS 可读 token，改由后端用 Cookie 里的身份服务端代取 Casdoor 昵称/头像。
  try {
    const res = await axios.get(`${API}/me/account`)
    myAccount.displayName = res.data?.displayName || ''
    myAccount.avatar = res.data?.avatar || ''
  } catch (e) {
    // 静默失败：个人中心面板会在打开时重新拉取，不影响登录主流程
  }
}

const openPersonalCenter = async () => {
  isMenuOpen.value = false
  personalCenterMessage.value = ''
  passwordChangeMessage.value = ''
  passwordChangeForm.currentPassword = ''
  passwordChangeForm.newPassword = ''
  passwordChangeForm.confirmPassword = ''
  isPersonalCenterOpen.value = true
  await fetchMyCasdoorAccount()
  personalCenterForm.displayName = myAccount.displayName || user.username
}

const closePersonalCenter = () => {
  isPersonalCenterOpen.value = false
}

const saveNickname = async () => {
  if (!personalCenterForm.displayName) {
    personalCenterMessage.value = '昵称不能为空'
    return
  }
  isPersonalCenterSaving.value = true
  personalCenterMessage.value = ''
  try {
    const res = await axios.put(`${API}/me/profile`, { displayName: personalCenterForm.displayName })
    personalCenterMessage.value = res.data
    await fetchMyCasdoorAccount()
  } catch (e) {
    personalCenterMessage.value = getApiErrorMessage(e, '昵称修改失败')
  } finally {
    isPersonalCenterSaving.value = false
  }
}

const submitOwnPasswordChange = async () => {
  const { currentPassword, newPassword, confirmPassword } = passwordChangeForm
  if (!currentPassword) return passwordChangeMessage.value = '请输入当前密码'
  if (!newPassword || newPassword.length < 6) return passwordChangeMessage.value = '新密码至少 6 位'
  if (newPassword !== confirmPassword) return passwordChangeMessage.value = '两次输入的新密码不一致'

  isPasswordChangeSaving.value = true
  passwordChangeMessage.value = ''
  try {
    const [encryptedCurrentPassword, encryptedNewPassword] = await Promise.all([
      encryptPassword(currentPassword),
      encryptPassword(newPassword)
    ])
    const res = await axios.put(`${API}/me/password`, {
      currentPassword: encryptedCurrentPassword,
      newPassword: encryptedNewPassword
    })
    passwordChangeMessage.value = res.data
    passwordChangeForm.currentPassword = ''
    passwordChangeForm.newPassword = ''
    passwordChangeForm.confirmPassword = ''
  } catch (e) {
    passwordChangeMessage.value = getApiErrorMessage(e, '密码修改失败')
  } finally {
    isPasswordChangeSaving.value = false
  }
}

const selectedSurvey = ref(null)
const reminderSaveState = ref('idle')
const reminderSaveMessage = ref('')
const resetReminderSaveState = () => {
  reminderSaveState.value = 'idle'
  reminderSaveMessage.value = ''
}
const openModal = (item) => {
  resetReminderSaveState()
  selectedSurvey.value = { ...item }
}
const closeModal = () => { selectedSurvey.value = null }
const openSurveyForm = () => { isSurveyFormOpen.value = true }
const closeSurveyForm = () => { isSurveyFormOpen.value = false }

const searchQuery = reactive({ keyword: '' })

const listStatus = ref('未处理')
const dueOnly = ref(false)
const surveys = ref([])
const pendingCount = ref(0)
const revisitTodayCount = ref(0)
const revisitOverdueCount = ref(0)
const revisitDueCount = ref(0)
const form = ref({ name: '', phone: '', wechat: '', city: '', socialAccount: '', project: '', budget: '', remarks: '', remindDays: 3 })
const page = ref(1); const totalPages = ref(1); const totalCount = ref(0)
const todayDate = new Date().toISOString().split('T')[0]
const usersList = ref([])
const userForm = ref({ username: '', password: '', nickname: '', role: 'staff' })
const userPasswordDraft = reactive({})
const orderPageSize = ref(20)
const revisitDeadlineDays = ref(3)
const isSystemSettingsOpen = ref(false)
const isSystemSettingsSaving = ref(false)
const PHONE_POLICY_ON = 'CLICK_TO_SESSION_VISIBLE'
const PHONE_POLICY_OFF = 'SINGLE_ORDER_TIMED_REVEAL'
const systemSettingsForm = reactive({
  phoneDisplayPolicy: 'CLICK_TO_SESSION_VISIBLE',
  orderPageSize: 20,
  revisitDeadlineDays: 3
})
const revealedPhones = reactive(new Map())
const nowTick = ref(Date.now())
let revealSweepTimer = null
const phoneRevealRequestState = {
  inFlightIds: reactive(new Set()),
  lastRevealAtById: reactive(new Map())
}
const isSelectedPhoneRevealBusy = computed(() => (
  selectedSurvey.value ? phoneRevealRequestState.inFlightIds.has(selectedSurvey.value.id) : false
))
const isPhonePrivacyOn = computed({
  get: () => systemSettingsForm.phoneDisplayPolicy !== PHONE_POLICY_OFF,
  set: (on) => { systemSettingsForm.phoneDisplayPolicy = on ? PHONE_POLICY_ON : PHONE_POLICY_OFF }
})

const clearPhoneRevealState = () => {
  clearPhoneRevealUiState(surveys.value, selectedSurvey.value, revealedPhones)
  nowTick.value = Date.now()
}

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
    throw new Error('当前页面不是安全上下文（非 HTTPS 且非 localhost/127.0.0.1），浏览器禁用了密码加密所需的 WebCrypto，请改用 https:// 或 http://localhost 访问')
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

const toggleMenuPanel = () => { isMenuOpen.value = !isMenuOpen.value }
const switchView = (v) => {
  view.value = v
  page.value = 1
  isMenuOpen.value = false
  if(v === 'users') fetchUsers(); else fetchData()
}

const openSystemSettings = async () => {
  if (user.role !== 'admin') return
  isMenuOpen.value = false
  try {
    const res = await axios.get(`${API}/admin/system-settings`)
    systemSettingsForm.phoneDisplayPolicy = res.data.phoneDisplayPolicy || 'CLICK_TO_SESSION_VISIBLE'
    systemSettingsForm.orderPageSize = Number(res.data.orderPageSize || 20)
    systemSettingsForm.revisitDeadlineDays = clampRevisitDeadlineDays(res.data.revisitDeadlineDays || 3)
    isSystemSettingsOpen.value = true
  } catch (e) {
    alert(getApiErrorMessage(e, '系统设置加载失败'))
  }
}

const closeSystemSettings = () => {
  isSystemSettingsOpen.value = false
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
      if (status === 401 || status === 403) return '登录失败：Casdoor Token 未通过业务系统校验'
      return `登录失败：请求异常（HTTP ${status}）`
    }
    if (error.request) return '登录失败：无法连接认证服务或业务后端，请检查 HTTPS 证书、CORS 与服务状态'
  }
  return fallback
}

const getApiErrorMessage = (error, fallback) => {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data
    if (typeof data === 'string') return data
    if (data && typeof data === 'object' && (data.message || data.code || data.requestId)) {
      const message = data.message || fallback
      const code = data.code || `HTTP ${error.response?.status || ''}`.trim()
      return data.requestId
        ? `${fallback}：${message}（${code}, requestId=${data.requestId}）`
        : `${fallback}：${message}（${code}）`
    }
    if (error.response?.status) return `${fallback}（HTTP ${error.response.status}）`
    if (error.request) return `${fallback}（无法连接后端）`
  }
  // 非网络错误（比如浏览器本身拒绝了某个 API）：把真实原因带出来，不要只显示空泛的 fallback。
  if (error instanceof Error && error.message) return `${fallback}：${error.message}`
  return fallback
}

const fetchAppSettings = async () => {
  try {
    const res = await axios.get(`${API}/app-settings`)
    orderPageSize.value = clampOrderPageSize(res.data?.orderPageSize || 20)
    revisitDeadlineDays.value = clampRevisitDeadlineDays(res.data?.revisitDeadlineDays || 3)
  } catch (e) {
    orderPageSize.value = 20
    revisitDeadlineDays.value = 3
  }
}

const clampOrderPageSize = (value) => Math.min(100, Math.max(1, Number(value) || 20))
const clampRevisitDeadlineDays = (value) => Math.min(30, Math.max(1, Number(value) || 3))

const clearAuthStorage = () => {
  // token 现在只在后端下发的 HttpOnly Cookie 里，前端无法删除它——由 POST /api/auth/logout 让其过期。
  clearAuthTransaction()
  clearPhoneRevealState()
}

axios.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      clearPhoneRevealState()
    }
    return Promise.reject(error)
  }
)

const getOidcMetadata = async () => {
  const discoveryUrl = buildDiscoveryUrl(casdoorBaseUrl, casdoorApplicationName)
  const res = await axios.get(discoveryUrl, { headers: { Accept: 'application/json' } })
  return res.data
}

const getCurrentInternalPath = () => {
  const path = `${window.location.pathname}${window.location.search}${window.location.hash}`
  if (PUBLIC_PATHS.has(window.location.pathname) || DISABLED_AUTH_PATHS.has(window.location.pathname)) return '/'
  return sanitizeReturnTo(path)
}

const getLoginReturnTo = () => {
  const url = new URL(document.location.toString())
  return sanitizeReturnTo(url.searchParams.get('returnTo') || '/')
}

const replaceRoute = (path) => {
  window.history.replaceState({}, document.title, path)
}

const replaceWithLogin = (returnTo = getCurrentInternalPath()) => {
  replaceRoute(`/login?returnTo=${encodeURIComponent(sanitizeReturnTo(returnTo))}`)
}

const getCasdoorApplication = async (transaction) => {
  const query = buildCasdoorLoginQuery({
    clientId: casdoorClientId,
    redirectUri: casdoorRedirectUri,
    scope: casdoorScopes,
    transaction
  })
  const res = await axios.get(`${casdoorBaseUrl}/api/get-app-login?${query.toString()}`, {
    withCredentials: true,
    headers: { Accept: 'application/json' }
  })
  if (res.data?.status !== 'ok' || !res.data?.data) {
    throw new Error(res.data?.msg || 'Casdoor 应用配置不可用')
  }
  return res.data.data
}

const extractCasdoorCode = (loginResult) => {
  if (loginResult?.status !== 'ok') {
    throw new Error(loginResult?.msg || '账号或密码错误')
  }
  if (loginResult.data3 || typeof loginResult.data !== 'string' || !loginResult.data) {
    throw new Error('当前账户需要 Casdoor 额外确认，请联系管理员处理')
  }
  return loginResult.data
}

const fetchCurrentUser = async () => {
  const res = await axios.get(`${API}/me`)
  return res.data
}

const enterAuthenticatedApp = async (profile, showWelcome = false) => {
  isLoggedIn.value = true
  user.username = profile.username
  user.role = profile.role
  loginForm.password = ''
  loginStatus.value = ''
  view.value = 'work'
  listStatus.value = '未处理'
  dueOnly.value = false
  await fetchAppSettings()
  await fetchPendingCount()
  await refreshNoticeData()
  fetchMyCasdoorAccount()
  if (showWelcome) {
    alert(`登录成功！\n目前有 ${pendingCount.value} 条待处理数据。`)
  }
  fetchData()
  fetchRevisitCount()
}

const handleLogin = async () => {
  if (!loginForm.username || !loginForm.password) {
    loginStatus.value = '请输入账号和密码'
    return
  }

  try {
    isLoginSubmitting.value = true
    loginStatus.value = '正在登录...'
    const transaction = await createAuthTransaction({
      cryptoApi: window.crypto,
      returnTo: getLoginReturnTo()
    })
    const application = await getCasdoorApplication(transaction)
    const query = buildCasdoorLoginQuery({
      clientId: casdoorClientId,
      redirectUri: casdoorRedirectUri,
      scope: casdoorScopes,
      transaction
    })
    // 密码以明文放进请求体直发 Casdoor /api/login：保护它的是 TLS 传输加密（生产必须 HTTPS/HSTS），
    // 不是"扮猪吃虎"——security through obscurity 不是有效控制，攻击者看到明文只会更快而非更慢。
    // Casdoor 登录没有 RSA 公钥加密；唯一可选项是组织级对称 Password Obfuscator（密钥对客户端可见，属混淆非加密），
    // 默认 Plain。真正的纵深防御应放在：强制 HTTPS、限流/锁定、反代不记录请求体，而非本层。
    const loginRes = await axios.post(
      `${casdoorBaseUrl}/api/login?${query.toString()}`,
      {
        type: 'code',
        signinMethod: 'Password',
        organization: application.organization,
        application: application.name,
        clientId: casdoorClientId,
        username: loginForm.username,
        password: loginForm.password,
        autoSignin: false,
        language: navigator.language || ''
      },
      { withCredentials: true }
    )
    const consumed = consumeAuthTransaction({ state: transaction.state })
    await completeCodeLogin(extractCasdoorCode(loginRes.data), consumed, true)
  } catch (e) {
    clearAuthStorage()
    loginForm.password = ''
    loginStatus.value = getRequestErrorMessage(e, e?.message || '登录失败，请稍后重试')
  } finally {
    isLoginSubmitting.value = false
  }
}

const handleAuthCallback = async () => {
  const url = new URL(document.location.toString())
  if (!url.searchParams.has('code') && !url.searchParams.has('error')) return false

  const error = url.searchParams.get('error')
  if (error) {
    clearAuthStorage()
    replaceWithLogin()
    loginStatus.value = `登录失败：${error}`
    return true
  }

  let transaction
  try {
    transaction = consumeAuthTransaction({ state: url.searchParams.get('state') })
  } catch {
    clearAuthStorage()
    replaceWithLogin()
    loginStatus.value = '登录状态校验失败，请重新登录'
    return true
  }

  try {
    loginStatus.value = '正在完成单点登录...'
    await completeCodeLogin(url.searchParams.get('code'), transaction, true)
  } catch (e) {
    clearAuthStorage()
    replaceWithLogin(transaction.returnTo)
    loginStatus.value = getRequestErrorMessage(e, '登录回调处理失败，请重新登录')
  }
  return true
}

const completeCodeLogin = async (code, transaction, showWelcome) => {
  const metadata = await getOidcMetadata()
  const tokenRes = await axios.post(
    metadata.token_endpoint,
    buildTokenRequestBody({
      clientId: casdoorClientId,
      redirectUri: casdoorRedirectUri,
      code,
      codeVerifier: transaction.codeVerifier
    }),
    { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }
  )

  // Pattern B（relay）：前端仍做 PKCE+换 token，但拿到后不落地到 JS，而是交给后端由其写入 HttpOnly Cookie。
  // 后端会用 JWKS 校验该 token 才建立会话；refresh_token 一律不带出、不存储。
  await axios.post(`${API}/auth/session`, { accessToken: tokenRes.data.access_token })
  clearAuthTransaction()
  replaceRoute(transaction.returnTo)
  await enterAuthenticatedApp(await fetchCurrentUser(), showWelcome)
}

const fetchPendingCount = async () => {
  const res = await axios.get(`${API}/surveys/pending-count`)
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
  try {
    const res = await axios.get(`${API}/notices/unread-count`)
    noticeUnreadCount.value = res.data.count || 0
  } catch (e) {
    noticeUnreadCount.value = 0
    console.error(getApiErrorMessage(e, '未读公告数量加载失败'))
  }
}

const fetchNoticeList = async () => {
  if (!user.username) return
  try {
    const res = await axios.get(`${API}/notices`, {
      params: {
        status: noticeStatus.value,
        page: noticePage.value,
        size: 8
      }
    })
    noticeList.value = res.data.data || []
    noticeTotalPages.value = res.data.pages || 1
    selectedNoticeIds.value = selectedNoticeIds.value.filter(id => noticeList.value.some(item => item.id === id))
  } catch (e) {
    noticeList.value = []
    noticeTotalPages.value = 1
    selectedNoticeIds.value = []
    alert(getApiErrorMessage(e, '公告加载失败，请稍后重试'))
  }
}

const fetchNoticeTicker = async () => {
  if (!user.username) return
  try {
    const res = await axios.get(`${API}/notices`, {
      params: {
        status: 'UNREAD',
        page: 1,
        size: 3
      }
    })
    noticeTickerList.value = res.data.data || []
    rebuildTickerMessages()
  } catch (e) {
    noticeTickerList.value = []
    rebuildTickerMessages()
    console.error(getApiErrorMessage(e, '公告滚动条加载失败'))
  }
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
  try {
    await axios.put(`${API}/notices/read`, {
      ids: selectedNoticeIds.value
    })
    selectedNoticeIds.value = []
    await refreshNoticeData()
  } catch (e) {
    alert(getApiErrorMessage(e, '公告标记已读失败，请稍后重试'))
  }
}

const deleteSelectedNotices = async () => {
  if (selectedNoticeIds.value.length === 0) return
  try {
    await axios.delete(`${API}/notices`, {
      data: {
        ids: selectedNoticeIds.value
      }
    })
    selectedNoticeIds.value = []
    await refreshNoticeData()
  } catch (e) {
    alert(getApiErrorMessage(e, '公告删除失败，请稍后重试'))
  }
}

const markSingleNoticeRead = async (id) => {
  try {
    await axios.put(`${API}/notices/read`, {
      ids: [id]
    })
    await refreshNoticeData()
  } catch (e) {
    alert(getApiErrorMessage(e, '公告标记已读失败，请稍后重试'))
  }
}

const deleteSingleNotice = async (id) => {
  try {
    await axios.delete(`${API}/notices`, {
      data: {
        ids: [id]
      }
    })
    await refreshNoticeData()
  } catch (e) {
    alert(getApiErrorMessage(e, '公告删除失败，请稍后重试'))
  }
}

const publishNotice = async () => {
  if (user.role !== 'admin') return alert('仅管理员可操作')
  if (!noticeForm.title.trim()) return alert('请填写通知标题')
  if (!noticeForm.content.trim()) return alert('请填写通知内容')
  try {
    const res = await axios.post(`${API}/notices`, {
      title: noticeForm.title,
      content: noticeForm.content,
      level: noticeForm.level
    })
    if (!res.data.success) return alert(res.data.message || '通知发布失败')
    noticeForm.title = ''
    noticeForm.content = ''
    noticeForm.level = 'INFO'
    await refreshNoticeData()
  } catch (e) {
    alert(getApiErrorMessage(e, '通知发布失败，请稍后重试'))
  }
}

const applySurveyResponse = (payload) => {
  const rows = Array.isArray(payload) ? payload : (payload.data || [])
  const now = Date.now()
  clearPhoneRevealState()
  surveys.value = rows.map((row) => {
    if (row?.id !== undefined && row.phoneRevealed === true && row.phoneDisplay) {
      revealedPhones.set(row.id, {
        phoneRevealed: true,
        phoneDisplay: row.phoneDisplay,
        expiresAt: row.phoneRevealExpiresAt ? Number(row.phoneRevealExpiresAt) : Number.POSITIVE_INFINITY
      })
    }
    return sanitizeSurveyItem(row)
  })
  totalPages.value = payload.pages || 1
  totalCount.value = payload.total || surveys.value.length
}

const sanitizeSurveyItem = ({
  id, tenantId, customerUuid, name, phoneDisplay, phoneMask, phoneRevealed, phoneDisplayMode, phoneDisplayPolicy, phoneRevealStatus,
  canRevealPhone,
  wechat, socialAccount, city, project,
  budget, remarks, owner, visibility, status, createTime, nextSurveyDate, sharedUsers
} = {}) => ({
  id, tenantId, customerUuid, name, phoneDisplay: phoneRevealed ? (phoneMask || '') : phoneDisplay, phoneMask, phoneRevealed, phoneDisplayMode, phoneDisplayPolicy, phoneRevealStatus,
  canRevealPhone: Boolean(canRevealPhone),
  wechat, socialAccount, city, project,
  budget, remarks, owner, visibility, status, createTime, nextSurveyDate, sharedUsers
})

const fetchData = async () => {
  try {
    const params = {
      status: dueOnly.value ? '未处理' : (listStatus.value === '全部' ? '' : listStatus.value),
      keyword: searchQuery.keyword.trim(),
      city: '',
      page: page.value,
      size: orderPageSize.value
    }
    if (dueOnly.value) params.revisit = 'due'
    const res = await axios.get(`${API}/surveys`, { params })
    applySurveyResponse(res.data)
    fetchRevisitCount()
  } catch (e) {
    alert(getApiErrorMessage(e, '回访记录加载失败，请稍后重试'))
  }
}

const fetchRevisitCount = async () => {
  try {
    const res = await axios.get(`${API}/surveys/revisit-count`)
    revisitTodayCount.value = res.data?.today || 0
    revisitOverdueCount.value = res.data?.overdue || 0
    revisitDueCount.value = res.data?.due ?? (revisitTodayCount.value + revisitOverdueCount.value)
  } catch (e) {
    console.error(getApiErrorMessage(e, '待回访统计加载失败'))
  }
}

const selectRevisitTab = () => {
  dueOnly.value = true
  listStatus.value = '未处理'
  page.value = 1
  fetchData()
}

const selectListStatus = (status) => {
  dueOnly.value = false
  listStatus.value = status
  doSearch()
}

const doSearch = () => { page.value = 1; fetchData(); }

const refreshAll = () => { fetchData(); fetchPendingCount(); refreshNoticeData(); }

const sweepRevealedPhones = () => {
  nowTick.value = Date.now()
  for (const [id, value] of revealedPhones.entries()) {
    if (!value || value.expiresAt <= nowTick.value) {
      revealedPhones.delete(id)
    }
  }
}

const revealSelectedPhone = async () => {
  if (!selectedSurvey.value) return
  const surveyId = selectedSurvey.value.id
  if (!beginPhoneRevealRequest(phoneRevealRequestState, surveyId)) return

  try {
    const res = await axios.post(`${API}/surveys/${surveyId}/phone/reveal`, {})
    if (!res.data?.phoneDisplay) throw new Error('后端未返回手机号展示值')
    const expiresAt = res.data.phoneRevealExpiresAt
      ? Number(res.data.phoneRevealExpiresAt)
      : (res.data.expiresInSeconds ? Date.now() + Number(res.data.expiresInSeconds) * 1000 : Number.POSITIVE_INFINITY)
    const revealEntry = { phoneRevealed: true, phoneDisplay: res.data.phoneDisplay, expiresAt }
    revealedPhones.set(surveyId, revealEntry)
    if (res.data.sessionActivated) {
      // ON: whole login session revealed; hide the eye, the list now shows full numbers.
      selectedSurvey.value.canRevealPhone = false
      selectedSurvey.value.phoneRevealStatus = res.data.phoneRevealStatus || 'SESSION_ACTIVATED'
    } else {
      // OFF: only this order, for 300s. Keep the eye so it can be re-revealed after expiry.
      selectedSurvey.value.phoneRevealStatus = res.data.phoneRevealStatus || 'SINGLE_ORDER_REVEALED'
    }
    // Refresh so the server-side grant (ON: whole session / OFF: this order's 300s window) drives the list.
    await fetchData()
    revealedPhones.set(surveyId, revealEntry)
    sweepRevealedPhones()
  } catch (e) {
    if (axios.isAxiosError(e) && e.response?.status === 401) clearPhoneRevealState()
    alert(getApiErrorMessage(e, '查看完整手机号失败'))
  } finally {
    finishPhoneRevealRequest(phoneRevealRequestState, surveyId)
  }
}

const submitAdd = async () => {
  if(!form.value.name) return alert('请填写称呼');
  if(!confirm(`确认将客户 [${form.value.name}] 的信息录入系统吗？`)) return;

  const res = await axios.post(`${API}/surveys`, form.value)
  if(res.data.success) {
    alert('录入成功！');
    form.value = { name: '', phone: '', wechat: '', city: '', socialAccount: '', project: '', budget: '', remarks: '', remindDays: 3 };
    closeSurveyForm()
    refreshAll()
  } else { alert(res.data.message); }
}

const processTask = async (id) => {
  if(confirm('⚠️ 确认跟进并完成此数据？\n完成后该记录将流转至“已处理”列表。')) {
    await axios.put(`${API}/surveys/${id}/process`);
    refreshAll()
  }
}

const saveSurveyDetail = async () => {
  if(!selectedSurvey.value) return;
  await axios.put(`${API}/surveys/${selectedSurvey.value.id}/remarks`, {
    remarks: selectedSurvey.value.remarks,
    project: selectedSurvey.value.project,
    budget: selectedSurvey.value.budget,
    socialAccount: selectedSurvey.value.socialAccount
  });
  alert('保存成功！');
  closeModal();
  fetchData();
}

const updateDate = async (id, newDate) => {
  if (!newDate) return
  reminderSaveState.value = 'saving'
  reminderSaveMessage.value = '保存中...'
  try {
    const res = await axios.put(`${API}/surveys/${id}/date`, { date: newDate })
    const savedDate = res.data?.nextSurveyDate || newDate
    if(selectedSurvey.value) selectedSurvey.value.nextSurveyDate = savedDate
    reminderSaveState.value = 'saved'
    const savedLabel = formatRevisitDate(savedDate)
    reminderSaveMessage.value = savedLabel ? `已保存，下次回访 ${savedLabel}` : '已保存'
    fetchData()
  } catch (e) {
    reminderSaveState.value = 'error'
    reminderSaveMessage.value = getApiErrorMessage(e, '保存失败，请重试')
  }
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
    const res = await axios.get(`${API}/users`);
    usersList.value = res.data;
    res.data.forEach((u) => {
      if (!(u.id in userPasswordDraft)) userPasswordDraft[u.id] = '';
    });
  } catch (e) {
    alert(getApiErrorMessage(e, '员工列表加载失败'));
  }
}

const saveSystemSettings = async () => {
  if (user.role !== 'admin') return alert('仅管理员可操作')
  const nextPageSize = clampOrderPageSize(systemSettingsForm.orderPageSize)
  const nextRevisitDeadlineDays = clampRevisitDeadlineDays(systemSettingsForm.revisitDeadlineDays)
  try {
    isSystemSettingsSaving.value = true
    const res = await axios.put(`${API}/admin/system-settings`, {
      phoneDisplayPolicy: systemSettingsForm.phoneDisplayPolicy,
      orderPageSize: nextPageSize,
      revisitDeadlineDays: nextRevisitDeadlineDays
    })
    systemSettingsForm.phoneDisplayPolicy = res.data.phoneDisplayPolicy || systemSettingsForm.phoneDisplayPolicy
    systemSettingsForm.orderPageSize = clampOrderPageSize(res.data.orderPageSize || nextPageSize)
    systemSettingsForm.revisitDeadlineDays = clampRevisitDeadlineDays(res.data.revisitDeadlineDays || nextRevisitDeadlineDays)
    orderPageSize.value = systemSettingsForm.orderPageSize
    revisitDeadlineDays.value = systemSettingsForm.revisitDeadlineDays
    alert('系统设置已保存。')
    closeSystemSettings()
    page.value = 1
    await fetchData()
  } catch (e) {
    alert(getApiErrorMessage(e, '系统设置保存失败'))
  } finally {
    isSystemSettingsSaving.value = false
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
      nickname: userForm.value.nickname,
      role: userForm.value.role
    });
    alert(res.data);
    userForm.value = { username: '', password: '', nickname: '', role: 'staff' };
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
      password: encryptedPassword
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
    const res = await axios.delete(`${API}/users/${staff.id}`);
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

const revisitTag = (item) => revisitTagImpl(item, todayDate, revisitDeadlineDays.value)
const surveyStatusBadge = (item) => surveyStatusBadgeImpl(item, todayDate, revisitDeadlineDays.value)
const logout = async () => {
  if(confirm('确定要安全退出系统吗？')) {
    // 先趁 token 仍有效通知后端把它加入拒绝名单（登出即撤销），必须 await——否则下面 clearAuthStorage
    // 会先清掉 sessionStorage，异步请求拦截器就取不到 token，后端也就无从识别要撤销哪个会话。
    try { await axios.post(`${API}/auth/logout`) } catch (e) { /* 撤销失败不阻断登出 */ }
    void axios.post(`${casdoorBaseUrl}/api/logout`, null, { withCredentials: true }).catch(() => {})
    clearAuthStorage()
    isLoggedIn.value = false
    user.username = ''
    user.role = ''
    loginForm.password = ''
    loginStatus.value = ''
    isMenuOpen.value = false
    isNoticeCenterOpen.value = false
    noticeList.value = []
    noticeTickerList.value = []
    tickerMessages.value = []
    noticeUnreadCount.value = 0
    selectedNoticeIds.value = []
    replaceWithLogin('/')
  }
}

onMounted(async () => {
  window.addEventListener('click', handleGlobalClick)
  revealSweepTimer = window.setInterval(sweepRevealedPhones, 1000)
  if (DISABLED_AUTH_PATHS.has(window.location.pathname)) {
    replaceWithLogin('/')
  }
  if (window.location.pathname === '/auth/callback') {
    await handleAuthCallback()
    return
  }
  // 会话在后端 HttpOnly Cookie 里，前端无从直接检查——直接探测 GET /api/me：成功即已登录，401 则回登录页。
  try {
    loginStatus.value = '正在恢复登录状态...'
    await enterAuthenticatedApp(await fetchCurrentUser(), false)
    if (window.location.pathname === '/login') {
      replaceRoute(getLoginReturnTo())
    }
  } catch (e) {
    loginStatus.value = ''
    if (!PUBLIC_PATHS.has(window.location.pathname)) {
      replaceWithLogin()
    }
  }
})
onUnmounted(() => {
  window.removeEventListener('click', handleGlobalClick)
  if (revealSweepTimer) window.clearInterval(revealSweepTimer)
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
  min-height: 100dvh;
  width: 100%;
  padding: 24px 16px;
  display: grid;
  place-items: center;
  background: #f4f7fc;
}

.login-shell {
  width: min(100%, 430px);
  border: 1px solid #dbe4ef;
  border-radius: 16px;
  padding: clamp(28px, 5vw, 40px);
  background: #ffffff;
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.1);
  animation: rise-in 0.32s ease both;
}

.login-header {
  margin-bottom: 26px;
  text-align: center;
}

.login-kicker {
  margin: 0 0 10px;
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
  letter-spacing: 0;
}

.login-form {
  width: 100%;
  display: grid;
  gap: 16px;
}

.login-field {
  display: grid;
  gap: 8px;
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.4;
}

.login-input {
  width: 100%;
  height: 46px;
  padding: 0 13px;
  border: 1px solid #d2dce8;
  border-radius: 10px;
  background: #ffffff;
  font-size: 15px;
  outline: none;
  transition: border-color 0.16s ease, box-shadow 0.16s ease;
}

.login-input::placeholder {
  color: #94a3b8;
}

.password-control {
  position: relative;
}

.password-control .login-input {
  padding-right: 64px;
}

.password-toggle {
  position: absolute;
  top: 50%;
  right: 8px;
  transform: translateY(-50%);
  width: 38px;
  height: 32px;
  border: 0;
  border-radius: 8px;
  padding: 0;
  background: transparent;
  color: #1d4ed8;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.password-toggle:hover {
  background: #eff6ff;
}

.password-toggle:focus-visible {
  outline: 2px solid #2563eb;
  outline-offset: 2px;
}

.password-toggle-icon {
  width: 21px;
  height: 21px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.login-input:focus {
  border-color: #1d4ed8;
  box-shadow: 0 0 0 3px rgba(29, 78, 216, 0.12);
}

.login-status {
  width: 100%;
  min-height: 20px;
  color: #b91c1c;
  font-size: 13px;
  line-height: 1.35;
  text-align: center;
}

.login-footer {
  margin-top: 24px;
  padding-top: 18px;
  border-top: 1px solid #edf2f7;
  color: #94a3b8;
  font-size: 13px;
  line-height: 1.5;
  text-align: center;
}

.btn-login-action:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.mega-title {
  margin: 0;
  font-size: clamp(28px, 4vw, 36px);
  line-height: 1.18;
  letter-spacing: 0;
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
.btn-action {
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
  background: var(--brand);
  box-shadow: 0 10px 18px rgba(29, 78, 216, 0.18);
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
  min-height: 48px;
  padding: 12px 14px;
  font-size: 15px;
  font-weight: 700;
  border-radius: 10px;
  background: #1d4ed8;
  box-shadow: 0 10px 18px rgba(29, 78, 216, 0.22);
}

.btn-login-action:hover {
  transform: translateY(-1px);
  filter: brightness(1.03);
}

.btn-login-action:active {
  transform: translateY(0);
  box-shadow: 0 6px 12px rgba(29, 78, 216, 0.2);
}

.dashboard {
  min-height: 100vh;
  background: #f4f7fc;
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
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid #dbe4ef;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
}

.taskbar-left,
.taskbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.taskbar-left {
  min-width: 0;
}

.menu-trigger-wrap {
  position: relative;
}

.hamburger-btn {
  min-width: 52px;
  height: 36px;
  border-radius: 10px;
  border: 1px solid #d2dce8;
  background: #ffffff;
  color: #1d4ed8;
  cursor: pointer;
  font-size: 13px;
  font-weight: 700;
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
  border: 1px solid #dbe4ef;
  background: #ffffff;
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.12);
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
  color: #334155;
  background: transparent;
  font-size: 14px;
  font-weight: 600;
  padding: 10px 12px;
  cursor: pointer;
}

.menu-popover button:hover,
.menu-popover button.active {
  color: #1d4ed8;
  background: #eff6ff;
}

.brand-text {
  color: #0f172a;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0;
  white-space: nowrap;
}

.desktop-nav {
  display: none;
  align-items: center;
  gap: 6px;
  margin-left: 8px;
  padding: 4px;
  border-radius: 12px;
  background: #f1f5f9;
}

.desktop-nav button {
  border: 0;
  border-radius: 9px;
  padding: 8px 14px;
  color: #475569;
  background: transparent;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}

.desktop-nav button:hover,
.desktop-nav button.active {
  color: #1d4ed8;
  background: #ffffff;
  box-shadow: 0 3px 8px rgba(15, 23, 42, 0.1);
}

.avatar-btn {
  border: 0;
  background: transparent;
  padding: 0;
  cursor: pointer;
  line-height: 0;
}

.avatar-circle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 999px;
  background: #1d4ed8;
  color: #ffffff;
  font-size: 15px;
  font-weight: 700;
}

.avatar-btn:hover .avatar-circle {
  filter: brightness(1.08);
}

.modal-divider {
  border: 0;
  border-top: 1px solid #e2e8f0;
  margin: 18px 0;
}

.modal-status {
  font-size: 13px;
  color: #1d4ed8;
  margin: 8px 0;
}

.btn-notice-icon {
  position: relative;
  min-width: 44px;
  height: 36px;
  border: 1px solid #d2dce8;
  background: #ffffff;
  color: #1d4ed8;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.btn-logout-icon {
  border: 1px solid #fecaca;
  background: #fff;
  color: #b91c1c;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0;
  padding: 7px 12px;
  cursor: pointer;
  white-space: nowrap;
}

.main-body {
  margin-top: 66px;
  padding: 16px;
  width: 100%;
  max-width: 1320px;
  margin-left: auto;
  margin-right: auto;
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
  background: #ffffff;
  color: #0f172a;
  border: 1px solid var(--line);
  display: flex;
  flex-direction: column;
  gap: 12px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
  animation: rise-in 0.5s ease both;
}

.white-text {
  margin: 0;
  color: #0f172a;
  font-size: clamp(23px, 3vw, 30px);
  letter-spacing: 0;
}

.header-tools {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.btn-refresh {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  padding: 0;
  flex: 0 0 auto;
  color: #64748b;
  border: none;
  border-radius: 50%;
  background: #f1f5f9;
  box-shadow: none;
}

.btn-refresh:hover {
  color: #1d4ed8;
  background: #e2e8f0;
}

.btn-refresh:active svg {
  transform: rotate(180deg);
  transition: transform 0.3s ease;
}

.tab-group-container {
  flex: 1;
  min-width: 220px;
}

.header-search-group {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  flex: 1 1 420px;
  min-width: 280px;
  max-width: 560px;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1 1 260px;
  min-width: 200px;
}

.header-search-group .search-input {
  flex: 1;
  min-width: 160px;
}

.tab-group {
  display: inline-flex;
  flex-wrap: wrap;
  border-radius: 12px;
  background: #f1f5f9;
  padding: 3px;
}

.tab-group button {
  border: 0;
  padding: 8px 13px;
  border-radius: 9px;
  font-size: 13px;
  font-weight: 700;
  color: #64748b;
  background: transparent;
  cursor: pointer;
}

.tab-group button.active {
  color: #1d4ed8;
  background: #fff;
  box-shadow: 0 3px 8px rgba(15, 23, 42, 0.1);
}

.form-card,
.table-container {
  border-radius: 16px;
  border: 1px solid var(--line);
  background: var(--bg-card);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
  animation: rise-in 0.42s ease both;
}

.search-input {
  flex: 1;
  min-width: 0;
  border: 1px solid #d2dce8;
  border-radius: 12px;
  background: #fff;
  color: #0f172a;
  padding: 11px 12px;
  font-size: 14px;
}

.btn-create-record {
  flex: 0 0 auto;
  width: fit-content;
  max-width: 100%;
  margin-left: auto;
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
  background: #1d4ed8 !important;
  color: #ffffff !important;
  box-shadow: 0 10px 18px rgba(29, 78, 216, 0.18) !important;
}

.form-grid-fluid > .btn-add-fluid {
  border: 1px solid #1d4ed8 !important;
  background: #1d4ed8 !important;
  color: #ffffff !important;
  box-shadow: 0 10px 18px rgba(29, 78, 216, 0.18) !important;
}

.btn-add-fluid.dark {
  background: #0f172a !important;
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
  width: 160px;
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

.bulk-policy-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 12px 0;
  color: #1f2937;
  font-size: 0.95rem;
}

.policy-subtitle {
  margin-top: 4px;
  color: #667085;
  font-size: 0.8rem;
}

.phone-policy-modal {
  max-width: 720px;
}

.policy-warning {
  margin: 0 0 16px;
  color: #475467;
  line-height: 1.6;
}

.policy-field {
  display: grid;
  gap: 6px;
  color: #344054;
  font-size: 0.92rem;
}

.policy-field select,
.policy-field input {
  width: 100%;
}

/* "手机号隐私设置" 滑动开关：ON=滑左/绿底，OFF=滑右/红底 */
.privacy-toggle {
  position: relative;
  justify-self: start;
  width: 76px;
  height: 30px;
  border: 0;
  border-radius: 999px;
  padding: 0;
  cursor: pointer;
  transition: background-color 0.2s ease;
}
.privacy-toggle.is-on { background: #16a34a; }
.privacy-toggle.is-off { background: #dc2626; }
.privacy-toggle-knob {
  position: absolute;
  top: 3px;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.3);
  transition: left 0.2s ease;
}
.privacy-toggle.is-on .privacy-toggle-knob { left: 3px; }
.privacy-toggle.is-off .privacy-toggle-knob { left: calc(100% - 27px); }
.privacy-toggle-text {
  position: absolute;
  top: 0;
  line-height: 30px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: #fff;
}
.privacy-toggle.is-on .privacy-toggle-text { right: 12px; }
.privacy-toggle.is-off .privacy-toggle-text { left: 12px; }

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

.btn-eye {
  flex: 0 0 auto;
  width: 32px;
  height: 32px;
  border: 1px solid #93c5fd;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #1d4ed8;
  background: #eff6ff;
  cursor: pointer;
}

.btn-eye:hover {
  background: #dbeafe;
}

.btn-eye:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn-eye:focus-visible {
  outline: 2px solid #2563eb;
  outline-offset: 2px;
}

.eye-icon {
  width: 16px;
  height: 16px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
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

.amber-tag {
  margin-top: 6px;
  display: inline-block;
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 12px;
  font-weight: 700;
  color: #92400e;
  background: #fef3c7;
}

.revisit-summary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 12px;
  padding: 8px 14px;
  border: 1px solid #fcd9b6;
  border-radius: 12px;
  background: #fff7ed;
  color: #9a3412;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.revisit-summary:hover {
  border-color: #f59e0b;
  background: #ffedd5;
}

.revisit-summary.active {
  border-color: #f59e0b;
  background: #ffedd5;
}

.revisit-summary-sep {
  color: #c2410c;
  opacity: 0.6;
}

.revisit-summary-overdue {
  color: #b91c1c;
}

.revisit-summary-empty {
  border-color: var(--line);
  background: transparent;
  color: var(--ink-sub);
  font-weight: 500;
  cursor: default;
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

.phone-reveal-row {
  align-items: center;
  gap: 8px;
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

.owner-nickname-line {
  margin-top: 4px;
  font-size: 12px;
}

.workload-cell {
  white-space: nowrap;
}

.staff-nickname {
  margin-top: 2px;
  font-size: 12px;
  color: #64748b;
}

.count-pill {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 700;
  color: #475569;
  background: #eef2f7;
}

.count-pill + .count-pill {
  margin-left: 6px;
}

.count-pill-danger {
  color: #b91c1c;
  background: #fee2e2;
}

.empty-state {
  text-align: center;
  color: #94a3b8;
  padding: 34px !important;
}

.notice-center-body {
  padding: 14px 18px 18px;
  overflow-y: auto;
  display: grid;
  gap: 12px;
}

.notice-filter-tabs {
  display: inline-flex;
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

.notice-selected-count {
  color: #475569;
  font-size: 13px;
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

.create-survey-modal {
  width: min(720px, 100%);
}

.create-survey-form {
  padding: 18px;
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
  min-width: 52px;
  min-height: 32px;
  padding: 0 10px;
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

.edit-input {
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  padding: 4px 8px;
  background: #fff;
  flex: 1;
  min-width: 0;
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
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.reminder-row-modal .lbl {
  color: #0f172a;
}

.reminder-copy {
  display: grid;
  gap: 3px;
  min-width: 220px;
}

.reminder-help,
.reminder-status {
  font-size: 12px;
  line-height: 1.4;
}

.reminder-help {
  color: #64748b;
}

.reminder-status.saved {
  color: #047857;
}

.reminder-status.error {
  color: #b91c1c;
}

.reminder-status.saving {
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

.modal-footer-right {
  display: flex;
  justify-content: flex-end;
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
  background: #ffffff;
  color: #1d4ed8;
  font-weight: 700;
  border-radius: 10px;
  padding: 8px 13px;
  cursor: pointer;
  transition: transform 0.16s ease, filter 0.2s ease;
}

.pager-fluid button:hover:not(:disabled) {
  transform: translateY(-1px);
  background: #eff6ff;
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

@media (min-width: 901px) {
  .mobile-menu {
    display: none;
  }

  .desktop-nav {
    display: flex;
  }

  .main-body {
    padding: 22px 30px;
  }

  .hide-on-mobile {
    display: inline-flex;
  }

  .page-header {
    display: grid;
    grid-template-columns: auto minmax(320px, 560px) auto;
    align-items: center;
    column-gap: 12px;
  }

  .page-header .white-text {
    justify-self: start;
  }

  .page-header .header-search-group {
    justify-self: stretch;
  }

  .page-header .header-tools {
    justify-self: end;
  }
}

@media (max-width: 900px) {
  .hide-on-mobile {
    display: none;
  }

  .taskbar-left,
  .taskbar-right {
    gap: 8px;
  }

  .brand-text {
    max-width: 42vw;
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: 15px;
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
