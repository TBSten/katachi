import { describe, expect, it } from 'vitest'
import { addField, DEFAULT_FIELDS, displayValue, normalizeFieldName, parseFields, removeField, sameFields, sanitizeFields, sanitizeOptions, serializeFields, splitOptions, toFieldDef, visibleFields, type FieldDef } from '../memo/fields'
import { emptyNote, withValue } from '../memo/model'

const text = (name: string): FieldDef => ({ name, type: 'text' })
const check = (name: string): FieldDef => ({ name, type: 'check' })
const select = (name: string, options: string[]): FieldDef => ({ name, type: 'select', options })

describe('field のひな形（テキスト）', () => {
	it('最初は「読みやすさ」だけ', () => {
		expect(parseFields(null)).toEqual([text('読みやすさ')])
		expect([...DEFAULT_FIELDS]).toEqual([text('読みやすさ')])
	})
	it('足す: 文字列はテキストの field。空白を詰める・重複と空は足さない', () => {
		expect(addField([text('a')], '  b   c ')).toEqual([text('a'), text('b c')])
		expect(addField([text('a')], 'a')).toEqual([text('a')])
		expect(addField([text('a')], '   ')).toEqual([text('a')])
		expect(normalizeFieldName('x'.repeat(41))).toBeNull()
	})
	it('外す: ひな形から消えるだけで、メモの値は残る', () => {
		const t = removeField([text('a'), text('b')], 'a')
		expect(t).toEqual([text('b')])
		const note = { values: { a: '残る', b: 'x' }, text: '' }
		expect(visibleFields(t, note)).toEqual([[text('b'), 'x']])
		expect(note.values.a).toBe('残る')
		expect(visibleFields(addField(t, 'a'), note)).toEqual([[text('b'), 'x'], [text('a'), '残る']])
	})
	it('メモごとに別の値（ひな形は共有・値は別）', () => {
		const t = [text('読みやすさ')]
		expect(visibleFields(t, withValue(emptyNote(), '読みやすさ', '3'))).toEqual([[text('読みやすさ'), '3']])
		expect(visibleFields(t, emptyNote())).toEqual([[text('読みやすさ'), '']])
	})
	it('_fields.json が壊れていれば既定、fields が配列でなければ既定、空の配列は空', () => {
		expect(parseFields('{oops')).toEqual([text('読みやすさ')])
		expect(parseFields('{"fields":"x"}')).toEqual([text('読みやすさ')])
		expect(parseFields('{"fields":[]}')).toEqual([])
	})
	it('今の利用者のファイル（文字列だけ）はそのまま読めて、書き出しても同じ中身になる', () => {
		const file = `${JSON.stringify({ fields: ['読みやすさ (0~10)', '内容の充実度(0~10)', 'v0.3 チェック'] }, null, 2)}\n`
		const t = parseFields(file)
		expect(t).toEqual([text('読みやすさ (0~10)'), text('内容の充実度(0~10)'), text('v0.3 チェック')])
		expect(serializeFields(t)).toBe(file)
	})
})

