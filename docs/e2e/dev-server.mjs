// Playwright の webServer.command から呼ぶ。docs を写し（repo-copy）、写しの中で astro dev を立てる。
// 終了の合図（SIGTERM / SIGINT）が来たら dev も止める。
import { spawn, spawnSync } from 'node:child_process'
import { existsSync, mkdirSync, rmSync, symlinkSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { COPY_CONFIG, COPY_DOCS, DOCS_DIR, FEEDBACK_OUT, PORT, REPO_ROOT, VITE_CACHE } from './env.mjs'

mkdirSync(COPY_DOCS, { recursive: true })
const rsync = spawnSync(
	'rsync',
	['-a', '--delete', '--exclude', 'node_modules', '--exclude', 'public', '--exclude', '.astro', '--exclude', 'dist', '--exclude', 'e2e', '--exclude', 'test-results', '--exclude', 'playwright-report', `${DOCS_DIR}/`, `${COPY_DOCS}/`],
	{ stdio: 'inherit' },
)
if (rsync.status !== 0) process.exit(rsync.status ?? 1)
for (const name of ['node_modules', 'public']) {
	const link = join(COPY_DOCS, name)
	if (!existsSync(link)) symlinkSync(join(DOCS_DIR, name), link)
}
// Vite のキャッシュを写し専用にする（env.mjs の VITE_CACHE）。設定は本物の astro.config.mjs を読み、cacheDir だけ差し替える。
writeFileSync(
	join(COPY_DOCS, COPY_CONFIG),
	[
		'// e2e/dev-server.mjs が書く。手で直さない。',
		"import config from './astro.config.mjs'",
		`export default { ...config, vite: { ...config.vite, cacheDir: ${JSON.stringify(VITE_CACHE)} } }`,
		'',
	].join('\n'),
)
// 前回の記録を持ち越さない
rmSync(FEEDBACK_OUT, { recursive: true, force: true })
mkdirSync(FEEDBACK_OUT, { recursive: true })

// AI エージェントの環境では astro が背景に回って終わってしまうので、前面で動かす（--ignore-lock）。
const dev = spawn('pnpm', ['exec', 'astro', 'dev', '--config', COPY_CONFIG, '--port', String(PORT), '--strictPort', '--ignore-lock'], {
	cwd: COPY_DOCS,
	stdio: 'inherit',
	env: {
		...process.env,
		// 写しは .git を持たないので、git の情報は本物のリポジトリを指す
		GIT_DIR: join(REPO_ROOT, '.git'),
		GIT_WORK_TREE: REPO_ROOT,
		KATACHI_FEEDBACK_DIR: FEEDBACK_OUT,
	},
})
const stop = () => dev.kill('SIGTERM')
process.on('SIGTERM', stop)
process.on('SIGINT', stop)
dev.on('exit', (code) => process.exit(code ?? 0))
