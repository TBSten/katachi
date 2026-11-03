#!/usr/bin/env python3
"""リリース前チェックの 10-4（IDE プラグインの文言の日英）の材料を出す。

IDE プラグインの画面の文言は messages/KatachiBundle.properties（英語）と KatachiBundle_ja.properties（日本語）に
置き、IDE の言語で出し分ける。ツールウィンドウは EnglishKatachiStrings / JapaneseKatachiStrings。英語のまま
直書きされた文言は、日本語の IDE でも英語で出る（v0.3 まではガター・Code Vision などがそうだった）。
鍵の突き合わせ（KatachiBundleParityTest）と日本語の文面の完全一致テストは 8 の `checkIdePlugin` で走るが、
bundle を通っていない直書きはテストでは拾えない。ここで機械的に候補を拾い、判定は subagent に任せる。

出すもの（Markdown。判定はしない）:
- A. Kotlin の直書きの文字列: src/main/kotlin・src/shared/kotlin から、英語の語を含む・または日本語を含む
  文字列リテラル。コメント・ログ・例外・bundle 経由の行と、EnglishKatachiStrings / JapaneseKatachiStrings・
  開発用の ide/debug/ は除く
- B. bundle の鍵: 片方にしか無い鍵と、ja の値が en と同じ鍵（直前の # コメントも添える。意図して英語の
  ままにした鍵は、そこに理由が書いてある）
- C. plugin.xml: bundle を通っていない text= / description= / displayName= と、bundle に
  action.<id>.text が無い <action>
- D. --base を渡したとき: 基準のタグから文言の置き場（messages/・*Strings*.kt）が変わったか。
  変わっていれば、日本語の IDE での目視を TODO.html に載せる材料になる

使い方（リポジトリの直下で）:
  python3 .claude/skills/prerelease/scripts/list-ide-plugin-texts.py
  python3 .claude/skills/prerelease/scripts/list-ide-plugin-texts.py --base v0.3.0 --out .local/release-v<版>/tmp/ide-plugin-texts.md

終了コード: 常に 0（候補が出たかどうかは判定ではない）
"""
import argparse, re, subprocess, sys
from pathlib import Path

sys.dont_write_bytecode = True

PLUGIN = "katachi-intellij-plugin"
SOURCES = ["src/main/kotlin", "src/shared/kotlin"]
BUNDLE_EN = "src/main/resources/messages/KatachiBundle.properties"
BUNDLE_JA = "src/main/resources/messages/KatachiBundle_ja.properties"
PLUGIN_XML = "src/main/resources/META-INF/plugin.xml"

SKIP_FILES = re.compile(r"(EnglishKatachiStrings|JapaneseKatachiStrings)\.kt$|/ide/debug/")
SKIP_LINE = re.compile(
    r"^\s*(\*|/\*|//|import |package |@Suppress|@JvmName)"
    r"|KatachiBundle\.message|\bRegex\(|\b(sdkCall|later|schedule|redrawNow)\(|thisLogger|\bLOG\b|\blogger\b|\.(debug|info|warn)\("
    r"|\bthrow\b|Exception\(|Error\(|\berror\(|\brequire(NotNull)?\(|\bcheck(NotNull)?\("
)
LITERAL = re.compile(r'"((?:[^"\\]|\\.)*)"')
# 語のあいだに空白がある（文）か、大文字で始まる1語（ボタン名など）。${…} の中は語に数えない
WORDS = re.compile(r"[A-Za-z]{3,},? [^\"]*[A-Za-z]{2,}|^[A-Z][a-z]{3,}$")
INTERPOLATION = re.compile(r"\$\{[^}]*\}|\$[A-Za-z_]\w*")
JAPANESE = re.compile(r"[぀-ヿ一-鿿]")
# 画面の文言を組み立てない層。候補はファイルごとの件数に畳んで後ろに回す（パースの失敗の理由・型名がほとんど）
QUIET = re.compile(r"/intellij/(data|model)/")
TOOL_WINDOW_JA = "src/shared/kotlin/me/tbsten/katachi/intellij/presentation/JapaneseKatachiStrings.kt"
MEMBER = re.compile(r"^\s*override (?:val|fun) (\w+)")


