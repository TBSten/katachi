// Review の見た目。shadow root の中だけで効く（共通の土台は shared/shell.css.ts）。
import { shellCss } from '../shared/shell.css'

export const css = shellCss + `
:host { --p-high: #dc2626; --p-mid: #ea580c; --p-low: #ca8a04; --p-min: #64748b; --p-none: #6366f1; --p-done: #16a34a; }
button, input { font: inherit; }
/* 枠と印。ページは触れたまま（枠はクリックを通す。印だけ押せる）。 */
.layer { position: fixed; inset: 0; pointer-events: none; }
.box { position: fixed; border: 2px dashed var(--c); border-radius: 4px; background: color-mix(in srgb, var(--c) 3%, transparent); pointer-events: none; }
.box.is-active { border-style: solid; border-width: 3px; background: color-mix(in srgb, var(--c) 7%, transparent); box-shadow: 0 0 0 3px color-mix(in srgb, var(--c) 25%, transparent); }
.box.is-resolved { opacity: .45; }
.pin { position: fixed; display: inline-flex; align-items: center; justify-content: center; min-width: 22px; height: 22px; padding: 0 5px; border: 1px solid #fff; border-radius: 11px; background: var(--c); color: #fff; font-size: 11px; font-weight: 700; line-height: 1; box-shadow: 0 1px 4px rgba(0,0,0,.4); pointer-events: auto; cursor: pointer; }
.pin.is-resolved { background: var(--p-done); opacity: .7; }
.pin.is-active { outline: 3px solid color-mix(in srgb, var(--c) 40%, transparent); }
.pin.is-hover { z-index: 1; transform: scale(1.25); outline: 3px solid color-mix(in srgb, var(--c) 40%, transparent); }
.box.is-hover { border-style: solid; background: color-mix(in srgb, var(--c) 10%, transparent); }
.pin:focus-visible { outline: 3px solid var(--link); }
[data-tier=high] { --c: var(--p-high); } [data-tier=mid] { --c: var(--p-mid); } [data-tier=low] { --c: var(--p-low); } [data-tier=min] { --c: var(--p-min); } [data-tier=none] { --c: var(--p-none); }
.box[data-active-tier=high] { --c: var(--p-high); } .box[data-active-tier=mid] { --c: var(--p-mid); } .box[data-active-tier=low] { --c: var(--p-low); } .box[data-active-tier=min] { --c: var(--p-min); } .box[data-active-tier=none] { --c: var(--p-none); }
.box.is-active.is-resolved { opacity: 1; }

/* popup。高さには上限があり、本文だけがスクロールする（ページまでスクロールが抜けない）。 */
.pop { position: fixed; z-index: 2; display: flex; flex-direction: column; width: 380px; max-width: calc(100vw - 16px); max-height: min(60vh, 520px); background: var(--bg); border: 1px solid var(--border); border-top: 4px solid var(--c); border-radius: 10px; box-shadow: 0 10px 30px rgba(0,0,0,.3); pointer-events: auto; }
.pop[hidden] { display: none; }
.pop-head { flex: none; display: flex; align-items: center; gap: 6px; padding: 6px 6px 6px 12px; border-bottom: 1px solid var(--border); }
.chip { flex: none; padding: 1px 7px; border-radius: 9px; background: var(--c); color: #fff; font-size: 11px; font-weight: 700; }
.badge { flex: none; padding: 1px 7px; border-radius: 9px; font-size: 11px; font-weight: 600; color: var(--p-done); border: 1px solid currentColor; }
.where { flex: 1; min-width: 0; font-size: 11px; color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.where { cursor: copy; }
.where:hover { color: var(--fg-2); text-decoration: underline dotted; }
.wire { position: fixed; inset: 0; width: 100vw; height: 100vh; overflow: visible; pointer-events: none; z-index: 1; }
.wire path { fill: none; stroke: var(--c); stroke-width: 2; stroke-dasharray: 5 4; opacity: .8; }
.where-line { font-weight: 600; color: var(--fg-2); }
.edit { flex: none; padding: 1px 7px; font: inherit; font-size: 11px; color: var(--fg-2); background: transparent; border: 1px solid var(--border); border-radius: 6px; cursor: pointer; }
.edit:hover { background: var(--hover); }
.x { flex: none; width: 30px; height: 30px; font-size: 18px; line-height: 1; color: var(--fg-2); background: none; border: 0; border-radius: 6px; cursor: pointer; }
.x:hover { background: var(--hover); }
.pop-body { flex: 1 1 auto; min-height: 0; overflow-y: auto; overscroll-behavior: contain; padding: 10px 14px; }
.pop-title { margin: 0 0 6px; font-size: 14px; line-height: 1.45; overflow-wrap: anywhere; }
.md { font-size: 13px; line-height: 1.6; overflow-wrap: anywhere; }
.md p, .md ul, .md ol, .md pre, .md blockquote { margin: 0 0 6px; }
.md ul, .md ol { padding-left: 18px; }
.md code { padding: 0 3px; font: 12px/1.5 ui-monospace, SFMono-Regular, Menlo, monospace; background: var(--soft); border-radius: 3px; }
.md pre { padding: 6px 8px; overflow-x: auto; background: var(--soft); border-radius: 6px; }
.md pre code { padding: 0; background: none; white-space: pre; }
.md blockquote { padding-left: 8px; border-left: 3px solid var(--border-strong); color: var(--fg-2); }
.md a { color: var(--link); }
.info { margin: 8px 0 0; padding: 6px 8px; font-size: 12px; border-radius: 6px; background: var(--soft); overflow-wrap: anywhere; }
.info.warn { color: var(--danger); background: var(--danger-bg); }
.info b { font-weight: 600; }
.pop-foot { flex: none; display: flex; flex-direction: column; gap: 6px; padding: 8px 10px; border-top: 1px solid var(--border); }
.act { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 6px; align-items: center; }
.act input { flex: 1 1 100%; min-width: 0; padding: 4px 8px; color: var(--fg); background: var(--bg); border: 1px solid var(--border-strong); border-radius: 6px; }
.btn { flex: none; padding: 4px 10px; color: var(--fg); background: var(--bg); border: 1px solid var(--border-strong); border-radius: 6px; cursor: pointer; white-space: nowrap; }
.btn:hover:not(:disabled) { background: var(--hover); }
.btn:disabled { opacity: .45; cursor: not-allowed; }
.btn.primary { color: #fff; background: var(--p-done); border-color: var(--p-done); font-weight: 600; }
.resolved-line { flex: 1; min-width: 0; font-size: 12px; color: var(--p-done); overflow-wrap: anywhere; }
.nav { display: flex; align-items: center; gap: 6px; }
.count { margin-left: auto; font-size: 11px; color: var(--muted); opacity: .8; font-variant-numeric: tabular-nums; }
.toast { z-index: 3; max-width: min(560px, calc(100vw - 32px)); }
.toast.warn { background: #92400e; }
.err { font-size: 12px; color: var(--danger); }
.err[hidden] { display: none; }
/* 人が足した選択肢（review/_actions.md）。初めからのボタンと見分けられるよう、点線の枠で 1 行にまとめる。 */
.custom-row { flex: 1 1 100%; display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 6px; }
.btn.custom { border-style: dashed; }
.btn.plus { padding: 4px 8px; color: var(--muted); border-style: dashed; border-color: var(--border); }
.btn.plus.is-on { color: var(--fg); background: var(--soft); border-style: solid; }
.actions-edit { flex: 1 1 100%; min-width: 0; display: flex; flex-direction: column; gap: 6px; padding: 8px; background: var(--soft); border-radius: 8px; }
.actions-top { display: flex; align-items: center; justify-content: space-between; gap: 6px; font-size: 12px; }
.action-list { display: flex; flex-wrap: wrap; gap: 4px; }
.action-chip { display: inline-flex; align-items: center; gap: 4px; padding: 1px 2px 1px 8px; font-size: 12px; background: var(--bg); border: 1px dashed var(--border-strong); border-radius: 12px; }
.action-decision { padding: 0 5px; font-size: 10px; color: var(--muted); border: 1px solid var(--border); border-radius: 8px; }
.action-x { width: 20px; height: 20px; padding: 0; font-size: 13px; line-height: 1; color: var(--fg-2); background: none; border: 0; border-radius: 10px; cursor: pointer; }
.action-x:hover:not(:disabled) { background: var(--hover); }
.action-form { display: flex; flex-direction: column; gap: 6px; }
.action-row { display: flex; gap: 6px; }
.action-form input { flex: 1 1 auto; min-width: 0; padding: 4px 8px; color: var(--fg); background: var(--bg); border: 1px solid var(--border-strong); border-radius: 6px; font: inherit; }
.action-form select { flex: 0 1 auto; max-width: 45%; padding: 4px; color: var(--fg); background: var(--bg); border: 1px solid var(--border-strong); border-radius: 6px; font: inherit; }
.action-hint { margin: 0; font-size: 11px; color: var(--muted); }
.action-hint.warn { color: var(--danger); }

/* 一覧のパネル（右上。畳める）。 */
.panel { position: fixed; top: 12px; right: 12px; z-index: 1; display: flex; flex-direction: column; width: 300px; max-width: calc(100vw - 24px); max-height: 60vh; background: var(--bg); border: 1px solid var(--border); border-radius: 12px; box-shadow: 0 8px 28px rgba(0,0,0,.3); pointer-events: auto; }
.panel-head { flex: none; display: flex; align-items: center; gap: 6px; padding: 6px 6px 6px 12px; }
.panel-head h2 { margin: 0; font-size: 14px; }
.n { flex: none; min-width: 18px; padding: 0 5px; border-radius: 9px; background: var(--soft); color: var(--fg-2); font-size: 11px; font-weight: 700; text-align: center; }
.sum { flex: 1; min-width: 0; font-size: 11px; color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.fold { flex: none; width: 30px; height: 30px; color: var(--fg-2); background: none; border: 0; border-radius: 6px; cursor: pointer; }
.fold:hover { background: var(--hover); }
.panel-body { flex: 1 1 auto; min-height: 0; overflow-y: auto; overscroll-behavior: contain; padding: 0 12px 12px; border-top: 1px solid var(--border); }
.panel.is-folded .panel-body { display: none; }
.filters { display: flex; flex-wrap: wrap; align-items: center; gap: 4px 10px; padding: 8px 0; font-size: 12px; }
.filters select { padding: 2px 4px; color: var(--fg); background: var(--bg); border: 1px solid var(--border-strong); border-radius: 6px; font: inherit; }
.filters label { display: inline-flex; align-items: center; gap: 4px; }
.sec { margin: 8px 0 4px; font-size: 12px; font-weight: 600; color: var(--fg-2); }
.rows { display: flex; flex-direction: column; gap: 2px; margin: 0; padding: 0; list-style: none; }
.row { display: flex; align-items: center; gap: 6px; width: 100%; padding: 4px 6px; text-align: left; color: var(--fg); background: none; border: 0; border-radius: 6px; cursor: pointer; text-decoration: none; }
.row:hover { background: var(--hover); }
.row.is-active { background: var(--hover); box-shadow: inset 3px 0 0 var(--c); }
.row .chip { min-width: 26px; text-align: center; }
.row.is-resolved .chip { background: var(--p-done); }
.row-title { flex: 1; min-width: 0; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.row-meta { flex: none; font-size: 11px; color: var(--muted); }
.row-meta.warn { color: var(--danger); }
.empty { font-size: 12px; color: var(--muted); }
.sets { margin: 10px 0 0; padding-top: 6px; border-top: 1px solid var(--border); font-size: 11px; color: var(--muted); }
.set { display: flex; gap: 8px; }
.set-title { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.set-n { flex: none; }
.errors { margin: 8px 0 0; padding: 6px 8px; font-size: 11px; color: var(--danger); background: var(--danger-bg); border-radius: 6px; overflow-wrap: anywhere; }
.errors summary { cursor: pointer; font-weight: 600; }
.errors ul { margin: 6px 0 0; padding-left: 16px; }

/* 幅 600px 以下: popup は下からのシート、パネルは畳むと小さな札。タッチは押せる大きさを 44px に。 */
@media (max-width: 600px) {
	.pop { left: 0 !important; right: 0; top: auto !important; bottom: 0; width: 100% !important; max-width: none; max-height: 55dvh; border-radius: 14px 14px 0 0; padding-bottom: env(safe-area-inset-bottom); }
	/* 下端の中央には Astro の toolbar があるので、一覧はその上に出す。畳んだ札はヘッダーの下の右端に置く（toolbar と重ねない）。 */
	.panel { top: auto; bottom: calc(76px + env(safe-area-inset-bottom)); left: 12px; right: auto; width: calc(100vw - 24px); max-height: 50dvh; }
	.panel.is-folded { top: 120px; bottom: auto; left: auto; right: 8px; width: auto; }
	.panel.is-folded .sum { display: none; }
	/* popup（シート）を開いているあいだは札を隠す（popup に閉じると前・次がある）。 */
	.panel.has-pop { display: none; }
	.act input, .action-form input, .action-form select { font-size: 16px; }
	/* トーストは下からのシートに隠れないよう、上に出す。 */
	.toast { top: calc(12px + env(safe-area-inset-top)); bottom: auto; left: 12px; right: 12px; max-width: none; }
}
@media (pointer: coarse) {
	.btn, .act input, .row, .action-form input, .action-form select { min-height: 44px; }
	.action-x { width: 32px; height: 32px; }
	.x, .fold { width: 44px; height: 44px; }
	.pin { min-width: 28px; height: 28px; border-radius: 14px; font-size: 13px; }
}
`
