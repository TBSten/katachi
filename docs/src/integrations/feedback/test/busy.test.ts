import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { busyLabel, busyTicker } from '../feedback/busy'

describe('busyLabel（送信中のボタンの文字）', () => {
	it('2 秒までは段階だけ、超えたら経過秒数を添える', () => {
		expect(busyLabel('撮影中…', 0)).toBe('撮影中…')
		expect(busyLabel('撮影中…', 2000)).toBe('撮影中…')
		expect(busyLabel('撮影中…', 2001)).toBe('撮影中…（2 秒）')
		expect(busyLabel('保存中…', 12_345)).toBe('保存中…（12 秒）')
	})
})

describe('busyTicker（段階の切り替えと経過秒数）', () => {
	beforeEach(() => vi.useFakeTimers())
	afterEach(() => vi.useRealTimers())
	it('撮影中… → 経過秒数 → 保存中…（数え直し）→ 止めたら書かない', () => {
		const labels: string[] = []
		const ticker = busyTicker((l) => labels.push(l), () => Date.now())
		ticker.stage('撮影中…')
		expect(labels.at(-1)).toBe('撮影中…')
		vi.advanceTimersByTime(3000)
		expect(labels.at(-1)).toBe('撮影中…（3 秒）')
		ticker.stage('保存中…')
		expect(labels.at(-1)).toBe('保存中…')
		vi.advanceTimersByTime(1000)
		expect(labels.at(-1)).toBe('保存中…')
		ticker.stop()
		const n = labels.length
		vi.advanceTimersByTime(5000)
		expect(labels).toHaveLength(n)
	})
})
