#!/bin/sh
# リリース前チェックの 9（インストールキットのチェック）。
#
# docs/public/install/katachi-install.sh を、合成の Gradle プロジェクト（fixture。定義は install-kit-fixtures.sh）で
# 実際に動かし、init → scaffold → ./gradlew :architecture-test:test が「配線は正しい」状態になるかを確かめる。
#
#   1. sh -n と /bin/bash -n（macOS の bash 3.2）で katachi-install.sh の構文を確かめる
#   2. リポジトリの katachi を mavenLocal に publish する（fixture は KATACHI_MAVEN_LOCAL=1 でそれを使う）
#   3. fixture ごとに（順番に。Gradle を重ねない）:
#      - KATACHI_DOCS=file://<repo>/docs/public で init --lang <ja|en> --katachi <今回の版> → scaffold
#        （--lang は既定 ja。en を渡すと、出力に日本語が残っていないかも確かめる）
#        （手順書どおり、init が Kotlin の版を検出できなかったときだけ scaffold に --kotlin を渡す）
#      - settings に architecture-test の include が1回だけ、正しい位置（最後の include 文の直後）に入ったか
#      - settings に dependencyResolutionManagement が無ければ、scaffold の警告どおり mavenLocal() と
#        mavenCentral() を足す（fixture に repositories が無いと依存を解決できないため）
#      - ./gradlew :architecture-test:test が「Unexpected だけで落ちる」なら配線は OK
#        （architecture { } が空なので、定義していないファイルが Unexpected になって落ちるのが正常）。
#        構文エラー・プラグインの衝突・解決できない・Unexpected 以外の違反で落ちたら NG。通ってしまっても NG
#      - ルートにファイルを1つ足し、--rerun なしでもう一度 test を走らせる。test タスクが UP-TO-DATE にならずに
#        実行され、足したファイルが Unexpected として出れば OK（入力にファイルの並びが効いている）
#   4. <release-dir>/install-kit.md に結果の表を書く。fixture は <release-dir>/tmp/install-kit/fixtures/、
#      ログは <release-dir>/tmp/install-kit/logs/（作業場所の直下には人が読む成果物だけを置く）
#
# 終了コード: 全部 OK なら 0、1つでも NG なら 1、引数の誤りは 2。
#
# 使い方（リポジトリの直下で。ほかの Gradle のビルドが走っていないことを pgrep -fl GradleWrapperMain で確かめてから）:
#   sh .claude/skills/prerelease/scripts/check-install-kit.sh --release-dir .local/release-v<版>
#   sh .claude/skills/prerelease/scripts/check-install-kit.sh --release-dir .local/release-v<版> --only agp,rootjvm
#   sh .claude/skills/prerelease/scripts/check-install-kit.sh --release-dir .local/release-v<版> --skip-publish
#   sh .claude/skills/prerelease/scripts/check-install-kit.sh --release-dir .local/release-v<版> --lang en --only agp
#
# 決まり: $(...) は使わない（リポジトリの決まり）。$VAR の直後に日本語を置かない（${VAR} で閉じる。bash 3.2 が食う）。
set -u

usage() {
	cat <<'EOF'
使い方: sh check-install-kit.sh --release-dir .local/release-v<版> [--only <fixture>,...] [--skip-publish] [--lang <ja|en>]
  --release-dir   作業場所（結果は <dir>/install-kit.md、fixture とログは <dir>/tmp/install-kit/）
  --only          指定した fixture だけ走らせる（カンマ区切り）
  --skip-publish  publishToMavenLocal を飛ばす（直前に publish 済みのとき）
  --lang          init に渡す言語（既定 ja）。en のときは init / scaffold の出力に日本語が残っていないかも見る
EOF
}

RELEASE_DIR=""
ONLY=""
SKIP_PUBLISH="no"
LANG_ARG="ja"
while [ $# -gt 0 ]; do
	case "$1" in
	--release-dir) RELEASE_DIR="${2:?--release-dir に値がありません}"; shift 2 ;;
	--only) ONLY="${2:?--only に値がありません}"; shift 2 ;;
	--skip-publish) SKIP_PUBLISH="yes"; shift ;;
	--lang) LANG_ARG="${2:?--lang に値がありません}"; shift 2 ;;
	-h | --help) usage; exit 0 ;;
	*) usage >&2; exit 2 ;;
	esac
done
[ -n "${RELEASE_DIR}" ] || { usage >&2; exit 2; }
case "${LANG_ARG}" in ja | en) ;; *) usage >&2; exit 2 ;; esac

