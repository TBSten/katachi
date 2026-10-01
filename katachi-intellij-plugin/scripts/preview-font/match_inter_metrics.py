#!/usr/bin/env python3
"""Gives the preview's Noto Sans JP the vertical metrics of Inter, the font it is drawn next to.

Noto Sans JP's own line box (ascender 1.16 em + descender 0.288 em) is 18.8 px at the preview's
13 px, taller than the 16 px a Jewel button gives its label, so every Japanese button label would
be cut. The OS's Japanese fonts the preview fell back to before were shorter (Hiragino Sans: 1.0 em)
on one macOS and taller on another, which is what made the cut-off gate pass locally and fail on
CI. With Inter's metrics (0.969 + 0.241 em, as Inter-Regular.ttf in standalone Jewel), a Japanese
line is as tall as a Latin one on every machine. Only the line box changes; the glyphs do not.

Usage: match_inter_metrics.py <in.ttf> <out.ttf>
"""

import sys

from fontTools.ttLib import TTFont

# Inter 4 (fonts/inter/Inter-*.ttf of jewel-int-ui-standalone): unitsPerEm 2048, hhea/typo/win
# ascender 1984, descender -494, line gap 0, for every weight.
INTER_UPM = 2048
INTER_ASCENDER = 1984
INTER_DESCENDER = -494


def main(src: str, dst: str) -> int:
    # Keep head.modified as it is, so that rebuilding from the same inputs gives the same bytes.
    font = TTFont(src, recalcTimestamp=False)
    upm = font["head"].unitsPerEm
    ascender = round(INTER_ASCENDER * upm / INTER_UPM)
    descender = round(INTER_DESCENDER * upm / INTER_UPM)

    hhea = font["hhea"]
    hhea.ascent, hhea.descent, hhea.lineGap = ascender, descender, 0
    os2 = font["OS/2"]
    os2.sTypoAscender, os2.sTypoDescender, os2.sTypoLineGap = ascender, descender, 0
    os2.usWinAscent, os2.usWinDescent = ascender, -descender
    # USE_TYPO_METRICS, as Inter sets it: every rasterizer then reads the same numbers.
    os2.fsSelection |= 1 << 7
    # The instancer stamps the build time; the source font's creation time instead, so that
    # rebuilding from the same inputs gives the same bytes.
    font["head"].modified = font["head"].created
    font.save(dst, reorderTables=True)
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr)
        sys.exit(2)
    sys.exit(main(sys.argv[1], sys.argv[2]))
