// Review の popup に人が足す選択肢（「直さない」「直してもらう」「解決済みにする」の隣に並ぶボタン）。
// DOM も node: も使わない（ブラウザ・サーバー・cli から import する）。形は README.md の「選択肢を足す」。
//
// 置き場は `<置き場>/review/_actions.md`（`_` で始まるので、指摘のまとまりとしては読まない）。1 つ = 見出し 1 つ:
//
//   ## 後で
//   - agent: v0.5 のチケットに積んでから review remove する
//   - decision: 後で
//
// 押すと指摘を解決済みにして、note の頭に見出しの文字を書く（`後で` / `後で: <ひとこと>`）。「直さない」と同じ形。
import { REVIEW_KEEP_NOTE, REVIEW_SENT_NOTE, type ReviewAction, type ReviewDecision } from '../core/protocol'
import { oneLine, splitLines } from './model'

/** 選択肢のファイル名（`<置き場>/review/` の中）。 */
export const ACTIONS_FILE = '_actions.md'

/** prerelease の判断の記録（`.claude/skills/prerelease/decisions/`）の decision。`- decision:` に書ける値。 */
export const DECISIONS: readonly ReviewDecision[] = ['直さない', 'このまま', '後で', '方針']

/** ボタンの文字の上限（popup の 1 行に収まる長さ）。 */
export const LABEL_CHARS = 20
/** agent への一言の上限。 */
export const AGENT_CHARS = 300
/** 足せる数の上限（popup のボタンの並びが崩れない数）。 */
export const MAX_ACTIONS = 8

/** 初めから並んでいるボタンと、note の頭に使う決まった文字。同じ名前の選択肢は足せない。 */
const RESERVED = [REVIEW_KEEP_NOTE, '直してもらう', '解決済みにする', REVIEW_SENT_NOTE]

const HEADER = [
	'# Review の選択肢',
	'',
	'popup の「直さない」「直してもらう」「解決済みにする」の隣に並ぶボタン。1 つ = 見出し 1 つ。',
	'押すと指摘を解決済みにして、note の頭に見出しの文字を書く。agent は `- agent:` に書いたとおりに扱う。',
	'形は docs/src/integrations/feedback/review/README.md の「選択肢を足す」。popup の「＋」からも足せる。',
	'',
]

const META_LINE = /^- (agent|decision):(?:\s+(.*))?$/

