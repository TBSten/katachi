import { defineToolbarApp } from 'astro/toolbar'
import { rectOfPick } from './anchor'
import { collect, type Pick } from './collect'
import { EVENT_RESULT, EVENT_SAVE, type SavePayload, type SaveResult, type SectionKey } from './protocol'
import { FeedbackUi } from './ui'

const STORAGE_KEY = 'katachi-feedback:checks'
const SAVE_TIMEOUT_MS = 10_000
/** 長押しの選択は範囲が何度も変わる。この時間変わらなければ選び終えたとみなす。 */
const SELECTION_DEBOUNCE_MS = 200
/** この距離以内・この時間以内で離したタッチだけを「タップ」とみなす（それ以外はスクロールや長押し）。 */
const TAP_SLOP_PX = 10
const TAP_MAX_MS = 400

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
			const ui = new FeedbackUi({ onExit: exit, onOpen: openDialog })
			const abort = new AbortController()
			const s = { ui, abort, pick: null as Pick | null, sawText: false }
			session = s
			const opts = { capture: true, signal: abort.signal }
			// 自前の UI と Astro の toolbar 自身の操作は、選ぶ対象にしない。
			const isOwn = (e: Event) => e.composedPath().some((n) => n === ui.host || (n as Element).tagName === 'ASTRO-DEV-TOOLBAR')
			const targetOf = (e: Event) => (e.target instanceof Element ? e.target : null)
			let swallowClick = false
			let pointerType = 'mouse' // 直近に押された入力の種類。タッチかどうかで挙動を変える。
			let touchDown: { x: number; y: number; time: number } | null = null // タップ判定用。動いたり長く押したりしたらタップではない。
			let mouseDown = false // マウスのドラッグ中は選択が途中なので、selectionchange では確定しない。
			let selTimer: ReturnType<typeof setTimeout> | undefined
			abort.signal.addEventListener('abort', () => clearTimeout(selTimer))
			/** 枠と、選択のそばのボタンを今の選択に合わせて置き直す。 */
			const refreshPicked = () => {
				const p = s.pick
				ui.setPicked(p?.kind === 'element' ? p.el.getBoundingClientRect() : null)
				ui.setFab(p && !ui.hasDialog() ? rectOfPick(p) : null)
			}
			const clearPick = () => {
				s.pick = null
				refreshPicked()
				if (s.sawText) getSelection()?.removeAllRanges()
			}
			const pickText = () => {
				const sel = getSelection()
				const text = sel?.toString() ?? ''
				if (!sel || sel.rangeCount === 0 || text.trim() === '') return false
				s.pick = { kind: 'text', range: sel.getRangeAt(0).cloneRange(), text }
				s.sawText = true
				refreshPicked()
				return true
			}

			document.addEventListener('pointermove', (e) => {
				// タッチには hover が無いので枠を出さない。
				if (e.pointerType === 'touch') return
				const t = targetOf(e)
				ui.setHover(t && !isOwn(e) && !ui.hasDialog() ? t.getBoundingClientRect() : null)
			}, opts)
			document.addEventListener('pointerdown', (e) => {
				pointerType = e.pointerType
				mouseDown = e.pointerType === 'mouse' && e.button === 0
				touchDown = e.pointerType === 'touch' && !isOwn(e) ? { x: e.clientX, y: e.clientY, time: e.timeStamp } : null
				if (isOwn(e) || e.button !== 0) return
				swallowClick = false
				// マウスはメニューの外、タッチはボタンが出ているときの選択の外が「やめる」。
				// どちらもこの1回で別の要素を選び直しはしない。長押しのように click が来ない操作でも、次の押下で解除される。
				const dismiss = e.pointerType === 'touch' ? s.pick !== null && !ui.hasDialog() : ui.hasMenu()
				if (dismiss) {
					ui.hideMenu()
					clearPick()
					swallowClick = true
				}
			}, opts)
			// iOS Safari は、click ハンドラを持たない要素（段落など）のタップでは click を出さない。
			// document の capture リスナーは数えられないので、タッチのタップは pointerup で自分で判定して要素を選ぶ。
			document.addEventListener('pointerup', (e) => {
				mouseDown = false
				const down = touchDown
				touchDown = null
				if (e.pointerType !== 'touch' || !down || isOwn(e)) return
				const isTap = Math.hypot(e.clientX - down.x, e.clientY - down.y) < TAP_SLOP_PX && e.timeStamp - down.time < TAP_MAX_MS
				if (swallowClick) { swallowClick = false; return }
				const t = targetOf(e)
				if (!isTap || !t) return
				if (s.sawText) getSelection()?.removeAllRanges()
				s.pick = { kind: 'element', el: t }
				refreshPicked()
			}, opts)
			document.addEventListener('pointercancel', () => { mouseDown = false; touchDown = null }, opts)
			document.addEventListener('mouseup', (e) => {
				if (isOwn(e) || e.button !== 0 || swallowClick) return
				pickText()
				if (s.pick?.kind === 'text') ui.setPicked(null)
			}, opts)
			// スマホの長押し選択にはマウスのドラッグが無い。選択範囲が変わったら、落ち着いてからテキストを選んだことにする。
			document.addEventListener('selectionchange', () => {
				clearTimeout(selTimer)
				selTimer = setTimeout(() => {
					if (mouseDown || ui.hasDialog()) return
					pickText()
				}, SELECTION_DEBOUNCE_MS)
			}, { signal: abort.signal })
			const onClick = (e: MouseEvent) => {
				if (isOwn(e)) return
				// リンクやボタンを動かさない（タップも click になるので、これでスマホでも動かない）。
				e.preventDefault()
				e.stopPropagation()
				// タッチの要素選択は pointerup で済んでいる。ここはリンクを動かさないためだけに通る。
				if (pointerType === 'touch') return
				if (swallowClick) { swallowClick = false; return }
				// 長押し・ドラッグでテキストを選んだ直後の click は、要素の選択で上書きしない。
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
				// タッチの長押しでも contextmenu が来る端末がある。そのときは自前メニューを出さず、そばのボタンに任せる。
				if (pointerType === 'touch') return
				ui.setHover(null)
				ui.showMenu(e.clientX, e.clientY, openDialog)
			}, opts)
			document.addEventListener('keydown', (e) => {
				if (e.key === 'Escape') { e.preventDefault(); exit() }
			}, opts)
			// 選んだ枠とボタンが、スクロールや画面サイズの変更に追従するように。
			window.addEventListener('scroll', refreshPicked, opts)
			window.addEventListener('resize', refreshPicked, opts)
			// ページ遷移（View Transitions を使うサイトでも）に UI を持ち越さない。
			document.addEventListener('astro:before-swap', exit, opts)
			window.addEventListener('pagehide', cleanup, opts)
		}

		app.onToggled(({ state }) => (state ? enter() : cleanup()))
	},
})
