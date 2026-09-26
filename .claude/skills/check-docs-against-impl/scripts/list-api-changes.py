#!/usr/bin/env python3
"""check-docs-against-impl の 1（範囲を決める）の材料: 基準からの実装の変更と、それに触れているドキュメントの場所を並べる。

出すもの:
  1. 実装のソースの変更（`git diff --stat <基準>`。katachi / katachi-konsist / Gradle プラグイン / tool/dokka）
  2. 公開 API の差分（:katachi・:katachi-konsist の公開宣言と、Gradle プラグインの public な型・メソッド）
       削除・改名の候補 … 基準にあって今は無い。**ドキュメントに残っていれば、ほぼ確実に古い**
       変更             … シグネチャ・opt-in の区分・パッケージが変わった
       追加             … 今だけにある。ドキュメントに1度も出てこないものに印を付ける
  3. それぞれの名前が、確かめる対象のドキュメント（日本語の原本・README.ja.md・sample の README・
     インストールキットの手順書）のどこに出てくるか（`file://` の絶対パス:行）

**すべて目安。**宣言は字句走査で拾い（public_api.py の冒頭を参照）、ドキュメントは名前を単語として grep するだけ。
`name` のような一般的な名前は別物も拾う（メンバは `.name` / `name(` / `name =` の形だけを数えて減らしている）。今も同じ名前の公開宣言が別にあるときは「同名が今もある」と書く。
食い違っているかどうかの判断はしない。それは読む側（subagent）の仕事。

使い方（リポジトリの直下で）:
  python3 .claude/skills/check-docs-against-impl/scripts/list-api-changes.py            # 基準は最新の vX.Y.Z タグ
  python3 .claude/skills/check-docs-against-impl/scripts/list-api-changes.py v0.1.0     # 基準を渡す（コミットでもタグでも）
  python3 .claude/skills/check-docs-against-impl/scripts/list-api-changes.py --max-hits 5
"""
import argparse, re, subprocess, sys
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import public_api as api  # noqa: E402

IMPL_DIRS = ("katachi/src/main", "katachi-konsist/src/main", "katachi-gradle-plugin/src/main", "tool/dokka/src/main")


class DocIndex:
    def __init__(self, root):
        self.root = root
        self.lines = {p: api.read_lines(root, p) for p in api.doc_files(root)}
        self._cache = {}

    def hits(self, name, member=False):
        """name が単語として出てくる行。member なら `.name` / `name(` / `name =` の形だけ（地の文の同じ語を拾わないため）。"""
        key = (name, member)
        if key not in self._cache:
            n = re.escape(name)
            word = re.compile(rf"(?:\.{n}(?!\w)|(?<![\w.]){n}\s*(?:\(|=(?!=)))" if member
                              else rf"(?<!\w){n}(?!\w)")
            self._cache[key] = [(p, no, l.strip()) for p, ls in self.lines.items()
                                for no, l in enumerate(ls, 1) if word.search(l)]
        return self._cache[key]


def dsl_name(java_name):
    """Gradle の getter / setter は、build.gradle.kts ではプロパティの名前で書く。"""
    m = re.match(r"^(?:get|set|is)([A-Z]\w*)$", java_name)
    return m.group(1)[0].lower() + m.group(1)[1:] if m else java_name


