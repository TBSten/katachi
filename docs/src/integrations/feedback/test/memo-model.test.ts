import { describe, expect, it } from 'vitest'
import { addField, DEFAULT_FIELDS, emptyNote, isNoteEmpty, memoDocPath, normalizeFieldName, parseFields, removeField, sanitizeFields, serializeFields, visibleFields, withValue } from '../memo/model'

describe('field のひな形', () => {
	it('最初は「読みやすさ」だけ', () => {
		expect(parseFields(null)).toEqual(['読みやすさ'])
		expect([...DEFAULT_FIELDS]).toEqual(['読みやすさ'])
	})
	it('足す: 空白を詰める・重複と空は足さない', () => {
		expect(addField(['a'], '  b   c ')).toEqual(['a', 'b c'])
		expect(addField(['a'], 'a')).toEqual(['a'])
		expect(addField(['a'], '   ')).toEqual(['a'])
		expect(normalizeFieldName('x'.repeat(41))).toBeNull()
	})
	it('外す: ひな形から消えるだけで、メモの値は残る', () => {
		const t = removeField(['a', 'b'], 'a')
		expect(t).toEqual(['b'])
		const note = { values: { a: '残る', b: 'x' }, text: '' }
		expect(visibleFields(t, note)).toEqual([['b', 'x']])
		expect(note.values.a).toBe('残る')
		// ひな形に戻せば値がまた見える
		expect(visibleFields(addField(t, 'a'), note)).toEqual([['b', 'x'], ['a', '残る']])
	})
	it('値の更新: 空にすると消える・元の Note は変えない', () => {
		const n = emptyNote()
		const n2 = withValue(n, 'a', 'v')
		expect(n2.values).toEqual({ a: 'v' })
		expect(n.values).toEqual({})
		expect(withValue(n2, 'a', '').values).toEqual({})
	})
	it('メモごとに別の値（ひな形は共有・値は別）', () => {
		const t = ['読みやすさ']
		expect(visibleFields(t, withValue(emptyNote(), '読みやすさ', '3'))).toEqual([['読みやすさ', '3']])
		expect(visibleFields(t, emptyNote())).toEqual([['読みやすさ', '']])
	})
	it('_fields.json の読み書き: 壊れていれば既定、形が違えば落とす', () => {
		expect(parseFields(serializeFields(['a', 'b']))).toEqual(['a', 'b'])
		expect(parseFields('{oops')).toEqual(['読みやすさ'])
		expect(parseFields('{"fields":"x"}')).toEqual(['読みやすさ'])
		expect(sanitizeFields(['a', 1, 'a', '', ' b '])).toEqual(['a', 'b'])
		expect(parseFields('{"fields":[]}')).toEqual([])
	})
	it('isNoteEmpty', () => {
		expect(isNoteEmpty(emptyNote())).toBe(true)
		expect(isNoteEmpty({ values: { a: ' ' }, text: '\n' })).toBe(true)
		expect(isNoteEmpty({ values: {}, text: 'x' })).toBe(false)
	})
})

describe('memoDocPath', () => {
	it('memo/<言語>/<ページのパス>.md', () => {
		expect(memoDocPath('ja', 'guides/basic-api/')).toBe('memo/ja/guides/basic-api.md')
		expect(memoDocPath('en', '0.2/guides/x')).toBe('memo/en/0.2/guides/x.md')
		expect(memoDocPath('ja', '')).toBe('memo/ja/index.md')
		expect(memoDocPath('ja', '/')).toBe('memo/ja/index.md')
	})
	it.each([['..'], ['a/../b'], ['a/.hidden'], ['a b'], ['a\\b'], ['a/%2e%2e']])('使えないパスは投げる: %s', (p) => {
		expect(() => memoDocPath('ja', p)).toThrow()
	})
	it.each([[''], ['../x'], ['JA'], ['ja/x'], ['j']])('使えない言語は投げる: %j', (l) => {
		expect(() => memoDocPath(l, 'a')).toThrow()
	})
})
