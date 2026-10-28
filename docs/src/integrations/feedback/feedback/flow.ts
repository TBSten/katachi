// 「選んだものにフィードバックを書いて保存し、UI を片付ける」流れ。モードの中でも、モードの外の右クリックでも同じ流れを使う。
import { rectOfPick } from '../shared/anchor'
import { collect, type Pick } from './collect'
import { EVENT_RESULT, EVENT_SAVE, type SavePayload, type SaveResult, type SectionKey } from '../core/protocol'
import { containerOf } from '../shared/pick'
import { createRpc, type Call } from '../shared/rpc'
import { captureShots } from './capture'
import { copyText, copyToClipboard } from './copy'
import { clearDraft, draftKey, loadDraft, locatorOf, saveDraft, type Draft } from './drafts'
import { buildPayload } from './payload'
import { FeedbackUi } from './ui'

const STORAGE_KEY = 'katachi-feedback:checks'

// チェックの状態は覚えておくが、読み書きできなくても動くようにする（プライベートウィンドウなど）。
function loadChecks(): Partial<Record<SectionKey, boolean>> {
	try {
		return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}')
	} catch {
		return {}
	}
}
function saveChecks(checks: Partial<Record<SectionKey, boolean>>) {
	try {
		localStorage.setItem(STORAGE_KEY, JSON.stringify(checks))
	} catch {
		// 覚えられなくても送信には影響しない。
	}
}

/** toolbar アプリのうち、この流れが使う部分。 */
export type AppLike = { toggleState(o: { state: boolean }): void }
export type ServerLike = {
	on<T>(event: string, cb: (data: T) => void): void
	send<T>(event: string, data: T): void
}

/**
 * 画面に出ているものの1回ぶん。null なら何も出ていない。
 * quick が true なら、モードの外で右クリックから開いたもの（toolbar アプリの on/off は触らない）。
 */
export type Session = { ui: FeedbackUi; abort: AbortController; pick: Pick | null; sawText: boolean; quick: boolean }

/** 「親を選ぶ」の行き先。選んだものを含む、ひとつ外側の要素（body・html までは広げない）。 */
export function parentOf(pick: Pick): Element | null {
	if (pick.kind === 'page' || pick.kind === 'elements') return null
	// 要素なら親、テキストならそれを含む最も内側の要素（テキストの「親」）。
	const next = pick.kind === 'element' ? pick.el.parentElement : containerOf(pick)
	return next && next !== document.body && next !== document.documentElement ? next : null
}

export type Flow = ReturnType<typeof createFlow>

