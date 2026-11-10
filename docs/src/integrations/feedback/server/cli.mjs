#!/usr/bin/env node
// 消化ループ（.claude/skills/docs-feedback-loop）用の cli。Store（store.ts）をそのまま使う。
//
//   node docs/src/integrations/feedback/server/cli.mjs list [--state=open|archived|all] [--json]
//   node docs/src/integrations/feedback/server/cli.mjs read <id>
//   node docs/src/integrations/feedback/server/cli.mjs archive <id> --note "<1行>"
//   node docs/src/integrations/feedback/server/cli.mjs review <list|show|check|remove|reopen> …   （docs への指摘。review/README.md）
//
// 置き場は KATACHI_FEEDBACK_DIR（無ければリポジトリ直下の .local/feedback/）。
//
// store.ts は TS で、import に拡張子が無い。Node の型の除去（22.18+ / 23.6+ は既定）で .ts を読み、
// 拡張子の補完だけ resolve フックで足す。契約の実装を二重に持たない（SSoT）ための選択。
import { registerHooks } from 'node:module'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { existsSync } from 'node:fs'

const here = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(here, '../../../../..')

// './naming' → './naming.ts'（相対 import で、.ts が実在するときだけ）
registerHooks({
	resolve(specifier, context, nextResolve) {
		if (/^\.\.?\//.test(specifier) && !/\.[cm]?[jt]s$/.test(specifier) && context.parentURL?.startsWith('file:')) {
			const candidate = new URL(`${specifier}.ts`, context.parentURL)
			if (existsSync(candidate)) return nextResolve(candidate.href, context)
		}
		return nextResolve(specifier, context)
	},
})

const USAGE = `使い方:
  cli.mjs list [--state=open|archived|all] [--json]   未対応（既定）の一覧。id・作成日時・ページ・ソース・要約
  cli.mjs read <id>                                    記録の全文
  cli.mjs archive <id> --note "<1行>"                  archived/ に移し、末尾に対応メモを足す（Review の「直してもらう」から来た記録なら、元の指摘も消す）
  cli.mjs review list [--status=open|resolved|all] [--json]  指摘の一覧（既定は all）。id・状態・優先度・場所・見出し・一言
  cli.mjs review show <id>                             指摘 1 件の全部（本文・今のソースでの行）
  cli.mjs review check                                 指摘のファイルを検査（読めない指摘・無いファイル・quote が見つからない行）。問題があれば 1 で終わる
  cli.mjs review remove <id>                           確かめた指摘を消す（解決済みのものだけ。--force で未解決も）
  cli.mjs review reopen <id> --note "<理由>"           解決済みを差し戻す（ai-note に理由を書く）
置き場: KATACHI_FEEDBACK_DIR か、リポジトリ直下の .local/feedback/`

/** 位置引数と --key=value / --key value に分ける。 */
function parseArgs(argv) {
	const positional = []
	const flags = {}
	for (let i = 0; i < argv.length; i++) {
		const a = argv[i]
		if (!a.startsWith('--')) positional.push(a)
		else if (a.includes('=')) flags[a.slice(2, a.indexOf('='))] = a.slice(a.indexOf('=') + 1)
		else if (['json', 'force'].includes(a.slice(2))) flags[a.slice(2)] = true
		else flags[a.slice(2)] = argv[++i] ?? ''
	}
	return { positional, flags }
}

