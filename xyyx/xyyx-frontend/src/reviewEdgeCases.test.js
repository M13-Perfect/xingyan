// 补充用例：只覆盖现有 3 个测试文件尚未触及的分支（过期回落、空 id、非字符串 returnTo、
// 非法日期、默认时限参数、登录 query 不泄露 client_secret）。纯函数、无框架、node --test 直接跑。
import test from 'node:test'
import assert from 'node:assert/strict'

import { displayPhoneValue, beginPhoneRevealRequest, finishPhoneRevealRequest } from './phonePrivacy.js'
import { formatRevisitDate, revisitTag } from './revisit.js'
import { sanitizeReturnTo, buildCasdoorLoginQuery, buildDiscoveryUrl } from './auth/oidc.js'

test('displayPhoneValue 回落：揭示条目过期或缺失时不再显示完整号', () => {
  const item = { id: 1, phoneRevealed: true, phoneDisplay: '13900000000', phoneMask: '139****0000' }
  const map = new Map([[1, { phoneRevealed: true, phoneDisplay: '13900000000', expiresAt: 1000 }]])
  // 未过期
  assert.equal(displayPhoneValue(item, map, 999), '13900000000')
  // 到点即过期（expiresAt > now 为 false）→ 回落到掩码
  assert.equal(displayPhoneValue(item, map, 1000), '139****0000')
  // 无 map 条目且无掩码 → '无'
  assert.equal(displayPhoneValue({ id: 2, phoneRevealed: true }, new Map(), 5), '无')
})

test('beginPhoneRevealRequest 拒绝空 id，正常 id 单飞', () => {
  const state = { inFlightIds: new Set(), lastRevealAtById: new Map() }
  assert.equal(beginPhoneRevealRequest(state, null, 0), false)
  assert.equal(beginPhoneRevealRequest(state, undefined, 0), false)
  assert.equal(beginPhoneRevealRequest(state, 9, 0), true)
  assert.equal(beginPhoneRevealRequest(state, 9, 1), false) // 在飞行中
  finishPhoneRevealRequest(state, 9)
})

test('sanitizeReturnTo 非字符串回落，保留内部锚点/查询', () => {
  assert.equal(sanitizeReturnTo(null), '/')
  assert.equal(sanitizeReturnTo(undefined), '/')
  assert.equal(sanitizeReturnTo(123), '/')
  assert.equal(sanitizeReturnTo('/work#tab'), '/work#tab')
  assert.equal(sanitizeReturnTo('/work?p=1'), '/work?p=1')
  assert.equal(sanitizeReturnTo('/%5Cevil'), '/') // 编码反斜杠解码后被拦
})

test('formatRevisitDate 对非法/缺失日期返回空串', () => {
  assert.equal(formatRevisitDate(''), '')
  assert.equal(formatRevisitDate('garbage'), '')
  assert.equal(formatRevisitDate('2026-00-15'), '') // 月份为 0
  assert.equal(formatRevisitDate('2026-07-05T00:00:00'), '7月5日') // ISO T 格式
})

test('revisitTag 省略第三参时默认时限为 3 天', () => {
  const tag = revisitTag({ status: '未处理', nextSurveyDate: '2026-07-01' }, '2026-07-05')
  assert.equal(tag.type, 'overdue')
  assert.equal(tag.text, '已逾期 1 天')
})

test('buildCasdoorLoginQuery 走 S256 且绝不携带 client_secret', () => {
  const q = buildCasdoorLoginQuery({
    clientId: 'xyyx-web',
    redirectUri: 'http://localhost:5173/auth/callback',
    transaction: { state: 's', nonce: 'n', codeChallenge: 'c' }
  })
  assert.equal(q.get('code_challenge_method'), 'S256')
  assert.equal(q.get('code_challenge'), 'c')
  assert.equal(q.has('client_secret'), false)
  assert.equal(q.has('password'), false)
})

test('buildDiscoveryUrl 对应用名做 URL 编码', () => {
  assert.equal(
    buildDiscoveryUrl('http://h:8000', 'a b'),
    'http://h:8000/.well-known/a%20b/openid-configuration'
  )
})
