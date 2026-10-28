import { describe, expect, it } from 'vitest'
import { isLocalHost, lanWarning } from '../server/host'

describe('--host の警告', () => {
	it.each([[undefined], [false], ['localhost'], ['127.0.0.1'], ['::1']])('%s ならローカルだけ。警告しない', (host) => {
		expect(isLocalHost(host)).toBe(true)
		expect(lanWarning(host)).toBeNull()
	})
	it.each([[true], ['0.0.0.0'], ['192.168.0.5'], ['::']])('%s は LAN に出る。警告する', (host) => {
		expect(isLocalHost(host)).toBe(false)
		expect(lanWarning(host)).toContain('書き込め')
		expect(lanWarning(host)).toContain('読み出せ')
	})
})