export function createFlow(app: AppLike, server: ServerLike) {
	let session: Session | null = null
	// 保存の依頼と結果を requestId で対応づける（時間切れもここで決着する）。
	const rpc = createRpc<SavePayload, SaveResult>(server, EVENT_SAVE, EVENT_RESULT)
	let pending: Call<SaveResult> | null = null
	/** 開いている入力欄の下書きの鍵。 */
	let draftKeyNow: string | null = null

	/** 画面に出したものを残らず消す。何度呼んでも安全。 */
	function cleanup() {
		pending?.cancel()
		pending = null
		draftKeyNow = null
		if (!session) return
		const s = session
		session = null
		s.abort.abort() // 全イベントリスナーを一括で外す（AbortSignal を渡して登録している）
		s.ui.destroy() // 枠・メニュー・案内・入力欄は shadow host ごと消える
		if (s.sawText) getSelection()?.removeAllRanges()
	}
	/** やめる。モードなら toolbar のアプリも閉じた状態に戻す（モードの外から開いたものは、触っていないので戻さない）。 */
	function exit() {
		const quick = session?.quick ?? false
		cleanup()
		if (!quick) app.toggleState({ state: false })
	}

	/** 新しい1回ぶんを始める。すでに出ていれば null。 */
	function begin(quick: boolean): Session | null {
		if (session) return null
		const ui = new FeedbackUi({ onExit: exit, onOpen: openDialog, onOpenPage: openPage, banner: !quick })
		session = { ui, abort: new AbortController(), pick: null, sawText: false, quick }
		return session
	}

	async function submit(comment: string, checked: SavePayload['sections'], offered: SavePayload['sections'], target?: SavePayload['target']) {
		const s = session
		if (!s) return
		const key = draftKeyNow
		s.ui.setBusy(true, checked.screenshot ? '撮影中…' : undefined)
		const payload = buildPayload({ requestId: '', comment, pick: s.pick!, offered, checked, target })
		if (checked.screenshot) {
			const shots = await captureShots(s.pick!)
			if (session !== s) return // 撮っている間にやめた
			if (shots.attachments.length) payload.attachments = shots.attachments
			if (shots.error) payload.screenshotError = shots.error
		}
		const call = rpc.call(payload)
		pending = call
		const result = await call.promise
		if (pending !== call || session !== s) return
		pending = null
		if (result.ok) {
			if (key) clearDraft(key)
			exit()
			FeedbackUi.toast(`保存しました: ${result.file}`)
		} else {
			s.ui.showError(result.error)
		}
	}

	function openDialog() {
		show()
	}

	/** 入力欄を出す。initial があれば（「親を選ぶ」で開き直すとき）それを使い、無ければ下書きを探す。 */
	function show(initial?: Draft | null) {
		if (!session?.pick) return
		const { ui, pick } = session
		const sections = collect(pick)
		const key = draftKey(location.href, locatorOf(pick))
		draftKeyNow = key
		ui.showDialog(sections, loadChecks(), {
			onSubmit: (comment, checked, target) => {
				// チェックを外した節の記憶も残したいので、出ていた節の全体で記録する。
				const state: Partial<Record<SectionKey, boolean>> = { ...loadChecks() }
				for (const k of Object.keys(sections) as SectionKey[]) state[k] = k in checked
				saveChecks(state)
				void submit(comment, checked, sections, target)
			},
			onCopy: (comment, checked, target) =>
				copyToClipboard(copyText(buildPayload({ requestId: 'copy', comment, pick, offered: sections, checked, target }))),
			onCancel: exit,
			onDraft: (draft) => saveDraft(key, draft),
			onParent: reparent,
		}, () => (pick.kind === 'page' ? ui.bannerRect() : rectOfPick(pick)), pick.kind === 'page', { initial: initial ?? loadDraft(key), canParent: parentOf(pick) !== null })
	}

	/**
	 * 選んだものを next に替えて、入力欄を開き直す。書きかけのコメントとチェックは引き継ぐ。
	 * 「親を選ぶ」と、入力欄を開いたままの Shift+クリック（要素を足す・外す）が使う。next が null なら入力欄を閉じる。
	 */
	function replacePick(next: Pick | null) {
		const s = session
		if (!s) return
		const carried = s.ui.dialogDraft()
		// 引き継ぐので、元の場所の下書きは残さない（新しい場所の下書きとして保存し直される）。
		if (draftKeyNow && s.ui.hasDialog()) clearDraft(draftKeyNow)
		if (s.sawText) getSelection()?.removeAllRanges()
		s.pick = next
		s.ui.closeDialog()
		if (!next) return
		show(carried)
		if (carried) saveDraft(draftKeyNow!, carried)
	}

	/** 選んだものをひとつ外側の要素に広げる。 */
	function reparent() {
		const next = session?.pick ? parentOf(session.pick) : null
		if (!session || !next) return
		session.ui.setPicked(next.getBoundingClientRect())
		replacePick({ kind: 'element', el: next })
	}

	/** モードが session.pick を書き換えたあと（Shift+クリック）、開いている入力欄を新しい選択で開き直す。 */
	function repick() {
		if (session) replacePick(session.pick)
	}

	/** 何も選ばずに、ページ全体・サイト全体へのフィードバックを書く（案内の帯のボタンから）。 */
	function openPage() {
		if (!session || session.ui.hasDialog()) return
		session.pick = { kind: 'page' }
		session.ui.setPicked(null)
		session.ui.setFab(null)
		if (session.sawText) getSelection()?.removeAllRanges()
		openDialog()
	}

	return { get session() { return session }, begin, cleanup, exit, openDialog, openPage, repick }
}
