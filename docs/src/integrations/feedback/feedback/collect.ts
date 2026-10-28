import { consoleSection, readLogEntries } from '../core/console-section'
import { GIT_PLACEHOLDER, type Section, type SectionKey } from '../core/protocol'
import { blockOf, blockText, clip, offsetIn } from '../shared/fragment'
import { pageSourceFile } from '../shared/srcmap'
import { versionOfPage } from '../shared/version'
import { collectState } from './state'
import { groupRow, locationSection, present, rectOfPick, sourceSection, type Pick } from '../shared/pick'

export type { Pick }

const rectOf = (r: DOMRect) =>
	`x=${Math.round(r.x)}, y=${Math.round(r.y)}, width=${Math.round(r.width)}, height=${Math.round(r.height)}`

function contentSection(pick: Exclude<Pick, { kind: 'page' }>): Section {
	// 複数の要素: 要素ごとに `### n.` で区切って並べる。
	if (pick.kind === 'elements') return pick.els.flatMap((el, i) => [groupRow(i + 1), ...contentSection({ kind: 'element', el })])
	if (pick.kind === 'element') {
		const { el } = pick
		return present([
			['タグ', el.tagName.toLowerCase()],
			['テキストの全文', blockText(el)],
			['outerHTML の抜粋', clip(el.outerHTML, 500)],
		])
	}
	const { range } = pick
	const startBlock = blockOf(range.startContainer)
	const endBlock = blockOf(range.endContainer)
	const rows: Array<[string, string | null]> = [
		['選んだテキスト', pick.text],
		['ブロックの全文', blockText(startBlock)],
		['ブロック内の開始位置', numberOrNull(offsetIn(startBlock, range.startContainer, range.startOffset))],
	]
	if (endBlock === startBlock) {
		rows.push(['ブロック内の終了位置', numberOrNull(offsetIn(endBlock, range.endContainer, range.endOffset))])
	} else {
		rows.push(['終了側のブロックの全文', blockText(endBlock)], ['終了側ブロック内の終了位置', numberOrNull(offsetIn(endBlock, range.endContainer, range.endOffset))])
	}
	return present(rows)
}

const numberOrNull = (n: number | null) => (n === null ? null : String(n))

/**
 * ダイアログを開いた時点の値を集める。選んだものに当てはまらない行は作らない。
 * git 節の値はサーバーが保存のときに求めるので、ここでは印だけ置く。
 */
export function collect(pick: Pick, now = new Date()): Partial<Record<SectionKey, Section>> {
	const theme = document.documentElement.dataset.theme ?? (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
	const pageFile = pageSourceFile()
	const page: Section = [
		['URL', location.href],
		['title', document.title],
		['言語', document.documentElement.lang || '(未指定)'],
		['版', versionOfPage()],
	]
	// ページ全体・サイト全体: 選んだものの節（source・location・content）は無く、ページのソースファイルは page 節に入れる。
	if (pick.kind === 'page') page.push(['ページのソースファイル', pageFile.guessed ? `${pageFile.path}（URL からの推定）` : pageFile.path])
	const rect = pick.kind === 'page' ? null : rectOfPick(pick)
	const picked: { source?: Section; location?: Section; content?: Section } = pick.kind === 'page' ? {} : { source: sourceSection(pick), location: locationSection(pick), content: contentSection(pick) }
	return {
		...(picked.source ? { source: picked.source } : {}),
		page,
		...(picked.location ? { location: picked.location, content: picked.content } : {}),
		screen: [
			...(rect ? [['getBoundingClientRect', rectOf(rect)] as [string, string]] : []),
			['scroll', `scrollX=${Math.round(scrollX)}, scrollY=${Math.round(scrollY)}`],
			['viewport', `${innerWidth} x ${innerHeight}`],
			['devicePixelRatio', String(devicePixelRatio)],
			['入力', `pointer: coarse=${matchMedia('(pointer: coarse)').matches}, hover: none=${matchMedia('(hover: none)').matches}, maxTouchPoints=${navigator.maxTouchPoints}`],
			['テーマ', theme === 'dark' ? 'ダーク' : 'ライト'],
		],
		screenshot: [['撮影', '送信するときに撮ります（画面全体に選んだ場所の赤枠を付けたものと、選んだ場所のまわりの切り抜き）']],
		console: consoleSection(readLogEntries(globalThis)),
		state: collectState(),
		browser: [['UserAgent', navigator.userAgent]],
		datetime: [['日時', now.toISOString()]],
		build: [['Astro・Starlight・Node の版、dev の起動時刻、統合のハッシュ、ソースの古さ', GIT_PLACEHOLDER]],
		git: [['commit', GIT_PLACEHOLDER], ['ソースの未コミットの変更', GIT_PLACEHOLDER]],
	}
}
