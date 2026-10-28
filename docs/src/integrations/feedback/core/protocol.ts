// ブラウザ（toolbar アプリ）とサーバー（integration）が共有する型とイベント名。
// 両側から import されるので、Node 専用・ブラウザ専用の API はここに書かない。

/** toolbar アプリの id。本番ビルドに出ていないことの確認でも、この文字列を grep する。 */
export const APP_ID = 'katachi-feedback'

/** 本文の要素に付ける、元の Markdown / MDX の位置の属性名（`path:開始行-終了行`）。dev のときだけ付く。 */
export const SRC_ATTR = 'data-fb-src'

/** ブラウザ → サーバー: 保存の依頼。 */
export const EVENT_SAVE = 'katachi-feedback:save'
/** サーバー → ブラウザ: 保存の結果。 */
export const EVENT_RESULT = 'katachi-feedback:result'

/** ダイアログでチェックできる情報の種類。この並びがそのままダイアログと Markdown の節の並びになる。 */
export const SECTION_KEYS = [
	'source',
	'page',
	'location',
	'content',
	'screenshot',
	'screen',
	'state',
	'console',
	'browser',
	'datetime',
	'git',
] as const
export type SectionKey = (typeof SECTION_KEYS)[number]

/** 節ごとの見出し（Markdown とダイアログで共通）。 */
export const SECTION_TITLES: Record<SectionKey, string> = {
	source: 'ソースの位置',
	page: 'ページ',
	location: 'ページ上の場所',
	content: '選んだ内容',
	screenshot: 'スクリーンショット',
	screen: '画面',
	state: 'サイトの状態',
	console: 'コンソール',
	browser: 'ブラウザ',
	datetime: '日時',
	git: 'git',
}

/**
 * git 節の値は保存するときにサーバーが求める（ブラウザは知らない）。
 * ブラウザが送る git 節の中身は捨てられ、プレビューには下の GIT_PLACEHOLDER を出す。
 */
export const GIT_PLACEHOLDER = '（保存するときにサーバーが求めます）'

/** 節の中身。キーと値の並び（値は文字列にしてから送る。サーバー側の整形を単純に保つため）。 */
export type Section = Array<[label: string, value: string]>

export type SavePayload = {
	/** 送信の対応づけ用。結果に同じ値を載せて返す。 */
	requestId: string
	comment: string
	/** ページ全体・サイト全体へのフィードバックのとき。要素やテキストを選んだ場合は無い。 */
	target?: 'page' | 'site'
	/** チェックされた節だけが入る。 */
	sections: Partial<Record<SectionKey, Section>>
}

export type SaveResult =
	| { requestId: string; ok: true; file: string }
	| { requestId: string; ok: false; error: string }
