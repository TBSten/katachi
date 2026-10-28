// 保存（saveFeedback）の後片付け。添付は記録より先に置くので、記録が書けなかったら添付を残さない。
import { mkdir, mkdtemp, readdir, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterAll, describe, expect, it } from 'vitest'
import { checkPayload, LIMITS } from '../core/limits'
import type { SavePayload } from '../core/protocol'
import { createMemoryStore } from '../server/memory-store'
import { saveFeedback } from '../server/save'
import { createFileStore } from '../server/store'

const dirs: string[] = []
afterAll(async () => {
	await Promise.all(dirs.map((d) => rm(d, { recursive: true, force: true })))
})

const now = new Date(2026, 9, 29, 10)
const payload = (): SavePayload => ({
	requestId: 'r',
	comment: 'c',
	kind: 'element',
	sections: { screenshot: [['撮影', '…']] },
	attachments: [{ name: 'window.png', mime: 'image/png', base64: Buffer.from('PNGDATA').toString('base64') }],
})
const build = { startedAt: now, startHash: '' }

describe('保存が失敗したときの添付', () => {
	it('その日の番号を使い切って保存が失敗したら、先に置いた添付も消す', async () => {
		const repo = await mkdtemp(join(tmpdir(), 'fb-save-'))
		dirs.push(repo)
		const root = join(repo, '.local/feedback')
		await mkdir(root, { recursive: true })
		await writeFile(join(root, '261029-999.md'), 'last')
		const store = createFileStore(root, repo)
		await expect(saveFeedback(store, payload(), { repoRoot: repo, build }, now)).rejects.toThrow('使い切りました')
		expect(await readdir(join(root, 'attachments'))).toEqual([])
	})
	it('記録の組み立てが投げても、添付を消す', async () => {
		const repo = await mkdtemp(join(tmpdir(), 'fb-save-'))
		dirs.push(repo)
		const store = createFileStore(join(repo, '.local/feedback'), repo)
		// comment が文字列でないと toMarkdown が投げる
		const bad = { ...payload(), comment: undefined } as unknown as SavePayload
		await expect(saveFeedback(store, bad, { repoRoot: repo, build }, now)).rejects.toThrow()
		expect(await readdir(join(repo, '.local/feedback/attachments'))).toEqual([])
	})
	it('メモリの Store でも添付が残らない', async () => {
		const store = createMemoryStore()
		store.putEntry = async () => {
			throw new Error('書けない')
		}
		await expect(saveFeedback(store, payload(), { repoRoot: tmpdir(), build }, now)).rejects.toThrow('書けない')
		expect(store.attachments.size).toBe(0)
	})
	it('成功すれば添付は残る', async () => {
		const repo = await mkdtemp(join(tmpdir(), 'fb-save-'))
		dirs.push(repo)
		const store = createFileStore(join(repo, '.local/feedback'), repo)
		await saveFeedback(store, payload(), { repoRoot: repo, build }, now)
		expect(await readdir(join(repo, '.local/feedback/attachments'))).toHaveLength(1)
	})
})

describe('removeAttachments は attachments/ の直下の 1 フォルダだけ消す', () => {
	it('外・attachments/ 自身・入れ子は消さない', async () => {
		const repo = await mkdtemp(join(tmpdir(), 'fb-save-'))
		dirs.push(repo)
		const root = join(repo, '.local/feedback')
		const store = createFileStore(root, repo)
		await writeFile(join(repo, 'keep.txt'), 'k')
		const put = await store.putAttachments([{ name: 'a.png', data: new Uint8Array([1]) }], { now })
		await store.removeAttachments('..')
		await store.removeAttachments('.')
		await store.removeAttachments('.local/feedback/attachments')
		await store.removeAttachments(`${put.dir}/a.png`)
		expect(await readdir(repo)).toContain('keep.txt')
		expect(await readdir(join(root, 'attachments', ...put.dir!.split('/').slice(-1)))).toEqual(['a.png'])
		await store.removeAttachments(put.dir!)
		expect(await readdir(join(root, 'attachments'))).toEqual([])
	})
})

describe('添付の合計の上限', () => {
	const big = (bytes: number) => 'A'.repeat(Math.ceil(bytes / 3) * 4)
	const withAttachments = (n: number, bytes: number) => ({
		...payload(),
		attachments: Array.from({ length: n }, (_, i) => ({ name: `a${i}.png`, mime: 'image/png' as const, base64: big(bytes) })),
	})
	it('1 件ごとの上限の範囲でも、合計が上限を超えたら断る', () => {
		expect(checkPayload(withAttachments(8, LIMITS.attachmentBytes - 10)).ok).toBe(false)
		const r = checkPayload(withAttachments(4, LIMITS.attachmentBytes - 10))
		expect(r).toEqual({ ok: false, reason: expect.stringContaining('合計') })
	})
	it('合計が上限の内側なら通す', () => {
		expect(checkPayload(withAttachments(3, 9_000_000)).ok).toBe(true)
	})
})
