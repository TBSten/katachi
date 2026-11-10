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
	'build',
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
	build: 'ビルド',
	git: 'git',
}

/**
 * git・build 節の値は保存するときにサーバーが求める（ブラウザは知らない）。
 * ブラウザが送るこれらの節の中身は捨てられ、プレビューには下の GIT_PLACEHOLDER を出す。
 * 「Markdown をコピー」の文には、この 2 節は入らない。
 */
export const SERVER_SECTION_KEYS = ['build', 'git'] as const satisfies readonly SectionKey[]
export const GIT_PLACEHOLDER = '（保存するときにサーバーが求めます）'

/** 複数の要素の節で、要素ごとの区切りに使う行の見出し（記録では `### 1.` の小見出しの行になる。値は使わない）。 */
export const GROUP_ROW_PREFIX = '### '

/** 節の中身。キーと値の並び（値は文字列にしてから送る。サーバー側の整形を単純に保つため）。 */
export type Section = Array<[label: string, value: string]>

/** 保存の依頼に載せる添付（スクリーンショットの PNG）。サーバーが Store.putAttachments に渡し、参照を記録に書く。 */
export type PayloadAttachment = { name: string; mime: 'image/png'; base64: string }

/** スクリーンショットの添付の名前。記録の「スクリーンショット」の節に出る順。 */
export const SHOT_WINDOW = 'window.png'
export const SHOT_ELEMENT = 'element.png'

export type SavePayload = {
	/** 送信の対応づけ用。結果に同じ値を載せて返す。 */
	requestId: string
	comment: string
	/** ページ全体・サイト全体へのフィードバックのとき。要素やテキストを選んだ場合は無い。 */
	target?: 'page' | 'site'
	/** 何を選んだか（element / elements / text / page / site）。記録の kind になる。無ければ節の中身から推す。 */
	kind?: 'element' | 'elements' | 'text' | 'page' | 'site'
	/** チェックされた節だけが入る。 */
	sections: Partial<Record<SectionKey, Section>>
	/** ダイアログに出ていたが、チェックを外された節。記録の omitted と「載せなかった情報」の行になる。 */
	omitted?: SectionKey[]
	/** ページを開いた時刻（performance.timeOrigin、ミリ秒）。ソースがそのあと変わったかの判定（stale）に使う。 */
	pageOpenedAt?: number
	/** スクリーンショットの PNG。「スクリーンショット」の節をチェックしたときだけ付く。 */
	attachments?: PayloadAttachment[]
	/** スクリーンショットを撮れなかった理由（attachments が無いとき）。記録の節に書く。 */
	screenshotError?: string
}

export type SaveResult =
	| { requestId: string; ok: true; file: string }
	| { requestId: string; ok: false; error: string }

// ---- Memo（ページ・選んだ箇所ごとのメモ） ----

/** Memo の toolbar アプリの id。本番ビルドに出ていないことの確認でも、この文字列を grep する。 */
export const MEMO_APP_ID = 'katachi-memo'

/** ブラウザ → サーバー: メモの読み込み・保存の依頼（op で分ける）。 */
export const EVENT_MEMO_REQUEST = 'katachi-memo:request'
/** サーバー → ブラウザ: その結果。 */
export const EVENT_MEMO_RESULT = 'katachi-memo:result'

/** 通信で送る field のひな形 1 つ（memo/fields.ts の FieldDef と同じ形。受け取った側は sanitizeFields で整え直す）。 */
export type MemoFieldDef = { name: string; type: 'text' | 'check' | 'select'; options?: string[] }

export type MemoRequest =
	| { requestId: string; op: 'load'; lang: string; pagePath: string }
	/**
	 * text はメモの Markdown 全体（null ならこのページのファイルは書かず、ひな形だけ書く）。
	 * fields は全ページ共通の field のひな形（`memo/_fields.json`）。
	 */
	| { requestId: string; op: 'save'; lang: string; pagePath: string; text: string | null; fields: MemoFieldDef[] }

export type MemoResult =
	/** text が null ならそのページのメモはまだ無い。fields はひな形（無ければ既定）。 */
	| { requestId: string; ok: true; op: 'load'; text: string | null; fields: MemoFieldDef[] }
	| { requestId: string; ok: true; op: 'save'; file: string | null }
	| { requestId: string; ok: false; error: string }

// ---- Review（docs への指摘を、ページの上に印と popup で出す） ----

