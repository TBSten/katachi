#!/bin/sh
# 本番ビルド（dist）に、dev 専用のフィードバックモードとメモの痕跡が残っていないことを確かめる。
# 使い方: scripts/check-no-dev-tools-in-dist.sh <dist のフォルダ>
# 1 件でも見つかれば、見つけたファイルと行を出して 1 で終わる。
#
# 下の PATTERNS は、統合の定数とずれないようにテストで突き合わせている
# （docs/src/integrations/feedback/test/dist-check.test.ts）。定数を変えたらここも直す。
#   katachi-feedback  APP_ID（core/protocol.ts）。toolbar アプリの id・イベント名の接頭辞
#   katachi-memo      メモの toolbar アプリの id
#   data-fb-src       SRC_ATTR（core/protocol.ts）。本文の要素に付く行番号の属性
#   __katachiFeedbackLog  LOG_GLOBAL（core/console-section.ts）。ログのバッファの window 名
#   modern-screenshot スクショの依存（toolbar アプリの中でだけ動的 import する）
set -eu

PATTERNS='katachi-feedback
katachi-memo
data-fb-src
__katachiFeedbackLog
modern-screenshot'

if [ "$#" -ne 1 ]; then
	echo "usage: $0 <dist dir>" >&2
	exit 2
fi
dist="$1"
if [ ! -d "$dist" ]; then
	echo "check-no-dev-tools-in-dist: $dist is not a directory" >&2
	exit 2
fi
# 空の dist は何も見つからないので通ってしまう。ビルドの失敗を見逃さないよう、html が 1 つはあることを求める。
if [ -z "$(find "$dist" -name '*.html' -print -quit)" ]; then
	echo "check-no-dev-tools-in-dist: $dist has no .html file (was the build run?)" >&2
	exit 2
fi

found=0
# 改行で区切って 1 つずつ見る（パイプにすると found が子シェルに閉じ込められる）。
OLDIFS="$IFS"
IFS='
'
for pat in $PATTERNS; do
	if grep -raFq -- "$pat" "$dist"; then
		echo "FOUND '$pat' in:" >&2
		grep -raFl -- "$pat" "$dist" | head -5 | sed 's/^/  /' >&2
		found=1
	fi
done
IFS="$OLDIFS"
if [ "$found" -ne 0 ]; then
	echo "check-no-dev-tools-in-dist: dev-only code leaked into $dist" >&2
	exit 1
fi
echo "check-no-dev-tools-in-dist: ok ($dist)"