describe('field の種類（check / select）', () => {
	it('文字列とオブジェクトの混在を読む', () => {
		const t = parseFields(JSON.stringify({ fields: ['a', { name: 'b', type: 'check' }, { name: 'c', type: 'select', options: ['x', 'y'] }, { name: 'd', type: 'text' }] }))
		expect(t).toEqual([text('a'), check('b'), select('c', ['x', 'y']), text('d')])
	})
	it('書き出し: テキストは文字列のまま、check と select はオブジェクト', () => {
		const out = JSON.parse(serializeFields([text('a'), check('b'), select('c', ['x'])]))
		expect(out).toEqual({ fields: ['a', { name: 'b', type: 'check' }, { name: 'c', type: 'select', options: ['x'] }] })
	})
	it('壊れた要素: 名前の無いもの・文字列以外は捨て、未知の type・options の無い select は text として読む', () => {
		expect(sanitizeFields([1, null, [], {}, { name: '' }, { name: 5, type: 'check' }, { type: 'check' }])).toEqual([])
		expect(sanitizeFields([{ name: 'a', type: 'number' }, { name: 'b' }, { name: 'c', type: 'select' }, { name: 'd', type: 'select', options: [] }, { name: 'e', type: 'select', options: [1, '', ' '] }])).toEqual([text('a'), text('b'), text('c'), text('d'), text('e')])
		expect(toFieldDef({ name: 'a', type: 'check', options: ['x'] })).toEqual(check('a'))
		expect(toFieldDef({ name: 'a', type: 'text', options: ['x'] })).toEqual(text('a'))
	})
	it('重複する名前は先のものを使う', () => {
		expect(sanitizeFields(['a', { name: 'a', type: 'check' }, { name: ' b ', type: 'check' }, 'b'])).toEqual([text('a'), check('b')])
	})
	it('上限: field は 30 個、選択肢は 20 個・各 40 文字', () => {
		const many = Array.from({ length: 35 }, (_, i) => `f${i}`)
		expect(sanitizeFields(many)).toHaveLength(30)
		const opts = Array.from({ length: 25 }, (_, i) => `o${i}`)
		expect((toFieldDef({ name: 's', type: 'select', options: opts }) as { options: string[] }).options).toEqual(opts.slice(0, 20))
		expect(sanitizeOptions(['ok', 'x'.repeat(41), 'x'.repeat(40), 'ok', '  a  b '])).toEqual(['ok', 'x'.repeat(40), 'a b'])
	})
	it('同じ名前を別の種類で足し直すと、その位置で種類が変わる', () => {
		const t = [text('a'), text('b')]
		expect(addField(t, check('a'))).toEqual([check('a'), text('b')])
		expect(addField(t, select('b', ['x']))).toEqual([text('a'), select('b', ['x'])])
		expect(addField([select('b', ['x'])], select('b', ['y', 'z']))).toEqual([select('b', ['y', 'z'])])
		expect(sameFields(addField(t, text('a')), t)).toBe(true)
	})
	it('上限の 30 個に達していても、既にある名前の種類は変えられる', () => {
		const full = Array.from({ length: 30 }, (_, i) => text(`f${i}`))
		expect(addField(full, 'new')).toHaveLength(30)
		expect(addField(full, check('f3'))[3]).toEqual(check('f3'))
	})
	it('選択肢の文字を分ける: カンマ・全角カンマ・改行', () => {
		expect(splitOptions('a, b，c\nd\n\n , ')).toEqual(['a', 'b', 'c', 'd'])
		expect(splitOptions('')).toEqual([])
	})
	it('値の見せ方: check で入っていれば [x]、型の合わない値と他の種類は値のまま', () => {
		expect(displayValue(check('a'), 'true')).toBe('[x]')
		expect(displayValue(check('a'), '7')).toBe('7')
		expect(displayValue(select('a', ['x']), 'x')).toBe('x')
		expect(displayValue(text('a'), 'true')).toBe('true')
		expect(displayValue(undefined, 'true')).toBe('true')
	})
	it('ひな形の種類を変えても、値は消えない', () => {
		const note = withValue(emptyNote(), 'a', '7')
		expect(visibleFields([check('a')], note)).toEqual([[check('a'), '7']])
		expect(note.values).toEqual({ a: '7' })
	})
})

// 依存を増やさないため、fast-check の代わりに種つきの乱数で要素を作る。
function rng(seed: number) {
	let s = seed >>> 0
	return () => {
		s = (s + 0x6d2b79f5) >>> 0
		let t = s
		t = Math.imul(t ^ (t >>> 15), t | 1)
		t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
		return ((t ^ (t >>> 14)) >>> 0) / 4294967296
	}
}
const PIECES = ['a', 'b', ' ', '  ', 'あ', '😀', ',', '\n', '"', '\\', 'x'.repeat(39), 'y'.repeat(45), '']
function randomElement(r: () => number): unknown {
	const str = () => Array.from({ length: Math.floor(r() * 3) }, () => PIECES[Math.floor(r() * PIECES.length)]).join('')
	const k = Math.floor(r() * 8)
	if (k === 0) return str()
	if (k === 1) return [null, 1, true, [], {}][Math.floor(r() * 5)]
	const type = ['text', 'check', 'select', 'number', undefined][Math.floor(r() * 5)]
	const options = r() < 0.8 ? Array.from({ length: Math.floor(r() * 25) }, () => (r() < 0.1 ? 3 : str())) : 'x'
	return { name: r() < 0.9 ? str() : 7, ...(type ? { type } : {}), ...(r() < 0.9 ? { options } : {}) }
}

describe('性質: 読む → 書く → 読む', () => {
	it('どんな要素の並びでも、整えたひな形は書いて読み直すと同じ・書き直しても同じ文字', () => {
		const r = rng(20261031)
		for (let i = 0; i < 500; i++) {
			const list = Array.from({ length: Math.floor(r() * 40) }, () => randomElement(r))
			const t = sanitizeFields(list)
			const file = serializeFields(t)
			const again = parseFields(file)
			expect(again).toEqual(t)
			expect(serializeFields(again)).toBe(file)
			// 整えた結果は上限と決まりを守る
			expect(t.length).toBeLessThanOrEqual(30)
			expect(new Set(t.map((f) => f.name)).size).toBe(t.length)
			for (const f of t) {
				expect(normalizeFieldName(f.name)).toBe(f.name)
				if (f.type === 'select') {
					expect(f.options.length).toBeGreaterThanOrEqual(1)
					expect(f.options.length).toBeLessThanOrEqual(20)
					expect(sanitizeOptions(f.options)).toEqual(f.options)
				} else expect('options' in f).toBe(false)
			}
		}
	})
	it('通信の形（FieldDef の並び）を整え直しても変わらない', () => {
		const r = rng(7)
		for (let i = 0; i < 300; i++) {
			const t = sanitizeFields(Array.from({ length: Math.floor(r() * 20) }, () => randomElement(r)))
			expect(sanitizeFields(JSON.parse(JSON.stringify(t)))).toEqual(t)
		}
	})
})
