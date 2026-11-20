#!/usr/bin/env python3
"""Gemini（`agy` = Antigravity CLI）で docs の翻訳・日本語チェックをして、結果を検査する。

使えないときは何も書かずに「フォールバック」の合図（終了コード 75）を返す。呼び出し側（Claude）はそれを見て
従来のやり方（Claude 自身・subagent）に切り替える。**原本は日本語のまま。出力は検査してから採用する。**

`gemini`（Gemini CLI）は Google 側のサポート終了で使えない。使うのは `agy` だけ。
`agy` が無い環境（クラウドのコンテナなど）では、いつも終了コード 75 になる。

使い方（リポジトリの直下で）:
  S=.claude/skills/translate-ja-en/scripts/agy.py
  python3 $S probe                                      # 実際に動くか確かめる（短い質問を 1 回投げる）
  python3 $S translate <原本> <訳> --base <base>        # 差分翻訳。訳が無ければ全文を訳す
  python3 $S check-ja <ja の .md/.mdx>...               # 不自然な日本語・表記ゆれ・prh 違反の指摘（直さない）
  python3 $S selftest                                   # 検査の自己テスト（agy は呼ばない）

終了コード: 0 = 成功 / 1 = 出力はできたが検査で落ちた（採用しない） / 75 = agy が使えない（フォールバック）/ 2 = 使い方の誤り

出力は `.local/tmp/agy/` に書く（`--out` で変えられる）。`translate` は `--apply` を付けたときだけ、検査が全部通った訳を
訳のファイルへ書く。付けなければ訳のファイルには触らない。

`--mode plan` は付けない。返事の代わりに計画ファイルを作られて、返事が来ない。
"""
import argparse
import difflib
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

EX_FALLBACK = 75
AGY_NAME = "agy"
AGY_FALLBACK_PATHS = [Path.home() / ".local/bin/agy", Path("/opt/homebrew/bin/agy"), Path("/usr/local/bin/agy")]
JAPANESE = re.compile(r"[ぁ-んァ-ヶ一-龠々ー、。「」（）]")
PLAN_FILE_HINT = re.compile(r"implementation_plan|/brain/|計画ファイル")
# 翻訳してよい属性。これ以外の属性（style・src・class など）は原本にあるものだけを使う。
TRANSLATABLE_ATTRS = {"title", "alt", "aria-label", "label", "description", "placeholder"}
ATTR = re.compile(r'\b([A-Za-z][\w:-]*)="([^"\n]*)"')


class Fallback(Exception):
    """agy が使えない（理由を持つ）。"""


# ---------------------------------------------------------------------------
# agy の呼び出し
# ---------------------------------------------------------------------------

def find_agy():
    found = shutil.which(AGY_NAME)
    if found:
        return found
    for p in AGY_FALLBACK_PATHS:
        if p.exists():
            return str(p)
    raise Fallback("agy コマンドが見つからない（Mac で動かすコマンド。クラウドには入っていない）")


def run_agy(prompt, effort="medium", model=None, timeout=300):
    """agy -p を実行して標準出力を返す。使えなければ Fallback。"""
    cmd = [find_agy(), "-p", prompt, "--output-format", "text", "--effort", effort]
    if model:
        cmd += ["--model", model]
    started = time.time()
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        raise Fallback(f"{timeout} 秒で終わらなかった")
    except OSError as e:
        raise Fallback(f"起動できなかった: {e}")
    took = time.time() - started
    err = (proc.stderr or "").strip()
    out = (proc.stdout or "").strip("\n")
    if proc.returncode != 0:
        raise Fallback(f"終了コード {proc.returncode}（{took:.0f} 秒）: {tail(err or out)}")
    if not out.strip():
        raise Fallback(f"出力が空（{took:.0f} 秒）: {tail(err)}")
    if len(out) < 600 and PLAN_FILE_HINT.search(out):
        raise Fallback(f"返事の代わりに計画ファイルが返った: {tail(out)}")
    log(f"agy 完了: {took:.0f} 秒、{len(out)} 文字")
    return out


def tail(text, n=300):
    text = " ".join(text.split())
    return text if len(text) <= n else "…" + text[-n:]


def log(msg):
    print(msg, file=sys.stderr)


def fallback_exit(e):
    print(f"FALLBACK: {e}", file=sys.stderr)
    print("→ agy は使えない。従来のやり方（Claude 自身・subagent）で進める。", file=sys.stderr)
    return EX_FALLBACK


