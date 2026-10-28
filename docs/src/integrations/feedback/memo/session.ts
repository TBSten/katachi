// Memo を開いてから閉じるまでの 1 回ぶん: 読み込み → 画面 → 選ぶ → 自動保存 → 片付け。
// 閉じると、パネル・印・枠・リスナーを全部消す（保存が済んでいない分は閉じる前に送る）。
import { containerOf, type SinglePick } from '../shared/pick'
import type { Relocated } from '../shared/relocate'
import { el } from '../shared/dom'
import { createCard, type Card } from './cards'
import { createController, type MemoCall } from './controller'
import { createMarks } from './marks'
import { createNoteEditor, type NoteEditor } from './note-editor'
import { locatorOf, pageInfo } from './page'
import { startPicker } from './picker'
import { MemoUi } from './ui'
import { guardKeys, swallowEscKeyup } from '../shared/keys'

/** 光らせるあいだ（marks.ts の FLASH_MS）パネルを引っ込める。 */
const PEEK_MS = 1800

export type MemoSession = { close(): void }

export function openMemo(o: { call: MemoCall; onClose(): void }): MemoSession {
	const { page, pagePath } = pageInfo()
	const controller = createController({ call: o.call, page, pagePath })
	const abort = new AbortController()
	const cards = new Map<string, Card>()
	let pageEditor: NoteEditor | null = null
	let stopPicker: (() => void) | null = null
	let closed = false

	const ui = new MemoUi({ onClose: o.onClose, onPick: startPick, onCancelPick: cancelPick })
	const marks = createMarks(ui.root, focusCard)
	ui.body.append(el('div', 'loading', '読み込み中…'))

	controller.onStatus((s) => ui.setStatus(s))
	controller.onTemplate((t) => {
		pageEditor?.setTemplate(t)
		for (const c of cards.values()) c.editor.setTemplate(t)
	})

	/** 印と欄の番号・「見つからない」を今の箇所の一覧に合わせる。 */
	function sync(known?: Map<string, Relocated>) {
		const spots = controller.doc.spots
		const found = marks.set(spots, known)
		spots.forEach((s, i) => {
			const c = cards.get(s.id)
			c?.setNumber(i + 1)
			c?.setLost(!found.get(s.id))
		})
	}

	function addCard(spot: (typeof controller.doc.spots)[number]) {
		const card = createCard(spot, controller.template, {
			onChange: (note) => controller.setSpotNote(spot.id, note),
			onAddField: (n) => controller.addField(n),
			onRemoveField: (n) => controller.removeField(n),
			onReveal: () => {
				const s = controller.doc.spots.find((x) => x.id === spot.id)
				if (!s) return
				if (marks.reveal(s)) ui.peek(PEEK_MS)
				else card.setLost(true)
			},
			onDelete: () => {
				controller.removeSpot(spot.id)
				cards.get(spot.id)?.root.remove()
				cards.delete(spot.id)
				sync()
			},
		})
		cards.set(spot.id, card)
		ui.spotsBox.append(card.root)
		card.editor.refit()
		return card
	}

	function focusCard(id: string) {
		const c = cards.get(id)
		if (!c) return
		c.root.scrollIntoView({ block: 'nearest' })
		c.root.classList.add('is-active')
		setTimeout(() => c.root.classList.remove('is-active'), 1500)
	}

	function startPick() {
		if (stopPicker || closed) return
		ui.setPicking(true)
		stopPicker = startPicker({ host: ui.host, onHover: (r) => ui.setHover(r), onPick: onPicked })
	}
	function cancelPick() {
		stopPicker?.()
		stopPicker = null
		ui.setPicking(false)
	}
	function onPicked(pick: SinglePick, more = false) {
		if (!more) {
			stopPicker = null
			ui.setPicking(false)
		}
		const spot = controller.addSpot(locatorOf(pick))
		if (!spot) return
		// 選んだものそのものを、引き直しの結果として使う（保存した手がかりで引き直せなくても印は出す）。
		const here: Relocated = pick.kind === 'text'
			? { how: 'fragment', el: containerOf(pick), range: pick.range }
			: { how: 'xpath', el: pick.el }
		const card = addCard(spot)
		sync(new Map([[spot.id, here]]))
		// 足している途中はパネルが隠れているので、最後の 1 つのあとで欄に移る。
		if (more) return
		card.root.scrollIntoView({ block: 'nearest' })
		card.editor.textarea.focus({ preventScroll: true })
	}

	function render() {
		ui.body.replaceChildren()
		pageEditor = createNoteEditor(controller.doc.note, controller.template, {
			onChange: (note) => controller.setPageNote(note),
			onAddField: (n) => controller.addField(n),
			onRemoveField: (n) => controller.removeField(n),
		})
		pageEditor.root.classList.add('page-note')
		ui.body.append(el('div', 'section-title', 'ページのメモ'), pageEditor.root, ui.pickButton, ui.spotsBox)
		pageEditor.refit()
		for (const s of controller.doc.spots) addCard(s)
		sync()
	}

	void controller.load().then((r) => {
		if (closed) return
		if (r.ok) return render()
		ui.body.replaceChildren(el('div', 'blocked', r.message))
	})

	// キーはページに渡さない（Esc は下で扱う）。
	guardKeys({ host: ui.host, signal: abort.signal })
	// Esc: 選んでいる間は選ぶのをやめる。そうでなければ閉じる。入力中の IME の Esc と、field 名の入力のやめるは邪魔しない。
	// Astro の toolbar は Esc の keyup で「開いているアプリを閉じる（無ければ toolbar を隠す）」ので、
	// こちらで扱った Esc の keyup は toolbar に渡さない（選ぶのをやめただけでアプリが閉じたり、閉じた後に toolbar が隠れたりしないように）。
	document.addEventListener('keydown', (e) => {
		if (e.key !== 'Escape' || e.isComposing) return
		swallowEscKeyup()
		const t = e.composedPath()[0]
		if (t instanceof HTMLElement && t.dataset.esc === 'local') return
		e.preventDefault()
		e.stopPropagation()
		if (stopPicker) cancelPick()
		else o.onClose()
	}, { capture: true, signal: abort.signal })

	// ページ遷移に UI を持ち越さない。画面が隠れる・離れるときは、書きかけを先に送る。
	document.addEventListener('astro:before-swap', () => o.onClose(), { signal: abort.signal })
	window.addEventListener('pagehide', () => { controller.flush(); session.close() }, { signal: abort.signal })
	document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'hidden') controller.flush() }, { signal: abort.signal })

	const session: MemoSession = {
		close() {
			if (closed) return
			closed = true
			stopPicker?.()
			controller.flush()
			abort.abort()
			marks.destroy()
			ui.destroy()
		},
	}
	return session
}
