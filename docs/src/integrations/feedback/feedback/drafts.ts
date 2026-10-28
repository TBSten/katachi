// 書きかけのコメントとチェックの状態の下書き。sessionStorage に、ページの URL と選んだ場所を鍵にして持つ。
// popover を閉じても Esc で抜けても、同じ場所をもう一度開けば戻る。送信したら消す。タブを閉じれば消える。
import type { SectionKey } from '../core/protocol'
import type { Pick as Picked } from '../shared/pick'
import { xpathOf } from '../shared/locate'
import { containerOf } from '../shared/pick'

export type Draft = { comment: string; checks: Partial<Record<SectionKey, boolean>> }

const PREFIX = 'katachi-feedback:draft:'

/** 読み書きに使う入れ物（テストでは差し替える）。 */
export type Store = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>

const defaultStore = (): Store | null => {
	try {
		return sessionStorage
	} catch {
		return null
	}
}

/** 選んだ場所を表す文字列。要素は XPath、テキストは選んだ文字列と開始側ブロックの XPath、ページ全体は固定。 */
export function locatorOf(pick: Picked, target?: 'page' | 'site'): string {
	if (pick.kind === 'page') return `page:${target ?? 'page'}`
	if (pick.kind === 'elements') return `els:${pick.els.map((e) => xpathOf(e) ?? e.tagName).join('|')}`
	if (pick.kind === 'element') return `el:${xpathOf(pick.el) ?? pick.el.tagName}`
	return `text:${xpathOf(containerOf(pick)) ?? ''}:${pick.text.slice(0, 200)}`
}

/** URL は hash を除く（目次のクリックで hash が変わっても同じページとして扱う）。 */
export const draftKey = (url: string, locator: string) => `${PREFIX}${url.split('#')[0]}|${locator}`

export function loadDraft(key: string, store: Store | null = defaultStore()): Draft | null {
	try {
		const raw = store?.getItem(key)
		if (!raw) return null
		const v = JSON.parse(raw) as Partial<Draft>
		if (typeof v.comment !== 'string') return null
		const checks: Draft['checks'] = {}
		for (const [k, b] of Object.entries(v.checks ?? {})) if (typeof b === 'boolean') checks[k as SectionKey] = b
		return { comment: v.comment, checks }
	} catch {
		return null
	}
}

/** コメントが空で、チェックも既定のままなら消す（空の下書きを残さない）。 */
export function saveDraft(key: string, draft: Draft, store: Store | null = defaultStore()) {
	try {
		if (!store) return
		if (draft.comment.trim() === '') store.removeItem(key)
		else store.setItem(key, JSON.stringify(draft))
	} catch {
		// 覚えられなくても入力には影響しない（容量超過・プライベートウィンドウなど）。
	}
}

export function clearDraft(key: string, store: Store | null = defaultStore()) {
	try {
		store?.removeItem(key)
	} catch {
		// 同上。
	}
}
