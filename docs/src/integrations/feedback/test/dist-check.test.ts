import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { APP_ID, MEMO_APP_ID, SRC_ATTR } from '../core/protocol'
import { LOG_GLOBAL } from '../core/console-section'

// 本番の検査 script の grep パターンが、統合の定数とずれていないことの確認。
const script = readFileSync(new URL('../../../../scripts/check-no-dev-tools-in-dist.sh', import.meta.url), 'utf8')
const patterns = script.match(/PATTERNS='([^']*)'/)?.[1].split('\n') ?? []

describe('check-no-dev-tools-in-dist.sh', () => {
	it.each([
		['APP_ID', APP_ID],
		['SRC_ATTR', SRC_ATTR],
		['LOG_GLOBAL', LOG_GLOBAL],
		['メモの toolbar アプリ', MEMO_APP_ID],
		['スクリーンショットの依存', 'modern-screenshot'],
	])('%s の文字列を探す', (_name, value) => {
		expect(patterns).toContain(value)
	})
})
