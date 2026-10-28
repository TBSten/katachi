// ファイルの Store。置き場のルート（既定 `.local/feedback/`）の下に記録・添付・メモの文書を置く。
import { randomBytes } from 'node:crypto'
import { appendFile, lstat, mkdir, open, readdir, readFile, realpath, rename, stat, writeFile, rm } from 'node:fs/promises'
import { dirname, isAbsolute, join, relative, resolve, sep } from 'node:path'
import { ARCHIVE_DIR, datePrefix, formatId, idOfFileName, isEntryId, MAX_PER_DAY, nextNumber } from '../core/naming'
import { safeRelPath } from '../core/paths'
import { ATTACH_NAME, archiveNoteText, type Attachment, type EntryInfo, type EntryState, type PutAttachmentsResult, type Store } from './store-types'

export type { Store, EntryInfo, EntryState, Attachment, PutAttachmentsResult } from './store-types'

const ATTACHMENTS_DIR = 'attachments'

const two = (n: number) => String(n).padStart(2, '0')
const stamp = (d: Date) => `${d.getFullYear()}${two(d.getMonth() + 1)}${two(d.getDate())}-${two(d.getHours())}${two(d.getMinutes())}${two(d.getSeconds())}`

/** ディレクトリ内の名前。無ければ空。 */
async function namesIn(dir: string): Promise<string[]> {
	try {
		return await readdir(dir)
	} catch (e) {
		if ((e as NodeJS.ErrnoException).code === 'ENOENT') return []
		throw e
	}
}

/**
 * `root` の中に収まる絶対パスを返す。文字列の検査に加え、既にある一番近い親の実体（シンボリックリンクの先）が root の外なら投げる。
 */
async function resolveInside(root: string, relPath: string): Promise<string> {
	const abs = resolve(root, safeRelPath(relPath))
	const rel = relative(root, abs)
	if (rel === '' || rel.startsWith('..') || isAbsolute(rel)) throw new Error(`置き場の外には出せません: ${relPath}`)
	let probe = abs
	for (;;) {
		try {
			await lstat(probe)
			break
		} catch {
			if (probe === root) break
			probe = dirname(probe)
		}
	}
	const realRoot = await realpath(root).catch(() => root)
	const realProbe = await realpath(probe).catch(() => probe)
	const back = relative(realRoot, realProbe)
	if (back.startsWith('..') || isAbsolute(back)) throw new Error(`置き場の外を指しています: ${relPath}`)
	return abs
}

/**
 * ファイルの Store を作る。
 * @param root 置き場のルート（絶対パス。無ければ作る）
 * @param repoRoot 渡すと ref・添付の参照を repoRoot からの相対にする。渡さなければ root からの相対。
 */
