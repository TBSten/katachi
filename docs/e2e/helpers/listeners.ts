// 自前（integrations/feedback）のスクリプトが document / window に登録したリスナーだけを数える（CDP）。
// Playwright 自身が足すリスナーは除く。Chromium だけで使える。
import type { Page } from '@playwright/test'

export async function attachListenerProbe(page: Page) {
	const cdp = await page.context().newCDPSession(page)
	const scriptUrl = new Map<string, string>()
	cdp.on('Debugger.scriptParsed', (e) => scriptUrl.set(e.scriptId, e.url))
	await cdp.send('Debugger.enable')
	await page.reload({ waitUntil: 'networkidle' }) // 登録済みのスクリプトの URL を拾い直す
	await page.waitForTimeout(1500)
	return {
		async list(): Promise<string> {
			const out: string[] = []
			for (const expr of ['document', 'window']) {
				const { result } = await cdp.send('Runtime.evaluate', { expression: expr })
				const { listeners } = await cdp.send('DOMDebugger.getEventListeners', { objectId: result.objectId! })
				out.push(...listeners.filter((l) => (scriptUrl.get(l.scriptId) ?? '').includes('integrations/feedback')).map((l) => `${expr}:${l.type}`))
			}
			return out.sort().join(' ')
		},
		count: (list: string): number => list.split(' ').filter(Boolean).length,
	}
}
