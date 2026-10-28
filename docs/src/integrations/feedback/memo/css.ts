// Memo の見た目。shadow root の中だけで効く（共通の土台は shared/shell.css.ts）。
import { DRAG_CSS } from '../shared/drag'
import { shellCss } from '../shared/shell.css'

export const css = shellCss + DRAG_CSS + `
.panel { pointer-events: auto; display: flex; flex-direction: column; background: var(--bg); border: 1px solid var(--border); box-shadow: 0 8px 28px rgba(0,0,0,.3); }
.panel[hidden], .sheet-frame[hidden] { display: none; }
/* 幅 600px より広い: 右上の大きめの popover（最大の高さは 70vh、中身はスクロール）。 */
.panel.popover { position: fixed; top: 12px; right: 12px; width: 420px; max-width: calc(100vw - 24px); max-height: 70vh; border-radius: 12px; }
.head { flex: none; display: flex; align-items: center; gap: 8px; padding: 8px 8px 8px 14px; border-bottom: 1px solid var(--border); }
.head h2 { margin: 0; font-size: 15px; }
.status { flex: 1; min-width: 0; font-size: 11px; color: var(--muted); text-align: right; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.status[data-state=saved] { color: var(--ok); }
.status[data-state=error] { color: var(--danger); }
.close { flex: none; width: 32px; height: 32px; font: inherit; font-size: 20px; line-height: 1; color: var(--fg-2); background: none; border: 0; border-radius: 6px; cursor: pointer; }
.close:hover { background: var(--hover); }
.body { flex: 1; min-height: 0; overflow-y: auto; overscroll-behavior: contain; padding: 10px 14px 14px; display: flex; flex-direction: column; gap: 12px; }
.section-title { margin: 0 0 4px; font-size: 12px; font-weight: 600; color: var(--fg-2); }
.note { display: flex; flex-direction: column; gap: 6px; }
.fields { display: grid; grid-template-columns: fit-content(40%) minmax(0, 1fr) auto; align-items: center; gap: 4px 8px; }
.field { display: contents; }
.field-label { min-width: 0; font-weight: 600; overflow-wrap: anywhere; }
.panel input, .panel textarea { color: var(--fg); background: var(--bg); }
.field input, .add-box input { flex: 1; min-width: 0; padding: 4px 8px; font: inherit; border: 1px solid var(--border-strong); border-radius: 6px; }
.field-drop { flex: none; width: 24px; height: 24px; padding: 0; font: inherit; color: var(--muted); background: none; border: 0; border-radius: 4px; cursor: pointer; }
.field-drop:hover { background: var(--hover); color: var(--danger); }
.add-box { display: flex; align-items: center; gap: 6px; }
.add-field { align-self: flex-start; padding: 2px 4px; font: inherit; color: var(--link); background: none; border: 0; cursor: pointer; }
.add-ok, .add-cancel, .pick, .reveal, .delete, .pill-cancel { font: inherit; padding: 4px 12px; border: 1px solid var(--border-strong); border-radius: 6px; background: var(--bg); cursor: pointer; }
.pick { align-self: flex-start; color: #fff; background: #2563eb; border-color: #2563eb; font-weight: 600; }
.pick:disabled { opacity: .5; cursor: not-allowed; }
.note-text { width: 100%; padding: 8px; font: inherit; border: 1px solid var(--border-strong); border-radius: 6px; resize: none; overflow: hidden; }
.spots { display: flex; flex-direction: column; gap: 10px; }
.card { display: flex; flex-direction: column; gap: 6px; padding: 8px; border: 1px solid var(--border); border-radius: 8px; background: var(--soft); }
.card.is-lost { border-style: dashed; }
.card.is-active { border-color: #2563eb; box-shadow: 0 0 0 2px rgba(37,99,235,.25); }
.card-head { display: flex; align-items: flex-start; gap: 8px; }
.num, .mark { flex: none; display: inline-flex; align-items: center; justify-content: center; width: 20px; height: 20px; border-radius: 50%; background: #f59e0b; color: #1c1c1c; font-size: 11px; font-weight: 700; }
.where { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.where-title { overflow-wrap: anywhere; }
.where-src, .where-quote { font-size: 11px; color: var(--muted); overflow-wrap: anywhere; }
.lost { flex: none; font-size: 11px; color: var(--danger); }
.card-foot { display: flex; justify-content: flex-end; gap: 8px; }
.delete { color: var(--danger); }
.reveal:disabled { opacity: .5; cursor: not-allowed; }
.empty-hint { font-size: 12px; color: var(--muted); }
.loading { color: var(--muted); }
.blocked { padding: 8px; color: var(--danger); background: var(--danger-bg); border-radius: 6px; }
/* ページに出す印・光る枠・選んでいる間の枠と帯。 */
.marks { position: fixed; inset: 0; pointer-events: none; }
.mark { position: fixed; padding: 0; border: 1px solid #fff; box-shadow: 0 1px 4px rgba(0,0,0,.4); pointer-events: auto; cursor: pointer; font-family: inherit; }
.flash { position: fixed; border: 3px solid #f59e0b; border-radius: 4px; background: rgba(245,158,11,.2); animation: memo-flash .6s ease-in-out 3; pointer-events: none; }
@keyframes memo-flash { 50% { background: rgba(245,158,11,.5); } }
.frame { position: fixed; display: none; border: 2px solid #3b82f6; background: rgba(59,130,246,.12); border-radius: 3px; pointer-events: none; }
.pill { position: fixed; top: 12px; left: 50%; transform: translateX(-50%); width: max-content; max-width: calc(100vw - 24px); display: flex; align-items: center; gap: 10px; padding: 8px 10px 8px 14px; background: #1c1c1c; color: #fff; border-radius: 10px; box-shadow: 0 4px 16px rgba(0,0,0,.3); pointer-events: auto; }
.pill[hidden] { display: none; }
.pill-cancel { color: #fff; background: #3f3f46; border-color: #71717a; }
/* 幅 600px 以下とタッチ端末: 16px 以上の入力、44px 以上の押せる高さ。 */
@media (max-width: 600px), (pointer: coarse) {
	.panel input, .panel textarea { font-size: 16px; }
	.field input, .add-box input { min-height: 44px; }
	.add-field, .add-ok, .add-cancel, .pick, .reveal, .delete, .pill-cancel { min-height: 44px; }
	.field-drop { width: 44px; height: 44px; }
	.close { width: 44px; height: 44px; }
	.mark { width: 28px; height: 28px; font-size: 13px; }
}
/* 幅 600px 以下: 下から出るシート（幅いっぱい・最大 75dvh・中身はスクロール）。ページは見えたまま触れる。 */
@media (max-width: 600px) {
	.panel.sheet { width: 100%; max-height: 75dvh; border-radius: 16px 16px 0 0; border-bottom: 0; padding-bottom: env(safe-area-inset-bottom); }
	.pill { top: calc(8px + env(safe-area-inset-top)); }
}
`
