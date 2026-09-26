#!/usr/bin/env python3
"""リリース前チェックの 1（前提チェック）と、作業場所の用意をする。

1. 今回の版（`gradle/libs.versions.toml` の `katachi`）と、公開済みの直前の版を出す。
   直前の版は git のタグ `vX.Y.Z` と Maven Central の maven-metadata.xml の両方から取る。
   Maven Central に届かなければタグだけで判定し、その旨を出す
2. 両者が同じ（または今回のほうが古い）なら `STOP` を出して終了コード 1 で止まる。作業場所は作らない
3. そうでなければ `.local/release-v<版>/` を作り、`prerelease-check-list.md` を写して
   1 の版の欄と、一覧のパスの欄を埋める。**既にあれば上書きしない**（書きかけを消さないため）

使い方（リポジトリの直下で）:
  python3 .claude/skills/prerelease/scripts/prepare.py
  python3 .claude/skills/prerelease/scripts/prepare.py --offline   # Maven Central を見ない
  python3 .claude/skills/prerelease/scripts/prepare.py --force     # 版が同じでも作業場所を作る（利用者が「この版で出す」と言ったとき）
"""
import argparse, re, subprocess, sys, urllib.request
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "check-docs-against-impl" / "scripts"))
import public_api  # noqa: E402

MAVEN_METADATA = "https://repo1.maven.org/maven2/me/tbsten/katachi/katachi/maven-metadata.xml"
TEMPLATE = Path(__file__).resolve().parent.parent / "prerelease-check-list.md"


def current_version(root):
    toml = (root / "gradle" / "libs.versions.toml").read_text(encoding="utf-8")
    m = re.search(r'^katachi\s*=\s*"([^"]+)"', toml, re.M)
    if not m:
        sys.exit("gradle/libs.versions.toml に katachi = \"...\" が見つかりません")
    return m.group(1)


def maven_versions():
    """(公開済みの版のリスト, 失敗の理由)。"""
    try:
        with urllib.request.urlopen(MAVEN_METADATA, timeout=10) as res:
            xml = res.read().decode("utf-8")
    except Exception as e:  # noqa: BLE001 ネットワークが無い・塞がれている、はどれも「取れなかった」
        return None, f"{type(e).__name__}: {e}"
    return re.findall(r"<version>([^<]+)</version>", xml), None


def fill_template(text, version, previous, previous_note, release_dir):
    text = text.replace("release-v0.0.0", release_dir.name)
    text = re.sub(r"(今回のリリースバージョン: )\{TODO\}", lambda m: m.group(1) + version, text, count=1)
    text = re.sub(r"(直前のリリースバージョン: )\{TODO\}",
                  lambda m: m.group(1) + (previous or "（無し）") + previous_note, text, count=1)
    text = text.replace(
        f"{{TODO file://.local/{release_dir.name}/visibility-check.md の絶対パス}}",
        (release_dir / "visibility-check.md").resolve().as_uri(),
    )
    return text


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--offline", action="store_true", help="Maven Central を見ない")
    ap.add_argument("--force", action="store_true", help="版が同じでも作業場所を作る")
    a = ap.parse_args()

    root = public_api.repo_root()
    version = current_version(root)
    tag = public_api.latest_release_tag(root)
    tag_version = tag[1:] if tag else None

    maven, maven_error = (None, "--offline") if a.offline else maven_versions()
    maven_latest = max(maven, key=public_api.parse_version) if maven else None

    print(f"今回の版:            {version}（gradle/libs.versions.toml）")
    print(f"直前のタグ:          {tag or '（v* のタグが無い）'}")
    if maven_latest:
        print(f"Maven Central の最新: {maven_latest}（公開済み: {', '.join(maven)}）")
    else:
        print(f"Maven Central:       取れなかった（{maven_error}）。タグだけで判定する")

    candidates = [v for v in (tag_version, maven_latest) if v]
    previous = max(candidates, key=public_api.parse_version) if candidates else None
    note = ""
    if tag_version and maven_latest and tag_version != maven_latest:
        note = f"（タグ {tag_version} と Maven Central {maven_latest} が食い違う）"
        print(f"WARN: {note[1:-1]}。タグの打ち忘れか、公開の失敗かを確かめる")
    elif not maven_latest:
        note = "（タグだけで判定）"
    stops = []
    if maven and version in maven:
        stops.append(f"{version} は Maven Central に公開済み。同じ版は二度と公開できない")
    if previous is not None and public_api.parse_version(version) <= public_api.parse_version(previous):
        stops.append(f"今回の版 {version} が直前の版 {previous} から上がっていない。版の上げ忘れの可能性が高い")
    for reason in stops:
        print(f"STOP: {reason}")
    if stops and not a.force:
        print("      利用者に「本当にこの版で出すか」を確かめる。版を上げずに続けるなら --force を付けて作り直す")
        return 1

    release_dir = root / ".local" / f"release-v{version}"
    release_dir.mkdir(parents=True, exist_ok=True)
    checklist = release_dir / "prerelease-check-list.md"
    if checklist.exists():
        print(f"作業場所:            {release_dir.as_uri()}（既にある。チェックリストは上書きしなかった）")
    else:
        checklist.write_text(
            fill_template(TEMPLATE.read_text(encoding="utf-8"), version, previous, note, release_dir),
            encoding="utf-8",
        )
        print(f"作業場所:            {release_dir.as_uri()}（新しく作った）")
    print(f"チェックリスト:      {checklist.as_uri()}")
    print(f"基準（直前のタグ）:  {tag or '（無し）'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
