#!/usr/bin/env python3
"""Compares two JMH result files (base and head of a pull request) as a Markdown table.

Both runs are expected to come from the same runner, one after the other, so that the
runner's own speed cancels out and the ratio is what the pull request changed. The table
has the time and the allocation per operation (`gc.alloc.rate.norm`) of every benchmark
found in both files, with head / base as a ratio.

A ratio above the threshold is marked, but nothing fails: one pair of runs on a shared
runner is not enough to fail a pull request on. The thresholds follow the nightly job
(time 150%, allocation 120%).

Usage:
    .github/scripts/jmh-compare.py <base results.json> <head results.json> [--time-threshold 1.5] [--alloc-threshold 1.2]

Writes the Markdown to standard output; the workflow appends it to $GITHUB_STEP_SUMMARY.
"""

import argparse
import json
import sys

ALLOC_METRIC = "gc.alloc.rate.norm"


def key_of(entry):
    params = entry.get("params") or {}
    return (entry["benchmark"], entry.get("mode"), tuple(sorted(params.items())))


def sort_key(key):
    """Orders by benchmark, then by parameters numerically where they are numbers (5 before 20)."""
    def value(v):
        try:
            return (0, float(v), "")
        except ValueError:
            return (1, 0.0, v)
    return (key[0], key[1] or "", tuple((k, value(v)) for k, v in key[2]))


def load(path):
    with open(path, encoding="utf-8") as f:
        return {key_of(e): e for e in json.load(f)}


def short_name(benchmark):
    # `me.tbsten.katachi.benchmark.WalkBench.validate` -> `WalkBench.validate`
    return ".".join(benchmark.split(".")[-2:])


def label_of(key):
    params = ", ".join("{}={}".format(k, v) for k, v in key[2])
    return short_name(key[0]) + (" ({})".format(params) if params else "")


def format_number(value):
    if value is None:
        return "-"
    if abs(value) >= 100:
        return "{:,.0f}".format(value)
    return "{:.3g}".format(value)


def format_bytes(value):
    if value is None:
        return "-"
    for unit, size in (("GB", 1e9), ("MB", 1e6), ("kB", 1e3)):
        if value >= size:
            return "{:.3g} {}".format(value / size, unit)
    return "{:.0f} B".format(value)


def ratio_cell(base, head, threshold, worse, better):
    """`head / base`, bold with [worse] above [threshold], with [better] below its inverse."""
    if base is None or head is None or base == 0:
        return "-"
    ratio = head / base
    cell = "{:.2f}x".format(ratio)
    if ratio > threshold:
        return "**{} {}**".format(cell, worse)
    if ratio < 1 / threshold:
        return "{} {}".format(cell, better)
    return cell


def alloc_of(entry):
    metric = entry.get("secondaryMetrics", {}).get(ALLOC_METRIC)
    return None if metric is None else metric.get("score")


def compare(base, head, time_threshold, alloc_threshold):
    rows = []
    regressions = 0
    for key in sorted(set(base) & set(head), key=sort_key):
        b, h = base[key], head[key]
        bt, ht = b["primaryMetric"]["score"], h["primaryMetric"]["score"]
        ba, ha = alloc_of(b), alloc_of(h)
        if bt and ht / bt > time_threshold:
            regressions += 1
        if ba and ha is not None and ha / ba > alloc_threshold:
            regressions += 1
        params = ", ".join("{}={}".format(k, v) for k, v in key[2]) or "-"
        unit = h["primaryMetric"].get("scoreUnit", "")
        rows.append(
            "| {} | {} | {} | {} | {} | {} | {} | {} |".format(
                short_name(key[0]),
                params,
                "{} {}".format(format_number(bt), unit),
                "{} {}".format(format_number(ht), unit),
                ratio_cell(bt, ht, time_threshold, "遅い", "速い"),
                format_bytes(ba),
                format_bytes(ha),
                ratio_cell(ba, ha, alloc_threshold, "増えた", "減った"),
            )
        )
    only_base = sorted(label_of(k) for k in set(base) - set(head))
    only_head = sorted(label_of(k) for k in set(head) - set(base))
    return rows, regressions, only_base, only_head


def render(rows, regressions, only_base, only_head, time_threshold, alloc_threshold):
    lines = ["## JMH: base と head の比較", ""]
    lines.append(
        "同じランナーで base → head の順に測った値です。比は head / base で、"
        "時間は {:.0f}%、alloc/op は {:.0f}% を超えたものを太字にしています。"
        "1組の計測なので、太字でもまず再実行して確かめてください。".format(
            time_threshold * 100, alloc_threshold * 100
        )
    )
    lines.append("")
    if regressions:
        lines.append("**閾値を超えたもの: {} 件**".format(regressions))
    else:
        lines.append("閾値を超えたものはありません。")
    lines.append("")
    if rows:
        lines.append("| ベンチマーク | パラメータ | time (base) | time (head) | time 比 | alloc/op (base) | alloc/op (head) | alloc 比 |")
        lines.append("|---|---|---:|---:|---:|---:|---:|---:|")
        lines.extend(rows)
    else:
        lines.append("base と head の両方にあるベンチマークがありません。")
    if only_base:
        lines += ["", "base にだけあるもの（head で消えた）: " + ", ".join(only_base)]
    if only_head:
        lines += ["", "head にだけあるもの（head で増えた）: " + ", ".join(only_head)]
    return "\n".join(lines) + "\n"


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("base")
    parser.add_argument("head")
    parser.add_argument("--time-threshold", type=float, default=1.5)
    parser.add_argument("--alloc-threshold", type=float, default=1.2)
    args = parser.parse_args(argv[1:])
    rows, regressions, only_base, only_head = compare(
        load(args.base), load(args.head), args.time_threshold, args.alloc_threshold
    )
    sys.stdout.write(
        render(rows, regressions, only_base, only_head, args.time_threshold, args.alloc_threshold)
    )
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
