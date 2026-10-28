// 「サイトの状態」節。docs サイト（Starlight）の画面で、記録を読む人に見えていたはずの状態を集める。
// 取れないものは行を作らない。DOM の文字そのものは拾わない（選んだ内容は content 節が持つ）。
import type { Section } from '../core/protocol'

const clip = (s: string, n: number) => {
	const one = s.replace(/\s+/g, ' ').trim()
	return one.length > n ? one.slice(0, n) + '…' : one
}

const THEME_LABEL: Record<string, string> = { dark: 'ダーク', light: 'ライト' }

function themeRows(): Section {
	const effective = document.documentElement.dataset.theme ?? (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
	let chosen = ''
	try {
		chosen = localStorage.getItem('starlight-theme') ?? ''
	} catch {
		// 読めなければ表示中のテーマだけ。
	}
	// starlight-theme: '' = 自動、'dark' / 'light' = 選択。
	const how = chosen === 'dark' || chosen === 'light' ? '選択' : '自動（OS に従う）'
	return [['テーマ', `${THEME_LABEL[effective] ?? effective}（${how}）`]]
}

/** 同期タブ（`starlight-tabs[data-sync-key]`）で今選ばれているタブ。 */
function tabRows(): Section {
	const rows: Section = []
	for (const tabs of document.querySelectorAll('starlight-tabs[data-sync-key]')) {
		const key = (tabs as HTMLElement).dataset.syncKey
		const selected = tabs.querySelector('[role="tab"][aria-selected="true"]')
		if (key && selected) rows.push([`同期タブ ${key}`, clip(selected.textContent ?? '', 40)])
	}
	// 同じ鍵が複数あっても 1 行にする（同期しているので同じ値）。
	return rows.filter(([l], i) => rows.findIndex(([x]) => x === l) === i)
}

/** 本文で開いている `<details>`（折りたたみのコードブロックを含む）。 */
function detailsRows(): Section {
	const opened = [...document.querySelectorAll('main details[open]')]
		.map((d) => clip(d.querySelector(':scope > summary')?.textContent ?? '(見出しなし)', 40))
		.slice(0, 10)
	return opened.length ? [['開いている details', opened.map((t) => `「${t}」`).join('、')]] : []
}

/** 検索のダイアログ（Starlight の `<site-search>`）。開閉と、入力されている語。 */
function searchRows(): Section {
	const host = document.querySelector('site-search')
	if (!host) return []
	const dialog = host.querySelector('dialog')
	const open = Boolean(dialog?.open)
	const rows: Section = [['検索', open ? '開いている' : '閉じている']]
	const input = dialog?.querySelector<HTMLInputElement>('input')
	if (open && input?.value) rows.push(['検索の語', clip(input.value, 80)])
	return rows
}

/** 目次（右側）で今いる見出し。 */
function tocRows(): Section {
	const current = document.querySelector('starlight-toc a[aria-current="true"]')
	const text = current?.textContent
	return text ? [['目次で今いる見出し', clip(text, 80)]] : []
}

/** サイトの状態。テーマは必ず入る。 */
export function collectState(): Section {
	return [...themeRows(), ...tabRows(), ...detailsRows(), ...searchRows(), ...tocRows()]
}
