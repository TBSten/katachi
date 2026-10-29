import { existsSync, mkdtempSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { createRequire } from 'node:module'
import { dirname, join, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import feedback from '../index'
import { UNBUNDLED_DEPS, devViteConfig } from '../server/optimize-deps'

/** 統合の astro:config:setup を command で呼び、updateConfig に渡った設定を集める。 */
function setupWith(command: 'dev' | 'build'): unknown[] {
	const updates: unknown[] = []
	const hook = feedback().hooks['astro:config:setup'] as (p: unknown) => void
	hook({
		command,
		updateConfig: (c: unknown) => updates.push(c),
		injectScript: () => {},
		addDevToolbarApp: () => {},
	})
	return updates
}

describe('dev の Vite の optimizeDeps', () => {
	it('dev では modern-screenshot を事前バンドルから外す（include には入れない）', () => {
		const updates = setupWith('dev')
		expect(updates).toEqual([{ vite: devViteConfig() }])
		const optimizeDeps = devViteConfig().optimizeDeps as { exclude: string[]; include?: string[] }
		expect(optimizeDeps.exclude).toContain('modern-screenshot')
		expect(optimizeDeps.include ?? []).not.toContain('modern-screenshot')
	})
	it('build では Vite の設定を変えない', () => {
		expect(setupWith('build')).toEqual([])
	})
})

// 事前バンドルから外した依存は、ブラウザが node_modules のファイルをそのまま読む。
// ほかの package の import（bare import）や CommonJS が混ざると、ブラウザで読めなくなる。
const require = createRequire(import.meta.url)
// `from 'x'`・`import 'x'`・`import('x')`。CSS の `@import "…"` やプロパティの `.import` は除く。
const SPECIFIER = /(?<![@\w$.])(?:from|import)\s*\(?\s*(['"])([^'"\s]+)\1/g

/** package の exports の import 条件で、ブラウザ向けの ESM の入口を引く。 */
function esmEntry(spec: string): string {
	const [scopeOrName, ...rest] = spec.split('/')
	const name = scopeOrName.startsWith('@') ? `${scopeOrName}/${rest.shift()}` : scopeOrName
	const sub = rest.length ? `./${rest.join('/')}` : '.'
	const pkgJson = require.resolve(`${name}/package.json`)
	const exp = JSON.parse(readFileSync(pkgJson, 'utf8')).exports[sub]
	const target = typeof exp === 'string' ? exp : (exp.browser ?? exp.import ?? exp.default)
	return join(dirname(pkgJson), target)
}

/** 入口から相対 import を辿り、bare import と require を集める。 */
function bareImports(entry: string): string[] {
	const found: string[] = []
	const seen = new Set<string>()
	const walk = (file: string) => {
		if (seen.has(file)) return
		seen.add(file)
		const code = readFileSync(file, 'utf8')
		if (/\brequire\s*\(/.test(code) || /\bmodule\.exports\b/.test(code)) found.push(`${file}: CommonJS`)
		for (const [, , s] of code.matchAll(SPECIFIER)) {
			if (!s.startsWith('.')) found.push(`${file}: ${s}`)
			else {
				const next = resolve(dirname(file), s)
				walk(existsSync(next) ? next : `${next}.js`)
			}
		}
	}
	walk(entry)
	return found
}

describe.each(UNBUNDLED_DEPS)('%s は事前バンドル無しでブラウザが読める', (dep) => {
	it('入口は ESM で、ほかの package を import しない', () => {
		const entry = esmEntry(dep)
		expect(existsSync(entry), entry).toBe(true)
		expect(bareImports(entry)).toEqual([])
	})
})

describe('bare import の探し方（上の確かめが空振りしていないこと）', () => {
	it('相対 import を辿った先の bare import・動的 import・CommonJS を拾い、CSS の @import は拾わない', () => {
		const dir = mkdtempSync(join(tmpdir(), 'fb-optimize-deps-'))
		writeFileSync(join(dir, 'entry.mjs'), "import { a } from './a.mjs'\nconst css = '@import \"x.css\";'\nexport { a, css }\n")
		writeFileSync(join(dir, 'a.mjs'), "export const a = () => import('lodash-es')\nimport 'side-effect'\nexport { b } from \"./b\"\n")
		writeFileSync(join(dir, 'b.js'), 'module.exports = {}\n')
		expect(bareImports(join(dir, 'entry.mjs')).map((f) => f.slice(dir.length + 1))).toEqual(['a.mjs: lodash-es', 'a.mjs: side-effect', 'b.js: CommonJS'])
	})
})

// astro/toolbar は Astro が include に足すので外せない。ブラウザで動くコードが値として import すると、deps/ が作り直されたときに 504 になる。
describe('ブラウザで動くコードは astro/toolbar を値として import しない（型だけ）', () => {
	const root = new URL('..', import.meta.url).pathname
	const sources = (readdirSync(root, { recursive: true }) as string[]).filter((f) => f.endsWith('.ts') && !f.startsWith('test'))
	it.each(sources)('%s', (file) => {
		const code = readFileSync(join(root, file), 'utf8')
		expect(code).not.toMatch(/import\s+(?!type\b)[^;]*?from\s*['"]astro\/toolbar['"]/)
		expect(code).not.toMatch(/import\s*\(\s*['"]astro\/toolbar['"]/)
	})
})
