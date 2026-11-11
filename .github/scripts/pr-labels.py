#!/usr/bin/env python3
"""PR のラベル（.github/labels.json）を GitHub に反映し、PR に規模のラベルを付ける。

    pr-labels.py sync                  labels.json のラベルを作る。あるものは色と説明をそろえる
    pr-labels.py size <PR 番号>         PR の変更行数から「規模:〜」を 1 つ付け、ほかの「規模:〜」を外す
    pr-labels.py count <files.tsv>     （手元の確認用）「行数<TAB>パス」の行から数えた行数と規模を出す

sync と size は gh を使う（GH_TOKEN と GH_REPO または GITHUB_REPOSITORY が要る）。
どのファイルを数えないかと、規模の分け方は CONTRIBUTING.ja.md の「PR のラベル」。
"""

import json
import os
import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import quote

LABELS_FILE = Path(__file__).resolve().parent.parent / "labels.json"
SIZE_PREFIX = "規模:"

# 規模に数えないファイル。レビューで 1 行ずつ読まないもの（テスト・スナップショット・画像・
# lock・英訳・版ごとに凍結した docs の写し）。数えるのは「読んで確かめる必要がある行」。
NOT_COUNTED = [
    # テスト: src/test・src/jvmTest・src/testFixtures など、docs の test/・e2e/、*.test.ts、test_*.py
    r"(^|/)src/[^/]*[Tt]est[^/]*/",
    r"(^|/)(tests?|e2e)/",
    r"\.(test|spec)\.[cm]?[jt]sx?$",
    r"(^|/)test_[^/]*\.py$",
    # スナップショット・golden
    r"(^|/)(snapshots?|__snapshots__|golden)/",
    r"\.snap$",
    # 画像・動画・フォント
    r"\.(png|jpe?g|gif|webp|ico|mp4|mov|webm|woff2?|ttf|otf)$",
    # lock・wrapper
    r"(^|/)(package-lock\.json|yarn\.lock|pnpm-lock\.yaml|[^/]*\.lock)$",
    r"(^|/)gradle/wrapper/",
    # 英訳（日本語が原本で、英語は 1 対 1 の訳）と、版ごとに凍結した docs の写し
    r"^docs/src/content/docs/(ja/)?\d+\.\d+/",
    r"^docs/src/content/docs/(?!ja/)",
    r"^(README|CONTRIBUTING)\.md$",
    r"^skills/katachi/assets/en/",
]
_NOT_COUNTED = re.compile("|".join(f"(?:{p})" for p in NOT_COUNTED))


def counted(path: str) -> bool:
    return _NOT_COUNTED.search(path) is None


def load_labels() -> list[dict]:
    return json.loads(LABELS_FILE.read_text(encoding="utf-8"))


def size_label(lines: int, labels: list[dict]) -> dict:
    sizes = [label for label in labels if label["name"].startswith(SIZE_PREFIX)]
    for label in sizes:
        if "maxLines" in label and lines <= label["maxLines"]:
            return label
    return sizes[-1]


def count_lines(files: list[tuple[int, str]]) -> int:
    return sum(changes for changes, path in files if counted(path))


def gh(*args: str) -> str:
    return subprocess.run(["gh", *args], check=True, capture_output=True, text=True).stdout


def repo() -> str:
    name = os.environ.get("GH_REPO") or os.environ.get("GITHUB_REPOSITORY")
    if not name:
        sys.exit("GH_REPO か GITHUB_REPOSITORY に owner/name を入れてください")
    return name


def ensure_label(label: dict) -> None:
    gh("label", "create", label["name"], "--repo", repo(), "--color", label["color"],
       "--description", label["description"], "--force")


def sync() -> None:
    for label in load_labels():
        ensure_label(label)
        print(f"synced: {label['name']}")


def pr_files(number: str) -> list[tuple[int, str]]:
    out = gh("api", "--paginate", f"repos/{repo()}/pulls/{number}/files",
             "--jq", '.[] | "\\(.changes)\\t\\(.filename)"')
    return parse_tsv(out)


def parse_tsv(text: str) -> list[tuple[int, str]]:
    files = []
    for row in text.splitlines():
        if row.strip():
            changes, path = row.split("\t", 1)
            files.append((int(changes), path))
    return files


def size(number: str) -> None:
    labels = load_labels()
    files = pr_files(number)
    lines = count_lines(files)
    target = size_label(lines, labels)
    issue = f"repos/{repo()}/issues/{number}/labels"
    current = gh("api", issue, "--jq", ".[].name").splitlines()
    stale = [name for name in current if name.startswith(SIZE_PREFIX) and name != target["name"]]
    print(f"PR #{number}: {lines} lines counted out of {sum(c for c, _ in files)} → {target['name']}")
    if target["name"] in current and not stale:
        return
    # gh pr edit ではなく REST を使う（gh pr edit は GITHUB_TOKEN で読めない projects まで読みに行くことがある）
    ensure_label(target)
    if target["name"] not in current:
        gh("api", "-X", "POST", issue, "-f", f"labels[]={target['name']}", "--silent")
    for name in stale:
        gh("api", "-X", "DELETE", f"{issue}/{quote(name, safe='')}", "--silent")


def count(path: str) -> None:
    files = parse_tsv(Path(path).read_text(encoding="utf-8"))
    lines = count_lines(files)
    print(f"{lines} lines counted out of {sum(c for c, _ in files)} → {size_label(lines, load_labels())['name']}")


def main(argv: list[str]) -> None:
    match argv:
        case ["sync"]:
            sync()
        case ["size", number]:
            size(number)
        case ["count", path]:
            count(path)
        case _:
            sys.exit(__doc__)


if __name__ == "__main__":
    main(sys.argv[1:])
