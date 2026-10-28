// Memo の toolbar アプリ。押すとパネルが開き、もう一度押す・×・Esc で閉じる（UI は全部消える）。
import { defineToolbarApp } from 'astro/toolbar'
import { EVENT_MEMO_REQUEST, EVENT_MEMO_RESULT, type MemoResult } from '../core/protocol'
import { createRpc } from '../shared/rpc'
import type { MemoCall } from './controller'
import { openMemo, type MemoSession } from './session'

export default defineToolbarApp({
	init(_canvas, app, server) {
		const rpc = createRpc<Parameters<MemoCall>[0], MemoResult>(server, EVENT_MEMO_REQUEST, EVENT_MEMO_RESULT)
		let session: MemoSession | null = null
		const close = () => {
			session?.close()
			session = null
		}
		app.onToggled(({ state }) => {
			if (!state) return close()
			if (session) return
			session = openMemo({
				call: (req) => rpc.call(req),
				// パネルの × や Esc から閉じたときは、toolbar のアプリも閉じた状態に戻す。
				onClose: () => {
					close()
					app.toggleState({ state: false })
				},
			})
		})
	},
})
