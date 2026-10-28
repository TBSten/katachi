// 記録（schema 1）の golden テスト。要素・テキスト・ページ・サイトの 4 種 × 節が全部オン／一部オフ。
import { load } from 'js-yaml'
import { describe, expect, it } from 'vitest'
import { GIT_PLACEHOLDER, type SavePayload, type Section, type SectionKey } from '../core/protocol'
import { escapeHeadings, toCopyMarkdown, toMarkdown } from '../core/record'

const created = new Date('2026-10-29T03:12:45.000Z')

const page = (extra: Section = []): Section => [
	['URL', 'http://localhost:4321/katachi/ja/guides/layout/'],
	['title', 'Layout'],
	['言語', 'ja'],
	['版', '0.2（現行）'],
	...extra,
]
const common: Partial<Record<SectionKey, Section>> = {
	screen: [['viewport', '1280 x 800']],
	state: [['テーマ', 'ライト（自動（OS に従う））']],
	browser: [['UserAgent', 'UA']],
	datetime: [['日時', created.toISOString()]],
	build: [['情報', GIT_PLACEHOLDER]],
	git: [['commit', GIT_PLACEHOLDER]],
}
const located: Section = [['XPath', '/html/body/main/p[3]'], ['CSSセレクタ', 'main p:nth-of-type(3)'], ['近くの見出し', '## パスを定数に切り出す']]

const element: SavePayload = {
	requestId: 'r', comment: '# 見出し風の1行目\n本文\n  ## これも見出しに見える', kind: 'element',
	sections: {
		source: [['ソース', 'docs/src/content/docs/ja/guides/layout.mdx:120-122']],
		page: page(), location: located,
		content: [['タグ', 'p'], ['テキストの全文', '意味のある単位でパスを定数にする']],
		...common,
	},
}
const text: SavePayload = {
	...element, kind: 'text',
	sections: {
		...element.sections,
		location: [...located, ['テキストフラグメントURL', 'http://localhost:4321/katachi/ja/x/#:~:text=a']],
		content: [['選んだテキスト', '定数に切り出す'], ['ブロックの全文', '意味のある単位で']],
	},
}
const pageP: SavePayload = {
	requestId: 'r', comment: 'このページ全体', kind: 'page', target: 'page',
	sections: { page: page([['ページのソースファイル', 'docs/src/content/docs/ja/guides/layout.mdx']]), ...common },
}
const site: SavePayload = { ...pageP, kind: 'site', target: 'site' }

const cases: Array<[string, SavePayload]> = [['element', element], ['text', text], ['page', pageP], ['site', site]]

const split = (md: string) => {
	const m = /^---\n([\s\S]*?)\n---\n/.exec(md)!
	return { front: load(m[1]!) as Record<string, unknown>, body: md.slice(m[0].length) }
}
const server = { created, id: '261029-004', dirty: false, stale: false }

describe.each(cases)('記録 schema 1 [%s]', (kind, payload) => {
	it('全部オン: frontmatter が YAML として読め、キーがそろう', () => {
		const { front, body } = split(toMarkdown(payload, server))
		expect(front).toMatchObject({ schema: 1, id: '261029-004', kind, created: created.toISOString(), dirty: false, stale: false, omitted: [] })
		expect(front.page).toBe('http://localhost:4321/katachi/ja/guides/layout/')
		expect(front.lang).toBe('ja')
		expect(body).toMatch(/^\n対象: /)
		expect(body).toContain('案内: katachi の docs サイト')
		expect(body).not.toContain('載せなかった情報')
	})

	it('一部オフ: omitted と本文の「載せなかった情報」に出て、節は出ない', () => {
		const sections = { ...payload.sections }
		delete sections.browser
		delete sections.state
		const md = toMarkdown({ ...payload, sections, omitted: ['browser', 'state'] }, server)
		const { front, body } = split(md)
		expect(front.omitted).toEqual(['state', 'browser'])
		expect(body).toContain('（載せなかった情報: サイトの状態、ブラウザ）')
		expect(md).not.toContain('## ブラウザ')
	})

	it('節の並びは SECTION_KEYS どおり', () => {
		const md = toMarkdown(payload, server)
		const heads = [...md.matchAll(/^## (.+)$/gm)].map((m) => m[1])
		const order = ['ソースの位置', 'ページ', 'ページ上の場所', '選んだ内容', '画面', 'サイトの状態', 'ブラウザ', '日時', 'ビルド', 'git']
		expect(heads).toEqual(order.filter((h) => heads.includes(h)))
	})

	it('コピーの文は、サーバーが付けるもの（id・commit・dirty・stale・ビルド・git の節）を除いて保存と同じ', () => {
		const saved = toMarkdown(payload, { ...server, dirty: undefined, stale: undefined })
		const copied = toCopyMarkdown(payload, created)
		const strip = (md: string) =>
			md
				.replace(/^id: .*\n/m, '')
				.replace(/\n## ビルド\n[\s\S]*?(?=\n## |$)/, '')
				.replace(/\n## git\n[\s\S]*$/, '')
				.trimEnd()
		expect(strip(copied)).toBe(strip(saved))
		expect(copied).not.toContain('## git')
		expect(copied).not.toContain('## ビルド')
		expect(split(copied).front.id).toBeUndefined()
	})
})

describe('記録の細部', () => {
	it('コメント中の行頭の # は見出しにならない', () => {
		expect(escapeHeadings('# a\n  ## b\nx # c')).toBe('\\# a\n  \\## b\nx # c')
		const md = toMarkdown(element, server)
		expect(md).toContain('# \\# 見出し風の1行目')
		expect(md.split('\n').filter((l) => /^#+ /.test(l) && !l.startsWith('# \\#') && !l.startsWith('## '))).toEqual([])
	})

	it('stale のとき本文に注意の 1 行が出る', () => {
		const md = toMarkdown(element, { ...server, stale: true })
		expect(split(md).front.stale).toBe(true)
		expect(md).toContain('注意: ページを開いた後にソースのファイルが変わっています')
	})

	it('dirty・stale・id・attachments は渡されたときだけ書く', () => {
		const { front } = split(toMarkdown(element, { created }))
		expect(front).not.toHaveProperty('dirty')
		expect(front).not.toHaveProperty('stale')
		expect(front).not.toHaveProperty('id')
		expect(front).not.toHaveProperty('attachments')
		expect(split(toMarkdown(element, { created, attachments: ['a/window.png'] })).front.attachments).toEqual(['a/window.png'])
	})

	it('redact が文全体に掛かる', () => {
		const md = toMarkdown({ ...element, comment: 'Authorization: Bearer abcdefghijklmnop を貼った' }, server)
		expect(md).not.toContain('abcdefghijklmnop')
		expect(split(md).front.schema).toBe(1)
	})

	it('引用符や改行を含む値でも frontmatter が壊れない', () => {
		const p = { ...element, sections: { ...element.sections, location: [['XPath', 'a"b\\c'], ['近くの見出し', '## "x": y']] as Section } }
		expect(split(toMarkdown(p, server)).front.xpath).toBe('a"b\\c')
	})
})
