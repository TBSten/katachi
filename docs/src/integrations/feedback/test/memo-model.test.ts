import { describe, expect, it } from 'vitest'
import { emptyNote, isNoteEmpty, memoDocPath, withValue } from '../memo/model'

describe('field の値', () => {
	it('値の更新: 空にすると消える・元の Note は変えない', () => {
		const n = emptyNote()
		const n2 = withValue(n, 'a', 'v')
		expect(n2.values).toEqual({ a: 'v' })
		expect(n.values).toEqual({})
		expect(withValue(n2, 'a', '').values).toEqual({})
	})
	it('長すぎる値は上限で切る', () => {
		expect(withValue(emptyNote(), 'a', 'x'.repeat(600)).values.a).toHaveLength(500)
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
