import { describe, expect, it } from 'vitest'
import { applyStatus, pageUrlOf, parseLine, parseReview, priorityTier, quoteMatches, relocate, removeItem } from '../review/model'

const FILE = `---
title: リリース前チェック
base: 1a7a072
---

前置き（読まない）

## [p3] dsl.gradle を個別 import している
- file: ja/get-started/first-architecture.mdx
- line: 168
- quote: \`import me.tbsten.katachi.dsl.gradle.gradle\`

- 該当: 本文の箇条書き（メタではない）
- file: これもメタではない（本文の後ろ）

## ページ全体への指摘
- file: docs/src/content/docs/ja/roadmap.mdx
- priority: 5

本文

\`\`\`md
## コードの中の見出しは指摘ではない
\`\`\`
`

describe('parseReview', () => {
	const set = parseReview('s1', FILE)
	it('frontmatter の title と base を読む', () => {
		expect(set).toMatchObject({ name: 's1', title: 'リリース前チェック', base: '1a7a072', errors: [] })
	})
	it('見出しごとに 1 件。コードフェンスの中の ## は見出しにしない', () => {
		expect(set.items.map((i) => i.title)).toEqual(['dsl.gradle を個別 import している', 'ページ全体への指摘'])
		expect(set.items[1]!.body).toContain('## コードの中の見出しは指摘ではない')
	})
	it('見出しの [pN] と - priority: を優先度にする', () => {
		expect(set.items.map((i) => i.priority)).toEqual([3, 5])
	})
	it('メタは見出しの直後に続く既知のキーだけ。本文の箇条書きは飲み込まない', () => {
		const a = set.items[0]!
		expect(a).toMatchObject({ file: 'ja/get-started/first-architecture.mdx', line: { start: 168, end: 168 }, quote: 'import me.tbsten.katachi.dsl.gradle.gradle', status: 'open' })
		expect(a.body.startsWith('- 該当: 本文の箇条書き')).toBe(true)
		expect(a.body).toContain('- file: これもメタではない')
	})
	it('line が無ければページ全体への指摘', () => {
		expect(set.items[1]!.line).toBeNull()
	})
	it('id は file と見出しから決まり、ほかの指摘を消しても変わらない', () => {
		const after = parseReview('s1', removeItem('s1', FILE, set.items[0]!.id))
		expect(after.items.map((i) => i.id)).toEqual([set.items[1]!.id])
		expect(set.items[0]!.id).toMatch(/^s1\/[0-9a-z]{7}$/)
	})
	it('同じ file・同じ見出しが 2 つあれば、2 つめの id に -2 を付ける', () => {
		const t = '## 同じ\n- file: a.mdx\n\n## 同じ\n- file: a.mdx\n'
		const ids = parseReview('s', t).items.map((i) => i.id)
		expect(ids[1]).toBe(`${ids[0]}-2`)
	})
	it('壊れた指摘は飛ばして理由を errors に書く（投げない）', () => {
		const t = '## file が無い\n本文\n\n## 行が読めない\n- file: a.mdx\n- line: abc\n- status: done\n\n## \n'
		const s = parseReview('s', t)
		expect(s.items.map((i) => i.title)).toEqual(['行が読めない'])
		expect(s.items[0]).toMatchObject({ line: null, status: 'open' })
		expect(s.errors.join('\n')).toMatch(/L1: 「- file:」がありません/)
		expect(s.errors.join('\n')).toMatch(/line の形が読めません: abc/)
		expect(s.errors.join('\n')).toMatch(/status は open か resolved です: done/)
	})
	it('CRLF のファイルも読める', () => {
		expect(parseReview('s', '## a\r\n- file: x.mdx\r\n- line: 3\r\n\r\n本文\r\n').items[0]).toMatchObject({ title: 'a', line: { start: 3, end: 3 }, body: '本文' })
	})
	it('frontmatter が無くても読める（title はファイル名）', () => {
		expect(parseReview('名前', '## a\n- file: x\n')).toMatchObject({ title: '名前', items: [{ title: 'a' }] })
	})
})

describe('parseLine', () => {
	it.each([
		['168', { start: 168, end: 168 }],
		['L168', { start: 168, end: 168 }],
		['160-186', { start: 160, end: 186 }],
		['L160〜L186', { start: 160, end: 186 }],
		[' 3 - 5 ', { start: 3, end: 5 }],
	])('%s', (v, want) => expect(parseLine(v)).toEqual(want))
	it.each(['0', '5-3', 'abc', '', '1,2'])('読めない: %s', (v) => expect(parseLine(v)).toBeNull())
})

