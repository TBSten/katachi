#!/usr/bin/env python3
"""リリース前チェックの 4（リリースノート）の材料: 直前のタグからのコミットを、Conventional Commits の type ごとに並べる。

出すもの:
  - BREAKING CHANGE（件名の `!` か、本文の `BREAKING CHANGE:` / `BREAKING-CHANGE:`）。本文の説明も添える
  - type ごとのコミット（利用者に効きやすい順: feat, fix, perf, refactor, revert, build, docs, test, ci, chore, style, そのほか）
  - scope ごとの件数
  - 作業ツリーの未コミットの変更（まだリリースに入っていないもの）の件数

**材料であって下書きではない。**どれが利用者に効くか、どうまとめるかは読む側が決める。
件名が Conventional Commits の形でないコミットは「形が違う」に入る。

使い方（リポジトリの直下で）:
  python3 .claude/skills/prerelease/scripts/release-note-material.py              # 基準は最新の vX.Y.Z タグ
  python3 .claude/skills/prerelease/scripts/release-note-material.py --base v0.1.0
  python3 .claude/skills/prerelease/scripts/release-note-material.py --files      # コミットごとに触ったファイルも出す
"""
import argparse, collections, re, subprocess, sys
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "check-docs-against-impl" / "scripts"))
import public_api as api  # noqa: E402

ORDER = ["feat", "fix", "perf", "refactor", "revert", "build", "docs", "test", "ci", "chore", "style"]
SUBJECT = re.compile(r"^(?P<type>[a-z]+)(?:\((?P<scope>[^)]*)\))?(?P<bang>!)?: (?P<desc>.+)$")
TRAILER = re.compile(r"^(Co-Authored-By|Claude-Session|Signed-off-by):", re.I)
BREAKING_BODY = re.compile(r"^BREAKING[ -]CHANGE:\s*(.*)", re.M | re.S)


def git(root, *args):
    return subprocess.run(["git", *args], cwd=root, capture_output=True, text=True).stdout


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", help="基準（既定は最新の vX.Y.Z タグ）")
    ap.add_argument("--files", action="store_true", help="コミットごとに触ったファイルを出す")
    a = ap.parse_args()

    root = api.repo_root()
    base = a.base or api.latest_release_tag(root)
    if not base:
        sys.exit("vX.Y.Z のタグが無いので基準が決まりません。--base で渡してください")
    raw = git(root, "log", "--reverse", "--format=%h%x1f%ad%x1f%s%x1f%b%x1e", "--date=short", f"{base}..HEAD")
    commits = []
    for rec in raw.split("\x1e"):
        rec = rec.strip("\n")
        if not rec:
            continue
        h, date, subject, body = (rec.split("\x1f") + [""] * 4)[:4]
        m = SUBJECT.match(subject)
        breaking = BREAKING_BODY.search(body)
        commits.append({
            "hash": h, "date": date, "subject": subject, "body": body.strip(),
            "type": m.group("type") if m else None,
            "scope": (m.group("scope") or "") if m else "",
            "desc": m.group("desc") if m else subject,
            "breaking": bool(m and m.group("bang")) or bool(breaking),
            "breaking_note": breaking.group(1).strip() if breaking else "",
        })

    head = git(root, "rev-parse", "--short", "HEAD").strip()
    print(f"# リリースノートの材料（{base}..{head}、{len(commits)} コミット）")
    print()

    def line(c):
        scope = f"({c['scope']}) " if c["scope"] else ""
        return f"- {c['hash']} {c['date']} {scope}{c['desc']}"

    def files(c):
        if a.files:
            for f in git(root, "show", "--name-only", "--format=", c["hash"]).split():
                print(f"    - {f}")

    breaking = [c for c in commits if c["breaking"]]
    print(f"## BREAKING CHANGE（{len(breaking)} 件）")
    print()
    for c in breaking:
        print(line(c) + f"  [{c['type']}]")
        note = c["breaking_note"] or c["body"]
        for body_line in [l for l in note.splitlines() if l.strip() and not TRAILER.match(l.strip())][:8]:
            if body_line.strip():
                print(f"    > {body_line.strip()}")
        files(c)
    print()

    by_type = collections.defaultdict(list)
    for c in commits:
        by_type[c["type"] or "（形が違う）"].append(c)
    types = [t for t in ORDER if t in by_type] + sorted(t for t in by_type if t not in ORDER)
    for t in types:
        print(f"## {t}（{len(by_type[t])} 件）")
        print()
        for c in by_type[t]:
            print(line(c) + ("  [BREAKING]" if c["breaking"] else ""))
            files(c)
        print()

    scopes = collections.Counter(c["scope"] or "（無し）" for c in commits)
    print("## scope ごとの件数")
    print()
    print(", ".join(f"{s} {n}" for s, n in scopes.most_common()))
    print()

    dirty = [l for l in git(root, "status", "--porcelain").splitlines() if l.strip()]
    print("## 未コミットの変更")
    print()
    if dirty:
        print(f"{len(dirty)} ファイル。コミットされるまでリリースには入らない（`git status` で確かめる）")
    else:
        print("無し")


if __name__ == "__main__":
    main()
