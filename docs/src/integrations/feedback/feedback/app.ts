// astro/toolbar の defineToolbarApp は使わず、型だけを astro から取る（astro/toolbar を import すると Vite の deps/ を経由する。理由は server/optimize-deps.ts）。
import type { DevToolbarApp } from 'astro'
import { createFlow } from './flow'
import { enterMode } from './mode'
import { setLinkSelectMode, setupAltSelect } from './linkselect'
import { setupQuick } from './quick'

export default {
	init(_canvas, app, server) {
		const flow = createFlow(app, server)
		// モードの外でも、テキストを選んで右クリックしたらフィードバックできるようにする（常設のリスナーは、これの contextmenu と下の Alt の keydown の 2 つ）。
		setupQuick(flow)
		// リンクカード（a::before の覆い）の中の文字を、モードの中と Alt+ドラッグで選べるようにする。
		setupAltSelect(() => flow.session !== null && !flow.session.quick)
		app.onToggled(({ state }) => {
			setLinkSelectMode(state)
			if (state) {
				// モードの外から開いていた分は、モードに入る前に片付ける。
				if (flow.session?.quick) flow.cleanup()
				enterMode(flow)
			} else if (flow.session && !flow.session.quick) {
				flow.cleanup()
			}
		})
	},
} satisfies DevToolbarApp
