// E2E の「真っ白でない」確かめ（e2e/helpers/pixels.ts）が、白＋赤枠の画像で落ち、実際のページらしい画像で通ることを確かめる。
import { mkdtemp, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import sharp from 'sharp'
import { afterAll, describe, expect, it } from 'vitest'
import { MIN_COLORS, MIN_LUMINANCE_STD, pixelStats } from '../../../../e2e/helpers/pixels'

const dirs: string[] = []
afterAll(async () => {
	await Promise.all(dirs.map((d) => rm(d, { recursive: true, force: true })))
})

const write = async (raw: Buffer, w: number, h: number) => {
	const d = await mkdtemp(join(tmpdir(), 'fb-px-'))
	dirs.push(d)
	const f = join(d, 'x.png')
	await writeFile(f, await sharp(raw, { raw: { width: w, height: h, channels: 3 } }).png().toBuffer())
	return f
}
const passes = (s: { colors: number; luminanceStd: number }) => s.colors >= MIN_COLORS && s.luminanceStd >= MIN_LUMINANCE_STD

describe('pixelStats', () => {
	it('真っ白に赤枠だけの画像は、真っ白とみなして落ちる', async () => {
		const w = 400, h = 300
		const buf = Buffer.alloc(w * h * 3, 255)
		const red = (x: number, y: number) => { const o = (y * w + x) * 3; buf[o] = 255; buf[o + 1] = 0; buf[o + 2] = 0 }
		for (let x = 50; x < 300; x++) for (const y of [50, 51, 52, 200, 201, 202]) red(x, y)
		for (let y = 50; y < 203; y++) for (const x of [50, 51, 52, 300, 301]) red(x, y)
		expect(passes(await pixelStats(await write(buf, w, h)))).toBe(false)
	})
	it('いろいろな色・明るさのある画像は通る', async () => {
		const w = 200, h = 200
		const buf = Buffer.alloc(w * h * 3)
		for (let i = 0; i < w * h; i++) { buf[i * 3] = (i * 7) % 256; buf[i * 3 + 1] = (i * 13) % 256; buf[i * 3 + 2] = (i * 29) % 256 }
		expect(passes(await pixelStats(await write(buf, w, h)))).toBe(true)
	})
})