/** frontmatter の `key: "json"` / `key: value` を読む。YAML 全体は読まない（一覧に要るキーだけ）。 */
export function readFront(text) {
	const m = /^---\r?\n([\s\S]*?)\r?\n---(?:\r?\n|$)/.exec(text)
	if (!m) return { front: {}, body: text }
	const front = {}
	for (const line of m[1].split(/\r?\n/)) {
		const kv = /^([A-Za-z][\w-]*):\s*(.*)$/.exec(line)
		if (!kv) continue
		// 引用符・括弧で始まる値は JSON として先に読む（値の途中の「空白 + #」はコメントではない）。読めなければコメントを切る。
		const whole = kv[2].trim()
		let value
		if (/^["[]/.test(whole)) {
			try {
				value = JSON.parse(whole)
			} catch {
				// 後ろにコメントが付いた形かもしれない
			}
		}
		if (value === undefined) {
			const raw = whole.replace(/\s+#.*$/, '').trim()
			try {
				value = JSON.parse(raw)
			} catch {
				value = raw
			}
		}
		front[kv[1]] = value
	}
	return { front, body: text.slice(m[0].length) }
}

const clip = (s, n) => {
	const one = s.replace(/\s+/g, ' ').trim()
	return one.length > n ? one.slice(0, n) + '…' : one
}

/** 一覧用の要約。新形は「対象:」の行、旧形は最初の見出し（コメントの1行目）。 */
export function summarize(text) {
	if (text.trim() === '') return '（0 バイト: 保存の途中かも。少し置いて再確認）'
	const { front, body } = readFront(text)
	const title = /^# (.+)$/m.exec(body)?.[1]
	const target = /^対象: (.+)$/m.exec(body)?.[1]
	const kind = front.schema ? `[${front.kind ?? '?'}] ` : ''
	return clip(kind + [title, !front.source && target && `（${target}）`].filter(Boolean).join(' ') || '（要約なし）', 110)
}

/** 一覧のソース欄。複数の要素（kind: elements）は要素の番号に対応する配列（無い要素は null）なので、分かるものの先頭と件数にする。 */
export const sourceText = (s) => {
	if (!Array.isArray(s)) return s ?? '-'
	const known = s.filter((v) => v !== null && v !== undefined)
	return known.length > 1 ? `${known[0]} ほか${known.length - 1}件` : (known[0] ?? '-')
}

const shortPage = (url) => {
	if (!url) return '-'
	try {
		const u = new URL(url)
		return decodeURIComponent(u.pathname.replace(/^\/katachi/, '')) + (u.hash ? '#…' : '')
	} catch {
		return clip(String(url), 50)
	}
}

async function main() {
	const { positional, flags } = parseArgs(process.argv.slice(2))
	const [cmd, id] = positional
	if (!cmd || flags.help || cmd === 'help') return console.log(USAGE)

	const { createFileStore } = await import(pathToFileURL(resolve(here, 'store.ts')).href)
	const dir = process.env.KATACHI_FEEDBACK_DIR ? resolve(process.env.KATACHI_FEEDBACK_DIR) : resolve(repoRoot, '.local/feedback')
	const store = createFileStore(dir, repoRoot)

	if (cmd === 'list') {
		const state = flags.state ?? 'open'
		if (!['open', 'archived', 'all'].includes(state)) throw new Error(`--state は open / archived / all のどれか: ${state}`)
		const rows = []
		// TODO: list と read の間に archive された記録があると「見つかりません」で全体が落ちる。1 件ごとに try/catch して <gone> と出す（review L22）。
		for (const e of await store.list({ state })) {
			const { text } = await store.read(e.id)
			const { front } = readFront(text)
			rows.push({
				id: e.id,
				state: e.state,
				created: front.created ?? e.createdAt,
				schema: front.schema ?? null,
				page: front.page ?? null,
				source: front.source ?? null,
				stale: front.stale ?? null,
				dirty: front.dirty ?? null,
				bytes: e.bytes,
				summary: summarize(text),
			})
		}
		if (flags.json) return console.log(JSON.stringify(rows, null, 2))
		if (rows.length === 0) return console.log(`（${state} の記録はありません: ${dir}）`)
		for (const r of rows) {
			const marks = [r.stale === true && 'stale', r.dirty === true && 'dirty', r.bytes === 0 && '0byte', r.state === 'archived' && 'archived'].filter(Boolean)
			console.log([r.id, r.created, shortPage(r.page), sourceText(r.source), r.summary, marks.length ? `<${marks.join(',')}>` : ''].filter((x) => x !== '').join(' | '))
		}
		console.log(`計 ${rows.length} 件（${state}）`)
		return
	}

	if (cmd === 'read') {
		if (!id) throw new Error('id を指定してください: read <id>')
		const e = await store.read(id)
		process.stdout.write(`<!-- ${e.id} ${e.state} ${e.ref} ${e.bytes}B -->\n${e.text}${e.text.endsWith('\n') ? '' : '\n'}`)
		return
	}

	if (cmd === 'archive') {
		if (!id) throw new Error('id を指定してください: archive <id> --note "<1行>"')
		const note = typeof flags.note === 'string' ? flags.note.trim() : ''
		if (!note) throw new Error('--note "<1行>" が必要です（何をしたか、判断待ちなら判断待ちと書く）')
		const e = await store.archive(id, note)
		console.log(`archived: ${e.id} -> ${e.ref}`)
		// Review の「直してもらう」から来た記録なら、元の指摘も消す（「- Review の指摘: <id>」の行）。
		const link = /^- Review の指摘: (\S+)$/m.exec((await store.read(e.id)).text)?.[1]
		if (link) {
			const { createReviewStore, REVIEW_SUBDIR } = await import(pathToFileURL(resolve(here, 'review.ts')).href)
			const reviews = createReviewStore(resolve(dir, REVIEW_SUBDIR), repoRoot)
			if (!(await reviews.load()).items.some((v) => v.id === link)) console.log(`Review の指摘 ${link} はもうありません（消してあります）`)
			else {
				await reviews.remove(link)
				console.log(`removed review: ${link}（Review の「直してもらう」から来た記録）`)
			}
		}
		return
	}

	if (cmd === 'review') return review(positional.slice(1), flags, dir)

	throw new Error(`知らないサブコマンド: ${cmd}\n${USAGE}`)
}

const placeMark = { ok: '', moved: '<moved>', lost: '<lost>', page: '', 'no-file': '<no-file>' }
const lineText = (v) => (v.line ? `:${v.line.start === v.line.end ? v.line.start : `${v.line.start}-${v.line.end}`}` : '')

/** 指摘（Review）。置き場の review/ を読む。 */
async function review([sub, id], flags, dir) {
	const { createReviewStore, REVIEW_SUBDIR } = await import(pathToFileURL(resolve(here, 'review.ts')).href)
	const { pageUrlOf } = await import(pathToFileURL(resolve(here, '../review/model.ts')).href)
	const { base: docsBase } = await import(pathToFileURL(resolve(here, '../../../lib/versions.mjs')).href)
	const store = createReviewStore(resolve(dir, REVIEW_SUBDIR), repoRoot)
	const data = await store.load()
	const find = () => {
		if (!id) throw new Error(`id を指定してください: review ${sub} <id>`)
		const v = data.items.find((x) => x.id === id)
		if (!v) throw new Error(`指摘が見つかりません: ${id}（review list で id を確かめてください）`)
		return v
	}

	if (!sub || sub === 'list') {
		const status = flags.status ?? 'all'
		if (!['open', 'resolved', 'all'].includes(status)) throw new Error(`--status は open / resolved / all のどれか: ${status}`)
		const rows = data.items.filter((v) => status === 'all' || v.status === status)
		if (flags.json) return console.log(JSON.stringify(rows, null, 2))
		if (!data.sets.length) return console.log(`（指摘のファイルがありません: ${store.dir}）`)
		for (const v of rows) {
			const note = [v.note && `人: ${v.note}`, v.aiNote && `agent: ${v.aiNote}`].filter(Boolean).join(' / ')
			console.log([v.id, v.status, v.priority === null ? '-' : `p${v.priority}`, `${v.file}${lineText(v)}`, clip(v.title, 70), placeMark[v.place], note && `（${clip(note, 80)}）`].filter((x) => x).join(' | '))
		}
		const count = (s) => data.items.filter((v) => v.status === s).length
		console.log(`計 ${rows.length} 件（open ${count('open')} / resolved ${count('resolved')}）`)
		return
	}

	if (sub === 'show') {
		const v = find()
		const set = data.sets.find((s) => s.name === v.set)
		const out = [
			`# ${v.title}`,
			'',
			`- id: ${v.id}`,
			`- まとまり: ${set?.title ?? v.set}（${set?.file ?? ''}）`,
			`- 状態: ${v.status}${v.resolvedAt ? `（${v.resolvedAt}）` : ''}`,
			`- 優先度: ${v.priority === null ? '-' : `p${v.priority}`}`,
			`- 場所: ${v.file}${lineText(v)}${v.place === 'moved' ? `（書かれた行 ${lineText({ line: v.written }).slice(1)} から動かした）` : ''}${v.place === 'lost' ? '（quote が今のソースに見つからない。もう直っているかも）' : ''}${v.place === 'no-file' ? '（ファイルが無い）' : ''}`,
			...(v.note ? [`- 人の一言: ${v.note}`] : []),
			...(v.aiNote ? [`- agent の一言: ${v.aiNote}`] : []),
			...(pageUrlOf(v.file, docsBase) ? [`- ページで開く: http://localhost:4321${pageUrlOf(v.file, docsBase)}#review=${v.id}（dev サーバーのポートに合わせる）`] : []),
			'',
			v.body,
		]
		return console.log(out.join('\n'))
	}

	if (sub === 'check') {
		let problems = 0
		for (const s of data.sets) for (const e of s.errors) { problems++; console.log(`${s.file}: ${e}`) }
		for (const v of data.items) {
			if (v.place === 'lost') { problems++; console.log(`${v.id}: quote が ${v.file}${lineText(v)} の近くに見つかりません（${clip(v.title, 50)}）`) }
		}
		const placed = data.items.filter((v) => v.place === 'moved').length
		console.log(`指摘 ${data.items.length} 件・問題 ${problems} 件${placed ? `（quote で行を合わせたもの ${placed} 件）` : ''}: ${store.dir}`)
		if (problems) process.exitCode = 1
		return
	}

	if (sub === 'remove') {
		const v = find()
		if (v.status !== 'resolved' && !flags.force) throw new Error(`まだ解決済みではありません: ${id}（人が解決済みにしたものだけを消します。消すなら --force）`)
		await store.remove(v.id)
		return console.log(`removed: ${v.id}（${clip(v.title, 60)}）`)
	}

	if (sub === 'reopen') {
		const v = find()
		const note = typeof flags.note === 'string' ? flags.note.trim() : ''
		if (!note) throw new Error('--note "<理由>" が必要です（何が直っていないか。人が popup で読みます）')
		await store.sendBack(v.id, note)
		return console.log(`reopened: ${v.id}`)
	}

	throw new Error(`知らない review のサブコマンド: ${sub}\n${USAGE}`)
}

// import されたとき（テスト）は走らせない。
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
	main().catch((e) => {
		console.error(`エラー: ${e instanceof Error ? e.message : e}`)
		process.exitCode = 1
	})
}
