// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest'
import type { ReviewView } from '../core/protocol'
import { groupByFile, onPage, parseFilter, passes, samePath, shortFile, sortItems } from '../review/order'
import { codeLines, findTarget } from '../review/place'
import { chooseSide, placeAt } from '../review/position'
import { renderInline, renderMarkdown, safeHref } from '../review/render'

const html = (f: DocumentFragment) => {
	const d = document.createElement('div')
	d.append(f)
	return d.innerHTML
}

describe('renderMarkdown', () => {
	it('段落・箇条書きの入れ子・コードフェンス・引用', () => {
		const out = html(renderMarkdown('一行目\n二行目\n\n- a\n  - b\n  続き\n- c\n\n```kt\nval x = 1\n```\n\n> 引用'))
		expect(out).toBe('<p>一行目<br>二行目</p><ul><li>a<ul><li>b<br>続き</li></ul></li><li>c</li></ul><pre><code>val x = 1</code></pre><blockquote><p>引用</p></blockquote>')
	})
	it('番号付きリスト', () => {
		expect(html(renderMarkdown('1. a\n2. b'))).toBe('<ol><li>a</li><li>b</li></ol>')
	})
	it('HTML は文字のまま（innerHTML にしない）', () => {
		const out = html(renderMarkdown('<img src=x onerror=alert(1)> **<b>太</b>**'))
		expect(out).toBe('<p>&lt;img src=x onerror=alert(1)&gt; <strong>&lt;b&gt;太&lt;/b&gt;</strong></p>')
	})
	it('閉じないコードフェンスは最後までコード', () => {
		expect(html(renderMarkdown('```\na\nb'))).toBe('<pre><code>a\nb</code></pre>')
	})
})

describe('renderInline', () => {
	it('`code` の中の ** や [] は書式にしない。`` ` `` の数が多い囲みも読む', () => {
		expect(html(renderInline('a `**x** [l](u)` b ```` ```kotlin ```` c'))).toBe('a <code>**x** [l](u)</code> b <code>```kotlin</code> c')
	})
	it('リンクは http(s)・相対だけ。javascript: は文字のまま', () => {
		expect(html(renderInline('[ok](https://e.com) [rel](/katachi/ja/) [bad](javascript:alert(1))'))).toBe('<a href="https://e.com" target="_blank" rel="noopener noreferrer">ok</a> <a href="/katachi/ja/" target="_blank" rel="noopener noreferrer">rel</a> [bad](javascript:alert(1))')
		expect(safeHref('JavaScript:x')).toBe(false)
	})
	it('閉じない ` と ** は文字のまま', () => {
		expect(html(renderInline('a ` b ** c'))).toBe('a ` b ** c')
	})
})

const v = (o: Partial<ReviewView>): ReviewView => ({ id: o.title ?? 'x', set: 's', title: 'x', priority: null, file: 'docs/src/content/docs/ja/a.mdx', line: null, written: null, place: 'ok', status: 'open', body: '', ...o })

describe('order', () => {
	it('ページのあるファイル → 無いファイル。ファイルの中はページ全体 → 行の順、同じ行は優先度の高い順', () => {
		const items = [
			v({ title: 'readme', file: 'README.ja.md', line: { start: 1, end: 1 } }),
			v({ title: 'b10', file: 'docs/src/content/docs/ja/b.mdx', line: { start: 10, end: 10 } }),
			v({ title: 'a20', line: { start: 20, end: 20 } }),
			v({ title: 'a5-p2', line: { start: 5, end: 5 }, priority: 2 }),
			v({ title: 'a5-p7', line: { start: 5, end: 5 }, priority: 7 }),
			v({ title: 'apage' }),
		]
		expect(sortItems(items, '/katachi').map((i) => i.title)).toEqual(['apage', 'a5-p7', 'a5-p2', 'a20', 'b10', 'readme'])
	})
	it('絞り込み: 解決済みは既定で隠す。優先度の無い指摘はいつも出す', () => {
		const f = parseFilter(JSON.stringify({ minPriority: 5, showResolved: false }))
		expect(passes(v({ priority: 4 }), f)).toBe(false)
		expect(passes(v({ priority: 5 }), f)).toBe(true)
		expect(passes(v({ priority: null }), f)).toBe(true)
		expect(passes(v({ priority: 9, status: 'resolved' }), f)).toBe(false)
		expect(passes(v({ priority: 9, status: 'resolved' }), { ...f, showResolved: true })).toBe(true)
	})
	it('壊れた・知らない絞り込みは既定に戻す', () => {
		expect(parseFilter('{')).toEqual({ minPriority: 0, showResolved: false })
		expect(parseFilter(JSON.stringify({ minPriority: 4, showResolved: 'yes' }))).toEqual({ minPriority: 0, showResolved: false })
	})
	it('そのページの指摘か（URL かソースのパス）', () => {
		expect(onPage(v({}), { pathname: '/katachi/ja/a/', source: null, base: '/katachi' })).toBe(true)
		expect(onPage(v({}), { pathname: '/katachi/ja/a', source: null, base: '/katachi' })).toBe(true)
		expect(onPage(v({ file: 'README.ja.md' }), { pathname: '/katachi/ja/a/', source: 'README.ja.md', base: '/katachi' })).toBe(true)
		expect(onPage(v({}), { pathname: '/katachi/ja/b/', source: null, base: '/katachi' })).toBe(false)
		expect(samePath('/katachi/ja/%E3%81%82/', '/katachi/ja/あ')).toBe(true)
	})
	it('ファイルごとの数と短い名前', () => {
		const g = groupByFile(sortItems([v({ title: '1' }), v({ title: '2' }), v({ title: '3', file: 'README.ja.md' })], '/katachi'), '/katachi')
		expect(g.map((x) => [shortFile(x.file), x.count, x.url])).toEqual([['ja/a.mdx', 2, '/katachi/ja/a/'], ['README.ja.md', 1, null]])
	})
})

