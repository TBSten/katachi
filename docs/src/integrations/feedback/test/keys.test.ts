// @vitest-environment happy-dom
// モード中・Memo を開いている間のキー: ページのリスナーへは届かせず、ブラウザの既定の動作は残す。
import { describe, expect, it } from 'vitest'
import { guardKeys } from '../shared/keys'

describe('guardKeys', () => {
	const setup = () => {
		const host = document.createElement('div')
		document.body.append(host)
		const ac = new AbortController()
		guardKeys({ host, signal: ac.signal })
		return { host, ac }
	}
	const fire = (target: EventTarget, init: KeyboardEventInit) => {
		const e = new KeyboardEvent('keydown', { bubbles: true, cancelable: true, composed: true, ...init })
		target.dispatchEvent(e)
		return e
	}

	it('ページの上の Cmd+C・Cmd+F・Space・矢印は、ブラウザの既定の動作を止めない', () => {
		const { ac } = setup()
		const got = {
			copy: fire(document.body, { key: 'c', metaKey: true }).defaultPrevented,
			find: fire(document.body, { key: 'f', metaKey: true }).defaultPrevented,
			space: fire(document.body, { key: ' ' }).defaultPrevented,
			arrow: fire(document.body, { key: 'ArrowDown' }).defaultPrevented,
			slash: fire(document.body, { key: '/' }).defaultPrevented,
		}
		expect(got).toEqual({ copy: false, find: false, space: false, arrow: false, slash: false })
		ac.abort()
	})

	it('ページのリスナー（後から足した window の capture・document・要素）へは届かない', () => {
		const { ac } = setup()
		let reached = 0
		const count = () => reached++
		window.addEventListener('keydown', count, true)
		window.addEventListener('keydown', count)
		document.addEventListener('keydown', count)
		document.body.addEventListener('keydown', count)
		fire(document.body, { key: 'k', ctrlKey: true })
		fire(document.body, { key: '/' })
		expect(reached).toBe(0)
		window.removeEventListener('keydown', count, true)
		ac.abort()
	})

	it('自前の UI の中のキーは止めない（入力の動作を生かす）', () => {
		const { host, ac } = setup()
		const input = document.createElement('input')
		host.append(input)
		let reached = 0
		window.addEventListener('keydown', () => reached++, { once: true })
		const e = fire(input, { key: 'a' })
		expect(e.defaultPrevented).toBe(false)
		expect(reached).toBe(1)
		ac.abort()
	})

	it('abort したあとはページへ届く', () => {
		const { ac } = setup()
		ac.abort()
		let reached = 0
		document.addEventListener('keydown', () => reached++, { once: true })
		fire(document.body, { key: '/' })
		expect(reached).toBe(1)
	})
})
