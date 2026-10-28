// shadow root の中だけで効く CSS。ページ側の style には何も足さない（片付けで host を消せば全部消える）。
import { DRAG_CSS } from '../shared/drag'
import { shellCss } from '../shared/shell.css'

export const css = shellCss + DRAG_CSS + `
.frame { position: fixed; display: none; border: 2px solid #3b82f6; background: rgba(59,130,246,.12); border-radius: 3px; }
.frame.picked { border-color: #f59e0b; background: rgba(245,158,11,.16); }
.frame.multi::after { content: attr(data-n); position: absolute; left: -2px; top: -18px; min-width: 16px; padding: 0 4px; font-size: 11px; line-height: 16px; text-align: center; color: #fff; background: #d97706; border-radius: 3px; }
.banner .notice { flex-basis: 100%; font-size: 11px; color: #fcd34d; }
.banner { flex-wrap: wrap; position: fixed; left: 12px; bottom: 56px; max-width: min(420px, calc(100vw - 24px)); padding: 8px 8px 8px 12px; display: flex; align-items: center; gap: 10px; background: #1c1c1c; color: #fff; border-radius: 8px; box-shadow: 0 4px 16px rgba(0,0,0,.3); pointer-events: auto; }
.banner .exit, .banner .about { flex: none; min-height: 44px; min-width: 64px; padding: 0 14px; font: inherit; color: #fff; background: #3f3f46; border: 1px solid #71717a; border-radius: 6px; cursor: pointer; }
.fab { position: fixed; display: none; min-height: 40px; padding: 0 14px; font: inherit; font-weight: 600; color: #fff; background: #2563eb; border: 0; border-radius: 20px; box-shadow: 0 4px 14px rgba(0,0,0,.35); pointer-events: auto; cursor: pointer; }
.menu { position: fixed; min-width: 160px; padding: 4px; background: var(--bg); border: 1px solid var(--border); border-radius: 8px; box-shadow: 0 6px 20px rgba(0,0,0,.25); pointer-events: auto; }
.menu button { display: block; width: 100%; padding: 6px 12px; border: 0; background: none; text-align: left; font: inherit; color: inherit; border-radius: 4px; cursor: pointer; }
.menu button:hover { background: var(--hover); }
.menu .hint { padding: 2px 12px 4px; font-size: 11px; color: var(--muted); white-space: nowrap; }
.dialog { width: min(560px, calc(100vw - 24px)); max-height: calc(100vh - 24px); overflow: auto; padding: 16px; background: var(--bg); border-radius: 12px; box-shadow: 0 10px 40px rgba(0,0,0,.4); display: flex; flex-direction: column; gap: 10px; }
.dialog.popover .error:empty { display: none; }
/* 幅 600px より広いとき: 選択のそばに浮かぶ popover。幕は敷かず、ページは見えたまま操作できる。 */
.dialog.popover { position: fixed; left: 0; top: 0; width: 360px; max-width: calc(100vw - 16px); max-height: calc(100vh - 16px); padding: 10px; gap: 8px; border-radius: 10px; border: 1px solid var(--border); box-shadow: 0 8px 28px rgba(0,0,0,.3); pointer-events: auto; }
.dialog.popover textarea { min-height: 4.6em; max-height: 40vh; resize: none; }
.dialog.popover .info > summary { cursor: pointer; padding: 2px 0; color: var(--fg-2); }
.dialog.popover .info .item { margin-top: 4px; }
.dialog .target { display: flex; gap: 14px; margin: 0; padding: 0; border: 0; }
.dialog .target label { display: flex; align-items: center; gap: 4px; min-height: 28px; cursor: pointer; }
.dialog h2 { margin: 0; font-size: 15px; }
.dialog .head { display: flex; align-items: center; gap: 8px; }
.dialog .head h2 { flex: 1; }
.dialog.popover .head { margin: -4px -4px 0; padding: 2px 4px; }
.dialog .head .parent { padding: 3px 10px; font: inherit; font-size: 12px; background: var(--bg); border: 1px solid var(--border); border-radius: 6px; cursor: pointer; }
.dialog .head .parent:hover { background: var(--hover); }
.dialog textarea, .dialog input:not([type=radio]):not([type=checkbox]) { color: var(--fg); background: var(--bg); }
.dialog textarea { width: 100%; min-height: 96px; padding: 8px; font: inherit; border: 1px solid var(--border-strong); border-radius: 6px; resize: vertical; }
.dialog .item { border: 1px solid var(--border); border-radius: 6px; padding: 4px 8px; }
.dialog .item summary { cursor: pointer; }
.dialog .item label { font-weight: 600; margin-left: 4px; }
.dialog .item pre { margin: 4px 0 2px; padding: 6px; max-height: 140px; overflow: auto; background: var(--soft); border-radius: 4px; white-space: pre-wrap; word-break: break-all; font: 11px/1.4 ui-monospace, monospace; }
.dialog .error { color: var(--danger); }
.dialog .actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.dialog .actions button { white-space: nowrap; padding: 6px 16px; font: inherit; border-radius: 6px; border: 1px solid var(--border-strong); background: var(--bg); cursor: pointer; }
.dialog .actions button.primary { background: #2563eb; border-color: #2563eb; color: #fff; }
.dialog .actions button:disabled { opacity: .5; cursor: not-allowed; }
.dialog .actions .copy { margin-right: auto; }
.dialog .note { font-size: 11px; color: var(--muted); line-height: 1.4; }
/* 幅 600px 以下とタッチ端末: 16px 以上の入力、44px 以上の押せる高さ。 */
@media (max-width: 600px), (pointer: coarse) {
	.dialog textarea, .dialog input { font-size: 16px; }
	.dialog .item summary { display: flex; align-items: center; min-height: 44px; }
	.dialog .target label { min-height: 44px; }
	.dialog .target input { width: 22px; height: 22px; margin: 0; }
	.dialog .item input[type=checkbox] { width: 22px; height: 22px; margin: 0; }
	.dialog .item label { display: flex; align-items: center; min-height: 44px; flex: 1; }
	.dialog .actions button { min-height: 44px; min-width: 88px; }
}
/* 幅 600px 以下: 下から出るシート（幅いっぱい・最大 85dvh・中身はスクロール）。 */
@media (max-width: 600px) {
	.backdrop { align-items: flex-end; }
	.dialog { width: 100%; max-height: min(85dvh, 100%); border-radius: 16px 16px 0 0; overflow-y: auto; overscroll-behavior: contain; padding-bottom: 0; }
	/* 送信ボタンは常にシートの下端に見える。ホームバーの分の余白は行の側で取る。 */
	.dialog .actions { position: sticky; bottom: 0; padding: 8px 0 calc(16px + env(safe-area-inset-bottom)); background: var(--bg); }
	.banner { bottom: calc(64px + env(safe-area-inset-bottom)); }
}
`