REPO=`git rev-parse --show-toplevel` || exit 2
case "${RELEASE_DIR}" in
/*) ;;
*) RELEASE_DIR="${REPO}/${RELEASE_DIR}" ;;
esac
SCRIPTS="${REPO}/.claude/skills/prerelease/scripts"
KIT="${REPO}/docs/public/install/katachi-install.sh"
WORK="${RELEASE_DIR}/tmp/install-kit"
LOGS="${WORK}/logs"
RESULT="${RELEASE_DIR}/install-kit.md"
GRADLE_CACHE="${REPO}/.local/tmp/gradle-cache/prerelease-install"

toml_value() { # $1 = キー, $2 = toml
	sed -n "s/^$1[[:space:]]*=[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p" "$2" | head -1
}
VERSION=`toml_value katachi "${REPO}/gradle/libs.versions.toml"`
K=`toml_value kotlin "${REPO}/gradle/libs.versions.toml"`
AGP=`toml_value agp "${REPO}/sample/android/gradle/sample.versions.toml"`
[ -n "${VERSION}" ] && [ -n "${K}" ] && [ -n "${AGP}" ] || {
	echo "版を読めませんでした（katachi=${VERSION} kotlin=${K} agp=${AGP}）" >&2
	exit 2
}

. "${SCRIPTS}/install-kit-fixtures.sh"
if [ -n "${ONLY}" ]; then
	for fx in `echo "${ONLY}" | tr ',' ' '`; do
		case " ${FIXTURES} " in
		*" ${fx} "*) ;;
		*) echo "未知の fixture: ${fx}（あるのは ${FIXTURES}）" >&2; exit 2 ;;
		esac
	done
	FIXTURES=`echo "${ONLY}" | tr ',' ' '`
fi

rm -rf "${WORK}"
mkdir -p "${LOGS}" "${WORK}/fixtures"
ROWS="${WORK}/rows.md"
: >"${ROWS}"
NG=0
OK_COUNT=0
TOTAL=0

# ---- 1. 構文 -------------------------------------------------------------------------------
SYNTAX="OK"
{
	echo "### sh -n"
	sh -n "${KIT}" 2>&1 || SYNTAX="NG"
	echo "### /bin/bash -n"
	/bin/bash --version | head -1
	/bin/bash -n "${KIT}" 2>&1 || SYNTAX="NG"
} >"${LOGS}/syntax.log" 2>&1
echo "構文: ${SYNTAX}"
[ "${SYNTAX}" = "OK" ] || NG=1

# ---- 2. publish ----------------------------------------------------------------------------
PUBLISH="OK"
if [ "${SKIP_PUBLISH}" = "yes" ]; then
	PUBLISH="飛ばした（--skip-publish）"
else
	echo "publishToMavenLocal ..."
	(cd "${REPO}" && ./gradlew publishToMavenLocal -Pkatachi.skipSigning --no-daemon --console=plain \
		--project-cache-dir .local/tmp/gradle-cache/prerelease-install) >"${LOGS}/publish.log" 2>&1 || PUBLISH="NG"
	echo "publish: ${PUBLISH}"
	[ "${PUBLISH}" = "NG" ] && NG=1
fi

# ---- 3. fixture ごと -----------------------------------------------------------------------

# Gradle のログから、落ちた理由の1行を拾う。
failure_reason() {
	_fr=`grep -E "already on the classpath|already requested|Could not resolve|Cannot resolve|Plugin \[id|Unresolved reference|Expecting|Unexpected tokens|Script compilation error|Katachi check failed" "$1" | head -1`
	if [ -z "${_fr}" ]; then
		_fr=`grep -A1 "What went wrong" "$1" | sed -n 2p`
	fi
	[ -n "${_fr}" ] || _fr="（理由を拾えなかった。ログを見る）"
	# 表を壊さないよう | を置き換える。
	echo "${_fr}" | sed 's/^[[:space:]>]*//; s/|/\\|/g' | cut -c1-160
}

