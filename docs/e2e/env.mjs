// E2E の置き場と URL の取り決め。playwright.config.ts・dev-server.mjs・helpers/ が同じ値を見る（SSoT）。
import { fileURLToPath } from 'node:url'
import { join } from 'node:path'

/** リポジトリのルート（このファイルは docs/e2e/ にある）。 */
export const REPO_ROOT = fileURLToPath(new URL('../../', import.meta.url))
export const DOCS_DIR = join(REPO_ROOT, 'docs')
/** docs の写し。利用者が docs を編集しても dev が読み込み直して揺れないよう、E2E はこちらで dev を立てる。 */
/** 並行して別の E2E を回す担当は E2E_TAG / E2E_PORT で置き場とポートを分ける。 */
const TAG = process.env.E2E_TAG ? `-${process.env.E2E_TAG}` : ''
export const COPY_ROOT = join(REPO_ROOT, `.local/tmp/e2e-repo-copy${TAG}`)
export const COPY_DOCS = join(COPY_ROOT, 'docs')
/** KATACHI_FEEDBACK_DIR。本物の .local/feedback/ には書かない。記録の attachments は写しのルートからの相対で書かれる。 */
export const FEEDBACK_OUT = join(REPO_ROOT, `.local/tmp/e2e-out${TAG}`)
/**
 * 写しの Vite のキャッシュ（optimizeDeps の deps/）。写しの node_modules は本物への symlink なので、
 * 既定の node_modules/.vite のままだと、利用者の dev が使っている deps/ を写しの dev が作り直してしまう。
 */
export const VITE_CACHE = join(COPY_ROOT, 'vite-cache')
/** 写しの astro の設定。本物の astro.config.mjs を読み、cacheDir だけ VITE_CACHE に差し替える（dev-server.mjs が書く）。 */
export const COPY_CONFIG = 'astro.e2e.config.mjs'
export const PORT = Number(process.env.E2E_PORT ?? 4350)
export const ORIGIN = `http://localhost:${PORT}`
export const BASE = `${ORIGIN}/katachi`
