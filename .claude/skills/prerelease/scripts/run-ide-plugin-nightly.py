#!/usr/bin/env python3
"""リリース前チェックの 10（IDE プラグインの nightly 相当のチェック）。

`.github/workflows/ide-plugin-nightly.yml` の `pbt` ジョブは、8 の `checkIdePlugin`（`test` 経由）が固定シード・少ない
系列数で走らせる uiTest（property-based tests）を、毎晩 20 倍の系列数・毎回違うシードで走らせている。nightly は落ちても
利用者が気づきにくいので、リリース前に一度は同じ広さで手元でも通す。

コマンドはここに書き写さず、毎回 ide-plugin-nightly.yml の `pbt` ジョブの「Run uiTest」ステップの `run:` から読む
（ide-plugin-nightly.yml が唯一の置き場。手順を足しても直すのはワークフローの方だけ）。

- `${PBT_SEED}` / `${PBT_SCALE}` は nightly の `env:`（既定はそれぞれ実行 id、20）に相当する。このスクリプトでは
  既定で実行時刻・20 に置き換える。`--seed` / `--scale` で上書きできる
- `./gradlew` の行には `--no-daemon --project-cache-dir .local/tmp/gradle-cache/prerelease-ide-plugin-nightly` を足す
  （ほかのビルドと cache を取り合わない。`--console=plain` は run: に既にある）
- 落ちても結果は最後まで書く。<release-dir>/ide-plugin-nightly.md に結果と、同じシードで再現するコマンドを書く。
  ログは <release-dir>/tmp/ide-plugin-nightly/ に置く
- 終了コード: 通れば 0、落ちれば 1

使い方（リポジトリの直下で）:
  python3 .claude/skills/prerelease/scripts/run-ide-plugin-nightly.py --list
  python3 .claude/skills/prerelease/scripts/run-ide-plugin-nightly.py --release-dir .local/release-v<版>
  python3 .claude/skills/prerelease/scripts/run-ide-plugin-nightly.py --release-dir .local/release-v<版> --scale 5   # 短く済ませたい時
"""
import argparse, re, subprocess, sys, time
from pathlib import Path

sys.dont_write_bytecode = True

WORKFLOW = Path(".github/workflows/ide-plugin-nightly.yml")
JOB = "pbt"
STEP_NAME = "Run uiTest"
GRADLE_FLAGS = ["--no-daemon", "--project-cache-dir", ".local/tmp/gradle-cache/prerelease-ide-plugin-nightly"]


def repo_root():
    out = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=True)
    return Path(out.stdout.strip())


def read_step(root):
    """ide-plugin-nightly.yml の JOB ジョブから、STEP_NAME の run:（と working-directory）だけを読む小さな読み手。
    run-ci-checks.py の jobs_of() と同じ、`- name:` / `working-directory:` / `run:` の形だけを追う。
    """
    text = (root / WORKFLOW).read_text(encoding="utf-8")
    lines = text.splitlines()
    job = None
    name = None
    workdir = None
    step_workdir = None  # working-directory の、STEP_NAME の run: を見つけた時点でのスナップショット
    in_run = False
    run_indent = 0
    run_lines = []
    for line in lines:
        m = re.match(r"^  ([A-Za-z0-9_-]+):\s*$", line)
        if m:
            job = m.group(1)
            in_run = False
            continue
        if job != JOB:
            continue
        if in_run:
            if line.strip() and (len(line) - len(line.lstrip())) > run_indent:
                run_lines.append(line.strip())
                continue
            in_run = False
        m = re.match(r"^\s+- name:\s*(.+?)\s*$", line)
        if m:
            name, workdir = m.group(1), None
            continue
        m = re.match(r"^\s+working-directory:\s*(.+?)\s*$", line)
        if m:
            workdir = m.group(1)
            continue
        m = re.match(r"^(\s+)run:\s*(>|>-|\|-|\|)?\s*$", line)
        if m and name == STEP_NAME:
            run_indent = len(m.group(1))
            in_run = True
            step_workdir = workdir  # 後続のステップが workdir を書き換える前に控えておく
    if not run_lines:
        sys.exit(f"{WORKFLOW} の {JOB} ジョブに「{STEP_NAME}」の run: が見つかりません。このスクリプトを直す")
    cmd = " ".join(run_lines)
    return f"cd {step_workdir} && {cmd}" if step_workdir else cmd


def with_seed_scale(cmd, seed, scale):
    return cmd.replace("${PBT_SEED}", str(seed)).replace("${PBT_SCALE}", str(scale))


def with_flags(cmd):
    return cmd + " " + " ".join(GRADLE_FLAGS)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--release-dir", help="結果（ide-plugin-nightly.md）とログ（tmp/ide-plugin-nightly/）を置く .local/release-v<版>")
    ap.add_argument("--list", action="store_true", help="走らせるコマンドを出すだけ")
    ap.add_argument("--seed", default=None, help="PBT のシード（既定: 実行時刻）")
    ap.add_argument("--scale", default="20", help="既定の系列数の何倍走らせるか（nightly の既定と同じ 20）")
    a = ap.parse_args()

    root = repo_root()
    raw_cmd = read_step(root)
    seed = a.seed or str(int(time.time()))
    cmd = with_seed_scale(raw_cmd, seed, a.scale)

    if a.list or not a.release_dir:
        print(f"[{JOB}] {STEP_NAME}: {with_flags(cmd)}")
        print(f"seed={seed} scale={a.scale}（--seed / --scale で上書き）")
        return 0

    release_dir = (root / a.release_dir).resolve()
    log_dir = release_dir / "tmp" / "ide-plugin-nightly"
    log_dir.mkdir(parents=True, exist_ok=True)
    log = log_dir / f"uiTest-scale{a.scale}-seed{seed}.log"

    print(f"[{JOB}] {STEP_NAME}（seed={seed} scale={a.scale}）", flush=True)
    start = time.monotonic()
    with log.open("w", encoding="utf-8") as out:
        code = subprocess.run(["bash", "-c", with_flags(cmd)], cwd=root, stdout=out, stderr=subprocess.STDOUT).returncode
    sec = time.monotonic() - start
    print(f"      {'OK' if code == 0 else 'FAILED'}（{sec:.0f} 秒）{log.as_uri()}", flush=True)

    replay = with_seed_scale(raw_cmd, seed, a.scale)
    summary = release_dir / "ide-plugin-nightly.md"
    summary.write_text(
        "# IDE プラグインの nightly 相当のチェック\n\n"
        f"- 結果: {'OK' if code == 0 else '**FAILED**'}\n"
        f"- seed: `{seed}` / scale: `{a.scale}`\n"
        f"- 所要: {sec:.0f} 秒\n"
        f"- ログ: [log]({log.as_uri()})\n\n"
        "同じシードで再現する:\n\n"
        f"```shell\n{replay}\n```\n",
        encoding="utf-8",
    )
    print(f"結果: {'OK' if code == 0 else 'FAILED'}。{summary.as_uri()}")
    return 0 if code == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
