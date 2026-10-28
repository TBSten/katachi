// 画面上の矩形の計算。
export interface Box { x: number; y: number; width: number; height: number }

/** 2 つの矩形の隙間（重なれば 0）。 */
export const gap = (a: Box, b: Box): number => {
	const dx = Math.max(0, a.x - (b.x + b.width), b.x - (a.x + a.width))
	const dy = Math.max(0, a.y - (b.y + b.height), b.y - (a.y + a.height))
	return Math.hypot(dx, dy)
}
/** 矩形が画面の中に収まっているか。 */
export const frameOk = (b: Box | null, vs: { width: number; height: number }): boolean =>
	!!b && b.x >= 0 && b.y >= 0 && b.x + b.width <= vs.width + 0.5 && b.y + b.height <= vs.height + 0.5
