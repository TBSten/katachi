import { describe, expect, it } from 'vitest'
import { datePrefix, formatId, idOfFileName, isEntryId, lastNumber, MAX_PER_DAY, nextNumber } from '../core/naming'

describe('naming', () => {
	it('datePrefix はローカル時刻の yymmdd', () => {
		expect(datePrefix(new Date(2026, 9, 29, 23, 59))).toBe('261029')
		expect(datePrefix(new Date(2031, 0, 5))).toBe('310105')
	})

	it('formatId は番号を 3 桁にそろえる', () => {
		expect(formatId('261029', 1)).toBe('261029-001')
		expect(formatId('261029', 123)).toBe('261029-123')
	})

	it.each(['261029-001', '261029-01', '261029-1234'])('isEntryId: %s は id', (id) => {
		expect(isEntryId(id)).toBe(true)
	})

	it.each(['261029-1', '26102-001', '261029-001.md', '../261029-001', 'a/261029-001', '', 'test-260925', '261029-001\n', 42, null])(
		'isEntryId: %j は id ではない',
		(id) => {
			expect(isEntryId(id)).toBe(false)
		},
	)

	it('idOfFileName は直下の記録のファイル名だけ id にする', () => {
		expect(idOfFileName('261029-001.md')).toBe('261029-001')
		expect(idOfFileName('261029-01.md')).toBe('261029-01')
		for (const n of ['archived', 'memo', 'attachments', 'HISTORY.md', 'test-260925', '261029-001.md.tmp', '261029-001.txt']) {
			expect(idOfFileName(n)).toBeNull()
		}
	})

	it('lastNumber は同じ日の最大。2 桁のころの名前も、拡張子の違うものも数える', () => {
		const names = ['261029-001.md', '261029-07.md', '261028-050.md', 'HISTORY.md', 'archived', '261029-003.png']
		expect(lastNumber(names, '261029')).toBe(7)
		expect(lastNumber(names, '261028')).toBe(50)
		expect(lastNumber(names, '261027')).toBe(0)
	})

	it('nextNumber は Inbox と archived の両方の名前を渡せば重ならない', () => {
		const inbox = ['261029-001.md']
		const archived = ['261029-002.md', '261029-003.md']
		expect(nextNumber([...inbox, ...archived], '261029')).toBe(4)
		expect(nextNumber(inbox, '261029')).toBe(2) // archived を渡さないと重なる（これが昔の不具合）
	})

	it('nextNumber は上限を超えると null', () => {
		expect(nextNumber([`261029-${MAX_PER_DAY}.md`], '261029')).toBeNull()
		expect(nextNumber([`261029-${MAX_PER_DAY - 1}.md`], '261029')).toBe(MAX_PER_DAY)
	})
})