def strip_wrapper(text):
    """出力全体が ``` で囲まれていたら外す（訳のファイルは --- で始まるので、囲まれていれば余計なもの）。"""
    lines = text.split("\n")
    if len(lines) > 2 and lines[0].startswith("```") and lines[-1].strip() == "```":
        return "\n".join(lines[1:-1])
    return text


# ---------------------------------------------------------------------------
# 検査（agy を呼ばない。selftest で試せる）
# ---------------------------------------------------------------------------

def headings(text):
    """コードブロックの外の Markdown 見出しの数。"""
    count, fence = 0, False
    for line in text.split("\n"):
        s = line.lstrip()
        if s.startswith("```") or s.startswith("~~~"):
            fence = not fence
        elif not fence and re.match(r"#{1,6} ", s):
            count += 1
    return count


def code_blocks(text):
    blocks, cur, fence = [], [], False
    for line in text.split("\n"):
        s = line.lstrip()
        if s.startswith("```") or s.startswith("~~~"):
            if fence:
                blocks.append("\n".join(cur))
                cur = []
            fence = not fence
        elif fence:
            cur.append(line)
    return blocks


def attrs(text):
    """HTML/JSX の `name="value"`。翻訳してよい属性は除く。"""
    return {(n, v) for n, v in ATTR.findall(text) if n not in TRANSLATABLE_ATTRS}


def changed_lines(old, new):
    n = 0
    for tag, i1, i2, j1, j2 in difflib.SequenceMatcher(None, old.split("\n"), new.split("\n"), autojunk=False).get_opcodes():
        if tag != "equal":
            n += max(i2 - i1, j2 - j1)
    return n


def diff_size(diff_text):
    """git diff の追加・削除の行数。"""
    return sum(1 for l in diff_text.split("\n") if l[:1] in "+-" and not l.startswith(("+++", "---")))


def verify_translation(ja, en_old, en_new, diff_text):
    """(ERROR のリスト, WARN のリスト)。en_old が None のときは全文翻訳（範囲の検査はしない）。"""
    errors, warns = [], []
    hj, hn = headings(ja), headings(en_new)
    if hj != hn:
        errors.append(f"見出しの数が原本と違う（原本 {hj} / 訳 {hn}）")
    if "/katachi/ja/" in en_new:
        errors.append("英語の訳に `/katachi/ja/` のリンクが残っている")
    ja_norm = ja.replace("/katachi/ja/", "/katachi/")
    allowed = attrs(ja_norm) | (attrs(en_old) if en_old is not None else set())
    extra = sorted(attrs(en_new) - allowed)
    for n, v in extra:
        errors.append(f'原本にも今の訳にも無い属性が足されている: {n}="{v}"')
    old_lines = set((en_old or "").split("\n"))
    jp = [l.strip() for l in en_new.split("\n") if JAPANESE.search(l) and l not in old_lines]
    if jp:
        warns.append(f"日本語が残っている行が {len(jp)} 行（例: {jp[0][:60]}）")
    if en_old is not None:
        size = changed_lines(en_old, en_new)
        budget = max(10, diff_size(diff_text) * 3)
        if size > budget:
            errors.append(f"今の訳からの変更が {size} 行で、原本の差分（{diff_size(diff_text)} 行）の 3 倍（{budget}）を超える")
        ob, nb = code_blocks(en_old), code_blocks(en_new)
        if len(ob) != len(nb):
            errors.append(f"コードブロックの数が違う（今の訳 {len(ob)} / 新しい訳 {len(nb)}）")
        else:
            changed = [i + 1 for i, (a, b) in enumerate(zip(ob, nb)) if a != b]
            if changed:
                warns.append(f"コードブロック {changed} が今の訳と違う（原本の差分がそこに触れていなければ不正）")
    return errors, warns


# ---------------------------------------------------------------------------
# translate
# ---------------------------------------------------------------------------

