import { describe, expect, it } from 'vitest'
import { checkPayload } from '../core/limits'
import { CONSOLE_LIMITS, LOG_GLOBAL, consoleSection, readLogEntries, type LogEntry, type LogKind } from '../core/console-section'
import { LOG_BUFFER_SCRIPT } from '../inline/log-buffer'

const at = (t: number, k: LogKind, m: string): LogEntry => ({ t, k, m })
const T0 = Date.UTC(2026, 9, 29, 1, 2, 3, 456)
const asObj = (s: ReturnType<typeof consoleSection>) => Object.fromEntries(s)

describe('consoleSection', () => {
	it('0 件なら「なし」と書く', () => {
		const s = consoleSection([])
		expect(s).toHaveLength(1)
		expect(s[0][1]).toMatch(/^なし/)
	})

	it('先頭に error と warn の件数、続けて古い順の一覧を並べる', () => {
		const s = consoleSection([at(T0, 'log', 'hello'), at(T0 + 1, 'error', 'boom'), at(T0 + 2, 'warn', 'careful')])
		expect(s[0][0]).toBe('件数')
		expect(s[0][1]).toContain('error 1 件 / warn 1 件 / 全 3 件')
		const list = s.slice(-3)
		expect(list.map(([l]) => l)).toEqual(['01:02:03.456 log', '01:02:03.457 error', '01:02:03.458 warn'])
	})

	it('要約には error と warn だけが入り、log は入らない', () => {
		const s = consoleSection([at(T0, 'log', 'quiet'), at(T0, 'resource', '<img> の読み込みに失敗: /x.png'), at(T0, 'warn', 'w')])
		const summary = s.slice(1, 3)
		expect(summary).toEqual([
			['error', '[resource] <img> の読み込みに失敗: /x.png'],
			['warn', '[warn] w'],
		])
	})

	it('連続する同じ文は ×N にまとめ、要約も種類ごとに数える', () => {
		const rep = [at(T0, 'error', 'x'), at(T0 + 1, 'error', 'x'), at(T0 + 2, 'error', 'x'), at(T0 + 3, 'log', 'y'), at(T0 + 4, 'error', 'x')]
		const s = consoleSection(rep)
		expect(s[1]).toEqual(['error', '[error] x ×4'])
		const list = s.slice(2)
		expect(list.map(([, v]) => v)).toEqual(['x ×3', 'y', 'x'])
	})

	it('要約は種類数の上限で切り、省略を行で残す', () => {
		const many = Array.from({ length: CONSOLE_LIMITS.summaryRows + 5 }, (_, i) => at(T0 + i, 'error', `e${i}`))
		const s = consoleSection(many)
		expect(s.filter(([l]) => l === 'error')).toHaveLength(CONSOLE_LIMITS.summaryRows)
		expect(asObj(s)['要約の省略']).toBe('ほか 5 種類')
	})

	it('一覧は行数の上限を超えたら古い方を捨て、省略を先頭に書く', () => {
		const many = Array.from({ length: CONSOLE_LIMITS.listRows + 30 }, (_, i) => at(T0 + i, 'log', `m${i}`))
		const s = consoleSection(many)
		const omitted = s.find(([l]) => l === '省略')
		expect(omitted?.[1]).toContain('古い方の 30 行')
		expect(s[s.length - 1][1]).toBe(`m${many.length - 1}`)
		expect(s.length).toBeLessThanOrEqual(CONSOLE_LIMITS.listRows + 3)
	})

	it('合計の大きさの上限を超えないよう、新しい方を残す', () => {
		const big = Array.from({ length: 200 }, (_, i) => at(T0 + i, 'log', `${i}:` + 'x'.repeat(CONSOLE_LIMITS.messageChars)))
		const s = consoleSection(big)
		const chars = s.reduce((n, [l, v]) => n + l.length + v.length, 0)
		expect(chars).toBeLessThan(CONSOLE_LIMITS.totalChars + 3_000)
		expect(s[s.length - 1][1].startsWith('199:')).toBe(true)
	})

	it('出来た節は受け口の検査（行数・長さ）を通る', () => {
		const many = Array.from({ length: 3000 }, (_, i) => at(T0 + i, i % 2 ? 'error' : 'warn', `e${i}`))
		const check = checkPayload({ requestId: 'r', comment: 'c', sections: { console: consoleSection(many) } })
		expect(check).toEqual({ ok: true })
	})
})

