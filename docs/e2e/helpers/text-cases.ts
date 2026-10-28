// テキスト選択の場所の違い。page.evaluate の中で動かす関数（外の変数は使えない）。選んだ範囲の真ん中の座標を返す。
export type SelectResult = { text: string; x: number; y: number }

export const textCases: { name: string; select: () => SelectResult }[] = [
	{
		name: '段落・短い語',
		select: () => {
			const p = document.querySelectorAll('main .sl-markdown-content > p')[1]
			const n = [...p.childNodes].find((x) => x.nodeType === 3 && (x.textContent ?? '').trim().length > 20)!
			const r = document.createRange()
			r.setStart(n, 3)
			r.setEnd(n, 15)
			const s = getSelection()!
			s.removeAllRanges()
			s.addRange(r)
			const b = r.getBoundingClientRect()
			return { text: s.toString(), x: b.x + b.width / 2, y: b.y + b.height / 2 }
		},
	},
	{
		name: '段落・長い文',
		select: () => {
			const w0 = document.createTreeWalker(document.querySelector('main .sl-markdown-content')!, NodeFilter.SHOW_TEXT)
			let n: Node | null
			while ((n = w0.nextNode())) if ((n.textContent ?? '').length > 60 && n.parentElement!.closest('p')) break
			const p = n!.parentElement!
			const r = document.createRange()
			r.setStart(n!, 2)
			r.setEnd(n!, Math.min((n!.textContent ?? '').length, 80))
			p.scrollIntoView({ block: 'center' })
			const s = getSelection()!
			s.removeAllRanges()
			s.addRange(r)
			const b = r.getBoundingClientRect()
			return { text: s.toString(), x: b.x + b.width / 2, y: b.y + b.height / 2 }
		},
	},
	{
		name: '2 つの段落にまたがる',
		select: () => {
			const ps = document.querySelectorAll('main .sl-markdown-content > p')
			const a = ps[0]
			const b = ps[1]
			const na = a.firstChild!.nodeType === 3 ? a.firstChild! : [...a.childNodes].find((x) => x.nodeType === 3)!
			const nb = [...b.childNodes].find((x) => x.nodeType === 3 && (x.textContent ?? '').length > 8)!
			const r = document.createRange()
			r.setStart(na, 5)
			r.setEnd(nb, 6)
			const s = getSelection()!
			s.removeAllRanges()
			s.addRange(r)
			const bb = r.getBoundingClientRect()
			return { text: s.toString(), x: bb.x + bb.width / 2, y: bb.y + 10 }
		},
	},
	{
		name: 'ページに何度も出る語(katachi)',
		select: () => {
			const ps = [...document.querySelectorAll('main .sl-markdown-content > p')]
			const find = (p: Element) => {
				const w = document.createTreeWalker(p, NodeFilter.SHOW_TEXT)
				let n: Node | null
				while ((n = w.nextNode())) {
					const i = (n.textContent ?? '').indexOf('katachi')
					if (i >= 0) return [n, i] as const
				}
				return null
			}
			const hit = find(ps[Math.floor(ps.length / 2)]) ?? find(ps.find((x) => (x.textContent ?? '').includes('katachi'))!)!
			const r = document.createRange()
			r.setStart(hit[0], hit[1])
			r.setEnd(hit[0], hit[1] + 7)
			hit[0].parentElement!.scrollIntoView({ block: 'center' })
			const s = getSelection()!
			s.removeAllRanges()
			s.addRange(r)
			const b = r.getBoundingClientRect()
			return { text: s.toString(), x: b.x + b.width / 2, y: b.y + b.height / 2 }
		},
	},
	{
		name: 'リスト項目の中',
		select: () => {
			const li = [...document.querySelectorAll('main .sl-markdown-content > ul > li')].at(-1)!
			li.scrollIntoView({ block: 'center' })
			const w = document.createTreeWalker(li, NodeFilter.SHOW_TEXT)
			let n: Node | null
			while ((n = w.nextNode())) if ((n.textContent ?? '').trim().length > 6) break
			const r = document.createRange()
			r.setStart(n!, 0)
			r.setEnd(n!, Math.min((n!.textContent ?? '').length, 10))
			const s = getSelection()!
			s.removeAllRanges()
			s.addRange(r)
			const b = r.getBoundingClientRect()
			return { text: s.toString(), x: b.x + b.width / 2, y: b.y + b.height / 2 }
		},
	},
	{
		name: '表のセルの中',
		select: () => {
			const td = [...document.querySelectorAll('main .sl-markdown-content table tbody td')].find((t) => (t.textContent ?? '').trim().length > 4)!
			td.scrollIntoView({ block: 'center' })
			const w = document.createTreeWalker(td, NodeFilter.SHOW_TEXT)
			let n: Node | null
			while ((n = w.nextNode())) if ((n.textContent ?? '').trim().length > 3) break
			const r = document.createRange()
			r.setStart(n!, 0)
			r.setEnd(n!, Math.min((n!.textContent ?? '').length, 8))
			const s = getSelection()!
			s.removeAllRanges()
			s.addRange(r)
			const b = r.getBoundingClientRect()
			return { text: s.toString(), x: b.x + b.width / 2, y: b.y + b.height / 2 }
		},
	},
	{
		name: 'コードブロックの中',
		select: () => {
			const code = document.querySelectorAll('main .expressive-code pre code')[1]
			code.scrollIntoView({ block: 'center' })
			const line = code.querySelector('.ec-line:nth-child(2)') ?? code
			const w = document.createTreeWalker(line, NodeFilter.SHOW_TEXT)
			const nodes: Node[] = []
			let n: Node | null
			while ((n = w.nextNode())) nodes.push(n)
			const r = document.createRange()
			r.setStart(nodes[0], 0)
			r.setEnd(nodes.at(-1)!, (nodes.at(-1)!.textContent ?? '').length)
			const s = getSelection()!
			s.removeAllRanges()
			s.addRange(r)
			const b = r.getBoundingClientRect()
			return { text: s.toString(), x: b.x + b.width / 2, y: b.y + b.height / 2 }
		},
	},
	{
		name: 'Aside の中',
		select: () => {
			const p = document.querySelector('main aside.starlight-aside .starlight-aside__content p')!
			p.scrollIntoView({ block: 'center' })
			const w = document.createTreeWalker(p, NodeFilter.SHOW_TEXT)
			let n: Node | null
			while ((n = w.nextNode())) if ((n.textContent ?? '').trim().length > 8) break
			const r = document.createRange()
			r.setStart(n!, 0)
			r.setEnd(n!, Math.min((n!.textContent ?? '').length, 12))
			const s = getSelection()!
			s.removeAllRanges()
			s.addRange(r)
			const b = r.getBoundingClientRect()
			return { text: s.toString(), x: b.x + b.width / 2, y: b.y + b.height / 2 }
		},
	},
]
