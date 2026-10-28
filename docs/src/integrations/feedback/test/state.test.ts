// @vitest-environment happy-dom
import { beforeEach, describe, expect, it } from 'vitest'
import { collectState } from '../feedback/state'

beforeEach(() => {
	document.documentElement.removeAttribute('data-theme')
	document.body.innerHTML = ''
	localStorage.clear()
	;(globalThis as { matchMedia?: unknown }).matchMedia ??= () => ({ matches: false })
})

const rows = () => Object.fromEntries(collectState())

describe('collectState', () => {
	it('テーマは必ず入る', () => {
		document.documentElement.dataset.theme = 'dark'
		expect(rows()['テーマ']).toBe('ダーク（自動（OS に従う））')
		localStorage.setItem('starlight-theme', 'dark')
		expect(rows()['テーマ']).toBe('ダーク（選択）')
	})

	it('同期タブ・開いた details・目次の今の見出しを拾う', () => {
		document.documentElement.dataset.theme = 'light'
		document.body.innerHTML = `
			<starlight-tabs data-sync-key="pkg"><ul><li><a role="tab" aria-selected="false">npm</a></li><li><a role="tab" aria-selected="true">pnpm</a></li></ul></starlight-tabs>
			<main><details open><summary>詳しい説明</summary></details><details><summary>閉じている</summary></details></main>
			<starlight-toc><a aria-current="true" href="#x">パスを定数に</a></starlight-toc>`
		const r = rows()
		expect(r['同期タブ pkg']).toBe('pnpm')
		expect(r['開いている details']).toBe('「詳しい説明」')
		expect(r['目次で今いる見出し']).toBe('パスを定数に')
	})

	it('検索の開閉と語', () => {
		document.documentElement.dataset.theme = 'light'
		document.body.innerHTML = '<site-search><dialog><input value="layout"></dialog></site-search>'
		expect(rows()['検索']).toBe('閉じている')
		;(document.querySelector('dialog') as HTMLDialogElement).setAttribute('open', '')
		const r = rows()
		expect(r['検索']).toBe('開いている')
		expect(r['検索の語']).toBe('layout')
	})

	it('無いものは行を作らない', () => {
		document.documentElement.dataset.theme = 'light'
		expect(Object.keys(rows())).toEqual(['テーマ'])
	})
})
