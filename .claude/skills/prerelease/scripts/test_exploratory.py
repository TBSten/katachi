#!/usr/bin/env python3
"""exploratory.py のテスト。リポジトリの直下で: python3 -m unittest discover -s .claude/skills/prerelease/scripts -p 'test_*.py'"""
import datetime
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import exploratory as E  # noqa: E402


class ExploratoryTest(unittest.TestCase):
    def test_repository_charters_are_well_formed(self):
        # リポジトリに入っているチャーターの書式と ROTATION の整合
        self.assertEqual(E.lint_all(), [])

    def test_rotation_covers_every_cloud_charter(self):
        cloud = {p.stem for p in E.charters() if E.is_cloud(p)}
        self.assertEqual(cloud, set(E.ROTATION))

    def test_week_walks_the_rotation_one_charter_per_week(self):
        start = datetime.date(2026, 11, 16)  # 月曜
        names = [E.charter_for_week(start + datetime.timedelta(weeks=i)) for i in range(len(E.ROTATION))]
        self.assertEqual(sorted(names), sorted(E.ROTATION))  # 一巡で全部が 1 回ずつ
        # 同じ週の中では同じ
        self.assertEqual(E.charter_for_week(start), E.charter_for_week(start + datetime.timedelta(days=6)))

    def test_lint_reports_missing_pieces(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / "x.md"
            p.write_text(textwrap.dedent("""\
                # チャーター: x

                - 利用者像: 誰か
                - 場所: クラウド

                ## 観点
                - 初めて
                ## やってみること（添え書きは許す）
                - なにか
                """), encoding="utf-8")
            problems = E.lint_charter(p)
        self.assertIn("「- 目安:」がありません", problems)
        self.assertIn("「## 入口」がありません", problems)
        self.assertIn("「## 過去に見つかったもの」がありません", problems)
        self.assertEqual(len(problems), 3)


if __name__ == "__main__":
    unittest.main()
