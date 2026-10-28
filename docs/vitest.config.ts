import { defineConfig } from 'vitest/config'

// フィードバックモードの単体テスト。DOM が要るファイルは先頭の `// @vitest-environment happy-dom` で切り替える。
export default defineConfig({
	test: { include: ['src/integrations/feedback/test/**/*.test.ts'] },
})
