// dev の最中に、同じ Vite のキャッシュ（deps/）を別のプロセスが作り直しても、スクリーンショットが撮れること。
// 起きたこと: dev の横で `astro build` を回すと、build は設定が違うので deps/ を作り直し、dev だけの依存
// （modern-screenshot・astro/toolbar）を消す。dev は消えたファイルの URL を覚えたままなので 504 を返し、
// 撮影が「Failed to fetch dynamically imported module」で失敗した（.local/feedback/archived/261029-005.md）。
//
// dev は一度配ったモジュールを覚えているので、先に撮っていると直す前でも通ってしまう。
// ファイル名を spec の並びの先頭（cleanup より前）にして、dev が起きてから最初に撮るのがこのテストになるようにしてある。
import { spawnSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { test, ok } from './helpers/fixtures'
import { frontKey, readRecord } from './helpers/store'
import { COPY_CONFIG, COPY_DOCS, COPY_ROOT, ORIGIN, VITE_CACHE } from './env.mjs'
import type { Fb } from './helpers/fb'

const metadata = join(VITE_CACHE, 'deps/_metadata.json')
const browserHash = (): string | null => (existsSync(metadata) ? (JSON.parse(readFileSync(metadata, 'utf8')).browserHash ?? null) : null)

/** dev と同じ Vite のキャッシュを使って `astro build` を回す（利用者が dev の横でビルドするのと同じ）。 */
function buildBesideDev(): { status: number | null; log: string } {
	const r = spawnSync('pnpm', ['exec', 'astro', 'build', '--config', COPY_CONFIG, '--outDir', join(COPY_ROOT, 'dist-cache-rebuild')], {
		cwd: COPY_DOCS,
		encoding: 'utf8',
		timeout: 150_000,
	})
	return { status: r.status, log: `${r.stdout}\n${r.stderr}` }
}

/** 段落を選んで送り、記録に 2 枚の添付が付き、「撮れませんでした」が無いこと。 */
async function sendWithShot(fb: Fb, label: string) {
	await fb.enter()
	await fb.pickEl(fb.para)
	await fb.openInfo()
	await fb.inUi('.dialog textarea').fill(`deps の作り直しのあと ${label}`)
	const f = await fb.submitAndWait(40_000)
	ok(`${label}: 記録ができる`, f !== null)
	if (!f) return
	const md = readRecord(f)
	const att: string[] = JSON.parse(frontKey(md, 'attachments') ?? '[]')
	const failure = /撮れませんでした: .*/.exec(md)?.[0] ?? ''
	ok(`${label}: スクリーンショットが 2 枚付く`, att.length === 2, failure || JSON.stringify(att))
	ok(`${label}: 「撮れませんでした」が無い`, failure === '', failure)
}

test('dev の横で astro build を回して deps/ が作り直されても、スクリーンショットが撮れ、読み直しても Feedback が動く', async ({ fb }) => {
	test.skip(fb.mobile, 'deps/ の扱いは端末に依らないので PC だけで見る')
	test.setTimeout(300_000)
	await fb.fresh()

	// 撮影とアプリの入口が deps/ を経由していないこと（経由していると、作り直しで消える）。
	for (const file of ['feedback/capture.ts', 'feedback/app.ts', 'memo/app.ts']) {
		const url = `${ORIGIN}/@fs${join(COPY_DOCS, 'src/integrations/feedback', file)}`
		const code = await (await fb.page.request.get(url)).text()
		const viaDeps = code.match(/["'][^"']*\/deps\/(modern-screenshot|astro_toolbar)[^"']*["']/g) ?? []
		ok(`${file} が modern-screenshot / astro/toolbar を deps/ から読まない`, viaDeps.length === 0, viaDeps.join(', '))
	}

	const before = browserHash()
	const build = buildBesideDev()
	ok('astro build が通る', build.status === 0, build.log.slice(-2000))
	// 場面が再現できたこと: build が deps/ を作り直した（browserHash が変わる）。
	ok('build が deps/ を作り直した（browserHash が変わる）', before !== null && browserHash() !== before, `${before} → ${browserHash()}`)

	await sendWithShot(fb, '開いたままのページ')
	await fb.fresh()
	await sendWithShot(fb, '読み直したページ')
})
