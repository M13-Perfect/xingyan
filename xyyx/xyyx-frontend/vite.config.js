import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
// Use http://localhost:5173 or http://127.0.0.1:5173 for dev — browsers only
// allow window.crypto.subtle (RSA password encryption, PKCE) on localhost or
// HTTPS. LAN-IP access over plain HTTP will hit that limit; making that work
// would also require Casdoor to speak real HTTPS, which isn't needed today.
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
