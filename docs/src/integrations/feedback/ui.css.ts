// shadow root の中だけで効く CSS。ページ側の style には何も足さない（片付けで host を消せば全部消える）。
export const css = `
:host { all: initial; position: fixed; inset: 0; z-index: 2147483000; pointer-events: none; font: 13px/1.5 system-ui, sans-serif; color: #1c1c1c; }
* { box-sizing: border-box; }
.frame { position: fixed; display: none; border: 2px solid #3b82f6; background: rgba(59,130,246,.12); border-radius: 3px; }
.frame.picked { border-color: #f59e0b; background: rgba(245,158,11,.16); }
.banner { position: fixed; left: 12px; bottom: 56px; max-width: 360px; padding: 8px 12px; background: #1c1c1c; color: #fff; border-radius: 8px; box-shadow: 0 4px 16px rgba(0,0,0,.3); }
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
`
