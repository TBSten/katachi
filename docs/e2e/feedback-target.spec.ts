// 何も選ばずに、ページ全体・サイト全体へ送る列（案内の帯の「このページ・サイトについて…」）。
import { existsSync } from 'node:fs'
import { join } from 'node:path'
import { test, ok, BASIC } from './helpers/fixtures'
import { COPY_ROOT, BASE } from './env.mjs'
import { files, readRecord } from './helpers/store'
import { gap } from './helpers/geometry'
import type { Fb } from './helpers/fb'

const SRC = 'docs/src/content/docs/ja/guides/basic-api.mdx'
const PAGE_URL = `${BASE}${BASIC}`

/** 案内の帯の「このページ・サイトについて…」を押して入力欄を開く。 */
async function openAbout(fb: Fb) {
	await fb.fresh()
	await fb.enter()
	const about = fb.inUi('.banner .about')
	ok('案内の帯に「このページ・サイトについて…」が出る', await about.isVisible())
	const bb = await about.boundingBox()
	ok('ボタンの高さ >= 44px', !!bb && bb.height >= 44, JSON.stringify(bb))
	await fb.act(about)
	await fb.page.waitForTimeout(400)
	return fb.inUi('.banner').boundingBox()
}

for (const target of ['page', 'site'] as const) {
	test(`${target === 'page' ? 'ページ全体' : 'サイト全体'}へ送ると、要素の節が無くページ・画面・ブラウザ・日時・git の節だけの記録になる`, async ({ fb }) => {
		const dlg = fb.mobile ? fb.inUi('.dialog:not(.popover)') : fb.inUi('.popover')
		const bannerBox = (await openAbout(fb))!
		ok(fb.mobile ? 'シートが開く' : 'popover が開く', await dlg.isVisible())
		if (!fb.mobile) ok('popover は案内の帯のそば（隙間 24px 以内）', gap(bannerBox, (await dlg.boundingBox())!) <= 24)
		ok('一番上に対象の選択があり既定は「このページ」', (await fb.inUi('.target input').count()) === 2 && (await fb.inUi('.target input[value=page]').isChecked()))
		ok('要素の節（選んだ内容・ページ上の場所・ソースの位置）が選べる情報に無い', (await fb.inUi('#chk-content').count()) === 0 && (await fb.inUi('#chk-location').count()) === 0 && (await fb.inUi('#chk-source').count()) === 0)
		ok('ページ・画面・ブラウザ・日時・git は選べる', (await Promise.all(['page', 'screen', 'browser', 'datetime', 'git'].map((k) => fb.inUi(`#chk-${k}`).count()))).every((n) => n === 1))
		if (target === 'site') await fb.act(fb.inUi('.target label').nth(1))
		await fb.shot(`target-${target}`)
		await fb.inUi('textarea').fill(`${target} への指摘のテスト`)
		const before = files().length
		await fb.act(fb.inUi('.dialog .primary'))
		await fb.waitSaved(before)
		ok('記録ができる', files().length === before + 1)
		const md = files().length === before + 1 ? readRecord(files().at(-1)!) : ''
		ok(`frontmatter に kind: ${target}`, new RegExp(`^(target|kind): "?${target}"?$`, 'm').test(md))
		const summary = md.split('\n').find((l) => l.startsWith('対象:')) ?? ''
		if (target === 'page') {
			ok('要約が「対象: <ソースファイル>（ページ全体）「title」」', summary === `対象: ${SRC}（ページ全体）「基本的な API | katachi」`, summary)
			ok('ページのソースファイルが正しい（実在する）', md.includes(`- ページのソースファイル: ${SRC}`) && existsSync(join(COPY_ROOT, SRC)) && /^source: "docs\/src\/content\/docs\/ja\/guides\/basic-api\.mdx"$/m.test(md))
		} else {
			ok('要約が「対象: サイト全体（<URL> から）」', summary === `対象: サイト全体（${PAGE_URL} から）`, summary)
			ok('どのページから送ったか（URL）が載る', md.includes(`- URL: ${PAGE_URL}`) && /^page: /m.test(md))
		}
		ok('要素の節が無い', !/^## (選んだ内容|ページ上の場所|ソースの位置)$/m.test(md) && !/^(xpath|selector|heading):/m.test(md))
		ok('ページ・画面・ブラウザ・日時・git の節がある', ['ページ', '画面', 'ブラウザ', '日時', 'git'].every((t) => new RegExp(`^## ${t}$`, 'm').test(md)))
		ok('画面に getBoundingClientRect が無い（選んだものが無い）', !md.includes('getBoundingClientRect'))
		ok('UI が残らず toolbar も閉じる', (await fb.rootCount()) === 0 && !(await fb.appActive()))
	})
}

test('ページ・サイト宛の入力欄をキャンセル・Esc で閉じると UI も toolbar も残らない', async ({ fb, page }) => {
	await openAbout(fb)
	await fb.act(fb.inUi('.dialog .actions button:not(.primary):not(.copy)'))
	await page.waitForTimeout(300)
	ok('キャンセルで UI が残らず toolbar も閉じる', (await fb.rootCount()) === 0 && !(await fb.appActive()))
	await openAbout(fb)
	await fb.esc()
	ok('Esc で UI が残らず toolbar も閉じる', (await fb.rootCount()) === 0 && !(await fb.appActive()))
})

test('PC: 外クリックは空なら閉じてモードは続き、書きかけなら閉じず、Esc で片付く', async ({ fb, page }) => {
	test.skip(fb.mobile, 'PC の列')
	await openAbout(fb)
	await page.locator('main h1').first().click({ position: { x: 2, y: 2 } })
	await page.waitForTimeout(300)
	ok('コメントが空なら外クリックで閉じ、モードは続く', (await fb.inUi('.popover').count()) === 0 && (await fb.rootCount()) === 1)
	await fb.act(fb.inUi('.banner .about'))
	await page.waitForTimeout(300)
	ok('閉じたあともう一度開ける', await fb.inUi('.popover').isVisible())
	await fb.inUi('textarea').fill('書きかけ')
	await page.locator('main h1').first().click({ position: { x: 2, y: 2 } })
	await page.waitForTimeout(300)
	ok('書きかけなら外クリックで閉じない', (await fb.inUi('.popover').count()) === 1 && (await fb.inUi('textarea').inputValue()) === '書きかけ')
	await fb.esc()
	ok('書きかけでも Esc で片付く', (await fb.rootCount()) === 0 && !(await fb.appActive()))
})
