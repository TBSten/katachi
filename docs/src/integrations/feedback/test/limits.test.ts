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

	it('添付: PNG の base64 は通り、base64 は全体の上限に数えない。件数・名前・中身・大きさは拒否', () => {
		const png = { name: 'window.png', mime: 'image/png', base64: 'iVBORw0KGgo=' }
		expect(checkPayload({ ...ok(), attachments: [png] })).toEqual({ ok: true })
		// 2MB を超える base64 でも、10MB 以内の添付なら通る（JSON の上限は添付を除いて数える）。
		const big = { ...png, base64: 'A'.repeat(LIMITS.payloadChars + 4) }
		expect(checkPayload({ ...ok(), attachments: [big] })).toEqual({ ok: true })
		expect(checkPayload({ ...ok(), attachments: Array.from({ length: LIMITS.attachmentCount + 1 }, () => png) })).toMatchObject({ ok: false })
		expect(checkPayload({ ...ok(), attachments: [{ ...png, name: '../x.png' }] })).toMatchObject({ ok: false })
		expect(checkPayload({ ...ok(), attachments: [{ ...png, mime: 'text/html' }] })).toMatchObject({ ok: false })
		expect(checkPayload({ ...ok(), attachments: [{ ...png, base64: '<script>' }] })).toMatchObject({ ok: false })
		expect(checkPayload({ ...ok(), attachments: [{ ...png, base64: 'A'.repeat(Math.ceil(LIMITS.attachmentBytes / 3) * 4 + 4) }] })).toMatchObject({ ok: false })
		expect(checkPayload({ ...ok(), screenshotError: 'x'.repeat(LIMITS.errorChars + 1) })).toMatchObject({ ok: false })
	})
})
