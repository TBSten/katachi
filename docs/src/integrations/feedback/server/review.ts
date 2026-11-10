// Review のサーバー側: 置き場の `review/*.md` を読み、指摘の行を今のソースに合わせて返す。解決済みの印の付け外しを書き戻す。
// dev サーバー（toolbar のメッセージ）と cli（server/cli.mjs の review）の両方から使う。
import { randomBytes } from 'node:crypto'
import { mkdir, open, readdir, readFile, rename, rm, stat, writeFile } from 'node:fs/promises'
import { isAbsolute, join, relative, resolve, sep } from 'node:path'
import { EVENT_REVIEW_REQUEST, EVENT_REVIEW_RESULT, REVIEW_LINK_LABEL, REVIEW_SENT_NOTE, type ReviewAction, type ReviewDecision, type ReviewRequest, type ReviewResult, type ReviewSetView, type ReviewView, type SavePayload, type Section } from '../core/protocol'
import { ACTIONS_FILE, addAction, parseActions, removeAction } from '../review/actions'
import { applyStatus, CONTENT_DIR, NOTE_CHARS, parseReview, relocate, removeItem, SET_NAME, splitLines, textHints, type ReviewItem, type StatusPatch } from '../review/model'

export type ReviewData = { sets: ReviewSetView[]; items: ReviewView[]; root: string; actions: ReviewAction[]; actionErrors: string[] }

/** 置き場の中で、指摘のファイルを置くフォルダ。 */
export const REVIEW_SUBDIR = 'review'

/** リポジトリの中のパスなら、リポジトリ直下からの相対（`/` 区切り）。外を指すなら null。 */
function insideRepo(repoRoot: string, p: string): string | null {
	if (!p || isAbsolute(p) || p.includes('\0')) return null
	const rel = relative(repoRoot, resolve(repoRoot, p))
	if (rel === '' || rel.startsWith('..') || isAbsolute(rel)) return null
	return rel.split(sep).join('/')
}

const exists = async (abs: string) => readFile(abs, 'utf8').then((t) => t, () => null)

/** ほかのプロセスの書き込みを待つ上限と、残ったままの lock を古いとみなすまでの時間。 */
const LOCK_WAIT_MS = 3000
const LOCK_STALE_MS = 10_000

/** lock のファイルを排他で作る（O_EXCL）。取れたら、外す関数を返す。落ちたプロセスが残した古い lock は消して取り直す。 */
async function lock(path: string): Promise<() => Promise<void>> {
	const until = Date.now() + LOCK_WAIT_MS
	for (;;) {
		try {
			const h = await open(path, 'wx')
			await h.close()
			return () => rm(path, { force: true })
		} catch (e) {
			if ((e as NodeJS.ErrnoException).code !== 'EEXIST') throw e
			const age = await stat(path).then((s) => Date.now() - s.mtimeMs, () => 0)
			if (age > LOCK_STALE_MS) {
				await rm(path, { force: true })
				continue
			}
			if (Date.now() > until) throw new Error(`指摘のファイルをほかが書いています（${path} が残っていれば消してください）`)
			await new Promise((r) => setTimeout(r, 15))
		}
	}
}

/**
 * @param dir 指摘のファイルのフォルダ（`<置き場>/review/`）
 * @param repoRoot 指摘の file を読む基準
 */
