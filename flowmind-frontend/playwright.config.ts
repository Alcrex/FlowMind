import { defineConfig } from '@playwright/test'
export default defineConfig({
  testDir: './tests', workers: 1, timeout: 60000,
  use: { baseURL: 'http://127.0.0.1:5173', channel: 'msedge', headless: true, actionTimeout: 10000, navigationTimeout: 15000,
    viewport: { width: 1440, height: 1000 }, screenshot: 'only-on-failure' },
  reporter: [['list']],
  webServer: [
    { command: 'powershell -NoProfile -ExecutionPolicy Bypass -File ../flowmind-backend/scripts/start_browser_test_server.ps1',
      url: 'http://127.0.0.1:8080/api/v1/market/templates', timeout: 120000, reuseExistingServer: false },
    { command: 'npm run dev', url: 'http://127.0.0.1:5173', reuseExistingServer: false }
  ]
})
