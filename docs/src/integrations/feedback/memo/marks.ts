// ページに出す番号の印と、「場所を表示」の光る枠（ブラウザ側）。UI の shadow root の中に作るので、host を消せば全部消える。
import { el } from '../shared/dom'
import { relocate, rectOfRelocated, type Place, type Relocated } from '../shared/relocate'
import type { Spot } from './model'

export const placeOf = (s: Spot): Place => ({
	kind: s.locator.kind,
	xpath: s.locator.xpath,
	selector: s.locator.selector,
	fragment: s.locator.fragment,
	source: s.locator.source,
})

/** 光らせている時間。 */
const FLASH_MS = 1800

export function createMarks(root: ShadowRoot, onMarkClick: (id: string) => void) {
	const layer = el('div', 'marks')
	// パネルより後ろに置く（シートや popover の下に隠れる箇所の印が、パネルの上に重ならないように）。
	root.prepend(layer)
	/** 箇所 id → 引き直した場所（見つからなければ null）。 */
	const found = new Map<string, Relocated | null>()
	const dots = new Map<string, HTMLButtonElement>()
	let order: Spot[] = []
	let raf = 0

	function dotFor(id: string): HTMLButtonElement {
		let d = dots.get(id)
		if (!d) {
			d = el('button', 'mark')
			d.type = 'button'
			d.dataset.spot = id
			d.addEventListener('click', (e) => { e.stopPropagation(); onMarkClick(id) })
			layer.append(d)
			dots.set(id, d)
		}
		return d
	}

	/** 今の位置に印を置き直す。消えた要素（DOM が差し替わった）は引き直す。 */
	function refresh() {
		order.forEach((s, i) => {
			let r = found.get(s.id) ?? null
			if (!r || !r.el.isConnected) {
				r = relocate(placeOf(s))
				found.set(s.id, r)
			}
			const d = dotFor(s.id)
			if (!r) return void (d.style.display = 'none')
			const rect = rectOfRelocated(r)
			const visible = rect.bottom >= 0 && rect.top <= innerHeight && rect.right >= 0 && rect.left <= innerWidth
			d.textContent = String(i + 1)
			d.style.display = visible ? 'flex' : 'none'
			d.style.left = `${Math.max(2, rect.left - 6)}px`
			d.style.top = `${Math.max(2, rect.top - 6)}px`
		})
	}
	const schedule = () => { cancelAnimationFrame(raf); raf = requestAnimationFrame(refresh) }
	const ac = new AbortController()
	window.addEventListener('scroll', schedule, { capture: true, passive: true, signal: ac.signal })
	window.addEventListener('resize', schedule, { signal: ac.signal })

	return {
		/** 箇所の一覧が変わった。引き直して印を置く。known があればそれを使う（選んだ直後は、選んだものそのもの）。 */
		set(spots: Spot[], known: Map<string, Relocated> = new Map()): Map<string, Relocated | null> {
			order = spots
			for (const id of [...dots.keys()]) if (!spots.some((s) => s.id === id)) { dots.get(id)!.remove(); dots.delete(id); found.delete(id) }
			for (const s of spots) if (!found.has(s.id) || !found.get(s.id)) found.set(s.id, known.get(s.id) ?? relocate(placeOf(s)))
			refresh()
			return new Map(found)
		},
		found: (id: string) => found.get(id) ?? null,
		refresh,
		/** その箇所までスクロールして光らせる。見つからなければ false。 */
		reveal(spot: Spot): boolean {
			const r = relocate(placeOf(spot)) ?? found.get(spot.id) ?? null
			if (!r) return false
			found.set(spot.id, r)
			r.el.scrollIntoView({ block: 'center', inline: 'nearest' })
			const frame = el('div', 'flash')
			layer.append(frame)
			const place = () => {
				const rect = rectOfRelocated(r)
				Object.assign(frame.style, { left: `${rect.left - 3}px`, top: `${rect.top - 3}px`, width: `${rect.width + 6}px`, height: `${rect.height + 6}px` })
			}
			place()
			window.addEventListener('scroll', place, { capture: true, passive: true, signal: ac.signal })
			setTimeout(() => frame.remove(), FLASH_MS)
			schedule()
			return true
		},
		destroy() {
			ac.abort()
			cancelAnimationFrame(raf)
			layer.remove()
		},
	}
}

export type Marks = ReturnType<typeof createMarks>
