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

// Shift+クリックで 3 つの要素を選んだ記録。節は要素ごとに `### n.` の行で区切られる。
const g = (n: number): [string, string] => [`### ${n}.`, '']
const elements: SavePayload = {
	...element, kind: 'elements', comment: '3 つとも直したい',
	sections: {
		source: [['ページのソースファイル', 'docs/a.mdx'], g(1), ['ソース', 'docs/a.mdx:10-12'], g(2), ['ソース', 'docs/a.mdx:30-31'], g(3), ['ソース', '不明（この要素の近くに行番号が付いていません）']],
		page: page(),
		location: [g(1), ['XPath', '/html/body/p[1]'], ['近くの見出し', '## 一'], g(2), ['XPath', '/html/body/p[2]'], g(3), ['XPath', '/html/body/ul/li[1]'], ['CSSセレクタ', 'ul li']],
		content: [g(1), ['タグ', 'p'], g(2), ['タグ', 'p'], g(3), ['タグ', 'li']],
		...common,
	},
}

const cases: Array<[string, SavePayload]> = [['element', element], ['elements', elements], ['text', text], ['page', pageP], ['site', site]]

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

describe('複数の要素（kind: elements）', () => {
	const md = toMarkdown(elements, server)
	const { front, body } = split(md)
	it('frontmatter の source・xpath・selector・heading は要素ごとの配列。1 つだけのときは文字列のまま', () => {
		expect(front.xpath).toEqual(['/html/body/p[1]', '/html/body/p[2]', '/html/body/ul/li[1]'])
		// 値の無い要素は null。詰めないので、n 番目が `### n.` の要素に対応する。
		expect(front.source).toEqual(['docs/a.mdx:10-12', 'docs/a.mdx:30-31', null])
		expect(front.selector).toEqual([null, null, 'ul li'])
		expect(front.heading).toEqual(['## 一', null, null])
		expect(front).not.toHaveProperty('fragment')
		expect(split(toMarkdown(element, server)).front.source).toBe('docs/src/content/docs/ja/guides/layout.mdx:120-122')
	})
	it('先頭の要素だけソースも見出しも無くても、配列は要素の番号とずれない', () => {
		const p: SavePayload = {
			...elements,
			sections: {
				...elements.sections,
				source: [['ページのソースファイル', 'docs/a.mdx'], g(1), ['ソース', '不明（この要素の近くに行番号が付いていません）'], g(2), ['ソース', 'docs/a.mdx:30-31']],
				location: [g(1), ['XPath', '/html/body/header'], g(2), ['XPath', '/html/body/main/p[2]'], ['CSSセレクタ', 'main p'], ['近くの見出し', '## 二']],
				content: [g(1), ['タグ', 'header'], g(2), ['タグ', 'p']],
			},
		}
		const f = split(toMarkdown(p, server)).front
		expect(f.source).toEqual([null, 'docs/a.mdx:30-31'])
		expect(f.heading).toEqual([null, '## 二'])
		expect(f.selector).toEqual([null, 'main p'])
		expect(f.xpath).toEqual(['/html/body/header', '/html/body/main/p[2]'])
	})
	it('要約の 1 行は「対象: N 個の要素（file:行 ほか）」', () => {
		expect(body).toMatch(/^\n対象: 3 個の要素（docs\/a\.mdx:10-12 ほか）\n/)
	})
	it('選んだ内容・ページ上の場所・ソースの節は `### n.` で要素ごとに並ぶ', () => {
		for (const title of ['ソースの位置', 'ページ上の場所', '選んだ内容']) {
			const sec = new RegExp(`## ${title}\\n\\n([\\s\\S]*?)(?=\\n## |$)`).exec(md)![1]!
			expect([...sec.matchAll(/^### (\d)\.$/gm)].map((m) => m[1])).toEqual(['1', '2', '3'])
		}
		expect(md).not.toMatch(/- ### /)
	})
	it('コピーの文にも同じ形で入る', () => {
		expect(toCopyMarkdown(elements, created)).toContain('### 3.')
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

describe('redact と frontmatter', () => {
	const withHeading = (heading: string, secret = false): SavePayload => ({
		requestId: 'r', comment: secret ? 'Cookie: session=abc123def456' : 'c', kind: 'element',
		sections: { location: [['XPath', '/html/body/p'], ['近くの見出し', heading]] },
	})
	it('見出しに「Cookie: …」があっても frontmatter の引用符が壊れず、YAML として読める', () => {
		const md = toMarkdown(withHeading('## Cookie: の扱い'), server)
		expect(split(md).front.heading).toBe('## Cookie: ***')
	})
	it('秘密情報は frontmatter の値でも本文でも伏せる', () => {
		const md = toMarkdown(withHeading('## token=abcdef123456 の扱い', true), server)
		expect(md).not.toContain('abcdef123456')
		expect(md).not.toContain('abc123def456')
		expect(split(md).front.heading).toBe('## token=*** の扱い')
	})
})
