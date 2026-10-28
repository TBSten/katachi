// Store の契約テスト。file と memory に同じテストを掛ける。新しい実装を足したら make に 1 つ足すだけでよい。
import { mkdir, mkdtemp, readdir, readFile, stat, symlink, writeFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterAll, describe, expect, it } from 'vitest'
import { createMemoryStore } from '../server/memory-store'
import { createFileStore } from '../server/store'
import type { Store } from '../server/store'

const dirs: string[] = []
const tmp = async () => {
	const d = await mkdtemp(join(tmpdir(), 'fb-store-'))
	dirs.push(d)
	return d
}
afterAll(async () => {
	await Promise.all(dirs.map((d) => rm(d, { recursive: true, force: true })))
})

type Made = { store: Store; root?: string }
const makers: Record<string, () => Promise<Made>> = {
	file: async () => {
		const root = join(await tmp(), 'feedback')
		return { store: createFileStore(root), root }
	},
	memory: async () => ({ store: createMemoryStore() }),
}

const day = new Date(2026, 9, 29, 10)
const ids = async (s: Store, state?: 'open' | 'archived' | 'all') => (await s.list({ state })).map((e) => e.id)

describe.each(Object.keys(makers))('Store 契約 [%s]', (kind) => {
	const make = makers[kind]!

	it('採番: 3 桁で 1 から。日付が変われば 1 に戻る', async () => {
		const { store } = await make()
		expect((await store.putEntry({ text: 'a', now: day })).id).toBe('261029-001')
		expect((await store.putEntry({ text: 'b', now: day })).id).toBe('261029-002')
		expect((await store.putEntry({ text: 'c', now: new Date(2026, 9, 30) })).id).toBe('261030-001')
	})

	it('archived の番号も数える（重ならない）', async () => {
		const { store } = await make()
		await store.putEntry({ text: 'a', now: day })
		await store.putEntry({ text: 'b', now: day })
		await store.archive('261029-002', '対応した', day)
		expect((await store.putEntry({ text: 'c', now: day })).id).toBe('261029-003')
		await store.archive('261029-003')
		await store.archive('261029-001')
		expect((await store.putEntry({ text: 'd', now: day })).id).toBe('261029-004')
	})

	it('上書きしない: 同じ内容を何度保存しても別の記録になり、前の本文が残る', async () => {
		const { store } = await make()
		const a = await store.putEntry({ text: 'first', now: day })
		const b = await store.putEntry({ text: 'second', now: day })
		expect(a.id).not.toBe(b.id)
		expect((await store.read(a.id)).text).toBe('first')
		expect((await store.read(b.id)).text).toBe('second')
	})

	it('同時に保存しても番号が重ならず、全部残る', async () => {
		const { store } = await make()
		const results = await Promise.all(Array.from({ length: 25 }, (_, i) => store.putEntry({ text: `t${i}`, now: day })))
		const got = results.map((r) => r.id)
		expect(new Set(got).size).toBe(25)
		expect((await ids(store)).length).toBe(25)
		const texts = await Promise.all(got.map(async (id) => (await store.read(id)).text))
		expect(new Set(texts).size).toBe(25)
	})

	it('id の検査: 不正な id は read / archive で拒否（パスの組み立てに使わない）', async () => {
		const { store } = await make()
		await store.putEntry({ text: 'a', now: day })
		for (const bad of ['../x', '261029-001/../..', 'archived/261029-001', '261029-001.md', '', '261029-1', 'HISTORY']) {
			await expect(store.read(bad)).rejects.toThrow()
			await expect(store.archive(bad)).rejects.toThrow()
		}
		await expect(store.read('261029-099')).rejects.toThrow('見つかりません')
	})

	it('パスがルートの外に出ない: writeDoc / readDoc は ../・絶対パス・空を拒否', async () => {
		const { store } = await make()
		for (const bad of ['../x.md', 'a/../../x.md', '/etc/passwd', '', '.', 'a\\b', 'x\0y']) {
			await expect(store.writeDoc(bad, 'x')).rejects.toThrow()
			await expect(store.readDoc(bad)).rejects.toThrow()
		}
		await store.writeDoc('memo/ja/a.md', 'ok')
		expect(await store.readDoc('memo/ja/a.md')).toBe('ok')
		expect(await store.readDoc('./memo//ja/a.md')).toBe('ok') // 正規形に直して同じ文書
	})

	it('writeDoc は上書きできる。無い文書は null', async () => {
		const { store } = await make()
		expect(await store.readDoc('memo/none.md')).toBeNull()
		await store.writeDoc('memo/a.md', 'v1')
		await store.writeDoc('memo/a.md', 'v2')
		expect(await store.readDoc('memo/a.md')).toBe('v2')
	})

	it('list は直下の記録だけ。archived / memo / HISTORY.md / ほかのフォルダは数えない', async () => {
		const { store, root } = await make()
		await store.putEntry({ text: 'a', now: day })
		await store.writeDoc('memo/ja/page.md', 'memo')
		await store.writeDoc('HISTORY.md', 'history')
		await store.putAttachments([{ name: 'window.png', data: new Uint8Array([1]) }], { now: day })
		if (root) {
			await mkdir(join(root, 'test-260925'), { recursive: true })
			await writeFile(join(root, 'test-260925', '260925-001.md'), 'x')
			await mkdir(join(root, '261029-009.md'), { recursive: true }) // 名前だけ記録に見えるフォルダ
			await writeFile(join(root, 'notes.md'), 'x')
		}
		expect(await ids(store)).toEqual(['261029-001'])
		await store.archive('261029-001')
		expect(await ids(store)).toEqual([])
		expect(await ids(store, 'archived')).toEqual(['261029-001'])
		expect(await ids(store, 'all')).toEqual(['261029-001'])
	})

	it('archive: 移して末尾に対応メモを 1 行足す。もう一度呼んでも二重にしない', async () => {
		const { store } = await make()
		await store.putEntry({ text: '# a\n', now: day })
		const a = await store.archive('261029-001', 'docs を直した\n改行は 1 行にする', day)
		expect(a.state).toBe('archived')
		const text = (await store.read('261029-001')).text
		expect(text.startsWith('# a\n')).toBe(true)
		expect(text.endsWith('\n---\n対応メモ: docs を直した 改行は 1 行にする（2026-10-29）\n')).toBe(true)
		await store.archive('261029-001', 'もう一度')
		expect((await store.read('261029-001')).text).toBe(text)
		expect(await ids(store)).toEqual([])
	})

	it('2 桁のころの記録も読めて、番号の数えにも入る', async () => {
		const { store, root } = await make()
		if (!root) return // memory は 2 桁の名前を作れない
		await mkdir(root, { recursive: true })
		await writeFile(join(root, '261029-05.md'), 'old')
		expect(await ids(store)).toEqual(['261029-05'])
		expect((await store.read('261029-05')).text).toBe('old')
		expect((await store.putEntry({ text: 'new', now: day })).id).toBe('261029-006')
	})

	it('putAttachments: 参照を返し、不正な名前は投げずに error', async () => {
		const { store } = await make()
		const ok = await store.putAttachments([{ name: 'window.png', data: new Uint8Array([1, 2]) }, { name: 'element.png', data: new Uint8Array([3]) }], { now: day })
		expect(ok.error).toBeNull()
		expect(Object.keys(ok.refs).sort()).toEqual(['element.png', 'window.png'])
		for (const bad of ['../x.png', 'a/b.png', '.hidden', '']) {
			const r = await store.putAttachments([{ name: bad, data: new Uint8Array([1]) }], { now: day })
			expect(r.error).not.toBeNull()
			expect(r.refs).toEqual({})
		}
	})
})

