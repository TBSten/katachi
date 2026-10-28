// 画像の画素の統計。スクリーンショットが「真っ白（全面が 1 色）」になっていないことの確かめに使う。
import { readFileSync } from 'node:fs'
import sharp from 'sharp'

export type PixelStats = {
	/** 色の種類の数（RGB を各 4 ビットに丸めたもの）。 */
	colors: number
	/** 輝度（0〜255）の標準偏差。 */
	luminanceStd: number
}

/** 画素の値のばらつきを数える。大きい画像は縮小せずそのまま読む（色の種類を潰さないため）。 */
export async function pixelStats(abs: string): Promise<PixelStats> {
	const { data, info } = await sharp(readFileSync(abs)).removeAlpha().raw().toBuffer({ resolveWithObject: true })
	const seen = new Set<number>()
	let sum = 0
	let sumSq = 0
	const n = info.width * info.height
	for (let i = 0; i < n; i++) {
		const r = data[i * 3]!
		const g = data[i * 3 + 1]!
		const b = data[i * 3 + 2]!
		seen.add(((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4))
		const y = 0.2126 * r + 0.7152 * g + 0.0722 * b
		sum += y
		sumSq += y * y
	}
	const mean = sum / n
	return { colors: seen.size, luminanceStd: Math.sqrt(Math.max(0, sumSq / n - mean * mean)) }
}

/** 真っ白（や全面べた塗り）の画像と見分けるための下限。実際のページ（本文・コードの色・図）は、これを大きく超える。 */
export const MIN_COLORS = 10
export const MIN_LUMINANCE_STD = 8
