// Store（記録の置き場）の型。ファイル実装（store.ts）とメモリ実装（memory-store.ts）が同じ契約を満たす。
// feedback-mode skill の Sink に、メモ機能が使う writeDoc / readDoc を足したもの。

export type EntryState = 'open' | 'archived'

export type EntryInfo = {
	/** `yymmdd-NNN`（旧形は `yymmdd-NN`）。置き場の中で一意。 */
	id: string
	/** 人・Agent が開ける参照。ファイル実装では repoRoot からの相対（repoRoot が無ければ置き場からの相対）。 */
	ref: string
	state: EntryState
	bytes: number
	createdAt: string
}

/** 添付の名前として使える形（パスの区切りや先頭の `.` を含めない）。 */
export const ATTACH_NAME = /^[A-Za-z0-9][A-Za-z0-9._-]*$/

export type Attachment = { name: string; data: Uint8Array; mime?: string }

export type PutAttachmentsResult = {
	/** name → 参照。記録に書かれ、Agent が開く。 */
	refs: Record<string, string>
	/** まとめ先。無ければ null。 */
	dir: string | null
	/** 失敗の理由。投げずに返す（記録に「撮れませんでした」と書くため）。 */
	error: string | null
}

export type Store = {
	kind: 'file' | 'memory'
	/** 新しい記録を作る。上書きしない。番号は Inbox と archived の両方を数えて採番する。 */
	putEntry(o: { text: string; now?: Date }): Promise<EntryInfo & { attempts: number }>
	/** Inbox（置き場の直下）の `yymmdd-NNN.md` だけ。`state` で archived や両方も選べる。 */
	list(o?: { state?: EntryState | 'all' }): Promise<EntryInfo[]>
	read(id: string): Promise<EntryInfo & { text: string }>
	/** `archived/` に移し、末尾に対応メモを 1 行足す。 */
	archive(id: string, note?: string, now?: Date): Promise<EntryInfo>
	/** 添付（スクリーンショットなど）を置く。 */
	putAttachments(files: Attachment[], o?: { now?: Date }): Promise<PutAttachmentsResult>
	/** 置き場のルートの中の文書を上書きする（メモ機能）。tmp に書いて rename する。 */
	writeDoc(relPath: string, text: string): Promise<void>
	/** 無ければ null。 */
	readDoc(relPath: string): Promise<string | null>
}

/** 対応メモの追記。frontmatter は書き換えず、末尾に区切りと 1 行を足す。 */
export function archiveNoteText(note: string, now: Date): string {
	const day = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
	return `\n---\n対応メモ: ${note.replace(/\s+/g, ' ').trim()}（${day}）\n`
}
