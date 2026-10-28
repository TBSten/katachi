// メモの状態（ページのメモ・箇所のメモ・field のひな形）と、自動保存。DOM には触らない（画面は app.ts がつなぐ）。
// 入力が止まって debounceMs たったら保存する。保存中の変更は、終わったあとにもう 1 回保存する。
import type { MemoRequest, MemoResult } from '../core/protocol'
import type { Call } from '../shared/rpc'
import { parseMarkdown, toMarkdown } from './markdown'
import { addField, emptyNote, isNoteEmpty, MEMO_LIMITS, removeField, sameNoteContent, sanitizeFields, type Locator, type MemoDoc, type MemoPage, type Note, type Spot } from './model'

/** 保存の状態。画面の端に小さく出す。 */
export type SaveStatus =
	| { kind: 'loading' }
	| { kind: 'idle' }
	| { kind: 'dirty' }
	| { kind: 'saving' }
	| { kind: 'saved'; at: Date }
	| { kind: 'error'; message: string }

type DistributiveOmit<T, K extends keyof never> = T extends unknown ? Omit<T, K> : never
export type MemoCall = (req: DistributiveOmit<MemoRequest, 'requestId'>) => Call<MemoResult>

export type ControllerDeps = {
	call: MemoCall
	page: MemoPage
	/** サーバーへ送るページのパス（base と言語を除いたもの）。 */
	pagePath: string
	debounceMs?: number
	/** 箇所の id。テストで固定するため差し替えられる。 */
	newId?: () => string
	now?: () => Date
}

export type LoadOutcome =
	| { ok: true; existed: boolean }
	/** 既存のファイルが読み直せない、または読み込みに失敗した。上書きしないよう保存を止める。 */
	| { ok: false; message: string }

export function createController(deps: ControllerDeps) {
	const debounceMs = deps.debounceMs ?? 500
	const newId = deps.newId ?? (() => Math.random().toString(36).slice(2, 10))
	const now = deps.now ?? (() => new Date())
	let template: string[] = []
	let doc: MemoDoc = { page: deps.page, note: emptyNote(), spots: [] }
	let existed = false
	let blocked: string | null = null
	let dirty = false
	let saving = false
	let timer: ReturnType<typeof setTimeout> | undefined
	let status: SaveStatus = { kind: 'loading' }
	const statusListeners = new Set<(s: SaveStatus) => void>()
	const templateListeners = new Set<(t: readonly string[]) => void>()

	const setStatus = (s: SaveStatus) => {
		status = s
		for (const l of statusListeners) l(s)
	}

	async function run() {
		if (blocked || saving || !dirty) return
		saving = true
		try {
			while (dirty) {
				dirty = false
				// 何も書いていないページに、空のファイルは作らない（全部消したあとでも、元から無ければ作らない）。ひな形だけは書く。
				const skipDoc = !existed && isNoteEmpty(doc.note) && doc.spots.length === 0
				setStatus({ kind: 'saving' })
				const res = await deps.call({ op: 'save', lang: deps.page.lang, pagePath: deps.pagePath, text: skipDoc ? null : toMarkdown(doc, now()), fields: template }).promise
				if (res.ok) {
					if (!skipDoc) existed = true
					if (!dirty) setStatus({ kind: 'saved', at: now() })
				} else {
					dirty = true
					setStatus({ kind: 'error', message: res.error })
					break
				}
			}
		} finally {
			saving = false
		}
	}

	function touch() {
		if (blocked) return
		dirty = true
		setStatus({ kind: 'dirty' })
		clearTimeout(timer)
		timer = setTimeout(() => void run(), debounceMs)
	}

	function setTemplate(next: string[]) {
		template = next
		for (const l of templateListeners) l(template)
	}

	return {
		get doc() { return doc },
		get template(): readonly string[] { return template },
		get status() { return status },
		get blocked() { return blocked },
		onStatus(l: (s: SaveStatus) => void) { statusListeners.add(l); return () => statusListeners.delete(l) },
		onTemplate(l: (t: readonly string[]) => void) { templateListeners.add(l); return () => templateListeners.delete(l) },

		/** サーバーから読み込む。失敗しても投げず、理由を返す（そのあいだは保存しない）。 */
		async load(): Promise<LoadOutcome> {
			setStatus({ kind: 'loading' })
			const res = await deps.call({ op: 'load', lang: deps.page.lang, pagePath: deps.pagePath }).promise
			if (!res.ok || res.op !== 'load') {
				blocked = res.ok ? '読み込みの応答が不正です' : res.error
				setStatus({ kind: 'error', message: `読み込めませんでした: ${blocked}` })
				return { ok: false, message: blocked }
			}
			setTemplate(sanitizeFields(res.fields))
			if (res.text !== null) {
				const parsed = parseMarkdown(res.text)
				if (!parsed) {
					blocked = '既存のメモのファイルを読み直せません。上書きしないよう保存を止めています（ファイルを確かめてください）'
					setStatus({ kind: 'error', message: blocked })
					return { ok: false, message: blocked }
				}
				doc = { page: deps.page, note: parsed.note, spots: parsed.spots }
				existed = true
			}
			setStatus({ kind: 'idle' })
			return { ok: true, existed }
		},

		// 中身が変わったときだけ最終更新時刻を進める（同じ値の入力やフォーカスの出入りでは進めない）。
		setPageNote(note: Note) {
			if (sameNoteContent(doc.note, note)) return
			doc = { ...doc, note: { values: note.values, text: note.text, updatedAt: now().toISOString() } }
			touch()
		},
		setSpotNote(id: string, note: Note) {
			const cur = doc.spots.find((s) => s.id === id)
			if (!cur || sameNoteContent(cur, note)) return
			const updatedAt = now().toISOString()
			doc = { ...doc, spots: doc.spots.map((s) => (s.id === id ? { ...s, values: note.values, text: note.text, updatedAt } : s)) }
			touch()
		},
		/** 箇所を足す。上限なら null。 */
		addSpot(locator: Locator): Spot | null {
			if (doc.spots.length >= MEMO_LIMITS.spots) return null
			const at = now().toISOString()
			const spot: Spot = { id: newId(), locator, ...emptyNote(), createdAt: at, updatedAt: at }
			doc = { ...doc, spots: [...doc.spots, spot] }
			touch()
			return spot
		},
		/** 箇所を消す。すぐ保存する（消したものがファイルに残らないように）。 */
		removeSpot(id: string) {
			doc = { ...doc, spots: doc.spots.filter((s) => s.id !== id) }
			touch()
			this.flush()
		},
		/** field を全ページ共通のひな形に足す。 */
		addField(name: string) {
			const next = addField(template, name)
			if (next.length === template.length) return
			setTemplate(next)
			touch()
		},
		/** ひな形から外す。値は消さない。 */
		removeField(name: string) {
			setTemplate(removeField(template, name))
			touch()
		},
		/** 待たずにすぐ保存する（閉じるとき・箇所を消したとき）。 */
		flush() {
			clearTimeout(timer)
			void run()
		},
	}
}

export type Controller = ReturnType<typeof createController>