def repo_root():
    out = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=True)
    return Path(out.stdout.strip())


def literals(plugin):
    hits = []
    for src in SOURCES:
        for f in sorted((plugin / src).rglob("*.kt")):
            rel = f.relative_to(plugin).as_posix()
            if SKIP_FILES.search(rel):
                continue
            for no, line in enumerate(f.read_text(encoding="utf-8").splitlines(), 1):
                if SKIP_LINE.search(line):
                    continue
                for m in LITERAL.finditer(line):
                    text = m.group(1)
                    bare = INTERPOLATION.sub("", text)
                    if WORDS.search(bare) or JAPANESE.search(bare):
                        hits.append((rel, no, text, "ja" if JAPANESE.search(text) else "en"))
    return hits


def tool_window_untranslated(plugin):
    """JapaneseKatachiStrings のメンバーのうち、値に日本語が無く英語の語があるもの。"""
    found, name, body = [], None, []

    def flush():
        values = [m.group(1) for m in LITERAL.finditer(" ".join(body))]
        if name and values and not any(JAPANESE.search(v) for v in values) \
                and any(re.search(r"[A-Za-z]{3,}", INTERPOLATION.sub("", v)) for v in values):
            found.append((name, " / ".join(values)))

    for line in (plugin / TOOL_WINDOW_JA).read_text(encoding="utf-8").splitlines():
        m = MEMBER.match(line)
        if m or line.strip() == "}":
            flush()
            name, body = (m.group(1), [line]) if m else (None, [])
        elif name:
            body.append(line)
    return found


def read_bundle(path):
    """鍵 → (値, 直前の # コメント)。値の行の継続（末尾の \\）は1行につなぐ。"""
    entries, comment, lines = {}, [], path.read_text(encoding="utf-8").splitlines()
    i = 0
    while i < len(lines):
        line = lines[i]
        if not line.strip():
            comment = []
        elif line.lstrip().startswith(("#", "!")):
            comment.append(line.strip().lstrip("#!").strip())
        elif "=" in line:
            key, value = line.split("=", 1)
            while value.endswith("\\") and i + 1 < len(lines):
                i += 1
                value = value[:-1] + lines[i].strip()
            entries[key.strip()] = (value, " ".join(comment))
            comment = []
        i += 1
    return entries


def bundle_report(plugin):
    en, ja = read_bundle(plugin / BUNDLE_EN), read_bundle(plugin / BUNDLE_JA)
    only_en = sorted(set(en) - set(ja))
    only_ja = sorted(set(ja) - set(en))
    same = [(k, ja[k][0], ja[k][1]) for k in sorted(set(en) & set(ja))
            if ja[k][0] == en[k][0] and re.search(r"[A-Za-z]{2,}", ja[k][0])]
    return en, only_en, only_ja, same


def plugin_xml_report(plugin, en):
    xml = (plugin / PLUGIN_XML).read_text(encoding="utf-8")
    hard = []
    for no, line in enumerate(xml.splitlines(), 1):
        if line.strip().startswith("<!--"):
            continue
        for m in re.finditer(r'\b(text|description|displayName)="([^"]+)"', line):
            hard.append((no, m.group(1), m.group(2), line.strip()))
    actions = re.findall(r'<action\s+id="([^"]+)"', xml)
    missing = [a for a in actions if f"action.{a}.text" not in en]
    return hard, missing


def changed_since(root, base):
    out = subprocess.run(
        ["git", "diff", "--name-only", f"{base}..HEAD", "--", f"{PLUGIN}/src/main/resources/messages",
         f"{PLUGIN}/src/shared/kotlin", f"{PLUGIN}/src/main/kotlin"],
        cwd=root, capture_output=True, text=True)
    if out.returncode != 0:
        return None, out.stderr.strip()
    files = out.stdout.split()
    return [f for f in files if "/messages/" in f or re.search(r"Strings[^/]*\.kt$", f)], None


