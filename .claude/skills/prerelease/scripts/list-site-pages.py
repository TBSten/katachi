#!/usr/bin/env python3
"""ビルド済みのドキュメントサイト（docs/dist）から、巡回するページの URL を列挙する。

サイトは base が /katachi なので、`pnpm --dir docs preview` で配信したときの URL を出す。
3つに分けて出す: 英語のページ / 日本語のページ / API リファレンス（api-docs）。
API リファレンスは数百ページあるので、既定では各モジュールのトップと、パッケージの
トップ（index.html）だけを出す（--all で全部）。

使い方（リポジトリの直下で。先に `cd docs && pnpm run build` と `./gradlew generateApiDocs`）:
  python3 .claude/skills/prerelease/scripts/list-site-pages.py                       # 件数の表
  python3 .claude/skills/prerelease/scripts/list-site-pages.py --group ja            # 日本語のページの URL
  python3 .claude/skills/prerelease/scripts/list-site-pages.py --group api-docs --all
  python3 .claude/skills/prerelease/scripts/list-site-pages.py --origin http://localhost:4321
"""
import argparse, sys
from pathlib import Path

sys.dont_write_bytecode = True

DIST = Path("docs/dist")
BASE = "/katachi"
GROUPS = ("en", "ja", "api-docs")


def group_of(rel: Path) -> str:
    first = rel.parts[0] if rel.parts else ""
    if first == "api-docs":
        return "api-docs"
    if first == "ja":
        return "ja"
    return "en"


def is_api_entry(rel: Path) -> bool:
    """API リファレンスのうち、既定で巡回するもの: ルート・モジュール・パッケージの index.html。"""
    return rel.name == "index.html" and len(rel.parts) <= 4


def pages(all_api: bool) -> dict:
    found = {g: [] for g in GROUPS}
    for path in sorted(DIST.rglob("*.html")):
        rel = path.relative_to(DIST)
        if rel.parts[0] in ("_astro", "pagefind") or rel.name in ("404.html", "navigation.html"):
            continue
        group = group_of(rel)
        if group == "api-docs" and not all_api and not is_api_entry(rel):
            continue
        url_path = str(rel).removesuffix("index.html")
        found[group].append(f"{BASE}/{url_path}")
    return found


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--group", choices=GROUPS)
    parser.add_argument("--all", action="store_true", help="API リファレンスの全ページを出す")
    parser.add_argument("--origin", default="http://localhost:4321", help="pnpm preview の配信元")
    args = parser.parse_args()

    if not DIST.is_dir():
        print(f"STOP: {DIST.resolve().as_uri()} が無い。先に `cd docs && pnpm run build` を実行する", file=sys.stderr)
        return 1
    found = pages(args.all)
    if not found["api-docs"]:
        print("WARN: API リファレンスが dist に無い。`./gradlew generateApiDocs` のあとに docs をビルドし直す", file=sys.stderr)

    if args.group:
        for p in found[args.group]:
            print(args.origin + p)
        return 0

    print("| 区分 | ページ数 |")
    print("|---|---|")
    for g in GROUPS:
        print(f"| {g} | {len(found[g])} |")
    print(f"\n配信: cd docs && pnpm preview（{args.origin}{BASE}/）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
