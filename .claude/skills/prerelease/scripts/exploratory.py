#!/usr/bin/env python3
"""探索的検証（prerelease の手順 13・週替わりの nightly 探索）のチャーターを扱う。

  python3 .claude/skills/prerelease/scripts/exploratory.py list            # チャーターの一覧と書式の検査
  python3 .claude/skills/prerelease/scripts/exploratory.py lint            # 書式の検査だけ（問題があれば終了コード 1）
  python3 .claude/skills/prerelease/scripts/exploratory.py week            # 今週の nightly 探索で回すチャーター
  python3 .claude/skills/prerelease/scripts/exploratory.py week --date 2026-11-21

チャーターは references/exploratory/<名前>.md。書式は references/exploratory.md の「チャーター」。
nightly 探索で回すのは「場所: クラウド」のチャーターだけで、ISO 週の番号で 1 週 1 つを順番に回す（名前の並びは `ROTATION`）。
標準ライブラリだけ。
"""
import argparse, datetime, re, sys
from pathlib import Path

sys.dont_write_bytecode = True

CHARTER_DIR = Path(__file__).resolve().parent.parent / "references" / "exploratory"
# nightly 探索の順番。クラウドで回せるチャーターを、利用者に近い入口から奥へ。
ROTATION = ["core", "konsist-konture", "gradle-plugin", "cli-script", "diff", "docs"]
REQUIRED_META = ("利用者像", "場所", "目安")
REQUIRED_SECTIONS = ("観点", "入口", "やってみること", "過去に見つかったもの")


def parse_charter(path):
    """(見出し, メタの辞書, `## ` の見出しの一覧)。"""
    text = path.read_text(encoding="utf-8")
    title, meta, sections = None, {}, []
    for line in text.splitlines():
        if title is None:
            m = re.match(r"^# チャーター: (.+)$", line)
            if m:
                title = m.group(1).strip()
            continue
        m = re.match(r"^- (%s): (.+)$" % "|".join(REQUIRED_META), line)
        if m and not sections:
            meta[m.group(1)] = m.group(2).strip()
            continue
        m = re.match(r"^## (.+)$", line)
        if m:
            sections.append(m.group(1).strip())
    return title, meta, sections


def lint_charter(path):
    title, meta, sections = parse_charter(path)
    problems = []
    if not title:
        problems.append("「# チャーター: <名前>」の見出しがありません")
    for k in REQUIRED_META:
        if k not in meta:
            problems.append(f"「- {k}:」がありません")
    for s in REQUIRED_SECTIONS:
        if not any(x.startswith(s) for x in sections):  # 「やってみること（観点カードの種）」のような添え書きは許す
            problems.append(f"「## {s}」がありません")
    return problems


def charters():
    return sorted(p for p in CHARTER_DIR.glob("*.md") if p.name != "README.md")


def is_cloud(path):
    _, meta, _ = parse_charter(path)
    return meta.get("場所", "").startswith("クラウド")


def charter_for_week(date):
    """その週に nightly 探索で回すチャーターの名前（ISO 週の番号で ROTATION を巡る）。"""
    week = date.isocalendar()[1]
    return ROTATION[week % len(ROTATION)]


def lint_all():
    problems = []
    names = {p.stem for p in charters()}
    for p in charters():
        problems += [f"{p.name}: {x}" for x in lint_charter(p)]
    for n in ROTATION:
        if n not in names:
            problems.append(f"ROTATION の「{n}」のチャーターがありません（{CHARTER_DIR / (n + '.md')}）")
        elif not is_cloud(CHARTER_DIR / f"{n}.md"):
            problems.append(f"ROTATION の「{n}」は「場所: クラウド」ではありません（nightly 探索はクラウドで回す）")
    for n in sorted(names - set(ROTATION)):
        if is_cloud(CHARTER_DIR / f"{n}.md"):
            problems.append(f"{n}.md は「場所: クラウド」なのに ROTATION にありません（nightly 探索で回らない）")
    return problems


def cmd_list(_args):
    for p in charters():
        title, meta, _ = parse_charter(p)
        print(f"{p.stem}\t{title}\t{meta.get('場所', '?')}\t{meta.get('目安', '?')}\t{'nightly' if p.stem in ROTATION else '-'}")
    problems = lint_all()
    for x in problems:
        print(f"PROBLEM {x}")
    print(f"チャーター {len(charters())} 件 / 問題 {len(problems)} 件")
    return 1 if problems else 0


def cmd_lint(_args):
    problems = lint_all()
    for x in problems:
        print(f"PROBLEM {x}")
    print(f"問題 {len(problems)} 件")
    return 1 if problems else 0


def cmd_week(args):
    date = datetime.date.fromisoformat(args.date) if args.date else datetime.date.today()
    name = charter_for_week(date)
    path = CHARTER_DIR / f"{name}.md"
    print(f"{name}\t{path}")
    return 0 if path.is_file() else 1


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("list").set_defaults(fn=cmd_list)
    sub.add_parser("lint").set_defaults(fn=cmd_lint)
    p = sub.add_parser("week")
    p.add_argument("--date", help="YYYY-MM-DD（既定: 今日）")
    p.set_defaults(fn=cmd_week)
    args = ap.parse_args(argv)
    return args.fn(args)


if __name__ == "__main__":
    sys.exit(main())