describe('applyStatus', () => {
	const id = parseReview('s1', FILE).items[0]!.id
	it('解決済みにすると、メタの最後に status・resolved-at・note を足す。ほかの行は変えない', () => {
		const out = applyStatus('s1', FILE, id, { status: 'resolved', note: '直した\n2 行目', at: '2026-11-09T00:00:00.000Z' })
		expect(out).toContain('- quote: `import me.tbsten.katachi.dsl.gradle.gradle`\n- status: resolved\n- resolved-at: 2026-11-09T00:00:00.000Z\n- note: 直した 2 行目\n\n- 該当:')
		expect(out.replace(/- status: resolved\n- resolved-at: [^\n]+\n- note: [^\n]+\n/, '')).toBe(FILE)
		expect(parseReview('s1', out).items[0]).toMatchObject({ id, status: 'resolved', note: '直した 2 行目', resolvedAt: '2026-11-09T00:00:00.000Z' })
	})
	it('一言が空なら note の行は書かない。2 回解決済みにしても行は重ならない', () => {
		const once = applyStatus('s1', FILE, id, { status: 'resolved', at: 'T1' })
		const twice = applyStatus('s1', once, id, { status: 'resolved', note: 'b', at: 'T2' })
		expect(once).not.toContain('- note:')
		expect(twice.match(/- status:/g)).toHaveLength(1)
		expect(twice).toContain('- resolved-at: T2\n- note: b')
	})
	it('戻すと、解決済みの印と一言を消して元の文に戻る', () => {
		const resolved = applyStatus('s1', FILE, id, { status: 'resolved', note: 'x', at: 'T' })
		expect(applyStatus('s1', resolved, id, { status: 'open' })).toBe(FILE)
	})
	it('agent の差し戻しは ai-note を書き換え、解決済みの印を消す', () => {
		const resolved = applyStatus('s1', FILE, id, { status: 'resolved', note: 'x', at: 'T' })
		const back = applyStatus('s1', resolved, id, { status: 'open', aiNote: 'まだ L168 が個別 import のまま' })
		expect(parseReview('s1', back).items[0]).toMatchObject({ status: 'open', aiNote: 'まだ L168 が個別 import のまま' })
		expect(back).not.toContain('resolved')
		const again = applyStatus('s1', back, id, { status: 'open', aiNote: '2 回目' })
		expect(again.match(/- ai-note:/g)).toHaveLength(1)
	})
	it('人が戻しても agent の ai-note は残す', () => {
		const back = applyStatus('s1', FILE, id, { status: 'open', aiNote: '理由' })
		const res = applyStatus('s1', back, id, { status: 'resolved', at: 'T' })
		expect(parseReview('s1', applyStatus('s1', res, id, { status: 'open' })).items[0]!.aiNote).toBe('理由')
	})
	it('メタの無い指摘（file だけ）でも書ける', () => {
		const t = '## a\n- file: x\n本文'
		const i = parseReview('s', t).items[0]!.id
		expect(applyStatus('s', t, i, { status: 'resolved', at: 'T' })).toBe('## a\n- file: x\n- status: resolved\n- resolved-at: T\n本文')
	})
	it('見つからない id は投げる', () => {
		expect(() => applyStatus('s1', FILE, 's1/nope', { status: 'open' })).toThrow(/見つかりません/)
	})
})

describe('removeItem', () => {
	it('見出しから次の見出しの手前まで消す', () => {
		const set = parseReview('s1', FILE)
		const out = removeItem('s1', FILE, set.items[0]!.id)
		expect(out).not.toContain('dsl.gradle')
		expect(out).toContain('前置き（読まない）\n\n## ページ全体への指摘')
		expect(removeItem('s1', out, set.items[1]!.id).trim()).toBe(FILE.split('\n## ')[0]!.trim())
	})
})

describe('relocate', () => {
	const src = ['a', 'b', 'import x.y', 'c', 'd', 'import x.y', 'e']
	it('quote が書かれた行にあれば、そのまま', () => {
		expect(relocate({ start: 3, end: 3 }, 'import x.y', src)).toEqual({ line: { start: 3, end: 3 }, place: 'ok' })
	})
	it('ずれていれば、いちばん近い quote の行へ動かす（範囲は幅を保つ）', () => {
		expect(relocate({ start: 5, end: 5 }, 'import x.y', src)).toEqual({ line: { start: 6, end: 6 }, place: 'moved' })
		expect(relocate({ start: 1, end: 2 }, '`import  x.y`', src)).toEqual({ line: { start: 3, end: 4 }, place: 'moved' })
	})
	it('quote が見つからなければ書かれた行のまま lost', () => {
		expect(relocate({ start: 2, end: 2 }, 'nothing', src)).toEqual({ line: { start: 2, end: 2 }, place: 'lost' })
	})
	it('quote が無ければ行のまま。ファイルより後ろの行は lost', () => {
		expect(relocate({ start: 2, end: 2 }, undefined, src).place).toBe('ok')
		expect(relocate({ start: 99, end: 99 }, undefined, src).place).toBe('lost')
	})
	it('行が無く quote があれば、quote の最初の行にする。無ければページ全体', () => {
		expect(relocate(null, 'import x.y', src)).toEqual({ line: { start: 3, end: 3 }, place: 'ok' })
		expect(relocate(null, 'zzz', src)).toEqual({ line: null, place: 'page' })
		expect(relocate(null, undefined, src)).toEqual({ line: null, place: 'page' })
	})
})

describe('pageUrlOf', () => {
	it.each([
		['docs/src/content/docs/ja/guides/role.mdx', '/katachi/ja/guides/role/'],
		['docs/src/content/docs/ja/index.mdx', '/katachi/ja/'],
		['docs/src/content/docs/guides/x/index.md', '/katachi/guides/x/'],
		['README.ja.md', null],
		['docs/src/content/docs/ja/x.ts', null],
	])('%s', (f, want) => expect(pageUrlOf(f, '/katachi')).toBe(want))
})

describe('priorityTier', () => {
	it.each([[9, 'high'], [7, 'high'], [6, 'mid'], [5, 'mid'], [4, 'low'], [3, 'low'], [2, 'min'], [0, 'min'], [null, 'none']] as const)('%s → %s', (p, t) => expect(priorityTier(p)).toBe(t))
})

describe('quoteMatches', () => {
	it.each([
		['description: アーキテクチャが二重管理されて腐る問題と、ほか', 'description: アーキテクチャが…腐る問題と、…', true],
		['a b c', 'a ... c', true],
		['a b c', 'c … a', false],
		['`x`  y', 'x y', true],
		['abc', '…', false],
	])('%s / %s', (t, q, want) => expect(quoteMatches(t, q)).toBe(want))
})
