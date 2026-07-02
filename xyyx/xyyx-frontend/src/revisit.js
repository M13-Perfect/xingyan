// The API serializes next_survey_date as ISO "yyyy-MM-ddTHH:mm:ss" (Map-based responses in
// SurveyPhoneDisplayService.toResponseRows bypass the entity's @JsonFormat), not "yyyy-MM-dd HH:mm".
// Slice the first 10 chars instead of splitting on a separator so this holds for either shape.
export const stripTime = (d) => d ? d.slice(0, 10) : ''

export function formatRevisitDate(value) {
  const ymd = stripTime(value || '')
  const parts = ymd.split('-')
  if (parts.length !== 3) return ''
  const month = Number(parts[1])
  const day = Number(parts[2])
  if (!month || !day) return ''
  return `${month}月${day}日`
}

// 已逾期 = 到达"下次回访日期"后,超过 revisitDeadlineDays 个自然日的宽限期仍未处理。
// 仍在宽限期内(已过回访日期,但未超时限)不返回 overdue,和"未来日期"视觉上一致。
export function revisitTag(item, todayDate, revisitDeadlineDays = 3) {
  if (!item || item.status !== '未处理' || !item.nextSurveyDate) return { type: null, text: '' }
  const ymd = stripTime(item.nextSurveyDate)
  if (!ymd) return { type: null, text: '' }
  if (ymd === todayDate) return { type: 'today', text: '今日回访' }
  if (ymd < todayDate) {
    const daysPast = Math.round(
      (new Date(`${todayDate}T00:00:00`).getTime() - new Date(`${ymd}T00:00:00`).getTime()) / 86400000
    )
    const overdueDays = daysPast - revisitDeadlineDays
    if (overdueDays >= 1) return { type: 'overdue', text: `已逾期 ${overdueDays} 天` }
  }
  return { type: null, text: '' }
}

// Drives the el-tag shown for a survey's status: reuses Element Plus's built-in
// 'danger'/'success'/'warning' tag types instead of custom colors, so an overdue
// 未处理 record turns the same status badge red and shows "已逾期 N 天" in place of "未处理".
export function surveyStatusBadge(item, todayDate, revisitDeadlineDays = 3) {
  if (!item) return { type: 'warning', text: '' }
  if (item.status === '已处理') return { type: 'success', text: item.status }
  const tag = revisitTag(item, todayDate, revisitDeadlineDays)
  if (tag.type === 'overdue') return { type: 'danger', text: tag.text }
  return { type: 'warning', text: item.status }
}