/** Review の toolbar アプリの id。本番ビルドに出ていないことの確認でも、この文字列を grep する。 */
export const REVIEW_APP_ID = 'katachi-review'

/** ブラウザ → サーバー: 指摘の読み込み・解決済みの印の付け外し（op で分ける）。 */
export const EVENT_REVIEW_REQUEST = 'katachi-review:request'
/** サーバー → ブラウザ: その結果。 */
export const EVENT_REVIEW_RESULT = 'katachi-review:result'

export type ReviewRequest =
	| { requestId: string; op: 'load' }
	/** 人が解決済みにする（note は一言。空でもよい）。 */
	| { requestId: string; op: 'resolve'; id: string; note: string }
	/** 人が解決済みを戻す。 */
	| { requestId: string; op: 'reopen'; id: string }
	/** 人が「直してもらう」: 指摘から Feedback の記録を作り、指摘は送った印を付けて解決済みにする。page はいまのページの URL。 */
	| { requestId: string; op: 'send'; id: string; note: string; page: string }
	/** 送ったのを取り消す: 指摘を未解決に戻し、作った記録（`yymmdd-NNN`）は archived に移す。 */
	| { requestId: string; op: 'unsend'; id: string; record: string }
	/** 人が popup の「＋」で選択肢を足す（`review/_actions.md` に書き足す）。 */
	| { requestId: string; op: 'add-action'; label: string; agent: string; decision?: ReviewDecision }
	/** 人が選択肢を直す（名前が from の選択肢を、その場で書き換える）。 */
	| { requestId: string; op: 'update-action'; from: string; label: string; agent: string; decision?: ReviewDecision }
	/** 人が選択肢を消す。 */
	| { requestId: string; op: 'remove-action'; label: string }

/** prerelease の判断の記録（`.claude/skills/prerelease/decisions/`）の decision。選択肢の `- decision:` に書ける。 */
export type ReviewDecision = '直さない' | 'このまま' | '後で' | '方針'

/** 人が足した選択肢（`review/_actions.md` の見出し 1 つ）。押すと解決済みにして、note の頭に label を書く。 */
export type ReviewAction = {
	label: string
	/** agent がすること（ボタンの説明と cli の review actions に出す）。 */
	agent?: string
	/** 対応する判断（prerelease の decisions/ に写すとき使う）。 */
	decision?: ReviewDecision
}

/** 「直さない」で解決済みにしたときの、ひとことの頭。agent はこれを見て、直ったかを確かめずに消す。 */
export const REVIEW_KEEP_NOTE = '直さない'

/** 「直してもらう」で解決済みにしたときの、ひとことの頭（`Feedback に送った: 261110-001`）。agent は Review の側では扱わない（Feedback の記録を archive すると消える）。 */
export const REVIEW_SENT_NOTE = 'Feedback に送った'
/** Feedback の記録の「選んだ内容」の節で、元の指摘の id を書く行の見出し。cli の archive がこれを見て指摘を消す。 */
export const REVIEW_LINK_LABEL = 'Review の指摘'

/** 画面に出す指摘 1 件。行は今のソースに合わせて引き直したもの（review/model.ts の relocate）。 */
export type ReviewView = {
	id: string
	set: string
	title: string
	priority: number | null
	/** リポジトリ直下からのパス。 */
	file: string
	/** 引き直した行。null ならページ全体への指摘。 */
	line: { start: number; end: number } | null
	/** 指摘のファイルに書かれた行。 */
	written: { start: number; end: number } | null
	place: 'ok' | 'moved' | 'lost' | 'page' | 'no-file'
	/** 本文に出ない行を、ページの文字から探す手がかり（review/model.ts の textHints）。無ければ省く。 */
	hints?: string[]
	status: 'open' | 'resolved'
	note?: string
	resolvedAt?: string
	aiNote?: string
	body: string
}

export type ReviewSetView = { name: string; title: string; base?: string; file: string; errors: string[] }

export type ReviewResult =
	/** root はリポジトリ直下の絶対パス（popup の「エディタで開く」に使う。dev サーバーの手元のパス）。 */
	/** sent は「直してもらう」で作った Feedback の記録の id（`yymmdd-NNN`）。 */
	/** actions は人が足した選択肢、actionErrors は `_actions.md` の読めなかった行。 */
	| { requestId: string; ok: true; sets: ReviewSetView[]; items: ReviewView[]; root?: string; sent?: string; actions?: ReviewAction[]; actionErrors?: string[] }
	| { requestId: string; ok: false; error: string }