export function createReviewStore(dir: string, repoRoot: string) {
	let chain: Promise<unknown> = Promise.resolve()
	/** 書き込みは 1 本ずつ（読み → 書き換え → 書き、が追い越し合わないように）。 */
	const serial = <T>(job: () => Promise<T>): Promise<T> => {
		const next = chain.then(job, job)
		chain = next.catch(() => {})
		return next
	}

	async function setNames(): Promise<string[]> {
		const names = await readdir(dir).catch(() => [] as string[])
		return names.filter((n) => n.endsWith('.md') && SET_NAME.test(n.slice(0, -3))).map((n) => n.slice(0, -3)).sort()
	}

	const setPath = (name: string) => {
		if (!SET_NAME.test(name)) throw new Error(`まとまりの名前が使えません: ${JSON.stringify(name)}`)
		return join(dir, `${name}.md`)
	}

	async function writeAtomic(abs: string, text: string) {
		await mkdir(dir, { recursive: true })
		const tmp = `${abs}.${process.pid}.${randomBytes(4).toString('hex')}.tmp`
		try {
			await writeFile(tmp, text, 'utf8')
			await rename(tmp, abs)
		} catch (e) {
			await rm(tmp, { force: true })
			throw e
		}
	}

	/** 書かれた file をリポジトリ直下からのパスにする。そのままで無ければ docs の本文の置き場からの省略形とみなす。 */
	async function resolveFile(file: string, cache: Map<string, string[] | null>): Promise<{ path: string; source: string[] | null }> {
		const candidates = [file, `${CONTENT_DIR}${file}`].map((p) => insideRepo(repoRoot, p)).filter((p): p is string => p !== null)
		for (const path of candidates) {
			if (!cache.has(path)) {
				const text = await exists(join(repoRoot, path))
				cache.set(path, text === null ? null : splitLines(text))
			}
			const source = cache.get(path)
			if (source) return { path, source }
		}
		return { path: candidates[0] ?? file, source: null }
	}

	function view(item: ReviewItem, path: string, source: string[] | null): ReviewView {
		const base = {
			id: item.id,
			set: item.set,
			title: item.title,
			priority: item.priority,
			file: path,
			written: item.line,
			status: item.status,
			...(item.note ? { note: item.note } : {}),
			...(item.resolvedAt ? { resolvedAt: item.resolvedAt } : {}),
			...(item.aiNote ? { aiNote: item.aiNote } : {}),
			body: item.body,
		}
		if (!source) return { ...base, line: item.line, place: 'no-file' }
		const r = relocate(item.line, item.quote, source)
		const hints = textHints(r.line, item.quote, source)
		return { ...base, line: r.line, place: r.place, ...(hints.length ? { hints } : {}) }
	}

	/** 全部のまとまりを読む。読めないファイルは errors に書いて続ける。 */
	async function load(): Promise<ReviewData> {
		const cache = new Map<string, string[] | null>()
		const sets: ReviewSetView[] = []
		const items: ReviewView[] = []
		for (const name of await setNames()) {
			const file = relative(repoRoot, setPath(name)).split(sep).join('/')
			const text = await exists(setPath(name))
			if (text === null) {
				sets.push({ name, title: name, file, errors: ['読めませんでした'] })
				continue
			}
			const set = parseReview(name, text)
			const errors = [...set.errors]
			for (const item of set.items) {
				const { path, source } = await resolveFile(item.file, cache)
				if (!source) errors.push(`ファイルがありません: ${item.file}（${item.title}）`)
				items.push(view(item, path, source))
			}
			sets.push({ name, title: set.title, ...(set.base ? { base: set.base } : {}), file, errors })
		}
		const at = await exists(actionsPath)
		const { actions, errors: actionErrors } = at === null ? { actions: [], errors: [] } : parseActions(at)
		return { sets, items, root: repoRoot, actions, actionErrors: actionErrors.map((e) => `${ACTIONS_FILE}: ${e}`) }
	}

	const setOf = (id: string) => {
		const name = id.split('/')[0] ?? ''
		if (!SET_NAME.test(name)) throw new Error(`id の形が違います: ${JSON.stringify(id)}`)
		return name
	}

	/**
	 * まとまりのファイルを読み、書き換えて書く。dev サーバーと cli（別のプロセス）が同じファイルを書くことがあるので、
	 * 横に `<名前>.md.lock` を排他で作ってから読み書きする（相手の書き換えを消さない）。
	 */
	function rewrite(id: string, edit: (name: string, text: string) => string): Promise<void> {
		return serial(async () => {
			const name = setOf(id)
			const abs = setPath(name)
			if ((await exists(abs)) === null) throw new Error(`指摘のファイルがありません: ${name}.md`)
			const release = await lock(`${abs}.lock`)
			try {
				const text = await exists(abs)
				if (text === null) throw new Error(`指摘のファイルがありません: ${name}.md`)
				await writeAtomic(abs, edit(name, text))
			} finally {
				await release()
			}
		})
	}

	const actionsPath = join(dir, ACTIONS_FILE)

	/** 選択肢のファイルを書き換える（指摘のファイルと同じく lock を取ってから）。 */
	function rewriteActions(edit: (text: string | null) => string): Promise<void> {
		return serial(async () => {
			await mkdir(dir, { recursive: true })
			const release = await lock(`${actionsPath}.lock`)
			try {
				await writeAtomic(actionsPath, edit(await exists(actionsPath)))
			} finally {
				await release()
			}
		})
	}

	/** id の指摘の印を書き換える。 */
	const patch = (id: string, p: StatusPatch) => rewrite(id, (name, text) => applyStatus(name, text, id, p))

	return {
		dir,
		load,
		resolve: (id: string, note: string, now = new Date()) => patch(id, { status: 'resolved', note, at: now.toISOString() }),
		reopen: (id: string) => patch(id, { status: 'open' }),
		/** agent が差し戻す（解決済みの印を消し、理由を ai-note に書く）。 */
		sendBack: (id: string, aiNote: string) => patch(id, { status: 'open', aiNote }),
		/** agent が確かめて消す。 */
		remove: (id: string) => rewrite(id, (name, text) => removeItem(name, text, id)),
		/** 人が選択肢を足す（`_actions.md` が無ければ作る）。 */
		addAction: (a: ReviewAction) => rewriteActions((text) => addAction(text, a)),
		/** 人が選択肢を消す。 */
		removeAction: (label: string) =>
			rewriteActions((text) => {
				if (text === null) throw new Error(`選択肢がありません: ${label}`)
				return removeAction(text, label)
			}),
	}
}

