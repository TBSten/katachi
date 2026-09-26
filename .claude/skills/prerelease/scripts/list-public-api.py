#!/usr/bin/env python3
"""リリース前チェックの 3（可視性）の材料: `:katachi` と `:katachi-konsist` の公開宣言を、3つの区分に分けて全部並べる。

区分:
  public        注釈なし
  internal-api  `@InternalKatachiApi`（自分か、囲む型に付いている）
  experimental  `@ExperimentalKatachiApi`（同上）

1件ごとに出すもの:
  - 基準（直前のタグ）からの印: [追加] / [変更] / [移動] / [区分変更]（同じなら無印）
  - 宣言の種類と名前、`file://` の絶対パス:行、`.internal` パッケージかどうか
  - 名前が出てくる回数（単語として。宣言そのものの1回は引く）。場所ごとに
      自 = 同じモジュールの main（KDoc の例を含む） / 他 = もう片方のライブラリ・Gradle プラグイン（Java の文字列を含む）・tool/
      test = 各モジュールのテストと architecture-test / sample = sample/ / docs = 日本語のドキュメントと README.ja.md
  - シグネチャ

**すべて目安。**宣言は字句走査で拾う（check-docs-against-impl/scripts/public_api.py の説明を参照）。
回数は名前だけで数えるので、`name` や `path` のような一般的な名前のメンバは別物も数える。
妥当かどうかの判断はしない。それは読む側（subagent）の仕事。

使い方（リポジトリの直下で）:
  python3 .claude/skills/prerelease/scripts/list-public-api.py                       # 全部を標準出力へ
  python3 .claude/skills/prerelease/scripts/list-public-api.py --only internal-api   # 1区分だけ（subagent に渡す）
  python3 .claude/skills/prerelease/scripts/list-public-api.py --changed             # 基準から増えた・変わったものだけ
  python3 .claude/skills/prerelease/scripts/list-public-api.py --release-dir .local/release-v0.2.0
      # <dir>/visibility-check.md に書き、<dir>/prerelease-check-list.md の件数の欄を埋める
  --base <ref> で基準を変える（既定は `vX.Y.Z` のタグのうち最新）
"""
import argparse, collections, re, subprocess, sys
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "check-docs-against-impl" / "scripts"))
import public_api as api  # noqa: E402

TEXT_SUFFIXES = {".kt", ".kts", ".java", ".md", ".mdx", ".toml", ".json", ".html", ".sh", ".yml", ".yaml", ".txt"}
AREAS = ("自", "他", "test", "sample", "docs")
IDENT = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")


def area_of(path, own_module):
    own_src = api.MODULES[own_module]
    if path.startswith(own_src + "/"):
        return "自"
    if any(path.startswith(src + "/") for src in api.MODULES.values()) \
            or path.startswith("katachi-gradle-plugin/src/main/") or path.startswith("tool/"):
        return "他"
    if "/src/test" in path or path.startswith("architecture-test/"):
        return "test"
    if path.startswith("sample/"):
        return "sample"
    if path.startswith("docs/src/content/docs/ja/") or path == "README.ja.md":
        return "docs"
    return None


def build_index(root):
    """ファイルごとの識別子の出現回数。git が追っているファイルと、追っていない新しいファイル（無視されるものを除く）。"""
    files = subprocess.run(["git", "ls-files", "-co", "--exclude-standard"], cwd=root,
                           capture_output=True, text=True).stdout.splitlines()
    index = {}
    for p in files:
        if Path(p).suffix not in TEXT_SUFFIXES:
            continue
        try:
            text = (root / p).read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            continue
        index[p] = collections.Counter(IDENT.findall(text))
    return index


def usage(decl, index, name_totals_cache):
    name = decl.name if decl.kind != "constructor" else decl.owners[-1]
    key = (decl.module, name)
    if key not in name_totals_cache:
        counts = dict.fromkeys(AREAS, 0)
        for path, tokens in index.items():
            n = tokens.get(name)
            if n:
                area = area_of(path, decl.module)
                if area:
                    counts[area] += n
        name_totals_cache[key] = counts
    counts = dict(name_totals_cache[key])
    counts["自"] = max(0, counts["自"] - 1)  # 宣言そのもの
    return counts


