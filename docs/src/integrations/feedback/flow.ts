// 「選んだものにフィードバックを書いて保存し、UI を片付ける」流れ。モードの中でも、モードの外の右クリックでも同じ流れを使う。
import { rectOfPick } from './anchor'
import { collect, type Pick } from './collect'
import { EVENT_RESULT, EVENT_SAVE, type SavePayload, type SaveResult, type SectionKey } from './protocol'
import { FeedbackUi } from './ui'

const STORAGE_KEY = 'katachi-feedback:checks'
const SAVE_TIMEOUT_MS = 10_000

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

export type Flow = ReturnType<typeof createFlow>

export function createFlow(app: AppLike, server: ServerLike) {
	let session: Session | null = null
	let pending: { requestId: string; timer: ReturnType<typeof setTimeout> } | null = null

	/** 画面に出したものを残らず消す。何度呼んでも安全。 */
	function cleanup() {
		if (pending) clearTimeout(pending.timer)
		pending = null
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
		const ui = new FeedbackUi({ onExit: exit, onOpen: openDialog, banner: !quick })
		session = { ui, abort: new AbortController(), pick: null, sawText: false, quick }
		return session
	}

	// サーバーからの結果。購読は外せないので1回だけ登録し、待っている送信のものだけ扱う。
	server.on<SaveResult>(EVENT_RESULT, (result) => {
		if (!pending || pending.requestId !== result.requestId || !session) return
		clearTimeout(pending.timer)
		pending = null
		if (result.ok) {
			exit()
			FeedbackUi.toast(`保存しました: ${result.file}`)
		} else {
			session.ui.showError(result.error)
		}
	})

	function submit(comment: string, checked: SavePayload['sections']) {
		if (!session) return
		const requestId = `${Date.now()}-${Math.random().toString(36).slice(2)}`
		const ui = session.ui
		ui.setBusy(true)
		pending = {
			requestId,
			timer: setTimeout(() => {
				pending = null
				ui.showError('サーバーから応答がありません（時間切れ）')
			}, SAVE_TIMEOUT_MS),
		}
		server.send<SavePayload>(EVENT_SAVE, { requestId, comment: comment.trim(), sections: checked })
	}

	function openDialog() {
		if (!session?.pick) return
		const { ui, pick } = session
		const sections = collect(pick)
		ui.showDialog(sections, loadChecks(), {
			onSubmit: (comment, checked) => {
				// チェックを外した節の記憶も残したいので、出ていた節の全体で記録する。
				const state: Partial<Record<SectionKey, boolean>> = { ...loadChecks() }
				for (const k of Object.keys(sections) as SectionKey[]) state[k] = k in checked
				saveChecks(state)
				submit(comment, checked)
			},
			onCancel: exit,
		}, () => rectOfPick(pick))
	}

	return { get session() { return session }, begin, cleanup, exit, openDialog }
}
