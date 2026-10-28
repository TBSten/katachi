// Memo（dev 限定のメモ）。開く → 書く → 0.5 秒で保存 → 閉じる → 開き直すと戻る、箇所のメモと印、場所を表示・削除、Esc での片付け。
import { test, ok, sleep, waitFor } from './helpers/fixtures'
import { memoDir, readMemo } from './helpers/store'
import { join } from 'node:path'
import { existsSync, readFileSync } from 'node:fs'
import type { Fb } from './helpers/fb'

const DOC = 'ja/guides/basic-api.md'
const ROLE = '/ja/guides/role/'
const fieldsFile = () => join(memoDir(), '_fields.json')
const readFields = () => (existsSync(fieldsFile()) ? readFileSync(fieldsFile(), 'utf8') : '{"fields":[]}')

async function openMemo(fb: Fb) {
	await fb.revealToolbar()
	await fb.act(fb.memoBtn)
	await fb.memoUi('.panel').waitFor({ state: 'visible', timeout: 5000 })
	await fb.memoUi('.note-text').first().waitFor({ state: 'visible', timeout: 5000 })
}
const closeByButton = async (fb: Fb) => {
	await fb.act(fb.memoUi('.close'))
	await fb.page.waitForTimeout(300)
}
const status = (fb: Fb) => fb.memoUi('.status').getAttribute('data-state')

