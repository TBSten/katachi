// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest'
import { planPrune, pruneFilter, type Measure } from '../feedback/prune'

/** 画面の高さ（CSS px）。 */
const VH = 800

/**
 * happy-dom には配置が無いので、data-* で位置とスタイルを決め打ちする。
 * data-top / data-h: 画面の座標での上端と高さ（無ければ親と同じ）。data-css: "display:inline;margin-top:8px" の形。
 * data-overflow があれば、中身がはみ出している。
 */
const fake: Measure = {
	rect(e) {
		const box = (x: Element | null): { top: number; height: number } => {
			if (!x) return { top: 0, height: VH }
			const t = x.getAttribute('data-top')
			if (t !== null) return { top: Number(t), height: Number(x.getAttribute('data-h') ?? 0) }
			return box(x.parentElement ?? (x.parentNode instanceof ShadowRoot ? x.parentNode.host : null))
		}
		const b = box(e)
		return { top: b.top, bottom: b.top + b.height, height: b.height }
	},
	style(e) {
		const css: Record<string, string> = {}
		for (const kv of (e.getAttribute('data-css') ?? '').split(';').filter(Boolean)) {
			const [k, v] = kv.split(':')
			css[k.trim()] = v.trim()
		}
		return {
			display: css.display ?? 'block',
			position: css.position ?? 'static',
			float: css.float ?? 'none',
			overflowX: css.overflow ?? 'visible',
			overflowY: css.overflow ?? 'visible',
			borderTopWidth: css['border-top'] ?? '0px',
			borderBottomWidth: css['border-bottom'] ?? '0px',
			paddingTop: css['padding-top'] ?? '0px',
			paddingBottom: css['padding-bottom'] ?? '0px',
			marginTop: css['margin-top'] ?? '0px',
			marginBottom: css['margin-bottom'] ?? '0px',
		}
	},
	overflows: (e) => e.hasAttribute('data-overflow'),
}

function setup(html: string) {
	document.body.innerHTML = html
	document.body.setAttribute('data-top', '-2000')
	document.body.setAttribute('data-h', '6000')
	const drop = planPrune(document.body, VH, fake)
	const keep = pruneFilter(drop)
	/** filter を親から順にかけて、複製される要素の id。 */
	const cloned: string[] = []
	const walk = (n: Node) => {
		for (const c of n.childNodes) {
			if (!keep(c)) continue
			if (c instanceof Element && c.id) cloned.push(c.id)
			walk(c)
		}
	}
	walk(document.body)
	const id = (s: string) => document.getElementById(s)!
	return { drop, keep, cloned, id }
}