def render(decls, status, removed, index, root, base, only, changed_only):
    cache = {}
    groups = {c: [d for d in decls if d.category == c] for c in api.CATEGORIES}
    out = ["# 公開 API の一覧（目安）", ""]
    out.append(f"- 基準: {base or '（タグ無し。印は付けない）'}")
    out.append(f"- 走査: {', '.join(f'{m}（{s}）' for m, s in api.MODULES.items())}")
    out.append("- 拾い方と回数の数え方は目安。誤検知・取りこぼしがありうる（public_api.py の冒頭の説明を参照）")
    out.append("")
    out.append("| 区分 | 件数 | うち `.internal` パッケージ | 基準から追加 | 変更・移動・区分変更 |")
    out.append("|---|---|---|---|---|")
    for c, ds in groups.items():
        added = sum(1 for d in ds if status.get(id(d)) == "追加")
        changed = sum(1 for d in ds if status.get(id(d)) not in (None, "追加"))
        out.append(f"| {api.CATEGORY_LABELS[c]} | {len(ds)} | {sum(d.in_internal_package for d in ds)} | {added} | {changed} |")
    out.append(f"| 基準にあって今は無い（削除・改名の候補） | {len(removed)} | | | |")
    out.append("")
    for c, ds in groups.items():
        if only and c != only:
            continue
        shown = [d for d in ds if not changed_only or status.get(id(d))]
        out.append(f"## {api.CATEGORY_LABELS[c]}（{len(shown)} 件）")
        out.append("")
        for sub, label in ((False, "`.internal` 以外"), (True, "`.internal` パッケージ")):
            part = [d for d in shown if d.in_internal_package == sub]
            if not part:
                continue
            out.append(f"### {label}（{len(part)} 件）")
            out.append("")
            for d in part:
                mark = f"[{status[id(d)]}] " if status.get(id(d)) else ""
                u = usage(d, index, cache)
                used = " / ".join(f"{k} {u[k]}" for k in AREAS)
                out.append(f"- {mark}{d.kind} `{d.qualified}`（{d.module} `{d.package}`）")
                out.append(f"  - {api.file_uri(root, d.path, d.line)}")
                out.append(f"  - 使用: {used}")
                out.append(f"  - `{d.signature[:200]}`")
            out.append("")
    if removed and not only:
        out.append(f"## 基準にあって今は無い（{len(removed)} 件。削除・改名・非公開化の候補）")
        out.append("")
        for d in removed:
            out.append(f"- {d.kind} `{d.qualified}`（{d.module} `{d.package}`、{base}:{d.path}:{d.line}）")
        out.append("")
    return "\n".join(out), {c: len(ds) for c, ds in groups.items()}


def fill_checklist(checklist, counts):
    labels = {"public": "public（注釈なし）", "internal-api": "public + `@InternalKatachiApi`",
              "experimental": "public + `@ExperimentalKatachiApi`"}
    text = checklist.read_text(encoding="utf-8")
    for c, label in labels.items():
        text = text.replace(f"{label}: {{TODO 件数}} 件", f"{label}: {counts[c]} 件", 1)
    checklist.write_text(text, encoding="utf-8")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", help="比べる基準（既定は最新の vX.Y.Z タグ）")
    ap.add_argument("--only", choices=api.CATEGORIES, help="1つの区分だけ出す")
    ap.add_argument("--changed", action="store_true", help="基準から追加・変更されたものだけ出す")
    ap.add_argument("--release-dir", help="<dir>/visibility-check.md に書き、チェックリストの件数を埋める")
    a = ap.parse_args()

    root = api.repo_root()
    base = a.base or api.latest_release_tag(root)
    decls = api.public_only(api.scan_worktree(root))
    base_decls = api.scan_ref(root, base) if base else None
    if base and base_decls is None:
        sys.exit(f"基準 {base} が git に見つかりません")
    status, removed = api.compare(decls, api.public_only(base_decls) if base_decls else None)
    text, counts = render(decls, status, removed, build_index(root), root, base, a.only, a.changed)

    if a.release_dir:
        release_dir = (root / a.release_dir).resolve()
        release_dir.mkdir(parents=True, exist_ok=True)
        out = release_dir / "visibility-check.md"
        out.write_text(text + "\n", encoding="utf-8")
        checklist = release_dir / "prerelease-check-list.md"
        if checklist.exists():
            fill_checklist(checklist, counts)
        print("\n\n".join(text.split("\n\n")[:3]))  # 冒頭の表まで
        print(f"\n書いた: {out.as_uri()}")
        if checklist.exists():
            print(f"件数を埋めた: {checklist.as_uri()}")
    else:
        print(text)


if __name__ == "__main__":
    main()
