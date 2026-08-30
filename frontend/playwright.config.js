import { defineConfig } from '@playwright/test'

const browserChannel = process.env.PLAYWRIGHT_BROWSER_CHANNEL

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  reporter: [['list']],
  use: {
    baseURL: 'http://127.0.0.1:5173'
  },
  projects: [
    {
      name: browserChannel || 'bundled-chromium',
      use: browserChannel ? { channel: browserChannel } : { browserName: 'chromium' }
    }
  ],
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1',
    url: 'http://127.0.0.1:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000
  }
})
