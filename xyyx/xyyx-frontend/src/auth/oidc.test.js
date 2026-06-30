import test from 'node:test'
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'

import {
  base64UrlFromBytes,
  buildCasdoorLoginQuery,
  buildDiscoveryUrl,
  buildTokenRequestBody,
  clearAuthTransaction,
  consumeAuthTransaction,
  createAuthTransaction,
  createPkcePair,
  sanitizeReturnTo
} from './oidc.js'

function createStorage() {
  const values = new Map()
  return {
    getItem: (key) => values.has(key) ? values.get(key) : null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key)
  }
}

test('buildDiscoveryUrl supports default and Casdoor application-specific discovery', () => {
  assert.equal(
    buildDiscoveryUrl('http://localhost:8000', ''),
    'http://localhost:8000/.well-known/openid-configuration'
  )
  assert.equal(
    buildDiscoveryUrl('http://localhost:8000/', 'xyyx'),
    'http://localhost:8000/.well-known/xyyx/openid-configuration'
  )
})

test('buildTokenRequestBody exchanges code without a client secret', () => {
  const body = buildTokenRequestBody({
    clientId: 'xyyx-web',
    redirectUri: 'http://localhost:5173/auth/callback',
    code: 'auth-code',
    codeVerifier: 'verifier-1'
  })

  assert.equal(body.get('grant_type'), 'authorization_code')
  assert.notEqual(body.get('grant_type'), 'password')
  assert.equal(body.get('client_id'), 'xyyx-web')
  assert.equal(body.get('redirect_uri'), 'http://localhost:5173/auth/callback')
  assert.equal(body.get('code'), 'auth-code')
  assert.equal(body.get('code_verifier'), 'verifier-1')
  assert.equal(body.has('client_secret'), false)
})

test('createPkcePair keeps S256 when crypto.subtle is unavailable', async () => {
  const fixedBytes = new Uint8Array(32).fill(7)
  const cryptoWithoutSubtle = {
    getRandomValues(target) {
      target.set(fixedBytes)
      return target
    }
  }

  const pkce = await createPkcePair(cryptoWithoutSubtle)
  const expectedVerifier = base64UrlFromBytes(fixedBytes)
  const expectedChallenge = createHash('sha256')
    .update(expectedVerifier)
    .digest('base64url')

  assert.equal(pkce.verifier, expectedVerifier)
  assert.equal(pkce.challenge, expectedChallenge)
  assert.match(pkce.challenge, /^[A-Za-z0-9_-]+$/)
  assert.equal(pkce.challenge.includes('='), false)
})

test('sanitizeReturnTo only accepts internal relative paths', () => {
  assert.equal(sanitizeReturnTo('/work?page=1'), '/work?page=1')
  assert.equal(sanitizeReturnTo('//evil.com'), '/')
  assert.equal(sanitizeReturnTo('https://evil.com'), '/')
  assert.equal(sanitizeReturnTo('%2F%2Fevil.com'), '/')
  assert.equal(sanitizeReturnTo('/next?to=https://evil.com'), '/')
  assert.equal(sanitizeReturnTo('/\\evil'), '/')
})

test('auth transaction stores PKCE in session storage and can only be consumed once', async () => {
  const storage = createStorage()
  const fixedBytes = new Uint8Array(32).fill(9)
  const cryptoApi = {
    getRandomValues(target) {
      target.set(fixedBytes.slice(0, target.length))
      return target
    },
    subtle: {
      async digest(_name, input) {
        return createHash('sha256').update(Buffer.from(input)).digest()
      }
    }
  }

  const transaction = await createAuthTransaction({
    cryptoApi,
    storage,
    returnTo: '/surveys?status=pending'
  })

  assert.equal(transaction.consumed, false)
  assert.equal(transaction.returnTo, '/surveys?status=pending')
  assert.equal(transaction.codeVerifier.length >= 43, true)
  assert.match(transaction.codeChallenge, /^[A-Za-z0-9_-]+$/)

  const query = buildCasdoorLoginQuery({
    clientId: 'xyyx-web',
    redirectUri: 'http://localhost:5173/auth/callback',
    transaction
  })
  assert.equal(query.get('responseType'), 'code')
  assert.equal(query.get('code_challenge_method'), 'S256')
  assert.equal(query.get('code_challenge'), transaction.codeChallenge)
  assert.equal(query.has('challengeMethod'), false)
  assert.equal(query.has('codeChallenge'), false)

  const consumed = consumeAuthTransaction({ state: transaction.state, storage })
  assert.equal(consumed.consumed, true)
  assert.throws(() => consumeAuthTransaction({ state: transaction.state, storage }))
  clearAuthTransaction(storage)
})
