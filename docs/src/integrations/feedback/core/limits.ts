// 大きさの上限と、受け口に届いた payload の検査。
// dev サーバーを `--host` で開くと同じネットワークの誰からでも届くので、形と大きさをここで確かめる。
import { SECTION_KEYS, type SavePayload } from './protocol'

export const LIMITS = {
	/** コメント 1 件の文字数。 */
	commentChars: 20_000,
	/** 1 つの節の行数。 */
	rowsPerSection: 500,
	/** 行のラベルの文字数。 */
	labelChars: 200,
	/** 行の値の文字数。 */
	valueChars: 100_000,
	/** payload 全体を JSON にした文字数。 */
	payloadChars: 2_000_000,
	/** 添付 1 つの大きさ（バイト）。 */
	attachmentBytes: 10_000_000,
	/** 添付の数。 */
	attachmentCount: 8,
	/** requestId の文字数。 */
	requestIdChars: 100,
} as const

export type PayloadCheck = { ok: true } | { ok: false; reason: string }

const isRow = (r: unknown): boolean =>
	Array.isArray(r) &&
	r.length === 2 &&
	typeof r[0] === 'string' &&
	typeof r[1] === 'string' &&
	r[0].length <= LIMITS.labelChars &&
	r[1].length <= LIMITS.valueChars

/** 保存の依頼の形と大きさを確かめる。理由つきで返す。 */
export function checkPayload(v: unknown): PayloadCheck {
	if (typeof v !== 'object' || v === null) return { ok: false, reason: 'payload がオブジェクトではありません' }
	const p = v as Partial<SavePayload>
	if (typeof p.requestId !== 'string' || p.requestId.length === 0 || p.requestId.length > LIMITS.requestIdChars) {
		return { ok: false, reason: 'requestId が不正です' }
	}
	if (typeof p.comment !== 'string') return { ok: false, reason: 'comment が文字列ではありません' }
	if (p.comment.length > LIMITS.commentChars) return { ok: false, reason: `comment が長すぎます（上限 ${LIMITS.commentChars} 文字）` }
	if (p.target !== undefined && p.target !== 'page' && p.target !== 'site') return { ok: false, reason: 'target が不正です' }
	if (typeof p.sections !== 'object' || p.sections === null || Array.isArray(p.sections)) {
		return { ok: false, reason: 'sections がオブジェクトではありません' }
	}
	for (const [key, rows] of Object.entries(p.sections)) {
		if (!(SECTION_KEYS as readonly string[]).includes(key)) return { ok: false, reason: `知らない節です: ${key}` }
		if (!Array.isArray(rows) || rows.length > LIMITS.rowsPerSection || !rows.every(isRow)) {
			return { ok: false, reason: `節 ${key} の行が不正か、大きすぎます` }
		}
	}
	if (p.kind !== undefined && !['element', 'text', 'page', 'site'].includes(p.kind)) return { ok: false, reason: 'kind が不正です' }
	if (p.omitted !== undefined && !(Array.isArray(p.omitted) && p.omitted.length <= SECTION_KEYS.length && p.omitted.every((k) => (SECTION_KEYS as readonly string[]).includes(k)))) {
		return { ok: false, reason: 'omitted が不正です' }
	}
	if (p.pageOpenedAt !== undefined && !(typeof p.pageOpenedAt === 'number' && Number.isFinite(p.pageOpenedAt))) {
		return { ok: false, reason: 'pageOpenedAt が不正です' }
	}
	let size: number
	try {
		size = JSON.stringify(p).length
	} catch {
		return { ok: false, reason: 'payload を JSON にできません' }
	}
	if (size > LIMITS.payloadChars) return { ok: false, reason: `payload が大きすぎます（上限 ${LIMITS.payloadChars} 文字）` }
	return { ok: true }
}

export const isPayload = (v: unknown): v is SavePayload => checkPayload(v).ok
