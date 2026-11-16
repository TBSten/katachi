#!/usr/bin/env python3
"""check-benchmark.py と .github/scripts/check-benchmark-coverage.py のテスト。
リポジトリの直下で: python3 -m unittest discover -s .claude/skills/prerelease/scripts -p 'test_*.py'"""
import importlib.util
import json
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
REPO = HERE.parents[3]


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


C = load("benchmark_coverage", REPO / ".github" / "scripts" / "check-benchmark-coverage.py")
B = load("check_benchmark", HERE / "check-benchmark.py")

HEADER = "| コンポーネント | 入口 | 場所 | ベンチ | 測らない理由 | 確認した版 |\n|---|---|---|---|---|---|\n"
ROWS = textwrap.dedent("""\
    | core/検査 | `validate()` | `katachi` | `WalkBench`・`realProject:nowinandroid` | | v0.4.0 |
    | Gradle plugin | `katachi<Key>` の `a | b` | `plugin` | `gradle-profiler:katachiDocs` | | v0.4.0 |
    | docs | サイト | `docs` | なし | 実行時間に関わらない | 未確認 |
    """)


def repository(tmp, rows=ROWS, labels=("core/検査", "Gradle plugin", "docs"), benches=("WalkBench",)):
    root = Path(tmp)
    for d in ("katachi", "plugin", "docs", ".github", "benchmark/gradle-profiler",
              "benchmark/src/jmh/kotlin", "benchmark/src/realProject/kotlin"):
        (root / d).mkdir(parents=True)
    (root / ".github/labels.json").write_text(json.dumps(
        [{"name": f"影響:{l}"} for l in labels] + [{"name": "規模:小"}], ensure_ascii=False), encoding="utf-8")
    for b in benches:
        (root / f"benchmark/src/jmh/kotlin/{b}.kt").write_text("", encoding="utf-8")
    (root / "benchmark/src/jmh/kotlin/BenchmarkSupport.kt").write_text("", encoding="utf-8")
    (root / "benchmark/src/realProject/kotlin/NowInAndroidArchitecture.kt").write_text("", encoding="utf-8")
    (root / "benchmark/gradle-profiler/sample.scenarios").write_text(
        'default-scenarios = ["katachiDocs"]\n\nkatachiDocs {\n    tasks = [":x"]\n}\n', encoding="utf-8")
    (root / "benchmark/README.md").write_text("# benchmark\n\n" + HEADER + rows + "\nあとの文。\n", encoding="utf-8")
    return root


class CoverageTable(unittest.TestCase):
    def problems(self, **kwargs):
        with tempfile.TemporaryDirectory() as tmp:
            return [m for _, m in C.problems(repository(tmp, **kwargs))]

    def test_matching_table_has_no_problem(self):
        self.assertEqual(self.problems(), [])

    def test_pipe_inside_code_does_not_split_a_cell(self):
        with tempfile.TemporaryDirectory() as tmp:
            rows = C.read_table(repository(tmp))
        self.assertEqual(rows[1]["入口"], "`katachi<Key>` の `a | b`")
        self.assertEqual(C.benches_of(rows[0]), ["WalkBench", "realProject:nowinandroid"])
        self.assertEqual(C.benches_of(rows[2]), [])

    def test_component_without_a_row(self):
        found = self.problems(labels=("core/検査", "Gradle plugin", "docs", "konture"))
        self.assertEqual(len(found), 1)
        self.assertIn("「konture」", found[0])

    def test_row_of_an_unknown_component(self):
        found = self.problems(labels=("core/検査", "Gradle plugin"))
        self.assertEqual(len(found), 1)
        self.assertIn("「docs」", found[0])

    def test_benchmark_not_in_any_row(self):
        found = self.problems(benches=("WalkBench", "NewBench"))
        self.assertEqual(len(found), 1)
        self.assertIn("`NewBench`", found[0])

    def test_row_names_a_benchmark_that_is_gone(self):
        found = self.problems(benches=())
        self.assertEqual(len(found), 1)
        self.assertIn("`WalkBench`", found[0])

    def test_none_needs_a_reason_and_a_version_has_a_form(self):
        rows = ROWS.replace("| 実行時間に関わらない | 未確認 |", "| | 0.4 |")
        found = self.problems(rows=rows)
        self.assertEqual(len(found), 2)
        self.assertTrue(any("測らない理由が空" in m for m in found))
        self.assertTrue(any("確認した版" in m for m in found))

    def test_path_that_does_not_exist(self):
        found = self.problems(rows=ROWS.replace("`docs` | なし", "`site` | なし"))
        self.assertEqual(len(found), 1)
        self.assertIn("`site`", found[0])

    def test_the_real_table_matches_the_repository(self):
        self.assertEqual(C.problems(REPO), [])


class History(unittest.TestCase):
    def test_short_name(self):
        self.assertEqual(B.short_name('me.tbsten.katachi.benchmark.WalkBench.validate ( {"files":"1000","roles":"5"} )'),
                         ("WalkBench", "validate (files=1000, roles=5)"))
        self.assertEqual(B.short_name("me.tbsten.katachi.benchmark.RoleMatchBench.matchEveryFile"),
                         ("RoleMatchBench", "matchEveryFile"))
        self.assertEqual(B.short_name("nowinandroid validate() cold"), (None, "nowinandroid validate() cold"))

    def test_median_with_and_without_the_runner_reference(self):
        runs = [{"benches": [{"name": "a", "value": v}, {"name": "ref", "value": r}]}
                for v, r in [(10, 1), (30, 2), (20, 4)]]
        self.assertEqual(B.median_of(runs, "a"), 20)
        self.assertEqual(B.median_of(runs, "a", "ref"), 10)   # 10/1, 30/2, 20/4 -> 10, 15, 5
        self.assertIsNone(B.median_of(runs, "missing"))


if __name__ == "__main__":
    unittest.main()
