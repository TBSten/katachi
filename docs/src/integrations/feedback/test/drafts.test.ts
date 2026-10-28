// @vitest-environment happy-dom
import { beforeEach, describe, expect, it } from 'vitest'
import { buildDialog } from '../feedback/dialog'
import type { Draft } from '../feedback/drafts'
import { clearDraft, draftKey, loadDraft, locatorOf, saveDraft } from '../feedback/drafts'

beforeEach(() => sessionStorage.clear())

describe('drafts', () => {
	it('書いて戻せる・消せる', () => {
		const key = draftKey('http://x/a/#h', 'el:/html/body/p')
		saveDraft(key, { comment: '直して', checks: { browser: false } })
		expect(loadDraft(key)).toEqual({ comment: '直して', checks: { browser: false } })
		clearDraft(key)
		expect(loadDraft(key)).toBeNull()
	})
	it('hash が違っても同じページ。場所が違えば別の下書き', () => {
		expect(draftKey('http://x/a/#a', 'l')).toBe(draftKey('http://x/a/#b', 'l'))
		expect(draftKey('http://x/a/', 'l1')).not.toBe(draftKey('http://x/a/', 'l2'))
		expect(draftKey('http://x/a/', 'l')).not.toBe(draftKey('http://x/b/', 'l'))
	})
	it('コメントが空になったら下書きを消す', () => {
		const key = draftKey('http://x/', 'l')
		saveDraft(key, { comment: 'a', checks: {} })
		saveDraft(key, { comment: '  ', checks: {} })
		expect(loadDraft(key)).toBeNull()
	})
	it('壊れた値・読み書きできない入れ物でも投げない', () => {
		sessionStorage.setItem('k', '{not json')
		expect(loadDraft('k')).toBeNull()
		sessionStorage.setItem('k', JSON.stringify({ comment: 1 }))
		expect(loadDraft('k')).toBeNull()
		const broken = { getItem() { throw new Error('x') }, setItem() { throw new Error('x') }, removeItem() { throw new Error('x') } }
		expect(loadDraft('k', broken)).toBeNull()
		expect(() => saveDraft('k', { comment: 'a', checks: {} }, broken)).not.toThrow()
		expect(() => clearDraft('k', broken)).not.toThrow()
	})
	it('場所の鍵: 要素は XPath、テキストは文字列つき、ページ全体は target', () => {
		document.body.innerHTML = '<main><p id="a">本文</p></main>'
		const el = document.querySelector('#a')!
		expect(locatorOf({ kind: 'element', el })).toMatch(/^el:/)
		const range = document.createRange()
		range.selectNodeContents(el)
		expect(locatorOf({ kind: 'text', range, text: '本文' })).toMatch(/^text:.*:本文$/)
		expect(locatorOf({ kind: 'page' })).toBe('page')
	})
})

describe('下書きの宛先（このページ / サイト全体）', () => {
	const open = (initial?: Draft) => {
		const drafts: Array<{ target?: 'page' | 'site' }> = []
		const parts = buildDialog({ page: [['URL', 'x']] }, {}, { onSubmit() {}, onCopy: async () => true, onCancel() {}, onDraft: (d) => drafts.push(d) }, true, true, { initial })
		document.body.append(parts.root)
		return { parts, drafts }
	}
	const radio = (root: HTMLElement, value: string) => root.querySelector<HTMLInputElement>(`input[name="fb-target"][value="${value}"]`)!
	it('サイト全体を選んで書いた下書きは、開き直すとサイト全体のまま', () => {
		const { parts, drafts } = open()
		radio(parts.root, 'site').checked = true
		radio(parts.root, 'site').dispatchEvent(new Event('change', { bubbles: true }))
		expect(drafts.at(-1)!.target).toBe('site')
		const key = draftKey('http://x/', 'page')
		saveDraft(key, { ...parts.draft(), comment: '全体への意見' })
		const back = open(loadDraft(key)!)
		expect(radio(back.parts.root, 'site').checked).toBe(true)
		expect(radio(back.parts.root, 'page').checked).toBe(false)
	})
	it('宛先の無い下書き（旧形）は「このページ」', () => {
		expect(radio(open({ comment: 'a', checks: {} }).parts.root, 'page').checked).toBe(true)
	})
})
