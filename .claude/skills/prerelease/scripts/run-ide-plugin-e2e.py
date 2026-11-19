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

`--android-studio` は手順 10-5（Android Studio での E2E）。10-2 と同じ smoke を `integrationTestAndroidStudio`（本物の
Android Studio で起動する。版は build.gradle.kts の `register("integrationTestAndroidStudio")`）で走らせる。初回は
Android Studio のダウンロード（約 1.5 GB）が乗る。結果は
<release-dir>/ide-plugin-android-studio.md、ログは <release-dir>/tmp/ide-plugin-android-studio/。

IDE のダウンロードが途中で止まらないように:
- `--android-studio` では、Android Studio を Gradle に落とさせず、先にこのスクリプトが curl で
  ~/.cache/katachi-prerelease/ide/ に落とす（`-C -` で続きから、`--retry 10 --retry-all-errors`、10 KB/s を 2 分下回ったら
  切って続きから）。releases list の sha256 を確かめてから展開し、`-Pkatachi.androidStudio.localPath` で Gradle に渡す。
  2 回目からはキャッシュを使う（`--no-ide-cache` で使わない）
- どちらのモードでも、Gradle の HTTP の待ちと再試行を伸ばす（GRADLE_HTTP_FLAGS。10-2 の IntelliJ IDEA のダウンロードにも効く）

使い方（リポジトリの直下で）:
  python3 .claude/skills/prerelease/scripts/run-ide-plugin-e2e.py --list
  python3 .claude/skills/prerelease/scripts/run-ide-plugin-e2e.py --release-dir .local/release-v<版>
  python3 .claude/skills/prerelease/scripts/run-ide-plugin-e2e.py --release-dir .local/release-v<版> --android-studio
"""
import argparse, hashlib, json, os, platform, re, shutil, subprocess, sys, tempfile, time, urllib.request
from pathlib import Path

sys.dont_write_bytecode = True

WORKDIR = "katachi-intellij-plugin"
CMD = "./gradlew integrationTest --console=plain --stacktrace"
# 10-5: the same smokes, started as Android Studio (SmokeProduct).
CMD_ANDROID_STUDIO = "./gradlew integrationTestAndroidStudio --console=plain --stacktrace"
# Absolute, so that a step run inside a subdirectory (katachi-intellij-plugin) still puts the cache
# under the repository's .local/tmp/ rather than <subdirectory>/.local/.
GRADLE_FLAGS = ["--no-daemon", "--project-cache-dir", str(Path(__file__).resolve().parents[4] / ".local/tmp/gradle-cache/prerelease-ide-plugin-e2e")]
# A whole IDE (1.5 GB and up) over a slow or flaky line: wait longer before Gradle gives a download up, and retry more.
GRADLE_HTTP_FLAGS = [
    "-Dorg.gradle.internal.http.connectionTimeout=120000",
    "-Dorg.gradle.internal.http.socketTimeout=300000",
    "-Dorg.gradle.internal.repository.max.retries=8",
    "-Dorg.gradle.internal.repository.initial.backoff=2000",
]
# 10-5 downloads Android Studio itself, resumable, and hands Gradle the unpacked copy.
IDE_CACHE = Path.home() / ".cache" / "katachi-prerelease" / "ide"
RELEASES_LIST = "https://jb.gg/android-studio-releases-list.json"
CURL_RESUMABLE = ["curl", "-fsSL", "-C", "-", "--retry", "10", "--retry-all-errors", "--retry-delay", "5",
                  "--speed-limit", "10240", "--speed-time", "120"]


def repo_root():
    out = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=True)
    return Path(out.stdout.strip())


def full_cmd(android_studio=False, local_path=None):
    extra = [f"'-Pkatachi.androidStudio.localPath={local_path}'"] if local_path else []
    return f"cd {WORKDIR} && {CMD_ANDROID_STUDIO if android_studio else CMD} " + " ".join(GRADLE_FLAGS + GRADLE_HTTP_FLAGS + extra)


def android_studio_version(root):
    """The version integrationTestAndroidStudio starts, read from its registration in build.gradle.kts."""
    text = (root / WORKDIR / "build.gradle.kts").read_text(encoding="utf-8")
    m = re.search(r'register\("integrationTestAndroidStudio"\)\s*\{.*?version\s*=\s*"([^"]+)"', text, re.S)
    if not m:
        sys.exit("build.gradle.kts の integrationTestAndroidStudio に version が見つからない（このスクリプトの読み方を直す）")
    return m.group(1)


def download_entry(version):
    """The releases list's download (link and sha256) of [version] for this machine."""
    suffix = {("Darwin", "arm64"): "-mac_arm.dmg", ("Darwin", "x86_64"): "-mac.dmg"}.get((platform.system(), platform.machine()), "-linux.tar.gz")
    with urllib.request.urlopen(RELEASES_LIST, timeout=60) as r:
        items = json.load(r)["content"]["item"]
    item = next((i for i in items if i.get("version") == version), None)
    if item is None:
        sys.exit(f"releases list に Android Studio {version} が無い: {RELEASES_LIST}")
    entry = next((d for d in item["download"] if d["link"].endswith(suffix)), None)
    if entry is None:
        sys.exit(f"Android Studio {version} に *{suffix} の配布が無い")
    return entry


