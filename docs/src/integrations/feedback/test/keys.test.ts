// @vitest-environment happy-dom
// モード中・Memo を開いている間のキー: ページのリスナーへは届かせず、ブラウザの既定の動作は残す。
import { afterEach, describe, expect, it } from 'vitest'
import { guardKeys, onKeyInside } from '../shared/keys'

describe('guardKeys', () => {
	// 確認が失敗しても、見張りと host を次のテストへ持ち越さない。
	const cleanups: (() => void)[] = []
	afterEach(() => cleanups.splice(0).forEach((f) => f()))
	const setup = () => {
		const host = document.createElement('div')
		document.body.append(host)
		const ac = new AbortController()
		guardKeys({ host, signal: ac.signal })
		cleanups.push(() => { ac.abort(); host.remove() })
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

	/** 本物と同じ、shadow root を持つ host と、その中の textarea。 */
	const setupShadow = (onEscape?: () => void) => {
		const host = document.createElement('div')
		const root = host.attachShadow({ mode: 'open' })
		const textarea = document.createElement('textarea')
		root.append(textarea)
		document.documentElement.append(host)
		const ac = new AbortController()
		guardKeys({ host, signal: ac.signal, onEscape })
		const done = () => { ac.abort(); host.remove() }
		cleanups.push(done)
		return { host, root, textarea, ac, done }
	}

	it('自前の UI の中のキーは、document の capture にあるサイトのショートカットへ届かない', () => {
		const { textarea, done } = setupShadow()
		const reached: string[] = []
		const page = (where: string) => (e: Event) => reached.push(`${where}:${(e as KeyboardEvent).key}`)
		const listeners = [
			[window, false], [document, true], [document, false], [document.documentElement, true], [document.body, true],
		] as const
		const fns = listeners.map(([t, capture]) => {
			const fn = page(`${t === window ? 'window' : (t as Node).nodeName}${capture ? '-capture' : ''}`)
			t.addEventListener('keydown', fn, capture)
			return fn
		})
		fire(textarea, { key: 'k', ctrlKey: true })
		fire(textarea, { key: 'k', metaKey: true })
		fire(textarea, { key: '/' })
		listeners.forEach(([t, capture], i) => t.removeEventListener('keydown', fns[i], capture))
		expect(reached).toEqual([])
		done()
	})

	it('自前の UI の中の処理（ダイアログの Ctrl+Enter・Alt+↑ など）は、対象から外へ向かう順に動き、preventDefault が効く', () => {
		const { root, textarea, done } = setupShadow()
		const got: string[] = []
		onKeyInside(root, 'keydown', (k) => {
			got.push(`root:${k.key}:${k.ctrlKey}:${k.altKey}:${k.shiftKey}:${k.metaKey}`)
			if (k.key === 'Enter' && k.ctrlKey) k.preventDefault()
		})
		onKeyInside(textarea, 'keydown', (e) => got.push(`textarea:${e.key}`))
		const submit = fire(textarea, { key: 'Enter', ctrlKey: true })
		const up = fire(textarea, { key: 'ArrowUp', altKey: true })
		const plain = fire(textarea, { key: 'a' })
		expect(got).toEqual([
			'textarea:Enter', 'root:Enter:true:false:false:false',
			'textarea:ArrowUp', 'root:ArrowUp:false:true:false:false',
			'textarea:a', 'root:a:false:false:false:false',
		])
		expect({ submit: submit.defaultPrevented, up: up.defaultPrevented, plain: plain.defaultPrevented })
			.toEqual({ submit: true, up: false, plain: false })
		done()
	})

	it('IME の変換中のキー（isComposing）は、その印のまま自前の UI の中の処理へ届く', () => {
		const { textarea, done } = setupShadow()
		const composing: boolean[] = []
		onKeyInside(textarea, 'keydown', (e) => composing.push(e.isComposing))
		fire(textarea, { key: 'Enter', isComposing: true })
		fire(textarea, { key: 'Enter' })
		expect(composing).toEqual([true, false])
		done()
	})

	it('自前の UI の中の Esc は、onEscape があればそれだけが動き、中のリスナーへは届かない（モードを抜ける）', () => {
		let exited = 0
		const { textarea, done } = setupShadow(() => exited++)
		let inside = 0
		onKeyInside(textarea, 'keydown', () => inside++)
		const e = fire(textarea, { key: 'Escape' })
		expect({ exited, inside, prevented: e.defaultPrevented }).toEqual({ exited: 1, inside: 0, prevented: true })
		done()
	})

	it('onEscape が無いとき（メモ）、自前の UI の中の Esc は素通しで、中のリスナーと document の capture に届く', () => {
		const { textarea, done } = setupShadow()
		const got: string[] = []
		const doc = () => got.push('document')
		document.addEventListener('keydown', doc, true)
		onKeyInside(textarea, 'keydown', () => got.push('textarea'))
		fire(textarea, { key: 'Escape' })
		document.removeEventListener('keydown', doc, true)
		expect(got).toEqual(['document', 'textarea'])
		done()
	})

	it('shadow の無い host でも、ページへは届かず、中の処理は 1 回だけ動く', () => {
		const { host, ac } = setup()
		const input = document.createElement('input')
		host.append(input)
		const got: string[] = []
		const win = () => got.push('window')
		window.addEventListener('keydown', win)
		onKeyInside(input, 'keydown', () => got.push('input'))
		const e = fire(input, { key: 'a' })
		window.removeEventListener('keydown', win)
		expect({ got, prevented: e.defaultPrevented }).toEqual({ got: ['input'], prevented: false })
		ac.abort()
	})

	it('モードを閉じた（abort）あとは、自前の UI の中のキーも元どおり伝わる', () => {
		const { textarea, ac, host } = setupShadow()
		ac.abort()
		let reached = 0
		document.addEventListener('keydown', () => reached++, { once: true, capture: true })
		fire(textarea, { key: 'k', ctrlKey: true })
		expect(reached).toBe(1)
		host.remove()
	})

	it('abort したあとはページへ届く', () => {
		const { ac } = setup()
		ac.abort()
		let reached = 0
		document.addEventListener('keydown', () => reached++, { once: true })
		fire(document.body, { key: '/' })
		expect(reached).toBe(1)
	})

	it('guardKeys の無い間（クイックの入力欄など）は、onKeyInside の処理が普通のリスナーとして 1 回動く', () => {
		const host = document.createElement('div')
		const root = host.attachShadow({ mode: 'open' })
		const textarea = document.createElement('textarea')
		root.append(textarea)
		document.documentElement.append(host)
		cleanups.push(() => host.remove())
		let n = 0
		onKeyInside(textarea, 'keydown', () => n++)
		fire(textarea, { key: 'Enter', ctrlKey: true })
		expect(n).toBe(1)
	})
})