describe('Store [file] だけの契約', () => {
	it('writeDoc は rename で書く: 上書きで inode が変わり、tmp が残らない', async () => {
		const root = join(await tmp(), 'feedback')
		const store = createFileStore(root)
		await store.writeDoc('memo/a.md', 'v1')
		const before = (await stat(join(root, 'memo/a.md'))).ino
		await store.writeDoc('memo/a.md', 'v2')
		const after = (await stat(join(root, 'memo/a.md'))).ino
		expect(after).not.toBe(before) // その場で書き換えたなら同じ inode のまま
		expect(await readdir(join(root, 'memo'))).toEqual(['a.md'])
	})

	it('シンボリックリンクで外へ出る書き込みを拒否', async () => {
		const base = await tmp()
		const outside = join(base, 'outside')
		const root = join(base, 'feedback')
		await mkdir(outside)
		await mkdir(root)
		await symlink(outside, join(root, 'link'))
		const store = createFileStore(root)
		await expect(store.writeDoc('link/x.md', 'x')).rejects.toThrow('外')
		await expect(readdir(outside)).resolves.toEqual([])
	})

	it('repoRoot を渡すと ref が repoRoot からの相対になる', async () => {
		const repo = await tmp()
		const store = createFileStore(join(repo, '.local/feedback'), repo)
		const e = await store.putEntry({ text: 'a', now: day })
		expect(e.ref).toBe('.local/feedback/261029-001.md')
		expect(await readFile(join(repo, e.ref), 'utf8')).toBe('a')
		const at = await store.putAttachments([{ name: 'window.png', data: new Uint8Array([1]) }], { now: day })
		expect(at.refs['window.png']).toBe('.local/feedback/attachments/20261029-100000/window.png')
	})

	it('同じ秒の添付は別のフォルダになる', async () => {
		const store = createFileStore(join(await tmp(), 'fb'))
		const f = [{ name: 'window.png', data: new Uint8Array([1]) }]
		const a = await store.putAttachments(f, { now: day })
		const b = await store.putAttachments(f, { now: day })
		expect(a.dir).not.toBe(b.dir)
	})
})
