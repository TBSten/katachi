// Review のサーバー側: 置き場の `review/*.md` を読み、指摘の行を今のソースに合わせて返す。解決済みの印の付け外しを書き戻す。
// dev サーバー（toolbar のメッセージ）と cli（server/cli.mjs の review）の両方から使う。
import { randomBytes } from 'node:crypto'
import { mkdir, open, readdir, readFile, rename, rm, stat, writeFile } from 'node:fs/promises'
import { isAbsolute, join, relative, resolve, sep } from 'node:path'
import { EVENT_REVIEW_REQUEST, EVENT_REVIEW_RESULT, type ReviewRequest, type ReviewResult, type ReviewSetView, type ReviewView } from '../core/protocol'
import { applyStatus, CONTENT_DIR, NOTE_CHARS, parseReview, relocate, removeItem, SET_NAME, splitLines, textHints, type ReviewItem, type StatusPatch } from '../review/model'

export type ReviewData = { sets: ReviewSetView[]; items: ReviewView[]; root: string }

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
		return { sets, items, root: repoRoot }
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
	if (o.op !== 'resolve' && o.op !== 'reopen') return fail('op が不正です')
	if (typeof o.id !== 'string' || o.id.length === 0 || o.id.length > 200) return fail('id が不正です')
	if (o.op === 'reopen') return { ok: true, req: { requestId, op: 'reopen', id: o.id } }
	if (o.op === 'resolve') {
		if (typeof o.note !== 'string') return fail('note が文字列ではありません')
		if (o.note.length > NOTE_CHARS * 4) return fail(`メモが長すぎます（上限 ${NOTE_CHARS} 文字）`)
		return { ok: true, req: { requestId, op: 'resolve', id: o.id, note: o.note } }
	}
	return fail('op が不正です')
}

/** 依頼 1 件を処理し、書き換えたあとの全体を返す。例外は投げず ok:false にする。 */
export function createReviewHandler(store: ReviewStore) {
	return async (data: unknown): Promise<ReviewResult | null> => {
		const check = checkReviewRequest(data)
		if (!check.ok) return check.requestId ? { requestId: check.requestId, ok: false, error: check.reason } : null
		const { req } = check
		try {
			if (req.op === 'resolve') await store.resolve(req.id, req.note)
			else if (req.op === 'reopen') await store.reopen(req.id)
			return { requestId: req.requestId, ok: true, ...(await store.load()) }
		} catch (e) {
			return { requestId: req.requestId, ok: false, error: e instanceof Error ? e.message : String(e) }
		}
	}
}

/** dev サーバーの toolbar に受け口を登録する。 */
export function registerReviewServer(toolbar: { on(event: string, cb: (data: unknown) => void): void; send(event: string, data: unknown): void }, store: ReviewStore) {
	const handler = createReviewHandler(store)
	toolbar.on(EVENT_REVIEW_REQUEST, async (data) => {
		const result = await handler(data)
		if (result) toolbar.send(EVENT_REVIEW_RESULT, result)
	})
}
