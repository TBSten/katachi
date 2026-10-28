// 保存のときにサーバー側で git の情報を求める。シェルは通さず execFile で呼び、失敗したものは書かない。
import { execFile } from 'node:child_process'
import { resolve } from 'node:path'
import { promisify } from 'node:util'
import type { SavePayload, Section } from '../core/protocol'

const run = promisify(execFile)

// --literal-pathspecs: ソースのパスに `:(glob)` や `:/` が書かれても magic として解釈させない。
// GIT_WORK_TREE が設定されていれば（dev を写しで動かすとき: .git は本物、ファイルは写し）、その作業ツリーの中で git を呼ぶ。
// cwd が作業ツリーの外だと status が空になり dirty が常に false になる。
const git = async (cwd: string, args: string[]) => {
	const workTree = process.env.GIT_WORK_TREE
	return (await run('git', ['--literal-pathspecs', ...args], { cwd: workTree ? resolve(cwd, workTree) : cwd, timeout: 5000 })).stdout.trim()
}

/** `source` 節の「ソース」(`path:12-14`) からファイルのパスだけ取り出す。リポジトリの外を指すものは使わない。 */
export function sourceFileOf(payload: SavePayload): string | null {
	// 複数の要素では先頭がソース不明のこともある。最初に行番号の読めたものを使う。
	const m = (payload.sections.source ?? []).filter(([l]) => l === 'ソース').map(([, v]) => /^(.+?):\d+(?:-\d+)?$/.exec(v)).find(Boolean)
	// ページ全体・サイト全体のときは行が無いので、page 節のページのソースファイルを使う（URL からの推定のものは使わない）。
	const pageFile = payload.sections.page?.find(([l]) => l === 'ページのソースファイル')?.[1]
	const path = m?.[1] ?? (payload.target && pageFile && !pageFile.includes('（') ? pageFile : undefined)
	return path && !path.startsWith('/') && !path.split('/').includes('..') ? path : null
}

/** ソースのファイルに未コミットの変更があるか。git が使えなければ undefined。 */
export async function sourceDirty(repoRoot: string, file: string): Promise<boolean | undefined> {
	try {
		return (await git(repoRoot, ['status', '--porcelain', '--', file])) !== ''
	} catch {
		return undefined
	}
}

/**
 * git 節の中身を本物の値にする。commit が求められなければ git 節ごと外す。
 * ソースのファイルが分からないときは、未コミットの変更の行だけ作らない。
 */
export async function withGit(payload: SavePayload, repoRoot: string): Promise<SavePayload> {
	if (!payload.sections.git) return payload
	const { git: _placeholder, ...rest } = payload.sections
	let commit: string
	try {
		commit = await git(repoRoot, ['rev-parse', '--short', 'HEAD'])
	} catch {
		return { ...payload, sections: rest }
	}
	if (!commit) return { ...payload, sections: rest }
	const section: Section = [['commit', commit]]
	const file = sourceFileOf(payload)
	if (file) {
		try {
			const status = await git(repoRoot, ['status', '--porcelain', '--', file])
			section.push(['ソースの未コミットの変更', status ? `あり（${file}）` : 'なし'])
		} catch {
			// 求められなかった行は書かない。
		}
	}
	return { ...payload, sections: { ...rest, git: section } }
}