def table(rows, header):
    out = ["| " + " | ".join(header) + " |", "|" + "---|" * len(header)]
    out += ["| " + " | ".join(str(c).replace("|", "\\|") for c in r) + " |" for r in rows]
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", help="基準のタグ（prepare.py の「基準（直前のタグ）」）。文言の置き場の変更を見る")
    ap.add_argument("--out", help="結果の Markdown を書くファイル。省くと標準出力")
    a = ap.parse_args()

    root = repo_root()
    plugin = root / PLUGIN
    hits = literals(plugin)
    en, only_en, only_ja, same = bundle_report(plugin)
    hard, missing_actions = plugin_xml_report(plugin, en)
    untranslated = tool_window_untranslated(plugin)
    loud = [h for h in hits if not QUIET.search(h[0])]
    quiet = [h for h in hits if QUIET.search(h[0])]

    md = ["# IDE プラグインの文言の日英（10-4 の材料）", "",
          f"- A. Kotlin の直書き: {len(hits)} 件（画面に近い層 {len(loud)} / data・model 層 {len(quiet)}。"
          f"うち日本語 {sum(h[3] == 'ja' for h in hits)}）",
          f"- E. ツールウィンドウの ja に日本語が無いメンバー: {len(untranslated)}",
          f"- B. bundle: en だけの鍵 {len(only_en)} / ja だけの鍵 {len(only_ja)} / ja が en と同じ値 {len(same)}",
          f"- C. plugin.xml: 直書きの属性 {len(hard)} / bundle に text が無い action {len(missing_actions)}", ""]

    md += ["## A. Kotlin の直書きの文字列", "",
           "画面に出るなら bundle（shared は *Strings の interface 経由）に移す候補。識別子・DSL の語・katachi 本体の"
           "本文・ファイル名などは意図した英語。", ""]
    md += table([(f"`{f}:{n}`", lang, f"`{t}`") for f, n, t, lang in loud], ["場所", "言語", "文字列"]) if loud else ["なし"]
    if quiet:
        per_file = {}
        for f, n, t, _ in quiet:
            per_file.setdefault(f, []).append(f"{n}: `{t}`")
        md += ["", f"<details><summary>data・model 層（画面の文言を組み立てない層。{len(per_file)} ファイル {len(quiet)} 件）</summary>", ""]
        for f, items in per_file.items():
            md += [f"- `{f}`（{len(items)} 件）"] + [f"  - {i}" for i in items]
        md += ["", "</details>"]

    md += ["", "## E. ツールウィンドウの文言（JapaneseKatachiStrings）", ""]
    md += table([(f"`{n}`", f"`{v}`") for n, v in untranslated], ["メンバー", "値"]) if untranslated else ["日本語が無いメンバー: なし"]

    md += ["", "## B. bundle の鍵", ""]
    md += [f"- en だけ（英語の IDE では出るが、ja は en にフォールバックする）: {', '.join(only_en) or 'なし'}",
           f"- ja だけ（英語の IDE で鍵が無く落ちる）: {', '.join(only_ja) or 'なし'}", "",
           "ja の値が en と同じ鍵（コメントに理由があれば意図した英語）:", ""]
    md += table([(f"`{k}`", f"`{v}`", c or "（コメントなし）") for k, v, c in same], ["鍵", "値", "直前のコメント"]) if same else ["なし"]

    md += ["", "## C. plugin.xml", ""]
    md += table([(f"`{PLUGIN_XML}:{n}`", attr, f"`{v}`") for n, attr, v, _ in hard], ["場所", "属性", "値"]) if hard else ["直書きの属性: なし"]
    md += ["", f"bundle に `action.<id>.text` が無い action: {', '.join(missing_actions) or 'なし'}"]

    if a.base:
        files, err = changed_since(root, a.base)
        md += ["", f"## D. {a.base} からの文言の置き場の変更", ""]
        if err:
            md += [f"git diff に失敗: {err}"]
        elif files:
            md += ["日本語の IDE での目視を TODO.html に載せる:", ""] + [f"- `{f}`" for f in files]
        else:
            md += ["変更なし（日本語の IDE での目視は要らない）"]

    text = "\n".join(md) + "\n"
    if a.out:
        out = Path(a.out)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(text, encoding="utf-8")
        print(f"書いた: {out}")
    else:
        sys.stdout.write(text)


if __name__ == "__main__":
    main()
