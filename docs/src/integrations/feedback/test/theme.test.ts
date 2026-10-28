// @vitest-environment happy-dom
import { afterEach, describe, expect, it } from 'vitest'
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
