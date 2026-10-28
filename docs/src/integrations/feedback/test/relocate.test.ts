// @vitest-environment happy-dom
import { beforeEach, describe, expect, it } from 'vitest'
import { byFragment, bySource, parseFragment, relocate } from '../shared/relocate'

const page = `
<main>
  <h2 id="intro">はじめに</h2>
  <p data-fb-src="a.mdx:3-3">最初の段落です。</p>
  <p data-fb-src="a.mdx:5-6">次の段落には <b>太字</b> が入ります。</p>
  <p data-fb-src="a.mdx:8-8">最初の段落です。</p>
  <ul><li data-fb-src="a.mdx:10-10">一つ目</li><li data-fb-src="a.mdx:11-11">二つ目</li></ul>
</main>
<script>var x = "最初の段落です。"</script>`

beforeEach(() => {
	document.body.innerHTML = page
})

const frag = (s: string) => `http://x/#:~:text=${s}`

describe('parseFragment', () => {
	it('prefix・head・tail を分ける', () => {
		expect(parseFragment(frag('%E5%89%8D-,%E9%A0%AD,%E5%B0%BE'))).toEqual({ prefix: '前', head: '頭', tail: '尾' })
		expect(parseFragment(frag('abc'))).toEqual({ prefix: null, head: 'abc', tail: null })
		expect(parseFragment('http://x/')).toBeNull()
	})
})

describe('byFragment', () => {
	it('head だけで見つかる。範囲はその文字', () => {
		const r = byFragment(frag(encodeURIComponent('二つ目')))
		expect(r?.toString()).toBe('二つ目')
	})
	it('インライン要素をまたいでも空白を詰めて見つかる', () => {
		const r = byFragment(frag(encodeURIComponent('次の段落には 太字 が入')))
		expect(r?.toString().replace(/\s+/g, ' ')).toBe('次の段落には 太字 が入')
	})
	it('同じ文字列が複数あれば prefix で絞る', () => {
		const r = byFragment(frag(`${encodeURIComponent('が入ります。')}-,${encodeURIComponent('最初の段落です。')}`))
		expect((r?.startContainer.parentElement as HTMLElement).dataset.fbSrc).toBe('a.mdx:8-8')
	})
	it('開始と終了の組で範囲を作る', () => {
		const r = byFragment(frag(`${encodeURIComponent('次の段落')},${encodeURIComponent('入ります。')}`))
		expect(r?.toString()).toContain('太字')
	})
	it('script の中は数えない・無ければ null', () => {
		expect(byFragment(frag(encodeURIComponent('var x')))).toBeNull()
		expect(byFragment(frag(encodeURIComponent('存在しない')))).toBeNull()
	})
})

describe('bySource', () => {
	it('一致する範囲を優先し、無ければ含むもの、重なるものの順', () => {
		expect(bySource('a.mdx:5-6')?.textContent).toContain('次の段落')
		expect(bySource('a.mdx:11')?.textContent).toBe('二つ目')
		expect(bySource('a.mdx:4-5')?.textContent).toContain('次の段落')
		expect(bySource('b.mdx:3')).toBeNull()
		expect(bySource('a.mdx:99')).toBeNull()
	})
})

describe('relocate', () => {
	// happy-dom には document.evaluate が無い。XPath が当たる側は E2E（実ブラウザ）で確かめる。
	it('XPath が使えない・外れたらセレクタ、次にフラグメント、最後にソース', () => {
		expect(relocate({ xpath: '//nothing', selector: 'h2' })?.how).toBe('selector')
		expect(relocate({ xpath: '//nothing', selector: 'p', fragment: frag(encodeURIComponent('一つ目')) })?.how).toBe('fragment')
		expect(relocate({ xpath: '//nothing', selector: '.x', fragment: frag('zzz'), source: 'a.mdx:10' })?.how).toBe('source')
	})
	it('セレクタが複数に当たるなら採用しない', () => {
		expect(relocate({ selector: 'li' })).toBeNull()
	})
	it('テキストの選択は、XPath があってもフラグメントで範囲まで戻す', () => {
		const r = relocate({ kind: 'text', selector: '#intro', fragment: frag(encodeURIComponent('一つ目')) })
		expect(r?.how).toBe('fragment')
		expect(r?.range?.toString()).toBe('一つ目')
	})
	it('手がかりがどれも外れたら null', () => {
		expect(relocate({ xpath: '//x', selector: '.x', fragment: frag('zzz'), source: 'q.mdx:1' })).toBeNull()
		expect(relocate({})).toBeNull()
	})
})
