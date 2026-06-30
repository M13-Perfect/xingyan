export const PHONE_REVEAL_COOLDOWN_MS = 5_000

export function displayPhoneValue(item = {}, revealedPhones, now = Date.now()) {
  const revealed = revealedPhones.get(item.id)
  if (revealed?.phoneRevealed === true && revealed.expiresAt > now && revealed.phoneDisplay) {
    return revealed.phoneDisplay
  }
  if (item.phoneRevealed === true) return item.phoneMask || '无'
  return item.phoneMask || '无'
}

function downgradePhoneItem(item) {
  if (!item) return
  if (item.phoneMask) item.phoneDisplay = item.phoneMask
  else if (item.phoneRevealed === true) item.phoneDisplay = ''
  item.phoneRevealed = false
  item.phoneDisplayMode = ''
  item.phoneRevealStatus = ''
}

export function clearPhoneRevealUiState(rows = [], selectedSurvey, revealedPhones) {
  revealedPhones.clear()
  for (const item of rows) downgradePhoneItem(item)
  downgradePhoneItem(selectedSurvey)
}

export function beginPhoneRevealRequest(state, surveyId, now = Date.now()) {
  if (surveyId === null || surveyId === undefined) return false
  if (state.inFlightIds.has(surveyId)) return false
  const lastAt = state.lastRevealAtById.get(surveyId)
  if (lastAt !== undefined && now - lastAt < PHONE_REVEAL_COOLDOWN_MS) return false
  state.inFlightIds.add(surveyId)
  state.lastRevealAtById.set(surveyId, now)
  return true
}

export function finishPhoneRevealRequest(state, surveyId) {
  state.inFlightIds.delete(surveyId)
}
