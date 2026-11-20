#!/usr/bin/env python3
"""Assembles the pull request comment from the Markdown files `katachiDiff` wrote.

The summary (`diff-report.md`) goes in as it is, except for its last line, which links the listing
of the files in no role by relative path -- a link that resolves next to the file on disk, not in a
comment. The listing is folded in below it instead, so that one comment carries the whole report:
the files no role allows (a warning the architecture test will also raise), open. The files of each
role are in the summary's table. The HTML report is not inlined; the comment points at the run's
artifact for it.

A comment body is capped at 65,536 characters by GitHub. When the listing does not fit, it is
left out and the comment says so; the summary table is what a reviewer needs first. When the
summary alone does not fit either, it is cut at a line and the comment points at the artifact.

Usage:
    assemble-comment.py <dir with diff-report*.md> --head <sha> --run-url <url> [--marker <text>] [--reproduce <command>]

Writes the comment body to standard output. The marker is the first line, so a later run can find
and remove this comment.
"""

import argparse
import os
import sys

GITHUB_COMMENT_LIMIT = 65536
# Room for the "listing left out" note and GitHub's own overhead.
BODY_BUDGET = GITHUB_COMMENT_LIMIT - 1024

SUMMARY = "diff-report.md"
FILES_IN_NO_ROLE = "diff-report.files-in-no-role.md"


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def read_optional(path):
    return read(path) if os.path.exists(path) else None


def split_listing_links(summary):
    """Splits off the trailing 'GitHub links per file: [Files in no role](...)' line.

    Returns the summary without it, and what the line says the listing holds per file, as HTML.
    A report for a pull request holds the links alone; one made by an older katachi, or for
    another range, holds `git diff` commands as well.
    """
    lines = summary.rstrip("\n").split("\n")
    holds = "GitHub links"
    if lines and lines[-1].startswith("GitHub links") and " per file: " in lines[-1]:
        if "`git diff` commands" in lines[-1]:
            holds = "GitHub links and <code>git diff</code> commands"
        lines = lines[:-1]
    return "\n".join(lines).rstrip("\n") + "\n", holds


def demote_headings(markdown):
    """`# Files in no role: ...` becomes `### ...`, so the listing sits under the summary's heading."""
    out = []
    for line in markdown.split("\n"):
        if line.startswith("# "):
            out.append("### " + line[2:])
        elif line.startswith("## "):
            out.append("#### " + line[3:])
        else:
            out.append(line)
    return "\n".join(out)


def drop_preamble(listing):
    """Removes the listing's own heading and the lines up to its first `<details`, which repeat the summary.

    The summary already carries the no role warning, so the listing's copy of it goes too.
    """
    lines = listing.split("\n")
    for index, line in enumerate(lines):
        if line.startswith("<details"):
            return "\n".join(lines[index:])
    return listing


def fit_summary(summary_lines, budget, run_url):
    """Cuts the summary at a line boundary so that it fits in `budget` characters, with a note on what was cut.

    A large change makes the table itself longer than a comment allows. Posting it whole fails, and the
    earlier report comment would be gone with nothing in its place.
    """
    total = len("\n".join(summary_lines))
    note = (
        f"_The report is longer than a comment allows ({total:,} characters, GitHub allows "
        f"{GITHUB_COMMENT_LIMIT:,}); the rest is in `diff-report.md` in the [run's artifact]({run_url})._"
    )
    kept = []
    size = len(note) + len("</details>") + 4
    for line in summary_lines:
        if size + len(line) + 1 > budget:
            break
        kept.append(line)
        size += len(line) + 1
    # A cut inside the folded tree of unchanged roles would fold the note away with it.
    opened = sum(line.startswith("<details") for line in kept) - sum(line.startswith("</details>") for line in kept)
    if opened > 0:
        kept += ["", "</details>"]
    return kept + ["", note]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("directory", help="where katachiDiff wrote diff-report.md and its listing")
    parser.add_argument("--head", required=True, help="the head commit the report was made for")
    parser.add_argument("--run-url", required=True, help="the workflow run, where the artifact with the HTML report is")
    parser.add_argument("--marker", default="<!-- katachi-diff-report -->", help="first line of the comment")
    parser.add_argument(
        "--reproduce",
        default="./gradlew katachiDiff --arg pr=<number>",
        help="the command a reader runs to make the same report locally, shown in the comment's last line",
    )
    args = parser.parse_args()

    summary_path = os.path.join(args.directory, SUMMARY)
    if not os.path.exists(summary_path):
        sys.exit(f"{summary_path} not found: did katachiDiff run?")
    summary, _ = split_listing_links(read(summary_path))
    summary = summary.replace("# Architecture diff report:", "## 🧩 Architecture diff report:", 1)

    head = [
        args.marker,
        summary.rstrip("\n"),
        "",
    ]
    tail = [
        "",
        f"<sub>katachiDiff on head `{args.head[:7]}` · base = the pull request's base, after = its head · "
        f"HTML report in the [run's artifact]({args.run_url}) · "
        f"`{args.reproduce}` to reproduce</sub>",
        "",
    ]

    listings = []
    no_role = read_optional(os.path.join(args.directory, FILES_IN_NO_ROLE))
    if no_role is not None:
        listings.append("### Files in no role\n\n" + drop_preamble(no_role).rstrip("\n"))

    body = "\n".join(head + ["\n".join(listings)] + tail)
    if len(body) > BODY_BUDGET:
        note = [
            "_The listing of the files in no role does not fit in a comment "
            f"({len(body):,} characters, GitHub allows {GITHUB_COMMENT_LIMIT:,}); "
            f"see `diff-report.files-in-no-role.md` in the [run's artifact]({args.run_url})._",
        ]
        body = "\n".join(head + note + tail)
    if len(body) > BODY_BUDGET:
        budget = BODY_BUDGET - len("\n".join([args.marker] + tail)) - 1
        body = "\n".join([args.marker] + fit_summary(summary.rstrip("\n").split("\n"), budget, args.run_url) + tail)
    sys.stdout.write(body)


if __name__ == "__main__":
    main()
