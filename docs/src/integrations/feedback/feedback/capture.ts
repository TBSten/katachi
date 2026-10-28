// スクリーンショット: 今見えている画面を絵にして、選んだ場所を赤枠で囲み、全体と選んだ場所のまわりの 2 枚にする。
// modern-screenshot（DOM を SVG の foreignObject 経由で絵にするライブラリ）を、撮るときに動的 import する。
// devDependencies なので、本番ビルドには入らない（docs/scripts/check-no-dev-tools-in-dist.sh で確かめる）。
import { LOG_GLOBAL } from '../core/console-section'
import { cropRect } from '../core/crop'
import { SHOT_ELEMENT, SHOT_WINDOW, type PayloadAttachment } from '../core/protocol'
import { rectOfPick, type Pick } from '../shared/pick'

export type Shots = {
	attachments: PayloadAttachment[]
	/** 撮れなかった（または一部だけ撮れた）理由。 */
	error?: string
}

/** 赤枠の色と太さ（CSS px）。 */
const FRAME_COLOR = '#ff0000'
const FRAME_WIDTH = 3
/** 画像の大きさを抑えるため、dpr が高くてもこの倍率までにする。 */
const MAX_SCALE = 2
/** 範囲の選択が何行にもまたがるときに描く枠の最大数。 */
const MAX_FRAMES = 40

/** 絵にしない要素: 自分の UI（Feedback・Memo・メモの toast）と Astro の toolbar、display: none の HTML 要素。 */
export function shouldSkip(node: Node): boolean {
	if (!(node instanceof Element)) return false
	const tag = node.tagName
	if (tag.startsWith('KATACHI-') || tag === 'ASTRO-DEV-TOOLBAR') return true
	// 見えない要素は、飛ばしても配置が変わらず、速くなる。SVG の <defs> などは <use> が参照するので飛ばさない（HTML 要素だけ）。
	return node instanceof HTMLElement && getComputedStyle(node).display === 'none'
}

/**
 * `:xmlns` のような XML として不正な属性名（expressive-code-collapsible の svg に付く）が 1 つでもあると、
 * 絵にする途中の XML が壊れて画像が全部空白になる。複製の側から外す。
 */
const VALID_ATTR = /^[A-Za-z_][-\w.]*(:[A-Za-z_][-\w.]*)?$/
export function stripInvalidAttributes(node: Node) {
	if (!(node instanceof Element)) return
	for (const a of [...node.attributes]) if (!VALID_ATTR.test(a.name)) node.removeAttribute(a.name)
}

/** 選んだものの枠（viewport 座標）。要素は 1 つ、テキストは行ごと。ページ全体は無し。 */
export function frameRects(pick: Pick): DOMRect[] {
	if (pick.kind === 'page') return []
	if (pick.kind === 'element') return [pick.el.getBoundingClientRect()]
	if (pick.kind === 'elements') return pick.els.map((e) => e.getBoundingClientRect())
	const lines = [...pick.range.getClientRects()].filter((r) => r.width > 0 && r.height > 0).slice(0, MAX_FRAMES)
	return lines.length ? lines : [pick.range.getBoundingClientRect()]
}

/** 画面（viewport）を絵にした canvas。スクロールしている分は、動かして合わせる。 */
async function renderViewport(scale: number): Promise<HTMLCanvasElement> {
	const { domToCanvas } = await import('modern-screenshot')
	return domToCanvas(document.documentElement, {
		width: innerWidth,
		height: innerHeight,
		scale,
		style: { transform: `translate(${-scrollX}px, ${-scrollY}px)`, transformOrigin: '0 0' },
		filter: (n) => !shouldSkip(n),
		onCloneEachNode: stripInvalidAttributes,
	})
}

/** numbered なら、枠の左上に番号を付ける（複数の要素。記録の `### 1.` と対応する）。 */
function drawFrames(canvas: HTMLCanvasElement, rects: DOMRect[], scale: number, numbered: boolean) {
	const ctx = canvas.getContext('2d')
	if (!ctx) return
	ctx.strokeStyle = FRAME_COLOR
	ctx.lineWidth = FRAME_WIDTH * scale
	const pad = (FRAME_WIDTH / 2) * scale
	rects.forEach((r, i) => {
		const x = r.left * scale - pad
		const y = r.top * scale - pad
		ctx.strokeRect(x, y, r.width * scale + pad * 2, r.height * scale + pad * 2)
		if (!numbered) return
		const size = 14 * scale
		ctx.font = `bold ${size}px sans-serif`
		const label = String(i + 1)
		const w = ctx.measureText(label).width + 8 * scale
		ctx.fillStyle = FRAME_COLOR
		ctx.fillRect(x, Math.max(0, y - size - 4 * scale), w, size + 4 * scale)
		ctx.fillStyle = '#fff'
		ctx.fillText(label, x + 4 * scale, Math.max(0, y - size - 4 * scale) + size)
	})
}

const toBlob = (c: HTMLCanvasElement) =>
	new Promise<Blob>((resolve, reject) => c.toBlob((b) => (b ? resolve(b) : reject(new Error('PNG にできませんでした'))), 'image/png'))

export async function blobToBase64(blob: Blob): Promise<string> {
	const bytes = new Uint8Array(await blob.arrayBuffer())
	let bin = ''
	for (let i = 0; i < bytes.length; i += 0x8000) bin += String.fromCharCode(...bytes.subarray(i, i + 0x8000))
	return btoa(bin)
}

/**
 * 画面全体（赤枠つき）と、選んだ場所のまわりの切り抜きを撮る。投げない: 失敗は error に入れて返す（撮れなくても記録は送る）。
 * 自分の UI は絵にしない（shouldSkip）ので、ダイアログを開いたままでも写らない。
 */
export async function captureShots(pick: Pick): Promise<Shots> {
	// 複製でカスタム要素（<site-search> など）が出す例外は、コンソールの記録に入れない。
	const log = (globalThis as unknown as Record<string, { muted?: boolean } | undefined>)[LOG_GLOBAL]
	if (log) log.muted = true
	try {
		return await capture(pick)
	} finally {
		if (log) log.muted = false
	}
}

async function capture(pick: Pick): Promise<Shots> {
	try {
		const scale = Math.min(devicePixelRatio || 1, MAX_SCALE)
		const rects = frameRects(pick)
		const canvas = await renderViewport(scale)
		drawFrames(canvas, rects, scale, pick.kind === 'elements')
		const attachments: PayloadAttachment[] = [{ name: SHOT_WINDOW, mime: 'image/png', base64: await blobToBase64(await toBlob(canvas)) }]
		if (pick.kind === 'page') return { attachments }
		const target = rectOfPick(pick)
		const crop = cropRect(
			{ x: target.x, y: target.y, width: target.width, height: target.height },
			{ width: innerWidth, height: innerHeight },
			{ width: canvas.width, height: canvas.height },
		)
		if (!crop) return { attachments, error: '選んだ場所が画面の外にあるので、まわりの切り抜きは作っていません' }
		const out = document.createElement('canvas')
		out.width = crop.width
		out.height = crop.height
		out.getContext('2d')!.drawImage(canvas, crop.x, crop.y, crop.width, crop.height, 0, 0, crop.width, crop.height)
		attachments.push({ name: SHOT_ELEMENT, mime: 'image/png', base64: await blobToBase64(await toBlob(out)) })
		return { attachments }
	} catch (e) {
		return { attachments: [], error: `撮影に失敗しました: ${e instanceof Error ? e.message : String(e)}` }
	}
}
