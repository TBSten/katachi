// 選んだものの横に浮かべるボタンの位置決め。DOM に触らない純粋な計算だけを置く。
export { rectOfPick } from './pick'

type Box = { left: number; top: number; right: number; bottom: number }

/**
 * ボタンの左上の座標を決める。選択のすぐ下、入らなければ上、どちらも入らなければ画面の下端に寄せる。
 * 左右は画面の外にはみ出さないよう収める。選択が画面の外に出たら null（隠す）。
 * iOS のコールアウト（コピーなど）は選択の上に出るので、まず下を試す。
 */
export function placeFloating(
	rect: Box,
	size: { width: number; height: number },
	view: { width: number; height: number },
	gap = 8,
	margin = 8,
): { left: number; top: number } | null {
	if (rect.bottom < 0 || rect.top > view.height || rect.right < 0 || rect.left > view.width) return null
	const maxLeft = Math.max(margin, view.width - size.width - margin)
	const left = Math.min(Math.max(rect.left, margin), maxLeft)
	const below = rect.bottom + gap
	if (below + size.height + margin <= view.height) return { left, top: below }
	const above = rect.top - gap - size.height
	if (above >= margin) return { left, top: above }
	return { left, top: Math.max(margin, view.height - size.height - margin) }
}

/**
 * popover の左上の座標を決める。置き方は placeFloating と同じだが、選択が画面の外に出ても隠さない。
 * 選択の矩形を画面の中に押し込んでから置くので、選択が画面の上へ出れば上端、下へ出れば下端に留まる。
 */
export function placePopover(
	rect: Box,
	size: { width: number; height: number },
	view: { width: number; height: number },
	/** 覆ってはいけない矩形（下端の案内の帯など）。 */
	avoid: Box[] = [],
): { left: number; top: number } {
	const clamp = (v: number, max: number) => Math.min(Math.max(v, 0), max)
	const inView = {
		left: clamp(rect.left, view.width),
		right: clamp(rect.right, view.width),
		top: clamp(rect.top, view.height),
		bottom: clamp(rect.bottom, view.height),
	}
	const pos = placeFloating(inView, size, view)!
	const margin = 8
	const hits = (p: { left: number; top: number }) =>
		avoid.some((a) => p.left < a.right && p.left + size.width > a.left && p.top < a.bottom && p.top + size.height > a.top)
	if (!hits(pos)) return pos
	// 選んだものの上 → 帯の真上 → 帯の真下、の順に、画面に収まって帯を覆わない置き場所を探す。見つからなければそのまま。
	const tops = [inView.top - 8 - size.height, ...avoid.flatMap((a) => [a.top - 8 - size.height, a.bottom + 8])]
	for (const top of tops) {
		const p = { left: pos.left, top }
		if (top >= margin && top + size.height + margin <= view.height && !hits(p)) return p
	}
	return pos
}
