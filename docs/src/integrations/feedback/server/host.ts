// dev サーバーを `--host` で LAN に出していないかを見る。出していれば起動時に警告する（決定 4.11 A。止めはしない）。

/** 自分の PC からしか届かない host か。未指定（Vite の既定は localhost）も含む。 */
export function isLocalHost(host: string | boolean | undefined): boolean {
	if (host === undefined || host === false) return true
	if (host === true) return false
	return ['localhost', '127.0.0.1', '::1', '[::1]'].includes(host)
}

/** LAN に出ているときの警告文。ローカルだけなら null。 */
export function lanWarning(host: string | boolean | undefined): string | null {
	if (isLocalHost(host)) return null
	return 'dev サーバーが LAN に開いています（--host）。同じネットワークの人が、フィードバックの記録とメモを書き込め、メモを読み出せます。信頼できるネットワークでだけ使ってください。'
}
