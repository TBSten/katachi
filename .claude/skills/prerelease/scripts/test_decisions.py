#!/usr/bin/env python3
"""decisions.py のテスト。リポジトリの直下で: python3 -m unittest discover -s .claude/skills/prerelease/scripts -p 'test_*.py'"""
import contextlib
import io
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import decisions as D  # noqa: E402

REVIEW = textwrap.dedent("""\
    ---
    title: テスト
    ---

    ## [p2] 指示語「これらは」
    - file: ja/get-started/comparison-with-other-tools.mdx
    - line: 16
    - quote: これらは **katachi と排他的とは限りません**
    - status: resolved
    - note: 直さない: 前の文で並べているので通じる

    - 破っている項目: D3-3（「これら」ではなく指すものを書く）
    - 直し方の案: 「Konsist や ArchUnit などは、…」

    ## [p1] Role に summary が無い（要確認）
    - file: ja/get-started/motivation.mdx
    - line: 66-74
    - quote: .role {
    - status: resolved
    - note: 直さない

    本文。

    ```md
    ## コードフェンスの中の見出しは見出しにしない
    ```

    ## [p1] Role に summary が無い（要確認）
    - file: ja/get-started/motivation.mdx
    - line: 307-327
    - quote: .role {
    - status: resolved
    - note: Feedback に送った: 261110-009

    ## [p3] Role 名が PascalCase でない
    - file: ja/get-started/motivation.mdx
    - line: 70
    - quote: .role {

    本文。
    """)


def run(*argv):
    out = io.StringIO()
    with contextlib.redirect_stdout(out):
        code = D.main(list(argv))
    return code, out.getvalue()


class DecisionsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.dir = self.root / "decisions"
        self.dir.mkdir()
        (self.dir / "README.md").write_text("# 読まない\n\n## 見出しがあっても判断ではない\n", encoding="utf-8")
        self.review = self.root / "prerelease-test.md"
        self.review.write_text(REVIEW, encoding="utf-8")

    def tearDown(self):
        self.tmp.cleanup()

    def test_parse_skips_headings_in_fences(self):
        entries, _ = D.parse(REVIEW, "x", D.REVIEW_KEYS)
        self.assertEqual([e.title for e in entries], ["指示語「これらは」", "Role に summary が無い（要確認）", "Role に summary が無い（要確認）", "Role 名が PascalCase でない"])
        self.assertEqual(entries[0].priority, 2)
        self.assertEqual(entries[0].file, "docs/src/content/docs/ja/get-started/comparison-with-other-tools.mdx")
        self.assertIn("```md", entries[1].body)

    def test_review_ids_match_the_review_cli(self):
        # cli.mjs review list が出した id（prerelease-261109-01-step11 の写しで確かめた値）
        entries, _ = D.parse(REVIEW, "prerelease-261109-01-step11", D.REVIEW_KEYS)
        ids = D.review_ids(entries, "prerelease-261109-01-step11")
        self.assertEqual(ids[0], "prerelease-261109-01-step11/1wgoff5")
        self.assertEqual(ids[1], "prerelease-261109-01-step11/18umevq")
        self.assertEqual(ids[2], "prerelease-261109-01-step11/18umevq-2")

    def test_record_review_copies_only_wont_fix_and_is_idempotent(self):
        code, out = run("--dir", str(self.dir), "record-review", str(self.review), "--decided", "2026-11-10 t KATACHI-1")
        self.assertEqual(code, 0)
        self.assertIn("写した 2 件", out)
        self.assertEqual(out.count("REMOVE "), 2)
        # 後ろから消す順（-2 が付いた id を先に消さないと、先頭を消したときに id がずれる）
        removes = [l.split()[1] for l in out.splitlines() if l.startswith("REMOVE ")]
        self.assertEqual(removes, list(reversed(D.review_ids(D.parse(REVIEW, "prerelease-test", D.REVIEW_KEYS)[0], "prerelease-test")[:2])))
        text = (self.dir / "docs.md").read_text(encoding="utf-8")
        self.assertIn("## 直さない: 指示語「これらは」", text)
        self.assertIn("利用者の一言: 前の文で並べているので通じる", text)
        self.assertIn("- rule: D3-3", text)
        self.assertNotIn("261110-009", text)  # 「直してもらう」は判断ではない
        code, out = run("--dir", str(self.dir), "record-review", str(self.review), "--decided", "2026-11-10 t KATACHI-1")
        self.assertIn("写した 0 件 / 写してあった 2 件", out)
        self.assertEqual((self.dir / "docs.md").read_text(encoding="utf-8"), text)

    def test_match_same_near_and_drop(self):
        run("--dir", str(self.dir), "record-review", str(self.review), "--decided", "2026-11-10 t KATACHI-1")
        out_path = self.root / "kept.md"
        code, out = run("--dir", str(self.dir), "match", str(self.review), "--drop", "--out", str(out_path))
        self.assertEqual(code, 0)
        # 見出しが同じ 3 件は同じ。quote（.role {）だけが重なる別の指摘は外さない
        self.assertEqual(out.count("SAME  "), 3)
        self.assertIn("NEAR  ja/get-started/motivation.mdx:70\tRole 名が PascalCase でない", out)
        kept, _ = D.parse(out_path.read_text(encoding="utf-8"), "kept", D.REVIEW_KEYS)
        self.assertEqual([e.title for e in kept], ["Role 名が PascalCase でない"])
        self.assertTrue(out_path.read_text(encoding="utf-8").startswith("---\ntitle: テスト\n---"))

    def test_quote_overlap_with_similar_title_is_same(self):
        d, _ = D.parse("## 直さない: 指示語「これら」\n- decision: 直さない\n- file: ja/a.mdx\n- quote: これらは katachi と\n- decided: x\n", "d", D.DECISION_KEYS)
        f, _ = D.parse("## [p2] 指示語「これらは」を使っている\n- file: ja/a.mdx\n- quote: `これらは` katachi と排他的\n", "f", D.REVIEW_KEYS)
        self.assertEqual(D.compare(f[0], d[0]), "same")
        other, _ = D.parse("## [p2] 指示語「これらは」\n- file: ja/b.mdx\n- quote: これらは katachi と\n", "f", D.REVIEW_KEYS)
        self.assertIsNone(D.compare(other[0], d[0]))

    def test_glob(self):
        self.assertTrue(D.file_matches("docs/src/content/docs/ja/get-started/*", "docs/src/content/docs/ja/get-started/faq.mdx"))
        self.assertFalse(D.file_matches("docs/src/content/docs/ja/*", "docs/src/content/docs/ja/get-started/faq.mdx"))
        self.assertTrue(D.file_matches("katachi/src/**", "katachi/src/main/kotlin/A.kt"))
        self.assertTrue(D.file_matches(D.normalize_file("ja/get-started/*"), "docs/src/content/docs/ja/get-started/faq.mdx"))

    def test_check_reports_errors_gone_and_lost(self):
        (self.root / "a.md").write_text("一行目\n残っている文\n", encoding="utf-8")
        (self.dir / "docs.md").write_text(textwrap.dedent("""\
            # docs

            ## 直さない: 残っている
            - decision: 直さない
            - file: a.md
            - quote: 残っている文
            - decided: 2026-11-10

            ## 直さない: 書き換わった
            - decision: 直さない
            - file: a.md
            - quote: もう無い文
            - decided: 2026-11-10

            ## 直さない: 消えたファイル
            - decision: 直さない
            - file: b.md
            - decided: 2026-11-10

            ## 後で: 条件が無い
            - decision: 後で
            - decided: 2026-11-10

            ## このまま: 見出しと違う
            - decision: 直さない
            - decided: 2026-11-10
            """), encoding="utf-8")
        code, out = run("--dir", str(self.dir), "check", "--root", str(self.root))
        self.assertEqual(code, 1)
        self.assertNotIn("残っている）", out)
        self.assertIn("LOST  docs.md:9", out)
        self.assertIn("GONE  docs.md:15", out)
        self.assertIn("revisit", out)
        self.assertIn("見出しの「このまま」と decision の「直さない」が違います", out)
        self.assertIn("問題 4 件", out)
        self.assertIn("判断 5 件（直さない 4・後で 1）", out)

    def test_dir_can_follow_the_subcommand(self):
        run("--dir", str(self.dir), "record-review", str(self.review), "--decided", "2026-11-10 t KATACHI-1")
        _, before = run("--dir", str(self.dir), "list")
        _, after = run("list", "--dir", str(self.dir))
        self.assertEqual(before, after)
        self.assertIn("計 2 件（直さない 2）", after)

    def test_repository_decisions_are_well_formed(self):
        # リポジトリに入っている判断そのものの形（無いファイル・消えた quote は check に任せ、ここでは形だけ）
        bad = [(d.source, d.line, e) for d in D.load_decisions() for e in d.errors]
        self.assertEqual(bad, [])


if __name__ == "__main__":
    unittest.main()
