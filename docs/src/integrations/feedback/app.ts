import { defineToolbarApp } from 'astro/toolbar'
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

export default defineToolbarApp({
	init(_canvas, app, server) {
		// 実行中のモード1回ぶんの状態。null なら何も画面に出ていない。
		let session: { ui: FeedbackUi; abort: AbortController; pick: Pick | null; sawText: boolean } | null = null
		let pending: { requestId: string; timer: ReturnType<typeof setTimeout> } | null = null

		/** 画面に出したものを残らず消す。何度呼んでも安全。 */
		function cleanup() {
			if (pending) clearTimeout(pending.timer)
			pending = null
			if (!session) return
			const s = session
			session = null
			s.abort.abort() // 全イベントリスナーを一括で外す（AbortSignal を渡して登録している）
			s.ui.destroy() // 枠・メニュー・案内・ダイアログは shadow host ごと消える
			if (s.sawText) getSelection()?.removeAllRanges()
		}
		/** モードをやめ、toolbar のアプリも閉じた状態に戻す。 */
		function exit() {
			cleanup()
			app.toggleState({ state: false })
		}

		// サーバーからの結果。購読は外せないので init で1回だけ登録し、待っている送信のものだけ扱う。
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
			const { ui } = session
			const sections = collect(session.pick)
			ui.showDialog(sections, loadChecks(), {
				onSubmit: (comment, checked) => {
					// チェックを外した節の記憶も残したいので、出ていた節の全体で記録する。
					const state: Partial<Record<SectionKey, boolean>> = { ...loadChecks() }
					for (const k of Object.keys(sections) as SectionKey[]) state[k] = k in checked
					saveChecks(state)
					submit(comment, checked)
				},
				onCancel: exit,
			})
		}

		function enter() {
			if (session) return
			const ui = new FeedbackUi()
			const abort = new AbortController()
			const s = { ui, abort, pick: null as Pick | null, sawText: false }
			session = s
			const opts = { capture: true, signal: abort.signal }
			// 自前の UI と Astro の toolbar 自身の操作は、選ぶ対象にしない。
			const isOwn = (e: Event) => e.composedPath().some((n) => n === ui.host || (n as Element).tagName === 'ASTRO-DEV-TOOLBAR')
			const targetOf = (e: Event) => (e.target instanceof Element ? e.target : null)
			let swallowClick = false
			const refreshPicked = () => ui.setPicked(s.pick?.kind === 'element' ? s.pick.el.getBoundingClientRect() : null)
			const clearPick = () => {
				s.pick = null
				ui.setPicked(null)
				if (s.sawText) getSelection()?.removeAllRanges()
			}

			document.addEventListener('mousemove', (e) => {
				const t = targetOf(e)
				ui.setHover(t && !isOwn(e) && !ui.hasDialog() ? t.getBoundingClientRect() : null)
			}, opts)
			document.addEventListener('mousedown', (e) => {
				if (isOwn(e) || e.button !== 0) return
				if (ui.hasMenu()) {
					// メニューの外のクリックは「やめる」。この1回で別の要素を選び直しはしない。
					ui.hideMenu()
					clearPick()
					swallowClick = true
				}
			}, opts)
			document.addEventListener('mouseup', (e) => {
				if (isOwn(e) || e.button !== 0 || swallowClick) return
				const sel = getSelection()
				const text = sel?.toString() ?? ''
				if (sel && sel.rangeCount > 0 && text.trim() !== '') {
					s.pick = { kind: 'text', range: sel.getRangeAt(0).cloneRange(), text }
					s.sawText = true
					ui.setPicked(null)
				}
			}, opts)
			const onClick = (e: MouseEvent) => {
				if (isOwn(e)) return
				// リンクやボタンを動かさない。
				e.preventDefault()
				e.stopPropagation()
				if (swallowClick) { swallowClick = false; return }
				// ドラッグでテキストを選んだ直後の click は、選んだことにしない（mouseup で決めた text を残す）。
				if (e.type === 'click' && s.pick?.kind === 'text' && (getSelection()?.toString().trim() ?? '') !== '') return
				const t = targetOf(e)
				if (!t || e.type !== 'click') return
				if (s.sawText) getSelection()?.removeAllRanges()
				s.pick = { kind: 'element', el: t }
				refreshPicked()
			}
			document.addEventListener('click', onClick, opts)
			document.addEventListener('auxclick', onClick, opts)
			document.addEventListener('contextmenu', (e) => {
				if (isOwn(e) || ui.hasDialog()) return
				// 何も選んでいないときはブラウザ標準のメニューのまま。
				if (!s.pick) return
				e.preventDefault()
				e.stopPropagation()
				ui.setHover(null)
				ui.showMenu(e.clientX, e.clientY, openDialog)
			}, opts)
			document.addEventListener('keydown', (e) => {
				if (e.key === 'Escape') { e.preventDefault(); exit() }
			}, opts)
			// 選んだ枠が、スクロールや画面サイズの変更に追従するように。
			window.addEventListener('scroll', refreshPicked, opts)
			window.addEventListener('resize', refreshPicked, opts)
			// ページ遷移（View Transitions を使うサイトでも）に UI を持ち越さない。
			document.addEventListener('astro:before-swap', exit, opts)
			window.addEventListener('pagehide', cleanup, opts)
		}

		app.onToggled(({ state }) => (state ? enter() : cleanup()))
	},
})