export type ReviewStore = ReturnType<typeof createReviewStore>

/** 依頼の形を確かめる。 */
export function checkReviewRequest(v: unknown): { ok: true; req: ReviewRequest } | { ok: false; requestId?: string; reason: string } {
	const o = (typeof v === 'object' && v !== null ? v : {}) as Record<string, unknown>
	const requestId = typeof o.requestId === 'string' && o.requestId.length > 0 && o.requestId.length <= 100 ? o.requestId : undefined
	const fail = (reason: string) => ({ ok: false as const, requestId, reason })
	if (!requestId) return fail('requestId が不正です')
	if (o.op === 'load') return { ok: true, req: { requestId, op: 'load' } }
	if (o.op === 'add-action' || o.op === 'remove-action') {
		if (typeof o.label !== 'string' || o.label.length === 0 || o.label.length > 200) return fail('label が不正です')
		if (o.op === 'remove-action') return { ok: true, req: { requestId, op: 'remove-action', label: o.label } }
		if (typeof o.agent !== 'string' || o.agent.length > 2000) return fail('agent が不正です')
		if (o.decision !== undefined && typeof o.decision !== 'string') return fail('decision が不正です')
		return { ok: true, req: { requestId, op: 'add-action', label: o.label, agent: o.agent, ...(o.decision ? { decision: o.decision as ReviewDecision } : {}) } }
	}
	if (o.op !== 'resolve' && o.op !== 'reopen' && o.op !== 'send' && o.op !== 'unsend') return fail('op が不正です')
	if (typeof o.id !== 'string' || o.id.length === 0 || o.id.length > 200) return fail('id が不正です')
	if (o.op === 'reopen') return { ok: true, req: { requestId, op: 'reopen', id: o.id } }
	if (o.op === 'unsend') {
		if (typeof o.record !== 'string' || !/^\d{6}-\d{2,}$/.test(o.record)) return fail('record が不正です')
		return { ok: true, req: { requestId, op: 'unsend', id: o.id, record: o.record } }
	}
	if (typeof o.note !== 'string') return fail('note が文字列ではありません')
	if (o.note.length > NOTE_CHARS * 4) return fail(`メモが長すぎます（上限 ${NOTE_CHARS} 文字）`)
	if (o.op === 'resolve') return { ok: true, req: { requestId, op: 'resolve', id: o.id, note: o.note } }
	if (typeof o.page !== 'string' || o.page.length > 2000) return fail('page が不正です')
	return { ok: true, req: { requestId, op: 'send', id: o.id, note: o.note, page: o.page } }
}