describe('position', () => {
	const view = { width: 1280, height: 800 }
	it('右に 280px 以上空いていれば右（幅は最大 380）', () => {
		expect(chooseSide({ left: 300, right: 900, top: 100, bottom: 200 }, view, 380, 280)).toEqual({ side: 'right', width: 360 })
	})
	it('右が狭く左が空いていれば左、どちらも狭ければ下', () => {
		expect(chooseSide({ left: 400, right: 1100, top: 0, bottom: 10 }, view, 380, 280)).toEqual({ side: 'left', width: 380 })
		expect(chooseSide({ left: 100, right: 1100, top: 0, bottom: 10 }, view, 380, 280).side).toBe('below')
	})
	it('横に置くときは要素の上端にそろえ、画面の下にはみ出すなら持ち上げる', () => {
		expect(placeAt('right', { left: 0, right: 500, top: 100, bottom: 120 }, { width: 300, height: 200 }, view)).toEqual({ left: 512, top: 100 })
		expect(placeAt('right', { left: 0, right: 500, top: 700, bottom: 720 }, { width: 300, height: 200 }, view)).toEqual({ left: 512, top: 592 })
		expect(placeAt('left', { left: 600, right: 900, top: -50, bottom: -10 }, { width: 300, height: 200 }, view)).toEqual({ left: 288, top: 8 })
	})
})

describe('findTarget', () => {
	const F = 'docs/src/content/docs/ja/a.mdx'
	it('行を含むいちばん狭い要素を選ぶ。行を含む要素が無ければ範囲が重なる要素', () => {
		document.body.innerHTML = `<main>
			<ol data-fb-src="${F}:10-30"><li data-fb-src="${F}:12-20"><p id="p" data-fb-src="${F}:14-15">x</p></li></ol>
			<p id="other" data-fb-src="docs/other.mdx:14">y</p>
		</main>`
		expect(findTarget(document, F, { start: 15, end: 15 })?.el.id).toBe('p')
		expect(findTarget(document, F, { start: 18, end: 18 })?.el.tagName).toBe('LI')
		expect(findTarget(document, F, { start: 5, end: 12 })?.el.tagName).toBe('LI')
		expect(findTarget(document, F, { start: 40, end: 41 })).toBeNull()
	})
	it('コードブロックの中の行は、その行の要素（フェンスの次の行が 1 行目）', () => {
		document.body.innerHTML = `<div class="expressive-code" data-fb-src="${F}:5-9"><pre><code><div class="ec-line">a</div><div class="ec-line">b</div><div class="ec-line">c</div></code></pre></div>`
		// happy-dom は大きさを持たないので、見えている扱いにする。
		vi.spyOn(Element.prototype, 'getBoundingClientRect').mockReturnValue(new DOMRect(0, 0, 10, 10))
		const el = document.querySelector('.expressive-code')!
		const src = { path: F, start: 5, end: 9 }
		expect(codeLines(el, src, { start: 7, end: 7 }).map((e) => e.textContent)).toEqual(['b'])
		expect(codeLines(el, src, { start: 6, end: 9 }).map((e) => e.textContent)).toEqual(['a', 'b', 'c'])
		// フェンスの行そのもの・行の数が合わないときはブロック全体
		expect(codeLines(el, src, { start: 5, end: 5 })).toEqual([])
		expect(codeLines(el, { ...src, end: 20 }, { start: 12, end: 12 })).toEqual([])
		vi.restoreAllMocks()
	})
	it('行を含む要素が無ければ（タブの見出し・LinkCard の属性の行）、前後の要素のあいだから手がかりの文字で探す', () => {
		document.body.innerHTML = `<header><a>Github Actions</a></header><main>
			<p id="before" data-fb-src="${F}:10">Github Actions を使う前に</p>
			<starlight-tabs><div role="tablist"><a role="tab" id="tab"><span>Github Actions</span></a></div>
			<div role="tabpanel"><p id="after" data-fb-src="${F}:14">以下を足す</p></div></starlight-tabs>
			<div class="card"><span class="desc" id="desc">最初の architecture { } を書く方法</span></div>
			<p data-fb-src="${F}:30">終わり</p>
		</main>`
		// 前の要素（L10）の中の同じ文字・ヘッダーの文字ではなく、あいだのタブを選ぶ。いちばん内側の要素。
		expect(findTarget(document, F, { start: 12, end: 12 }, ['Github Actions'])?.el.tagName).toBe('SPAN')
		expect(findTarget(document, F, { start: 12, end: 12 }, ['Github Actions'])?.el.closest('#tab')).not.toBeNull()
		expect(findTarget(document, F, { start: 20, end: 20 }, ['最初の architecture { } を書く'])?.el.id).toBe('desc')
		// あいだに無ければ探さない（後ろの要素より先は見ない）
		expect(findTarget(document, F, { start: 12, end: 12 }, ['終わり'])).toBeNull()
		expect(findTarget(document, F, { start: 12, end: 12 })).toBeNull()
	})
	it('閉じた details の中の要素も選ぶ（見せるのは popup を開くとき）', () => {
		document.body.innerHTML = `<details data-fb-src="${F}:1-9"><summary>s</summary><p id="in" data-fb-src="${F}:3">x</p></details>`
		expect(findTarget(document, F, { start: 3, end: 3 })?.el.id).toBe('in')
	})
})
