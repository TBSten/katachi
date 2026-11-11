// shadow root の中の DOM を組み立てるための小さなツール。

/** 画面の主な入力がタッチかどうか（案内文と載せる情報の切り替えに使う）。 */
export const isCoarse = () => matchMedia('(pointer: coarse)').matches

/** 幅 600px 以下かどうか。true なら下から出るシート、false なら選択のそばの popover にする。 */
export const isNarrow = () => matchMedia('(max-width: 600px)').matches

export const el = <K extends keyof HTMLElementTagNameMap>(tag: K, cls?: string, text?: string) => {
	const e = document.createElement(tag)
	if (cls) e.className = cls
	if (text) e.textContent = text
	return e
}