/** ボタンの文字として使えるか。使えなければ理由を返す。 */
export function labelProblem(label: string): string | null {
	if (!label) return '名前が空です'
	if ([...label].length > LABEL_CHARS) return `名前は ${LABEL_CHARS} 文字までです`
	if (/[:：\n\r]/.test(label)) return '名前に「:」は使えません（note の「名前: ひとこと」の区切りに使うため）'
	if (/^\[p\d/i.test(label)) return '名前を [pN] で始められません'
	if (RESERVED.includes(label)) return `「${label}」は初めから並んでいるボタンか、決まった文字です`
	return null
}

/** `_actions.md` を読む。読めない選択肢は飛ばして errors に理由を書く（投げない）。 */
export function parseActions(text: string): { actions: ReviewAction[]; errors: string[] } {
	const lines = splitLines(text)
	const actions: ReviewAction[] = []
	const errors: string[] = []
	let cur: (ReviewAction & { line: number }) | null = null
	let inMeta = false
	const flush = () => {
		if (!cur) return
		const { line, ...a } = cur
		const problem = labelProblem(a.label)
		if (problem) errors.push(`L${line}: ${problem}（${a.label}）`)
		else if (actions.some((x) => x.label === a.label)) errors.push(`L${line}: 同じ名前の選択肢がもうあります（${a.label}）`)
		else if (actions.length >= MAX_ACTIONS) errors.push(`L${line}: 選択肢は ${MAX_ACTIONS} 個までです（${a.label} から先は出しません）`)
		else actions.push(a)
		cur = null
	}
	let inFence = false
	lines.forEach((l, i) => {
		if (/^\s*(```|~~~)/.test(l)) inFence = !inFence
		if (inFence) return
		if (/^## /.test(l)) {
			flush()
			cur = { label: l.slice(3).trim(), line: i + 1 }
			inMeta = true
			return
		}
		if (!cur || !inMeta) return
		const m = META_LINE.exec(l)
		if (!m) {
			inMeta = false
			return
		}
		const v = (m[2] ?? '').trim()
		if (m[1] === 'agent' && v) cur.agent = v
		if (m[1] === 'decision' && v) {
			if ((DECISIONS as readonly string[]).includes(v)) cur.decision = v as ReviewDecision
			else errors.push(`L${i + 1}: decision は ${DECISIONS.join(' / ')} のどれかです: ${v}（${cur.label}）`)
		}
	})
	flush()
	return { actions, errors }
}

/** 選択肢を 1 つ書き足した全文を返す（text が null ならファイルを作る）。使えない名前・同じ名前があれば投げる。 */
export function addAction(text: string | null, a: ReviewAction): string {
	const label = oneLine(a.label, 200)
	const problem = labelProblem(label)
	if (problem) throw new Error(problem)
	const { actions } = parseActions(text ?? '')
	if (actions.some((x) => x.label === label)) throw new Error(`「${label}」はもうあります`)
	if (actions.length >= MAX_ACTIONS) throw new Error(`選択肢は ${MAX_ACTIONS} 個までです（_actions.md から消してください）`)
	if (a.decision && !DECISIONS.includes(a.decision)) throw new Error(`decision は ${DECISIONS.join(' / ')} のどれかです`)
	const block = blockOf(label, a)
	const head = text === null || !text.trim() ? HEADER : splitLines(text.replace(/\n+$/, ''))
	return [...head, ...(head[head.length - 1] === '' ? [] : ['']), ...block, ''].join('\n')
}

/** 名前が label の選択肢の場所（見出しの行・メタの終わり・次の見出しの手前。0 始まり）。無ければ投げる。 */
function findAction(lines: string[], label: string): { start: number; metaEnd: number; end: number } {
	let start = -1
	let end = lines.length
	let inFence = false
	for (let i = 0; i < lines.length; i++) {
		if (/^\s*(```|~~~)/.test(lines[i]!)) inFence = !inFence
		if (inFence || !/^## /.test(lines[i]!)) continue
		if (start >= 0) {
			end = i
			break
		}
		if (lines[i]!.slice(3).trim() === label) start = i
	}
	if (start < 0) throw new Error(`選択肢がありません: ${label}（読み込み直してください）`)
	let metaEnd = start + 1
	while (metaEnd < end && META_LINE.test(lines[metaEnd]!)) metaEnd++
	return { start, metaEnd, end }
}

/** 見出しとメタの行（`## 名前` / `- agent:` / `- decision:`）。 */
function blockOf(label: string, a: Omit<ReviewAction, 'label'>): string[] {
	const agent = a.agent ? oneLine(a.agent, AGENT_CHARS) : ''
	return [`## ${label}`, ...(agent ? [`- agent: ${agent}`] : []), ...(a.decision ? [`- decision: ${a.decision}`] : [])]
}

/** 名前が label の選択肢を消した全文を返す（見出しから次の見出しの手前まで）。無ければ投げる。 */
export function removeAction(text: string, label: string): string {
	const lines = splitLines(text)
	const { start, end } = findAction(lines, label)
	return [...lines.slice(0, start), ...lines.slice(end)].join('\n').replace(/\n{3,}/g, '\n\n')
}

/**
 * 名前が from の選択肢を、その場で a に書き換えた全文を返す（並びと、メタの後ろの人のメモは変えない）。
 * 名前を変えるときは、使えない名前・ほかの選択肢と同じ名前なら投げる。
 */
export function updateAction(text: string, from: string, a: ReviewAction): string {
	const label = oneLine(a.label, 200)
	const problem = labelProblem(label)
	if (problem) throw new Error(problem)
	if (label !== from && parseActions(text).actions.some((x) => x.label === label)) throw new Error(`「${label}」はもうあります`)
	if (a.decision && !DECISIONS.includes(a.decision)) throw new Error(`decision は ${DECISIONS.join(' / ')} のどれかです`)
	const lines = splitLines(text)
	const { start, metaEnd } = findAction(lines, from)
	return [...lines.slice(0, start), ...blockOf(label, a), ...lines.slice(metaEnd)].join('\n')
}

/** 選択肢で解決済みにするときの note（`後で` / `後で: ひとこと`）。 */
export const noteOf = (label: string, note: string) => (note.trim() ? `${label}: ${note.trim()}` : label)

/** note の頭（「: 」の手前）。「直さない: 誤検知」なら「直さない」。 */
export const noteHead = (note: string | undefined) => (note ?? '').split(/[:：]\s?/)[0]!.trim()

const escapeRe = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

/**
 * note がどの名前で始まるか（無ければ null）と、その後ろの理由。prerelease の decisions.py の note_choice と同じ読み方。
 * popup は `名前` / `名前: 理由` と書く。手で付けた `名前（理由）`・`名前 (理由)`・`名前 理由` も受ける。
 * 名前は長いものから試す（`PR #65 待ち` と `PR` のように頭が重なっても、長い方に合わせる）。
 */
export function matchNote(note: string | undefined, names: readonly string[]): { name: string; reason: string } | null {
	const n = (note ?? '').trim()
	for (const name of [...names].sort((a, b) => b.length - a.length)) {
		const m = new RegExp(`^${escapeRe(name)}(?:\\s*[:：]\\s*([\\s\\S]*)|\\s*[（(]([\\s\\S]*)[）)]|\\s+([\\s\\S]*))?$`).exec(n)
		if (m) return { name, reason: (m[1] ?? m[2] ?? m[3] ?? '').trim() }
	}
	return null
}

/** note がどの選択肢で付いたか（無ければ null）。 */
export const actionOfNote = (note: string | undefined, actions: readonly ReviewAction[]) => {
	const m = matchNote(note, actions.map((a) => a.label))
	return m ? (actions.find((a) => a.label === m.name) ?? null) : null
}
