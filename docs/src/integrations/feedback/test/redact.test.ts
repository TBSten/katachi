import { describe, expect, it } from 'vitest'
import { redact } from '../core/redact'

describe('redact', () => {
	it.each([
		['Authorization: Bearer abcdefghijkl1234', 'abcdefghijkl1234'],
		['token=abcdef123456', 'abcdef123456'],
		['{"password": "hunter2hunter2"}', 'hunter2hunter2'],
		['Cookie: session=abc123; other=xyz', 'abc123'],
		['key sk-abcdefghijklmnopqrstuvwxyz', 'abcdefghijklmnop'],
		['ghp_abcdefghijklmnopqrstuvwxyz0123', 'abcdefghijklmnopqrst'],
		['jwt eyJhbGciOiJIUzI1.eyJzdWIiOiIxMjM0NTY.SflKxwRJSMeKKF2QT4', 'SflKxwRJSMeKKF2QT4'],
	])('%s を伏せる', (input, secret) => {
		const out = redact(input)
		expect(out).not.toContain(secret)
		expect(out).toContain('***')
	})

	it('秘密を含まない文は変えない（コードブロック・日本語・短い token も）', () => {
		const text = '## 見出し\n\n`const token = 1` と、token=ab のような短いものは残す。\nhttps://example.com/a?b=c'
		expect(redact(text)).toBe(text)
	})

	it('2 回掛けても同じ', () => {
		const once = redact('password=supersecret1 and Bearer abcdefghijkl')
		expect(redact(once)).toBe(once)
	})
})
