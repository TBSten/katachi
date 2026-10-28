import { describe, expect, it } from 'vitest'
import { checkPayload, isPayload, LIMITS } from '../core/limits'

const ok = () => ({ requestId: 'r1', comment: 'hello', sections: { page: [['URL', 'http://x/']] } })

describe('limits', () => {
	it('正しい payload は通る（target あり・節なしも）', () => {
		expect(checkPayload(ok())).toEqual({ ok: true })
		expect(isPayload({ ...ok(), target: 'page' })).toBe(true)
		expect(isPayload({ requestId: 'r', comment: '', sections: {} })).toBe(true)
	})

	it.each([
		['null', null],
		['文字列', 'x'],
		['requestId なし', { comment: '', sections: {} }],
		['requestId が空', { requestId: '', comment: '', sections: {} }],
		['comment が数値', { requestId: 'r', comment: 1, sections: {} }],
		['sections なし', { requestId: 'r', comment: '' }],
		['sections が配列', { requestId: 'r', comment: '', sections: [] }],
		['target が不正', { ...ok(), target: 'all' }],
		['知らない節', { ...ok(), sections: { evil: [['a', 'b']] } }],
		['節が配列ではない', { ...ok(), sections: { page: 'x' } }],
		['行が 2 要素ではない', { ...ok(), sections: { page: [['a']] } }],
		['行の値が文字列ではない', { ...ok(), sections: { page: [['a', 1]] } }],
	])('%s は拒否', (_name, v) => {
		expect(isPayload(v)).toBe(false)
	})

	it('大きさの上限: comment・行数・値・全体', () => {
		expect(checkPayload({ ...ok(), comment: 'a'.repeat(LIMITS.commentChars + 1) })).toMatchObject({ ok: false })
		const rows = Array.from({ length: LIMITS.rowsPerSection + 1 }, () => ['a', 'b'])
		expect(checkPayload({ ...ok(), sections: { page: rows } })).toMatchObject({ ok: false })
		expect(checkPayload({ ...ok(), sections: { page: [['a', 'b'.repeat(LIMITS.valueChars + 1)]] } })).toMatchObject({ ok: false })
		const many = Object.fromEntries(
			(['page', 'source', 'content', 'location', 'screen', 'browser', 'datetime', 'git', 'state', 'console', 'screenshot'] as const).map((k) => [
				k,
				Array.from({ length: 30 }, () => ['a', 'x'.repeat(LIMITS.valueChars)]),
			]),
		)
		expect(checkPayload({ ...ok(), sections: many })).toMatchObject({ ok: false, reason: expect.stringContaining('大きすぎ') })
	})

	it('理由を返す', () => {
		const r = checkPayload({ ...ok(), sections: { evil: [] } })
		expect(r).toEqual({ ok: false, reason: expect.stringContaining('evil') })
	})
})
