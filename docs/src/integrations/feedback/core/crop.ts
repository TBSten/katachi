// スクリーンショットの切り抜き範囲。feedback-mode skill の cropRect の写し（DOM を触らない純粋な計算）。

/** 選んだ場所のまわりに足す余白（CSS px）。 */
export const ELEMENT_MARGIN = 24

type Size = { width: number; height: number }
export type Rect = { x: number; y: number; width: number; height: number }

/**
 * 要素の切り抜き範囲（画像の px）。target（CSS px）+ margin に、画像と viewport の比（devicePixelRatio）を掛け、画像の中に収める。
 * 収まる部分が無ければ null。
 */
export function cropRect(target: Rect, viewport: Size, image: Size, margin = ELEMENT_MARGIN): Rect | null {
	const sx = viewport.width > 0 ? image.width / viewport.width : 1
	const sy = viewport.height > 0 ? image.height / viewport.height : 1
	const left = Math.max(0, Math.floor((target.x - margin) * sx))
	const top = Math.max(0, Math.floor((target.y - margin) * sy))
	const right = Math.min(image.width, Math.ceil((target.x + target.width + margin) * sx))
	const bottom = Math.min(image.height, Math.ceil((target.y + target.height + margin) * sy))
	if (right - left < 1 || bottom - top < 1) return null
	return { x: left, y: top, width: right - left, height: bottom - top }
}
