import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { checkReviewRequest, createReviewHandler, createReviewStore } from '../server/review'

let root: string
let dir: string
const SRC = 'docs/src/content/docs/ja/guides/x.mdx'

beforeEach(async () => {
	root = await mkdtemp(join(tmpdir(), 'review-'))
	dir = join(root, '.local/feedback/review')
	await mkdir(join(root, 'docs/src/content/docs/ja/guides'), { recursive: true })
	await mkdir(dir, { recursive: true })
	await writeFile(join(root, SRC), ['---', 'title: x', '---', '', '追加した行', '', 'import a.b.c', ''].join('\n'))
})
afterEach(() => rm(root, { recursive: true, force: true }))

const SET = `---
title: まとまり
---

## [p5] import の指摘
- file: ja/guides/x.mdx
- line: 5
- quote: import a.b.c

本文

## ファイルが無い
- file: ja/nope.mdx

## 外を指す
- file: ../../etc/passwd
`

describe('createReviewStore', () => {
	it('review/*.md を読み、省略形の file を本文の置き場から探し、quote で行を合わせる', async () => {
		await writeFile(join(dir, 's1.md'), SET)
		const { sets, items } = await createReviewStore(dir, root).load()
		expect(sets).toEqual([expect.objectContaining({ name: 's1', title: 'まとまり', file: '.local/feedback/review/s1.md' })])
		expect(items[0]).toMatchObject({ file: SRC, line: { start: 7, end: 7 }, written: { start: 5, end: 5 }, place: 'moved', priority: 5, status: 'open' })
		expect(items[1]).toMatchObject({ place: 'no-file' })
		expect(items[2]).toMatchObject({ place: 'no-file' })
		expect(sets[0]!.errors.join('\n')).toMatch(/ファイルがありません: ja\/nope.mdx/)
	})
	it('リポジトリの外のファイルは読まない', async () => {
		await writeFile(join(root, '..', 'outside-review-test.txt'), 'x').catch(() => {})
		await writeFile(join(dir, 's1.md'), '## 外\n- file: ../outside-review-test.txt\n- line: 1\n')
		const { items } = await createReviewStore(dir, root).load()
		expect(items[0]!.place).toBe('no-file')
	})
	it('フォルダが無ければ空（投げない）。.md 以外と使えない名前のファイルは読まない', async () => {
		expect(await createReviewStore(join(root, 'none'), root).load()).toEqual({ sets: [], items: [] })
		await writeFile(join(dir, 'memo.txt'), SET)
		await writeFile(join(dir, '.hidden.md'), SET)
		expect((await createReviewStore(dir, root).load()).sets).toEqual([])
	})
	it('解決済みにする → 戻す で元のファイルに戻る', async () => {
		await writeFile(join(dir, 's1.md'), SET)
		const store = createReviewStore(dir, root)
		const id = (await store.load()).items[0]!.id
		await store.resolve(id, 'なおした', new Date('2026-11-09T01:02:03Z'))
		const text = await readFile(join(dir, 's1.md'), 'utf8')
		expect(text).toContain('- quote: import a.b.c\n- status: resolved\n- resolved-at: 2026-11-09T01:02:03.000Z\n- note: なおした\n')
		expect((await store.load()).items[0]).toMatchObject({ status: 'resolved', note: 'なおした' })
		await store.reopen(id)
		expect(await readFile(join(dir, 's1.md'), 'utf8')).toBe(SET)
	})
	it('同時に何件も解決済みにしても、書き込みが追い越し合わない', async () => {
		const many = Array.from({ length: 20 }, (_, i) => `## 指摘 ${i}\n- file: ja/guides/x.mdx\n- line: 1\n`).join('\n')
		await writeFile(join(dir, 'm.md'), many)
		const store = createReviewStore(dir, root)
		const ids = (await store.load()).items.map((v) => v.id)
		await Promise.all(ids.map((id) => store.resolve(id, '')))
		const after = await store.load()
		expect(after.items.every((v) => v.status === 'resolved')).toBe(true)
	})
	it('agent が消す・差し戻す', async () => {
		await writeFile(join(dir, 's1.md'), SET)
		const store = createReviewStore(dir, root)
		const [a, b] = (await store.load()).items
		await store.sendBack(a!.id, 'まだ直っていない')
		expect((await store.load()).items[0]).toMatchObject({ status: 'open', aiNote: 'まだ直っていない' })
		await store.remove(b!.id)
		const left = (await store.load()).items.map((v) => v.title)
		expect(left).toEqual(['import の指摘', '外を指す'])
	})
	it('書き換えのあいだにファイルから消えた指摘は、分かる文で失敗する', async () => {
		await writeFile(join(dir, 's1.md'), SET)
		const store = createReviewStore(dir, root)
		const id = (await store.load()).items[0]!.id
		await writeFile(join(dir, 's1.md'), '## 別の指摘\n- file: x\n')
		await expect(store.resolve(id, '')).rejects.toThrow(/読み込み直してください/)
	})
})

describe('Review の受け口', () => {
	it('load は全部を返し、resolve / reopen は書き換えたあとの全部を返す', async () => {
		await writeFile(join(dir, 's1.md'), SET)
		const h = createReviewHandler(createReviewStore(dir, root))
		const loaded = await h({ requestId: 'r1', op: 'load' })
		expect(loaded).toMatchObject({ requestId: 'r1', ok: true })
		const id = (loaded as { items: { id: string }[] }).items[0]!.id
		expect(await h({ requestId: 'r2', op: 'resolve', id, note: 'x' })).toMatchObject({ requestId: 'r2', ok: true, items: [{ id, status: 'resolved' }, {}, {}] })
		expect(await h({ requestId: 'r3', op: 'reopen', id })).toMatchObject({ ok: true, items: [{ id, status: 'open' }, {}, {}] })
	})
	it('失敗は ok:false で返す（投げない）。requestId が無い依頼には答えない', async () => {
		const h = createReviewHandler(createReviewStore(dir, root))
		expect(await h({ requestId: 'r', op: 'resolve', id: 'nope/xx', note: '' })).toMatchObject({ ok: false, error: expect.stringMatching(/指摘のファイルがありません/) })
		expect(await h({ requestId: 'r', op: 'resolve', id: '../x/y', note: '' })).toMatchObject({ ok: false })
		expect(await h({ op: 'load' })).toBeNull()
	})
	it.each([
		[{ requestId: 'r', op: 'nope' }, /op が不正/],
		[{ requestId: 'r', op: 'resolve', id: 'a/b' }, /note/],
		[{ requestId: 'r', op: 'resolve', id: 'a/b', note: 'x'.repeat(5000) }, /長すぎ/],
		[{ requestId: 'r', op: 'reopen' }, /id/],
	])('形の違う依頼を断る %#', (req, re) => {
		const c = checkReviewRequest(req)
		expect(c.ok).toBe(false)
		expect((c as { reason: string }).reason).toMatch(re)
	})
})
