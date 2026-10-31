// 送信中のボタンの文字。今どの段階か（撮影・保存）と、長くかかっているときは経過秒数を見せる。

export type BusyStage = '撮影中…' | '保存中…'

/** これより長くかかったら経過秒数を添える（ms）。 */
export const SHOW_SECONDS_AFTER = 2000
/** 文字を書き直す間隔（ms）。撮影の複製の間は画面が止まるので、止まる前と後でしか変わらない。 */
const TICK = 500

/** 段階と経過時間からボタンの文字を作る。2 秒までは段階だけ、超えたら「撮影中…（3 秒）」。 */
export function busyLabel(stage: BusyStage, elapsedMs: number): string {
	return elapsedMs > SHOW_SECONDS_AFTER ? `${stage}（${Math.floor(elapsedMs / 1000)} 秒）` : stage
}

/**
 * 段階ごとに文字を書き直す。stage() で段階を進め（経過秒数は段階ごとに 0 から数える）、stop() で止める。
 * setLabel にはボタンの文字を書く関数を渡す。
 */
export function busyTicker(setLabel: (label: string) => void, now: () => number = () => performance.now()) {
	let stage: BusyStage | null = null
	let since = 0
	const render = () => {
		if (stage) setLabel(busyLabel(stage, now() - since))
	}
	const timer = setInterval(render, TICK)
	return {
		stage(next: BusyStage) {
			stage = next
			since = now()
			render()
		},
		stop() {
			clearInterval(timer)
			stage = null
		},
	}
}
