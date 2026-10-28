// 「ビルド」節の中身。Astro・Starlight・Node の版、dev サーバーの起動時刻、この統合のソースのハッシュ、
// そしてソースの古さ（ページを開いた後に mdx が変わったか）。保存のときにサーバーが求める。
import { createHash } from 'node:crypto'
import { readdir, readFile, stat } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import type { Section } from '../core/protocol'

/** この統合のフォルダ（`docs/src/integrations/feedback/`）。ハッシュの対象。 */
const integrationDir = fileURLToPath(new URL('../', import.meta.url))

/** パッケージの版。この統合から上へ `node_modules/<name>/package.json` を探す（exports で package.json が隠れていても読める）。 */
export async function packageVersion(name: string, from = integrationDir): Promise<string | null> {
	let dir = from
	for (let i = 0; i < 12; i++, dir = dirname(dir)) {
		try {
			const pkg = JSON.parse(await readFile(join(dir, 'node_modules', name, 'package.json'), 'utf8')) as { version?: string }
			return pkg.version ?? null
		} catch {
			// 上の階層へ。
		}
		if (dirname(dir) === dir) break
	}
	return null
}

/** この統合のソース（テストを除く .ts / .mjs）の内容のハッシュ。先頭 12 桁。 */
export async function integrationHash(dir = integrationDir): Promise<string> {
	const files: string[] = []
	const walk = async (d: string) => {
		for (const e of await readdir(d, { withFileTypes: true })) {
			if (e.name === 'test' || e.name === 'node_modules') continue
			const p = join(d, e.name)
			if (e.isDirectory()) await walk(p)
			else if (/\.(ts|mjs)$/.test(e.name)) files.push(p)
		}
	}
	await walk(dir)
	files.sort()
	const h = createHash('sha1')
	for (const f of files) h.update(f.slice(dir.length)).update(await readFile(f))
	return h.digest('hex').slice(0, 12)
}

/**
 * ページを開いた後にソースが変わったか。mtime の方が新しければ true。
 * 開いた時刻が分からない（古いブラウザ側など）ときは判定しない。
 */
export function isStale(sourceMtimeMs: number, pageOpenedAtMs: number | undefined): boolean | undefined {
	if (pageOpenedAtMs === undefined || !Number.isFinite(pageOpenedAtMs)) return undefined
	return sourceMtimeMs > pageOpenedAtMs
}

/** dev サーバーを起動した時点の情報。`astro:server:setup` で 1 回作る。 */
export type BuildContext = {
	startedAt: Date
	/** 起動時点の統合のハッシュ。保存時と違えば、統合を直したのに dev を再起動していない。 */
	startHash: string
}

export async function createBuildContext(startedAt = new Date(), dir = integrationDir): Promise<BuildContext> {
	return { startedAt, startHash: await integrationHash(dir).catch(() => '') }
}

export type BuildInfo = { section: Section; stale: boolean | undefined }

const iso = (ms: number) => new Date(ms).toISOString()

/** 保存のときの「ビルド」節と stale。sourceFile は repoRoot からの相対（分からなければ null）。 */
export async function buildInfo(
	ctx: BuildContext,
	o: { repoRoot: string; sourceFile: string | null; pageOpenedAt?: number },
): Promise<BuildInfo> {
	const rows: Section = []
	const [astro, starlight] = await Promise.all([packageVersion('astro'), packageVersion('@astrojs/starlight')])
	if (astro) rows.push(['Astro', astro])
	if (starlight) rows.push(['Starlight', starlight])
	rows.push(['Node', process.versions.node])
	rows.push(['dev サーバーの起動', ctx.startedAt.toISOString()])
	const now = await integrationHash().catch(() => '')
	if (now) {
		rows.push(['統合のハッシュ', now])
		if (ctx.startHash && ctx.startHash !== now) {
			rows.push(['統合の古さ', `dev の起動時（${ctx.startHash}）から統合のソースが変わっています。dev を再起動すると反映されます`])
		}
	}
	if (o.pageOpenedAt !== undefined) rows.push(['ページを開いた時刻', iso(o.pageOpenedAt)])
	let stale: boolean | undefined
	if (o.sourceFile) {
		try {
			const mtime = (await stat(join(o.repoRoot, o.sourceFile))).mtimeMs
			rows.push(['ソースの更新時刻', iso(mtime)])
			stale = isStale(mtime, o.pageOpenedAt)
			if (stale !== undefined) {
				rows.push(['ソースの古さ', stale ? 'ページを開いた後にソースが変わっています（記録の位置がずれているかもしれません）' : 'ページを開いた後の変更はありません'])
			}
		} catch {
			// ソースが読めなければ判定しない。
		}
	}
	return { section: rows, stale }
}
