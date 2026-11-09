// Review を開いてから閉じるまでの 1 回ぶん: 読み込み → 印と枠 → popup → 解決済みの印の付け外し → 片付け。
// 閉じると、印・枠・popup・パネル・リスナーを全部消す（host を remove するだけ）。
import type { ReviewResult, ReviewView } from '../core/protocol'
import { el, isNarrow } from '../shared/dom'
import { runInsideHost, swallowEscKeyup, inToolbar, onKeyInside } from '../shared/keys'
import { createHost } from '../shared/shell'
import { pageSourceFile } from '../shared/srcmap'
import { css } from './css'
import { formatLine, pageUrlOf, priorityTier } from './model'
import { groupByFile, onPage, parseFilter, passes, PRIORITY_STEPS, shortFile, sortItems, type Filter } from './order'
import { expandCode, findTarget, isHidden, rectOfTarget, type Target } from './place'
import { chooseSide, placeAt } from './position'
import { renderInline, renderMarkdown } from './render'

export type ReviewCall = (req: { op: 'load' } | { op: 'resolve'; id: string; note: string } | { op: 'reopen'; id: string }) => Promise<ReviewResult | { ok: false; error: string }>

export type ReviewSession = {
	close(): void
	/** id の指摘を開く（`#review=<id>` のリンク）。読み込み前なら読み込んだあとで開く。 */
	reveal(id: string): void
}

/** 別のページへ移ったあとで開く指摘の id（sessionStorage）。 */
export const OPEN_KEY = 'katachi-review:open'
/** 開いている指摘の id（sessionStorage）。docs を直してページが読み直されても、同じ指摘を開き直す。 */
export const ACTIVE_KEY = 'katachi-review:active'
const FILTER_KEY = 'katachi-review:filter'
const FOLD_KEY = 'katachi-review:folded'
/** 解決済みにしたあと「戻す」を出しておく時間。 */
const UNDO_MS = 6000

const store = {
	get(s: Storage | undefined, k: string): string | null {
		try { return s?.getItem(k) ?? null } catch { return null }
	},
	set(s: Storage | undefined, k: string, v: string | null) {
		try { v === null ? s?.removeItem(k) : s?.setItem(k, v) } catch { /* 覚えられなくても動く */ }
	},
}
const local = () => (typeof localStorage === 'undefined' ? undefined : localStorage)
const session = () => (typeof sessionStorage === 'undefined' ? undefined : sessionStorage)

const isEditable = (n: EventTarget | undefined) => n instanceof HTMLElement && (n.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(n.tagName))

const fmtTime = (iso?: string) => {
	if (!iso) return ''
	const d = new Date(iso)
	if (Number.isNaN(d.getTime())) return iso
	const two = (n: number) => String(n).padStart(2, '0')
	return `${d.getMonth() + 1}/${d.getDate()} ${two(d.getHours())}:${two(d.getMinutes())}`
}

/** 指摘を書いた行と今の行の関係・置けなかった理由（popup と一覧に出す）。 */
function placeNote(v: ReviewView, found: boolean, hasPage: boolean): { text: string; warn: boolean } | null {
	const written = v.written ? formatLine(v.written) : null
	if (!hasPage && v.place !== 'no-file') return { text: `${v.file} はサイトのページではありません。エディタで開いて確かめてください。`, warn: false }
	switch (v.place) {
		case 'moved': return { text: `指摘を書いたあとでソースが変わったので、${written} → ${formatLine(v.line!)} に合わせて出しています。`, warn: false }
		case 'lost': return { text: `quote が今のソースに見つかりません。書かれた行（${written}）に出しています。もう直っているかもしれません。`, warn: true }
		case 'no-file': return { text: `ファイルがありません: ${v.file}`, warn: true }
		case 'page': return { text: 'ページ全体への指摘です（行の指定がありません）。印はページの見出しに出しています。', warn: false }
		default: return found || !v.line ? null : { text: `${formatLine(v.line)} は本文に出ない行です（frontmatter・import・コメントなど）。印はページの見出しに出しています。`, warn: false }
	}
}

/** 隠れている要素を見えるようにする（閉じた details を開き、選ばれていないタブを選ぶ）。 */
function unhide(target: Element) {
	for (let cur: Element | null = target; cur; cur = cur.parentElement) {
		if (cur instanceof HTMLDetailsElement && !cur.open) cur.open = true
		if (cur.getAttribute('role') === 'tabpanel' && cur.hasAttribute('hidden') && cur.id) {
			// Starlight のタブは aria-controls ではなく href="#<panel の id>" で panel を指す。
			const id = CSS.escape(cur.id)
			const tab = document.querySelector<HTMLElement>(`[role=tab][aria-controls="${id}"], [role=tab][href="#${id}"]`)
			tab?.click()
		}
	}
}

