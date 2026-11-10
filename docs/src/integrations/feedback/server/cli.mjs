#!/usr/bin/env node
// 消化ループ（.claude/skills/docs-feedback-loop）用の cli。Store（store.ts）をそのまま使う。
//
//   node docs/src/integrations/feedback/server/cli.mjs list [--state=open|archived|all] [--json]
//   node docs/src/integrations/feedback/server/cli.mjs read <id>
//   node docs/src/integrations/feedback/server/cli.mjs archive <id> --note "<1行>"
//   node docs/src/integrations/feedback/server/cli.mjs review <list|show|check|remove|reopen|actions> …   （docs への指摘。review/README.md）
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
  cli.mjs review list [--status=open|resolved|all] [--action=<名前>] [--impact=あり|なし] [--json]  指摘の一覧（既定は all）。id・状態・優先度・ユーザ影響・場所・見出し・一言。--action は押したボタン（直さない・直してもらう・人が足した選択肢）で、--impact はユーザ影響で絞る
  cli.mjs review show <id>                             指摘 1 件の全部（本文・今のソースでの行）
  cli.mjs review check                                 指摘のファイルを検査（読めない指摘・無いファイル・quote が見つからない行・選択肢に無い推奨度）。問題があれば 1 で終わる。ユーザ影響・推奨度の書き漏れは「注意」に出す
  cli.mjs review remove <id>                           確かめた指摘を消す（解決済みのものだけ。--force で未解決も）
  cli.mjs review reopen <id> --note "<理由>"           解決済みを差し戻す（ai-note に理由を書く）
  cli.mjs review actions [--json]                      人が popup に足した選択肢（review/_actions.md）と、選ばれた指摘を agent がどう扱うか
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
const impactText = (v) => (v.impact ? (v.impact.user ? '影響あり' : '影響なし') : '影響-')

