// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest'
import { elementsOf, MAX_ELEMENTS, pickOf, togglePick } from '../shared/multi'
import { containerOf, rectOfPick } from '../shared/pick'
import { execFileSync } from 'node:child_process'
import { resolve } from 'node:path'

const make = (n: number) => Array.from({ length: n }, () => document.createElement('p'))

describe('togglePick（Shift+クリック）', () => {
	it('足す → 2 つ以上は elements、1 つは element、0 は null', () => {
		const [a, b] = make(2) as unknown as [Element, Element]
		const one = togglePick(null, a!).pick
		expect(one).toEqual({ kind: 'element', el: a })
		const two = togglePick(one, b!).pick
		expect(two).toEqual({ kind: 'elements', els: [a, b] })
		expect(togglePick(two, a!).pick).toEqual({ kind: 'element', el: b })
		expect(togglePick(togglePick(two, a!).pick, b!).pick).toBeNull()
	})
	it('上限を超えると足さず full を返す。外すことはできる', () => {
		const els = make(MAX_ELEMENTS + 1)
		let pick = pickOf(els.slice(0, MAX_ELEMENTS))
		const over = togglePick(pick, els[MAX_ELEMENTS]!)
		expect(over.full).toBe(true)
		expect(elementsOf(over.pick)).toHaveLength(MAX_ELEMENTS)
		pick = togglePick(pick, els[0]!).pick
		expect(elementsOf(pick)).toHaveLength(MAX_ELEMENTS - 1)
		expect(togglePick(pick, els[MAX_ELEMENTS]!).full).toBe(false)
	})
	it('テキストやページの選択からは数え直す（呼び出し側が null を渡す）', () => {
		expect(elementsOf({ kind: 'page' })).toEqual([])
	})
})

describe('複数の要素の矩形と共通の祖先', () => {
	it('containerOf は全部を含む最も内側の要素', () => {
		document.body.innerHTML = '<main><section><p id="a"></p><p id="b"></p></section><aside id="c"></aside></main>'
		const q = (s: string) => document.querySelector(s)!
		expect(containerOf({ kind: 'elements', els: [q('#a'), q('#b')] }).tagName).toBe('SECTION')
		expect(containerOf({ kind: 'elements', els: [q('#a'), q('#c')] }).tagName).toBe('MAIN')
		expect(rectOfPick({ kind: 'elements', els: [q('#a'), q('#b')] })).toBeInstanceOf(DOMRect)
	})
})

describe('cli の elements の扱い', () => {
	// cli.mjs は shebang と registerHooks を持つので、vite には読ませず Node で別に走らせる。
	const cli = resolve(import.meta.dirname, '../server/cli.mjs')
	const run = (expr: string) =>
		JSON.parse(execFileSync('node', ['--input-type=module', '-e', `import * as c from ${JSON.stringify(cli)}; console.log(JSON.stringify(${expr}))`], { encoding: 'utf8' }))
	it('一覧のソース欄は先頭と件数。要約に kind が出る', () => {
		expect(run(`[c.sourceText(['a:1-2', 'b:3', 'c:4']), c.sourceText(['a:1-2']), c.sourceText('x:1'), c.sourceText(null)]`)).toEqual(['a:1-2 ほか2件', 'a:1-2', 'x:1', '-'])
		const text = '---\nschema: 1\nkind: "elements"\nsource: ["a:1-2","b:3"]\n---\n\n対象: 2 個の要素（a:1-2 ほか）\n\n# 直す\n\n直す\n'
		expect(run(`c.summarize(${JSON.stringify(text)})`)).toBe('[elements] 直す')
		expect(run(`c.readFront(${JSON.stringify(text)}).front.source`)).toEqual(['a:1-2', 'b:3'])
	})
	it('値の無い要素（null）があっても一覧のソース欄と読み込みが動く', () => {
		expect(run(`[c.sourceText([null, 'a:1-2', null, 'b:3']), c.sourceText([null, null]), c.sourceText([null, 'a:1-2'])]`)).toEqual(['a:1-2 ほか1件', '-', 'a:1-2'])
		const text = '---\nschema: 1\nkind: "elements"\nsource: [null,"b:3"]\n---\n\n# x\n'
		expect(run(`c.readFront(${JSON.stringify(text)}).front.source`)).toEqual([null, 'b:3'])
	})
	it('readFront は値の途中の「空白 + #」をコメントとして切らない', () => {
		const text = '---\nschema: 1\nselector: "main #install > p"\nheading: "## Step #2"\nsource: "docs/a.mdx:1" # コメント\nkind: text # 末尾のコメント\n---\n\n# x\n'
		expect(run(`c.readFront(${JSON.stringify(text)}).front`)).toMatchObject({ selector: 'main #install > p', heading: '## Step #2', source: 'docs/a.mdx:1', kind: 'text' })
	})
})
