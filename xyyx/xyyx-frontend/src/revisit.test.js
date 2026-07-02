import test from 'node:test'
import assert from 'node:assert/strict'
import { revisitTag, formatRevisitDate, surveyStatusBadge } from './revisit.js'

test('revisitTag stays silent while inside the deadline grace window', () => {
  // 回访日期 7/1，时限 3 天 -> 7/2、7/3、7/4 都在时限内，不应显示逾期。
  for (const today of ['2026-07-02', '2026-07-03', '2026-07-04']) {
    const tag = revisitTag({ status: '未处理', nextSurveyDate: '2026-07-01 00:00:00' }, today, 3)
    assert.equal(tag.type, null, `expected no tag on ${today}`)
  }
})

test('revisitTag reports the first overdue day right after the deadline', () => {
  // 到 7/5 才超过默认 3 天时限，逾期 1 天。
  const tag = revisitTag({ status: '未处理', nextSurveyDate: '2026-07-01' }, '2026-07-05', 3)
  assert.equal(tag.type, 'overdue')
  assert.equal(tag.text, '已逾期 1 天')
})

test('revisitTag overdue days keep climbing after the deadline', () => {
  const tag = revisitTag({ status: '未处理', nextSurveyDate: '2026-07-01' }, '2026-07-07', 3)
  assert.equal(tag.type, 'overdue')
  assert.equal(tag.text, '已逾期 3 天')
})

test('revisitTag marks the due date itself as today, not overdue', () => {
  const tag = revisitTag({ status: '未处理', nextSurveyDate: '2026-07-01' }, '2026-07-01', 3)
  assert.equal(tag.type, 'today')
})

test('revisitTag ignores processed records and missing dates', () => {
  assert.equal(revisitTag({ status: '已处理', nextSurveyDate: '2026-01-01' }, '2026-07-05', 3).type, null)
  assert.equal(revisitTag({ status: '未处理', nextSurveyDate: '' }, '2026-07-05', 3).type, null)
  assert.equal(revisitTag(null, '2026-07-05', 3).type, null)
})

test('revisitTag respects a shorter configured deadline', () => {
  // 时限改为 1 天：回访日期次日仍在宽限期内，第 3 天才逾期 1 天。
  assert.equal(revisitTag({ status: '未处理', nextSurveyDate: '2026-07-01' }, '2026-07-02', 1).type, null)
  assert.equal(revisitTag({ status: '未处理', nextSurveyDate: '2026-07-01' }, '2026-07-03', 1).type, 'overdue')
})

test('formatRevisitDate renders month/day in Chinese', () => {
  assert.equal(formatRevisitDate('2026-07-05 00:00:00'), '7月5日')
  assert.equal(formatRevisitDate(''), '')
})

test('surveyStatusBadge turns the status tag red once a record is overdue', () => {
  const badge = surveyStatusBadge({ status: '未处理', nextSurveyDate: '2026-07-01' }, '2026-07-05', 3)
  assert.equal(badge.type, 'danger')
  assert.equal(badge.text, '已逾期 1 天')
})

test('surveyStatusBadge keeps the plain 未处理 label inside the grace window and on future dates', () => {
  assert.deepEqual(
    surveyStatusBadge({ status: '未处理', nextSurveyDate: '2026-07-01' }, '2026-07-03', 3),
    { type: 'warning', text: '未处理' }
  )
  assert.deepEqual(
    surveyStatusBadge({ status: '未处理', nextSurveyDate: '2026-08-01' }, '2026-07-03', 3),
    { type: 'warning', text: '未处理' }
  )
})

test('surveyStatusBadge marks processed records success regardless of date', () => {
  const badge = surveyStatusBadge({ status: '已处理', nextSurveyDate: '2026-01-01' }, '2026-07-05', 3)
  assert.deepEqual(badge, { type: 'success', text: '已处理' })
})

// Regression: GET /api/surveys serializes nextSurveyDate via SurveyPhoneDisplayService.toResponseRows,
// which puts the raw LocalDateTime into a Map and loses the entity's @JsonFormat("yyyy-MM-dd HH:mm")
// annotation, so the real wire format is ISO "yyyy-MM-ddTHH:mm:ss", not space-separated. stripTime used
// to split on ' ', which left the full "...T00:00:00" suffix in place and made every row silently fall
// through to {type: null} — counts (backend-computed) looked right, but no row ever rendered red/amber.
test('revisitTag and surveyStatusBadge handle the real ISO "T" wire format from GET /api/surveys', () => {
  const today = revisitTag({ status: '未处理', nextSurveyDate: '2026-07-01T00:00:00' }, '2026-07-01', 3)
  assert.equal(today.type, 'today')

  const overdue = revisitTag({ status: '未处理', nextSurveyDate: '2026-06-24T21:59:00' }, '2026-07-01', 3)
  assert.equal(overdue.type, 'overdue')
  assert.equal(overdue.text, '已逾期 4 天')

  const badge = surveyStatusBadge({ status: '未处理', nextSurveyDate: '2026-06-24T21:59:00' }, '2026-07-01', 3)
  assert.deepEqual(badge, { type: 'danger', text: '已逾期 4 天' })
})