TRANSLATE_RULES = """\
- 日本語が原本、英語は訳。節の構成・順序・表の行は原本と 1 対 1。英語側にだけ節や文を足さない
- 原本の `TODO` は訳でも `TODO` のまま。MDX コメント（`{/* TODO ... */}`）の中身は英語に訳す
- コードブロックの中の日本語（コメント・制約名の文字列・説明）も訳す。コマンド、API 名、ファイルパス、コードの識別子は変えない
- katachi が出力する固定の文字列（`## Placement` `## Constraints` `## Examples`、表の見出し `Role | Summary` など）は英語のまま写す
- リンクの `/katachi/ja/` は `/katachi/` に直す
- frontmatter の `title` / `description` も訳す
- HTML・JSX の属性（`style`・`src`・`class` など）は、原本に書いてあるものだけを使う。足さない・そろえない・直さない
- 原本が間違っていそうでも直さずそのまま訳す（気づいた点は訳の外に書かず、無視してよい）"""


def build_translate_prompt(src, ja, en_old, diff_text):
    head = ("あなたは技術文書の翻訳者です。katachi（Kotlin のライブラリ）のドキュメントを、日本語の原本から英語に訳します。\n\n"
            f"## 決まり\n{TRANSLATE_RULES}\n\n")
    if en_old is None:
        return (head + f"## 仕事\n次の日本語原本 `{src}` を、英語の訳として全文訳してください。\n\n"
                "## 出力\n訳のファイルの全文だけを出力する。前置き・説明・全体を囲むコードフェンスは付けない。\n\n"
                f"## 日本語原本\n<<<ORIGINAL\n{ja}\nORIGINAL>>>\n")
    return (head + "## 仕事\n日本語原本が変わった。**変わった部分だけ**を、今の英語訳に反映する。"
            "差分に関係しない行は、今の英語訳を 1 文字も変えない（言い回しの改善・属性のそろえ直しもしない）。\n\n"
            "## 出力\n更新後の英語訳のファイルの全文だけを出力する。前置き・説明・全体を囲むコードフェンスは付けない。\n\n"
            f"## 日本語原本の差分（訳の最後の更新から今まで）\n<<<DIFF\n{diff_text}\nDIFF>>>\n\n"
            f"## 日本語原本（今）\n<<<ORIGINAL\n{ja}\nORIGINAL>>>\n\n"
            f"## 今の英語訳\n<<<CURRENT_EN\n{en_old}\nCURRENT_EN>>>\n")


def git_diff(base, path):
    r = subprocess.run(["git", "diff", base, "--", path], capture_output=True, text=True)
    return r.stdout


def cmd_translate(a):
    src, dst = Path(a.src), Path(a.dst)
    if not src.exists():
        print(f"原本が無い: {src}", file=sys.stderr)
        return 2
    ja = src.read_text(encoding="utf-8")
    en_old = dst.read_text(encoding="utf-8") if dst.exists() else None
    diff_text = ""
    if en_old is not None:
        if not a.base:
            print("訳が既にあるときは --base（訳を最後に変えたコミット。list-targets.py が出す）が要る", file=sys.stderr)
            return 2
        diff_text = git_diff(a.base, str(src))
        if not diff_text.strip():
            print("原本に差分が無い。訳すものは無い。")
            return 0
    prompt = build_translate_prompt(str(src), ja, en_old, diff_text)
    if a.dry_run:
        print(prompt)
        return 0
    try:
        raw = run_agy(prompt, a.effort, a.model, a.timeout)
    except Fallback as e:
        return fallback_exit(e)
    en_new = strip_wrapper(raw)
    if not en_new.endswith("\n") and (en_old is None or en_old.endswith("\n")):
        en_new += "\n"
    out = Path(a.out) if a.out else Path(".local/tmp/agy") / (dst.name + ".agy")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(en_new, encoding="utf-8")
    errors, warns = verify_translation(ja, en_old, en_new, diff_text)
    print(f"出力: {out}")
    for w in warns:
        print(f"WARN: {w}")
    for e in errors:
        print(f"ERROR: {e}")
    if en_old is not None:
        print(f"確認: diff -u {dst} {out}")
    if errors:
        print("検査に落ちたので採用しない（訳のファイルには書いていない）。直すか、従来のやり方で訳す。")
        return 1
    if a.apply:
        dst.parent.mkdir(parents=True, exist_ok=True)
        dst.write_text(en_new, encoding="utf-8")
        print(f"検査を通ったので {dst} に書いた。")
    else:
        print("検査は通った。採用するなら diff を読んでから --apply（または手でコピー）。")
    return 0


# ---------------------------------------------------------------------------
# check-ja
# ---------------------------------------------------------------------------

CHECK_FORMAT = "- L<行番号> | <a/b/c> | 「今の文（原文のまま抜き出す）」 | 「直し案」 | 理由（短く）"


