#!/bin/sh
# Builds the Japanese font the headless preview draws with (src/preview/resources/fonts/noto-sans-jp):
# Noto Sans JP from google/fonts, cut to static Regular / SemiBold / Bold and subset to the
# characters collect_chars.py finds. The preview then draws the same pixels on every machine,
# instead of each OS's own Japanese font (whose line height differs between macOS versions).
#
# Needs fontTools (pyftsubset and fonttools on PATH; `pip install fonttools` or
# `uv tool install fonttools`). Run from anywhere:
#     sh katachi-intellij-plugin/scripts/preview-font/build_font.sh
# then run updatePreview, look at the Japanese PNGs, and commit chars.txt, the fonts and the golden.
set -eu

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
PLUGIN_DIR=$(cd "${SCRIPT_DIR}/../.." && pwd)
OUT_DIR="${PLUGIN_DIR}/src/preview/resources/fonts/noto-sans-jp"
# Pinned so that the font, and with it the golden, changes only when this line does.
FONTS_COMMIT=66a36c8c94b1a5d992ee4e7f392fccfe4945767c
BASE_URL="https://raw.githubusercontent.com/google/fonts/${FONTS_COMMIT}/ofl/notosansjp"

WORK_DIR=$(mktemp -d)
trap 'rm -rf "${WORK_DIR}"' EXIT

python3 "${SCRIPT_DIR}/collect_chars.py" > "${SCRIPT_DIR}/chars.txt"

curl -fsSL -o "${WORK_DIR}/NotoSansJP.ttf" "${BASE_URL}/NotoSansJP%5Bwght%5D.ttf"
mkdir -p "${OUT_DIR}"
curl -fsSL -o "${OUT_DIR}/OFL.txt" "${BASE_URL}/OFL.txt"

# The weights the preview's UI uses: Normal, SemiBold (list headers) and Bold (titles).
for pair in Regular:400 SemiBold:600 Bold:700; do
    name=${pair%%:*}
    weight=${pair##*:}
    fonttools varLib.instancer "${WORK_DIR}/NotoSansJP.ttf" "wght=${weight}" --static --update-name-table \
        -o "${WORK_DIR}/NotoSansJP-${name}.ttf"
    # No hinting: Skia draws the preview without it, and it would only make the files larger.
    pyftsubset "${WORK_DIR}/NotoSansJP-${name}.ttf" \
        --text-file="${SCRIPT_DIR}/chars.txt" \
        --no-hinting \
        --output-file="${WORK_DIR}/NotoSansJP-${name}-subset.ttf"
    python3 "${SCRIPT_DIR}/match_inter_metrics.py" \
        "${WORK_DIR}/NotoSansJP-${name}-subset.ttf" "${OUT_DIR}/NotoSansJP-${name}.ttf"
done

ls -l "${OUT_DIR}"
