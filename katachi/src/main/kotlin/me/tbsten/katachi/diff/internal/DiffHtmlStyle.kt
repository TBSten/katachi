package me.tbsten.katachi.diff.internal

// The page's stylesheet: the katachi design system's tokens, light first.
internal val DIFF_HTML_STYLE: String = """
/* Tokens copied from the katachi design system (docs site: Starlight + nova), light first. */
:root {
  --bg: #ffffff; --bg-muted: #f3f4f6; --bg-sunken: #e5e7eb; --bg-nav: #ffffffcc;
  --line: #d1d5dc; --hairline: #e5e7eb; --wash: #99a1af4d;
  --ink: #101828; --text: #364153; --muted: #6a7282; --accent: #1e2939; --on-accent: #ffffff;
  /* Heat is data, so it keeps a hue: the caution (amber) steps, pale -> deep. */
  --heat-low: #fef3c6; --heat-mid: #ffb900; --heat-high: #973c00;
  --success-low: #dcfce7; --success-high: #016630; --danger-low: #ffe2e2; --danger-high: #9f0712;
  --caution-low: #fef3c6; --caution: #ffb900; --caution-high: #973c00; --note-low: #dbeafe; --note-high: #193cb8;
  --neutral-low: #e5e7eb; --neutral-high: #101828; --sky: #a2ccf7; --line-strong: #99a1af; --success: #05df72; --danger: #ff6467;
  --font-sans: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", "Noto Sans", Arial, sans-serif;
  --font-mono: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  --radius-sm: 4px; --radius-md: 6px; --radius-lg: 8px; --radius-full: 9999px;
  --shadow-sm: 0px 1px 1px rgba(0, 0, 0, 0.06), 0px 2px 1px rgba(0, 0, 0, 0.06);
  --shadow-md: 0px 8px 4px rgba(0, 0, 0, 0.03), 0px 5px 2px rgba(0, 0, 0, 0.03), 0px 3px 2px rgba(0, 0, 0, 0.06), 0px 1px 1px rgba(0, 0, 0, 0.06);
}
@media (prefers-color-scheme: dark) {
  :root {
    --bg: #030712; --bg-muted: #1e2939; --bg-sunken: #1e2939; --bg-nav: #03071280;
    --line: #364153; --hairline: #1e2939;
    --ink: #ffffff; --text: #d1d5dc; --muted: #99a1af; --accent: #f3f4f6; --on-accent: #030712;
    --heat-low: #461901; --heat-mid: #7b3306; --heat-high: #fee685;
    --success-low: #032e15; --success-high: #b9f8cf; --danger-low: #460809; --danger-high: #ffc9c9;
    --caution-low: #461901; --caution: #7b3306; --caution-high: #fee685; --note-low: #162456; --note-high: #bedbff;
    --neutral-low: #030712; --neutral-high: #f3f4f6; --line-strong: #4a5565; --success: #0d542b; --danger: #82181a;
    --shadow-sm: 0px 1px 1px rgba(0, 0, 0, 0.12), 0px 2px 1px rgba(0, 0, 0, 0.24);
    --shadow-md: 0px 8px 4px rgba(0, 0, 0, 0.08), 0px 5px 2px rgba(0, 0, 0, 0.08), 0px 3px 2px rgba(0, 0, 0, 0.12), 0px 1px 1px rgba(0, 0, 0, 0.15);
  }
}
* { box-sizing: border-box; }
body { margin: 0; background: var(--bg); color: var(--text); font: 16px/1.75 var(--font-sans); }
header.site { position: sticky; top: 0; z-index: 2; display: flex; align-items: center; gap: 8px;
  padding: 12px 24px; background: var(--bg-nav); backdrop-filter: blur(8px); border-bottom: 1px solid var(--hairline); }
header.site svg { width: 28px; height: 28px; flex: none; }
header.site .site-title { font-size: 18px; font-weight: 600; color: var(--text); }
header.site .crumb { color: var(--muted); font-size: 14px; }
header.site .slash { color: var(--line-strong); }
header.site .spacer { flex: 1 1 auto; }
header.site .generated { font: 12px var(--font-mono); color: var(--muted); }
main { max-width: 1152px; margin: 0 auto; padding: 32px 24px 64px; }
@media (min-width: 800px) { main { padding: 40px 64px 64px; } }
h1 { font-size: 29px; line-height: 1.2; font-weight: 600; color: var(--ink); margin: 8px 0 0; }
.eyebrow { font: 500 11.52px var(--font-mono); letter-spacing: 0.1em; text-transform: uppercase; color: var(--muted); }
h1 .dots { color: var(--line-strong); font-weight: 400; }
.stats { display: flex; flex-wrap: wrap; gap: 24px; margin: 16px 0 24px; }
.stats > div { display: flex; flex-direction: column; gap: 2px; }
.stats .num { font-size: 24px; font-weight: 600; line-height: 1.2; color: var(--ink); }
.stats .of { font-size: 16px; font-weight: 400; color: var(--muted); }
.stats .cap { font-size: 13px; color: var(--muted); }
.stats .warn-stat .num, .stats .warn-stat .cap { color: var(--caution-high); }
.limit { margin: 0 0 24px; padding: 12px 16px; border: 1px solid var(--caution-high); border-radius: var(--radius-lg);
  background: var(--caution-low); color: var(--caution-high); font-size: 14px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 8px; margin-bottom: 12px; font-size: 13px; }
.actions { display: flex; gap: 6px; }
.toolbar button, .controls select { font: inherit; color: var(--text); background: var(--bg); cursor: pointer;
  border: 1px solid var(--line); border-radius: var(--radius-md); padding: 3px 10px; transition: background 150ms; }
.toolbar button:hover, .controls select:hover { background: var(--bg-muted); }
.anchor { display: inline-flex; align-items: center; vertical-align: middle; margin-left: 6px; padding: 2px;
  border: 0; background: none; color: var(--muted); cursor: pointer; border-radius: var(--radius-sm); opacity: 0; transition: opacity 150ms; }
.anchor svg { width: 14px; height: 14px; }
.anchor.copied { color: var(--success-high); }
tr:hover .anchor, tr:focus-within .anchor, .eyebrow:hover .anchor, details.unchanged summary:hover .anchor, .anchor:focus-visible { opacity: 1; }
@keyframes flash { from { background: var(--caution-low); } to { background: transparent; } }
.flash, .flash > td { animation: flash 1.5s ease-out; }
@media (prefers-reduced-motion: reduce) { .flash, .flash > td { animation: none; background: var(--caution-low); } }
.right { display: flex; gap: 6px; align-items: center; }
.menu { position: relative; }
.menu-button { display: inline-flex; align-items: center; gap: 6px; }
.menu-button svg { width: 16px; height: 16px; }
.menu-button[aria-expanded="true"] { background: var(--bg-muted); }
.controls { position: absolute; right: 0; top: calc(100% + 6px); z-index: 3; width: 300px; display: flex; flex-direction: column;
  gap: 12px; padding: 14px 16px; background: var(--bg); border: 1px solid var(--line); border-radius: var(--radius-lg); box-shadow: var(--shadow-md); }
.controls[hidden] { display: none; }
.controls label { display: flex; justify-content: space-between; gap: 8px; align-items: center; color: var(--muted); }
.controls .seg button { border: 0; border-radius: 0; }
.seg { display: inline-flex; border: 1px solid var(--line); border-radius: var(--radius-md); overflow: hidden; }
.seg button { font: inherit; color: var(--text); border: 0; background: transparent; padding: 3px 10px; cursor: pointer; transition: background 150ms; }
.seg button:hover { background: var(--bg-muted); }
.seg button.on { background: var(--accent); color: var(--on-accent); font-weight: 600; }
button:focus-visible, select:focus-visible, a:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
.table-wrap { overflow-x: auto; border: 1px solid var(--line); border-radius: var(--radius-lg); }
table { width: 100%; border-collapse: collapse; min-width: 720px; font-size: 14px; line-height: 1.4; }
th, td { padding: 6px 12px; border-bottom: 1px solid var(--hairline); text-align: left; white-space: nowrap; }
tbody tr:last-child td { border-bottom: 0; }
th { font-size: 13px; font-weight: 600; color: var(--muted); background: var(--bg-muted); position: sticky; top: 0; }
tbody tr:hover td { background: var(--bg-muted); }
tr.group > td { font-weight: 600; color: var(--ink); background: var(--bg-muted); }
tr.group .sub, tr.role .sub { font-size: 12px; }
.not-weighted { font-size: 12px; color: var(--caution-high); }
.not-weighted + .bar { display: none; }
tr.role > td.name .label { color: var(--ink); }
td.name { padding-left: calc(12px + var(--depth) * 20px); }
.toggle, .leaf { display: inline-block; width: 18px; border: 0; background: none; color: var(--muted);
  cursor: pointer; padding: 0; font: inherit; }
.toggle::before { content: ""; display: inline-block; width: 6px; height: 6px; border-right: 2px solid currentColor;
  border-bottom: 2px solid currentColor; transform: translateY(-2px) rotate(45deg); transition: transform 0.2s; }
tr.collapsed .toggle::before { transform: rotate(-45deg); }
.sub { color: var(--muted); font-weight: 400; margin-left: 8px; font-size: 13px; }
.weight, .status { font: 12px/1.2 var(--font-mono); border-radius: var(--radius-sm); padding: 1px 6px; margin-left: 6px;
  border: 1px solid transparent; }
.weight { color: var(--muted); background: transparent; border-color: var(--line); }
.status { color: var(--neutral-high); background: var(--neutral-low); border-color: var(--line); }
.status.added { color: var(--success-high); background: var(--success-low); border-color: var(--success); }
.status.untracked { color: var(--caution-high); background: var(--caution-low); border-color: var(--caution); }
.status.deleted { color: var(--danger-high); background: var(--danger-low); border-color: var(--danger); }
td.heat { width: 160px; }
.bar { display: block; height: 8px; border-radius: var(--radius-full); }
td.lines { font: 13px var(--font-mono); text-align: right; color: var(--ink); }
.add { color: var(--success-high); } .del { color: var(--danger-high); }
/* Stays a table cell: a flex td loses the row's background and borders. */
td.links > * + * { margin-left: 6px; }
.copy { font: 12px/1.6 var(--font-mono); color: var(--text); background: var(--bg-muted); cursor: pointer;
  border: 1px solid var(--line); border-radius: var(--radius-sm); padding: 1px 8px; text-decoration: none;
  display: inline-block; transition: background 150ms; }
.copy:hover { background: var(--bg-sunken); }
code.path { font: 13px var(--font-mono); color: var(--ink); }
tr.unassigned > td { background: var(--caution-low); color: var(--caution-high);
  border-top: 1px solid var(--caution); border-bottom: 1px solid var(--caution); }
tr.unassigned .label, tr.unassigned .sub, tr.unassigned .toggle { color: var(--caution-high); }
tr.unassigned .sub { font-weight: 500; }
tr.unassigned:hover > td { background: var(--caution-low); }
tr.unassigned-file > td { background: color-mix(in srgb, var(--caution-low) 45%, transparent); }
svg.warn { width: 16px; height: 16px; vertical-align: -3px; margin-right: 6px; }
tr.more .label { color: var(--muted); font-style: italic; }
tr.hidden { display: none; }
details.unchanged { margin-top: 24px; border: 1px solid var(--hairline); border-radius: var(--radius-lg); overflow: hidden; }
details.unchanged summary { display: flex; align-items: center; gap: 8px; padding: 10px 16px; cursor: pointer; list-style: none; }
details.unchanged summary::-webkit-details-marker { display: none; }
details.unchanged .chev { width: 6px; height: 6px; border-right: 2px solid var(--muted); border-bottom: 2px solid var(--muted);
  transform: rotate(-45deg); transition: transform 0.2s; margin-right: 4px; }
details.unchanged[open] .chev { transform: translateY(-2px) rotate(45deg); }
details.unchanged .title { font-size: 16px; font-weight: 600; color: var(--ink); }
details.unchanged .count { font-size: 13px; color: var(--muted); }
.unchanged-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 16px 24px;
  padding: 12px 16px 16px; border-top: 1px solid var(--hairline); font-size: 13px; line-height: 1.75; color: var(--muted); }
.unchanged-grid .group-name { font-weight: 600; }
.unchanged-grid ul { margin: 0; padding-left: 18px; list-style: none; }
.unchanged-grid b { font-weight: 600; }
footer { margin-top: 24px; padding-top: 16px; border-top: 1px solid var(--hairline); color: var(--muted); font-size: 12px;
  display: flex; flex-wrap: wrap; gap: 4px 16px; }
footer code { font: 12px var(--font-mono); }
.status.modified { display: none; }
tr.filtered, tr.limited { display: none; }
.unchanged-grid .changed { display: none; }
h1 .scope { font-size: 16px; font-weight: 400; color: var(--muted); }
details.commits { margin: 0 0 16px; border: 1px solid var(--hairline); border-radius: var(--radius-lg); font-size: 13px; }
details.commits summary { display: flex; align-items: center; gap: 8px; padding: 8px 16px; cursor: pointer; list-style: none; }
details.commits summary::-webkit-details-marker { display: none; }
details.commits .chev { width: 6px; height: 6px; border-right: 2px solid var(--muted); border-bottom: 2px solid var(--muted);
  transform: rotate(-45deg); transition: transform 0.2s; margin-right: 4px; }
details.commits[open] .chev { transform: translateY(-2px) rotate(45deg); }
details.commits .title { font-size: 14px; font-weight: 600; color: var(--ink); }
details.commits .count { color: var(--muted); }
.commit-actions { margin-left: auto; display: flex; gap: 6px; }
.commit-actions button { font: inherit; color: var(--text); background: var(--bg); border: 1px solid var(--line);
  border-radius: var(--radius-md); padding: 1px 10px; cursor: pointer; }
.commit-actions button:hover { background: var(--bg-muted); }
details.commits ul { list-style: none; margin: 0; padding: 4px 8px 8px; border-top: 1px solid var(--hairline); max-height: 280px; overflow-y: auto; }
details.commits li label { display: grid; grid-template-columns: 18px 100px minmax(0, 1fr) 140px 84px; gap: 8px; align-items: center;
  padding: 3px 8px; border-radius: var(--radius-sm); cursor: pointer; }
details.commits li label:hover { background: var(--bg-muted); }
details.commits code { font: 12px var(--font-mono); color: var(--ink); white-space: nowrap; }
details.commits .subject { color: var(--text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
details.commits .author, details.commits .date { color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
details.commits .approx, details.commits .omitted { margin: 0 16px 8px; padding: 6px 10px; border-radius: var(--radius-md);
  background: var(--caution-low); color: var(--caution-high); }
details.commits input { accent-color: var(--accent); margin: 0; }
.unchanged-grid .nomatch { display: none; }
.search { font: inherit; font-size: 13px; color: var(--text); background: var(--bg); border: 1px solid var(--line);
  border-radius: var(--radius-md); padding: 3px 10px; width: 220px; }
.search:focus-visible { outline: 2px solid var(--accent); outline-offset: 1px; }
.warn-only { display: inline-flex; align-items: center; gap: 6px; color: var(--caution-high); cursor: pointer; }
.warn-only input { accent-color: var(--caution-high); margin: 0; }
.actions { flex-wrap: wrap; align-items: center; }
details.commits li.linked label { background: var(--note-low); box-shadow: inset 3px 0 0 var(--note-high); }
tbody tr.linked > td { background: var(--note-low); }
tbody tr.linked > td.name { box-shadow: inset 3px 0 0 var(--note-high); }
"""
