// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createHost } from '../shared/shell'
import { css as feedbackCss } from '../feedback/ui.css'
import { css as memoCss } from '../memo/css'

afterEach(() => {
	document.documentElement.removeAttribute('style')
	document.querySelectorAll('x-test').forEach((e) => e.remove())
})

describe('ダークテーマ', () => {
	it('host の color-scheme が、ページ（Starlight）の color-scheme に合う', () => {
		document.documentElement.style.colorScheme = 'dark'
		const { host } = createHost('x-test', '')
		expect(host.style.colorScheme).toBe('dark')
	})
	it('ページが決めていなければ OS の設定に従う', () => {
		const { host } = createHost('x-test', '')
		expect(host.style.colorScheme).toBe('light dark')
	})
	it('テーマが切り替わったら追従する', async () => {
		document.documentElement.style.colorScheme = 'light'
		const { host } = createHost('x-test', '')
		expect(host.style.colorScheme).toBe('light')
		document.documentElement.style.colorScheme = 'dark'
		await new Promise((r) => setTimeout(r, 0))
		expect(host.style.colorScheme).toBe('dark')
	})
	it('popover・ダイアログの面の色は固定の白でなく、light-dark() の変数を使う', () => {
		for (const css of [feedbackCss, memoCss]) {
			expect(css).toContain('var(--bg)')
			expect(css).not.toMatch(/\.(dialog|panel|menu)[^{]*\{[^}]*background: #fff/)
		}
	})
})

describe('テーマの監視の後始末', () => {
	/** 生きている（observe して disconnect していない）MutationObserver を数える。 */
	const countLive = () => {
		const Real = globalThis.MutationObserver
		const live = new Set<MutationObserver>()
		class Counting extends Real {
			observe(target: Node, init?: MutationObserverInit) {
				live.add(this)
				super.observe(target, init)
			}
			disconnect() {
				live.delete(this)
				super.disconnect()
			}
		}
		vi.stubGlobal('MutationObserver', Counting)
		return live
	}
	afterEach(() => vi.unstubAllGlobals())
	const tick = () => new Promise((r) => setTimeout(r, 0))

	it('host を外したら、テーマが変わらなくても observer を外す（開け閉めを繰り返しても溜まらない）', async () => {
		const live = countLive()
		for (let i = 0; i < 10; i++) {
			const { host } = createHost('x-test', '')
			await tick()
			host.remove()
			await tick()
		}
		expect(live.size).toBe(0)
	})
	it('開いている間の host の observer は残り、テーマに追従する', async () => {
		const live = countLive()
		const { host } = createHost('x-test', '')
		// ほかの要素が html に足されても外さない
		const other = document.createElement('x-other')
		document.documentElement.append(other)
		await tick()
		other.remove()
		await tick()
		expect(live.size).toBe(1)
		document.documentElement.style.colorScheme = 'dark'
		await tick()
		expect(host.style.colorScheme).toBe('dark')
		host.remove()
		await tick()
		expect(live.size).toBe(0)
	})
})
