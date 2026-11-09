// 指摘の本文（Markdown の小さな部分集合）を DOM にする（ブラウザ側）。innerHTML は使わず、文字はすべて textContent で入れる。
// 扱う形: 段落・箇条書き（入れ子は字下げ 2 つ以上）・番号付きリスト・引用・コードフェンス・インラインの `code`（`` ` `` の数は任意）・**太字**・[リンク](URL)。

const LIST = /^(\s*)([-*+]|\d+[.)])\s+(.*)$/
const FENCE = /^\s*(```+|~~~+)(.*)$/

/** 開いてよいリンク先（http(s)・ページ内・相対）。javascript: などは文字のまま出す。 */
export const safeHref = (href: string) => /^(https?:\/\/|\/|#|\.{0,2}\/)/i.test(href.trim()) && !/^\s*javascript:/i.test(href)

/** インラインの書式。`code` を先に切り出し、残りから **太字** と [リンク](URL) を拾う。 */
export function renderInline(text: string, doc: Document = document): DocumentFragment {
	const out = doc.createDocumentFragment()
	const code = /(`+)([\s\S]*?[^`])\1(?!`)/g
	let last = 0
	for (let m = code.exec(text); m; m = code.exec(text)) {
		emphasis(text.slice(last, m.index), out, doc)
		const c = doc.createElement('code')
		// `` ` x ` `` のように両端の空白 1 つずつは囲みのためのもの（CommonMark と同じ）。
		const inner = m[2]!
		c.textContent = /^ .* $/.test(inner) && inner.trim() !== '' ? inner.slice(1, -1) : inner
		out.append(c)
		last = m.index + m[0].length
	}
	emphasis(text.slice(last), out, doc)
	return out
}

function emphasis(text: string, out: DocumentFragment | HTMLElement, doc: Document) {
	const re = /\*\*([^*]+?)\*\*|\[([^\]]+)\]\(([^)\s]+)\)/g
	let last = 0
	for (let m = re.exec(text); m; m = re.exec(text)) {
		if (m.index > last) out.append(doc.createTextNode(text.slice(last, m.index)))
		if (m[1] !== undefined) {
			const b = doc.createElement('strong')
			b.textContent = m[1]
			out.append(b)
		} else if (safeHref(m[3]!)) {
			const a = doc.createElement('a')
			a.textContent = m[2]!
			a.href = m[3]!
			a.target = '_blank'
			a.rel = 'noopener noreferrer'
			out.append(a)
		} else {
			out.append(doc.createTextNode(m[0]))
		}
		last = m.index + m[0].length
	}
	if (last < text.length) out.append(doc.createTextNode(text.slice(last)))
}

/** 本文全体。 */
export function renderMarkdown(src: string, doc: Document = document): DocumentFragment {
	const out = doc.createDocumentFragment()
	const lines = src.replace(/\r\n?/g, '\n').split('\n')
	let i = 0
	const para = (buf: string[]) => {
		if (!buf.length) return
		const p = doc.createElement('p')
		buf.forEach((l, k) => {
			if (k) p.append(doc.createElement('br'))
			p.append(renderInline(l.trim(), doc))
		})
		out.append(p)
	}
	let buf: string[] = []
	while (i < lines.length) {
		const line = lines[i]!
		const fence = FENCE.exec(line)
		if (fence) {
			para(buf)
			buf = []
			const mark = fence[1]!
			const body: string[] = []
			i++
			while (i < lines.length && !lines[i]!.trim().startsWith(mark)) body.push(lines[i++]!)
			i++
			const pre = doc.createElement('pre')
			const code = doc.createElement('code')
			code.textContent = body.join('\n')
			pre.append(code)
			out.append(pre)
			continue
		}
		if (LIST.test(line)) {
			para(buf)
			buf = []
			const items: string[] = []
			while (i < lines.length && (LIST.test(lines[i]!) || (/^\s{2,}\S/.test(lines[i]!) && items.length))) items.push(lines[i++]!)
			out.append(renderList(items, doc))
			continue
		}
		if (/^>\s?/.test(line)) {
			para(buf)
			buf = []
			const q: string[] = []
			while (i < lines.length && /^>\s?/.test(lines[i]!)) q.push(lines[i++]!.replace(/^>\s?/, ''))
			const bq = doc.createElement('blockquote')
			bq.append(renderMarkdown(q.join('\n'), doc))
			out.append(bq)
			continue
		}
		if (line.trim() === '') {
			para(buf)
			buf = []
		} else {
			buf.push(line)
		}
		i++
	}
	para(buf)
	return out
}

/** 箇条書きの行（字下げつき）を入れ子のリストにする。項目に続く字下げの行は、その項目の続き。 */
function renderList(lines: string[], doc: Document): HTMLElement {
	type Node = { indent: number; ordered: boolean; text: string[]; children: Node[] }
	const root: Node = { indent: -1, ordered: false, text: [], children: [] }
	const stack: Node[] = [root]
	for (const line of lines) {
		const m = LIST.exec(line)
		if (!m) {
			stack[stack.length - 1]!.text.push(line.trim())
			continue
		}
		const indent = m[1]!.replace(/\t/g, '  ').length
		while (stack.length > 1 && stack[stack.length - 1]!.indent >= indent) stack.pop()
		const node: Node = { indent, ordered: /\d/.test(m[2]!), text: [m[3]!], children: [] }
		stack[stack.length - 1]!.children.push(node)
		stack.push(node)
	}
	const build = (nodes: Node[]): HTMLElement => {
		const list = doc.createElement(nodes[0]?.ordered ? 'ol' : 'ul')
		for (const n of nodes) {
			const li = doc.createElement('li')
			n.text.forEach((t, k) => {
				if (k) li.append(doc.createElement('br'))
				li.append(renderInline(t, doc))
			})
			if (n.children.length) li.append(build(n.children))
			list.append(li)
		}
		return list
	}
	return build(root.children)
}