# include の確かめ。結果を INC に入れる（OK / NG: 理由）。
check_include() { # $1 = fixture, $2 = settings のパス
	_n=`grep -c "architecture-test" "$2"`
	_inc=`grep -cE "^[[:space:]]*include[[:space:](].*architecture-test" "$2"`
	if [ "${_n}" != "1" ] || [ "${_inc}" != "1" ]; then
		INC="NG: architecture-test が ${_n} 回（include の行は ${_inc} 回）"
		return
	fi
	_at=`grep -n "architecture-test" "$2" | cut -d: -f1`
	_want=`fx_anchor_$1`
	if [ "${_want}" = "END" ]; then
		_last=`awk 'NF { n = NR } END { print n }' "$2"`
		if [ "${_at}" = "${_last}" ]; then INC="OK（末尾）"; else INC="NG: 末尾に無い（${_at} 行目 / 最終 ${_last} 行目）"; fi
		return
	fi
	_prev=`awk -v n="${_at}" 'NR == n - 1' "$2" | sed 's/^[[:space:]]*//; s/[[:space:]]*$//'`
	if [ "${_prev}" = "${_want}" ]; then
		INC="OK（\`${_want}\` の直後）"
	else
		INC="NG: 直前が \`${_prev}\`（期待: \`${_want}\`）"
	fi
}

run_fixture() { # $1 = fixture
	fx="$1"
	d="${WORK}/fixtures/${fx}"
	setup_log="${LOGS}/${fx}-setup.log"
	t1_log="${LOGS}/${fx}-test1.log"
	t2_log="${LOGS}/${fx}-test2.log"
	INIT="-"; SCAFFOLD="-"; INC="-"; TEST="-"; RERUN="-"; NOTE=""

	mkdir -p "$d"
	fx_wrapper "$d"
	"fx_${fx}" "$d"
	fx_git_init "$d" >/dev/null 2>&1
	settings="$d/settings.gradle.kts"
	[ -f "${settings}" ] || settings="$d/settings.gradle"

	export KATACHI_DOCS="file://${REPO}/docs/public"
	export KATACHI_MAVEN_LOCAL=1
	{
		echo "### init --lang ${LANG_ARG} --katachi ${VERSION}"
		(cd "$d" && sh "${KIT}" init --lang "${LANG_ARG}" --katachi "${VERSION}")
		echo "init exit=$?"
	} >"${setup_log}" 2>&1
	cli=`sed -n 's/^KATACHI_CLI=//p' "${setup_log}" | tail -1`
	if grep -q "^init exit=0" "${setup_log}" && [ -n "${cli}" ] && [ -f "${cli}" ]; then
		INIT="OK"
	else
		INIT="NG"
		return
	fi

	# 手順書どおり、init が Kotlin の版を検出できなかったときだけ --kotlin を渡す。
	kotlin_arg=""
	if grep -q "^KATACHI_KOTLIN=$" "${setup_log}"; then
		kotlin_arg="--kotlin ${K}"
		NOTE="init が Kotlin を検出できず、--kotlin を渡した"
	fi
	{
		echo "### scaffold --package com.example.fx ${kotlin_arg}"
		(cd "$d" && sh "${cli}" scaffold --package com.example.fx ${kotlin_arg})
		echo "scaffold exit=$?"
		echo "### git diff（settings / ルートの build ファイル）"
		(cd "$d" && git diff -- settings.gradle.kts settings.gradle build.gradle.kts build.gradle && git status --short)
	} >>"${setup_log}" 2>&1
	if grep -q "^scaffold exit=0" "${setup_log}"; then SCAFFOLD="OK"; else SCAFFOLD="NG"; return; fi

	# en のときは、init / scaffold の出力（### の見出しと git diff より前の行）に日本語が残っていないか。
	# 見るのはひらがな・カタカナ・漢字・全角の記号。fixture のパスには日本語が無い前提。
	if [ "${LANG_ARG}" = "en" ]; then
		ja_lines=`sed '/^### git diff/,$d' "${setup_log}" | grep -v '^###' | python3 -c 'import re,sys
n=0
for l in sys.stdin:
    if re.search("[\u3000-\u30ff\u4e00-\u9fff\uff00-\uffef]", l):
        n+=1; sys.stderr.write(l)
print(n)' 2>>"${LOGS}/${fx}-ja-lines.log"`
		if [ "${ja_lines}" != "0" ]; then
			SCAFFOLD="NG: en の出力に日本語が ${ja_lines} 行（${LOGS}/${fx}-ja-lines.log）"
			return
		fi
	fi

	check_include "${fx}" "${settings}"

	if ! grep -q "dependencyResolutionManagement" "${settings}"; then
		cat >>"${settings}" <<'EOF'

// check-install-kit.sh: scaffold の警告どおり、依存の解決先を足した（この fixture は repositories を持たないため）
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}
EOF
		NOTE="${NOTE:+${NOTE}、}dependencyResolutionManagement を足した"
	fi

	(cd "$d" && ./gradlew :architecture-test:test --no-daemon --console=plain \
		--project-cache-dir "${GRADLE_CACHE}/${fx}") >"${t1_log}" 2>&1
	if grep -q "> Task :architecture-test:test FAILED" "${t1_log}" &&
		grep -qE "Katachi check failed: [0-9]+ violations? \(Unexpected: [0-9]+\)" "${t1_log}"; then
		TEST="OK（Unexpected だけで落ちた）"
	elif grep -q "BUILD SUCCESSFUL" "${t1_log}"; then
		TEST="NG: 通ってしまった（Unexpected が出ていない）"
		return
	else
		reason=`failure_reason "${t1_log}"`
		TEST="NG: ${reason}"
		return
	fi

	: >"$d/katachi-rerun-probe.txt"
	(cd "$d" && ./gradlew :architecture-test:test --no-daemon --console=plain \
		--project-cache-dir "${GRADLE_CACHE}/${fx}") >"${t2_log}" 2>&1
	task_line=`grep -E "^> Task :architecture-test:test( |$)" "${t2_log}" | tail -1`
	case "${task_line}" in
	*UP-TO-DATE* | *FROM-CACHE* | *SKIPPED* | "")
		RERUN="NG: 実行されなかった（${task_line:-test タスクの行が無い}）"
		;;
	*)
		if grep -q "katachi-rerun-probe.txt" "${t2_log}"; then
			RERUN="OK"
		else
			RERUN="NG: 実行されたが、足したファイルが出ていない"
		fi
		;;
	esac
}