def build_check_prompt(path, text, prh):
    numbered = "\n".join(f"{i}: {l}" for i, l in enumerate(text.split("\n"), 1))
    return ("あなたは日本語の技術文書の校正者です。katachi（Kotlin のライブラリ）のドキュメントの日本語を確かめます。\n\n"
            "## 指摘する種類\n"
            "- a: 不自然・硬い・読みにくい日本語、誤字、変換ミス、余計な半角スペース、「することができます」の冗長さ、1 文に同じ語が重複\n"
            "- b: 表記ゆれ（同じものを別の書き方で書いている）\n"
            "- c: 下の prh.yml の言い換え辞書に反する語\n\n"
            "## 決まり\n"
            "- **ファイルは直さない。指摘だけを出す。**\n"
            "- コードブロック・インラインコードの中は指摘しない\n"
            "- 迷うものは出さない（偽陽性より取りこぼしのほうがよい）\n"
            "- 「今の文」は行の中の原文をそのまま抜き出す（言い換えない）。行番号は下の `行番号: ` の数字\n\n"
            f"## 出力の形（1 指摘 1 行。前置き・まとめは書かない。指摘が無ければ `なし` とだけ書く）\n{CHECK_FORMAT}\n\n"
            f"## prh.yml\n<<<PRH\n{prh}\nPRH>>>\n\n"
            f"## 対象: {path}\n<<<FILE\n{numbered}\nFILE>>>\n")


FINDING = re.compile(r"^\s*-\s*L(\d+)\s*\|\s*([abc])\s*\|\s*「(.*?)」\s*\|\s*「(.*?)」\s*\|\s*(.*)$")


def verify_findings(text, output):
    """指摘の行番号・引用が原本と合っているかを確かめる。(確認済み, 未確認, 読めない行)。"""
    lines = text.split("\n")
    ok, bad, other = [], [], []
    for raw in output.split("\n"):
        if not raw.strip() or raw.strip() == "なし":
            continue
        m = FINDING.match(raw)
        if not m:
            other.append(raw)
            continue
        n, kind, quote, fix, why = int(m.group(1)), m.group(2), m.group(3), m.group(4), m.group(5)
        window = "\n".join(lines[max(0, n - 3):n + 2])
        (ok if quote and quote in window else bad).append((n, kind, quote, fix, why))
    return ok, bad, other


def cmd_check_ja(a):
    prh_path = Path(a.prh)
    prh = prh_path.read_text(encoding="utf-8") if prh_path.exists() else "（prh.yml が見つからない）"
    outdir = Path(a.out) if a.out else Path(".local/tmp/agy/check-ja")
    outdir.mkdir(parents=True, exist_ok=True)
    rc = 0
    for f in a.files:
        path = Path(f)
        if not path.exists():
            print(f"ファイルが無い: {path}", file=sys.stderr)
            return 2
        text = path.read_text(encoding="utf-8")
        prompt = build_check_prompt(str(path), text, prh)
        if a.dry_run:
            print(prompt)
            continue
        try:
            output = run_agy(prompt, a.effort, a.model, a.timeout)
        except Fallback as e:
            return fallback_exit(e)
        ok, bad, other = verify_findings(text, output)
        report = outdir / (path.stem + ".md")
        body = [f"# 日本語チェック（agy）: {path}", "",
                f"- 確認済み（行番号・引用が原本と一致）: {len(ok)} 件 / 未確認（引用が原本に無い）: {len(bad)} 件 / 読めない行: {len(other)} 行",
                "- **直していない。** docs の本文は人が書く。採用するかは人が決める", ""]
        for title, items in (("確認済み", ok), ("未確認（引用が原本に見つからない。採用前に確かめる）", bad)):
            body += [f"## {title}", ""]
            body += [f"- L{n}（{k}）: 「{q}」 → 「{fx}」 — {w}" for n, k, q, fx, w in items] or ["なし"]
            body.append("")
        if other:
            body += ["## 形式が読めなかった行", ""] + [f"- {l}" for l in other] + [""]
        report.write_text("\n".join(body), encoding="utf-8")
        print(f"{path}: 確認済み {len(ok)} / 未確認 {len(bad)} / 読めない {len(other)} → {report}")
    return rc


# ---------------------------------------------------------------------------
# probe / selftest
# ---------------------------------------------------------------------------

