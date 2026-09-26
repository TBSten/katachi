#!/usr/bin/env python3
"""Turns JMH's allocation per operation into github-action-benchmark's custom format.

github-action-benchmark's `tool: 'jmh'` reads only `primaryMetric` (the time). The allocation
per operation that `-prof gc` records sits in `secondaryMetrics["gc.alloc.rate.norm"]`, which
it ignores. This script lifts that value out into a `customSmallerIsBetter` array, so that the
allocations become a series of their own with a threshold of their own (they move far less
than time between runs, so they can be held to a tighter one).

The names follow the ones the action gives the time series,
`<benchmark> ( {"param":"value"} )`, so the two charts line up entry for entry.

Usage:
    .github/scripts/jmh-alloc-to-custom.py <jmh results.json> <output.json>

Exits non-zero when the input has no benchmark at all, or when no benchmark carries
`gc.alloc.rate.norm` (the gc profiler was not on): an empty series would be pushed silently
otherwise.
"""

import json
import sys

ALLOC_METRIC = "gc.alloc.rate.norm"


def series_name(entry):
    """The name github-action-benchmark gives a JMH entry (src/extract.ts, extractJmhResult)."""
    name = entry["benchmark"]
    params = entry.get("params")
    if params:
        name += " ( " + json.dumps(params, separators=(",", ":")) + " )"
    return name


def convert(results):
    converted = []
    for entry in results:
        alloc = entry.get("secondaryMetrics", {}).get(ALLOC_METRIC)
        if alloc is None:
            continue
        error = alloc.get("scoreError")
        item = {
            "name": series_name(entry),
            "unit": alloc.get("scoreUnit", "B/op"),
            "value": alloc["score"],
        }
        # JMH writes "NaN" when there were too few samples for an error; leave it out then.
        if isinstance(error, (int, float)):
            item["range"] = "± {:.0f}".format(error)
        item["extra"] = "mode: {}\nforks: {}".format(entry.get("mode"), entry.get("forks"))
        converted.append(item)
    return converted


def main(argv):
    if len(argv) != 3:
        print("usage: jmh-alloc-to-custom.py <jmh results.json> <output.json>", file=sys.stderr)
        return 2
    with open(argv[1], encoding="utf-8") as f:
        results = json.load(f)
    if not results:
        print(f"::error::{argv[1]} にベンチマークの結果が1件もありません。", file=sys.stderr)
        return 1
    converted = convert(results)
    if not converted:
        print(
            f"::error::{argv[1]} のどの結果にも {ALLOC_METRIC} がありません。"
            " :benchmark の jmh { profilers } に gc が入っているか確かめてください。",
            file=sys.stderr,
        )
        return 1
    with open(argv[2], "w", encoding="utf-8") as f:
        json.dump(converted, f, indent=2)
        f.write("\n")
    print(f"{len(converted)} 件の {ALLOC_METRIC} を {argv[2]} に書きました。")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
