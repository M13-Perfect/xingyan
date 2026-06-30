import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  beginPhoneRevealRequest,
  clearPhoneRevealUiState,
  displayPhoneValue,
  finishPhoneRevealRequest
} from './phonePrivacy.js'

test('survey search always uses the surveys endpoint and surfaces failures', () => {
  const app = readFileSync(new URL('./App.vue', import.meta.url), 'utf8')

  assert.match(app, /axios\.get\(`\$\{API\}\/surveys`/)
  assert.match(app, /keyword:\s*searchQuery\.keyword/)
  assert.match(app, /catch\s*\(e\)/)
  assert.doesNotMatch(app, /customers\/search\/phone/)
  assert.doesNotMatch(app, /classifyPhoneSearchInput/)
})

test('phone privacy UI uses only approved phone response fields', () => {
  const app = readFileSync(new URL('./App.vue', import.meta.url), 'utf8')

  assert.doesNotMatch(app, /window\.prompt/)
  assert.doesNotMatch(app, /localStorage/)
  assert.doesNotMatch(app, /indexedDB|IndexedDB/)
  assert.doesNotMatch(app, /sessionStorage\.setItem\([^)]*phone/i)
  assert.doesNotMatch(app, /\b(phoneHash|phone_hash|ciphertext|phoneCiphertext|phoneIv|phoneTag)\b/)
  assert.doesNotMatch(app, /\b(phone_ciphertext|phone_iv|phone_tag)\b/)
  assert.match(app, /phoneDisplay/)
  assert.match(app, /phoneMask/)
  assert.match(app, /phoneRevealed/)
  assert.match(app, /phoneDisplayMode/)
  assert.match(app, /phoneRevealStatus/)
})

test('single reveal posts an empty body to the survey reveal endpoint', () => {
  const app = readFileSync(new URL('./App.vue', import.meta.url), 'utf8')

  assert.match(app, /axios\.post\(`\$\{API\}\/surveys\/\$\{surveyId\}\/phone\/reveal`,\s*\{\s*\}\)/)
  assert.doesNotMatch(app, /customers\/\$\{[^}]+}\/phone\/reveal/)
})

test('employee frontend does not expose self-service session reveal toggle', () => {
  const app = readFileSync(new URL('./App.vue', import.meta.url), 'utf8')

  assert.doesNotMatch(app, /phone-reveal-session\/status/)
  assert.doesNotMatch(app, /phone-reveal-session\/enable/)
  assert.doesNotMatch(app, /phone-reveal-session\/disable/)
  assert.doesNotMatch(app, /phone-session-toggle/)
  assert.doesNotMatch(app, /本次登录完整手机号显示|本次登录显示完整手机号/)
})

test('admin system settings modal replaces employee phone policy controls', () => {
  const app = readFileSync(new URL('./App.vue', import.meta.url), 'utf8')

  assert.match(app, /系统设置/)
  assert.match(app, /openSystemSettings/)
  assert.match(app, /isSystemSettingsOpen/)
  assert.match(app, /admin\/system-settings/)
  assert.match(app, /app-settings/)
  assert.match(app, /CLICK_TO_SESSION_VISIBLE/)
  assert.match(app, /单页显示订单数量/)
  assert.doesNotMatch(app, /openPhonePolicyModal/)
  assert.doesNotMatch(app, /openBatchPhonePolicyModal/)
  assert.doesNotMatch(app, /updateEmployeePhonePolicy/)
  assert.doesNotMatch(app, /batchUpdateEmployeePhonePolicy/)
  assert.doesNotMatch(app, /admin\/users\/\$\{staff\.id\}\/phone-display-policy/)
  assert.doesNotMatch(app, /admin\/users\/phone-display-policy\/batch/)
  assert.doesNotMatch(app, /手机号显示策略/)
  assert.doesNotMatch(app, /批量设置手机号策略/)
  assert.doesNotMatch(app, /设置策略/)
  assert.doesNotMatch(app, /隐藏时间/)
  assert.doesNotMatch(app, /单页完整显示上限/)
})

test('single reveal displays backend json error message code and request id', () => {
  const app = readFileSync(new URL('./App.vue', import.meta.url), 'utf8')

  assert.match(app, /data\.message/)
  assert.match(app, /data\.code/)
  assert.match(app, /data\.requestId/)
  assert.match(app, /requestId=\$\{data\.requestId\}/)
  assert.match(app, /查看完整手机号失败/)
})

test('single reveal success refreshes current list for session-wide visibility', () => {
  const app = readFileSync(new URL('./App.vue', import.meta.url), 'utf8')

  assert.match(app, /const revealSelectedPhone = async \(\) =>[\s\S]*await fetchData\(\)/)
  assert.match(app, /sessionActivated/)
})

test('phone privacy setting renders ON/OFF slide toggle and drops masked-only dropdown', () => {
  const app = readFileSync(new URL('./App.vue', import.meta.url), 'utf8')

  assert.match(app, /手机号隐私设置/)
  assert.match(app, /privacy-toggle/)
  assert.match(app, /isPhonePrivacyOn/)
  assert.match(app, /SINGLE_ORDER_TIMED_REVEAL/)
  assert.match(app, /is-on/)
  assert.match(app, /is-off/)
  assert.match(app, /'ON' : 'OFF'/)
  // OFF reveal honors the server 300s window via per-row expiry.
  assert.match(app, /phoneRevealExpiresAt/)
  assert.match(app, /expiresInSeconds/)
  // The old "仅脱敏" dropdown and its options array are gone.
  assert.doesNotMatch(app, /仅脱敏/)
  assert.doesNotMatch(app, /SYSTEM_PHONE_POLICY_OPTIONS/)
})