describe('planPrune / pruneFilter（画面から遠い中身を複製しない）', () => {
	it('画面内の要素は中身まで複製する。画面外のブロックは箱だけ残して中身を落とす', () => {
		const { cloned, drop, id } = setup(`
			<main id="main" data-top="-2000" data-h="6000">
				<p id="above" data-top="-1000" data-h="100"><b id="above-b">x</b>上</p>
				<p id="in" data-top="100" data-h="100"><b id="in-b">x</b>中</p>
				<p id="below" data-top="1500" data-h="100"><b id="below-b">x</b>下</p>
			</main>`)
		expect(cloned).toEqual(['main', 'above', 'in', 'in-b', 'below'])
		expect(drop.has(id('above'))).toBe(true)
		expect(drop.has(id('below'))).toBe(true)
	})
	it('画面の上下 50px までは画面内とみなす', () => {
		const { cloned } = setup(`
			<div id="near-top" data-top="-140" data-h="100"><i id="nt">x</i></div>
			<div id="near-bottom" data-top="${VH + 40}" data-h="100"><i id="nb">x</i></div>
			<div id="far" data-top="${VH + 60}" data-h="100"><i id="f">x</i></div>`)
		expect(cloned).toEqual(['near-top', 'nt', 'near-bottom', 'nb', 'far'])
	})
	it('文字の節点も、親が中身を落とす要素なら複製しない', () => {
		const { keep, id } = setup('<p id="far" data-top="3000" data-h="20">遠い</p>')
		expect(keep(id('far').firstChild!)).toBe(false)
		expect(keep(id('far'))).toBe(true)
	})
	it('fixed の子が画面に出ているなら、画面外の親の中身も落とさない', () => {
		const { cloned } = setup(`
			<div id="parent" data-top="-1500" data-h="100">
				<nav id="fixed" data-top="0" data-h="60" data-css="position:fixed"><a id="link">x</a></nav>
			</div>`)
		expect(cloned).toEqual(['parent', 'fixed', 'link'])
	})
	it('sticky で貼り付いて画面に出ている要素と、その祖先は落とさない', () => {
		const { cloned } = setup(`
			<aside id="col" data-top="-3000" data-h="2900">
				<div id="sticky" data-top="0" data-h="400" data-css="position:sticky"><ul id="toc"><li id="item">x</li></ul></div>
				<div id="rest" data-top="-2000" data-h="100"><i id="r">x</i></div>
			</aside>`)
		expect(cloned).toEqual(['col', 'sticky', 'toc', 'item', 'rest'])
	})
	it('画面外の親から、絶対配置で画面内に出る子があれば、親の中身を落とさない', () => {
		const { cloned } = setup(`
			<section id="sec" data-top="${VH + 300}" data-h="100">
				<div id="pop" data-top="200" data-h="100" data-css="position:absolute">x</div>
				<p id="sib" data-top="${VH + 300}" data-h="50"><i id="s">x</i></p>
			</section>`)
		expect(cloned).toEqual(['sec', 'pop', 'sib'])
	})
	it('inline の要素は中身を落とさない（行の折り返しが変わるので）。その中のブロックは落とせる', () => {
		const { cloned } = setup(`
			<span id="inl" data-top="-1000" data-h="20" data-css="display:inline"><b id="b">x</b></span>
			<a id="ib" data-top="-1000" data-h="20" data-css="display:inline-block"><b id="ib-b">x</b></a>`)
		expect(cloned).toEqual(['inl', 'b', 'ib', 'ib-b'])
	})
	it('子の margin が相殺して外に出るブロックは中身を落とさない（ずれるので）。padding で区切られていれば落とす', () => {
		const { cloned, drop, id } = setup(`
			<div id="leak" data-top="-1000" data-h="100"><h2 id="h" data-css="margin-top:32px"><i id="hi">x</i></h2></div>
			<div id="deep" data-top="-900" data-h="100"><div id="mid"><p id="p" data-css="margin-bottom:16px">x</p></div></div>
			<div id="pad" data-top="-800" data-h="100" data-css="padding-top:8px;padding-bottom:8px"><h2 id="h2" data-css="margin-top:32px">x</h2></div>
			<div id="flex" data-top="-700" data-h="100" data-css="display:flex"><h2 id="h3" data-css="margin-top:32px">x</h2></div>
			<div id="text" data-top="-600" data-h="100">文字が先<h2 id="h4" data-css="margin-top:32px">x</h2></div>`)
		expect(drop.has(id('leak'))).toBe(false)
		expect(drop.has(id('h'))).toBe(true) // h2 自身は相殺の外へは出ない（中身は無い）
		expect(drop.has(id('deep'))).toBe(false)
		expect(drop.has(id('mid'))).toBe(false)
		expect(drop.has(id('p'))).toBe(true)
		for (const k of ['pad', 'flex', 'text']) expect(drop.has(id(k))).toBe(true)
		expect(cloned).toEqual(['leak', 'h', 'deep', 'mid', 'p', 'pad', 'flex', 'text'])
	})
	it('表の部品・はみ出し・高さ 0・SVG の defs/symbol を持つものは落とさない', () => {
		const { drop, id } = setup(`
			<table id="tbl" data-top="-1000" data-h="100" data-css="display:table"><tbody id="tb" data-css="display:table-row-group"><tr id="tr" data-css="display:table-row"><td id="td" data-css="display:table-cell">x</td></tr></tbody></table>
			<div id="over" data-top="-900" data-h="10" data-overflow><p>x</p></div>
			<div id="zero" data-top="-800" data-h="0"><p id="zp">x</p></div>
			<div id="sprite" data-top="-700" data-h="10"><svg id="svg"><defs><linearGradient id="g"></linearGradient></defs></svg></div>`)
		expect(drop.has(id('tbl'))).toBe(true) // 表そのものは箱ごと残せる
		for (const k of ['tb', 'tr', 'td', 'over', 'zero', 'sprite', 'svg']) expect(drop.has(id(k)), k).toBe(false)
	})
	it('shadow root の中は落とさない。中の要素が画面に出ていれば host も残す', () => {
		document.body.innerHTML = '<div id="host" data-top="-1500" data-h="100"></div><div id="host2" data-top="-1500" data-h="100"></div>'
		const host = document.getElementById('host')!
		const sr = host.attachShadow({ mode: 'open' })
		sr.innerHTML = '<dialog id="dlg" data-top="100" data-h="100" data-css="position:fixed"><p>x</p></dialog>'
		const host2 = document.getElementById('host2')!
		host2.attachShadow({ mode: 'open' }).innerHTML = '<p data-top="-1500" data-h="10">x</p>'
		document.body.setAttribute('data-top', '-2000')
		document.body.setAttribute('data-h', '6000')
		const drop = planPrune(document.body, VH, fake)
		expect(drop.has(host)).toBe(false)
		expect(drop.has(host2)).toBe(false) // shadow root を持つ要素は中身の並びが違うので落とさない
		const keep = pruneFilter(drop)
		expect(keep(sr.firstElementChild!)).toBe(true)
	})
})