def print_hits(root, docs, name, max_hits, indent="  ", member=False):
    hits = docs.hits(name, member)
    print(f"{indent}- ドキュメント: {len(hits)} か所" + ("" if hits else "（出てこない）"))
    for p, no, text in hits[:max_hits]:
        print(f"{indent}  - {api.file_uri(root, p, no)}  {text[:140]}")
    if len(hits) > max_hits:
        print(f"{indent}  - …ほか {len(hits) - max_hits} か所")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("base", nargs="?", help="基準（既定は最新の vX.Y.Z タグ）")
    ap.add_argument("--max-hits", type=int, default=8, help="1つの名前につき出すドキュメントの場所の数")
    a = ap.parse_args()

    root = api.repo_root()
    base = a.base or api.latest_release_tag(root)
    if not base:
        sys.exit("vX.Y.Z のタグが無いので基準が決まりません。引数で渡してください")
    base_decls = api.scan_ref(root, base)
    if base_decls is None:
        sys.exit(f"基準 {base} が git に見つかりません")

    current = api.public_only(api.scan_worktree(root))
    status, removed = api.compare(current, api.public_only(base_decls))
    current_names = {d.name for d in current}
    docs = DocIndex(root)

    print(f"# {base} からの実装の変更と、触れているドキュメント（目安）")
    print()
    print(f"確かめる対象のドキュメント: {len(docs.lines)} ファイル（{', '.join(api.DOC_GLOBS)}）")
    print()

    print("## 1. 実装のソースの変更")
    print()
    stat = subprocess.run(["git", "diff", "--stat=200", base, "--", *IMPL_DIRS], cwd=root,
                          capture_output=True, text=True).stdout.rstrip()
    print("```")
    print(stat or "（変更なし）")
    print("```")
    print()

    # 型ごと消えたものは、メンバを型の下にまとめる
    removed_keys = {(d.module, d.qualified) for d in removed}
    top_removed = [d for d in removed if not d.owners or (d.module, ".".join(d.owners)) not in removed_keys]
    print(f"## 2. 削除・改名の候補（{len(top_removed)} 件。型ごと消えたもののメンバは型の下にまとめた）")
    print()
    for d in top_removed:
        if d.kind == "constructor":
            continue
        members = [m for m in removed if m.owners[:len(d.owners) + 1] == d.owners + [d.name] and m.kind != "constructor"]
        still = "（同名が今もある）" if d.name in current_names else ""
        print(f"- {d.kind} `{d.qualified}` {still}— {d.module} `{d.package}`（{base}:{d.path}:{d.line}）")
        if members:
            print(f"  - メンバ: {', '.join(m.name for m in members)}")
        if d.name != "Companion":
            print_hits(root, docs, d.name, a.max_hits, member=bool(d.owners))
        for m in members:
            if m.name not in current_names and docs.hits(m.name, member=True):
                print(f"  - メンバ `{m.name}`（今の公開 API に同名なし）:")
                print_hits(root, docs, m.name, a.max_hits, indent="    ", member=True)
    print()

    changed = [d for d in current if status.get(id(d)) not in (None, "追加") and d.kind != "constructor"]
    print(f"## 3. 変更（{len(changed)} 件。シグネチャ・区分・パッケージのどれかが基準と違う）")
    print()
    for d in changed:
        print(f"- [{status[id(d)]}] {d.kind} `{d.qualified}` — {api.file_uri(root, d.path, d.line)}")
        print(f"  - 今: `{d.signature[:180]}`")
        print_hits(root, docs, d.name, a.max_hits, member=bool(d.owners))
    print()

    added = [d for d in current if status.get(id(d)) == "追加" and d.kind != "constructor"]
    top_added = [d for d in added if not d.owners or all(o.qualified != ".".join(d.owners) for o in added)]
    print(f"## 4. 追加（{len(added)} 件。うち型ごと増えたものは型だけを出す: {len(top_added)} 件）")
    print()
    for d in top_added:
        n = len(docs.hits(d.name, member=bool(d.owners)))
        mark = "  ← ドキュメントに出てこない" if n == 0 else f"  （ドキュメント {n} か所）"
        print(f"- {d.kind} `{d.qualified}` [{d.category}] — {api.file_uri(root, d.path, d.line)}{mark}")
    print()

    base_java = api.java_api_ref(root, base)
    now_java = api.java_api_worktree(root)
    print("## 5. Gradle プラグイン（Java）の public な型・メソッドの差分")
    print()
    if not base_java:
        print(f"基準 {base} にはまだ Gradle プラグインが無い。今の {len(now_java)} 件はすべて追加")
        print()
    gone = sorted(set(base_java) - set(now_java))
    for owner, name in gone:
        print(f"- 削除 `{owner}.{name}`（DSL では `{dsl_name(name)}`）")
        print_hits(root, docs, dsl_name(name), a.max_hits)
    new = sorted(set(now_java) - set(base_java))
    new_dsl = sorted({dsl_name(n) for _, n in new if dsl_name(n) != n or n[0].islower()})
    if new_dsl:
        silent = [n for n in new_dsl if not docs.hits(n)]
        print(f"- 追加された DSL の名前: {', '.join(new_dsl)}")
        if silent:
            print(f"  - うちドキュメントに出てこない: {', '.join(silent)}")


if __name__ == "__main__":
    main()
