import { describe, expect, it } from 'vitest'
import { SHOT_ELEMENT, SHOT_WINDOW, type SavePayload } from '../core/protocol'
import { toMarkdown, toCopyMarkdown } from '../core/record'
import { createMemoryStore } from '../server/memory-store'
import { storeScreenshots } from '../server/screenshots'

const png = Buffer.from([0x89, 0x50, 0x4e, 0x47]).toString('base64')
const base: SavePayload = { requestId: 'r', comment: '崩れている', kind: 'element', sections: { page: [['URL', 'http://x/']], screenshot: [['撮影', '送るときに撮ります']] } }
const att = (name: string) => ({ name, mime: 'image/png' as const, base64: png })
const now = new Date(2026, 9, 29, 10)

describe('storeScreenshots', () => {
	it('添付を置き、参照を節と refs に返す', async () => {
		const store = createMemoryStore()
		const r = await storeScreenshots(store, { ...base, attachments: [att(SHOT_WINDOW), att(SHOT_ELEMENT)] }, now)
		expect(r.refs).toHaveLength(2)
		expect(r.section!.map(([, v]) => v)).toEqual(r.refs)
		expect([...store.attachments.values()][0]).toEqual(new Uint8Array([0x89, 0x50, 0x4e, 0x47]))
	})
	it('節をチェックしていなければ何もしない', async () => {
		const { screenshot: _s, ...sections } = base.sections
		const store = createMemoryStore()
		expect(await storeScreenshots(store, { ...base, sections, attachments: [att(SHOT_WINDOW)] }, now)).toEqual({ section: null, refs: [], dir: null })
		expect(store.attachments.size).toBe(0)
	})
	it('撮れなかったときは理由を節に書く（添付なし）', async () => {
		const r = await storeScreenshots(createMemoryStore(), { ...base, screenshotError: '描画に失敗しました' }, now)
		expect(r).toEqual({ section: [['撮れませんでした', '描画に失敗しました']], refs: [], dir: null })
		expect((await storeScreenshots(createMemoryStore(), base, now)).section![0]![1]).toContain('届きませんでした')
	})
	it('置けなかったときも投げずに理由を返す', async () => {
		const r = await storeScreenshots(createMemoryStore(), { ...base, attachments: [att('../bad.png')] }, now)
		expect(r.refs).toEqual([])
		expect(r.section![0]![1]).toContain('置けませんでした')
	})
	it('記録に attachments と節が入り、コピーには入らない', async () => {
		const r = await storeScreenshots(createMemoryStore(), { ...base, attachments: [att(SHOT_WINDOW)] }, now)
		const md = toMarkdown({ ...base, sections: { ...base.sections, screenshot: r.section! } }, { created: now, id: '261029-001', attachments: r.refs })
		expect(md).toMatch(/attachments: \["memory:attach\/1\/window\.png"\]/)
		expect(md).toContain('## スクリーンショット')
		expect(md).toContain('- 画面全体（選んだ場所を赤枠で囲んだもの）: memory:attach/1/window.png')
		expect(toCopyMarkdown(base, now)).not.toContain('スクリーンショット')
	})
})
