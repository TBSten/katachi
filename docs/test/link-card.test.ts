import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { describe, expect, it } from 'vitest'

// <LinkCard> の title / description にはコード表記（バッククォート・<code>）を使わない。
// カードは見出しと説明の文で、コードは本文で見せる。
const docsRoot = join(import.meta.dirname, '..', 'src', 'content', 'docs')

const walk = (dir: string): string[] =>
	readdirSync(dir).flatMap((name) => {
		const path = join(dir, name)
		return statSync(path).isDirectory() ? walk(path) : path.endsWith('.mdx') ? [path] : []
	})

// 過去の版（0.2/）は凍結しているので対象にしない。
const files = walk(docsRoot).filter((f) => !relative(docsRoot, f).startsWith('0.2/'))

export const findCodeInLinkCards = (source: string): string[] =>
	[...source.matchAll(/<LinkCard\b[\s\S]*?\/>/g)]
		.map((m) => m[0])
		.filter((tag) => /`|<code\b/.test(tag))

describe('LinkCard のコード表記禁止', () => {
	it('検出器はバッククォートと <code> を見つける', () => {
		expect(findCodeInLinkCards('<LinkCard title="`a`" href="/x/" />')).toHaveLength(1)
		expect(findCodeInLinkCards('<LinkCard\n title="a"\n description="<code>b</code>"\n href="/x/"\n/>')).toHaveLength(1)
		expect(findCodeInLinkCards('<LinkCard title="a" description="b" href="/x/" />')).toHaveLength(0)
	})

	it.each(files.map((f) => [relative(docsRoot, f), f]))('%s の LinkCard にコード表記がない', (_name, file) => {
		expect(findCodeInLinkCards(readFileSync(file, 'utf8'))).toEqual([])
	})
})
