import { test as base } from '@playwright/test'
import { Fb } from './fb'
import { resetOut } from './store'

export { expect } from '@playwright/test'
export { ok, sleep, waitFor, BASIC } from './fb'

/** `fb` は Feedback / Memo の操作のツール。テストごとに保存先を空にして渡す。 */
export const test = base.extend<{ fb: Fb }>({
	fb: async ({ page, isMobile }, use, info) => {
		resetOut()
		const fb = new Fb(page, isMobile, info.project.name, info)
		await use(fb)
		fb.assertNoPageErrors()
	},
})
