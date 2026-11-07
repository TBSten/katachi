package me.tbsten.katachi.diff.internal

// The page's script: sorting, folding, the view menu, the URL state and the anchors.
internal val DIFF_HTML_SCRIPT: String = """
(() => {
  const state = { lines: "total", score: "weighted", path: "short", sort: "definition", q: "", warn: false };
  const onRecompute = [];
  const tbody = document.querySelector("tbody");
  const rows = [...tbody.querySelectorAll("tr")];
  const byId = new Map(rows.map(r => [r.dataset.id, r]));
  const children = new Map();
  for (const r of rows) {
    const p = r.dataset.parent;
    if (!children.has(p)) children.set(p, []);
    children.get(p).push(r);
  }
  for (const r of rows) {
    const hasKids = (children.get(r.dataset.id) || []).length > 0;
    if (r.dataset.changed === "false" || r.dataset.collapsedDefault === "true") r.classList.add("collapsed");
    if (!hasKids) { const t = r.querySelector(".toggle"); if (t) t.style.visibility = "hidden"; }
  }
  // The no-role and ignored groups hold files no role owns, so no weight applies to them or their files.
  const isRoleless = r => r.classList.contains("unassigned") || r.classList.contains("ignored");
  const value = r => state.score === "weighted" && !isRoleless(r) && !isRolelessFile(r)
    ? Number(r.dataset.weighted) : Number(r.dataset.added) + Number(r.dataset.deleted);
  const isRolelessFile = r => { const p = byId.get(r.dataset.parent); return p && isRoleless(p); };
  // Three amber stops read from the theme: a low ratio is the pale one, the hottest row the deepest.
  const css = getComputedStyle(document.documentElement);
  const rgb = name => { const h = css.getPropertyValue(name).trim().replace("#", "");
    return [0, 2, 4].map(i => parseInt(h.slice(i, i + 2), 16)); };
  const stops = [rgb("--heat-low"), rgb("--heat-mid"), rgb("--heat-high")];
  const heatColor = ratio => {
    const t = Math.min(1, Math.max(0, ratio)) * 2, i = Math.min(1, Math.floor(t)), f = t - i;
    const c = stops[i].map((v, k) => Math.round(v + (stops[i + 1][k] - v) * f));
    return "rgb(" + c.join(", ") + ")";
  };
  // Commits: the page re-adds the report for the commits that are checked. With every commit checked
  // the numbers are the net diff the report was made from; with some, they are summed per commit.
  const data = JSON.parse(document.getElementById("diff-data").textContent);
  const boxes = [...document.querySelectorAll("input[data-commit]")];
  const maxFiles = Number(document.querySelector("table").dataset.maxFiles) || 0;
  const fullImportance = Number(document.querySelector("table").dataset.fullImportance);
  const fileRows = rows.filter(r => r.classList.contains("file") && !r.classList.contains("more"));
  const statusOf = r => r.querySelector(".status");
  const net = new Map(fileRows.map(r => [r, { added: r.dataset.added, deleted: r.dataset.deleted, binary: r.dataset.binary,
    status: statusOf(r) ? statusOf(r).className : "", label: statusOf(r) ? statusOf(r).textContent : "" }]));
  const commitIndex = new Map(data.commits.map((c, i) => [c.short, i]));
  const STATUS = { A: "added", M: "modified", D: "deleted", R: "renamed", U: "untracked" };
  const pathOf = r => r.dataset.key.slice("file:".length);
  const roleWeight = r => { const p = byId.get(r.dataset.parent); return p && p.dataset.weight ? Number(p.dataset.weight) : 0; };
  const files = n => n === 1 ? "1 file" : n + " files";
  const selected = () => boxes.filter(b => b.checked).map(b => commitIndex.get(b.dataset.commit));
  const allSelected = () => boxes.every(b => b.checked);
  const isIn = r => !r.classList.contains("filtered");
  function recompute() {
    const all = allSelected(), sel = new Set(selected());
    for (const r of fileRows) {
      let a = 0, d = 0, bin = false, status = null, touched = all;
      if (all) {
        const n = net.get(r); a = Number(n.added); d = Number(n.deleted); bin = n.binary === "true";
      } else {
        const letters = [];
        for (const [i, ad, de, b, st] of (data.files[pathOf(r)] || [])) {
          if (!sel.has(i)) continue;
          a += ad; d += de; bin = bin || b === 1; letters.push(st);
        }
        touched = letters.length > 0;
        status = !touched ? null : letters[0] === "A" || letters[0] === "U" ? letters[0]
          : letters[letters.length - 1] === "D" ? "D" : letters.includes("R") ? "R" : "M";
      }
      r.dataset.added = a; r.dataset.deleted = d; r.dataset.binary = String(bin);
      r.dataset.weighted = (a + d) * roleWeight(r);
      r.classList.toggle("filtered", !touched);
      const badge = statusOf(r);
      if (badge) {
        if (all) { badge.className = net.get(r).status; badge.textContent = net.get(r).label; }
        else if (status) { badge.className = "status " + STATUS[status]; badge.textContent = STATUS[status]; }
      }
    }
    // maxFiles keeps the heaviest files of the selection, each path once, then by path.
    const best = new Map();
    for (const r of fileRows.filter(isIn)) {
      const score = isRolelessFile(r) ? Number(r.dataset.added) + Number(r.dataset.deleted) : Number(r.dataset.weighted);
      best.set(pathOf(r), Math.max(best.get(pathOf(r)) ?? -1, score));
    }
    const ranked = [...best.entries()].sort((x, y) => y[1] - x[1] || (x[0] < y[0] ? -1 : 1)).map(e => e[0]);
    const kept = new Set(maxFiles > 0 ? ranked.slice(0, maxFiles) : ranked);
    for (const r of fileRows) r.classList.toggle("limited", isIn(r) && !kept.has(pathOf(r)));
    // Roles, the no-role group and the ignored group from their files, then groups from their children.
    const kids = r => children.get(r.dataset.id) || [];
    const aggregate = r => {
      let a = 0, d = 0, w = 0, n = 0;
      if (r.classList.contains("role") || isRoleless(r)) {
        let limited = 0;
        for (const f of kids(r)) {
          if (f.classList.contains("more") || !isIn(f)) continue;
          a += Number(f.dataset.added); d += Number(f.dataset.deleted); w += Number(f.dataset.weighted); n++;
          if (f.classList.contains("limited")) limited++;
        }
        const more = kids(r).find(f => f.classList.contains("more"));
        if (more) {
          more.classList.toggle("filtered", limited === 0);
          more.querySelector(".label").textContent = "…and " + (limited === 1 ? "1 more file" : limited + " more files");
        }
      } else {
        for (const c of kids(r)) { aggregate(c); if (!isIn(c)) continue; a += Number(c.dataset.added); d += Number(c.dataset.deleted); w += Number(c.dataset.weighted); n += Number(c.dataset.files); }
      }
      r.dataset.added = a; r.dataset.deleted = d; r.dataset.weighted = w; r.dataset.files = n;
      r.classList.toggle("filtered", n === 0);
      const sub = r.querySelector(".sub");
      if (sub) sub.textContent = r.classList.contains("unassigned")
        ? files(n) + " no layout { } allows. The architecture test will fail on " + (n === 1 ? "it." : "them.")
        : r.classList.contains("ignored")
        ? files(n) + " under ignore(). The architecture test does not look at " + (n === 1 ? "it." : "them.") : files(n);
    };
    for (const r of children.get("") || []) aggregate(r);
    // The stats count each path once.
    const seen = new Map();
    for (const r of fileRows.filter(isIn)) seen.set(pathOf(r), r);
    let added = 0, deleted = 0;
    for (const r of seen.values()) { added += Number(r.dataset.added); deleted += Number(r.dataset.deleted); }
    const stat = name => document.querySelector('[data-stat="' + name + '"]');
    stat("files").textContent = seen.size;
    stat("added").textContent = "+" + added; stat("deleted").textContent = "-" + deleted;
    const changedRoles = new Set(rows.filter(r => r.classList.contains("role") && isIn(r)).map(r => r.dataset.key));
    stat("touched").textContent = changedRoles.size;
    const noRole = new Set(fileRows.filter(r => isIn(r) && r.classList.contains("unassigned-file")).map(pathOf)).size;
    stat("no-role").textContent = noRole; stat("no-role-noun").textContent = noRole === 1 ? "file" : "files";
    stat("no-role").closest(".warn-stat").hidden = noRole === 0;
    const ignored = new Set(fileRows.filter(r => isIn(r) && r.classList.contains("ignored-file")).map(pathOf)).size;
    stat("ignored").textContent = ignored; stat("ignored-noun").textContent = ignored === 1 ? "file" : "files";
    stat("ignored").closest(".ignored-stat").hidden = ignored === 0;
    stat("limit").hidden = kept.size >= seen.size;
    stat("shown").textContent = kept.size; stat("limit-total").textContent = seen.size;
    const scope = stat("scope");
    if (scope) { scope.hidden = all; scope.textContent = " · " + sel.size + " of " + boxes.length + " commits"; }
    const selCount = stat("selected"); if (selCount) selCount.textContent = sel.size;
    onRecompute.forEach(f => f());
    const approx = document.querySelector(".commits .approx");
    if (approx) approx.hidden = all || sel.size < 2;
    // The Unchanged tree shows every role the selection leaves untouched.
    const grid = document.querySelector(".unchanged-grid");
    if (grid) {
      const roleKeys = [...grid.querySelectorAll("li[data-key]")].filter(li => !li.querySelector("b")).map(li => li.dataset.key)
        .concat([...grid.querySelectorAll("[data-column]")].filter(c => !c.querySelector("ul")).map(c => c.dataset.column));
      const untouched = roleKeys.filter(k => !changedRoles.has(k));
      const holds = key => untouched.some(k => k === key || k.startsWith(key + "."));
      for (const li of grid.querySelectorAll("li[data-key]")) li.classList.toggle("changed", !holds(li.dataset.key));
      for (const c of grid.querySelectorAll("[data-column]")) c.classList.toggle("changed", !holds(c.dataset.column));
      stat("unchanged").textContent = untouched.length === 1 ? "1 role" : untouched.length + " roles";
      grid.closest("details").hidden = untouched.length === 0;
    }
  }
  let lastBox = null;
  for (const box of boxes) {
    box.addEventListener("click", e => {
      if (e.shiftKey && lastBox) {
        const [from, to] = [boxes.indexOf(lastBox), boxes.indexOf(box)].sort((x, y) => x - y);
        for (let i = from; i <= to; i++) boxes[i].checked = box.checked;
      }
      lastBox = box;
    });
    box.addEventListener("change", () => { recompute(); render(); });
  }
  // Cross-highlight: a row lights up the commits that touched it, a commit the rows it touched. Only a
  // class is toggled, nothing is laid out again.
  const touchedBy = new Map();
  const commitsOf = r => {
    if (touchedBy.has(r)) return touchedBy.get(r);
    const set = new Set();
    if (r.classList.contains("file") && !r.classList.contains("more")) {
      for (const [i] of (data.files[pathOf(r)] || [])) set.add(i);
    } else {
      for (const c of children.get(r.dataset.id) || []) for (const i of commitsOf(c)) set.add(i);
    }
    touchedBy.set(r, set);
    return set;
  };
  const commitItem = i => boxes.find(b => commitIndex.get(b.dataset.commit) === i)?.closest("li");
  const clearLinked = () => document.querySelectorAll(".linked").forEach(e => e.classList.remove("linked"));
  const lightRow = e => {
    const r = e.target.closest("tr"); if (!r || r.classList.contains("more")) return;
    clearLinked();
    for (const i of commitsOf(r)) commitItem(i)?.classList.add("linked");
  };
  tbody.addEventListener("mouseover", lightRow);
  tbody.addEventListener("focusin", lightRow);
  tbody.addEventListener("mouseleave", clearLinked);
  tbody.addEventListener("focusout", clearLinked);
  const commitList = document.querySelector("details.commits ul");
  if (commitList) {
    const lightCommit = e => {
      const box = e.target.closest("li")?.querySelector("input[data-commit]"); if (!box) return;
      clearLinked();
      const i = commitIndex.get(box.dataset.commit);
      for (const r of rows) if (!r.classList.contains("more") && commitsOf(r).has(i)) r.classList.add("linked");
    };
    commitList.addEventListener("mouseover", lightCommit);
    commitList.addEventListener("focusin", lightCommit);
    commitList.addEventListener("mouseleave", clearLinked);
    commitList.addEventListener("focusout", clearLinked);
  }
  const setAll = checked => { boxes.forEach(b => b.checked = checked); recompute(); render(); };
  document.querySelector("[data-action=all-commits]")?.addEventListener("click", e => { e.preventDefault(); setAll(true); });
  document.querySelector("[data-action=no-commits]")?.addEventListener("click", e => { e.preventDefault(); setAll(false); });
  function render() {
    for (const r of rows) {
      if (r.classList.contains("more")) continue;
      const v = value(r);
      const bar = r.querySelector(".bar");
      const ratio = Math.min(1, v / fullImportance);
      bar.style.width = v > 0 ? "max(6px, " + ratio * 100 + "%)" : "0";
      bar.style.background = heatColor(ratio);
      const a = Number(r.dataset.added), d = Number(r.dataset.deleted);
      r.querySelector(".lines").innerHTML = r.dataset.binary === "true" ? "binary"
        : state.lines === "total" ? String(a + d)
        : '<span class="add">+' + a + '</span> <span class="del">-' + d + "</span>";
      const path = r.querySelector("code.path");
      if (path) path.textContent = state.path === "short" ? path.dataset.short : path.dataset.full;
    }
    const compare = {
      "definition": (x, y) => x.dataset.order - y.dataset.order,
      "definition-desc": (x, y) => y.dataset.order - x.dataset.order,
      "lines-desc": (x, y) => value(y) - value(x) || x.dataset.order - y.dataset.order,
      "lines-asc": (x, y) => value(x) - value(y) || x.dataset.order - y.dataset.order,
    }[state.sort];
    // "…and N more" stays under its siblings, then the no-role group and the ignored group under every group, whatever the sort.
    const isMore = r => r.classList.contains("more") ? 1 : 0;
    const isNoRole = r => r.classList.contains("unassigned") ? 1 : r.classList.contains("ignored") ? 2 : 0;
    const orderedCompare = (x, y) => isMore(x) - isMore(y) || isNoRole(x) - isNoRole(y) || compare(x, y);
    // Search and "warnings only": a row stays when it, a row above it or a row below it matches, and the
    // folding is set aside while a filter is on so that a match is never hidden in a closed group.
    const q = state.q.trim().toLowerCase();
    const filtering = q !== "" || state.warn;
    const textOf = r => ((r.dataset.key || "") + " " + (r.querySelector(".label")?.textContent || "")).toLowerCase();
    const isWarn = r => r.classList.contains("unassigned") || r.classList.contains("unassigned-file");
    const selfMatch = r => !r.classList.contains("more") && (q === "" || textOf(r).includes(q)) && (!state.warn || isWarn(r));
    const memo = new Map();
    const below = r => {
      if (!memo.has(r)) memo.set(r, selfMatch(r) || (children.get(r.dataset.id) || []).some(below));
      return memo.get(r);
    };
    const ordered = [];
    const walk = (parent, visible, matchedAbove) => {
      for (const r of [...(children.get(parent) || [])].sort(orderedCompare)) {
        const passes = !filtering || matchedAbove || below(r);
        const show = visible && passes && !r.classList.contains("filtered") && !r.classList.contains("limited");
        r.classList.toggle("hidden", !show);
        ordered.push(r);
        walk(r.dataset.id, show && (filtering || !r.classList.contains("collapsed")), matchedAbove || (filtering && selfMatch(r)));
      }
    };
    walk("", true, false);
    // The Unchanged tree follows the same search; "warnings only" empties it, as no unchanged role warns.
    const grid = document.querySelector(".unchanged-grid");
    if (grid) {
      const hit = key => q === "" || key.toLowerCase().includes(q);
      const keyOf = e => e.dataset.key || e.dataset.column;
      const items = [...grid.querySelectorAll("li[data-key]")].concat([...grid.querySelectorAll("[data-column]")]);
      for (const e of items) {
        const k = keyOf(e);
        const related = items.some(o => { const ok = keyOf(o); return hit(ok) && (ok === k || ok.startsWith(k + ".") || k.startsWith(ok + ".")); });
        e.classList.toggle("nomatch", state.warn || !related);
      }
    }
    tbody.append(...ordered);
    writeHash();
  }
  tbody.addEventListener("click", e => {
    const copy = e.target.closest(".copy");
    if (copy) {
      navigator.clipboard?.writeText(copy.dataset.command);
      const old = copy.textContent; copy.textContent = "copied"; setTimeout(() => copy.textContent = old, 1000);
      return;
    }
    if (e.target.closest("a")) return;
    const row = e.target.closest("tr");
    if (row && (children.get(row.dataset.id) || []).length) { row.classList.toggle("collapsed"); render(); }
  });
  for (const seg of document.querySelectorAll(".seg")) {
    seg.addEventListener("click", e => {
      const b = e.target.closest("button"); if (!b) return;
      for (const x of seg.children) x.classList.toggle("on", x === b);
      state[seg.dataset.control] = b.dataset.value; render();
    });
  }
  document.querySelector("[data-control=sort]").addEventListener("change", e => { state.sort = e.target.value; render(); });
  document.querySelector("[data-action=expand]").addEventListener("click", () => { rows.forEach(r => r.classList.remove("collapsed")); render(); });
  document.querySelector("[data-action=collapse]").addEventListener("click", () => {
    rows.forEach(r => { if ((children.get(r.dataset.id) || []).length) r.classList.add("collapsed"); }); render();
  });
  // The view settings are rarely touched, so they sit folded behind one button.
  const menuButton = document.querySelector(".menu-button"), panel = document.getElementById("view-panel");
  const setMenu = open => { panel.hidden = !open; menuButton.setAttribute("aria-expanded", String(open)); };
  menuButton.addEventListener("click", () => setMenu(panel.hidden));
  document.addEventListener("keydown", e => { if (e.key === "Escape" && !panel.hidden) { setMenu(false); menuButton.focus(); } });
  document.addEventListener("click", e => { if (!panel.hidden && !e.target.closest(".menu")) setMenu(false); });

  // The view lives in the URL hash, so a link opens the report as it was shown. Only what differs from
  // the default is written, and replaceState keeps the Back button for real navigation.
  const DEFAULTS = { lines: "total", score: "weighted", path: "short", sort: "definition" };
  const PARAM = { lines: "lines", score: "heat", path: "path", sort: "sort" };
  const sortSelect = document.querySelector("[data-control=sort]");
  const ALLOWED = {
    lines: ["total", "split"], score: ["weighted", "raw"], path: ["short", "full"],
    sort: [...sortSelect.options].map(o => o.value),
  };
  const unchanged = document.querySelector("details.unchanged");
  const keyed = rows.filter(r => r.dataset.key && (children.get(r.dataset.id) || []).length);
  const collapsedByDefault = r => r.dataset.changed === "false" || r.dataset.collapsedDefault === "true";
  let at = null;
  function viewParams() {
    const p = [];
    for (const k of Object.keys(DEFAULTS)) if (state[k] !== DEFAULTS[k]) p.push(PARAM[k] + "=" + encodeURIComponent(state[k]));
    const open = keyed.filter(r => collapsedByDefault(r) && !r.classList.contains("collapsed"));
    const closed = keyed.filter(r => !collapsedByDefault(r) && r.classList.contains("collapsed"));
    if (open.length) p.push("open=" + open.map(r => encodeURIComponent(r.dataset.key)).join(","));
    if (closed.length) p.push("closed=" + closed.map(r => encodeURIComponent(r.dataset.key)).join(","));
    if (unchanged && unchanged.open) p.push("unchanged=1");
    if (state.q.trim()) p.push("q=" + encodeURIComponent(state.q.trim()));
    if (state.warn) p.push("warn=1");
    if (!allSelected()) p.push("commits=" + boxes.filter(b => b.checked).map(b => encodeURIComponent(b.dataset.commit)).join(","));
    return p;
  }
  function writeHash() {
    const p = viewParams();
    if (at) p.push("at=" + encodeURIComponent(at));
    const hash = p.join("&");
    const url = location.pathname + location.search + (hash ? "#" + hash : "");
    // Compared with the whole href, so a bare trailing "#" left by an emptied hash is removed too.
    if (location.origin + url !== location.href && url !== location.href) history.replaceState(null, "", url);
  }
  function readHash() {
    const params = new Map();
    const raw = location.hash.replace(/^#/, "");
    const decode = v => { try { return decodeURIComponent(v); } catch (e) { return null; } };
    // A bare #<key>, typed by hand, means the same as at=<key>.
    if (raw && !raw.includes("=")) params.set("at", raw);
    else for (const pair of raw.split("&")) {
      const i = pair.indexOf("="); if (i <= 0) continue;
      params.set(pair.slice(0, i), pair.slice(i + 1));
    }
    at = params.has("at") ? decode(params.get("at")) : null;
    for (const k of Object.keys(DEFAULTS)) {
      const v = params.has(PARAM[k]) ? decode(params.get(PARAM[k])) : DEFAULTS[k];
      state[k] = ALLOWED[k].includes(v) ? v : DEFAULTS[k];
    }
    for (const seg of document.querySelectorAll(".seg")) {
      for (const b of seg.children) b.classList.toggle("on", b.dataset.value === state[seg.dataset.control]);
    }
    sortSelect.value = state.sort;
    state.q = params.has("q") ? (decode(params.get("q")) || "") : "";
    state.warn = params.get("warn") === "1";
    searchBox.value = state.q; warnBox.checked = state.warn;
    const keys = name => new Set((params.get(name) || "").split(",").filter(Boolean).map(decode));
    const open = keys("open"), closed = keys("closed");
    for (const r of keyed) {
      const collapsed = open.has(r.dataset.key) ? false : closed.has(r.dataset.key) ? true : collapsedByDefault(r);
      r.classList.toggle("collapsed", collapsed);
    }
    if (unchanged) unchanged.open = params.get("unchanged") === "1";
    if (params.has("commits")) {
      const wanted = new Set(params.get("commits").split(",").map(decode).filter(c => commitIndex.has(c)));
      // A list naming no commit of this report is ignored rather than emptying the page.
      boxes.forEach(b => b.checked = wanted.size === 0 || wanted.has(b.dataset.commit));
    } else {
      boxes.forEach(b => b.checked = true);
    }
  }
  const searchBox = document.querySelector("[data-control=search]");
  const warnBox = document.querySelector("[data-control=warnings]");
  searchBox.addEventListener("input", () => { state.q = searchBox.value; render(); });
  warnBox.addEventListener("change", () => { state.warn = warnBox.checked; render(); });
  // {{EXTRAS}}
  const targetOf = key => [...document.querySelectorAll("[data-key]")].find(e => e.dataset.key === key);
  function goTo(key) {
    const target = key && targetOf(key);
    if (!target) return;
    if (unchanged && unchanged.contains(target) && target !== unchanged) unchanged.open = true;
    if (target.tagName === "TR") {
      for (let p = byId.get(target.dataset.parent); p; p = byId.get(p.dataset.parent)) p.classList.remove("collapsed");
      render();
    }
    const shown = target.tagName === "DETAILS" ? target.querySelector("summary") : target;
    shown.scrollIntoView({ block: "center" });
    shown.classList.remove("flash"); void shown.offsetWidth; shown.classList.add("flash");
    setTimeout(() => shown.classList.remove("flash"), 1600);
  }
  document.addEventListener("click", e => {
    const button = e.target.closest(".anchor"); if (!button) return;
    e.preventDefault(); e.stopPropagation();
    const p = viewParams(); p.push("at=" + encodeURIComponent(button.dataset.anchor));
    navigator.clipboard?.writeText(location.href.replace(/#.*$/, "") + "#" + p.join("&"));
    button.classList.add("copied"); setTimeout(() => button.classList.remove("copied"), 1000);
  }, true);
  window.addEventListener("hashchange", () => { readHash(); recompute(); render(); goTo(at); });
  if (unchanged) unchanged.addEventListener("toggle", writeHash);
  const copyLink = document.querySelector("[data-action=copy-link]");
  copyLink.addEventListener("click", () => {
    navigator.clipboard?.writeText(location.href);
    const old = copyLink.textContent; copyLink.textContent = "copied"; setTimeout(() => copyLink.textContent = old, 1000);
  });
  readHash();
  // All commits selected means the panel has nothing to say yet; a link with commits= opens it.
  const commitsPanel = document.querySelector("details.commits");
  if (commitsPanel) commitsPanel.open = !allSelected();
  recompute();
  render();
  goTo(at);
})();
"""
