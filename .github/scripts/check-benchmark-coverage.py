#!/usr/bin/env python3
"""Checks the coverage table in benchmark/README.md against what is really there.

A feature that gets no benchmark leaves no trace anywhere: nothing fails, nothing is listed.
The table says, per component, which benchmarks watch it, or why none does. This script keeps
the table honest, so that "not measured" is always a line someone wrote, never an omission:

  1. every component of .github/labels.json (`影響:<component>`) has a row, and no row names a
     component that is not a label
  2. every benchmark that exists is named in a row, and every benchmark a row names exists:
       `XxxBench`                   a JMH class in benchmark/src/jmh
       `realProject:<name>`         <Name>Architecture.kt in benchmark/src/realProject
       `gradle-profiler:<scenario>` a scenario in benchmark/gradle-profiler/*.scenarios
  3. a row without a benchmark (`なし`) gives a reason
  4. the "reviewed at" column is a release (`vX.Y.Z`) or `未確認`
  5. the paths of a row exist

Whether a row is still right for the code as it is now is not something a script can tell; the
release checklist asks that (prerelease step 12, .claude/skills/prerelease/scripts/check-benchmark.py,
which loads this file for the table).

Usage, from the repository root:  python3 .github/scripts/check-benchmark-coverage.py
"""
import json
import re
import sys
from pathlib import Path

TABLE = "benchmark/README.md"
COLUMNS = ["コンポーネント", "入口", "場所", "ベンチ", "測らない理由", "確認した版"]
NONE = "なし"
UNREVIEWED = "未確認"
LABEL_PREFIX = "影響:"


def split_cells(line):
    """Cells of one Markdown table line. A `|` inside backticks does not split."""
    cells, cell, in_code = [], "", False
    for ch in line.strip().strip("|"):
        if ch == "`":
            in_code = not in_code
        if ch == "|" and not in_code:
            cells.append(cell.strip())
            cell = ""
        else:
            cell += ch
    cells.append(cell.strip())
    return cells


def code_spans(cell):
    return re.findall(r"`([^`]+)`", cell)


def read_table(root):
    """The rows of the coverage table as dicts keyed by COLUMNS, plus `line` (1-based)."""
    path = root / TABLE
    lines = path.read_text(encoding="utf-8").splitlines()
    for i, line in enumerate(lines):
        if line.startswith("|") and split_cells(line) == COLUMNS:
            break
    else:
        raise SystemExit(f"::error file={TABLE}::対応表の見出し行（| {' | '.join(COLUMNS)} |）が見つかりません。")
    rows = []
    for n, line in enumerate(lines[i + 2:], start=i + 3):
        if not line.startswith("|"):
            break
        cells = split_cells(line)
        if len(cells) != len(COLUMNS):
            raise SystemExit(f"::error file={TABLE},line={n}::列が {len(cells)} 個あります（{len(COLUMNS)} 個のはず）。")
        row = dict(zip(COLUMNS, cells))
        row["line"] = n
        rows.append(row)
    return rows


def benches_of(row):
    """The benchmarks a row names: the backticked names of the benchmark column."""
    return [] if row["ベンチ"] == NONE else code_spans(row["ベンチ"])


def paths_of(row):
    return code_spans(row["場所"])


def components(root):
    labels = json.loads((root / ".github/labels.json").read_text(encoding="utf-8"))
    return [l["name"][len(LABEL_PREFIX):] for l in labels if l["name"].startswith(LABEL_PREFIX)]


def existing_benchmarks(root):
    """Every benchmark that exists, under the name the table uses for it."""
    names = set()
    for f in (root / "benchmark/src/jmh").rglob("*Bench.kt"):
        names.add(f.stem)
    for f in (root / "benchmark/src/realProject").rglob("*Architecture.kt"):
        names.add("realProject:" + f.stem[: -len("Architecture")].lower())
    for f in (root / "benchmark/gradle-profiler").glob("*.scenarios"):
        # A scenario is a top-level `name {` block.
        for m in re.finditer(r"^([A-Za-z_][\w-]*)\s*\{", f.read_text(encoding="utf-8"), re.M):
            names.add("gradle-profiler:" + m.group(1))
    return names


def problems(root):
    """(line, message) for everything in which the table and the repository disagree."""
    rows = read_table(root)
    out = []

    known = components(root)
    seen = {}
    for row in rows:
        name = row["コンポーネント"]
        if name in seen:
            out.append((row["line"], f"コンポーネント「{name}」の行が 2 つあります（{seen[name]} 行目）。"))
        seen[name] = row["line"]
        if name not in known:
            out.append((row["line"], f"コンポーネント「{name}」は .github/labels.json の `影響:` に無い名前です。"))
    for name in known:
        if name not in seen:
            out.append((0, f"コンポーネント「{name}」（.github/labels.json）の行がありません。ベンチを書くか、`なし` と測らない理由を書いてください。"))

    existing = existing_benchmarks(root)
    named = set()
    for row in rows:
        if row["ベンチ"] != NONE and not benches_of(row):
            out.append((row["line"], "ベンチの列は、ベンチの名前を `…` で書くか、`なし` と書いてください。"))
        for bench in benches_of(row):
            named.add(bench)
            if bench not in existing:
                out.append((row["line"], f"ベンチ `{bench}` が benchmark/ にありません（名前が変わった・消えた）。"))
        if row["ベンチ"] == NONE and not row["測らない理由"]:
            out.append((row["line"], f"「{row['コンポーネント']}」はベンチが `なし` なのに、測らない理由が空です。"))
        if not re.fullmatch(r"v\d+\.\d+\.\d+|" + UNREVIEWED, row["確認した版"]):
            out.append((row["line"], f"確認した版「{row['確認した版']}」は `vX.Y.Z` か `{UNREVIEWED}` で書いてください。"))
        if not paths_of(row):
            out.append((row["line"], "場所の列に、リポジトリ直下からのパスを `…` で書いてください。"))
        for p in paths_of(row):
            if not (root / p).exists():
                out.append((row["line"], f"場所 `{p}` がありません。"))
    for bench in sorted(existing - named):
        out.append((0, f"ベンチ `{bench}` がどの行にも載っていません。見ているコンポーネントの行に足してください。"))
    return out


def main():
    root = Path(__file__).resolve().parents[2]
    found = problems(root)
    for line, message in found:
        where = f"file={TABLE},line={line}" if line else f"file={TABLE}"
        print(f"::error {where}::{message}", file=sys.stderr)
    if found:
        print(f"{TABLE} の対応表と実物が {len(found)} か所で食い違っています。", file=sys.stderr)
        return 1
    rows = read_table(root)
    measured = sum(1 for r in rows if r["ベンチ"] != NONE)
    print(f"{TABLE}: {len(rows)} 行（ベンチあり {measured}・測らない {len(rows) - measured}）、"
          f"ベンチ {len(existing_benchmarks(root))} 個。食い違いなし。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
