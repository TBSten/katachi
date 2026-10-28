import { describe, expect, it } from 'vitest'
import { parseMarkdown, spotTitle, toMarkdown } from '../memo/markdown'
import type { MemoDoc } from '../memo/model'

const page = { path: '/katachi/ja/guides/x/', lang: 'ja', title: 'X のガイド', source: 'docs/src/content/docs/ja/guides/x.mdx' }
const doc: MemoDoc = {
	page,
	note: { values: { 読みやすさ: '3' }, text: 'ページ全体のメモ\n二行目' },
	spots: [
		{
			id: 's1',
			locator: { kind: 'element', source: 'docs/src/content/docs/ja/guides/x.mdx:12-14', xpath: '//*[@id="a"]', selector: '#a', heading: '## 使い方', fragment: 'http://x/#:~:text=abc', excerpt: '最初の段落' },
			values: {},
			text: '箇所のメモ',
		},
		{ id: 's2', locator: { kind: 'text', excerpt: '選んだ文字' }, values: { 読みやすさ: '1', 外した: 'v' }, text: '' },
	],
}
const now = new Date('2026-10-29T03:04:05.000Z')

describe('toMarkdown', () => {
	const md = toMarkdown(doc, now)
	it('frontmatter にページ・ソースのファイル・更新日時が入る', () => {
		expect(md).toMatch(/^---\nschema: katachi-memo\/1\npage: "\/katachi\/ja\/guides\/x\/"\nlang: "ja"\ntitle: "X のガイド"\nsource: "docs\/src\/content\/docs\/ja\/guides\/x.mdx"\nupdated: 2026-10-29T03:04:05.000Z\n---\n/)
	})
	it('節は、ページのメモと箇所ごとのメモ', () => {
		expect(md).toContain('## ページのメモ\n\n- 読みやすさ: 3\n\nページ全体のメモ\n二行目')
		expect(md).toContain('## 箇所のメモ')
		expect(md).toContain('### 1. 使い方')
		expect(md).toContain('### 2. 「選んだ文字」')
	})
	it('箇所には場所（source・xpath・selector・heading・テキストフラグメント）が入る', () => {
		for (const s of ['source: docs/src/content/docs/ja/guides/x.mdx:12-14', 'xpath: //*[@id="a"]', 'selector: #a', 'heading: ## 使い方', 'text fragment: http://x/#:~:text=abc']) expect(md).toContain(s)
	})
	it('ひな形から外した field の値も書く', () => {
		expect(md).toContain('- 外した: v')
	})
	it('空なら（なし）。箇所が無ければ箇所の節も作らない', () => {
		const empty = toMarkdown({ page, note: { values: {}, text: '' }, spots: [] }, now)
		expect(empty).toContain('## ページのメモ\n\n（なし）')
		expect(empty).not.toContain('## 箇所のメモ')
	})
})

describe('読み直し', () => {
	it('同じ Markdown から同じ内容に戻る', () => {
		expect(parseMarkdown(toMarkdown(doc, now))).toEqual(doc)
	})
	it('本文に --> や HTML コメントや見出しがあっても壊れない', () => {
		const tricky: MemoDoc = { ...doc, note: { values: {}, text: '--> <!-- katachi-memo:data v1\n{"x":1}\n-->\n## 偽の節\n</script>' }, spots: [] }
		expect(parseMarkdown(toMarkdown(tricky, now))).toEqual(tricky)
	})
	it('絵文字・日本語・引用符・バックスラッシュ', () => {
		const odd: MemoDoc = { ...doc, note: { values: { 'q"x': 'a\\b"c' }, text: '😀 "引用" \\n' }, spots: [] }
		expect(parseMarkdown(toMarkdown(odd, now))).toEqual(odd)
	})
	it('データのコメントが無い・壊れていれば null', () => {
		expect(parseMarkdown('# 手書き\n')).toBeNull()
		expect(parseMarkdown('<!-- katachi-memo:data v1\n{oops\n-->')).toBeNull()
		expect(parseMarkdown('<!-- katachi-memo:data v1\n{"page":1}\n-->')).toBeNull()
	})
	it('欠けた箇所は落とし、形の違う値は直して読む', () => {
		const text = `<!-- katachi-memo:data v1\n${JSON.stringify({ page, note: { values: { a: 1, b: 'ok' }, text: 5 }, spots: [{ id: '', locator: {} }, { id: 'k', locator: { kind: 'x' }, values: {}, text: 't' }] })}\n-->`
		const d = parseMarkdown(text)!
		expect(d.note).toEqual({ values: { b: 'ok' }, text: '' })
		expect(d.spots).toEqual([{ id: 'k', locator: { kind: 'element', excerpt: '' }, values: {}, text: 't' }])
	})
})

describe('spotTitle', () => {
	it('見出し → 抜粋 → ソース の順', () => {
		expect(spotTitle({ kind: 'element', heading: '### A', excerpt: 'x' })).toBe('A')
		expect(spotTitle({ kind: 'element', excerpt: 'x' })).toBe('「x」')
		expect(spotTitle({ kind: 'element', excerpt: '', source: 'a:1' })).toBe('a:1')
	})
})