export function openReview(o: { call: ReviewCall; onClose(): void }): ReviewSession {
	const base = import.meta.env.BASE_URL.replace(/\/$/, '')
	const abort = new AbortController()
	const { host, root } = createHost('katachi-review-root', css)
	const layer = el('div', 'layer')
	const pop = el('div', 'pop')
	pop.hidden = true
	pop.setAttribute('role', 'dialog')
	pop.setAttribute('aria-label', '指摘')
	const panel = el('section', 'panel')
	const toastBox = el('div')
	// 枠と popup をつなぐ線（PC だけ。popup が指摘の場所から離れていても、どこへの指摘か分かるように）。
	const SVG = 'http://www.w3.org/2000/svg'
	const wire = document.createElementNS(SVG, 'svg')
	wire.setAttribute('class', 'wire')
	const wireLine = document.createElementNS(SVG, 'path')
	wire.append(wireLine)
	root.append(layer, wire, panel, pop, toastBox)

	let filter: Filter = parseFilter(store.get(local(), FILTER_KEY))
	let items: ReviewView[] = []
	let sets: Extract<ReviewResult, { ok: true }>['sets'] = []
	/** リポジトリ直下の絶対パス（エディタで開くのに使う）。 */
	let repoRoot = ''
	let sorted: ReviewView[] = []
	let here: ReviewView[] = []
	let loadError: string | null = null
	let loaded = false
	const targets = new Map<string, Target | null>()
	const marks = new Map<string, { box: HTMLElement; pin: HTMLButtonElement }>()
	let activeId: string | null = null
	/** 解決済みにしたあと次の指摘へ進んだ直後は、ボタンを押せない時刻（ダブルクリックで次の指摘まで解決済みにしない）。 */
	let guardUntil = 0
	const GUARD_MS = 700
	/** popup に出す保存の失敗（別の指摘を開くと消す）。 */
	let popError: string | null = null
	let busy = false
	let closed = false
	let raf = 0
	const source = (() => { const s = pageSourceFile(); return s.guessed ? null : s.path })()

	// ---- データ ----

	function accept(r: ReviewResult | { ok: false; error: string }): boolean {
		if (!r.ok) { loadError = r.error; return false }
		loadError = null
		loaded = true
		items = r.items
		sets = r.sets
		repoRoot = r.root ?? ''
		return true
	}

	/** 絞り込みと並びを計算し直し、印・一覧・popup を作り直す。 */
	function rebuild() {
		sorted = sortItems(items.filter((v) => passes(v, filter)), base)
		here = sorted.filter((v) => onPage(v, { pathname: location.pathname, source, base }))
		targets.clear()
		for (const v of here) targets.set(v.id, v.line ? findTarget(document, v.file, v.line) : null)
		if (activeId && !sorted.some((v) => v.id === activeId)) activeId = null
		renderMarks()
		renderPanel()
		renderPop()
		schedule()
	}

	async function load() {
		const r = await o.call({ op: 'load' })
		if (closed) return
		accept(r)
		rebuild()
		const want = store.get(session(), OPEN_KEY)
		if (want) {
			store.set(session(), OPEN_KEY, null)
			reveal(want)
			return
		}
		// 読み直す前に開いていた指摘（docs を直すと Astro がページを読み直す）。このページのものだけ、黙って開き直す。
		const last = store.get(session(), ACTIVE_KEY)
		if (last && here.some((v) => v.id === last)) open(last)
		else if (last) store.set(session(), ACTIVE_KEY, null)
	}

	/** id の指摘を開く。このページ（かページの無いファイル）のものだけ。開けなければ理由を知らせる。 */
	function reveal(want: string) {
		const v = sorted.find((x) => x.id === want)
		if (v && (here.includes(v) || !pageUrlOf(v.file, base))) open(want)
		else if (v) toast('その指摘はほかのページです（一覧の「ほかのページ」から開けます）', undefined, true)
		else toast(items.some((x) => x.id === want) ? 'その指摘は絞り込みで隠れています（解決済みも出す・優先度を見直してください）' : 'その指摘は見つかりません（消されたか、見出しか file が変わったかもしれません）', undefined, true)
	}

	// ---- 印と枠 ----

	const numberOf = (id: string) => sorted.findIndex((v) => v.id === id) + 1

	/**
	 * 印を置く先。本文の要素が無い指摘（ページ全体・frontmatter など本文に出ない行）は、ページの見出し（h1）に置く。
	 * targets には本文で見つけた要素だけを入れておく（「本文の外」の表示に使う）。
	 */
	const markTarget = (v: ReviewView): Target | null => {
		const t = targets.get(v.id)
		if (t) return t
		const h1 = document.querySelector('main h1') ?? document.querySelector('h1')
		return h1 ? { el: h1, lines: [] } : null
	}

	/** 同じ場所（同じ要素・同じコードの行）を指す指摘は、枠を 1 つにまとめる（色はいちばん高い優先度）。 */
	const boxes = new Map<string, HTMLElement>()
	const placeKey = (t: Target) => `${t.el.getAttribute('data-fb-src')}|${t.lines.length ? `${[...t.el.querySelectorAll('.ec-line')].indexOf(t.lines[0]!)}+${t.lines.length}` : ''}`

	function renderMarks() {
		layer.replaceChildren()
		marks.clear()
		boxes.clear()
		for (const v of here) {
			const t = markTarget(v)
			if (!t) continue
			const tier = priorityTier(v.priority)
			const key = placeKey(t)
			const shared = boxes.get(key)
			const box = shared ?? el('div', 'box')
			if (!shared) {
				boxes.set(key, box)
				box.dataset.ids = ''
			}
			box.dataset.ids += ` ${v.id}`
			const pin = el('button', 'pin', String(numberOf(v.id)))
			pin.dataset.tier = tier
			pin.dataset.id = v.id
			pin.classList.toggle('is-resolved', v.status === 'resolved')
			// まとめた枠の色は、未解決のうちいちばん高い優先度（全部解決済みなら薄く）。
			const rank = (x: string | undefined) => ['none', 'min', 'low', 'mid', 'high'].indexOf(x ?? 'none')
			if (!shared || (v.status === 'open' && (box.classList.contains('is-resolved') || rank(tier) > rank(box.dataset.tier)))) box.dataset.tier = tier
			if (!shared) box.classList.toggle('is-resolved', v.status === 'resolved')
			else if (v.status === 'open') box.classList.remove('is-resolved')
			pin.type = 'button'
			pin.title = `${v.priority !== null ? `[p${v.priority}] ` : ''}${v.title}`
			pin.setAttribute('aria-label', `指摘 ${numberOf(v.id)}: ${v.title}`)
			pin.addEventListener('click', (e) => {
				e.stopPropagation()
				if (activeId === v.id && !pop.hidden) closePop()
				else open(v.id, { scroll: false })
			})
			if (!shared) layer.append(box)
			layer.append(pin)
			marks.set(v.id, { box, pin })
		}
	}

	/**
	 * 画面の上に貼り付いた帯（Starlight のヘッダー・スマホの目次の帯）。枠と印がその上に重ならないよう、その下端から下だけに出す。
	 * 帯の候補は開いたときに 1 回だけ探し、位置は置き直すたびに測る。
	 */
	const bars = [...document.body.querySelectorAll<HTMLElement>('body > *, header, nav, [class*="toc"], mobile-starlight-toc')].filter((e) => {
		const p = getComputedStyle(e).position
		return (p === 'fixed' || p === 'sticky') && !e.hasAttribute('data-katachi-dev-ui') && e.localName !== 'astro-dev-toolbar'
	})
	function topInset(): number {
		let inset = 0
		for (const b of bars) {
			const r = b.getBoundingClientRect()
			if (r.height > 0 && r.height < 160 && r.top <= inset + 1 && r.width > innerWidth * 0.5) inset = Math.max(inset, r.bottom)
		}
		return inset
	}

	/** 今の位置に枠と印を置き直す（重なる印は縦にずらす）。popup も置き直す。 */
	function refresh() {
		if (closed) return
		const inset = topInset()
		layer.style.clipPath = wire.style.clipPath = inset ? `inset(${inset}px 0 0 0)` : ''
		/** 置いた印の左端と下端（同じ列で重なれば下へずらす）。 */
		const placed: { left: number; bottom: number }[] = []
		for (const v of here) {
			const m = marks.get(v.id)
			let t = markTarget(v)
			if (!m || !t) continue
			// DOM が差し替わった（mermaid の描画など）ら探し直す。
			if (!t.el.isConnected) {
				if (targets.get(v.id)) targets.set(v.id, v.line ? findTarget(document, v.file, v.line) : null)
				t = markTarget(v)
				if (!t) { m.box.style.display = m.pin.style.display = 'none'; continue }
			}
			const r = rectOfTarget(t)
			const hidden = r.width === 0 && r.height === 0
			const offscreen = r.bottom < -40 || r.top > innerHeight + 40
			m.box.style.display = m.pin.style.display = hidden || offscreen ? 'none' : ''
			if (hidden || offscreen) continue
			Object.assign(m.box.style, { left: `${r.left - 3}px`, top: `${r.top - 3}px`, width: `${r.width + 6}px`, height: `${r.height + 6}px` })
			const w = m.pin.offsetWidth || 22
			const ph = m.pin.offsetHeight || 22
			const outside = r.left - w - 6
			const left = outside >= 4 ? outside : r.left + 4
			let top = Math.max(4, r.top)
			// here は行の順なので、前に置いた印の下端より上に来るなら、その下へずらす。
			for (const p of placed) if (Math.abs(p.left - left) < w && top < p.bottom + 3) top = p.bottom + 3
			placed.push({ left, bottom: top + ph })
			Object.assign(m.pin.style, { left: `${left}px`, top: `${top}px` })
		}
		placePop()
	}
	const schedule = () => { cancelAnimationFrame(raf); raf = requestAnimationFrame(refresh) }

	// ---- popup ----

	/** popup を置く基準（指摘の要素・ページの見出し・パネル）。 */
	function anchorOf(v: ReviewView): DOMRect {
		const t = here.includes(v) ? markTarget(v) : null
		if (t) {
			const r = rectOfTarget(t)
			if (r.width || r.height) return r
		}
		return panel.getBoundingClientRect()
	}

	/** popup と枠を線でつなぐ。横に置いたときだけ（下・上に置いたときは近いので引かない）。 */
	function drawWire(rect: DOMRect | null, side: string | null) {
		const pr = pop.getBoundingClientRect()
		if (!rect || pop.hidden || isNarrow() || (side !== 'right' && side !== 'left') || !(rect.width || rect.height) || rect.bottom < 0 || rect.top > innerHeight) {
			wire.style.display = 'none'
			return
		}
		const x1 = side === 'right' ? rect.right + 3 : rect.left - 3
		const x2 = side === 'right' ? pr.left : pr.right
		if (Math.abs(x2 - x1) < 4) { wire.style.display = 'none'; return }
		const y1 = Math.min(Math.max(pr.top + 22, rect.top + 4), rect.bottom - 4)
		const y2 = Math.min(Math.max(y1, pr.top + 14), pr.bottom - 14)
		const mx = (x1 + x2) / 2
		wireLine.setAttribute('d', `M${x1},${y1} C${mx},${y1} ${mx},${y2} ${x2},${y2}`)
		wire.dataset.tier = pop.dataset.tier ?? 'none'
		wire.style.display = ''
	}

	function placePop() {
		if (pop.hidden || !activeId || isNarrow()) return drawWire(null, null)
		const v = sorted.find((x) => x.id === activeId)
		if (!v) return drawWire(null, null)
		const rect = anchorOf(v)
		const view = { width: innerWidth, height: innerHeight }
		const { side, width } = chooseSide(rect, view, 380, 280)
		pop.style.width = `${width}px`
		pop.style.maxHeight = ''
		const pos = placeAt(side, rect, { width, height: pop.offsetHeight }, view)
		// 一覧のパネルに重なるなら、パネルの下へずらす（入らなければ高さを詰める。詰めても低すぎればそのまま重ねる）。
		const pr = panel.getBoundingClientRect()
		const overlaps = pos.left < pr.right && pos.left + width > pr.left && pos.top < pr.bottom && pos.top + pop.offsetHeight > pr.top
		if (overlaps) {
			const top = pr.bottom + 8
			const room = innerHeight - top - 8
			if (room >= 200) {
				pos.top = Math.max(top, Math.min(pos.top, innerHeight - 8 - pop.offsetHeight))
				pop.style.maxHeight = `${Math.min(room, 520)}px`
			}
		}
		pop.style.left = `${pos.left}px`
		pop.style.top = `${pos.top}px`
		pop.dataset.side = side
		drawWire(here.includes(v) ? rect : null, side)
	}

	function closePop() {
		pop.hidden = true
		drawWire(null, null)
		panel.classList.remove('has-pop')
		activeId = null
		store.set(session(), ACTIVE_KEY, null)
		if (autoFolded) {
			autoFolded = false
			renderPanel()
		}
		syncActive()
	}

	function syncActive() {
		for (const [id, m] of marks) m.pin.classList.toggle('is-active', id === activeId)
		for (const b of boxes.values()) {
			const on = !!activeId && (b.dataset.ids ?? '').split(' ').includes(activeId)
			b.classList.toggle('is-active', on)
			// 選んでいる指摘の色で光らせる。
			if (on) b.dataset.activeTier = marks.get(activeId!)?.pin.dataset.tier ?? ''
			else delete b.dataset.activeTier
		}
		for (const row of panel.querySelectorAll<HTMLElement>('.row[data-id]')) row.classList.toggle('is-active', row.dataset.id === activeId)
	}

	/** 指摘を開く。別のページの指摘なら、そのページへ移ってから開く。 */
	function open(id: string, opt: { scroll?: boolean } = {}) {
		const v = sorted.find((x) => x.id === id) ?? items.find((x) => x.id === id)
		if (!v) return
		const url = pageUrlOf(v.file, base)
		if (url && !onPage(v, { pathname: location.pathname, source, base })) {
			store.set(session(), OPEN_KEY, id)
			location.assign(url)
			return
		}
		if (activeId !== id) popError = null
		activeId = id
		store.set(session(), ACTIVE_KEY, id)
		// popup を開いているあいだは、一覧を畳んで popup の場所を空ける（閉じれば戻す）。スマホのシートは下に重ねるので畳まない以外は同じ。
		if (!folded && !autoFolded) {
			autoFolded = true
			renderPanel()
		}
		const t = here.includes(v) ? markTarget(v) : null
		if (t) {
			// 隠れていた（タブ・details・畳んだコード）なら、開くと場所が動くので、印から開いたときもスクロールする。
			let moved = false
			if (isHidden(t)) {
				moved = true
				unhide(t.el)
				// 開いたタブ・details の中身で探し直す（見えている要素を選び直す）。
				targets.set(id, v.line ? findTarget(document, v.file, v.line) : null)
			}
			const nt = markTarget(v)
			// コードの行が畳んだブロックの中なら開く（「Show more」）。
			if (nt && expandCode(nt)) moved = true
			// scrollIntoView は畳んだコードブロックの中までスクロールしてしまうので、ページだけを動かす。開いた直後は高さが変わるので 2 フレーム待つ。
			if (nt && (opt.scroll !== false || moved)) {
				requestAnimationFrame(() => requestAnimationFrame(() => {
					if (closed || activeId !== id) return
					const r = rectOfTarget(nt)
					if (isNarrow()) {
						// 下からのシートが画面の下半分を覆うので、上のほう（ヘッダーの下）に寄せる。
						const sheetTop = pop.getBoundingClientRect().top || innerHeight * 0.45
						if (r.top < 110 || r.top > sheetTop - 40) window.scrollBy({ top: r.top - 120, behavior: 'instant' as ScrollBehavior })
					} else if (r.top < 60 || r.bottom > innerHeight - 60) window.scrollBy({ top: r.top + Math.min(r.height, innerHeight * 0.6) / 2 - innerHeight / 2, behavior: 'instant' as ScrollBehavior })
					schedule()
				}))
			}
		}
		renderPop()
		syncActive()
		schedule()
	}

	function step(d: 1 | -1) {
		if (!activeId) return
		const i = sorted.findIndex((v) => v.id === activeId)
		const next = sorted[i + d]
		if (next) open(next.id)
	}

	function renderPop() {
		const v = activeId ? sorted.find((x) => x.id === activeId) : undefined
		if (!v) {
			// 開いていた指摘が消えた（絞り込み・解決済み・ファイルの書き換え）。閉じたときと同じに戻す。
			if (!pop.hidden || autoFolded) closePop()
			return
		}
		pop.hidden = false
		pop.dataset.tier = priorityTier(v.priority)
		pop.dataset.id = v.id
		const i = sorted.indexOf(v)

		const head = el('div', 'pop-head')
		if (v.priority !== null) head.append(el('span', 'chip', `p${v.priority}`))
		if (v.status === 'resolved') head.append(el('span', 'badge', '解決済み'))
		// 行番号は切れないよう先に、ファイルはファイル名だけ（全体は title）。
		const where = el('span', 'where')
		if (v.line) where.append(el('b', 'where-line', formatLine(v.line)), document.createTextNode(' '))
		where.append(document.createTextNode(v.file.split('/').pop() ?? v.file))
		const set = sets.find((s) => s.name === v.set)
		const loc = `${v.file}${v.line ? `:${v.line.start === v.line.end ? v.line.start : `${v.line.start}-${v.line.end}`}` : ''}`
		where.title = `${loc}\n${set?.title ?? v.set}${set?.base ? `（${set.base} の行番号）` : ''}\nクリックで「${loc}」をコピー`
		where.setAttribute('role', 'button')
		where.tabIndex = 0
		const copy = () => void navigator.clipboard?.writeText(loc).then(() => toast(`コピーしました: ${loc}`), () => toast('コピーできませんでした', undefined, true))
		where.addEventListener('click', copy)
		onKeyInside(where, 'keydown', (e) => { if (e.key === 'Enter') copy() })
		const x = el('button', 'x', '×')
		x.type = 'button'
		x.title = '閉じる（Esc）'
		x.setAttribute('aria-label', '閉じる')
		x.addEventListener('click', closePop)
		head.append(where)
		if (repoRoot && v.place !== 'no-file') {
			// Astro の dev サーバーの「エディタで開く」（dev toolbar の Audit・Inspect と同じ口）。
			const edit = el('button', 'edit', '開く')
			edit.type = 'button'
			edit.title = `エディタで開く（${loc}）`
			edit.addEventListener('click', () => {
				const file = `${repoRoot.replace(/\/$/, '')}/${v.file}:${v.line?.start ?? 1}:1`
				fetch(`/__open-in-editor?file=${encodeURIComponent(file)}`).then(
					(res) => toast(res.ok ? `エディタで開きます: ${loc}` : 'エディタで開けませんでした', undefined, !res.ok),
					() => toast('エディタで開けませんでした', undefined, true),
				)
			})
			head.append(edit)
		}
		head.append(x)

		const body = el('div', 'pop-body')
		const title = el('h3', 'pop-title')
		title.append(renderInline(v.title))
		body.append(title)
		const md = el('div', 'md')
		md.append(renderMarkdown(v.body))
		body.append(md)
		const pn = placeNote(v, !!targets.get(v.id), pageUrlOf(v.file, base) !== null)
		if (pn) body.append(el('p', `info${pn.warn ? ' warn' : ''}`, pn.text))
		if (v.aiNote) {
			const p = el('p', 'info')
			p.append(el('b', undefined, 'agent から: '), document.createTextNode(v.aiNote))
			body.append(p)
		}

		const foot = el('div', 'pop-foot')
		const act = el('div', 'act')
		const err = el('div', 'err')
		err.setAttribute('role', 'alert')
		err.textContent = popError ?? ''
		err.hidden = !popError
		if (v.status === 'open') {
			const input = el('input')
			input.type = 'text'
			input.placeholder = 'ひとこと（任意。agent が読みます）'
			input.maxLength = 500
			input.setAttribute('aria-label', '解決済みにするときのひとこと')
			const btn = el('button', 'btn primary', '解決済みにする')
			btn.type = 'button'
			btn.disabled = busy || Date.now() < guardUntil
			const go = () => void resolve(v, input.value)
			btn.addEventListener('click', go)
			// 変換の確定の Enter では送らない（Safari は確定の keydown で isComposing が false のまま keyCode 229 を出す）。
			onKeyInside(input, 'keydown', (e) => { if (e.key === 'Enter' && !e.isComposing && e.keyCode !== 229) { e.preventDefault(); go() } })
			act.append(input, btn)
		} else {
			const line = el('span', 'resolved-line', `✓ 解決済み ${fmtTime(v.resolvedAt)}${v.note ? ` — ${v.note}` : ''}`)
			const back = el('button', 'btn', '戻す')
			back.type = 'button'
			back.disabled = busy
			back.title = '未解決に戻す'
			back.addEventListener('click', () => void reopen(v))
			act.append(line, back)
		}
		const nav = el('div', 'nav')
		const prev = el('button', 'btn', '‹ 前')
		const next = el('button', 'btn', '次 ›')
		prev.type = next.type = 'button'
		prev.disabled = i <= 0
		next.disabled = i >= sorted.length - 1
		const nv = sorted[i + 1]
		if (nv && !onPage(nv, { pathname: location.pathname, source, base }) && pageUrlOf(nv.file, base)) {
			next.textContent = '次のページ ›'
			next.title = `次は ${shortFile(nv.file)}`
		}
		const pv = sorted[i - 1]
		if (pv && !onPage(pv, { pathname: location.pathname, source, base }) && pageUrlOf(pv.file, base)) {
			prev.textContent = '‹ 前のページ'
			prev.title = `前は ${shortFile(pv.file)}`
		}
		prev.addEventListener('click', () => step(-1))
		next.addEventListener('click', () => step(1))
		const count = el('span', 'count', `${i + 1} / ${sorted.length}`)
		count.title = `絞り込み後の全体での位置（このページは ${here.length} 件）`
		nav.append(prev, next, count)
		foot.append(act, err, nav)
		pop.replaceChildren(head, body, foot)
		panel.classList.add('has-pop')
		placePop()
	}

	// ---- 解決済みの印 ----

	async function resolve(v: ReviewView, note: string) {
		if (busy || Date.now() < guardUntil) return
		busy = true
		setBusy()
		const i = sorted.indexOf(v)
		const r = await o.call({ op: 'resolve', id: v.id, note })
		busy = false
		if (closed) return
		if (!r.ok) return failed(v, r.error)
		accept(r)
		popError = null
		// 解決済みを隠しているなら、次の指摘（無ければ前）へ進む。出しているならそのまま。
		const stay = filter.showResolved
		const follow = stay ? v.id : (sorted[i + 1] ?? sorted[i - 1])?.id ?? null
		rebuild()
		const left = sorted.filter((x) => x.status === 'open' && onPage(x, { pathname: location.pathname, source, base })).length
		toast(left ? '解決済みにしました' : '解決済みにしました。このページの未解決はもうありません', () => void reopen(v))
		guardUntil = Date.now() + GUARD_MS
		setTimeout(() => { if (!closed) for (const b of pop.querySelectorAll<HTMLButtonElement>('.act .btn')) b.disabled = busy }, GUARD_MS + 20)
		if (follow && sorted.some((x) => x.id === follow)) {
			const fv = sorted.find((x) => x.id === follow)!
			// 次が別のページなら移らずに閉じる（勝手にページを移らない）。
			if (onPage(fv, { pathname: location.pathname, source, base }) || !pageUrlOf(fv.file, base)) open(follow)
			else closePop()
		} else closePop()
	}

	async function reopen(v: ReviewView) {
		if (busy) return
		busy = true
		setBusy()
		const r = await o.call({ op: 'reopen', id: v.id })
		busy = false
		if (closed) return
		if (!r.ok) return failed(v, r.error)
		accept(r)
		popError = null
		rebuild()
		if (onPage(v, { pathname: location.pathname, source, base })) open(v.id, { scroll: false })
	}

	/**
	 * 保存に失敗した。ファイルが外で書き換わった（agent が消した・直した）かもしれないので読み直し、
	 * 指摘がまだあれば popup に理由を出す。無くなっていれば popup を閉じてトーストで知らせる。
	 */
	async function failed(v: ReviewView, error: string) {
		const message = `保存できません: ${error}`
		const l = await o.call({ op: 'load' })
		if (closed) return
		if (accept(l)) rebuild()
		if (sorted.some((x) => x.id === v.id) && activeId === v.id) {
			popError = message
			renderPop()
		} else {
			toast('この指摘は指摘のファイルから無くなっていました（agent が消したのかもしれません）。読み込み直しました。', undefined, true)
		}
	}

	/** 保存中はボタンを押せなくする（連打で 2 回送らない）。 */
	function setBusy() {
		for (const b of pop.querySelectorAll<HTMLButtonElement>('.act .btn')) b.disabled = true
	}

	function toast(message: string, undo?: () => void, warn = false) {
		const t = el('div', `toast${warn ? ' warn' : ''}`)
		t.style.pointerEvents = 'auto'
		t.style.display = 'flex'
		t.style.gap = '10px'
		t.style.alignItems = 'center'
		t.append(el('span', undefined, message))
		if (undo) {
			const b = el('button', 'btn', '戻す')
			b.type = 'button'
			b.addEventListener('click', () => { t.remove(); undo() })
			t.append(b)
		}
		toastBox.replaceChildren(t)
		// 知らせるだけのものは短く。戻せるもの・警告は読んで押せるだけ残す。
		setTimeout(() => t.remove(), undo || warn ? UNDO_MS : 2500)
	}

	// ---- 一覧のパネル ----

	let folded = (() => {
		const v = store.get(local(), FOLD_KEY)
		return v === null ? isNarrow() : v === '1'
	})()
	/** popup を開いたので一時的に畳んでいる（覚えない）。 */
	let autoFolded = false

	function row(v: ReviewView): HTMLElement {
		const b = el('button', `row${v.status === 'resolved' ? ' is-resolved' : ''}${v.id === activeId ? ' is-active' : ''}`)
		b.type = 'button'
		b.dataset.id = v.id
		b.dataset.tier = priorityTier(v.priority)
		b.append(el('span', 'chip', String(numberOf(v.id))), el('span', 'row-title', v.title))
		const t = targets.get(v.id)
		const meta = !v.line ? 'ページ' : v.place === 'lost' ? '見つからない' : v.place === 'no-file' ? 'ファイル無し' : !t && onPage(v, { pathname: location.pathname, source, base }) ? `${formatLine(v.line)}・本文の外` : formatLine(v.line)
		b.append(el('span', `row-meta${v.place === 'lost' || v.place === 'no-file' ? ' warn' : ''}`, meta))
		b.title = `${v.priority !== null ? `[p${v.priority}] ` : ''}${v.title}`
		b.addEventListener('click', () => open(v.id))
		// 一覧の行に乗せると、ページの上のその印と枠を目立たせる（どこへの指摘かを開く前に見せる）。
		const hover = (on: boolean) => {
			const m = marks.get(v.id)
			m?.pin.classList.toggle('is-hover', on)
			m?.box.classList.toggle('is-hover', on)
		}
		b.addEventListener('mouseenter', () => hover(true))
		b.addEventListener('mouseleave', () => hover(false))
		return b
	}

	function renderPanel() {
		const shut = folded || autoFolded
		panel.classList.toggle('is-folded', shut)
		panel.classList.toggle('has-pop', !pop.hidden)
		const head = el('header', 'panel-head')
		head.append(el('h2', undefined, '指摘'))
		if (loaded) head.append(el('span', 'n', String(here.length)))
		const sum = el('span', 'sum', loaded ? `このページ ${here.length} · 全体 ${sorted.length}` : loadError ? '読み込めません' : '読み込み中…')
		const fold = el('button', 'fold', shut ? '▸' : '▾')
		fold.type = 'button'
		fold.title = shut ? '一覧を開く' : '一覧を畳む'
		fold.setAttribute('aria-expanded', String(!shut))
		fold.addEventListener('click', () => {
			if (autoFolded) autoFolded = false
			else {
				folded = !folded
				store.set(local(), FOLD_KEY, folded ? '1' : '0')
			}
			renderPanel()
			schedule()
		})
		const x = el('button', 'x', '×')
		x.type = 'button'
		x.title = '指摘を隠す'
		x.setAttribute('aria-label', '指摘を隠す')
		x.addEventListener('click', () => o.onClose())
		head.append(sum, fold, x)
		if (shut) head.addEventListener('click', (e) => { if (e.target === head || e.target === sum) fold.click() })

		const body = el('div', 'panel-body')
		if (loadError && !loaded) {
			body.append(el('p', 'errors', `読み込めません: ${loadError}`))
			panel.replaceChildren(head, body)
			return
		}
		const filters = el('div', 'filters')
		const sel = el('select')
		sel.setAttribute('aria-label', '優先度で絞る')
		for (const p of PRIORITY_STEPS) {
			const opt = el('option', undefined, p === 0 ? 'すべての優先度' : `p${p} 以上`)
			opt.value = String(p)
			opt.selected = filter.minPriority === p
			sel.append(opt)
		}
		sel.addEventListener('change', () => setFilter({ ...filter, minPriority: Number(sel.value) }))
		const lab = el('label')
		const cb = el('input')
		cb.type = 'checkbox'
		cb.checked = filter.showResolved
		cb.addEventListener('change', () => setFilter({ ...filter, showResolved: cb.checked }))
		const resolvedCount = items.filter((v) => v.status === 'resolved').length
		lab.append(cb, document.createTextNode(`解決済みも出す（${resolvedCount}）`))
		filters.append(sel, lab)
		body.append(filters)

		body.append(el('div', 'sec', 'このページ'))
		if (here.length) {
			const ul = el('div', 'rows')
			for (const v of here) ul.append(row(v))
			body.append(ul)
		} else body.append(el('p', 'empty', items.length ? 'このページへの指摘はありません（絞り込みで隠れているものを除く）。' : '指摘がありません。置き場の review/ に指摘のファイルを置くと出ます。'))

		const others = groupByFile(sorted.filter((v) => !here.includes(v)), base)
		const pages = others.filter((g) => g.url)
		if (pages.length) {
			body.append(el('div', 'sec', 'ほかのページ'))
			const ul = el('div', 'rows')
			for (const g of pages) {
				const b = el('button', 'row')
				b.type = 'button'
				b.dataset.tier = priorityTier(Math.max(...sorted.filter((v) => v.file === g.file).map((v) => v.priority ?? -1)) || null)
				b.append(el('span', 'row-title', shortFile(g.file)), el('span', 'row-meta', `${g.count} 件`))
				b.title = `${g.file} を開いて、最初の指摘を出す`
				b.addEventListener('click', () => open(g.first.id))
				ul.append(b)
			}
			body.append(ul)
		}
		const loose = sorted.filter((v) => !here.includes(v) && !pageUrlOf(v.file, base))
		if (loose.length) {
			body.append(el('div', 'sec', 'ページの無いファイル'))
			const ul = el('div', 'rows')
			for (const v of loose) ul.append(row(v))
			body.append(ul)
		}
		if (sets.length) {
			const foot = el('div', 'sets')
			for (const st of sets) {
				const mine = items.filter((v) => v.set === st.name)
				const line = el('div', 'set')
				line.append(el('span', 'set-title', st.title), el('span', 'set-n', `未解決 ${mine.filter((v) => v.status === 'open').length} · 解決済み ${mine.filter((v) => v.status === 'resolved').length}`))
				line.title = `${st.title}\n${st.file}`
				foot.append(line)
			}
			body.append(foot)
		}
		const errors = sets.flatMap((s) => s.errors.map((e) => `${s.name}.md: ${e}`))
		if (loadError) errors.unshift(`読み込めません: ${loadError}`)
		if (errors.length) {
			// 読めない指摘は畳んでおく（指摘を書いた agent が直すもの。cli の review check でも出る）。
			const box = el('details', 'errors')
			box.append(el('summary', undefined, `読めない指摘・ファイル ${errors.length} 件`))
			const ul = el('ul')
			for (const e of errors) ul.append(el('li', undefined, e))
			box.append(ul)
			body.append(box)
		}
		panel.replaceChildren(head, body)
	}

	function setFilter(f: Filter) {
		filter = f
		store.set(local(), FILTER_KEY, JSON.stringify(f))
		rebuild()
	}

	// ---- イベント ----

	panel.append(el('header', 'panel-head', '指摘 読み込み中…'))
	renderPanel()

	// 自前の UI の中のキーはページへ渡さない（Starlight の検索のショートカットなど）。入力・ボタン操作はそのまま。
	// popup を開いているあいだ: Esc で popup を閉じる（アプリは閉じない）、← → で前・次（入力中は除く）。
	for (const type of ['keydown', 'keyup', 'keypress'] as const) {
		window.addEventListener(type, (e) => {
			if (inToolbar(e)) return
			const inside = e.composedPath().includes(host)
			if (type === 'keydown' && !pop.hidden && !e.isComposing) {
				if (e.key === 'Escape') {
					e.preventDefault()
					e.stopPropagation()
					swallowEscKeyup()
					closePop()
					return
				}
				const t = e.composedPath()[0]
				if ((e.key === 'ArrowRight' || e.key === 'ArrowLeft') && !e.altKey && !e.ctrlKey && !e.metaKey && !e.shiftKey && !isEditable(t)) {
					e.preventDefault()
					e.stopPropagation()
					step(e.key === 'ArrowRight' ? 1 : -1)
					return
				}
			}
			if (inside) runInsideHost(e, host)
		}, { capture: true, signal: abort.signal })
	}
	window.addEventListener('scroll', schedule, { capture: true, passive: true, signal: abort.signal })
	window.addEventListener('resize', () => { schedule() }, { signal: abort.signal })
	// 図（mermaid）の描画や画像の読み込みで位置がずれるので、ときどき置き直す。
	const timer = setInterval(schedule, 700)
	// 別のタブで agent が指摘を足した・消したときに追いつく。
	document.addEventListener('visibilitychange', () => {
		if (document.visibilityState !== 'visible' || busy) return
		void o.call({ op: 'load' }).then((r) => { if (!closed && accept(r)) rebuild() })
	}, { signal: abort.signal })
	document.addEventListener('astro:before-swap', () => o.onClose(), { signal: abort.signal })

	void load()

	return {
		reveal(id: string) {
			if (closed) return
			if (loaded) reveal(id)
			else store.set(session(), OPEN_KEY, id)
		},
		close() {
			if (closed) return
			closed = true
			clearInterval(timer)
			cancelAnimationFrame(raf)
			abort.abort()
			host.remove()
		},
	}
}
