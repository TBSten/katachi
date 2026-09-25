#!/usr/bin/env python3
"""日本語の原本と英語の訳の組を列挙し、訳し直しが要るものを判定する。

判定:
  missing  英語の訳が無い
  stale    原本のほうが新しい（原本の最終変更が、訳の最終変更より後）
  drift    時刻では追いついているが、見出しの数が原本と違う（最後に同期したときに既にずれていた）
  ok       訳が原本に追いついている

時刻だけで判定すると、原本と訳を同じコミットで触った組は、中身がずれていても ok に見える。
drift はそれを拾うための2つめの目で、Markdown（.md / .mdx）の見出しの数をコードブロックの外だけで数えて比べる。

「最終変更」は、作業ツリーに未コミットの変更があればファイルの更新時刻、無ければ最後のコミットの時刻。
stale のものには、訳を最後に変えたコミット（base）を出す。原本の差分は
`git diff <base> -- <原本>` で読める（作業ツリーの変更も含む）。

使い方（リポジトリの直下で）:
  python3 .claude/skills/translate-ja-en/scripts/list-targets.py          # 要るものだけ
  python3 .claude/skills/translate-ja-en/scripts/list-targets.py --all    # ok も含めて全部
"""
import csv, os, subprocess, sys
from pathlib import Path

PATTERN_FILE = Path(__file__).resolve().parent.parent / "target-file-pattern.csv"
SRC_COLUMN, DST_COLUMN = "対象ファイル", "翻訳先ファイル"


def load_patterns():
    """target-file-pattern.csv を読み、(ディレクトリの組, ファイルの組) を返す。

    1行が1組。列は「対象ファイル」「翻訳先ファイル」（「備考」は読むだけで使わない）。
    ディレクトリの組: (原本のディレクトリ, 訳のディレクトリ, 拡張子の集合)
    ファイルの組:     (原本, 訳)
    """
    dir_pairs, file_pairs = [], []
    with PATTERN_FILE.open(encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        missing = {SRC_COLUMN, DST_COLUMN} - set(reader.fieldnames or [])
        if missing:
            sys.exit(f"{PATTERN_FILE.name}: 見出しの行に {', '.join(sorted(missing))} がありません")
        for n, row in enumerate(reader, 2):
            src, dst = (row.get(SRC_COLUMN) or "").strip(), (row.get(DST_COLUMN) or "").strip()
            if not src and not dst:
                continue
            if not src or not dst:
                sys.exit(f"{PATTERN_FILE.name}:{n}: 対象ファイルと翻訳先ファイルの両方が要ります")
            if ("*" in src) != ("*" in dst):
                sys.exit(f"{PATTERN_FILE.name}:{n}: 片方だけがワイルドカードです")
            if "*" not in src:
                file_pairs.append((src, dst))
                continue
            if "/**/" not in src or "/**/" not in dst:
                sys.exit(f"{PATTERN_FILE.name}:{n}: ディレクトリの組は `<dir>/**/*.<拡張子>` で書きます")
            src_root, src_leaf = src.split("/**/", 1)
            dst_root, dst_leaf = dst.split("/**/", 1)
            exts = extensions(src_leaf)
            if exts != extensions(dst_leaf):
                sys.exit(f"{PATTERN_FILE.name}:{n}: 対象と翻訳先で拡張子が違います")
            dir_pairs.append((src_root, dst_root, exts))
    return dir_pairs, file_pairs


def extensions(leaf):
    """`*.md` から拡張子の集合を取り出す（`*.{md,mdx}` も読めるが、CSV では引用符が要るので1行1拡張子で書く）。"""
    if not leaf.startswith("*."):
        sys.exit(f"{PATTERN_FILE.name}: ファイル名は `*.<拡張子>` で書きます: {leaf}")
    body = leaf[2:]
    if body.startswith("{") and body.endswith("}"):
        return {"." + e.strip() for e in body[1:-1].split(",")}
    return {"." + body}


def git(*args):
    return subprocess.run(["git", *args], capture_output=True, text=True).stdout.strip()


def last_change(path):
    """(時刻, 説明, base コミット)。未コミットの変更があれば更新時刻を使う。"""
    if not os.path.exists(path):
        return None
    dirty = git("status", "--porcelain", "--", path)
    commit = git("log", "-1", "--format=%H %ct", "--", path)
    base = commit.split()[0] if commit else ""
    if dirty or not commit:
        return os.path.getmtime(path), "未コミット", base
    return float(commit.split()[1]), "コミット済み", base


def headings(path):
    """コードブロックの外にある Markdown の見出しの数。.md / .mdx 以外は None。"""
    if not path.endswith((".md", ".mdx")) or not os.path.exists(path):
        return None
    count, fence = 0, False
    for line in open(path, encoding="utf-8"):
        stripped = line.lstrip()
        if stripped.startswith("```") or stripped.startswith("~~~"):
            fence = not fence
        elif not fence and stripped.startswith("#") and stripped[:7].rstrip("#").startswith(" ") is False and stripped.split(" ", 1)[0].strip("#") == "":
            count += 1
    return count


def judge(src, dst, s, d):
    """時刻と見出しの数で1組を判定する。"""
    if d is None:
        return ("missing", "", "訳が無い")
    if s[0] > d[0]:
        return ("stale", d[2], f"原本={s[1]} / 訳={d[1]}")
    hs, hd = headings(src), headings(dst)
    if hs is not None and hs != hd:
        return ("drift", d[2], f"見出しの数が違う（原本 {hs} / 訳 {hd}）")
    return ("ok", "", "")


def main():
    show_all = "--all" in sys.argv
    PAIRS, FILE_PAIRS = load_patterns()
    rows = []
    for src_root, dst_root, exts in PAIRS:
        for src in sorted(Path(src_root).rglob("*")):
            if not src.is_file() or src.suffix not in exts:
                continue
            dst = Path(dst_root) / src.relative_to(src_root)
            s, d = last_change(str(src)), last_change(str(dst))
            status, base, note = judge(str(src), str(dst), s, d)
            if status != "ok" or show_all:
                rows.append((status, str(src), str(dst), base, note))
    for src, dst in FILE_PAIRS:
        s, d = last_change(src), last_change(dst)
        if s is None:
            rows.append(("orphan", src, dst, "", "原本が無い（訳の側にだけある）"))
        else:
            status, base, note = judge(src, dst, s, d)
            if status != "ok" or show_all:
                rows.append((status, src, dst, base, note))
    # 訳の側にしか無いファイル（原本が消えた・原本が無いまま英語で書かれた）
    for src_root, dst_root, exts in PAIRS:
        for dst in sorted(Path(dst_root).rglob("*")):
            if not dst.is_file() or dst.suffix not in exts:
                continue
            if Path(src_root) in dst.parents or dst.parent == Path(src_root):
                continue
            src = Path(src_root) / dst.relative_to(dst_root)
            if not src.exists():
                rows.append(("orphan", str(src), str(dst), "", "原本が無い（訳の側にだけある）"))
    for status, src, dst, base, note in rows:
        print(f"{status:8} {src}\n         -> {dst}" + (f"\n         base={base[:10]}  {note}" if base or note else ""))
    if not rows:
        print("訳し直しが要るものはありません。")
    need = sum(1 for r in rows if r[0] != "ok")
    print(f"\n要るもの: {need} 件")


if __name__ == "__main__":
    main()