def sha256_of(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def fetch_android_studio(version, log):
    """Downloads Android Studio [version] into IDE_CACHE (resuming a cut download), checks its sha256, unpacks it once, and
    returns what integrationTestAndroidStudio takes as localPath: the `.app` on macOS, the `android-studio` directory on Linux."""
    target = IDE_CACHE / f"android-studio-{version}"
    done = target / ".complete"
    if done.exists():
        local = Path(done.read_text(encoding="utf-8").strip())
        print(f"      キャッシュの Android Studio {version} を使う: {local}", flush=True)
        return local
    entry = download_entry(version)
    archive = IDE_CACHE / entry["link"].rsplit("/", 1)[1]
    IDE_CACHE.mkdir(parents=True, exist_ok=True)
    for attempt in range(1, 4):
        print(f"      Android Studio {version} を落とす（{attempt} 回目、続きから）: {archive}", flush=True)
        with log.open("a", encoding="utf-8") as out:
            code = subprocess.run(CURL_RESUMABLE + ["-o", str(archive), entry["link"]], stdout=out, stderr=subprocess.STDOUT).returncode
        if code != 0:
            print(f"      curl が {code} で止まった。続きからやり直す", flush=True)
            continue
        actual = sha256_of(archive)
        if entry.get("checksum") and actual != entry["checksum"]:
            print(f"      sha256 が合わない（{actual}）。消して落とし直す", flush=True)
            archive.unlink()
            continue
        break
    else:
        sys.exit(f"Android Studio {version} を落とせなかった。ログ: {log}")

    if target.exists():
        shutil.rmtree(target)
    target.mkdir(parents=True)
    if archive.suffix == ".dmg":
        mount = Path(tempfile.mkdtemp(prefix="katachi-as-"))
        subprocess.run(["hdiutil", "attach", "-nobrowse", "-readonly", "-mountpoint", str(mount), str(archive)], check=True, stdout=subprocess.DEVNULL)
        try:
            app = next(mount.glob("*.app"))
            local = target / app.name
            subprocess.run(["ditto", str(app), str(local)], check=True)
        finally:
            subprocess.run(["hdiutil", "detach", str(mount)], check=False, stdout=subprocess.DEVNULL)
    else:
        subprocess.run(["tar", "xzf", str(archive), "-C", str(target)], check=True)
        local = target / "android-studio"
    done.write_text(str(local), encoding="utf-8")
    archive.unlink()
    return local


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--release-dir", help="結果（ide-plugin-e2e.md）とログ（tmp/ide-plugin-e2e/）を置く .local/release-v<版>")
    ap.add_argument("--list", action="store_true", help="走らせるコマンドを出すだけ")
    ap.add_argument("--android-studio", action="store_true", help="10-5: Android Studio で integrationTestAndroidStudio を走らせる")
    ap.add_argument("--no-ide-cache", action="store_true", help="10-5: キャッシュの Android Studio を使わず落とし直す")
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

    if a.android_studio:
        version = android_studio_version(root)
        if a.no_ide_cache:
            shutil.rmtree(IDE_CACHE / f"android-studio-{version}", ignore_errors=True)
        local = fetch_android_studio(version, log_dir / "download.log")
        cmd = full_cmd(True, local)

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
