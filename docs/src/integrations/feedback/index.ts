import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'
import type { AstroIntegration } from 'astro'
import { saveFeedback } from './save'
import { APP_ID, EVENT_RESULT, EVENT_SAVE, type SavePayload, type SaveResult } from './protocol'

/** リポジトリ直下の `.local/feedback/`（このファイルは docs/src/integrations/feedback/ にある）。 */
const defaultDir = fileURLToPath(new URL('../../../../.local/feedback/', import.meta.url))

const isPayload = (v: unknown): v is SavePayload =>
	typeof v === 'object' && v !== null &&
	typeof (v as SavePayload).requestId === 'string' &&
	typeof (v as SavePayload).comment === 'string' &&
	typeof (v as SavePayload).sections === 'object' && (v as SavePayload).sections !== null

/**
 * `astro dev` のときだけ使えるフィードバックモード。
 * dev toolbar のアプリとして入口を足し、保存は toolbar のメッセージでサーバーへ送る。
 * `command === 'dev'` のときしか何も登録しないので、本番ビルドにはコードもマークアップも出ない。
 */
export default function feedback(): AstroIntegration {
	return {
		name: 'katachi-feedback',
		hooks: {
			'astro:config:setup': ({ command, addDevToolbarApp }) => {
				if (command !== 'dev') return
				addDevToolbarApp({
					id: APP_ID,
					name: 'Feedback',
					icon: 'lightbulb',
					entrypoint: new URL('./app.ts', import.meta.url),
				})
			},
			'astro:server:setup': ({ toolbar }) => {
				// 検証で本物の .local/feedback/ を汚さないよう、環境変数で上書きできる。
				const dir = process.env.KATACHI_FEEDBACK_DIR ? resolve(process.env.KATACHI_FEEDBACK_DIR) : defaultDir
				toolbar.on(EVENT_SAVE, async (data: unknown) => {
					if (!isPayload(data)) return
					let result: SaveResult
					try {
						result = { requestId: data.requestId, ok: true, file: await saveFeedback(dir, data) }
					} catch (e) {
						result = { requestId: data.requestId, ok: false, error: e instanceof Error ? e.message : String(e) }
					}
					toolbar.send(EVENT_RESULT, result)
				})
			},
		},
	}
}
