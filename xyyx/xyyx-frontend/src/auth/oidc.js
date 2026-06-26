const trimTrailingSlash = (value) => value.replace(/\/+$/, '')
const AUTH_TRANSACTION_KEY = 'xyyx.casdoor.auth_transaction'
const AUTH_TRANSACTION_TTL_MS = 10 * 60 * 1000
const SHA256_K = [
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
  0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
  0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
  0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
  0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
  0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
  0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
  0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
  0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
  0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
  0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
  0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
  0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
  0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
  0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
  0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
]

export function buildDiscoveryUrl(baseUrl, applicationName = '') {
  const base = trimTrailingSlash(baseUrl)
  const appName = applicationName.trim()
  if (!appName) return `${base}/.well-known/openid-configuration`
  return `${base}/.well-known/${encodeURIComponent(appName)}/openid-configuration`
}

export function buildTokenRequestBody({
  clientId,
  redirectUri,
  code,
  codeVerifier
}) {
  return new URLSearchParams({
    grant_type: 'authorization_code',
    client_id: clientId,
    redirect_uri: redirectUri,
    code,
    code_verifier: codeVerifier
  })
}

export function base64UrlFromBytes(bytes) {
  let binary = ''
  const chunkSize = 0x8000
  for (let i = 0; i < bytes.length; i += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunkSize))
  }
  return btoa(binary)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '')
}

const rightRotate = (value, bits) => (value >>> bits) | (value << (32 - bits))

function sha256Fallback(inputBytes) {
  const bytes = Array.from(inputBytes)
  const bitLength = bytes.length * 8
  const hash = [
    0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
    0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
  ]

  bytes.push(0x80)
  while (bytes.length % 64 !== 56) bytes.push(0)

  const high = Math.floor(bitLength / 0x100000000)
  const low = bitLength >>> 0
  for (let shift = 24; shift >= 0; shift -= 8) bytes.push((high >>> shift) & 0xff)
  for (let shift = 24; shift >= 0; shift -= 8) bytes.push((low >>> shift) & 0xff)

  const words = new Uint32Array(64)
  for (let offset = 0; offset < bytes.length; offset += 64) {
    for (let i = 0; i < 16; i++) {
      const j = offset + i * 4
      words[i] = (
        (bytes[j] << 24) |
        (bytes[j + 1] << 16) |
        (bytes[j + 2] << 8) |
        bytes[j + 3]
      ) >>> 0
    }

    for (let i = 16; i < 64; i++) {
      const s0 = rightRotate(words[i - 15], 7) ^ rightRotate(words[i - 15], 18) ^ (words[i - 15] >>> 3)
      const s1 = rightRotate(words[i - 2], 17) ^ rightRotate(words[i - 2], 19) ^ (words[i - 2] >>> 10)
      words[i] = (words[i - 16] + s0 + words[i - 7] + s1) >>> 0
    }

    let [a, b, c, d, e, f, g, h] = hash
    for (let i = 0; i < 64; i++) {
      const s1 = rightRotate(e, 6) ^ rightRotate(e, 11) ^ rightRotate(e, 25)
      const ch = (e & f) ^ (~e & g)
      const temp1 = (h + s1 + ch + SHA256_K[i] + words[i]) >>> 0
      const s0 = rightRotate(a, 2) ^ rightRotate(a, 13) ^ rightRotate(a, 22)
      const maj = (a & b) ^ (a & c) ^ (b & c)
      const temp2 = (s0 + maj) >>> 0
      h = g
      g = f
      f = e
      e = (d + temp1) >>> 0
      d = c
      c = b
      b = a
      a = (temp1 + temp2) >>> 0
    }

    hash[0] = (hash[0] + a) >>> 0
    hash[1] = (hash[1] + b) >>> 0
    hash[2] = (hash[2] + c) >>> 0
    hash[3] = (hash[3] + d) >>> 0
    hash[4] = (hash[4] + e) >>> 0
    hash[5] = (hash[5] + f) >>> 0
    hash[6] = (hash[6] + g) >>> 0
    hash[7] = (hash[7] + h) >>> 0
  }

  const digest = new Uint8Array(32)
  hash.forEach((value, index) => {
    digest[index * 4] = (value >>> 24) & 0xff
    digest[index * 4 + 1] = (value >>> 16) & 0xff
    digest[index * 4 + 2] = (value >>> 8) & 0xff
    digest[index * 4 + 3] = value & 0xff
  })
  return digest
}