test('開く → ページのメモを書く → 0.5 秒後に保存 → 閉じると UI が残らず → 開き直すと値が戻る', async ({ fb }) => {
	const ui = fb.memoUi
	await fb.fresh()
	await openMemo(fb)
	ok('popover/シートで開く', await ui('.panel').isVisible())
	const panel = (await ui('.panel').boundingBox())!
	const vp = fb.page.viewportSize()!
	if (fb.mobile) ok('シートで出る（下端に付き、幅いっぱい）', (await ui('.panel.sheet').count()) === 1 && Math.abs(panel.x) < 1 && Math.abs(panel.width - vp.width) < 2 && Math.abs(panel.y + panel.height - vp.height) < 2, JSON.stringify(panel))
	else ok('右上に popover（幅 420px・最大高 70vh）', (await ui('.panel.popover').count()) === 1 && Math.abs(panel.width - 420) < 2 && panel.x + panel.width > vp.width - 20 && panel.y < 20 && panel.height <= vp.height * 0.7 + 2, JSON.stringify(panel))
	ok('一番上に memo', (await ui('.head h2').textContent()) === 'memo')
	ok('初期の field は「読みやすさ」だけ', (await ui('.page-note .field-label').allTextContents()).join() === '読みやすさ')
	const rows = await ui('.page-note .note-text').evaluate((t) => t.getAttribute('rows'))
	ok(`textarea の最初の行数（${fb.mobile ? 3 : 5} 行）`, rows === String(fb.mobile ? 3 : 5), String(rows))
	const h0 = (await ui('.page-note .note-text').boundingBox())!.height
	await ui('.page-note .field input').fill('3')
	await ui('.page-note .note-text').fill('ページ全体のメモ\n二行目\n三行目\n四行目\n五行目\n六行目\n七行目\n八行目')
	await sleep(150)
	ok('入力の直後はまだ保存されない（未保存の表示）', readMemo(DOC) === null && (await status(fb)) === 'dirty')
	ok('書くと textarea が伸びる', (await ui('.page-note .note-text').boundingBox())!.height > h0)
	ok('0.5 秒ほど後に保存される', await waitFor(() => (readMemo(DOC) ?? '').includes('ページ全体のメモ')))
	await sleep(300)
	ok('保存済みを端に出す', (await status(fb)) === 'saved' && /保存済み/.test((await ui('.status').textContent()) ?? ''))
	const md = readMemo(DOC) ?? ''
	ok('AI が読む Markdown の frontmatter', /^---\nschema: katachi-memo\/1\npage: "\/katachi\/ja\/guides\/basic-api\/"\nlang: "ja"/.test(md) && md.includes('source: "docs/src/content/docs/ja/guides/basic-api.mdx"') && /updated: \d{4}-/.test(md))
	ok('ページのメモ（最終更新の行・field・本文）', /## ページのメモ\n\n- 最終更新: \d{4}-[^\n]+\n\n- 読みやすさ: 3\n\nページ全体のメモ/.test(md), md.split('## ページのメモ')[1]?.slice(0, 120))
	await fb.shot('1-popover')
	await closeByButton(fb)
	ok('閉じると UI が残らない', (await fb.memoRootCount()) === 0)
	await openMemo(fb)
	ok('開き直すと値が戻っている', (await ui('.page-note .field input').inputValue()) === '3' && (await ui('.page-note .note-text').inputValue()).startsWith('ページ全体のメモ\n二行目'))
	await closeByButton(fb)
})

test('field を足すとひな形に保存され、別のページでも同じ field（値は空・ファイルは作らない）。ひな形から外すと行が消える', async ({ fb }) => {
	const ui = fb.memoUi
	await fb.fresh()
	await openMemo(fb)
	await fb.act(ui('.page-note .add-field'))
	await ui('.page-note .add-input').fill('密度')
	await ui('.page-note .add-input').press('Enter')
	ok('field の行が足される', (await ui('.page-note .field-label').allTextContents()).join() === '読みやすさ,密度')
	ok('ひな形ファイルに保存される', await waitFor(() => JSON.parse(readFields()).fields.join() === '読みやすさ,密度'), readFields())
	await closeByButton(fb)
	await fb.fresh(ROLE)
	await openMemo(fb)
	ok('別のページでも同じ field（値は空）', (await ui('.page-note .field-label').allTextContents()).join() === '読みやすさ,密度' && (await ui('.page-note .field input').evaluateAll((is) => is.every((i) => (i as HTMLInputElement).value === ''))))
	ok('別ページの textarea も空・ファイルはまだ作らない', (await ui('.page-note .note-text').inputValue()) === '' && readMemo('ja/guides/role.md') === null)
	if (!fb.mobile) {
		await ui('.page-note .field').nth(1).locator('.field-drop').click()
		ok('ひな形から外すと行が消える', (await ui('.page-note .field-label').allTextContents()).join() === '読みやすさ')
		ok('ひな形ファイルから外れる', await waitFor(() => JSON.parse(readFields()).fields.join() === '読みやすさ'))
	}
	await closeByButton(fb)
})

test('要素・テキストを選んで箇所のメモを書く → 印 → 閉じる → 再読み込み → 印が同じ箇所に戻る', async ({ fb }) => {
	const { page } = fb
	const ui = fb.memoUi
	const para = fb.para
	await fb.fresh()
	await openMemo(fb)
	await fb.act(ui('.pick'))
	await page.waitForTimeout(300)
	ok('選ぶ操作に入るとパネルが隠れ、帯が出る', !(await ui('.panel').isVisible()) && (await ui('.pill').isVisible()))
	if (!fb.mobile) {
		await para.hover()
		await page.waitForTimeout(200)
		ok('hover の枠が出る', (await ui('.frame').evaluate((e) => getComputedStyle(e).display)) === 'block')
	}
	await fb.act(para, { position: { x: 8, y: 8 } })
	await page.waitForTimeout(500)
	ok('選ぶとパネルが戻り、欄が足される', (await ui('.panel').isVisible()) && (await ui('.card').count()) === 1)
	ok('欄の構成（見出し・初期の field 1 つ・+ field・textarea・削除・場所を表示）', (await ui('.card .where-title').count()) === 1 && (await ui('.card .field').count()) === 1 && (await ui('.card .add-field').count()) === 1 && (await ui('.card .note-text').count()) === 1 && (await ui('.card .delete').count()) === 1 && (await ui('.card .reveal').count()) === 1)
	const spotTitle = await ui('.card .where').innerText()
	ok('見出しに場所（ソースの行）が出る', /basic-api\.mdx:\d+/.test(spotTitle), spotTitle)
	ok('番号の印が出る', (await ui('.mark').count()) === 1 && (await ui('.mark').textContent()) === '1')
	await ui('.card .field input').first().fill('2')
	await ui('.card .note-text').fill('この段落は長い')
	ok('箇所のメモが保存される（場所つき）', await waitFor(() => { const m = readMemo(DOC) ?? ''; return m.includes('## 箇所のメモ') && m.includes('この段落は長い') }))
	const md3 = readMemo(DOC) ?? ''
	for (const k of ['source: docs/src/content/docs/ja/guides/basic-api.mdx:', 'xpath: ', 'selector: ', 'excerpt: ', 'katachi-memo:data v1']) ok(`Markdown に ${k.trim()}`, md3.includes(k))
	ok('箇所に作成・最終更新の時刻の行がある', /### 1\.[\s\S]*- 作成: \d{4}-[\s\S]*- 最終更新: \d{4}-/.test(md3))
	await fb.shot('3-card-mark')
	await closeByButton(fb)
	ok('閉じると印も枠も UI も全部消える', (await fb.memoRootCount()) === 0)
	await page.reload({ waitUntil: 'networkidle' })
	await page.waitForTimeout(1200)
	ok('再読み込みした直後は何も出ていない', (await fb.memoRootCount()) === 0)
	await openMemo(fb)
	await page.waitForTimeout(500)
	ok('開き直すと保存した欄が戻る', (await ui('.card .note-text').inputValue()) === 'この段落は長い' && (await ui('.card .field input').first().inputValue()) === '2')
	const markAfter = (await ui('.mark').boundingBox())!
	const rectAfter = (await para.boundingBox())!
	ok('印が同じ要素のところに出る', Math.abs(markAfter.x - (rectAfter.x - 6)) < 3 && Math.abs(markAfter.y - (rectAfter.y - 6)) < 3, JSON.stringify({ markAfter, rectAfter }))
	await fb.shot('3-reload-marks')

	// テキストの選択で箇所を足す（PC は範囲ドラッグ、スマホは selection）
	await fb.act(ui('.pick'))
	await page.waitForTimeout(200)
	const para2 = page.locator('main p').nth(1)
	if (fb.mobile) {
		await para2.evaluate((p) => {
			const r = document.createRange()
			r.selectNodeContents(p)
			const s = getSelection()!
			s.removeAllRanges()
			s.addRange(r)
		})
	} else {
		const bb = (await para2.boundingBox())!
		await page.mouse.move(bb.x + 2, bb.y + 14)
		await page.mouse.down()
		await page.mouse.move(bb.x + 400, bb.y + 14, { steps: 8 })
		await page.mouse.up()
	}
	await page.waitForTimeout(700)
	ok('テキストを選んでも箇所が足される', (await ui('.card').count()) === 2 && (await ui('.mark').count()) === 2)
	ok('テキストの選択はテキストフラグメントつきで保存', await waitFor(() => (readMemo(DOC) ?? '').includes('種類: テキストの選択') && (readMemo(DOC) ?? '').includes('text fragment: http')))
	await ui('.card').nth(1).locator('.note-text').fill('選んだ文字へのメモ')
	await waitFor(() => (readMemo(DOC) ?? '').includes('選んだ文字へのメモ'))
	await closeByButton(fb)
	await page.reload({ waitUntil: 'networkidle' })
	await page.waitForTimeout(1200)
	await openMemo(fb)
	await page.waitForTimeout(500)
	const m2 = (await ui('.mark').nth(1).boundingBox())!
	const p2 = (await para2.boundingBox())!
	ok('テキスト選択の印も再読み込み後に同じ箇所へ', (await ui('.mark').count()) === 2 && Math.abs(m2.y - (p2.y - 6)) < 40, JSON.stringify({ m2, p2 }))
	ok('見つからない欄は無い', (await ui('.card.is-lost').count()) === 0)

	if (fb.mobile) return
	// 場所を表示・削除（PC）
	await fb.act(ui('.pick'))
	const lastH = page.locator('main h2').last()
	await lastH.scrollIntoViewIfNeeded()
	await page.waitForTimeout(300)
	await lastH.click({ position: { x: 4, y: 4 } })
	await page.waitForTimeout(500)
	ok('3 つ目の箇所が足される', (await ui('.card').count()) === 3)
	await ui('.card').nth(2).locator('.note-text').fill('消すメモ XYZ')
	await waitFor(() => (readMemo(DOC) ?? '').includes('消すメモ XYZ'))
	await page.evaluate(() => scrollTo(0, 0))
	await page.waitForTimeout(500)
	const y0 = await page.evaluate(() => scrollY)
	await ui('.card').nth(2).locator('.reveal').click()
	await page.waitForTimeout(700)
	const y1 = await page.evaluate(() => scrollY)
	ok('場所を表示でその箇所までスクロールする', y1 > y0 + 300, `${y0} -> ${y1}`)
	const flash = await ui('.flash').boundingBox().catch(() => null)
	ok('光る枠が見える範囲に出る', !!flash && flash.y >= 0 && flash.y < page.viewportSize()!.height, JSON.stringify(flash))
	await fb.shot('4-reveal')
	await sleep(2200)
	ok('光る枠は自然に消える', (await ui('.flash').count()) === 0)
	await ui('.card').nth(2).locator('.delete').click()
	await page.waitForTimeout(300)
	ok('削除で欄と印が消える', (await ui('.card').count()) === 2 && (await ui('.mark').count()) === 2)
	ok('ファイルからも消える', await waitFor(() => { const m = readMemo(DOC) ?? ''; return !m.includes('XYZ') && m.includes('選んだ文字へのメモ') }))
	await closeByButton(fb)
})

test('PC: Esc・閉じる・toolbar でもう一度で UI が残らず、選ぶ最中の Esc と field 名の入力中の Esc はそれだけをやめる', async ({ fb }) => {
	test.skip(fb.mobile, 'PC の列（Esc とホバー）')
	const { page } = fb
	const ui = fb.memoUi
	const vp = page.viewportSize()!
	await fb.fresh()
	await openMemo(fb)
	await page.keyboard.press('Escape')
	await page.waitForTimeout(300)
	ok('Esc で閉じると UI が残らない', (await fb.memoRootCount()) === 0)
	await openMemo(fb)
	await ui('.pick').click()
	await page.waitForTimeout(200)
	await page.keyboard.press('Escape')
	await page.waitForTimeout(300)
	ok('選ぶ最中の Esc は選ぶのをやめるだけ（パネルは戻る）', (await fb.memoRootCount()) === 1 && (await ui('.panel').isVisible()) && !(await ui('.pill').isVisible()))
	await ui('.pick').click()
	await page.mouse.move(500, 400)
	await page.keyboard.press('Escape')
	await page.keyboard.press('Escape')
	await page.waitForTimeout(300)
	ok('続けて Esc で閉じる。hover の枠も残らない', (await fb.memoRootCount()) === 0)
	await openMemo(fb)
	await ui('.page-note .add-field').click()
	await ui('.page-note .add-input').press('Escape')
	ok('field 名の入力中の Esc は入力だけやめる', (await fb.memoRootCount()) === 1 && (await ui('.page-note .add-input').count()) === 0)
	await page.mouse.move(640, vp.height - 10)
	await page.waitForTimeout(300)
	await fb.memoBtn.click()
	await page.waitForTimeout(300)
	ok('toolbar をもう一度押して閉じると UI が残らない', (await fb.memoRootCount()) === 0)
})
