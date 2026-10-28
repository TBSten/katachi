import { describe, expect, it } from 'vitest'
import { createMemoHandler, checkRequest } from '../memo/server'
import { createMemoryStore } from '../server/memory-store'
import { MEMO_LIMITS } from '../memo/model'

const save = (o: object = {}) => ({ requestId: 'r', op: 'save', lang: 'ja', pagePath: 'guides/x/', text: 'T', fields: ['読みやすさ'], ...o })

describe('Memo の受け口', () => {
	it('保存すると memo/<言語>/<ページ>.md とひな形ができ、読み込みで戻る', async () => {
		const store = createMemoryStore()
		const h = createMemoHandler(store)
		expect(await h(save())).toMatchObject({ ok: true, op: 'save', file: 'memo/ja/guides/x.md' })
		expect(await store.readDoc('memo/ja/guides/x.md')).toBe('T')
		expect(JSON.parse((await store.readDoc('memo/_fields.json'))!)).toEqual({ fields: ['読みやすさ'] })
		expect(await h({ requestId: 'r2', op: 'load', lang: 'ja', pagePath: 'guides/x/' })).toEqual({ requestId: 'r2', ok: true, op: 'load', text: 'T', fields: ['読みやすさ'] })
	})
	it('未保存のページは text が null・ひな形は既定', async () => {
		const h = createMemoHandler(createMemoryStore())
		expect(await h({ requestId: 'r', op: 'load', lang: 'en', pagePath: '' })).toEqual({ requestId: 'r', ok: true, op: 'load', text: null, fields: ['読みやすさ'] })
	})
	it('ひな形は全ページ共通: 別のページの読み込みにも出る', async () => {
		const h = createMemoHandler(createMemoryStore())
		await h(save({ fields: ['読みやすさ', '新しい'] }))
		expect(await h({ requestId: 'r', op: 'load', lang: 'ja', pagePath: 'other/' })).toMatchObject({ text: null, fields: ['読みやすさ', '新しい'] })
	})
	it('使えないパス・形の違う依頼は ok:false（投げない）', async () => {
		const h = createMemoHandler(createMemoryStore())
		expect(await h(save({ pagePath: '../x' }))).toMatchObject({ ok: false })
		expect(await h(save({ lang: '../x' }))).toMatchObject({ ok: false })
		expect(await h(save({ text: 1 }))).toMatchObject({ ok: false, requestId: 'r' })
		expect(await h({ nope: 1 })).toBeNull()
	})
	it('大きすぎる本文は断る', () => {
		expect(checkRequest(save({ text: 'x'.repeat(MEMO_LIMITS.docChars + 1) }))).toMatchObject({ ok: false })
	})
	it('同時の保存は送った順に書かれる', async () => {
		const store = createMemoryStore()
		const h = createMemoHandler(store)
		await Promise.all([1, 2, 3, 4, 5].map((i) => h(save({ requestId: `r${i}`, text: `v${i}` }))))
		expect(await store.readDoc('memo/ja/guides/x.md')).toBe('v5')
	})
})
