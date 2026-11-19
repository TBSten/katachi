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

`--android-studio` は手順 10-5（Android Studio での E2E）。`integrationTestAndroidStudio`（SmokeIde の smoke を本物の
Android Studio で起動する。版は build.gradle.kts の `register("integrationTestAndroidStudio")`）で
`ArchModuleNameSmokeTest` を走らせる。定義モジュールの検出を Android Studio の既定設定（sync でタスク一覧を作らない）の
まま見る（KATACHI-363 で壊れたところ）。初回は Android Studio のダウンロード（約 1.5 GB）が乗る。結果は
<release-dir>/ide-plugin-android-studio.md、ログは <release-dir>/tmp/ide-plugin-android-studio/。

使い方（リポジトリの直下で）:
  python3 .claude/skills/prerelease/scripts/run-ide-plugin-e2e.py --list
  python3 .claude/skills/prerelease/scripts/run-ide-plugin-e2e.py --release-dir .local/release-v<版>
  python3 .claude/skills/prerelease/scripts/run-ide-plugin-e2e.py --release-dir .local/release-v<版> --android-studio
"""
import argparse, subprocess, sys, time
from pathlib import Path

sys.dont_write_bytecode = True

WORKDIR = "katachi-intellij-plugin"
CMD = "./gradlew integrationTest --console=plain --stacktrace"
# 10-5: the smokes on SmokeIde, started as Android Studio. ArchModuleNameSmokeTest sees the definition module found with
# Android Studio's defaults (no task list on sync), where KATACHI-363 broke.
CMD_ANDROID_STUDIO = "./gradlew integrationTestAndroidStudio --tests '*ArchModuleNameSmokeTest*' --console=plain --stacktrace"
# Absolute, so that a step run inside a subdirectory (katachi-intellij-plugin) still puts the cache
# under the repository's .local/tmp/ rather than <subdirectory>/.local/.
GRADLE_FLAGS = ["--no-daemon", "--project-cache-dir", str(Path(__file__).resolve().parents[4] / ".local/tmp/gradle-cache/prerelease-ide-plugin-e2e")]


def repo_root():
    out = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=True)
    return Path(out.stdout.strip())


def full_cmd(android_studio=False):
    return f"cd {WORKDIR} && {CMD_ANDROID_STUDIO if android_studio else CMD} " + " ".join(GRADLE_FLAGS)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--release-dir", help="結果（ide-plugin-e2e.md）とログ（tmp/ide-plugin-e2e/）を置く .local/release-v<版>")
    ap.add_argument("--list", action="store_true", help="走らせるコマンドを出すだけ")
    ap.add_argument("--android-studio", action="store_true", help="10-5: Android Studio で integrationTestAndroidStudio を走らせる")
    a = ap.parse_args()

    root = repo_root()
    cmd = full_cmd(a.android_studio)
    task = "integrationTestAndroidStudio" if a.android_studio else "integrationTest"
    name = "ide-plugin-android-studio" if a.android_studio else "ide-plugin-e2e"
    title = "Android Studio での E2E（integrationTestAndroidStudio）" if a.android_studio else "IDE プラグインの E2E（integrationTest）"

    if a.list or not a.release_dir:
        print(f"[{task}] Run {task}: {cmd}")
        print("前提: ディスプレイのあるセッション（実 IDE のウィンドウを開く）。詳細は references/ide-plugin-nightly.md")
        return 0

    release_dir = (root / a.release_dir).resolve()
    log_dir = release_dir / "tmp" / name
    log_dir.mkdir(parents=True, exist_ok=True)
    log = log_dir / f"{task}.log"

    print(f"[{task}] Run {task}", flush=True)
    start = time.monotonic()
    with log.open("w", encoding="utf-8") as out:
        code = subprocess.run(["bash", "-c", cmd], cwd=root, stdout=out, stderr=subprocess.STDOUT).returncode
    sec = time.monotonic() - start
    print(f"      {'OK' if code == 0 else 'FAILED'}（{sec:.0f} 秒）{log.as_uri()}", flush=True)

    summary = release_dir / f"{name}.md"
    summary.write_text(
        f"# {title}\n\n"
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
