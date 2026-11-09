// Review の toolbar アプリ。押すと、今のページへの指摘を印と枠で出し、印を押すと popup で読める。もう一度押す・× で隠す。
// 既定は隠したまま。出しているあいだはタブの中で覚えておき（sessionStorage）、ページを移っても出し続ける。
// astro/toolbar の defineToolbarApp は使わず、型だけを astro から取る（理由は server/optimize-deps.ts）。
import type { DevToolbarApp } from 'astro'
import { EVENT_REVIEW_REQUEST, EVENT_REVIEW_RESULT, type ReviewRequest, type ReviewResult } from '../core/protocol'
import { createRpc } from '../shared/rpc'
import { pageSourceFile } from '../shared/srcmap'
import { onPage } from './order'
import { OPEN_KEY, openReview, type ReviewSession } from './session'

/** 出しているあいだ立てておく印（sessionStorage）。 */
export const ON_KEY = 'katachi-review:on'

const remember = (on: boolean) => {
	try {
		if (on) sessionStorage.setItem(ON_KEY, '1')
		else {
			sessionStorage.removeItem(ON_KEY)
			sessionStorage.removeItem(OPEN_KEY)
		}
	} catch {
		// 覚えられなくても動く（ページを移ると隠れるだけ）。
	}
}
const remembered = () => {
	try {
		return sessionStorage.getItem(ON_KEY) === '1'
	} catch {
		return false
	}
}

type Req = ReviewRequest extends infer R ? (R extends { requestId: string } ? Omit<R, 'requestId'> : never) : never

export default {
	init(_canvas, app, server) {
		const rpc = createRpc<Req, ReviewResult>(server, EVENT_REVIEW_REQUEST, EVENT_REVIEW_RESULT)
		let session: ReviewSession | null = null
		/** 隠しているあいだも、このページに未解決の指摘があれば toolbar のボタンに小さな点を出す。 */
		const hint = async () => {
			const r = await rpc.call({ op: 'load' }).promise
			if (!r.ok) return
			const base = import.meta.env.BASE_URL.replace(/\/$/, '')
			const src = pageSourceFile()
			const where = { pathname: location.pathname, source: src.guessed ? null : src.path, base }
			const n = r.items.filter((v) => v.status === 'open' && onPage(v, where)).length
			app.toggleNotification({ state: n > 0, level: 'info' })
		}
		const close = () => {
			session?.close()
			session = null
			void hint()
		}
		app.onToggled(({ state }) => {
			remember(state)
			if (!state) return close()
			if (session) return
			session = openReview({
				call: (req) => rpc.call(req).promise,
				// パネルの × から隠したときは、toolbar のアプリも閉じた状態に戻す。
				onClose: () => {
					close()
					app.toggleState({ state: false })
				},
			})
		})
		// `#review=<id>` のリンク（cli の review show が出す）で来たら、出してその指摘を開く。同じページの中でリンクを踏んだときも。
		const follow = () => {
			const linked = /^#review=(.+)$/.exec(location.hash)
			if (!linked) return false
			const id = decodeURIComponent(linked[1]!)
			history.replaceState(history.state, '', location.pathname + location.search)
			if (session) session.reveal(id)
			else {
				try {
					sessionStorage.setItem(OPEN_KEY, id)
				} catch {
					// 覚えられなければ出すだけ。
				}
				app.toggleState({ state: true })
			}
			return true
		}
		window.addEventListener('hashchange', () => void follow())
		// 前のページで出していたら、このページでも出す。
		if (!follow() && remembered()) app.toggleState({ state: true })
		void hint()
	},
} satisfies DevToolbarApp
