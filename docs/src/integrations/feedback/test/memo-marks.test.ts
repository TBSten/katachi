// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMarks } from '../memo/marks'
import type { Spot } from '../memo/model'

afterEach(() => vi.useRealTimers())

describe('「場所を表示」の scroll リスナー', () => {
	it('光る枠が消えたら、その枠の scroll リスナーも外れる（押すたびに残らない）', () => {
		vi.useFakeTimers()
		document.body.innerHTML = '<p id="a">a</p>'
		const host = document.createElement('div')
		document.body.append(host)
		const root = host.attachShadow({ mode: 'open' })
		const marks = createMarks(root, () => {})
		const spot: Spot = { id: 's1', locator: { kind: 'element', selector: '#a', excerpt: 'a' }, values: {}, text: '' }
		marks.set([spot])
		const measure = vi.spyOn(Element.prototype, 'getBoundingClientRect')
		const scrollOnce = () => {
			measure.mockClear()
			window.dispatchEvent(new Event('scroll'))
			return measure.mock.calls.length // 同期で測る分（枠ごとの place）
		}
		const idle = scrollOnce()
		for (let i = 0; i < 5; i++) expect(marks.reveal(spot)).toBe(true)
		expect(scrollOnce()).toBeGreaterThan(idle) // 光っている間は枠が追従する
		vi.advanceTimersByTime(10_000)
		expect(scrollOnce()).toBe(idle) // 消えたあとは測らない
		marks.destroy()
	})
})
