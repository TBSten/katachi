import { describe, expect, it } from 'vitest'
import { actionOfNote, addAction, LABEL_CHARS, labelProblem, MAX_ACTIONS, noteHead, noteOf, parseActions, removeAction } from '../review/actions'

const FILE = `# Review の選択肢

前置き（読まない）

## 後で
- agent: v0.5 のチケットに積んでから review remove
- decision: 後で

人向けのメモ（読まない）
- agent: 本文の箇条書きはメタにしない

## このまま

\`\`\`md
## コードフェンスの中は見出しにしない
\`\`\`
`

describe('parseActions', () => {
	it('見出し 1 つが選択肢 1 つ。見出しの直後の agent・decision だけを読む', () => {
		const { actions, errors } = parseActions(FILE)
		expect(errors).toEqual([])
		expect(actions).toEqual([
			{ label: '後で', agent: 'v0.5 のチケットに積んでから review remove', decision: '後で' },
			{ label: 'このまま' },
		])
	})
	it('使えない名前・同じ名前・知らない decision は飛ばして理由を書く', () => {
		const { actions, errors } = parseActions('## a:b\n## 直さない\n## x\n## x\n## y\n- decision: いつか\n')
		expect(actions.map((a) => a.label)).toEqual(['x', 'y'])
		expect(actions[1]!.decision).toBeUndefined()
		expect(errors.join('\n')).toMatch(/L1: 名前に「:」/)
		expect(errors.join('\n')).toMatch(/L2: 「直さない」は初めから/)
		expect(errors.join('\n')).toMatch(/L4: 同じ名前/)
		expect(errors.join('\n')).toMatch(/L6: decision は/)
	})
	it(`${MAX_ACTIONS} 個より多いと、そこから先は出さない`, () => {
		const text = Array.from({ length: MAX_ACTIONS + 2 }, (_, i) => `## a${i}`).join('\n')
		const { actions, errors } = parseActions(text)
		expect(actions).toHaveLength(MAX_ACTIONS)
		expect(errors).toHaveLength(2)
	})
})

describe('labelProblem', () => {
	it('空・長すぎる・区切りの「:」・初めからのボタンの名前は使えない', () => {
		expect(labelProblem('')).toMatch(/空/)
		expect(labelProblem('あ'.repeat(LABEL_CHARS + 1))).toMatch(/文字まで/)
		expect(labelProblem('あ'.repeat(LABEL_CHARS))).toBeNull()
		expect(labelProblem('a：b')).toMatch(/「:」/)
		for (const l of ['直さない', '直してもらう', '解決済みにする', 'Feedback に送った']) expect(labelProblem(l)).toMatch(/初めから/)
		expect(labelProblem('PR #65 待ち')).toBeNull()
	})
})

describe('addAction / removeAction', () => {
	it('ファイルが無ければ説明つきで作る。足したものは読み直せる', () => {
		const out = addAction(null, { label: '後で', agent: 'v0.5 に積む\n2 行目', decision: '後で' })
		expect(out).toMatch(/^# Review の選択肢\n/)
		expect(out.endsWith('## 後で\n- agent: v0.5 に積む 2 行目\n- decision: 後で\n')).toBe(true)
		expect(parseActions(out).actions).toEqual([{ label: '後で', agent: 'v0.5 に積む 2 行目', decision: '後で' }])
	})
	it('あるファイルには末尾に足す。ほかの行は変えない', () => {
		const out = addAction(FILE, { label: 'PR #65 待ち' })
		expect(out.startsWith(FILE.trimEnd())).toBe(true)
		expect(parseActions(out).actions.map((a) => a.label)).toEqual(['後で', 'このまま', 'PR #65 待ち'])
	})
	it('同じ名前・使えない名前は投げる', () => {
		expect(() => addAction(FILE, { label: '後で' })).toThrow(/もうあります/)
		expect(() => addAction(FILE, { label: '直さない' })).toThrow(/初めから/)
	})
	it('消すと見出しから次の見出しの手前までが無くなる。無い名前は投げる', () => {
		const out = removeAction(FILE, '後で')
		expect(parseActions(out).actions).toEqual([{ label: 'このまま' }])
		expect(out).toContain('前置き（読まない）')
		expect(out).not.toContain('人向けのメモ')
		expect(removeAction(addAction(FILE, { label: 'z' }), 'z')).toBe(FILE.trimEnd() + '\n')
		expect(() => removeAction(FILE, 'nope')).toThrow(/ありません/)
	})
})

describe('note', () => {
	it('名前とひとことを「: 」でつなぐ。頭を取り出せる', () => {
		expect(noteOf('後で', '')).toBe('後で')
		expect(noteOf('後で', ' v0.5 で ')).toBe('後で: v0.5 で')
		expect(noteHead('後で: v0.5: で')).toBe('後で')
		expect(noteHead('Feedback に送った: 261110-001')).toBe('Feedback に送った')
		expect(noteHead(undefined)).toBe('')
		const actions = parseActions(FILE).actions
		expect(actionOfNote('このまま: 読みやすい', actions)?.label).toBe('このまま')
		expect(actionOfNote('直した', actions)).toBeNull()
	})
})
