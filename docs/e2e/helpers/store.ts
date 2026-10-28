// 保存先（KATACHI_FEEDBACK_DIR）の読み書き。dev サーバーは 1 つで保存先も 1 つなので、テストの前に空にして使う。
import { mkdirSync, readdirSync, readFileSync, rmSync, existsSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { createRequire } from 'node:module'
import { COPY_DOCS, COPY_ROOT, FEEDBACK_OUT } from '../env.mjs'

const require = createRequire(join(COPY_DOCS, 'package.json'))
const yaml = require('js-yaml') as { load: (s: string) => unknown }

/** 保存先の記録（Feedback）の相対ファイル名。古い順。 */
export const files = (): string[] => (existsSync(FEEDBACK_OUT) ? readdirSync(FEEDBACK_OUT).filter((f) => f.endsWith('.md')).sort() : [])
export const readRecord = (name: string): string => readFileSync(join(FEEDBACK_OUT, name), 'utf8')
export const latestRecord = (): string => (files().length ? readRecord(files().at(-1)!) : '')
/** Memo の Markdown（ページのパスから決まる場所）。無ければ null。 */
export const readMemo = (rel: string): string | null => {
	const p = join(FEEDBACK_OUT, 'memo', rel)
	return existsSync(p) ? readFileSync(p, 'utf8') : null
}
export const memoDir = () => join(FEEDBACK_OUT, 'memo')
/** 前のテストの記録・添付・メモを消す。 */
export function resetOut(): void {
	mkdirSync(FEEDBACK_OUT, { recursive: true })
	for (const f of readdirSync(FEEDBACK_OUT)) rmSync(join(FEEDBACK_OUT, f), { recursive: true, force: true })
}
/** 記録の frontmatter に書かれた添付のパスは、dev のリポジトリのルート（写し）からの相対。 */
export const attachmentAbs = (rel: string): string => resolve(COPY_ROOT, rel)

/** frontmatter を YAML として読む。 */
export function parseRecord(md: string): { front: Record<string, any>; body: string } {
	const m = /^---\n([\s\S]*?)\n---\n/.exec(md)
	if (!m) return { front: {}, body: md }
	return { front: (yaml.load(m[1]) ?? {}) as Record<string, any>, body: md.slice(m[0].length) }
}
/** frontmatter の 1 キーを行の文字列のまま取る。 */
export const frontKey = (md: string, k: string): string | undefined => md.match(new RegExp(`^${k}: (.*)$`, 'm'))?.[1]
/** `- ラベル: 値` の行の値。 */
export const bullet = (md: string, label: string): string | undefined => new RegExp(`^- ${label}: (.*)$`, 'm').exec(md)?.[1]
export const hasSection = (md: string, title: string): boolean => new RegExp(`^## ${title}$`, 'm').test(md)
/** 節 `## title` の本文（次の節まで）。 */
export const sectionBody = (md: string, title: string): string => new RegExp(`## ${title}\\n\\n([\\s\\S]*?)(?=\\n## |$)`).exec(md)?.[1] ?? ''

/** PNG の大きさ。 */
export function pngInfo(abs: string): { sig: boolean; w: number; h: number; bytes: number } {
	const b = readFileSync(abs)
	return { sig: b.subarray(1, 4).toString() === 'PNG', w: b.readUInt32BE(16), h: b.readUInt32BE(20), bytes: b.length }
}
