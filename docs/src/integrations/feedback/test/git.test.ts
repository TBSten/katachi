import { execFileSync } from 'node:child_process'
import { mkdtemp, realpath, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterAll, describe, expect, it } from 'vitest'
import { sourceDirty } from '../server/git'

const dirs: string[] = []
afterAll(async () => {
	await Promise.all(dirs.map((d) => rm(d, { recursive: true, force: true })))
})

describe('git の pathspec', () => {
	it('ソースのパスの pathspec の magic（:(glob)・:/）を解釈せず、別のファイルの変更を拾わない', async () => {
		const repo = await realpath(await mkdtemp(join(tmpdir(), 'fb-git-')))
		dirs.push(repo)
		const env = { ...process.env, GIT_DIR: undefined, GIT_WORK_TREE: undefined } as NodeJS.ProcessEnv
		const git = (...a: string[]) => execFileSync('git', a, { cwd: repo, env })
		git('init', '-q')
		await writeFile(join(repo, 'a.mdx'), 'a')
		git('add', '.')
		git('-c', 'user.email=x@x', '-c', 'user.name=x', 'commit', '-qm', 'x')
		await writeFile(join(repo, 'a.mdx'), 'changed')
		expect(await sourceDirty(repo, 'a.mdx')).toBe(true)
		expect(await sourceDirty(repo, ':(glob)*.mdx')).toBe(false)
		expect(await sourceDirty(repo, ':/')).toBe(false)
	})
})

describe('GIT_WORK_TREE が設定されているとき（dev を写しで動かす）', () => {
	it('cwd が作業ツリーの外でも、作業ツリーの中で git を呼び、dirty を求められる', async () => {
		const real = await realpath(await mkdtemp(join(tmpdir(), 'fb-git-real-')))
		const copy = await realpath(await mkdtemp(join(tmpdir(), 'fb-git-copy-')))
		dirs.push(real, copy)
		const clean = { ...process.env, GIT_DIR: undefined, GIT_WORK_TREE: undefined } as NodeJS.ProcessEnv
		const git = (...a: string[]) => execFileSync('git', a, { cwd: real, env: clean })
		git('init', '-q')
		await writeFile(join(real, 'a.mdx'), 'a')
		git('add', '.')
		git('-c', 'user.email=x@x', '-c', 'user.name=x', 'commit', '-qm', 'x')
		await writeFile(join(real, 'a.mdx'), 'changed')
		const saved = { dir: process.env.GIT_DIR, tree: process.env.GIT_WORK_TREE }
		process.env.GIT_DIR = join(real, '.git')
		process.env.GIT_WORK_TREE = real
		try {
			expect(await sourceDirty(copy, 'a.mdx')).toBe(true)
		} finally {
			if (saved.dir === undefined) delete process.env.GIT_DIR
			else process.env.GIT_DIR = saved.dir
			if (saved.tree === undefined) delete process.env.GIT_WORK_TREE
			else process.env.GIT_WORK_TREE = saved.tree
		}
	})
})
