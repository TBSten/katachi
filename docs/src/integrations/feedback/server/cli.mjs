#!/usr/bin/env node
// 消化ループ（.claude/skills/docs-feedback-loop）用の cli。Store（store.ts）をそのまま使う。
//
//   node docs/src/integrations/feedback/server/cli.mjs list [--state=open|archived|all] [--json]
//   node docs/src/integrations/feedback/server/cli.mjs read <id>
//   node docs/src/integrations/feedback/server/cli.mjs archive <id> --note "<1行>"
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
  cli.mjs archive <id> --note "<1行>"                  archived/ に移し、末尾に対応メモを足す
置き場: KATACHI_FEEDBACK_DIR か、リポジトリ直下の .local/feedback/`

/** 位置引数と --key=value / --key value に分ける。 */
function parseArgs(argv) {
	const positional = []
	const flags = {}
	for (let i = 0; i < argv.length; i++) {
		const a = argv[i]
		if (!a.startsWith('--')) positional.push(a)
		else if (a.includes('=')) flags[a.slice(2, a.indexOf('='))] = a.slice(a.indexOf('=') + 1)
		else if (['json'].includes(a.slice(2))) flags[a.slice(2)] = true
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
		const raw = kv[2].replace(/\s+#.*$/, '').trim()
		try {
			front[kv[1]] = JSON.parse(raw)
		} catch {
			front[kv[1]] = raw
		}
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

/** 一覧のソース欄。複数の要素（kind: elements）は配列なので、先頭と件数にする。 */
export const sourceText = (s) => (Array.isArray(s) ? (s.length > 1 ? `${s[0]} ほか${s.length - 1}件` : (s[0] ?? '-')) : (s ?? '-'))

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
		return
	}

	throw new Error(`知らないサブコマンド: ${cmd}\n${USAGE}`)
}

// import されたとき（テスト）は走らせない。
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
	main().catch((e) => {
		console.error(`エラー: ${e instanceof Error ? e.message : e}`)
		process.exitCode = 1
	})
}
