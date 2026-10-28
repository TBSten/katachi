// 保存の依頼 → 記録の Markdown → Store。採番・排他的な書き込みは Store の putEntry がする。
// サーバーだけが知る値（git・ビルド・dirty・stale）はここで足す。
import { toMarkdown } from '../core/record'
import type { SavePayload } from '../core/protocol'
import { buildInfo, type BuildContext } from './build-info'
import { sourceDirty, sourceFileOf, withGit } from './git'
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
	const final: SavePayload = wantBuild ? { ...withServer, sections: { ...withServer.sections, build: info.section } } : withServer
	const dirty = file ? await sourceDirty(ctx.repoRoot, file) : undefined
	const entry = await store.putEntry({
		text: (id) => toMarkdown(final, { created: now, id, dirty, stale: info.stale }),
		now,
	})
	return `${entry.id}.md`
}
