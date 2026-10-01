#!/usr/bin/env python3
"""Collects the characters the headless preview can draw that Inter does not have.

The preview (src/preview) draws Japanese with a subset of Noto Sans JP bundled in
src/preview/resources/fonts/noto-sans-jp, so that it does not depend on the OS fonts. This
script gathers every non-ASCII character of the sources the preview draws text from, and
build_font.sh subsets the font to them.

Sources: the message bundles (\\uXXXX escapes decoded), the Composables and the strings in
src/shared, the presentation layer in src/main, and the preview's own fixtures in src/preview.

Usage (from katachi-intellij-plugin/):
    python3 scripts/preview-font/collect_chars.py > scripts/preview-font/chars.txt

When the preview draws a character the bundled font lacks, verifyPreview fails with the
character's code point; rerun build_font.sh then.
"""

import pathlib
import re
import sys

PLUGIN_DIR = pathlib.Path(__file__).resolve().parents[2]

SOURCES = [
    ("src/main/resources/messages", "*.properties"),
    ("src/shared/kotlin", "**/*.kt"),
    ("src/main/kotlin/me/tbsten/katachi/intellij/presentation", "**/*.kt"),
    ("src/main/kotlin/me/tbsten/katachi/intellij/data", "**/*.kt"),
    ("src/preview/kotlin", "**/*.kt"),
]

UNICODE_ESCAPE = re.compile(r"\\u([0-9a-fA-F]{4})")


def text_of(path: pathlib.Path) -> str:
    text = path.read_text(encoding="utf-8")
    if path.suffix == ".properties":
        # Bundles may be written with \uXXXX escapes (ISO-8859-1 era); decode them.
        text = UNICODE_ESCAPE.sub(lambda m: chr(int(m.group(1), 16)), text)
    return text


def main() -> int:
    chars: set[str] = set()
    for directory, pattern in SOURCES:
        root = PLUGIN_DIR / directory
        if not root.is_dir():
            print(f"collect_chars: {root} is missing; update SOURCES in {__file__}", file=sys.stderr)
            return 1
        for path in sorted(root.glob(pattern)):
            chars.update(c for c in text_of(path) if ord(c) > 0x7E)
    # One character per line, by code point, so that a diff shows what was added or dropped.
    for c in sorted(chars):
        print(c)
    return 0


if __name__ == "__main__":
    sys.exit(main())
