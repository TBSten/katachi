#!/usr/bin/env python3
"""Assembles the pull request comment from the Markdown files `katachiDiff` wrote.

The summary (`diff-report.md`) goes in as it is, except for its last line, which links the two
listings by relative path -- a link that resolves next to the file on disk, not in a comment. The
listings are folded in below it instead, so that one comment carries the whole report: the files
by role under a `<details>`, the files no role allows (a warning the architecture test will also
raise) open. The HTML report is not inlined; the comment points at the run's artifact for it.

A comment body is capped at 65,536 characters by GitHub. When the listings do not fit, they are
left out and the comment says so; the summary table is what a reviewer needs first.

Usage:
    assemble-comment.py <dir with diff-report*.md> --head <sha> --run-url <url> [--marker <text>] [--reproduce <command>]

Writes the comment body to standard output. The marker is the first line, so a later run can find
and remove this comment.
"""

import argparse
import os
import sys

GITHUB_COMMENT_LIMIT = 65536
# Room for the "listings left out" note and GitHub's own overhead.
BODY_BUDGET = GITHUB_COMMENT_LIMIT - 1024

SUMMARY = "diff-report.md"
FILES_BY_ROLE = "diff-report.files-by-role.md"
FILES_IN_NO_ROLE = "diff-report.files-in-no-role.md"


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def read_optional(path):
    return read(path) if os.path.exists(path) else None


def strip_listing_links(summary):
    """Drops the trailing 'GitHub links and `git diff` commands per file: [Files by role](...)' line."""
    lines = summary.rstrip("\n").split("\n")
    if lines and lines[-1].startswith("GitHub links and `git diff` commands per file:"):
        lines = lines[:-1]
    return "\n".join(lines).rstrip("\n") + "\n"


def demote_headings(markdown):
    """`# Files by role: ...` becomes `### ...`, so the listing sits under the summary's heading."""
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
    """Removes the listing's own heading and the lines up to its first `<details`, which repeat the summary."""
    lines = listing.split("\n")
    for index, line in enumerate(lines):
        if line.startswith("<details") or line.startswith("> [!"):
            return "\n".join(lines[index:])
    return listing


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("directory", help="where katachiDiff wrote diff-report.md and its listings")
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
    summary = strip_listing_links(read(summary_path))
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
    by_role = read_optional(os.path.join(args.directory, FILES_BY_ROLE))
    if by_role is not None:
        listings.append(
            "<details><summary><b>Files by role</b> (GitHub links and <code>git diff</code> commands per file)</summary>\n\n"
            + drop_preamble(by_role).rstrip("\n")
            + "\n\n</details>"
        )

    body = "\n".join(head + ["\n".join(listings)] + tail)
    if len(body) > BODY_BUDGET:
        note = [
            "_The per-file listings do not fit in a comment "
            f"({len(body):,} characters, GitHub allows {GITHUB_COMMENT_LIMIT:,}); "
            f"see `diff-report.files-by-role.md` in the [run's artifact]({args.run_url})._",
        ]
        body = "\n".join(head + note + tail)
    sys.stdout.write(body)


if __name__ == "__main__":
    main()