/**
 * 指摘 1 件から、Feedback の記録（保存の依頼の形）を作る。agent はいつもの消化ループ（cli list → 直す → archive）で読む。
 * 見出しは指摘の見出し、本文は人のひとことと指摘の本文。場所は Review が持っているので、そのまま「ソースの位置」にする。
 * 「選んだ内容」の節に元の指摘の id を書く（cli の archive がこれを見て指摘を消す）。
 */
export function reviewToFeedback(v: ReviewView, note: string, page: string): SavePayload {
	const line = v.line ? `${v.line.start === v.line.end ? v.line.start : `${v.line.start}-${v.line.end}`}` : null
	const comment = [`直してほしい: ${v.title}`, note.trim(), v.body.trim()].filter(Boolean).join('\n\n')
	const content: Section = [[REVIEW_LINK_LABEL, v.id]]
	if (v.place === 'lost') content.push(['注意', 'quote が今のソースに見つかりません。もう直っているかもしれません'])
	const sections: SavePayload['sections'] = {
		...(line ? { source: [['ソース', `${v.file}:${line}`]] as Section } : {}),
		page: [['URL', page], ['ページのソースファイル', v.file]],
		content,
	}
	return { requestId: `review-${v.id}`, comment, ...(line ? { kind: 'element' as const } : { target: 'page' as const }), sections }
}

/** 「直してもらう」で記録を書く先（Feedback の Store）。dev サーバーが渡す。 */
export type ReviewFeedbackSink = {
	/** 記録を保存して、ファイル名（`yymmdd-NNN.md`）を返す。 */
	send(payload: SavePayload): Promise<string>
	/** 送ったのを取り消す（記録を archived に移す）。 */
	unsend(record: string): Promise<void>
}

/** 依頼 1 件を処理し、書き換えたあとの全体を返す。例外は投げず ok:false にする。 */
export function createReviewHandler(store: ReviewStore, feedback?: ReviewFeedbackSink) {
	return async (data: unknown): Promise<ReviewResult | null> => {
		const check = checkReviewRequest(data)
		if (!check.ok) return check.requestId ? { requestId: check.requestId, ok: false, error: check.reason } : null
		const { req } = check
		try {
			let sent: string | undefined
			if (req.op === 'add-action') await store.addAction({ label: req.label, agent: req.agent, ...(req.decision ? { decision: req.decision } : {}) })
			else if (req.op === 'remove-action') await store.removeAction(req.label)
			else if (req.op === 'resolve') await store.resolve(req.id, req.note)
			else if (req.op === 'reopen') await store.reopen(req.id)
			else if (req.op === 'send' || req.op === 'unsend') {
				if (!feedback) throw new Error('Feedback の置き場がありません')
				if (req.op === 'send') {
					const v = (await store.load()).items.find((x) => x.id === req.id)
					if (!v) throw new Error(`指摘がありません: ${req.id}（読み込み直してください）`)
					sent = (await feedback.send(reviewToFeedback(v, req.note, req.page))).replace(/\.md$/, '')
					const head = `${REVIEW_SENT_NOTE}: ${sent}`
					await store.resolve(req.id, req.note.trim() ? `${head} — ${req.note.trim()}` : head)
				} else {
					await store.reopen(req.id)
					await feedback.unsend(req.record)
				}
			}
			return { requestId: req.requestId, ok: true, ...(await store.load()), ...(sent ? { sent } : {}) }
		} catch (e) {
			return { requestId: req.requestId, ok: false, error: e instanceof Error ? e.message : String(e) }
		}
	}
}

/** dev サーバーの toolbar に受け口を登録する。 */
export function registerReviewServer(toolbar: { on(event: string, cb: (data: unknown) => void): void; send(event: string, data: unknown): void }, store: ReviewStore, feedback?: ReviewFeedbackSink) {
	const handler = createReviewHandler(store, feedback)
	toolbar.on(EVENT_REVIEW_REQUEST, async (data) => {
		const result = await handler(data)
		if (result) toolbar.send(EVENT_REVIEW_RESULT, result)
	})
}
