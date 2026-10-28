// E2E の置き場と URL の取り決め。playwright.config.ts・dev-server.mjs・helpers/ が同じ値を見る（SSoT）。
import { fileURLToPath } from 'node:url'
import { join } from 'node:path'

/** リポジトリのルート（このファイルは docs/e2e/ にある）。 */
export const REPO_ROOT = fileURLToPath(new URL('../../', import.meta.url))
export const DOCS_DIR = join(REPO_ROOT, 'docs')
/** docs の写し。利用者が docs を編集しても dev が読み込み直して揺れないよう、E2E はこちらで dev を立てる。 */
export const COPY_ROOT = join(REPO_ROOT, '.local/tmp/e2e-repo-copy')
export const COPY_DOCS = join(COPY_ROOT, 'docs')
/** KATACHI_FEEDBACK_DIR。本物の .local/feedback/ には書かない。記録の attachments は写しのルートからの相対で書かれる。 */
export const FEEDBACK_OUT = join(REPO_ROOT, '.local/tmp/e2e-out')
export const PORT = 4350
export const ORIGIN = `http://localhost:${PORT}`
export const BASE = `${ORIGIN}/katachi`
