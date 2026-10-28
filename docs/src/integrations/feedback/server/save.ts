// 保存の依頼 → 記録の Markdown → Store。採番・排他的な書き込みは Store の putEntry がする。
// サーバーだけが知る値（git・ビルド・dirty・stale）はここで足す。
import { toMarkdown } from '../core/record'
import type { SavePayload } from '../core/protocol'
import { buildInfo, type BuildContext } from './build-info'
import { sourceDirty, sourceFileOf, withGit } from './git'
import { storeScreenshots } from './screenshots'
import type { Store } from './store'

/** 保存に必要な、サーバー側の文脈。 */
export type SaveContext = { repoRoot: string; build: BuildContext }

/** 保存して、作られたファイル名（`yymmdd-NNN.md`）を返す。 */
export async function saveFeedback(store: Store, payload: SavePayload, ctx: SaveContext, now = new Date()): Promise<string> {
	const file = sourceFileOf(payload)
	// ブラウザが送ったサーバー側の節は捨て、チェックされていたものだけサーバーが作り直す。
	const wantBuild = Boolean(payload.sections.build)
	const sections = { ...payload.sections }
	if (!wantBuild) delete sections.build
	const withServer = await withGit({ ...payload, sections }, ctx.repoRoot)
	const info = await buildInfo(ctx.build, { repoRoot: ctx.repoRoot, sourceFile: file, pageOpenedAt: payload.pageOpenedAt })
	// スクリーンショットは記録より先に置き、参照を節と frontmatter に書く。
	const shots = await storeScreenshots(store, payload, now)
	const withBuild: SavePayload = wantBuild ? { ...withServer, sections: { ...withServer.sections, build: info.section } } : withServer
	const final: SavePayload = shots.section ? { ...withBuild, sections: { ...withBuild.sections, screenshot: shots.section } } : withBuild
	// TODO: 同じ git status を withGit と sourceDirty で 2 回呼んでいる。1 回にまとめる（review L2）。
	const dirty = file ? await sourceDirty(ctx.repoRoot, file) : undefined
	let entry
	try {
		entry = await store.putEntry({
			text: (id) => toMarkdown(final, { created: now, id, dirty, stale: info.stale, attachments: shots.refs }),
			now,
		})
	} catch (e) {
		// 記録が書けなかったので、先に置いた添付は誰からも参照されない。消す。
		if (shots.dir) await store.removeAttachments(shots.dir).catch(() => {})
		throw e
	}
	return `${entry.id}.md`
}
