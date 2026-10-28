// スクリーンショットの添付: 要素・テキスト・ページ宛でそれぞれ撮れること、外せば撮らず記憶されること。
import { test, ok, sleep } from './helpers/fixtures'
import { attachmentAbs, files, frontKey, pngInfo, readRecord } from './helpers/store'
import { MIN_COLORS, MIN_LUMINANCE_STD, pixelStats } from './helpers/pixels'
import type { Fb } from './helpers/fb'

/** 添付の検証。要素・テキストは window.png と element.png の 2 枚、ページは window.png だけ。 */
async function checkAttachments(fb: Fb, kind: string, md: string, withElement: boolean) {
	const att: string[] = JSON.parse(frontKey(md, 'attachments') ?? '[]')
	ok(`${kind}: frontmatter に attachments が ${withElement ? 2 : 1} 枚`, att.length === (withElement ? 2 : 1), JSON.stringify(att))
	ok(`${kind}: 「## スクリーンショット」の節に参照が書かれる`, md.includes('## スクリーンショット') && att.every((a) => md.includes(a)))
	const infos = att.map((a) => ({ a, ...pngInfo(attachmentAbs(a)) }))
	ok(`${kind}: PNG が実在し壊れていない`, infos.every((i) => i.sig && i.bytes > 2000), JSON.stringify(infos.map((i) => [i.a.split('/').slice(-2).join('/'), i.w, i.h, i.bytes])))
	// 真っ白（全面が 1 色。例: 不正な属性で複製が空になる）でも赤枠だけで 2000 バイトは超えるので、画素のばらつきも見る。
	for (const i of infos) {
		const st = await pixelStats(attachmentAbs(i.a))
		ok(`${kind}: ${i.a.split('/').pop()} は真っ白でない（色 ${st.colors} 種類・輝度の標準偏差 ${st.luminanceStd.toFixed(1)}）`, st.colors >= MIN_COLORS && st.luminanceStd >= MIN_LUMINANCE_STD, JSON.stringify(st))
	}
	const win = infos.find((i) => i.a.endsWith('window.png'))
	const vp = fb.page.viewportSize()!
	const sc = Math.min(await fb.page.evaluate(() => devicePixelRatio), 2)
	ok(`${kind}: window.png の大きさ = viewport × min(dpr,2)`, !!win && Math.abs(win.w - Math.round(vp.width * sc)) <= 1 && Math.abs(win.h - Math.round(vp.height * sc)) <= 1, `${win?.w}x${win?.h} vs ${vp.width * sc}x${vp.height * sc}`)
	if (withElement) {
		const el = infos.find((i) => i.a.endsWith('element.png'))
		ok(`${kind}: element.png は window.png より小さい`, !!el && !!win && (el.w < win.w || el.h < win.h))
	}
}

for (const kind of ['element', 'text', 'page'] as const) {
	test(`${kind === 'element' ? '要素' : kind === 'text' ? 'テキスト' : 'ページ'}宛の送信でスクリーンショットが撮れ、記録に添付される`, async ({ fb }) => {
		await fb.fresh()
		await fb.enter()
		if (kind === 'element') await fb.pickEl(fb.para)
		else if (kind === 'text') await fb.selectText(0, 14)
		else {
			await fb.act(fb.page.locator('katachi-feedback-root .about'))
			await fb.page.waitForTimeout(400)
		}
		await fb.openInfo()
		ok('「載せる情報」にスクリーンショットがあり既定でオン', await fb.inUi('#chk-screenshot').isChecked())
		await fb.inUi('.dialog textarea').fill(`スクショ ${kind} ${fb.project}`)
		const before = files().length
		const t0 = Date.now()
		await fb.act(fb.inUi('.dialog .primary'))
		await fb.page.waitForTimeout(300)
		const label = await fb.inUi('.dialog .primary').textContent().catch(() => null)
		ok('送信ボタンが「撮影中…」になる', label === '撮影中…', String(label))
		while (Date.now() - t0 < 40_000 && files().length === before) await sleep(250)
		const f = files().length === before + 1 ? files().at(-1)! : null
		ok(`記録ができる（${Math.round((Date.now() - t0) / 1000)} 秒）`, f !== null)
		if (f) await checkAttachments(fb, kind, readRecord(f), kind !== 'page')
	})
}

test('スクリーンショットのチェックを外すと撮らず、「載せなかった情報」に出て、次に開いても外れたまま', async ({ fb }) => {
	await fb.fresh()
	await fb.enter()
	await fb.pickEl(fb.para)
	await fb.openInfo()
	await fb.inUi('#chk-screenshot').uncheck()
	await fb.inUi('.dialog textarea').fill('スクショ無し')
	const f = await fb.submitAndWait(15_000)
	ok('記録ができる', f !== null)
	if (f) {
		const md = readRecord(f)
		ok('attachments が無く、載せなかった情報にスクリーンショット', !/^attachments:/m.test(md) && /載せなかった情報:[^）]*スクリーンショット/.test(md) && !md.includes('## スクリーンショット'))
	}
	await fb.reenter()
	await fb.pickEl(fb.para)
	await fb.openInfo()
	ok('チェックの記憶（次も外れている）', (await fb.inUi('#chk-screenshot').isChecked()) === false)
})
