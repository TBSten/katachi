// ブラウザのコンソール（error・warn・未捕捉の例外・rejection・Vite のエラー）が、記録の「コンソール」節に載ること。PC の Chromium だけ。
import { readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { test, ok } from './helpers/fixtures'
import { COPY_DOCS } from './env.mjs'
import { readRecord, sectionBody } from './helpers/store'

test('console.error / warn / 未捕捉の例外 / rejection が、バッファに溜まり、記録のコンソール節に件数・要約・一覧で載る', async ({ fb, page }) => {
	fb.allowPageErrors = true // わざと例外を投げる
	await fb.fresh()
	await page.evaluate(() => {
		console.error('E2E わざとの error')
		console.error('E2E わざとの error')
		console.warn('E2E わざとの warn')
		console.log('E2E log')
		setTimeout(() => { throw new Error('E2E 未捕捉の例外') }, 10)
		void Promise.reject(new Error('E2E rejection'))
	})
	await page.waitForTimeout(500)
	const buf = await page.evaluate(() => ((window as any).__katachiFeedbackLog?.entries() ?? []).map((e: { k: string; m: string }) => `${e.k}:${e.m.slice(0, 60)}`))
	ok('バッファに error・warn・log・例外・rejection が入る', ['error:E2E わざとの error', 'warn:E2E わざとの warn', 'log:E2E log'].every((s) => buf.some((b: string) => b.startsWith(s))) && buf.some((b: string) => /E2E 未捕捉の例外/.test(b)) && buf.some((b: string) => /E2E rejection/.test(b)), JSON.stringify(buf))
	await fb.enter()
	await fb.pickEl(fb.para)
	await fb.inUi('.popover textarea').fill('console 検証')
	const f = await fb.submitAndWait()
	ok('記録ができる', f !== null)
	if (!f) return
	const sec = sectionBody(readRecord(f), 'コンソール')
	ok('同じ error は ×2 にまとまる', /E2E わざとの error.*×2|×2.*E2E わざとの error/.test(sec), sec.slice(0, 400))
	ok('warn・例外・rejection が一覧にある', ['E2E わざとの warn', 'E2E 未捕捉の例外', 'E2E rejection'].every((s) => sec.includes(s)), sec.slice(0, 600))
	ok('先頭に error / warn の件数の要約がある', /error/.test(sec.split('\n').slice(0, 3).join('\n')))
})

test('壊れた画像（resource の読み込み失敗）がコンソールの節に載る', async ({ fb, page }) => {
	await fb.fresh()
	await page.evaluate(() => {
		const img = document.createElement('img')
		img.src = '/katachi/e2e-no-such-image.png'
		document.body.append(img)
	})
	await page.waitForTimeout(800)
	await fb.enter()
	await fb.pickEl(fb.para)
	await fb.inUi('.popover textarea').fill('resource 検証')
	const f = await fb.submitAndWait()
	ok('記録ができる', f !== null)
	if (f) ok('読めなかった画像の URL が載る', sectionBody(readRecord(f), 'コンソール').includes('e2e-no-such-image.png'), sectionBody(readRecord(f), 'コンソール').slice(0, 400))
})

test('CSS を壊すと Vite のエラーが「vite」としてバッファに入る', async ({ fb, page }) => {
	const css = join(COPY_DOCS, 'src/styles/custom-global.css')
	const orig = readFileSync(css, 'utf8')
	await fb.fresh()
	await page.evaluate(() => { (window as any).__marker = 1 })
	try {
		writeFileSync(css, orig + '\n@import "./nonexistent-e2e.css";\n.x { color: \n')
		await page.waitForTimeout(4000)
	} finally {
		writeFileSync(css, orig)
	}
	const vite = await page.evaluate(() => ((window as any).__katachiFeedbackLog?.entries() ?? []).filter((e: { k: string }) => e.k === 'vite').map((e: { m: string }) => e.m.slice(0, 200)))
	ok('Vite のエラーが vite の行で溜まる', vite.length > 0, JSON.stringify(vite))
	await page.waitForTimeout(1500) // 元に戻した CSS の再読み込みが落ち着くまで待つ

})
