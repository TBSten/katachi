import { defineToolbarApp } from 'astro/toolbar'
import { createFlow } from './flow'
import { enterMode } from './mode'
import { setupQuick } from './quick'

export default defineToolbarApp({
	init(_canvas, app, server) {
		const flow = createFlow(app, server)
		// モードの外でも、テキストを選んで右クリックしたらフィードバックできるようにする（常設のリスナーはこの1つ）。
		setupQuick(flow)
		app.onToggled(({ state }) => {
			if (state) {
				// モードの外から開いていた分は、モードに入る前に片付ける。
				if (flow.session?.quick) flow.cleanup()
				enterMode(flow)
			} else if (flow.session && !flow.session.quick) {
				flow.cleanup()
			}
		})
	},
})
