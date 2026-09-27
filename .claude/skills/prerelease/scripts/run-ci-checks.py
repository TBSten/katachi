#!/usr/bin/env python3
"""リリース前チェックの 8（CI と同等のチェック）。`.github/workflows/ci.yml` の `run:` を手元で順に走らせる。

コマンドはここに書き写さず、毎回 ci.yml から読む（ci.yml が唯一の置き場。手順を足しても直すのは ci.yml だけ）。

- 走らせるのは、ジョブ `check` と `docs` と `ide-plugin` の `run:` 全部。ci.yml に書いた順（ステップの `working-directory` も読む）
- ベンチマークのジョブ（名前が `bench-` で始まるもの）は既定で飛ばす。失敗で落ちない計測で、JMH だけで 15〜20 分かかる。
  `--with-bench` で JMH（`bench-jmh` の `./gradlew` の行）だけ足す。nowinandroid の計測は CI の環境変数に頼るので走らせない
- `./gradlew` の行には `--no-daemon --console=plain --project-cache-dir .local/tmp/gradle-cache/prerelease-ci` を足す
  （ほかのビルドと cache を取り合わない）
- 1つ落ちても最後まで走らせ、全部の結果を表にする（<release-dir>/ci-checks.md）。ログは1コマンド1ファイルで
  <release-dir>/tmp/ci-checks/ に置く（作業場所の直下には人が読む成果物だけを置く）
- 終了コード: 全部通れば 0、1つでも落ちれば 1

使い方（リポジトリの直下で）:
  python3 .claude/skills/prerelease/scripts/run-ci-checks.py --release-dir .local/release-v<版>
  python3 .claude/skills/prerelease/scripts/run-ci-checks.py --list      # 走らせるコマンドを出すだけ
"""
import argparse, re, subprocess, sys, time
from pathlib import Path

sys.dont_write_bytecode = True

JOBS = ("check", "docs", "ide-plugin")
GRADLE_FLAGS = ["--no-daemon", "--console=plain", "--project-cache-dir", ".local/tmp/gradle-cache/prerelease-ci"]


def repo_root():
    out = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=True)
    return Path(out.stdout.strip())


def jobs_of(ci_yml):
    """{ジョブ名: [(ステップ名, コマンド)]}。ci.yml の形（2 字下げのジョブ、`- name:` と `run:`）だけを読む小さな読み手。"""
    jobs, job, name, workdir = {}, None, None, None
    lines = ci_yml.splitlines()
    # Only what is under the top-level `jobs:` (not `on:`, whose keys sit at the same indent).
    i = next((n + 1 for n, l in enumerate(lines) if l.rstrip() == "jobs:"), len(lines))
    while i < len(lines):
        line = lines[i]
        if re.match(r"^[A-Za-z]", line):
            break
        m = re.match(r"^  ([A-Za-z0-9_-]+):\s*$", line)
        if m:
            job, name = m.group(1), None
            jobs[job] = []
            i += 1
            continue
        m = re.match(r"^\s+- name:\s*(.+?)\s*$", line)
        if m and job:
            name, workdir = m.group(1), None
        m = re.match(r"^\s+working-directory:\s*(.+?)\s*$", line)
        if m and job:
            workdir = m.group(1)
        m = re.match(r"^(\s+)run:\s*(.*?)\s*$", line)
        if m and job:
            indent, value = len(m.group(1)), m.group(2)
            if value in (">", "|", ">-", "|-"):
                body = []
                i += 1
                while i < len(lines) and (not lines[i].strip() or len(lines[i]) - len(lines[i].lstrip()) > indent):
                    body.append(lines[i].strip())
                    i += 1
                body = [b for b in body if b]
                value = (" " if value.startswith(">") else "\n").join(body)
                jobs[job].append((name or value, in_dir(workdir, value)))
                continue
            jobs[job].append((name or value, in_dir(workdir, value)))
        i += 1
    return jobs


def in_dir(workdir, cmd):
    """Runs `cmd` in the step's working-directory, as GitHub Actions does."""
    return f"cd {workdir} && {cmd}" if workdir else cmd


def commands(root, with_bench):
    jobs = jobs_of((root / ".github" / "workflows" / "ci.yml").read_text(encoding="utf-8"))
    missing = [j for j in JOBS if j not in jobs]
    if missing:
        sys.exit(f"ci.yml にジョブ {', '.join(missing)} が見つかりません。このスクリプトの JOBS を直す")
    picked = [(job, name, cmd) for job in JOBS for name, cmd in jobs[job]]
    if with_bench and "bench-jmh" in jobs:
        picked += [("bench-jmh", n, c) for n, c in jobs["bench-jmh"] if c.startswith("./gradlew")]
    skipped = sorted(j for j in jobs if j not in JOBS and not (with_bench and j == "bench-jmh"))
    return picked, skipped


def with_flags(cmd):
    return cmd + " " + " ".join(GRADLE_FLAGS) if "./gradlew" in cmd.split(" && ")[-1][:10] else cmd


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--release-dir", help="結果の表（ci-checks.md）とログ（tmp/ci-checks/）を置く .local/release-v<版>")
    ap.add_argument("--list", action="store_true", help="走らせるコマンドを出すだけ")
    ap.add_argument("--with-bench", action="store_true", help="JMH（bench-jmh）も走らせる")
    a = ap.parse_args()

    root = repo_root()
    picked, skipped = commands(root, a.with_bench)
    if a.list or not a.release_dir:
        for job, name, cmd in picked:
            print(f"[{job}] {name}: {with_flags(cmd)}")
        print(f"飛ばすジョブ: {', '.join(skipped) or '（無し）'}")
        return 0

    release_dir = (root / a.release_dir).resolve()
    log_dir = release_dir / "tmp" / "ci-checks"
    log_dir.mkdir(parents=True, exist_ok=True)
    rows = []
    for n, (job, name, cmd) in enumerate(picked, 1):
        log = log_dir / f"{n:02d}-{re.sub(r'[^A-Za-z0-9]+', '-', name).strip('-').lower()[:50]}.log"
        print(f"[{n}/{len(picked)}] {job}: {name}", flush=True)
        start = time.monotonic()
        with log.open("w", encoding="utf-8") as out:
            code = subprocess.run(["bash", "-c", with_flags(cmd)], cwd=root, stdout=out, stderr=subprocess.STDOUT).returncode
        sec = time.monotonic() - start
        rows.append((job, name, code, sec, log))
        print(f"      {'OK' if code == 0 else 'FAILED'}（{sec:.0f} 秒）{log.as_uri()}", flush=True)

    failed = [r for r in rows if r[2] != 0]
    table = ["| ジョブ | ステップ | 結果 | 秒 | ログ |", "|---|---|---|---|---|"]
    table += [f"| {j} | {s} | {'OK' if c == 0 else '**FAILED**'} | {t:.0f} | [log]({l.as_uri()}) |" for j, s, c, t, l in rows]
    summary = release_dir / "ci-checks.md"
    summary.write_text(
        "# CI と同等のチェック\n\n"
        f"- 通った: {len(rows) - len(failed)} / {len(rows)}\n"
        f"- 飛ばしたジョブ: {', '.join(skipped) or '（無し）'}\n\n" + "\n".join(table) + "\n",
        encoding="utf-8",
    )
    print(f"結果: {len(rows) - len(failed)} / {len(rows)} 通過。{summary.as_uri()}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
