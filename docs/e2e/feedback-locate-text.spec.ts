// テキストを選んだときに、記録がその場所を一意に指すこと。選び方の違い（短い語・長い文・段落をまたぐ・繰り返す語・リスト・表・コード・Aside）ごとに 1 件。
// 新しいページで「テキストフラグメントURL」を開き、選んだ場所が実際にハイライトされるところまで確かめる。PC の Chromium だけ。
import { test, ok, BASIC } from './helpers/fixtures'
import { Locate, topRuns } from './helpers/locate'
import { bullet, parseRecord } from './helpers/store'
import { textCases } from './helpers/text-cases'

for (const tc of textCases) {
	test(`[テキスト選択] ${tc.name}: 選んだ文字がソース・XPath・テキストフラグメントで指せる`, async ({ fb, context }) => {
		const loc = new Locate(fb)
		const tag = `[${tc.name}]`
		await fb.fresh(BASIC)
		await fb.page.waitForTimeout(300)
		const { handle, text } = await loc.openText(tc.select)
		ok(`${tag} popover が開く`, await loc.pop().isVisible(), JSON.stringify(text.slice(0, 20)))
		const md = await loc.submit({ comment: `${tc.name}の検証` })
		ok(`${tag} 記録ができる`, !!md)
		if (!md) return
		await loc.sourceOk(tag, md, handle, text)
		const front = await loc.locationOk(tag, md, handle)
		ok(`${tag} ブロック内の開始位置が数字`, /^- ブロック内の開始位置: \d+$/m.test(md), bullet(md, 'ブロック内の開始位置'))
		const whole = bullet(md, 'ブロックの全文') ?? ''
		ok(`${tag} ブロックの全文が選んだ語を含む`, whole.length > 0 && !!topRuns(text, 2).find((r) => whole.includes(r)))
		const frag = bullet(md, 'テキストフラグメントURL')
		ok(`${tag} テキストフラグメントの URL がある`, !!frag && frag.includes('#:~:text='), frag)
		if (frag) {
			const page2 = await context.newPage()
			await loc.fragmentHighlight(page2, tag, frag, parseRecord(md).front.xpath ?? front.xpath, tc.name.replace(/[^\p{L}\p{N}]/gu, ''))
			await page2.close()
		}
	})
}