/** 指摘（Review）。置き場の review/ を読む。 */
async function review([sub, id], flags, dir) {
	const { createReviewStore, REVIEW_SUBDIR } = await import(pathToFileURL(resolve(here, 'review.ts')).href)
	const { pageUrlOf, formatRecommend } = await import(pathToFileURL(resolve(here, '../review/model.ts')).href)
	const { matchNote, ACTIONS_FILE } = await import(pathToFileURL(resolve(here, '../review/actions.ts')).href)
	const { REVIEW_KEEP_NOTE: KEEP, REVIEW_SENT_NOTE } = await import(pathToFileURL(resolve(here, '../core/protocol.ts')).href)
	const { base: docsBase } = await import(pathToFileURL(resolve(here, '../../../lib/versions.mjs')).href)
	const store = createReviewStore(resolve(dir, REVIEW_SUBDIR), repoRoot)
	const data = await store.load()
	// 解決済みの指摘が、どのボタンで解決済みにされたか（note の頭）。読めなければ null。
	const choiceNames = [KEEP, REVIEW_SENT_NOTE, ...data.actions.map((a) => a.label)]
	const choiceOf = (v) => (v.status === 'resolved' ? (matchNote(v.note, choiceNames)?.name ?? null) : null)
	const find = () => {
		if (!id) throw new Error(`id を指定してください: review ${sub} <id>`)
		const v = data.items.find((x) => x.id === id)
		if (!v) throw new Error(`指摘が見つかりません: ${id}（review list で id を確かめてください）`)
		return v
	}

	if (!sub || sub === 'list') {
		const status = flags.status ?? 'all'
		if (!['open', 'resolved', 'all'].includes(status)) throw new Error(`--status は open / resolved / all のどれか: ${status}`)
		// popup のボタンの名前で絞る。「直してもらう」は note の頭が「Feedback に送った」になる。
		const asked = typeof flags.action === 'string' ? flags.action.trim() : ''
		const action = asked === '直してもらう' ? REVIEW_SENT_NOTE : asked
		if (action && !choiceNames.includes(action)) console.log(`（「${asked}」という選択肢はありません。review actions で名前を確かめてください）`)
		// ユーザ影響で絞る（`- impact:` の無い指摘は、絞ると出ない）。
		const impactFlag = typeof flags.impact === 'string' ? flags.impact.trim() : ''
		const impactWant = { あり: true, yes: true, user: true, なし: false, no: false, none: false }[impactFlag]
		if (impactFlag && impactWant === undefined) throw new Error(`--impact は あり / なし のどちらか: ${impactFlag}`)
		const rows = data.items.filter((v) => (status === 'all' || v.status === status) && (!action || choiceOf(v) === action) && (impactWant === undefined || v.impact?.user === impactWant))
		if (flags.json) return console.log(JSON.stringify(rows, null, 2))
		if (!data.sets.length) return console.log(`（指摘のファイルがありません: ${store.dir}）`)
		for (const v of rows) {
			const note = [v.note && `人: ${v.note}`, v.aiNote && `agent: ${v.aiNote}`].filter(Boolean).join(' / ')
			console.log([v.id, v.status, v.priority === null ? '-' : `p${v.priority}`, impactText(v), `${v.file}${lineText(v)}`, clip(v.title, 70), placeMark[v.place], note && `（${clip(note, 80)}）`].filter((x) => x).join(' | '))
		}
		const count = (s) => data.items.filter((v) => v.status === s).length
		console.log(`計 ${rows.length} 件（open ${count('open')} / resolved ${count('resolved')}）${action ? `・「${asked}」で絞り込み` : ''}${impactFlag ? `・ユーザ影響 ${impactFlag} で絞り込み` : ''}`)
		return
	}

	if (sub === 'actions') {
		if (flags.json) return console.log(JSON.stringify({ actions: data.actions, errors: data.actionErrors }, null, 2))
		const n = (label) => data.items.filter((v) => choiceOf(v) === label).length
		const plain = data.items.filter((v) => v.status === 'resolved' && choiceOf(v) === null).length
		console.log(`初めからの選択肢: ${KEEP}（${n(KEEP)} 件。確かめずに review remove）/ 直してもらう（${n(REVIEW_SENT_NOTE)} 件。Feedback の消化で直して archive）/ 解決済みにする（${plain} 件。確かめて remove か reopen）`)
		if (!data.actions.length) console.log(`人が足した選択肢はありません（${resolve(store.dir, ACTIONS_FILE)}）`)
		for (const a of data.actions) console.log([`${a.label}（${n(a.label)} 件）`, a.decision && `判断: ${a.decision}`, `agent: ${a.agent ?? '（書かれていません。人に聞く）'}`].filter(Boolean).join(' | '))
		for (const e of data.actionErrors) console.log(`読めない行: ${e}`)
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
			`- ユーザ影響: ${v.impact ? `${v.impact.user ? 'あり' : 'なし'}${v.impact.reason ? `（${v.impact.reason}）` : ''}` : '（書かれていません）'}`,
			...(v.recommend ? [`- 推奨度: ${formatRecommend(v.recommend)}（0〜10。popup のボタンに出る）`] : []),
			`- 場所: ${v.file}${lineText(v)}${v.place === 'moved' ? `（書かれた行 ${lineText({ line: v.written }).slice(1)} から動かした）` : ''}${v.place === 'lost' ? '（quote が今のソースに見つからない。もう直っているかも）' : ''}${v.place === 'no-file' ? '（ファイルが無い）' : ''}`,
			...(v.note ? [`- 人の一言: ${v.note}`] : []),
			...((() => {
				const name = choiceOf(v)
				const a = data.actions.find((x) => x.label === name)
				return a ? [`- 人が選んだ: ${a.label}（${a.agent ? `agent: ${a.agent}` : 'agent への一言が無い。人に聞く'}${a.decision ? `・判断: ${a.decision}` : ''}）`] : []
			})()),
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
		for (const e of data.actionErrors) { problems++; console.log(e) }
		for (const v of data.items) {
			if (v.place === 'lost') { problems++; console.log(`${v.id}: quote が ${v.file}${lineText(v)} の近くに見つかりません（${clip(v.title, 50)}）`) }
		}
		// ユーザ影響・推奨度の書き漏れ（読めはするので問題には数えない。prerelease の登録では 0 にする）。
		const noImpact = data.items.filter((v) => v.status === 'open' && !v.impact)
		const noRec = data.items.filter((v) => v.status === 'open' && !v.recommend)
		if (noImpact.length) console.log(`注意: ユーザ影響（- impact:）が無い未解決の指摘 ${noImpact.length} 件: ${noImpact.slice(0, 5).map((v) => v.id).join(', ')}${noImpact.length > 5 ? ' …' : ''}`)
		if (noRec.length) console.log(`注意: 推奨度（- recommend:）が無い未解決の指摘 ${noRec.length} 件: ${noRec.slice(0, 5).map((v) => v.id).join(', ')}${noRec.length > 5 ? ' …' : ''}`)
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
