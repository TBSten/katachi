// ブラウザ（toolbar アプリ）とサーバー（integration）が共有する型とイベント名。
// 両側から import されるので、Node 専用・ブラウザ専用の API はここに書かない。

/** toolbar アプリの id。本番ビルドに出ていないことの確認でも、この文字列を grep する。 */
export const APP_ID = 'katachi-feedback'

/** ブラウザ → サーバー: 保存の依頼。 */
export const EVENT_SAVE = 'katachi-feedback:save'
/** サーバー → ブラウザ: 保存の結果。 */
export const EVENT_RESULT = 'katachi-feedback:result'

/** ダイアログでチェックできる情報の種類。この並びがそのままダイアログと Markdown の節の並びになる。 */
export const SECTION_KEYS = ['page', 'element', 'text', 'position', 'screen', 'browser', 'datetime'] as const
export type SectionKey = (typeof SECTION_KEYS)[number]

/** 節ごとの見出し（Markdown とダイアログで共通）。 */
export const SECTION_TITLES: Record<SectionKey, string> = {
	page: 'ページ',
	element: '選んだ要素',
	text: '選んだテキスト',
	position: '位置',
	screen: '画面',
	browser: 'ブラウザ',
	datetime: '日時',
}

/** 節の中身。キーと値の並び（値は文字列にしてから送る。サーバー側の整形を単純に保つため）。 */
export type Section = Array<[label: string, value: string]>

export type SavePayload = {
	/** 送信の対応づけ用。結果に同じ値を載せて返す。 */
	requestId: string
	comment: string
	/** チェックされた節だけが入る。 */
	sections: Partial<Record<SectionKey, Section>>
}

export type SaveResult =
	| { requestId: string; ok: true; file: string }
	| { requestId: string; ok: false; error: string }