export function createFileStore(root: string, repoRoot?: string): Store {
	const base = resolve(root)
	const refBase = repoRoot ? resolve(repoRoot) : base
	const ref = (abs: string) => relative(refBase, abs).split(sep).join('/')
	const dirOf = (state: EntryState) => (state === 'open' ? base : join(base, ARCHIVE_DIR))

	const info = async (id: string, state: EntryState, file: string): Promise<EntryInfo> => {
		const st = await stat(file)
		return { id, ref: ref(file), state, bytes: st.size, createdAt: st.birthtime.toISOString() }
	}
	const locate = async (id: string): Promise<{ state: EntryState; file: string }> => {
		if (!isEntryId(id)) throw new Error(`id が不正です: ${JSON.stringify(id)}`)
		for (const state of ['open', 'archived'] as const) {
			const file = join(dirOf(state), `${id}.md`)
			try {
				if ((await stat(file)).isFile()) return { state, file }
			} catch {
				// 次の置き場へ
			}
		}
		throw new Error(`見つかりません: ${id}`)
	}
	const scan = async (state: EntryState): Promise<EntryInfo[]> => {
		const dir = dirOf(state)
		const out: EntryInfo[] = []
		for (const name of await namesIn(dir)) {
			const id = idOfFileName(name)
			if (!id) continue
			const file = join(dir, name)
			if ((await stat(file)).isFile()) out.push(await info(id, state, file))
		}
		return out
	}

	return {
		kind: 'file',

		/**
		 * `<root>/yymmdd-NNN.md` に排他的（wx）に書く。
		 * 番号は一覧から決めるが、同時の保存と取り合うので、作れた番号を採用する。既にある番号は EEXIST で次へ進む。
		 */
		async putEntry({ text, now = new Date() }) {
			await mkdir(base, { recursive: true })
			const prefix = datePrefix(now)
			const names = [...(await namesIn(base)), ...(await namesIn(join(base, ARCHIVE_DIR)))]
			const start = nextNumber(names, prefix)
			if (start === null) throw new Error(`その日の連番（${MAX_PER_DAY}）を使い切りました`)
			let attempts = 0
			for (let n = start; n <= MAX_PER_DAY; n++) {
				attempts++
				const id = formatId(prefix, n)
				const file = join(base, `${id}.md`)
				// archived に同じ番号が現れた場合（別プロセスが移した直後）は使わない。
				if ((await namesIn(join(base, ARCHIVE_DIR))).includes(`${id}.md`)) continue
				let handle
				try {
					handle = await open(file, 'wx')
				} catch (e) {
					if ((e as NodeJS.ErrnoException).code === 'EEXIST') continue
					throw e
				}
				try {
					await handle.writeFile(typeof text === 'function' ? text(id) : text, 'utf8')
				} finally {
					await handle.close()
				}
				return { ...(await info(id, 'open', file)), attempts }
			}
			throw new Error(`その日の連番（${MAX_PER_DAY}）を使い切りました`)
		},

		async list({ state = 'open' } = {}) {
			const out = [...(state !== 'archived' ? await scan('open') : []), ...(state !== 'open' ? await scan('archived') : [])]
			return out.sort((a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0))
		},

		async read(id) {
			const { state, file } = await locate(id)
			return { ...(await info(id, state, file)), text: await readFile(file, 'utf8') }
		},

		async archive(id, note, now = new Date()) {
			const { state, file } = await locate(id)
			if (state === 'archived') return info(id, state, file)
			const dest = join(base, ARCHIVE_DIR, `${id}.md`)
			await mkdir(dirname(dest), { recursive: true })
			if (await stat(dest).then(() => true, () => false)) throw new Error(`既にあります: ${ref(dest)}`)
			await rename(file, dest)
			if (note) await appendFile(dest, archiveNoteText(note, now), 'utf8')
			return info(id, 'archived', dest)
		},

		/** `<root>/attachments/<YYYYMMDD-HHMMSS>/<name>`。失敗しても投げず error に入れる。 */
		async putAttachments(files: Attachment[], { now = new Date() } = {}): Promise<PutAttachmentsResult> {
			try {
				if (files.length === 0) return { refs: {}, dir: null, error: '添付がありません' }
				for (const f of files) if (!ATTACH_NAME.test(f.name)) throw new Error(`添付の名前が使えません: ${f.name}`)
				const root0 = join(base, ATTACHMENTS_DIR)
				await mkdir(root0, { recursive: true })
				let dir = join(root0, stamp(now))
				// 同じ秒のものは -2, -3 …（mkdir を再帰なしで試し、取り合いに強くする）
				for (let i = 1; ; i++) {
					const candidate = i === 1 ? dir : `${dir}-${i}`
					try {
						await mkdir(candidate)
						dir = candidate
						break
					} catch (e) {
						if ((e as NodeJS.ErrnoException).code !== 'EEXIST') throw e
					}
				}
				const refs: Record<string, string> = {}
				for (const f of files) {
					await writeFile(join(dir, f.name), f.data)
					refs[f.name] = ref(join(dir, f.name))
				}
				return { refs, dir: ref(dir), error: null }
			} catch (e) {
				return { refs: {}, dir: null, error: e instanceof Error ? e.message : String(e) }
			}
		},

		/** 同じディレクトリの tmp に書いてから rename する。読み手に書きかけが見えない。 */
		async writeDoc(relPath, text) {
			const abs = await resolveInside(base, relPath)
			await mkdir(dirname(abs), { recursive: true })
			const tmp = `${abs}.${process.pid}.${randomBytes(4).toString('hex')}.tmp`
			try {
				await writeFile(tmp, text, 'utf8')
				await rename(tmp, abs)
			} catch (e) {
				await rm(tmp, { force: true })
				throw e
			}
		},

		async readDoc(relPath) {
			const abs = await resolveInside(base, relPath)
			try {
				return await readFile(abs, 'utf8')
			} catch (e) {
				if ((e as NodeJS.ErrnoException).code === 'ENOENT') return null
				throw e
			}
		},
	}
}
