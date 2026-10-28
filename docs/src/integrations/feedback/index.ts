import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'
import type { AstroIntegration } from 'astro'
import { createBuildContext } from './server/build-info'
import { lanWarning } from './server/host'
import { saveFeedback } from './server/save'
import { createFileStore } from './server/store'
import { registerMemoServer } from './memo/server'
import { MEMO_ICON } from './memo/icon'
import { LOG_BUFFER_SCRIPT } from './inline/log-buffer'
import { checkPayload } from './core/limits'
import { createCodeBlockPlugin, createMermaidPlugin, createSourcePlugin } from './server/source'
import { APP_ID, MEMO_APP_ID, EVENT_RESULT, EVENT_SAVE, type SavePayload, type SaveResult } from './core/protocol'

/** リポジトリ直下の `.local/feedback/`（このファイルは docs/src/integrations/feedback/ にある）。 */
const defaultDir = fileURLToPath(new URL('../../../../.local/feedback/', import.meta.url))

/** リポジトリ直下。行番号の付く path はここからの相対にする。 */
const repoRoot = fileURLToPath(new URL('../../../../', import.meta.url))

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
			'astro:config:setup': ({ command, addDevToolbarApp, injectScript, updateConfig }) => {
				if (command !== 'dev') return
				isDev = true
				// スクリーンショットの依存は撮るときに動的 import する。先に束ねておかないと、初めて撮った瞬間に Vite がページを再読み込みする。
				updateConfig({ vite: { optimizeDeps: { include: ['modern-screenshot'] } } })
				injectScript('head-inline', LOG_BUFFER_SCRIPT)
				addDevToolbarApp({
					id: APP_ID,
					name: 'Feedback',
					icon: 'lightbulb',
					entrypoint: new URL('./feedback/app.ts', import.meta.url),
				})
				// 並びは登録順なので、Feedback の右に出るよう Feedback の後に登録する。
				addDevToolbarApp({
					id: MEMO_APP_ID,
					name: 'Memo',
					icon: MEMO_ICON,
					entrypoint: new URL('./memo/app.ts', import.meta.url),
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
			'astro:server:setup': ({ toolbar, server, logger }) => {
				if (!isDev) return
				// LAN に開いているときは警告する（LAN からの書き込み・読み出しは止めない。大きさの上限は core/limits.ts）。
				const warning = lanWarning(server.config.server.host)
				if (warning) logger.warn(warning)
				// 検証で本物の .local/feedback/ を汚さないよう、環境変数で上書きできる。
				const dir = process.env.KATACHI_FEEDBACK_DIR ? resolve(process.env.KATACHI_FEEDBACK_DIR) : defaultDir
				const store = createFileStore(dir, repoRoot)
				registerMemoServer(toolbar, store)
				// dev サーバーの起動時刻と、起動時の統合のハッシュ（記録のビルド節・古さの判定に使う）。
				const build = createBuildContext()
				toolbar.on(EVENT_SAVE, async (data: unknown) => {
					const check = checkPayload(data)
					if (!check.ok) {
						const requestId = (data as { requestId?: unknown } | null)?.requestId
						if (typeof requestId === 'string') toolbar.send(EVENT_RESULT, { requestId, ok: false, error: check.reason } satisfies SaveResult)
						return
					}
					const payload = data as SavePayload
					let result: SaveResult
					try {
						result = { requestId: payload.requestId, ok: true, file: await saveFeedback(store, payload, { repoRoot, build: await build }) }
					} catch (e) {
						result = { requestId: payload.requestId, ok: false, error: e instanceof Error ? e.message : String(e) }
					}
					toolbar.send(EVENT_RESULT, result)
				})
			},
		},
	}
}