for fx in ${FIXTURES}; do
	TOTAL=`expr "${TOTAL}" + 1`
	started=`date +%s`
	echo "== ${fx} =="
	run_fixture "${fx}"
	elapsed=`date +%s`
	elapsed=`expr "${elapsed}" - "${started}"`
	all="${INIT} ${SCAFFOLD} ${INC} ${TEST} ${RERUN}"
	case "${all}" in
	*NG*) VERDICT="**NG**"; NG=1 ;;
	*) VERDICT="OK"; OK_COUNT=`expr "${OK_COUNT}" + 1` ;;
	esac
	echo "   init=${INIT} scaffold=${SCAFFOLD} include=${INC} test=${TEST} rerun=${RERUN} -> ${VERDICT}（${elapsed}s）"
	desc=`fx_desc_${fx}`
	[ -n "${NOTE}" ] && desc="${desc}<br>（${NOTE}）"
	logs="[setup](file://${LOGS}/${fx}-setup.log)"
	[ -f "${LOGS}/${fx}-test1.log" ] && logs="${logs} / [test1](file://${LOGS}/${fx}-test1.log)"
	[ -f "${LOGS}/${fx}-test2.log" ] && logs="${logs} / [test2](file://${LOGS}/${fx}-test2.log)"
	printf '| %s | %s | %s | %s | %s | %s | %s | %s | %s |\n' \
		"${fx}" "${desc}" "${INIT}" "${SCAFFOLD}" "${INC}" "${TEST}" "${RERUN}" "${VERDICT}" "${logs}" >>"${ROWS}"
done

# ---- 4. 結果 -------------------------------------------------------------------------------
{
	echo "# インストールキットのチェック"
	echo
	echo "- 通った: ${OK_COUNT} / ${TOTAL}"
	echo "- 版: katachi ${VERSION} / Kotlin ${K} / AGP ${AGP}"
	echo "- init の --lang: ${LANG_ARG}"
	echo "- 構文（sh -n・/bin/bash -n）: ${SYNTAX}（[ログ](file://${LOGS}/syntax.log)）"
	echo "- publishToMavenLocal: ${PUBLISH}（[ログ](file://${LOGS}/publish.log)）"
	echo "- スクリプト: \`.claude/skills/prerelease/scripts/check-install-kit.sh\`（fixture の定義は \`install-kit-fixtures.sh\`）"
	echo
	echo "test（配線）は「定義していないファイルが Unexpected として出て落ちる」なら OK。2回目は、ルートにファイルを1つ足して"
	echo "\`--rerun\` なしで走らせ、test タスクが実行されて足したファイルが出れば OK。"
	echo
	echo "| fixture | 形 | init | scaffold | settings の include | test（配線） | 2回目が走ったか | 結果 | ログ |"
	echo "|---|---|---|---|---|---|---|---|---|"
	cat "${ROWS}"
} >"${RESULT}"
rm -f "${ROWS}"

echo "結果: ${OK_COUNT} / ${TOTAL} OK -> file://${RESULT}"
[ "${NG}" = "0" ] || exit 1
exit 0
