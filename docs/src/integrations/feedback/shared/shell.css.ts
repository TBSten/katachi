// Feedback と Memo が共有する見た目: host の土台・シート用の幕・トースト。shadow root の中だけで効く。
export const shellCss = `
:host { all: initial; position: fixed; inset: 0; z-index: 2147483000; pointer-events: none; font: 13px/1.5 system-ui, sans-serif; color: var(--fg);
	/* 色は light-dark() で決める。どちらを使うかは host の color-scheme（createHost がページの Starlight のテーマに合わせる）。 */
	--bg: light-dark(#fff, #1f2023); --fg: light-dark(#1c1c1c, #ececf0); --fg-2: light-dark(#404040, #c8c8d0); --muted: light-dark(#737373, #a1a1aa);
	--border: light-dark(#d4d4d4, #3f3f46); --border-strong: light-dark(#a3a3a3, #71717a); --soft: light-dark(#f5f5f5, #2a2b2f); --hover: light-dark(#eef2ff, #33353b);
	--ok: light-dark(#166534, #86efac); --danger: light-dark(#b91c1c, #fca5a5); --danger-bg: light-dark(#fef2f2, #3b1d1d); --link: light-dark(#2563eb, #8ab4ff); }
* { box-sizing: border-box; }
.backdrop { position: fixed; inset: 0; display: flex; align-items: center; justify-content: center; background: rgba(0,0,0,.35); pointer-events: auto; }
.sheet-frame { position: fixed; inset: 0; display: flex; align-items: flex-end; justify-content: center; pointer-events: none; }
.toast { position: fixed; right: 16px; bottom: 56px; padding: 8px 14px; background: #166534; color: #fff; border-radius: 8px; box-shadow: 0 4px 16px rgba(0,0,0,.3); }
`
