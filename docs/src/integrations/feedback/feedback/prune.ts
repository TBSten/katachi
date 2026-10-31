// 撮影を速くするため、画面から遠い部分の中身を複製しない（filter で落とす）。
// modern-screenshot は文書全体を複製して要素ごとに計算済みのスタイルを写すので、かかる時間は要素数に比例する。
// 画面の外にある要素そのものは残し（計算済みの幅・高さが写るので配置は変わらない）、その子孫だけを落とす。
// 落とすと画面の見た目が変わりうる要素（中身が外にはみ出す・margin が親の外へ相殺する・inline・表の部品・SVG など）は落とさない。

/** 画面の上下にこれだけ余裕を持たせる（CSS px）。影やはみ出しの小さいものを拾うため。 */
export const PRUNE_MARGIN = 50
/** margin の相殺を子孫へたどる深さの上限。超えたら落とさない側に倒す。 */
const MAX_LEAK_DEPTH = 24

type Rect = { top: number; bottom: number; height: number }
type Style = Pick<CSSStyleDeclaration, 'display' | 'position' | 'float' | 'overflowX' | 'overflowY' | 'borderTopWidth' | 'borderBottomWidth' | 'paddingTop' | 'paddingBottom' | 'marginTop' | 'marginBottom'>

/** 計り方。テストでは差し替える（happy-dom には配置が無い）。 */
export type Measure = {
	rect(e: Element): Rect
	style(e: Element): Style
	/** 中身が要素の箱の外へはみ出しているか（float・長い文字列など）。 */
	overflows(e: Element): boolean
}

export const domMeasure: Measure = {
	rect: (e) => e.getBoundingClientRect(),
	style: (e) => getComputedStyle(e),
	overflows: (e) => e.scrollHeight > e.clientHeight + 1 || e.scrollWidth > e.clientWidth + 1,
}

/** 合成木（shadow root を越える）の親。 */
function parentOf(e: Element): Element | null {
	if (e.parentElement) return e.parentElement
	const root = e.parentNode
	return root instanceof ShadowRoot ? root.host : null
}

/** e と、open な shadow root の中を含む子孫の全要素。 */
function allElements(root: Element): Element[] {
	const out: Element[] = [root]
	const walk = (scope: Element | ShadowRoot) => {
		for (const e of scope.querySelectorAll('*')) {
			out.push(e)
			if (e.shadowRoot) walk(e.shadowRoot)
		}
	}
	if (root.shadowRoot) walk(root.shadowRoot)
	walk(root)
	return out
}

const px = (v: string) => parseFloat(v) || 0

/** 子の margin を相殺させない（自分が新しいブロック整形文脈を作る）display。 */
const ISOLATING_DISPLAY = /^(flow-root|flex|grid|table|inline-|-webkit-box)/

/**
 * e の上端（top）または下端（bottom）の margin に、子孫の margin が相殺して混ざりうるか。
 * 混ざるなら子孫を落とすと e の外の配置が変わるので、e は「中身を落とす要素」にできない。
 */
function leaks(e: Element, side: 'top' | 'bottom', m: Measure, depth = 0): boolean {
	if (depth > MAX_LEAK_DEPTH) return true
	const s = m.style(e)
	if (ISOLATING_DISPLAY.test(s.display)) return false
	if (s.overflowX !== 'visible' || s.overflowY !== 'visible') return false
	if (s.position === 'absolute' || s.position === 'fixed' || s.float !== 'none') return false
	const border = side === 'top' ? s.borderTopWidth : s.borderBottomWidth
	const padding = side === 'top' ? s.paddingTop : s.paddingBottom
	if (px(border) > 0 || px(padding) > 0) return false
	// 端の、流れに乗っている子を探す。文字か inline が先に来れば行の箱ができるので、相殺はそこで止まる。
	const nodes = [...e.childNodes]
	if (side === 'bottom') nodes.reverse()
	for (const n of nodes) {
		if (n.nodeType === Node.TEXT_NODE) {
			if (/\S/.test(n.textContent ?? '')) return false
			continue
		}
		if (!(n instanceof Element)) continue
		const c = m.style(n)
		if (c.display === 'none' || c.position === 'absolute' || c.position === 'fixed') continue
		if (c.display === 'contents') return true // 中を見ないので、落とさない側に倒す
		if (c.display.startsWith('inline') || c.display.startsWith('ruby')) return false
		if (px(side === 'top' ? c.marginTop : c.marginBottom) !== 0) return true
		return leaks(n, side, m, depth + 1)
	}
	return false
}

/** 中身を落としてよいか（e は画面の外にあり、画面内に出る子孫も無い）。 */
function canDropChildren(e: Element, m: Measure): boolean {
	// SVG は <defs>・<symbol> を画面内の <use> が参照しうる。shadow root を持つ要素は中身の並びが light DOM と違う。
	if (!(e instanceof HTMLElement) || e.shadowRoot) return false
	if (e.querySelector('svg defs, svg symbol')) return false
	const s = m.style(e)
	const d = s.display
	// inline は中身を落とすと行の折り返しが変わる。表の部品は列の幅が他の行の中身で決まる。contents は自分の箱が無い。
	if (d.startsWith('inline') || d.startsWith('table-') || d.startsWith('ruby') || d === 'contents' || d === 'none') return false
	if (m.rect(e).height <= 0) return false
	if (m.overflows(e)) return false
	return !leaks(e, 'top', m) && !leaks(e, 'bottom', m)
}

/**
 * 中身を落とす要素の集合を決める。画面（上下 margin px の余裕つき）に掛かる要素とその祖先は全部複製し、
 * 画面の外にある要素のうち、親を複製するものだけを「箱だけ残して中身を落とす」候補にする。
 * viewportHeight は CSS px。測るのは開始時の一度だけ（複製の途中で配置は変わらない前提）。
 */
export function planPrune(root: Element, viewportHeight: number, m: Measure = domMeasure, margin = PRUNE_MARGIN): Set<Element> {
	const all = allElements(root)
	const onScreen = new Set<Element>()
	for (const e of all) {
		if (onScreen.has(e)) continue
		const r = m.rect(e)
		if (r.bottom < -margin || r.top > viewportHeight + margin) continue
		// 画面内に出る要素の祖先は全部残す（fixed・sticky・絶対配置で、親の箱の外から画面に出るもの）。
		for (let p: Element | null = e; p && !onScreen.has(p); p = parentOf(p)) onScreen.add(p)
	}
	const drop = new Set<Element>()
	const visit = (e: Element) => {
		for (const c of e.children) {
			if (onScreen.has(c)) visit(c)
			else if (canDropChildren(c, m)) drop.add(c)
			else visit(c)
		}
	}
	visit(root)
	return drop
}

/** modern-screenshot の filter に渡す判定。親が「中身を落とす要素」なら複製しない。 */
export function pruneFilter(drop: ReadonlySet<Element>): (node: Node) => boolean {
	return (node) => {
		const p = node.parentNode
		const parent = p instanceof ShadowRoot ? p.host : p
		return !(parent instanceof Element && drop.has(parent))
	}
}
