// Feedback と Memo が共有する見た目: host の土台・シート用の幕・トースト。shadow root の中だけで効く。
export const shellCss = `
:host { all: initial; position: fixed; inset: 0; z-index: 2147483000; pointer-events: none; font: 13px/1.5 system-ui, sans-serif; color: #1c1c1c; }
* { box-sizing: border-box; }
.backdrop { position: fixed; inset: 0; display: flex; align-items: center; justify-content: center; background: rgba(0,0,0,.35); pointer-events: auto; }
.sheet-frame { position: fixed; inset: 0; display: flex; align-items: flex-end; justify-content: center; pointer-events: none; }
.toast { position: fixed; right: 16px; bottom: 56px; padding: 8px 14px; background: #166534; color: #fff; border-radius: 8px; box-shadow: 0 4px 16px rgba(0,0,0,.3); }
`
