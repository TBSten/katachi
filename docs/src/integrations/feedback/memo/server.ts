// Memo のサーバー側。読み込み・保存の依頼を Store の readDoc / writeDoc につなぐ（node: は Store の中だけ）。
import { EVENT_MEMO_REQUEST, EVENT_MEMO_RESULT, type MemoRequest, type MemoResult } from '../core/protocol'
import type { Store } from '../server/store-types'
import { FIELDS_PATH, MEMO_LIMITS, memoDocPath, parseFields, sanitizeFields, serializeFields } from './model'

/** 依頼の形を確かめる。理由つきで返す。 */
export function checkRequest(v: unknown): { ok: true; req: MemoRequest } | { ok: false; requestId?: string; reason: string } {
	const o = (typeof v === 'object' && v !== null ? v : {}) as Record<string, unknown>
	const requestId = typeof o.requestId === 'string' && o.requestId.length > 0 && o.requestId.length <= 100 ? o.requestId : undefined
	const fail = (reason: string) => ({ ok: false as const, requestId, reason })
	if (!requestId) return fail('requestId が不正です')
	if (typeof o.lang !== 'string' || typeof o.pagePath !== 'string') return fail('lang / pagePath が文字列ではありません')
	if (o.pagePath.length > 1000) return fail('pagePath が長すぎます')
	if (o.op === 'load') return { ok: true, req: { requestId, op: 'load', lang: o.lang, pagePath: o.pagePath } }
	if (o.op === 'save') {
		if (o.text !== null && typeof o.text !== 'string') return fail('text が文字列ではありません')
		if (typeof o.text === 'string' && o.text.length > MEMO_LIMITS.docChars) return fail(`メモが大きすぎます（上限 ${MEMO_LIMITS.docChars} 文字）`)
		if (!Array.isArray(o.fields)) return fail('fields が配列ではありません')
		return { ok: true, req: { requestId, op: 'save', lang: o.lang, pagePath: o.pagePath, text: o.text, fields: sanitizeFields(o.fields) } }
	}
	return fail('op が不正です')
}

/**
 * 依頼 1 件を処理する。書き込みはひな形も含めて 1 本ずつ順に行う（メモの保存が追い越し合わないように）。
 * 例外は投げず、結果の ok:false にする。
 */
export function createMemoHandler(store: Store) {
	let chain: Promise<unknown> = Promise.resolve()
	const serial = <T>(job: () => Promise<T>): Promise<T> => {
		const next = chain.then(job, job)
		chain = next.catch(() => {})
		return next
	}

	async function handle(req: MemoRequest): Promise<MemoResult> {
		const path = memoDocPath(req.lang, req.pagePath)
		if (req.op === 'load') {
			const [text, fields] = await Promise.all([store.readDoc(path), store.readDoc(FIELDS_PATH)])
			return { requestId: req.requestId, ok: true, op: 'load', text, fields: parseFields(fields) }
		}
		// ひな形は変わったときだけ書く。
		const current = await store.readDoc(FIELDS_PATH)
		const next = serializeFields(req.fields)
		if (current !== next) await store.writeDoc(FIELDS_PATH, next)
		if (req.text === null) return { requestId: req.requestId, ok: true, op: 'save', file: null }
		await store.writeDoc(path, req.text)
		return { requestId: req.requestId, ok: true, op: 'save', file: path }
	}

	return async (data: unknown): Promise<MemoResult | null> => {
		const check = checkRequest(data)
		if (!check.ok) return check.requestId ? { requestId: check.requestId, ok: false, error: check.reason } : null
		try {
			return await serial(() => handle(check.req))
		} catch (e) {
			return { requestId: check.req.requestId, ok: false, error: e instanceof Error ? e.message : String(e) }
		}
	}
}

/** dev サーバーの toolbar に受け口を登録する。 */
export function registerMemoServer(toolbar: { on(event: string, cb: (data: unknown) => void): void; send(event: string, data: unknown): void }, store: Store) {
	const handler = createMemoHandler(store)
	toolbar.on(EVENT_MEMO_REQUEST, async (data) => {
		const result = await handler(data)
		if (result) toolbar.send(EVENT_MEMO_RESULT, result)
	})
}
