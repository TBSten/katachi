#!/usr/bin/env python3
"""リリース前チェックの 12（benchmark の棚卸し）の材料を出す。

benchmark は `check` に入らず、CI の bench ジョブは遅くなっても落ちない。機能を足してベンチを足し忘れても、
履歴の保存が止まっても、どこにも出ない。リリースの前に 1 回、次の 3 つを材料として並べる（判断はオーケストレータ）。

1. 範囲: `benchmark/README.md` の対応表の行ごとに、基準（直前のタグ）からそのコンポーネントが変わったか、
   行が見直されているか（「確認した版」）。対応表と実物の食い違い、コードに残った「benchmark で分かったら」の TODO
2. 数値: gh-pages の履歴（`dev/bench/data.js`。CI の bench-store が main の push ごとに足す）から、
   基準の時点 → 今 の倍率。JMH は手元で走らせない（手元の数値は CI の履歴と比べられない）
3. 鮮度: 履歴が今のコミットまで続いているか、固定しているコミット・版がいつのものか、週 1 の gradle-profiler

Gradle を使わず数秒で終わるので、ほかの手順と重ねてよい。

使い方（リポジトリの直下で）:
  python3 .claude/skills/prerelease/scripts/check-benchmark.py --release-dir .local/release-v<版>
  python3 .claude/skills/prerelease/scripts/check-benchmark.py                 # 標準出力に出すだけ
  python3 .claude/skills/prerelease/scripts/check-benchmark.py --base v0.3.0   # 基準を指定する
  python3 .claude/skills/prerelease/scripts/check-benchmark.py --no-fetch      # gh-pages を取り直さない
"""
import argparse
import datetime
import importlib.util
import json
import re
import shutil
import statistics
import subprocess
import sys
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "check-docs-against-impl" / "scripts"))
import public_api  # noqa: E402

# ci.yml の bench-store の alert-threshold と同じ（time 150%・alloc 120%）。
TIME_WARN, ALLOC_WARN = 1.5, 1.2
# 警告にはしないが目を通す倍率（time だけ。runner の速さは回ごとに 0.6〜1.35 倍ぶれる）。
TIME_NOTE = 1.2
# 基準の時点・今として中央値を取る回数。1 回だけだと runner のぶれをそのまま読んでしまう。
BASE_RUNS, NOW_RUNS = 5, 7
# runner の速さをそろえる物差し。katachi のコードを通らない（Konsist がファイルを読むだけ）ので、
# この値の動きは runner の速さの動きとして読める。
RUNNER_REFERENCE = ("KonsistScopeBench.parse", '"files":"2000"')
# main の先頭より履歴がこの日数以上古ければ、bench-store が止まっているとみなす。
STALE_DAYS = 2
# 固定しているコミット・版がこの日数以上そのままなら、見直しの候補にする。
OLD_PIN_DAYS = 180


def git(root, *args, check=True):
    r = subprocess.run(["git", "-C", str(root), *args], capture_output=True, text=True)
    if check and r.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)}: {r.stderr.strip()}")
    return r.stdout


