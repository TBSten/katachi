// dev のときだけ、本文の要素に元の Markdown / MDX の行番号（data-fb-src）を付ける hast プラグイン。
// Astro 7 の Markdown は Sätteri なので、rehype ではなく Sätteri の hast プラグインとして processor に足す。
// satteri は docs の直接の依存ではないため import せず、プラグインを素のオブジェクトで書く（型は最小限に自前で持つ）。
import { relative, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import { SRC_ATTR } from './protocol'

/** 行番号を付ける要素。見出し・段落・リスト・表・コードブロック・引用。 */
const TAGS = ['h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'p', 'ul', 'ol', 'li', 'table', 'tr', 'blockquote', 'pre', 'dl', 'dt', 'dd']
/** Starlight の部品（MDX の JSX 要素）。属性を受けない部品もあるが、渡すだけなら害は無い。 */
const COMPONENTS = ['Aside', 'Tabs', 'TabItem', 'Steps', 'Card', 'CardGrid', 'LinkCard', 'FileTree']

type Pos = { start: { line: number }; end: { line: number } }
type Node = { position?: Pos; tagName?: string; properties?: Record<string, unknown>; name?: string }
type Ctx = {
	fileURL?: URL
	data: Record<string, any>
	setProperty(node: Node, key: string, value: unknown): void
}

/** `docs/src/content/...:12-14`。パスはリポジトリ直下からの相対。 */
function format(repoRoot: string, fileURL: URL | undefined, pos: Pos): string | null {
	if (!fileURL || fileURL.protocol !== 'file:') return null
	const path = relative(repoRoot, fileURLToPath(fileURL)).split(sep).join('/')
	const { start, end } = pos
	return `${path}:${start.line === end.line ? start.line : `${start.line}-${end.line}`}`
}

/**
 * 付けるプラグインを返す。EC（expressive-code）は `pre` を別の木で置き換えて付けた属性を消すので、
 * `pre` の位置は ctx.data に順番に控えておき、置き換えのあとに走る {@link createCodeBlockPlugin} が付け直す。
 */
export function createSourcePlugin(repoRoot: string) {
	const tag = (node: Node, ctx: Ctx) => {
		if (!node.position) return
		const value = format(repoRoot, ctx.fileURL, node.position)
		if (value) ctx.setProperty(node, SRC_ATTR, value)
		return value
	}
	return {
		name: 'katachi-feedback-source',
		options: { position: true },
		element: {
			filter: TAGS,
			visit(node: Node, ctx: Ctx) {
				const value = tag(node, ctx)
				if (node.tagName === 'pre' && value) (ctx.data.fbPre ??= []).push(value)
			},
		},
		mdxJsxFlowElement: { filter: COMPONENTS, visit: (node: Node, ctx: Ctx) => void tag(node, ctx) },
	}
}

/** EC が作ったコードブロック（`.expressive-code`）に、控えておいた `pre` の位置を順番に付け直す。 */
export function createCodeBlockPlugin() {
	return {
		name: 'katachi-feedback-code-block',
		element: {
			filter: ['div'],
			visit(node: Node, ctx: Ctx) {
				const cls = node.properties?.className
				const list = Array.isArray(cls) ? cls : typeof cls === 'string' ? cls.split(' ') : []
				if (!list.includes('expressive-code')) return
				const value = (ctx.data.fbPre as string[] | undefined)?.[(ctx.data.fbPreIdx = (ctx.data.fbPreIdx ?? 0) + 1) - 1]
				if (value) ctx.setProperty(node, SRC_ATTR, value)
			},
		},
	}
}

const escapeHtml = (s: string) =>
	s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!)

/**
 * astro-mermaid は ```mermaid を mdast の段階で `<pre class="mermaid">` の html ノードに差し替えるので、
 * hast には素の html として届き、属性を付けられない。そこで mermaid より前に走る mdast プラグインで、
 * 同じ形の html ノードに data-fb-src を付けて差し替える（astro-mermaid と同じエスケープ）。
 * 差し替えるのは行の分かる mermaid だけ。
 */
export function createMermaidPlugin(repoRoot: string) {
	return {
		name: 'katachi-feedback-mermaid',
		options: { position: true },
		code(node: { lang?: string; value: string; position?: Pos }, ctx: Ctx & { replaceNode(n: unknown, r: unknown): void }) {
			if (node.lang !== 'mermaid' || !node.position) return
			const src = format(repoRoot, ctx.fileURL, node.position)
			if (!src) return
			ctx.replaceNode(node, { type: 'html', value: `<pre class="mermaid" ${SRC_ATTR}="${src}">${escapeHtml(node.value)}</pre>` })
		},
	}
}
