import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'
import type { AstroIntegration } from 'astro'
import { withGit } from './git'
import { saveFeedback } from './save'
import { createCodeBlockPlugin, createMermaidPlugin, createSourcePlugin } from './source'
import { APP_ID, EVENT_RESULT, EVENT_SAVE, type SavePayload, type SaveResult } from './protocol'

/** リポジトリ直下の `.local/feedback/`（このファイルは docs/src/integrations/feedback/ にある）。 */
const defaultDir = fileURLToPath(new URL('../../../../.local/feedback/', import.meta.url))

/** リポジトリ直下。行番号の付く path はここからの相対にする。 */
const repoRoot = fileURLToPath(new URL('../../../../', import.meta.url))

const isPayload = (v: unknown): v is SavePayload =>
	typeof v === 'object' && v !== null &&
	typeof (v as SavePayload).requestId === 'string' &&
	typeof (v as SavePayload).comment === 'string' &&
	((v as SavePayload).target === undefined || (v as SavePayload).target === 'page' || (v as SavePayload).target === 'site') &&
	typeof (v as SavePayload).sections === 'object' && (v as SavePayload).sections !== null

/**
 * `astro dev` のときだけ使えるフィードバックモード。
 * dev toolbar のアプリとして入口を足し、保存は toolbar のメッセージでサーバーへ送る。
 * `command === 'dev'` のときしか何も登録しないので、本番ビルドにはコードもマークアップも出ない。
 */
export default function feedback(): AstroIntegration {
	let isDev = false
	return {
		name: 'katachi-feedback',
		hooks: {
			'astro:config:setup': ({ command, addDevToolbarApp }) => {
				if (command !== 'dev') return
				isDev = true
				addDevToolbarApp({
					id: APP_ID,
					name: 'Feedback',
					icon: 'lightbulb',
					entrypoint: new URL('./app.ts', import.meta.url),
				})
			},
			// EC など他の統合が processor に足し終えたあとで、前に行番号付け・後ろにコードブロックの付け直しを差す。
			'astro:config:done': ({ config, logger }) => {
				if (!isDev) return
				const processor = config.markdown?.processor as { name?: string; options?: { hastPlugins?: unknown[]; mdastPlugins?: unknown[] } } | undefined
				const list = processor?.name === 'satteri' ? processor.options?.hastPlugins : undefined
				if (!list) return logger.warn('markdown.processor が Sätteri ではないので、ソースの行番号（data-fb-src）は付けません')
				processor!.options!.mdastPlugins?.unshift(createMermaidPlugin(repoRoot))
				list.unshift(createSourcePlugin(repoRoot))
				list.push(createCodeBlockPlugin())
			},
			'astro:server:setup': ({ toolbar }) => {
				// 検証で本物の .local/feedback/ を汚さないよう、環境変数で上書きできる。
				const dir = process.env.KATACHI_FEEDBACK_DIR ? resolve(process.env.KATACHI_FEEDBACK_DIR) : defaultDir
				toolbar.on(EVENT_SAVE, async (data: unknown) => {
					if (!isPayload(data)) return
					let result: SaveResult
					try {
						result = { requestId: data.requestId, ok: true, file: await saveFeedback(dir, await withGit(data, repoRoot)) }
					} catch (e) {
						result = { requestId: data.requestId, ok: false, error: e instanceof Error ? e.message : String(e) }
					}
					toolbar.send(EVENT_RESULT, result)
				})
			},
		},
	}
}
