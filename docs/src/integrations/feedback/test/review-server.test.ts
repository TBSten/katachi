import { mkdtemp, mkdir, readFile, rm, stat, utimes, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { checkReviewRequest, createReviewHandler, createReviewStore, reviewToFeedback } from '../server/review'
import { toMarkdown } from '../core/record'
import type { SavePayload } from '../core/protocol'

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
		expect(await createReviewStore(join(root, 'none'), root).load()).toEqual({ sets: [], items: [], root, actions: [], actionErrors: [] })
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
	it('別のプロセス（dev サーバーと cli）が同じファイルを同時に書いても、相手の書き換えを消さない', async () => {
		const many = Array.from({ length: 10 }, (_, i) => `## 指摘 ${i}\n- file: ja/guides/x.mdx\n- line: 1\n`).join('\n')
		await writeFile(join(dir, 'm.md'), many)
		// 書き込みの順番待ちを共有しない 2 つの store で、別々のプロセスを真似る。
		const dev = createReviewStore(dir, root)
		const cli = createReviewStore(dir, root)
		const ids = (await dev.load()).items.map((v) => v.id)
		await Promise.all([...ids.slice(0, 5).map((id) => dev.resolve(id, '')), ...ids.slice(5).map((id) => cli.remove(id))])
		const after = await dev.load()
		expect(after.items.map((v) => [v.id, v.status])).toEqual(ids.slice(0, 5).map((id) => [id, 'resolved']))
	})
	it('落ちたプロセスが残した古い lock は消して書く。書いたあとは lock を残さない', async () => {
		await writeFile(join(dir, 's1.md'), SET)
		const store = createReviewStore(dir, root)
		const id = (await store.load()).items[0]!.id
		const lockPath = join(dir, 's1.md.lock')
		await writeFile(lockPath, '')
		const old = new Date(Date.now() - 60_000)
		await utimes(lockPath, old, old)
		await store.resolve(id, '')
		expect((await store.load()).items[0]!.status).toBe('resolved')
		await expect(stat(lockPath)).rejects.toThrow()
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
	it('add-action / remove-action は _actions.md を書き換え、load が選択肢と読めない行を返す', async () => {
		await writeFile(join(dir, 's1.md'), SET)
		const h = createReviewHandler(createReviewStore(dir, root))
		expect(await h({ requestId: 'r1', op: 'load' })).toMatchObject({ actions: [], actionErrors: [] })
		const added = await h({ requestId: 'r2', op: 'add-action', label: '後で', agent: 'v0.5 に積む', decision: '後で' })
		expect(added).toMatchObject({ ok: true, actions: [{ label: '後で', agent: 'v0.5 に積む', decision: '後で' }] })
		expect(await readFile(join(dir, '_actions.md'), 'utf8')).toContain('## 後で\n- agent: v0.5 に積む\n- decision: 後で\n')
		// _actions.md は指摘のまとまりとして読まない
		expect((added as { sets: unknown[] }).sets).toHaveLength(1)
		expect(await h({ requestId: 'r3', op: 'add-action', label: '後で', agent: '' })).toMatchObject({ ok: false, error: expect.stringMatching(/もうあります/) })
		expect(await h({ requestId: 'r4', op: 'add-action', label: '直さない', agent: '' })).toMatchObject({ ok: false, error: expect.stringMatching(/初めから/) })
		expect(await h({ requestId: 'r4b', op: 'update-action', from: '後で', label: '後で', agent: 'v0.5 で見直す' })).toMatchObject({ ok: true, actions: [{ label: '後で', agent: 'v0.5 で見直す' }] })
		expect(await h({ requestId: 'r5', op: 'remove-action', label: '後で' })).toMatchObject({ ok: true, actions: [] })
		expect(await h({ requestId: 'r6', op: 'remove-action', label: '後で' })).toMatchObject({ ok: false })
		await writeFile(join(dir, '_actions.md'), '## a:b\n')
		expect(await h({ requestId: 'r7', op: 'load' })).toMatchObject({ actions: [], actionErrors: [expect.stringMatching(/^_actions\.md: L1: /)] })
		await rm(join(dir, '_actions.md'))
		expect(await h({ requestId: 'r8', op: 'remove-action', label: 'x' })).toMatchObject({ ok: false, error: expect.stringMatching(/ありません/) })
	})
	it('send は Feedback の記録を作り、指摘に送った印を付けて解決済みにする。unsend は戻して記録を archived に移す', async () => {
		await writeFile(join(dir, 's1.md'), SET)
		const sent: SavePayload[] = []
		const unsent: string[] = []
		const h = createReviewHandler(createReviewStore(dir, root), {
			send: async (p) => { sent.push(p); return '261110-001.md' },
			unsend: async (r) => { unsent.push(r) },
		})
		const id = ((await h({ requestId: 'r1', op: 'load' })) as { items: { id: string }[] }).items[0]!.id
		const r = await h({ requestId: 'r2', op: 'send', id, note: '表現を直して', page: 'http://localhost:4321/katachi/ja/guides/x/' })
		expect(r).toMatchObject({ ok: true, sent: '261110-001', items: [{ id, status: 'resolved', note: 'Feedback に送った: 261110-001 — 表現を直して' }, {}, {}] })
		expect(sent[0]).toMatchObject({ kind: 'element', sections: { source: [['ソース', `${SRC}:7`]], content: [['Review の指摘', id]] } })
		expect(sent[0]!.comment).toBe('直してほしい: import の指摘\n\n表現を直して\n\n本文')
		expect(await h({ requestId: 'r3', op: 'unsend', id, record: '261110-001' })).toMatchObject({ ok: true, items: [{ id, status: 'open' }, {}, {}] })
		expect(unsent).toEqual(['261110-001'])
		expect(await readFile(join(dir, 's1.md'), 'utf8')).toBe(SET)
	})
	it('Feedback の置き場が無ければ send は失敗する（投げない）。record の形も確かめる', async () => {
		await writeFile(join(dir, 's1.md'), SET)
		const h = createReviewHandler(createReviewStore(dir, root))
		expect(await h({ requestId: 'r', op: 'send', id: 's1/x', note: '', page: '' })).toMatchObject({ ok: false, error: expect.stringMatching(/Feedback の置き場/) })
		expect(checkReviewRequest({ requestId: 'r', op: 'unsend', id: 'a/b', record: '../x' }).ok).toBe(false)
	})
	it('記録の Markdown に、元の指摘の id とソースの位置が入る（cli の archive が id を拾う）', () => {
		const md = toMarkdown(reviewToFeedback({ id: 's1/abc', set: 's1', title: '見出し', priority: 3, file: SRC, line: { start: 5, end: 6 }, written: null, place: 'ok', status: 'open', body: '## 本文の見出し\n本文' }, '', 'http://x/'), { created: new Date('2026-11-10T00:00:00Z'), id: '261110-001' })
		expect(md).toMatch(/^- Review の指摘: s1\/abc$/m)
		expect(md).toContain(`source: "${SRC}:5-6"`)
		expect(md).toContain('# 直してほしい: 見出し')
		expect(md).not.toMatch(/^## 本文の見出し/m)
	})
	it('推奨度の名前は、初めからのボタンと人が足した選択肢だけ。無い名前は読めない指摘に出す。impact は記録に入る', async () => {
		await writeFile(join(dir, 's1.md'), ['## 指摘', '- file: ja/guides/x.mdx', '- impact: あり', '- recommend: 直してもらう 8, 解決済みにする 1, 後で 5, 謎 3', ''].join('\n'))
		await writeFile(join(dir, '_actions.md'), '## 後で\n- agent: 積む\n')
		const { sets, items } = await createReviewStore(dir, root).load()
		expect(items[0]).toMatchObject({ impact: { user: true }, recommend: [{ name: '直してもらう', score: 8 }, { name: '解決済みにする', score: 1 }, { name: '後で', score: 5 }, { name: '謎', score: 3 }] })
		expect(sets[0]!.errors).toEqual([expect.stringMatching(/^L1: 推奨度の「謎」は popup の選択肢にありません/)])
		const fb = reviewToFeedback({ ...items[0]!, impact: { user: false, reason: '画面に出ない' } }, '', 'http://x/')
		expect(fb.sections.content).toContainEqual(['ユーザ影響', 'なし（画面に出ない）'])
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
		[{ requestId: 'r', op: 'add-action', label: '' }, /label/],
		[{ requestId: 'r', op: 'add-action', label: 'x' }, /agent/],
		[{ requestId: 'r', op: 'add-action', label: 'x', agent: '', decision: 1 }, /decision/],
		[{ requestId: 'r', op: 'remove-action' }, /label/],
		[{ requestId: 'r', op: 'update-action', label: 'x', agent: '' }, /from/],
	])('形の違う依頼を断る %#', (req, re) => {
		const c = checkReviewRequest(req)
		expect(c.ok).toBe(false)
		expect((c as { reason: string }).reason).toMatch(re)
	})
})
