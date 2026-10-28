// 「コンソール」の節の整形。ブラウザが溜めたログ（inline/log-buffer.ts）を、記録の Markdown に載せる行にする。
// 純粋な関数だけ（DOM も node: も使わない）。ブラウザからは collect.ts が、テストからは直接呼ぶ。
import type { Section } from './protocol'

/** ログのリングバッファを置く window の名前。ブラウザ側（log-buffer.ts）と読む側（ここ）で共有する。 */
export const LOG_GLOBAL = '__katachiFeedbackLog'

/** 溜める種類。console の 3 レベルと、console を通らない失敗。 */
export type LogKind = 'error' | 'warn' | 'log' | 'exception' | 'rejection' | 'resource' | 'vite'

/** バッファの 1 件。短い名前にしてあるのは、ページに入れる script を小さく保つため。 */
export type LogEntry = {
	/** 起きた時刻（epoch ミリ秒）。 */
	t: number
	k: LogKind
	/** 本文。1 件の長さはバッファ側で切ってある。 */
	m: string
}

export const CONSOLE_LIMITS = {
	/** バッファが持つ件数（古いものから捨てる）。 */
	bufferEntries: 500,
	/** 1 件の本文の文字数。 */
	messageChars: 1_000,
	/** 節に載せる一覧の行数（行数の上限 500 より小さく、要約の行の分を空ける）。 */
	listRows: 400,
	/** 要約に載せる error/warn の種類数。 */
	summaryRows: 40,
	/** 節の値の合計文字数。新しい方から残す。 */
	totalChars: 50_000,
} as const

/** error として数える種類。 */
const ERROR_KINDS: ReadonlySet<LogKind> = new Set(['error', 'exception', 'rejection', 'resource', 'vite'])
const isError = (k: LogKind) => ERROR_KINDS.has(k)
const isWarn = (k: LogKind) => k === 'warn'

/** `globalThis` からバッファを読む。無い（読み込み前・本番）か、形が違えば空。 */
export function readLogEntries(scope: object): LogEntry[] {
	const api = (scope as Record<string, unknown>)[LOG_GLOBAL] as { entries?: () => unknown } | undefined
	let list: unknown
	try {
		list = api?.entries?.()
	} catch {
		return []
	}
	if (!Array.isArray(list)) return []
	return list.filter(
		(e): e is LogEntry => typeof e === 'object' && e !== null && typeof (e as LogEntry).t === 'number' && typeof (e as LogEntry).k === 'string' && typeof (e as LogEntry).m === 'string',
	)
}

type Group = { kind: LogKind; message: string; count: number; first: number; last: number }

/** 連続する同じ種類・同じ本文を 1 つにまとめる（古い順は保つ）。 */
function collapse(entries: LogEntry[]): Group[] {
	const out: Group[] = []
	for (const e of entries) {
		const prev = out[out.length - 1]
		if (prev && prev.kind === e.k && prev.message === e.m) {
			prev.count++
			prev.last = e.t
		} else {
			out.push({ kind: e.k, message: e.m, count: 1, first: e.t, last: e.t })
		}
	}
	return out
}

const clock = (t: number) => new Date(t).toISOString().slice(11, 23)
const times = (n: number) => (n > 1 ? ` ×${n}` : '')
const oneLine = (s: string, max = 160) => {
	const flat = s.replace(/\s+/g, ' ').trim()
	return flat.length > max ? `${flat.slice(0, max)}…` : flat
}

/** error と warn を（種類・本文）ごとに数えた要約の行。多い順ではなく最初に出た順。 */
function summaryRows(entries: LogEntry[]): Array<[string, string]> {
	const seen = new Map<string, { kind: LogKind; message: string; count: number }>()
	for (const e of entries) {
		if (!isError(e.k) && !isWarn(e.k)) continue
		const key = `${e.k}\u0000${e.m}`
		const hit = seen.get(key)
		if (hit) hit.count++
		else seen.set(key, { kind: e.k, message: e.m, count: 1 })
	}
	const all = [...seen.values()]
	const rows = all.slice(0, CONSOLE_LIMITS.summaryRows).map((g): [string, string] => [isWarn(g.kind) ? 'warn' : 'error', `[${g.kind}] ${oneLine(g.message)}${times(g.count)}`])
	if (all.length > rows.length) rows.push(['要約の省略', `ほか ${all.length - rows.length} 種類`])
	return rows
}

/**
 * コンソールの節を作る。先頭に error/warn の件数と要約、続けて古い順の一覧。
 * 大きさの上限を超えるときは古い方を捨て、捨てた件数を行で残す。0 件のときは「なし」と書く。
 */
export function consoleSection(entries: LogEntry[]): Section {
	if (entries.length === 0) return [['コンソール', 'なし（このページを開いてから error・warn・log は出ていません）']]
	const errors = entries.filter((e) => isError(e.k)).length
	const warns = entries.filter((e) => isWarn(e.k)).length
	const head: Section = [
		['件数', `error ${errors} 件 / warn ${warns} 件 / 全 ${entries.length} 件（直近 ${CONSOLE_LIMITS.bufferEntries} 件まで保持。時刻は UTC）`],
		...summaryRows(entries),
	]
	// 一覧は新しい方から上限まで取り、古い順に並べ直す。
	const groups = collapse(entries)
	const kept: Section = []
	let chars = head.reduce((n, [l, v]) => n + l.length + v.length, 0)
	let dropped = 0
	for (let i = groups.length - 1; i >= 0; i--) {
		const g = groups[i]
		const row: [string, string] = [`${clock(g.first)} ${g.kind}`, `${g.message}${times(g.count)}`]
		chars += row[0].length + row[1].length
		if (kept.length >= CONSOLE_LIMITS.listRows || chars > CONSOLE_LIMITS.totalChars) {
			dropped = i + 1
			break
		}
		kept.push(row)
	}
	kept.reverse()
	if (dropped > 0) kept.unshift(['省略', `古い方の ${dropped} 行（まとめ後）は大きさの上限で載せていません`])
	return [...head, ...kept]
}