async function sha256Bytes(inputBytes, cryptoApi) {
  if (cryptoApi?.subtle?.digest) {
    return new Uint8Array(await cryptoApi.subtle.digest('SHA-256', inputBytes))
  }
  return sha256Fallback(inputBytes)
}

export async function createPkcePair(cryptoApi = globalThis.crypto) {
  if (!cryptoApi?.getRandomValues) {
    throw new Error('Secure random values are required for OAuth2 PKCE')
  }

  const verifierBytes = new Uint8Array(32)
  cryptoApi.getRandomValues(verifierBytes)
  const verifier = base64UrlFromBytes(verifierBytes)
  const digest = await sha256Bytes(new TextEncoder().encode(verifier), cryptoApi)

  return {
    verifier,
    challenge: base64UrlFromBytes(digest)
  }
}

export function createRandomState(cryptoApi = globalThis.crypto) {
  if (!cryptoApi?.getRandomValues) {
    throw new Error('WebCrypto is required for OAuth2 state')
  }
  const bytes = new Uint8Array(16)
  cryptoApi.getRandomValues(bytes)
  return base64UrlFromBytes(bytes)
}

export function sanitizeReturnTo(value, fallback = '/') {
  if (typeof value !== 'string') return fallback

  let decoded
  try {
    decoded = decodeURIComponent(value.trim())
  } catch {
    return fallback
  }

  const candidates = [value.trim(), decoded]
  for (const candidate of candidates) {
    const lower = candidate.toLowerCase()
    if (
      !candidate.startsWith('/') ||
      candidate.startsWith('//') ||
      candidate.includes('\\') ||
      lower.startsWith('http://') ||
      lower.startsWith('https://') ||
      lower.includes('http://') ||
      lower.includes('https://') ||
      lower.includes('http%3a') ||
      lower.includes('https%3a')
    ) {
      return fallback
    }
  }

  return decoded || fallback
}

export function clearAuthTransaction(storage = globalThis.sessionStorage) {
  storage?.removeItem(AUTH_TRANSACTION_KEY)
}

export function loadAuthTransaction(storage = globalThis.sessionStorage, now = Date.now()) {
  const raw = storage?.getItem(AUTH_TRANSACTION_KEY)
  if (!raw) return null

  try {
    const transaction = JSON.parse(raw)
    if (!transaction?.createdAt || now - transaction.createdAt > AUTH_TRANSACTION_TTL_MS) {
      clearAuthTransaction(storage)
      return null
    }
    return transaction
  } catch {
    clearAuthTransaction(storage)
    return null
  }
}

export async function createAuthTransaction({
  cryptoApi = globalThis.crypto,
  storage = globalThis.sessionStorage,
  returnTo = '/'
} = {}) {
  const pkce = await createPkcePair(cryptoApi)
  if (
    pkce.verifier.length < 43 ||
    pkce.verifier.length > 128 ||
    !pkce.challenge ||
    /[+/=]/.test(pkce.challenge)
  ) {
    throw new Error('Invalid PKCE transaction')
  }

  const transaction = {
    state: createRandomState(cryptoApi),
    nonce: createRandomState(cryptoApi),
    codeVerifier: pkce.verifier,
    codeChallenge: pkce.challenge,
    returnTo: sanitizeReturnTo(returnTo),
    createdAt: Date.now(),
    consumed: false
  }
  storage?.setItem(AUTH_TRANSACTION_KEY, JSON.stringify(transaction))
  return transaction
}

export function consumeAuthTransaction({
  state,
  storage = globalThis.sessionStorage,
  now = Date.now()
} = {}) {
  const transaction = loadAuthTransaction(storage, now)
  if (!transaction || transaction.consumed || transaction.state !== state) {
    clearAuthTransaction(storage)
    throw new Error('Invalid OAuth transaction')
  }

  const consumed = { ...transaction, consumed: true }
  storage?.setItem(AUTH_TRANSACTION_KEY, JSON.stringify(consumed))
  return consumed
}

export function buildCasdoorLoginQuery({
  clientId,
  redirectUri,
  scope = 'openid profile email',
  transaction
}) {
  return new URLSearchParams({
    clientId,
    responseType: 'code',
    redirectUri,
    type: 'code',
    scope,
    state: transaction.state,
    nonce: transaction.nonce,
    code_challenge_method: 'S256',
    code_challenge: transaction.codeChallenge
  })
}
