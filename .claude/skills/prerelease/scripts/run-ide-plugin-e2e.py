#!/usr/bin/env python3
"""リリース前チェックの 10-2（IDE プラグインの E2E）。

`integrationTest`（channel D の Driver smoke。実 IDE を Starter で起動し、sample/jvm の写しを Gradle import してから
ツールウィンドウで生成する GenerationIdeErrorsTest・ToolWindowSmokeTest）は ci.yml にも ide-plugin-nightly.yml にも
無く、どちらのワークフローでも自動では回っていない（katachi-intellij-plugin/build.gradle.kts のコメントと ci.yml の
コメントに、どちらも「run it by hand（before a release）」とある）。手で回すとされたまま忘れられないよう、ここで
リリース前に必ず一度は通す。

コマンドは `./gradlew integrationTest` の1つだけで、ci.yml の `run:` のような一箇所の置き場が無い（どのワークフロー
にも出てこないため）。書き写す先は katachi-intellij-plugin/build.gradle.kts の
`intellijPlatformTesting.testIdeUi.register("integrationTest")`（タスク名が変わったらこのスクリプトも直す）。

- `./gradlew integrationTest` に `--console=plain --stacktrace --no-daemon
  --project-cache-dir .local/tmp/gradle-cache/prerelease-ide-plugin-e2e` を足して、`katachi-intellij-plugin` で走らせる
- IDE 本体は 8 / 10-1 と同じ `katachi-intellij-plugin/.intellijPlatform` のキャッシュを使う（テストへ
  `intellijPlatform.platformPath` をそのまま渡すので、Starter が別に IDE をダウンロードし直すことはない。ただし
  8 / 10-1 を一度も走らせていない初回は、その場でダウンロードが乗る）
- ディスプレイのあるセッションが要る（実 IDE のウィンドウを開く。SSH だけのヘッドレス環境では動かない。macOS の
  ローカルセッションか、GUI を持つ CI ランナーで）
- テストは2本（GenerationIdeErrorsTest・ToolWindowSmokeTest）。1本あたり IDE 起動込みで2分半〜3分が目安。
  sample/jvm の写しを IDE がさらに Gradle import する分、初回はもっとかかる（テスト側のタイムアウトは
  安全側に40分まで見ている）
- Starter は `<git root>/out/ide-tests` に IDE の複製・ログを置く。このスクリプトは消さない
- 落ちても最後まで書く。<release-dir>/ide-plugin-e2e.md に結果を書く。ログは <release-dir>/tmp/ide-plugin-e2e/ に置く
- 終了コード: 通れば 0、落ちれば 1

使い方（リポジトリの直下で）:
  python3 .claude/skills/prerelease/scripts/run-ide-plugin-e2e.py --list
  python3 .claude/skills/prerelease/scripts/run-ide-plugin-e2e.py --release-dir .local/release-v<版>
"""
import argparse, subprocess, sys, time
from pathlib import Path

sys.dont_write_bytecode = True

WORKDIR = "katachi-intellij-plugin"
CMD = "./gradlew integrationTest --console=plain --stacktrace"
# Absolute, so that a step run inside a subdirectory (katachi-intellij-plugin) still puts the cache
# under the repository's .local/tmp/ rather than <subdirectory>/.local/.
GRADLE_FLAGS = ["--no-daemon", "--project-cache-dir", str(Path(__file__).resolve().parents[4] / ".local/tmp/gradle-cache/prerelease-ide-plugin-e2e")]


def repo_root():
    out = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=True)
    return Path(out.stdout.strip())


def full_cmd():
    return f"cd {WORKDIR} && {CMD} " + " ".join(GRADLE_FLAGS)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--release-dir", help="結果（ide-plugin-e2e.md）とログ（tmp/ide-plugin-e2e/）を置く .local/release-v<版>")
    ap.add_argument("--list", action="store_true", help="走らせるコマンドを出すだけ")
    a = ap.parse_args()

    root = repo_root()
    cmd = full_cmd()

    if a.list or not a.release_dir:
        print(f"[integrationTest] Run integrationTest: {cmd}")
        print("前提: ディスプレイのあるセッション（実 IDE のウィンドウを開く）。詳細は references/ide-plugin-nightly.md")
        return 0

    release_dir = (root / a.release_dir).resolve()
    log_dir = release_dir / "tmp" / "ide-plugin-e2e"
    log_dir.mkdir(parents=True, exist_ok=True)
    log = log_dir / "integrationTest.log"

    print("[integrationTest] Run integrationTest", flush=True)
    start = time.monotonic()
    with log.open("w", encoding="utf-8") as out:
        code = subprocess.run(["bash", "-c", cmd], cwd=root, stdout=out, stderr=subprocess.STDOUT).returncode
    sec = time.monotonic() - start
    print(f"      {'OK' if code == 0 else 'FAILED'}（{sec:.0f} 秒）{log.as_uri()}", flush=True)

    summary = release_dir / "ide-plugin-e2e.md"
    summary.write_text(
        "# IDE プラグインの E2E（integrationTest）\n\n"
        f"- 結果: {'OK' if code == 0 else '**FAILED**'}\n"
        f"- 所要: {sec:.0f} 秒\n"
        f"- ログ: [log]({log.as_uri()})\n\n"
        "再現する:\n\n"
        f"```shell\n{cmd}\n```\n",
        encoding="utf-8",
    )
    print(f"結果: {'OK' if code == 0 else 'FAILED'}。{summary.as_uri()}")
    return 0 if code == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
