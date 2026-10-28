import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createController, type MemoCall } from '../memo/controller'
import { createMemoHandler } from '../memo/server'
import { createMemoryStore } from '../server/memory-store'
import { parseMarkdown } from '../memo/markdown'

const page = { path: '/katachi/ja/guides/x/', lang: 'ja', title: 'X', source: 'x.mdx' }

/** サーバー側の受け口を直結した call。送られた依頼を記録する。 */
function setup() {
	const store = createMemoryStore()
	const handler = createMemoHandler(store)
	const sent: unknown[] = []
	const call: MemoCall = (req) => {
		const requestId = `r${sent.length}`
		sent.push(req)
		return { requestId, promise: handler({ ...req, requestId }) as never, cancel() {} }
	}
	const make = () => createController({ call, page, pagePath: 'guides/x/', newId: (() => { let n = 0; return () => `s${++n}` })(), now: () => new Date('2026-10-29T00:00:00Z') })
	return { store, sent, make }
}
const settle = async () => { await vi.advanceTimersByTimeAsync(0) }

beforeEach(() => vi.useFakeTimers())
afterEach(() => vi.useRealTimers())

describe('自動保存', () => {
	it('入力が止まって 0.5 秒たってから保存する。連続した入力は 1 回にまとまる', async () => {
		const { make, sent, store } = setup()
		const c = make()
		await c.load()
		c.setPageNote({ values: {}, text: 'a' })
		await vi.advanceTimersByTimeAsync(400)
		c.setPageNote({ values: {}, text: 'ab' })
		await vi.advanceTimersByTimeAsync(400)
		expect(sent.filter((s) => (s as { op: string }).op === 'save')).toHaveLength(0)
		expect(c.status.kind).toBe('dirty')
		await vi.advanceTimersByTimeAsync(200)
		expect(sent.filter((s) => (s as { op: string }).op === 'save')).toHaveLength(1)
		expect(c.status.kind).toBe('saved')
		expect(parseMarkdown((await store.readDoc('memo/ja/guides/x.md'))!)?.note.text).toBe('ab')
	})
	it('保存中に変えた分は、終わってからもう 1 回保存する', async () => {
		const { make, store } = setup()
		const c = make()
		await c.load()
		c.setPageNote({ values: {}, text: '1' })
		await vi.advanceTimersByTimeAsync(500)
		c.setPageNote({ values: {}, text: '2' })
		c.flush()
		await settle()
		expect(parseMarkdown((await store.readDoc('memo/ja/guides/x.md'))!)?.note.text).toBe('2')
	})
	it('何も書いていないページにはファイルを作らない。ひな形だけは書く', async () => {
		const { make, store } = setup()
		const c = make()
		await c.load()
		c.addField('新しい')
		await vi.advanceTimersByTimeAsync(600)
		expect(await store.readDoc('memo/ja/guides/x.md')).toBeNull()
		expect(JSON.parse((await store.readDoc('memo/_fields.json'))!).fields).toEqual(['読みやすさ', '新しい'])
	})
	it('閉じるときの flush で待たずに保存する', async () => {
		const { make, store } = setup()
		const c = make()
		await c.load()
		c.setPageNote({ values: { 読みやすさ: '2' }, text: '' })
		c.flush()
		await settle()
		expect(parseMarkdown((await store.readDoc('memo/ja/guides/x.md'))!)?.note.values).toEqual({ 読みやすさ: '2' })
	})
	it('サーバーが失敗したらエラーを出し、次の変更で再び試す', async () => {
		const { make } = setup()
		let fail = true
		const c = createController({
			page, pagePath: 'guides/x/',
			call: (req) => ({ requestId: 'r', cancel() {}, promise: Promise.resolve(req.op === 'load' ? { requestId: 'r', ok: true as const, op: 'load' as const, text: null, fields: [] } : fail ? { requestId: 'r', ok: false as const, error: 'boom' } : { requestId: 'r', ok: true as const, op: 'save' as const, file: 'x' }) }),
		})
		void make
		await c.load()
		c.setPageNote({ values: {}, text: 'a' })
		await vi.advanceTimersByTimeAsync(600)
		expect(c.status).toEqual({ kind: 'error', message: 'boom' })
		fail = false
		c.setPageNote({ values: {}, text: 'ab' })
		await vi.advanceTimersByTimeAsync(600)
		expect(c.status.kind).toBe('saved')
	})
})

describe('読み直しと箇所', () => {
	const locator = { kind: 'element' as const, excerpt: '抜粋', source: 'x.mdx:3' }
	it('保存したものが次の load で戻る（ページのメモ・箇所・値）', async () => {
		const { make } = setup()
		const a = make()
		await a.load()
		a.setPageNote({ values: { 読みやすさ: '4' }, text: 'ページ' })
		const spot = a.addSpot(locator)!
		a.setSpotNote(spot.id, { values: {}, text: '箇所' })
		a.flush()
		await settle()
		const b = make()
		expect(await b.load()).toEqual({ ok: true, existed: true })
		expect(b.doc.note).toEqual({ values: { 読みやすさ: '4' }, text: 'ページ' })
		expect(b.doc.spots).toMatchObject([{ locator, text: '箇所' }])
	})
	it('箇所を消すとすぐ保存され、ファイルからも消える', async () => {
		const { make, store } = setup()
		const a = make()
		await a.load()
		const spot = a.addSpot(locator)!
		a.flush()
		await settle()
		a.removeSpot(spot.id)
		await settle()
		expect(parseMarkdown((await store.readDoc('memo/ja/guides/x.md'))!)?.spots).toEqual([])
		expect((await store.readDoc('memo/ja/guides/x.md'))).not.toContain('抜粋')
	})
	it('field: 別ページでも同じひな形が出て値は空。外しても値はファイルに残る', async () => {
		const { make, store } = setup()
		const a = make()
		await a.load()
		a.addField('密度')
		a.setPageNote({ values: { 密度: '高', 読みやすさ: '3' }, text: '' })
		a.flush()
		await settle()
		expect(a.template).toEqual(['読みやすさ', '密度'])
		a.removeField('密度')
		a.flush()
		await settle()
		expect(a.template).toEqual(['読みやすさ'])
		expect(parseMarkdown((await store.readDoc('memo/ja/guides/x.md'))!)?.note.values).toEqual({ 密度: '高', 読みやすさ: '3' })
		// 別のページ
		const other = createController({ call: (() => { const h = createMemoHandler(store); return (req: never) => ({ requestId: 'r', cancel() {}, promise: h({ ...(req as object), requestId: 'r' }) as never }) })() as unknown as MemoCall, page: { ...page, path: '/katachi/ja/other/' }, pagePath: 'other/' })
		await other.load()
		expect(other.template).toEqual(['読みやすさ'])
		expect(other.doc.note).toEqual({ values: {}, text: '' })
	})
	it('読み直せない既存ファイルは上書きしない', async () => {
		const { make, store, sent } = setup()
		await store.writeDoc('memo/ja/guides/x.md', '# 手で書いたメモ\n')
		const c = make()
		const r = await c.load()
		expect(r.ok).toBe(false)
		c.setPageNote({ values: {}, text: 'x' })
		c.flush()
		await vi.advanceTimersByTimeAsync(1000)
		expect(sent.filter((s) => (s as { op: string }).op === 'save')).toHaveLength(0)
		expect(await store.readDoc('memo/ja/guides/x.md')).toBe('# 手で書いたメモ\n')
	})
})
