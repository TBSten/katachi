// toolbar のメッセージ（送る → 結果が返る）を、requestId で対応づける小さなツール。Feedback の保存も Memo の保存・読み込みも使う。
// toolbar の購読は外せないので、結果のイベントは作ったときに 1 回だけ登録する。

export type ServerLike = {
	on<T>(event: string, cb: (data: T) => void): void
	send<T>(event: string, data: T): void
}

/** 結果には、依頼と同じ requestId が載っている。 */
export type WithRequestId = { requestId: string }

/** 進行中の 1 件。cancel すると結果を受け取らない（待っている promise は決着しない）。 */
export type Call<Res> = { requestId: string; promise: Promise<Res | { ok: false; error: string; requestId: string }>; cancel: () => void }

export const TIMEOUT_MESSAGE = 'サーバーから応答がありません（時間切れ）'

/**
 * @param sendEvent 依頼のイベント名
 * @param resultEvent 結果のイベント名
 * @param timeoutMs これを過ぎたら `{ ok: false, error }` で決着させる
 */
// TODO: 時間切れのあとにサーバーが保存を終えると、送り直しで同じ記録が 2 件できる。requestId を記録に書いて同じ id を 2 回保存しない（review L8）。
export function createRpc<Req extends object, Res extends WithRequestId>(server: ServerLike, sendEvent: string, resultEvent: string, timeoutMs = 10_000) {
	const waiting = new Map<string, (res: Res) => void>()
	server.on<Res>(resultEvent, (res) => {
		const done = waiting.get(res?.requestId)
		if (done) done(res)
	})
	return {
		/** 依頼を送る。requestId はここで付ける。 */
		call(data: Req): Call<Res> {
			const requestId = `${Date.now()}-${Math.random().toString(36).slice(2)}`
			let timer: ReturnType<typeof setTimeout> | undefined
			const promise = new Promise<Res | { ok: false; error: string; requestId: string }>((resolve) => {
				waiting.set(requestId, (res) => {
					clearTimeout(timer)
					waiting.delete(requestId)
					resolve(res)
				})
				timer = setTimeout(() => {
					waiting.delete(requestId)
					resolve({ ok: false, error: TIMEOUT_MESSAGE, requestId })
				}, timeoutMs)
			})
			server.send(sendEvent, { ...data, requestId })
			return {
				requestId,
				promise,
				cancel: () => {
					clearTimeout(timer)
					waiting.delete(requestId)
				},
			}
		},
		/** 待っている件数（テスト用）。 */
		get pending() {
			return waiting.size
		},
	}
}