test('next follow-up date UI explains real behavior and reports save state', () => {
  const app = readFileSync(new URL('./App.vue', import.meta.url), 'utf8')

  assert.match(app, /下次回访日期/)
  assert.match(app, /到这天还没处理的客户，会自动进入「待回访」并在列表里标红/)
  assert.match(app, /reminderSaveState/)
  assert.match(app, /已保存，下次回访 \$\{savedLabel\}/)
  assert.match(app, /getApiErrorMessage\(e, '保存失败，请重试'\)/)
  assert.doesNotMatch(app, /到期后列表会标记为需复访/)
  assert.doesNotMatch(app, /提醒日期已保存/)
  assert.doesNotMatch(app, /提醒日期保存失败/)
  assert.doesNotMatch(app, /⏱️ 设置下次提醒/)
})

test('revisit list tags distinguish today vs overdue follow-ups', () => {
  const app = readFileSync(new URL('./App.vue', import.meta.url), 'utf8')

  assert.match(app, /今日回访/)
  assert.match(app, /已逾期 \$\{days\} 天/)
  assert.match(app, /revisitTag/)
  assert.match(app, /amber-tag/)
  assert.doesNotMatch(app, /需复访/)
})

test('revisit filter tab and summary entry drive the due query', () => {
  const app = readFileSync(new URL('./App.vue', import.meta.url), 'utf8')

  assert.match(app, /待回访/)
  assert.match(app, /surveys\/revisit-count/)
  assert.match(app, /params\.revisit = 'due'/)
  assert.match(app, /const fetchRevisitCount = async \(\) =>/)
  assert.match(app, /revisitTodayCount/)
  assert.match(app, /revisitOverdueCount/)
  assert.match(app, /revisitDueCount/)
  assert.match(app, /今日待回访/)
})

test('401 and survey refresh use centralized map-only phone cleanup', () => {
  const app = readFileSync(new URL('./App.vue', import.meta.url), 'utf8')

  assert.match(app, /const clearPhoneRevealState = \(\) =>/)
  assert.match(app, /response\?\.status === 401\)[\s\S]{0,80}clearPhoneRevealState\(\)/)
  assert.match(app, /const applySurveyResponse = \(payload\) =>[\s\S]{0,160}clearPhoneRevealState\(\)/)
  assert.doesNotMatch(app, /Object\.assign\(selectedSurvey\.value/)
  assert.doesNotMatch(app, /Object\.assign\(row/)
  assert.doesNotMatch(app, /revealedPhones\.clear\(\)/)
})

test('displayPhoneValue never falls back to stale full phone after memory reveal is gone', () => {
  const now = 1_000
  const item = { id: 7, phoneRevealed: true, phoneDisplay: '13912345678', phoneMask: '139****5678' }
  const revealed = new Map([[7, { phoneRevealed: true, phoneDisplay: '13912345678', expiresAt: now + 60_000 }]])

  assert.equal(displayPhoneValue(item, revealed, now), '13912345678')
  revealed.clear()
  assert.equal(displayPhoneValue(item, revealed, now), '139****5678')

  assert.equal(
    displayPhoneValue({ id: 8, phoneRevealed: false, phoneDisplay: '13912345678', phoneMask: '139****5678', phoneDisplayMode: 'FULL' }, new Map(), now),
    '139****5678'
  )
})

test('clearPhoneRevealUiState drops memory reveal and downgrades rows to masked values', () => {
  const rows = [
    { id: 7, phoneRevealed: true, phoneDisplay: '13912345678', phoneMask: '139****5678', phoneDisplayMode: 'FULL', phoneRevealStatus: 'ENABLED' }
  ]
  const selected = { id: 7, phoneRevealed: true, phoneDisplay: '13912345678', phoneMask: '139****5678', phoneDisplayMode: 'FULL', phoneRevealStatus: 'ENABLED' }
  const revealed = new Map([[7, { phoneRevealed: true, phoneDisplay: '13912345678', expiresAt: 61_000 }]])

  clearPhoneRevealUiState(rows, selected, revealed)

  assert.equal(revealed.size, 0)
  assert.equal(rows[0].phoneDisplay, '139****5678')
  assert.equal(rows[0].phoneRevealed, false)
  assert.equal(selected.phoneDisplay, '139****5678')
  assert.equal(selected.phoneRevealed, false)
})

test('single reveal request is single-flight and throttled for five seconds', () => {
  const state = { inFlightIds: new Set(), lastRevealAtById: new Map() }

  assert.equal(beginPhoneRevealRequest(state, 7, 1_000), true)
  assert.equal(beginPhoneRevealRequest(state, 7, 1_001), false)
  finishPhoneRevealRequest(state, 7)
  assert.equal(beginPhoneRevealRequest(state, 7, 5_999), false)
  assert.equal(beginPhoneRevealRequest(state, 7, 6_000), true)
})
