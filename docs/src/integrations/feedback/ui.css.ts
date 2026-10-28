// shadow root の中だけで効く CSS。ページ側の style には何も足さない（片付けで host を消せば全部消える）。
export const css = `
:host { all: initial; position: fixed; inset: 0; z-index: 2147483000; pointer-events: none; font: 13px/1.5 system-ui, sans-serif; color: #1c1c1c; }
* { box-sizing: border-box; }
.frame { position: fixed; display: none; border: 2px solid #3b82f6; background: rgba(59,130,246,.12); border-radius: 3px; }
.frame.picked { border-color: #f59e0b; background: rgba(245,158,11,.16); }
.banner { position: fixed; left: 12px; bottom: 56px; max-width: min(420px, calc(100vw - 24px)); padding: 8px 8px 8px 12px; display: flex; align-items: center; gap: 10px; background: #1c1c1c; color: #fff; border-radius: 8px; box-shadow: 0 4px 16px rgba(0,0,0,.3); pointer-events: auto; }
.banner .exit { flex: none; min-height: 44px; min-width: 64px; padding: 0 14px; font: inherit; color: #fff; background: #3f3f46; border: 1px solid #71717a; border-radius: 6px; cursor: pointer; }
.fab { position: fixed; display: none; min-height: 40px; padding: 0 14px; font: inherit; font-weight: 600; color: #fff; background: #2563eb; border: 0; border-radius: 20px; box-shadow: 0 4px 14px rgba(0,0,0,.35); pointer-events: auto; cursor: pointer; }
.menu { position: fixed; min-width: 160px; padding: 4px; background: #fff; border: 1px solid #d4d4d4; border-radius: 8px; box-shadow: 0 6px 20px rgba(0,0,0,.25); pointer-events: auto; }
.menu button { display: block; width: 100%; padding: 6px 12px; border: 0; background: none; text-align: left; font: inherit; color: inherit; border-radius: 4px; cursor: pointer; }
.menu button:hover { background: #eef2ff; }
.backdrop { position: fixed; inset: 0; display: flex; align-items: center; justify-content: center; background: rgba(0,0,0,.35); pointer-events: auto; }
.dialog { width: min(560px, calc(100vw - 24px)); max-height: calc(100vh - 24px); overflow: auto; padding: 16px; background: #fff; border-radius: 12px; box-shadow: 0 10px 40px rgba(0,0,0,.4); display: flex; flex-direction: column; gap: 10px; }
.dialog h2 { margin: 0; font-size: 15px; }
.dialog textarea { width: 100%; min-height: 96px; padding: 8px; font: inherit; border: 1px solid #a3a3a3; border-radius: 6px; resize: vertical; }
.dialog .item { border: 1px solid #e5e5e5; border-radius: 6px; padding: 4px 8px; }
.dialog .item summary { cursor: pointer; }
.dialog .item label { font-weight: 600; margin-left: 4px; }
.dialog .item pre { margin: 4px 0 2px; padding: 6px; max-height: 140px; overflow: auto; background: #f5f5f5; border-radius: 4px; white-space: pre-wrap; word-break: break-all; font: 11px/1.4 ui-monospace, monospace; }
.dialog .error { color: #b91c1c; }
.dialog .actions { display: flex; justify-content: flex-end; gap: 8px; }
.dialog .actions button { padding: 6px 16px; font: inherit; border-radius: 6px; border: 1px solid #a3a3a3; background: #fff; cursor: pointer; }
.dialog .actions button.primary { background: #2563eb; border-color: #2563eb; color: #fff; }
.dialog .actions button:disabled { opacity: .5; cursor: not-allowed; }
.toast { position: fixed; right: 16px; bottom: 56px; padding: 8px 14px; background: #166534; color: #fff; border-radius: 8px; box-shadow: 0 4px 16px rgba(0,0,0,.3); }
/* 幅 600px 以下とタッチ端末: 16px 以上の入力、44px 以上の押せる高さ。 */
@media (max-width: 600px), (pointer: coarse) {
	.dialog textarea, .dialog input { font-size: 16px; }
	.dialog .item summary { display: flex; align-items: center; min-height: 44px; }
	.dialog .item input[type=checkbox] { width: 22px; height: 22px; margin: 0; }
	.dialog .item label { display: flex; align-items: center; min-height: 44px; flex: 1; }
	.dialog .actions button { min-height: 44px; min-width: 88px; }
}
/* 幅 600px 以下: 下から出るシート（幅いっぱい・最大 85dvh・中身はスクロール）。 */
@media (max-width: 600px) {
	.backdrop { align-items: flex-end; }
	.dialog { width: 100%; max-height: min(85dvh, 100%); border-radius: 16px 16px 0 0; overflow-y: auto; overscroll-behavior: contain; padding-bottom: 0; }
	/* 送信ボタンは常にシートの下端に見える。ホームバーの分の余白は行の側で取る。 */
	.dialog .actions { position: sticky; bottom: 0; padding: 8px 0 calc(16px + env(safe-area-inset-bottom)); background: #fff; }
	.banner { bottom: calc(64px + env(safe-area-inset-bottom)); }
}
`