def load_coverage(root):
    path = root / ".github" / "scripts" / "check-benchmark-coverage.py"
    spec = importlib.util.spec_from_file_location("benchmark_coverage", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def current_version(root):
    toml = (root / "gradle" / "libs.versions.toml").read_text(encoding="utf-8")
    m = re.search(r'^katachi\s*=\s*"([^"]+)"', toml, re.M)
    if not m:
        sys.exit('gradle/libs.versions.toml に katachi = "..." が見つかりません')
    return m.group(1)


def warn(priority, title, detail):
    return f"### [WARN / priority {priority}/10] {title}\n\n{detail}\n"


# ---------------------------------------------------------------------------------------------
# 1. 範囲
# ---------------------------------------------------------------------------------------------

def shortstat(root, base, paths):
    """(ファイル数, 足した行, 消した行)。基準が無ければ None。"""
    if not base:
        return None
    out = git(root, "diff", "--shortstat", base, "HEAD", "--", *paths)
    n = lambda pattern: int(m.group(1)) if (m := re.search(pattern, out)) else 0  # noqa: E731
    return n(r"(\d+) files? changed"), n(r"(\d+) insertions?"), n(r"(\d+) deletions?")


def coverage_section(root, base, version):
    coverage = load_coverage(root)
    lines, warnings = [], []

    found = coverage.problems(root)
    if found:
        warnings.append(warn(8, "benchmark の対応表が実物と食い違っている",
                             "CI の `check` も同じ理由で落ちる。\n\n" + "\n".join(f"- {m}" for _, m in found)))

    want = f"v{version}"
    judge_now, bump = [], []
    lines += ["| コンポーネント | 基準からの変更 | ベンチ | 確認した版 | 判定 |", "|---|---|---|---|---|"]
    for row in coverage.read_table(root):
        stat = shortstat(root, base, coverage.paths_of(row))
        changed = "基準なし" if stat is None else ("変更なし" if stat[0] == 0 else f"{stat[0]} ファイル +{stat[1]} −{stat[2]}")
        reviewed = row["確認した版"]
        if reviewed == coverage.UNREVIEWED:
            verdict = "**要判断**（未確認）"
            judge_now.append(row)
        elif reviewed == want:
            verdict = "確認済み"
        elif stat is None or stat[0] > 0:
            verdict = "**要確認**（変更あり）"
            judge_now.append(row)
        else:
            verdict = f"変更なし（{want} に上げてよい）"
            bump.append(row)
        lines.append(f"| {row['コンポーネント']} | {changed} | {row['ベンチ']} | {reviewed} | {verdict} |")

    lines.append("")
    if judge_now:
        lines.append(f"見直す行: {len(judge_now)} 行。行ごとに、基準からの変更で新しい入口・通り道が増えていないかを見て、"
                     "ベンチを足すか・測らない理由を直すかを決める。")
        for row in judge_now:
            reason = f"測らない理由: {row['測らない理由']}" if row["ベンチ"] == coverage.NONE else f"ベンチ: {row['ベンチ']}"
            lines.append(f"- {row['コンポーネント']}（{row['入口']}）… {reason}")
    else:
        lines.append("見直す行: 0 行。")
    if bump:
        lines.append(f"\n変更が無いので「確認した版」を {want} に上げるだけの行: "
                     + "、".join(r["コンポーネント"] for r in bump))

    own = shortstat(root, base, ["benchmark"])
    if own is not None:
        lines.append(f"\n`benchmark/` 自体の変更（{base}..HEAD）: {own[0]} ファイル +{own[1]} −{own[2]}")

    # コードの側の合図: 「benchmark で分かったら直す」の類は、ベンチが無いと永遠に分からない。
    todo = git(root, "grep", "-n", "-i", "-E", r"(TODO|FIXME).*(benchmark|ベンチ)", "--", "*.kt", "*.kts",
               ":!benchmark", check=False).strip()
    lines.append("")
    if todo:
        lines.append("コードに残っている benchmark 待ちの TODO:")
        lines += [f"- `{l}`" for l in todo.splitlines()]
    else:
        lines.append("コードに残っている benchmark 待ちの TODO: なし")
    return lines, warnings, len(judge_now)


# ---------------------------------------------------------------------------------------------
# 2. 数値
# ---------------------------------------------------------------------------------------------

def read_history(root, fetch):
    """gh-pages の dev/bench/data.js の `entries`。取れなければ (None, 理由)。"""
    if fetch:
        git(root, "fetch", "--quiet", "origin", "gh-pages", check=False)
    for ref in ("FETCH_HEAD" if fetch else None, "origin/gh-pages", "gh-pages"):
        if not ref:
            continue
        r = subprocess.run(["git", "-C", str(root), "show", f"{ref}:dev/bench/data.js"], capture_output=True, text=True)
        if r.returncode == 0 and "{" in r.stdout:
            try:
                return json.loads(r.stdout[r.stdout.index("{"):])["entries"], None
            except (ValueError, KeyError) as e:
                return None, f"dev/bench/data.js が読めません（{e}）"
    return None, "gh-pages の dev/bench/data.js が取れません（`git fetch origin gh-pages` が通るか確かめる）"


def short_name(name):
    """`me.tbsten.katachi.benchmark.WalkBench.validate ( {"files":"1000"} )` -> (`WalkBench`, `validate (files=1000)`)。"""
    m = re.match(r"(?:[\w.]+\.)?(\w+Bench)\.(\w+)(?: \( (\{.*\}) \))?$", name)
    if not m:
        return None, name
    params = ""
    if m.group(3):
        try:
            params = " (" + ", ".join(f"{k}={v}" for k, v in json.loads(m.group(3)).items()) + ")"
        except ValueError:
            params = " " + m.group(3)
    return m.group(1), m.group(2) + params


def median_of(runs, name, reference=None):
    values = []
    for run in runs:
        by_name = {b["name"]: b["value"] for b in run["benches"]}
        if name not in by_name:
            continue
        if reference is None:
            values.append(by_name[name])
        elif by_name.get(reference):
            values.append(by_name[name] / by_name[reference])
    return statistics.median(values) if values else None


def numbers_section(root, base, fetch):
    entries, problem = read_history(root, fetch)
    if entries is None:
        return [problem], [warn(8, "benchmark の履歴が読めない", problem)], None

    ancestors_of_head = set(git(root, "rev-list", "HEAD").split())
    ancestors_of_base = set(git(root, "rev-list", base).split()) if base else set()
    lines, warnings, summary = [], [], {"series": 0, "warn": 0, "note": 0, "new": 0}
    detail = ["| 系列 | 基準 | 今 | 倍率 | runner 補正 | |", "|---|---|---|---|---|---|"]
    by_group = {}
    latest = None

    for suite, runs in entries.items():
        is_alloc = "alloc" in suite
        threshold = ALLOC_WARN if is_alloc else TIME_WARN
        now_runs = [r for r in runs if r["commit"]["id"] in ancestors_of_head][-NOW_RUNS:]
        if not now_runs:
            lines.append(f"- {suite}: HEAD までの計測が履歴に無い（履歴 {len(runs)} 回）。")
            continue
        base_runs = [r for r in runs if r["commit"]["id"] in ancestors_of_base][-BASE_RUNS:]
        if latest is None or now_runs[-1]["date"] > latest["date"]:
            latest = now_runs[-1]
        units = {b["name"]: b.get("unit", "") for b in now_runs[-1]["benches"]}
        names = list(units)
        reference = None
        if not is_alloc:
            reference = next((n for n in names if all(part in n for part in RUNNER_REFERENCE)), None)
        gone = sorted({b["name"] for r in base_runs for b in r["benches"]} - set(names))

        for name in names:
            summary["series"] += 1
            cls, label = short_name(name)
            group = by_group.setdefault(f"{suite}: {cls}" if cls else suite, {"ratios": [], "norms": [], "new": 0, "warn": 0})
            now = median_of(now_runs, name)
            unit = units[name]
            before = median_of(base_runs, name)
            shown = f"{cls}.{label}" if cls else label
            if before is None:
                summary["new"] += 1
                group["new"] += 1
                detail.append(f"| {suite}: {shown} | - | {now:.4g} {unit} | 新規 | | |")
                continue
            ratio = now / before
            norm = None
            if reference and name != reference:
                a, b = median_of(now_runs, name, reference), median_of(base_runs, name, reference)
                norm = a / b if a and b else None
            group["ratios"].append(ratio)
            if norm is not None:
                group["norms"].append(norm)
            mark = ""
            if ratio >= threshold and (norm is None or norm >= threshold):
                mark = "**警告**"
                summary["warn"] += 1
                group["warn"] += 1
                warnings.append(warn(8, f"{shown} が基準の {ratio:.2f} 倍（{suite}）",
                                     f"基準 {before:.4g} {unit} → 今 {now:.4g} {unit}"
                                     + (f"、runner 補正 {norm:.2f} 倍" if norm is not None else "")
                                     + f"。閾値 {threshold:.1f} 倍。"))
            elif not is_alloc and max(ratio, norm or 0) >= TIME_NOTE:
                mark = "注意"
                summary["note"] += 1
            detail.append(f"| {suite}: {shown} | {before:.4g} | {now:.4g} {unit} | {ratio:.2f} | "
                          f"{'' if norm is None else f'{norm:.2f}'} | {mark} |")
        for name in gone:
            cls, label = short_name(name)
            detail.append(f"| {suite}: {cls + '.' if cls else ''}{label} | あり | - | 消えた | | |")

        lines.append(f"- {suite}: 基準 {len(base_runs)} 回の中央値 → 今 {len(now_runs)} 回の中央値"
                     f"（〜{now_runs[-1]['commit']['id'][:8]}）" + ("" if base_runs else "。**基準の時点の計測が無い**"))

    span = lambda values: "-" if not values else f"{min(values):.2f}〜{max(values):.2f}"  # noqa: E731
    lines += ["", "| まとまり | 系列 | 倍率 | runner 補正 | 新規 | 警告 |", "|---|---|---|---|---|---|"]
    for label, g in by_group.items():
        count = len(g["ratios"]) + g["new"]
        lines.append(f"| {label} | {count} | {span(g['ratios'])} | {span(g['norms'])} | {g['new'] or ''} | {g['warn'] or ''} |")
    lines += ["",
              f"{summary['series']} 系列: 警告 {summary['warn']}・注意 {summary['note']}・新規 {summary['new']}。"
              f"倍率は 今 ÷ 基準（1.00 = 変化なし）。警告は time {TIME_WARN} 倍・alloc {ALLOC_WARN} 倍以上"
              "（time は runner 補正の倍率も閾値以上のときだけ）。",
              "", "<details>", "<summary>系列ごと</summary>", "", *detail, "", "</details>"]
    return lines, warnings, latest


# ---------------------------------------------------------------------------------------------
# 3. 鮮度
# ---------------------------------------------------------------------------------------------

def commit_date(root, ref):
    out = git(root, "log", "-1", "--format=%cs", ref, check=False).strip()
    return datetime.date.fromisoformat(out) if out else None


def pin(root, label, file, pattern):
    """固定している値と、その行が最後に変わった日。"""
    text = (root / file).read_text(encoding="utf-8")
    m = re.search(pattern, text, re.M)
    if not m:
        return f"- {label}: `{file}` に見つからない（置き場が変わった。この手順の `pin()` を直す）", None
    value = m.group(1)
    # その行ごと探す（値だけだと、同じ版の別のライブラリの変更を拾う）。
    since = git(root, "log", "-1", "--format=%cs", f"-S{m.group(0)}", "--", file, check=False).strip()
    days = (datetime.date.today() - datetime.date.fromisoformat(since)).days if since else None
    age = "" if days is None else f"（{since} から {days} 日）"
    return f"- {label}: `{value[:12]}`{age} … `{file}`", days


def freshness_section(root, latest):
    lines, warnings = [], []
    head = git(root, "rev-parse", "HEAD").strip()
    if latest:
        sha = latest["commit"]["id"]
        behind = int(git(root, "rev-list", "--count", f"{sha}..HEAD").strip())
        measured = datetime.datetime.fromtimestamp(latest["date"] / 1000, datetime.timezone.utc).date()
        lines.append(f"- 履歴の最後: {sha[:8]}（{measured} 計測）。HEAD {head[:8]} まで {behind} コミット"
                     + ("（HEAD そのものが測られている）" if behind == 0 else ""))
        tip = commit_date(root, "origin/main") or commit_date(root, "HEAD")
        if tip and (tip - measured).days >= STALE_DAYS:
            warnings.append(warn(8, "benchmark の履歴が止まっている",
                                 f"main の先頭は {tip}、履歴の最後の計測は {measured}。ci.yml の bench-store が失敗していないか、"
                                 "GitHub の Actions で main の CI を見る。"))
        elif behind:
            lines.append("  - HEAD が main に入って bench-store が終われば足される。main に無いコミットをリリースしようとしていないかだけ確かめる")
    else:
        lines.append("- 履歴の最後: 分からない（履歴が読めない）")

    old = []
    for label, file, pattern in [
        ("実プロジェクト（nowinandroid）の固定コミット", ".github/workflows/ci.yml", r"REAL_PROJECT_COMMIT:\s*(\w+)"),
        ("gradle-profiler の版", ".github/workflows/gradle-profiler.yml", r"GRADLE_PROFILER_VERSION:\s*'?([\w.]+)"),
        ("JMH の版", "gradle/libs.versions.toml", r'^jmh\s*=\s*"([^"]+)"'),
        ("JMH の Gradle plugin の版", "gradle/libs.versions.toml", r'^jmhPlugin\s*=\s*"([^"]+)"'),
    ]:
        line, days = pin(root, label, file, pattern)
        lines.append(line)
        if days is not None and days >= OLD_PIN_DAYS:
            old.append(label)
    if old:
        warnings.append(warn(3, "benchmark が固定しているコミット・版が古い",
                             f"{OLD_PIN_DAYS} 日以上そのまま: " + "、".join(old) + "。上げるかこのままかを決める。"))

    if shutil.which("gh"):
        r = subprocess.run(["gh", "run", "list", "--workflow", "gradle-profiler.yml", "--limit", "3",
                            "--json", "conclusion,createdAt,headSha"], capture_output=True, text=True, cwd=root)
        runs = json.loads(r.stdout) if r.returncode == 0 and r.stdout.strip() else None
    else:
        runs = None
    if runs:
        lines.append("- 週 1 の gradle-profiler: " + "、".join(
            f"{x['createdAt'][:10]} {x['conclusion'] or '実行中'}（{x['headSha'][:8]}）" for x in runs))
        if runs[0]["conclusion"] not in ("success", None, ""):
            warnings.append(warn(5, "週 1 の gradle-profiler の最新の実行が成功していない",
                                 f"{runs[0]['createdAt'][:10]} {runs[0]['conclusion']}。"))
    else:
        lines.append("- 週 1 の gradle-profiler: **未確認**（`gh` が使えない。GitHub の Actions で `Gradle profiler` の最新の実行が"
                     "成功しているかを見て、ここに書く）")
    return lines, warnings


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", help="基準のタグ（既定: 直前のリリースのタグ）")
    ap.add_argument("--release-dir", help=".local/release-v<版>。指定すると tmp/benchmark-material.md に書く")
    ap.add_argument("--no-fetch", action="store_true", help="gh-pages を取り直さない")
    a = ap.parse_args()

    root = public_api.repo_root()
    version = current_version(root)
    base = a.base or public_api.latest_release_tag(root)

    scope, w1, to_judge = coverage_section(root, base, version)
    numbers, w2, latest = numbers_section(root, base, not a.no_fetch)
    fresh, w3 = freshness_section(root, latest)
    warnings = w1 + w2 + w3

    out = [f"# 12. benchmark の棚卸し（材料）", "",
           f"今回の版 v{version} / 基準 {base or '（タグなし）'} / HEAD {git(root, 'rev-parse', '--short', 'HEAD').strip()}", "",
           "## 範囲（benchmark/README.md の対応表）", "", *scope, "",
           "## 数値（gh-pages の履歴）", "", *numbers, "",
           "## 鮮度", "", *fresh, "",
           f"## 警告（{len(warnings)} 件）", "", *(warnings or ["なし"])]
    text = "\n".join(out) + "\n"

    if a.release_dir:
        target = Path(a.release_dir) / "tmp" / "benchmark-material.md"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding="utf-8")
        print(f"材料: {target}")
        print(f"見直す行 {to_judge} 行 / 警告 {len(warnings)} 件")
    else:
        print(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
