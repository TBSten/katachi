import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { describe, expect, it } from 'vitest'
// @ts-expect-error versions.mjs は型宣言のない JS
import { archivedVersionSlugs } from '../src/lib/versions.mjs'

// 英語のページに、全角の記号・全角の英数字・日本語の文字が残っていたら失敗する。
// 英語のページは日本語の原本（ja/）からの翻訳で、訳すときに「（）」「、」「。」が残りやすい。
// frontmatter・本文・コメント・コードブロックの中も対象（コード例の中の日本語も訳す）。
//
// 検出する範囲:
//   - U+3000–303F  CJK の記号と句読点（全角スペース・、。〜「」『』【】 など）
//   - U+3040–30FF  ひらがな・カタカナ（「・」U+30FB と「ー」U+30FC を含む）
//   - U+4E00–9FFF  漢字
//   - U+FF00–FFEF  全角の記号・全角の英数字・半角カタカナ（（）［］！？：；，． など）
// 対象にしないもの: … — → ← × › ↗ など。英語のページでも使う記号で、全角ではない。
// 意図して残す箇所は ALLOWED に、理由つきで足す。
const docsRoot = join(import.meta.dirname, '..', 'src', 'content', 'docs')

type Allowed = { file: string; text: string; reason: string }

/** 理由つきの許可リスト。file は docsRoot からの相対パス、text はその行に現れる文字列。 */
export const ALLOWED: Allowed[] = []

type Violation = { line: number; column: number; char: string; context: string }

const forbidden = /[　-ヿ一-鿿＀-￯]/u

/** source から禁止文字を探す。allowed の text に含まれる位置は見逃す。 */
export const findForbidden = (source: string, file: string, allowed: Allowed[] = []): Violation[] =>
	source.split('\n').flatMap((text, i) => {
		const exempt = allowed
			.filter((a) => a.file === file)
			.flatMap((a) =>
				[...text.matchAll(new RegExp(a.text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'gu'))].map((m) => [
					m.index!,
					m.index! + m[0].length,
				]),
			)
		return [...text.matchAll(new RegExp(forbidden, 'gu'))]
			.filter((m) => !exempt.some(([from, to]) => m.index! >= from && m.index! < to))
			.map((m) => ({
				line: i + 1,
				column: m.index! + 1,
				char: m[0],
				context: text.slice(Math.max(0, m.index! - 20), m.index! + 20).trim(),
			}))
	})

const format = (file: string, v: Violation) =>
	`${file}:${v.line}:${v.column} 「${v.char}」(U+${v.char.codePointAt(0)!.toString(16).toUpperCase().padStart(4, '0')}) … ${v.context}\n` +
	`  直し方: 半角の記号・英語の文に直す。意図して残す箇所なら ALLOWED に { file, text, reason } を足す`

const walk = (dir: string): string[] =>
	readdirSync(dir).flatMap((name) => {
		const path = join(dir, name)
		return statSync(path).isDirectory() ? walk(path) : path.endsWith('.mdx') ? [path] : []
	})

// 日本語の原本（ja/）と、凍結した版の写し（0.2/ など。版は versions.mjs の archivedVersions）は対象にしない。
const skipped = ['ja', ...archivedVersionSlugs]
const files = walk(docsRoot)
	.map((f) => relative(docsRoot, f))
	.filter((f) => !skipped.includes(f.split('/')[0]))

describe('英語のページに全角・日本語を残さない', () => {
	it('検出器は全角の括弧・読点・句点・全角英数字・全角スペース・日本語を見つける', () => {
		expect(findForbidden('KDoc（説明）', 'a.mdx').map((v) => v.char)).toEqual(['（', '説', '明', '）'])
		expect(findForbidden('a、b。c', 'a.mdx').map((v) => v.char)).toEqual(['、', '。'])
		expect(findForbidden('Ａ１　', 'a.mdx')).toHaveLength(3)
		expect(findForbidden('ひらがなカタカナ', 'a.mdx')).toHaveLength(8)
	})

	it('検出器は行・列・前後の文脈を返す', () => {
		expect(findForbidden('line1\nab（c', 'a.mdx')).toEqual([{ line: 2, column: 3, char: '（', context: 'ab（c' }])
	})

	it('検出器は半角と英語のページで使う記号だけなら見つけない', () => {
		expect(findForbidden('KDoc (x), y. … — → ← × › ↗ "a"', 'a.mdx')).toEqual([])
	})

	it('検出器は許可リストの箇所を見逃し、ほかの箇所は見つける', () => {
		const allowed = [{ file: 'a.mdx', text: '日本語', reason: '言語名を見せる' }]
		expect(findForbidden('Japanese is 日本語', 'a.mdx', allowed)).toEqual([])
		expect(findForbidden('日本語（x）', 'a.mdx', allowed).map((v) => v.char)).toEqual(['（', '）'])
		expect(findForbidden('日本語', 'b.mdx', allowed)).toHaveLength(3)
	})

	it('許可リストのファイルは実在し、理由が書いてある', () => {
		for (const a of ALLOWED) {
			expect(files, a.file).toContain(a.file)
			expect(a.reason.trim(), a.file).not.toBe('')
		}
	})

	it('対象のページがある', () => {
		expect(files.length).toBeGreaterThan(0)
		expect(files.every((f) => !f.startsWith('ja/') && !/^[0-9]/.test(f))).toBe(true)
	})

	it.each(files.map((f) => [f]))('%s に全角の記号・日本語がない', (file) => {
		const violations = findForbidden(readFileSync(join(docsRoot, file), 'utf8'), file, ALLOWED)
		expect(violations.map((v) => format(file, v)).join('\n')).toBe('')
	})
})
