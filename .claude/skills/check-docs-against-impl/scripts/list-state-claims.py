#!/usr/bin/env python3
"""check-docs-against-impl の材料: ドキュメントの「状態の主張」の行を候補として並べる。

「まだ使えない」「v0.x で入る予定」「〜だけです」のような文は、書いたときは正しくても実装が進むと嘘になる。
その候補を、確かめる対象のドキュメント（public_api.DOC_GLOBS。日本語の原本だけ）から拾う。

拾う語（行の中のどこかにあれば拾う）: まだ / 予定 / 今後 / 将来 / 現時点 / 現状 / 現在は / いずれ /
  未対応 / 未実装 / 対応していません / サポートしていません / だけです / のみです / しかありません /
  v0.x で / v0.x から / v0.x まで / TODO

**候補であって、食い違いではない。**実装と合っているかは読む側（subagent）が確かめる。
コードブロックの中の行には [code] を付ける（サンプルのコメントの主張も確かめる対象なので、外さない）。

使い方（リポジトリの直下で）:
  python3 .claude/skills/check-docs-against-impl/scripts/list-state-claims.py
  python3 .claude/skills/check-docs-against-impl/scripts/list-state-claims.py --path docs/src/content/docs/ja/guides   # 一部だけ
"""
import argparse, collections, re, sys
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import public_api as api  # noqa: E402

CLAIM = re.compile(
    r"まだ|予定|今後|将来|現時点|現状|現在は|いずれ|未対応|未実装|対応していません|サポートしていません|"
    r"だけです|のみです|しかありません|v\d+\.\d+(?:\.\d+)?\s*(?:で|から|まで|以降)|TODO"
)
FENCE = re.compile(r"^\s*(```|~~~)")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--path", action="append", default=[], help="このパスで始まるファイルだけ（繰り返せる）")
    a = ap.parse_args()

    root = api.repo_root()
    files = [p for p in api.doc_files(root) if not a.path or any(p.startswith(x.rstrip("/")) for x in a.path)]
    per_file = collections.OrderedDict()
    for p in files:
        in_code = False
        for no, line in enumerate(api.read_lines(root, p), 1):
            if FENCE.match(line):
                in_code = not in_code
                continue
            words = sorted(set(CLAIM.findall(line)))
            if words:
                per_file.setdefault(p, []).append((no, in_code, words, line.strip()))

    total = sum(len(v) for v in per_file.values())
    print(f"# 状態の主張の候補（{total} 行 / {len(per_file)} ファイル。対象 {len(files)} ファイル）")
    print()
    for p, hits in per_file.items():
        print(f"## {api.file_uri(root, p)}（{len(hits)} 行）")
        print()
        for no, in_code, words, text in hits:
            code = " [code]" if in_code else ""
            print(f"- {api.file_uri(root, p, no)}{code} 〔{'・'.join(words)}〕 {text[:160]}")
        print()


if __name__ == "__main__":
    main()