def cmd_probe(a):
    try:
        out = run_agy("「ok」とだけ返してください。", a.effort, a.model, a.timeout)
    except Fallback as e:
        return fallback_exit(e)
    print(f"agy は使える（返事: {tail(out, 60)}）")
    return 0


def cmd_selftest(_):
    ja = '---\ntitle: 例\n---\n\n# 見出し\n\n[リンク](/katachi/ja/guides/x/)\n\n<iframe\n  src="a.html"\n  style="width: 100%;"\n/>\n\n<iframe\n  src="b.html"\n  style="width: 100%;"\n/>\n\n```kotlin\n// コメント\nval a = 1\n```\n'
    old = '---\ntitle: Example\n---\n\n# Heading\n\n[link](/katachi/guides/x/)\n\n<iframe\n  src="a.html"\n  style="width: 100%;"\n/>\n\n<iframe\n  src="b.html"\n  style="width: 100%;"\n/>\n\n```kotlin\n// comment\nval a = 1\n```\n'
    diff = "+新しい一文\n-古い一文\n"
    failures = []

    def expect(name, new, want_errors, want_warns=0):
        e, w = verify_translation(ja, old, new, diff)
        if (len(e) > 0) != want_errors or len(w) != want_warns:
            failures.append(f"{name}: errors={e} warns={w}")

    expect("変更なし", old, False)
    expect("属性を勝手に足した（height の件）", old.replace('src="b.html"\n  style="width: 100%;"', 'src="b.html"\n  style="width: 100%; height: 40rem;"'), True)
    expect("ja リンクが残った", old.replace("/katachi/guides/x/", "/katachi/ja/guides/x/"), True)
    expect("見出しが増えた", old + "\n## Extra\n", True)
    expect("コードブロックが変わった", old.replace("val a = 1", "val a = 2"), False, 1)
    expect("title の翻訳は許す", old.replace("title: Example", "title: Sample"), False)
    big = old + "\n".join(f"line {i}" for i in range(40))
    expect("変更が多すぎる", big, True)
    ok, bad, other = verify_findings("一行目\n二行目の誤字です\n", "- L2 | a | 「誤字」 | 「正しい」 | 理由\n- L1 | b | 「存在しない」 | 「x」 | y\n前置き\n")
    if (len(ok), len(bad), len(other)) != (1, 1, 1):
        failures.append(f"指摘の検査: ok={ok} bad={bad} other={other}")
    if failures:
        print("selftest 失敗:\n" + "\n".join(failures))
        return 1
    print("selftest 成功")
    return 0


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)

    def common(sp, timeout):
        sp.add_argument("--effort", default="medium", choices=["low", "medium", "high", "xhigh", "max"])
        sp.add_argument("--model", default=None, help="未指定なら agy の既定")
        sp.add_argument("--timeout", type=int, default=timeout, help="秒")
        sp.add_argument("--dry-run", action="store_true", help="プロンプトを出すだけ（agy は呼ばない）")

    t = sub.add_parser("translate", help="差分翻訳（訳が無ければ全文）")
    t.add_argument("src", help="日本語の原本")
    t.add_argument("dst", help="英語の訳")
    t.add_argument("--base", help="訳を最後に変えたコミット（list-targets.py の base）")
    t.add_argument("--out", help="出力先（既定 .local/tmp/agy/<訳>.agy）")
    t.add_argument("--apply", action="store_true", help="検査が全部通ったときだけ、訳のファイルに書く")
    common(t, 300)
    t.set_defaults(fn=cmd_translate)

    c = sub.add_parser("check-ja", help="日本語の指摘（直さない）")
    c.add_argument("files", nargs="+")
    c.add_argument("--prh", default="docs/prh.yml")
    c.add_argument("--out", help="指摘の置き場（既定 .local/tmp/agy/check-ja）")
    common(c, 600)
    c.set_defaults(fn=cmd_check_ja)

    pr = sub.add_parser("probe", help="agy が実際に動くか確かめる")
    pr.add_argument("--effort", default="low", choices=["low", "medium", "high", "xhigh", "max"])
    pr.add_argument("--model", default=None)
    pr.add_argument("--timeout", type=int, default=120)
    pr.set_defaults(fn=cmd_probe)

    s = sub.add_parser("selftest", help="検査の自己テスト")
    s.set_defaults(fn=cmd_selftest)

    a = p.parse_args()
    sys.exit(a.fn(a))


if __name__ == "__main__":
    main()
