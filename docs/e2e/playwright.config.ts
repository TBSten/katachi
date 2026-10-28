import { defineConfig, devices } from '@playwright/test'
import { existsSync } from 'node:fs'
import { homedir } from 'node:os'
import { join } from 'node:path'
import { BASE } from './env.mjs'

// iPhone 相当は WebKit。同梱の Playwright(1.58) が探す WebKit の版と手元の版が違うので、手元にある版を実行ファイルで指す。
// 新しいブラウザはダウンロードしない。別の場所のものを使うなら E2E_WEBKIT_EXECUTABLE で指す。
const webkitExecutable = process.env.E2E_WEBKIT_EXECUTABLE ?? join(homedir(), 'Library/Caches/ms-playwright/webkit-2272/pw_run.sh')
const iphoneLaunch = existsSync(webkitExecutable) ? { executablePath: webkitExecutable } : {}

// PC の操作（Shift+クリック・右クリック・clipboard・ハイライトの画素・CDP）を使う列は、スマホの projects では回さない。
const desktopOnly = ['**/feedback-locate-*.spec.ts', '**/feedback-quick.spec.ts', '**/feedback-record.spec.ts', '**/feedback-console.spec.ts', '**/feedback-multi.spec.ts']

// dev サーバーの保存先は 1 つなので、テストは 1 本ずつ順番に回す（記録の件数を数える確認があるため）。
export default defineConfig({
	testDir: '.',
	testMatch: '**/*.spec.ts',
	fullyParallel: false,
	workers: 1,
	retries: 0,
	timeout: 180_000,
	expect: { timeout: 10_000 },
	reporter: [['list'], ['html', { open: 'never' }]],
	use: { baseURL: BASE, trace: 'off', screenshot: 'off' },
	webServer: {
		command: 'node dev-server.mjs',
		url: `${BASE}/ja/guides/basic-api/`,
		timeout: 240_000,
		reuseExistingServer: !!process.env.E2E_REUSE_SERVER,
		stdout: 'ignore',
		stderr: 'pipe',
	},
	projects: [
		{ name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } } },
		{ name: 'iphone', use: { ...devices['iPhone 13'], launchOptions: iphoneLaunch }, testIgnore: desktopOnly },
		{ name: 'pixel', use: { ...devices['Pixel 7'] }, testIgnore: desktopOnly },
	],
})
