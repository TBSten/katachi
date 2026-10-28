import { mkdtemp, mkdir, utimes, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { buildInfo, createBuildContext, integrationHash, isStale, packageVersion } from '../server/build-info'

describe('isStale', () => {
	it('ページを開いた後に更新されていれば true', () => expect(isStale(2000, 1000)).toBe(true))
	it('開く前の更新なら false', () => expect(isStale(1000, 2000)).toBe(false))
	it('同時刻は false', () => expect(isStale(1000, 1000)).toBe(false))
	it('開いた時刻が無ければ判定しない', () => {
		expect(isStale(1000, undefined)).toBeUndefined()
		expect(isStale(1000, Number.NaN)).toBeUndefined()
	})
})

describe('buildInfo', () => {
	const setup = async () => {
		const root = await mkdtemp(join(tmpdir(), 'fb-build-'))
		await mkdir(join(root, 'docs'), { recursive: true })
		const file = 'docs/a.mdx'
		await writeFile(join(root, file), 'x')
		return { root, file }
	}
	const ctx = { startedAt: new Date('2026-10-29T00:00:00Z'), startHash: '' }

	it('mtime がページを開いた時刻より新しければ stale: true', async () => {
		const { root, file } = await setup()
		await utimes(join(root, file), new Date(), new Date('2026-10-29T01:00:00Z'))
		const opened = Date.parse('2026-10-29T00:30:00Z')
		const info = await buildInfo(ctx, { repoRoot: root, sourceFile: file, pageOpenedAt: opened })
		expect(info.stale).toBe(true)
		expect(info.section.find(([l]) => l === 'ソースの古さ')?.[1]).toContain('変わっています')
	})

	it('古い mtime なら stale: false', async () => {
		const { root, file } = await setup()
		await utimes(join(root, file), new Date(), new Date('2026-10-29T00:10:00Z'))
		const info = await buildInfo(ctx, { repoRoot: root, sourceFile: file, pageOpenedAt: Date.parse('2026-10-29T00:30:00Z') })
		expect(info.stale).toBe(false)
	})

	it('ソースが無い・読めないときは stale を決めない', async () => {
		const { root } = await setup()
		expect((await buildInfo(ctx, { repoRoot: root, sourceFile: null, pageOpenedAt: 1 })).stale).toBeUndefined()
		expect((await buildInfo(ctx, { repoRoot: root, sourceFile: 'nope.mdx', pageOpenedAt: 1 })).stale).toBeUndefined()
	})

	it('版・Node・起動時刻・ハッシュの行を持つ', async () => {
		const { root } = await setup()
		const real = await createBuildContext()
		const { section } = await buildInfo(real, { repoRoot: root, sourceFile: null })
		const labels = section.map(([l]) => l)
		expect(labels).toEqual(expect.arrayContaining(['Astro', 'Node', 'dev サーバーの起動', '統合のハッシュ']))
		expect(labels).not.toContain('統合の古さ')
	})

	it('起動時のハッシュと違えば統合の古さを書く', async () => {
		const { root } = await setup()
		const { section } = await buildInfo({ ...ctx, startHash: 'deadbeef0000' }, { repoRoot: root, sourceFile: null })
		expect(section.map(([l]) => l)).toContain('統合の古さ')
	})
})

describe('hash・版', () => {
	it('ハッシュは 12 桁で、内容が変わると変わる', async () => {
		const dir = await mkdtemp(join(tmpdir(), 'fb-hash-'))
		await writeFile(join(dir, 'a.ts'), '1')
		const a = await integrationHash(dir)
		expect(a).toMatch(/^[0-9a-f]{12}$/)
		await writeFile(join(dir, 'a.ts'), '2')
		expect(await integrationHash(dir)).not.toBe(a)
	})
	it('astro の版が取れる', async () => expect(await packageVersion('astro')).toMatch(/^\d+\./))
})
