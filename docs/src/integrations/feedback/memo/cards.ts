// 箇所ごとのメモの欄: 場所の見出し・note の入力欄・場所を表示・削除。
import { el } from '../shared/dom'
import { spotTitle } from './markdown'
import type { FieldDef } from './fields'
import type { Spot } from './model'
import { createNoteEditor, type NoteEditor, type NoteEditorHandlers } from './note-editor'

export type CardHandlers = NoteEditorHandlers & {
	onReveal(): void
	onDelete(): void
}

export type Card = { root: HTMLElement; editor: NoteEditor; setLost(lost: boolean): void; setNumber(n: number): void }

/** 場所の見出し（近くの見出し・ソースの行・本文の抜粋）。 */
function whereLines(spot: Spot): HTMLElement {
	const l = spot.locator
	const box = el('div', 'where')
	box.append(el('strong', 'where-title', spotTitle(l)))
	if (l.source) box.append(el('span', 'where-src', l.source))
	if (l.excerpt && l.heading) box.append(el('span', 'where-quote', `「${l.excerpt}」`))
	return box
}

export function createCard(spot: Spot, template: readonly FieldDef[], h: CardHandlers): Card {
	const root = el('article', 'card')
	root.dataset.spot = spot.id
	const num = el('span', 'num')
	const lost = el('span', 'lost', '見つからない')
	lost.hidden = true
	const head = el('div', 'card-head')
	head.append(num, whereLines(spot), lost)
	const editor = createNoteEditor(spot, template, h)
	const foot = el('div', 'card-foot')
	const reveal = el('button', 'reveal', '場所を表示')
	reveal.type = 'button'
	reveal.addEventListener('click', h.onReveal)
	const del = el('button', 'delete', '削除')
	del.type = 'button'
	del.addEventListener('click', h.onDelete)
	foot.append(reveal, del)
	root.append(head, editor.root, foot)
	return {
		root,
		editor,
		setLost(isLost) {
			lost.hidden = !isLost
			reveal.disabled = isLost
			root.classList.toggle('is-lost', isLost)
		},
		setNumber(n) { num.textContent = String(n) },
	}
}