describe('readLogEntries', () => {
	it('バッファが無い・壊れているときは空', () => {
		expect(readLogEntries({})).toEqual([])
		expect(readLogEntries({ [LOG_GLOBAL]: { entries: () => 'x' } })).toEqual([])
		expect(readLogEntries({ [LOG_GLOBAL]: { entries: () => { throw new Error('x') } } })).toEqual([])
	})
	it('形の合う件だけ読む', () => {
		const good = at(T0, 'log', 'a')
		expect(readLogEntries({ [LOG_GLOBAL]: { entries: () => [good, { k: 'log' }, null] } })).toEqual([good])
	})
})

describe('LOG_BUFFER_SCRIPT', () => {
	// 実際のページと同じく、script を window 相当の場で動かして溜まり方を確かめる。
	const run = () => {
		const listeners: Record<string, Array<(e: unknown) => void>> = {}
		const calls: string[] = []
		const win: Record<string, unknown> = {
			addEventListener: (n: string, f: (e: unknown) => void) => (listeners[n] ??= []).push(f),
		}
		const con = { error: (...a: unknown[]) => calls.push(`error:${a.join(' ')}`), warn: () => {}, log: () => {} }
		// import() は dev の client が無いので失敗させる（失敗しても落ちないことの確認を兼ねる）。
		new Function('window', 'console', LOG_BUFFER_SCRIPT.replace("import('/@vite/client')", 'Promise.reject(new Error("no vite"))'))(win, con)
		return { win, con, listeners, calls }
	}

	it('console を溜めつつ元の出力も呼ぶ', () => {
		const { win, con, calls } = run()
		con.error('a', { b: 1 })
		const got = readLogEntries(win)
		expect(got.map((e) => [e.k, e.m])).toEqual([['error', 'a {"b":1}']])
		expect(calls).toEqual(['error:a [object Object]'])
	})

	it('件数と 1 件の長さに上限がある', () => {
		const { win, con } = run()
		for (let i = 0; i < CONSOLE_LIMITS.bufferEntries + 10; i++) con.error(`n${i}`)
		con.error('y'.repeat(CONSOLE_LIMITS.messageChars + 500))
		const got = readLogEntries(win)
		expect(got).toHaveLength(CONSOLE_LIMITS.bufferEntries)
		expect(got[got.length - 1].m.length).toBe(CONSOLE_LIMITS.messageChars + 1)
		expect(got[0].m).toBe('n11')
	})

	it('未捕捉の例外・rejection・読み込み失敗を種類つきで溜める', () => {
		const { win, listeners } = run()
		listeners.error[0]({ message: 'bad', filename: 'a.js', lineno: 3, colno: 4 })
		listeners.error[0]({ target: { tagName: 'IMG', src: '/broken.png' } })
		listeners.unhandledrejection[0]({ reason: new Error('nope') })
		const got = readLogEntries(win)
		expect(got.map((e) => e.k)).toEqual(['exception', 'resource', 'rejection'])
		expect(got[0].m).toBe('bad (a.js:3:4)')
		expect(got[1].m).toContain('/broken.png')
		expect(got[2].m).toContain('nope')
	})

	it('2 回入れても 1 つしか置かない', () => {
		const { win } = run()
		const first = win[LOG_GLOBAL]
		new Function('window', 'console', LOG_BUFFER_SCRIPT)(win, { error() {}, warn() {}, log() {} })
		expect(win[LOG_GLOBAL]).toBe(first)
	})
})
