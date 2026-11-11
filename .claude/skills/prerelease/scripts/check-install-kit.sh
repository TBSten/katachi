#!/bin/sh
# リリース前チェックの 9-1（インストールキットのチェック）。
#
# katachi コマンド（katachi-script の CLI。brew で入るものと同じ形）を、合成の Gradle プロジェクト（fixture。定義は
# install-kit-fixtures.sh）で手順書どおりに動かし、作業用ディレクトリ → katachi scaffold → katachi agent test が
# 「配線は正しい」状態になるかを確かめる。
#
#   1. katachi コマンドを組む（build-katachi-cli.sh: publishToMavenLocal と :katachi-script:cliDist）。
#      --cli で渡したときは組まない（publishToMavenLocal は fixture が mavenLocal から katachi を取るのに要るので、
#      --skip-publish を付けない限り回す）
#   2. katachi --version がリポジトリの版か（古い build や brew の別の版を回していないか）を確かめる
#   3. fixture ごとに（順番に。Gradle を重ねない）:
#      - KATACHI_MAVEN_LOCAL=1 で、katachi skill の手順 0-1 / 0-2 どおり katachi doctor → 作業用ディレクトリ
#        （skills/katachi/assets/<ja|en>/ を .local/install-katachi に写して meta を埋める）→ katachi scaffold
#        （--lang は既定 ja。en を渡すと、出力に日本語が残っていないかも確かめる）
#        （手順書どおり、doctor が Kotlin の版を出さなかったときだけ scaffold に --kotlin を渡す）
#        （fx_module_dir_<名前> がある fixture は meta.moduleDir にその置き場所を書き、以降の include の確かめ・
#          test のタスク名・toolchain の突き合わせに使う）
#        （fx_scaffold_args_<名前> があれば scaffold にその引数を足す。fx_expect_<名前> があれば生成物を grep で検査する）
#      - settings に検査用モジュールの include が1回だけ、正しい位置（最後の include 文の直後）に入ったか
#      - settings に dependencyResolutionManagement が無ければ、scaffold の警告どおり mavenLocal() と
#        mavenCentral() を足す（fixture に repositories が無いと依存を解決できないため）
#      - katachi agent test（中で ./gradlew <KATACHI_MODULE_PATH>:test）が「Unexpected だけで落ちる」なら配線は OK
#        （architecture { } が空なので、定義していないファイルが Unexpected になって落ちるのが正常）。
#        構文エラー・プラグインの衝突・解決できない・Unexpected 以外の違反で落ちたら NG。通ってしまっても NG
#      - ルートにファイルを1つ足し、--rerun なしでもう一度 katachi agent test を走らせる。test タスクが UP-TO-DATE に
#        ならずに実行され、足したファイルが Unexpected として出れば OK（入力にファイルの並びが効いている）
#   4. <release-dir>/install-kit.md に結果の表を書く。fixture は <release-dir>/tmp/install-kit/fixtures/、
#      ログは <release-dir>/tmp/install-kit/logs/（作業場所の直下には人が読む成果物だけを置く）
#      --lang en や --only を付けた実行は、install-kit-en.md / install-kit-only-<fixture>.md のように名前に付けて別に残す
#      （ja で全 fixture の結果を上書きしないため）
#
# 終了コード: 全部 OK なら 0、1つでも NG なら 1、引数の誤り・katachi コマンドが無いときは 2。
#
# 使い方（リポジトリの直下で。ほかの Gradle のビルドが走っていないことを pgrep -fl GradleWrapperMain で確かめてから）:
#   sh .claude/skills/prerelease/scripts/check-install-kit.sh --release-dir .local/release-v<版>
#   sh .claude/skills/prerelease/scripts/check-install-kit.sh --release-dir .local/release-v<版> --only agp,rootjvm
#   sh .claude/skills/prerelease/scripts/check-install-kit.sh --release-dir .local/release-v<版> --skip-publish
#   sh .claude/skills/prerelease/scripts/check-install-kit.sh --release-dir .local/release-v<版> --lang en --only agp
#   sh .claude/skills/prerelease/scripts/check-install-kit.sh --release-dir .local/release-v<版> --cli /opt/homebrew/bin/katachi
#
# 決まり: $(...) は使わない（リポジトリの決まり）。$VAR の直後に日本語を置かない（${VAR} で閉じる。bash 3.2 が食う）。
set -u

usage() {
	cat <<'EOF'
使い方: sh check-install-kit.sh --release-dir .local/release-v<版> [--only <fixture>,...] [--skip-publish] [--lang <ja|en>] [--cli <path>]
  --release-dir   作業場所（結果は <dir>/install-kit.md、fixture とログは <dir>/tmp/install-kit/。--lang en / --only の実行は名前に付けて別に残す）
  --only          指定した fixture だけ走らせる（カンマ区切り）
  --skip-publish  katachi コマンドを組まない（publishToMavenLocal と cliDist。直前に build-katachi-cli.sh を回したとき）
  --lang          写すチェックリストとレポートの言語（既定 ja）。en のときは doctor / scaffold の出力に日本語が残っていないかも見る
  --cli           katachi コマンド（既定 katachi-script/build/cli/katachi-cli-<版>/bin/katachi。build-katachi-cli.sh が組む）
EOF
}

RELEASE_DIR=""
ONLY=""
SKIP_PUBLISH="no"
LANG_ARG="ja"
KATACHI=""
while [ $# -gt 0 ]; do
	case "$1" in
	--release-dir) RELEASE_DIR="${2:?--release-dir に値がありません}"; shift 2 ;;
	--only) ONLY="${2:?--only に値がありません}"; shift 2 ;;
	--skip-publish) SKIP_PUBLISH="yes"; shift ;;
	--lang) LANG_ARG="${2:?--lang に値がありません}"; shift 2 ;;
	--cli) KATACHI="${2:?--cli に値がありません}"; shift 2 ;;
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
# 既定（ja で全 fixture）以外の実行は、名前に言語と --only を含める。再実行が全件の結果を上書きしないように。
SUFFIX=""
[ "${LANG_ARG}" = "ja" ] || SUFFIX="${SUFFIX}-${LANG_ARG}"
if [ -n "${ONLY}" ]; then
	ONLY_TAG=`echo "${ONLY}" | tr ',' '-'`
	SUFFIX="${SUFFIX}-only-${ONLY_TAG}"
fi
WORK="${RELEASE_DIR}/tmp/install-kit${SUFFIX}"
LOGS="${WORK}/logs"
RESULT="${RELEASE_DIR}/install-kit${SUFFIX}.md"
GRADLE_CACHE="${REPO}/.local/tmp/gradle-cache/prerelease-install"

toml_value() { # $1 = キー, $2 = toml
	sed -n "s/^$1[[:space:]]*=[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p" "$2" | head -1
}
VERSION=`toml_value katachi "${REPO}/gradle/libs.versions.toml"`
K=`toml_value kotlin "${REPO}/gradle/libs.versions.toml"`
KOTEST=`toml_value kotest "${REPO}/gradle/libs.versions.toml"`
AGP=`toml_value agp "${REPO}/sample/android/gradle/sample.versions.toml"`
[ -n "${VERSION}" ] && [ -n "${K}" ] && [ -n "${KOTEST}" ] && [ -n "${AGP}" ] || {
	echo "版を読めませんでした（katachi=${VERSION} kotlin=${K} kotest=${KOTEST} agp=${AGP}）" >&2
	exit 2
}

# katachi コマンド。--cli が無ければ build-katachi-cli.sh が組む場所（cliDist の出力）。
CLI_GIVEN="yes"
if [ -z "${KATACHI}" ]; then
	CLI_GIVEN="no"
	KATACHI="${REPO}/katachi-script/build/cli/katachi-cli-${VERSION}/bin/katachi"
fi
case "${KATACHI}" in /*) ;; */*) KATACHI="`pwd`/${KATACHI}" ;; esac

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

# ---- 1. katachi コマンドを組む（publishToMavenLocal と cliDist）-------------------------------
PUBLISH="OK"
if [ "${SKIP_PUBLISH}" = "yes" ]; then
	PUBLISH="飛ばした（--skip-publish）"
else
	echo "katachi コマンドを組む（publishToMavenLocal・:katachi-script:cliDist）..."
	sh "${SCRIPTS}/build-katachi-cli.sh" >"${LOGS}/publish.log" 2>&1 || PUBLISH="NG"
	echo "build: ${PUBLISH}"
	[ "${PUBLISH}" = "NG" ] && NG=1
fi

# ---- 2. katachi --version ------------------------------------------------------------------
CLI_CHECK="OK"
{
	echo "### ${KATACHI} --version"
	"${KATACHI}" --version
	echo "exit=$?"
} >"${LOGS}/cli-version.log" 2>&1
if ! grep -q "^exit=0" "${LOGS}/cli-version.log"; then
	CLI_CHECK="NG: 動かない"
elif ! grep -v '^###' "${LOGS}/cli-version.log" | grep -qF "${VERSION}"; then
	CLI_CHECK="NG: 版が ${VERSION} ではない"
fi
echo "katachi コマンド: ${CLI_CHECK}（${KATACHI}）"
case "${CLI_CHECK}" in
NG*)
	NG=1
	if [ "${CLI_GIVEN}" = "no" ] && [ "${SKIP_PUBLISH}" = "yes" ]; then
		echo "  --skip-publish を外すか、先に sh .claude/skills/prerelease/scripts/build-katachi-cli.sh を回してください" >&2
	fi
	;;
esac

# ---- 3. fixture ごと -----------------------------------------------------------------------

# JDK 21 の置き場所を出す（fx_toolchain_<名前> を持つ fixture は「Gradle を JDK 21 で動かす」前提。JAVA_HOME が 17 を
# 指す環境でも回せるように、見つけたものを JAVA_HOME に入れる）。見つからなければ 1。
find_jdk21() {
	for _c in "${JAVA_HOME:-}" "`/usr/libexec/java_home -v 21 2>/dev/null`" \
		/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home /opt/homebrew/opt/openjdk@21 \
		"${HOME}"/.gradle/jdks/*21*/Contents/Home "${HOME}"/.gradle/jdks/*21* \
		"${HOME}"/Library/Java/JavaVirtualMachines/*21*/Contents/Home /Library/Java/JavaVirtualMachines/*21*/Contents/Home \
		/usr/lib/jvm/*21*; do
		[ -n "${_c}" ] && [ -x "${_c}/bin/java" ] || continue
		_v=`"${_c}/bin/java" -version 2>&1 | grep 'version "' | head -1 | sed -n 's/.*version "\([0-9]*\).*/\1/p'`
		if [ "${_v}" = "21" ]; then
			echo "${_c}"
			return 0
		fi
	done
	return 1
}

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
# include の行は Gradle パスの形（include(":static-analysis:katachi") / include ':architecture-test'）。
# 1 階層では `architecture-test` と `:architecture-test` の両方を数える（コロン無しの既存 include も include 済み扱い）。
check_include() { # $1 = fixture, $2 = settings のパス
	_mp=`echo "${MODULE_PATH}" | sed 's/^://'`
	_pat="[\"']:?${_mp}[\"']"
	_n=`grep -cE "${_pat}" "$2"`
	_inc=`grep -cE "^[[:space:]]*include[[:space:](].*${_pat}" "$2"`
	if [ "${_n}" != "1" ] || [ "${_inc}" != "1" ]; then
		INC="NG: ${MODULE_PATH} が ${_n} 回（include の行は ${_inc} 回）"
		return
	fi
	_at=`grep -nE "${_pat}" "$2" | cut -d: -f1`
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
	t1_gradle="${LOGS}/${fx}-test1-gradle.log"
	t2_gradle="${LOGS}/${fx}-test2-gradle.log"
	INIT="-"; SCAFFOLD="-"; INC="-"; TEST="-"; RERUN="-"; NOTE=""
	MODULE_DIR="architecture-test"; MODULE_PATH=":architecture-test"

	mkdir -p "$d"
	fx_wrapper "$d"
	"fx_${fx}" "$d"
	fx_git_init "$d" >/dev/null 2>&1
	settings="$d/settings.gradle.kts"
	[ -f "${settings}" ] || settings="$d/settings.gradle"

	# fx_module_dir_<名前> を持つ fixture は、検査用モジュールの置き場所をチェックリストの meta.moduleDir に書く。
	MODULE_DIR="architecture-test"
	if command -v "fx_module_dir_${fx}" >/dev/null 2>&1; then
		MODULE_DIR=`fx_module_dir_${fx}`
	fi
	MODULE_PATH=":`printf '%s' "${MODULE_DIR}" | tr '/' ':'`"
	NOTE="module dir ${MODULE_DIR}"

	export KATACHI_MAVEN_LOCAL=1
	# katachi skill の手順 0-1 / 0-2 どおり: doctor を回し、skill のテンプレートを作業用ディレクトリに写して meta を埋める。
	workdir="$d/.local/install-katachi"
	{
		echo "### katachi doctor --lang ${LANG_ARG}"
		(cd "$d" && "${KATACHI}" doctor --lang "${LANG_ARG}")
		echo "doctor exit=$?"
	} >"${setup_log}" 2>&1
	kotlin=`sed -n 's/^.*Kotlin: \([0-9][^ ]*\).*$/\1/p' "${setup_log}" | head -1`
	{
		echo "### 作業用ディレクトリ（skills/katachi/assets/${LANG_ARG}/ を写す）"
		mkdir -p "${workdir}/tmp" &&
			cp "${REPO}/skills/katachi/assets/${LANG_ARG}/check-list.html" "${workdir}/check-list.html" &&
			python3 - "${workdir}/check-list.html" "$d" "${MODULE_DIR}" "${VERSION}" "${kotlin}" <<'PY'
import json, sys
path, root, module_dir, version, kotlin = sys.argv[1:]
lines = open(path, encoding="utf-8").read().split("\n")
start = lines.index('<script type="application/json" id="checklist">')
end = lines.index("</script>", start)
d = json.loads("\n".join(lines[start + 1:end]))
d["meta"].update(workdir=".local/install-katachi", projectRoot=root, moduleDir=module_dir, version=version, kotlin=kotlin or None, git="yes")
lines[start + 1:end] = json.dumps(d, ensure_ascii=False, indent=2).split("\n")
open(path, "w", encoding="utf-8").write("\n".join(lines))
PY
		echo "workdir exit=$?"
	} >>"${setup_log}" 2>&1
	if grep -q "^doctor exit=0" "${setup_log}" && grep -q "^workdir exit=0" "${setup_log}"; then
		INIT="OK"
	else
		INIT="NG"
		return
	fi

	# 手順書どおり、doctor が Kotlin の版を出さなかったときだけ --kotlin を渡す。
	kotlin_arg=""
	if [ -z "${kotlin}" ]; then
		kotlin_arg="--kotlin ${K}"
		NOTE="${NOTE}、doctor が Kotlin を検出できず --kotlin を渡した"
	fi
	# fx_scaffold_args_<名前> を持つ fixture は、scaffold にその引数（--test-framework kotest など）を足す。
	scaffold_args=""
	if command -v "fx_scaffold_args_${fx}" >/dev/null 2>&1; then
		scaffold_args=`fx_scaffold_args_${fx}`
	fi
	{
		echo "### katachi scaffold --package com.example.fx ${kotlin_arg} ${scaffold_args}"
		(cd "$d" && "${KATACHI}" scaffold --package com.example.fx ${kotlin_arg} ${scaffold_args})
		echo "scaffold exit=$?"
		echo "### git diff（settings / ルートの build ファイル / version catalog）"
		(cd "$d" && git diff -- settings.gradle.kts settings.gradle build.gradle.kts build.gradle gradle/libs.versions.toml && git status --short)
	} >>"${setup_log}" 2>&1
	if grep -q "^scaffold exit=0" "${setup_log}"; then SCAFFOLD="OK"; else SCAFFOLD="NG"; return; fi
	# scaffold が出す「テスト: junit5（検出: ...）」/「Test framework: kotest (...)」を NOTE に写す。
	tf=`sed -En 's/^.*(テスト:|Test framework:)[[:space:]]*([a-z0-9]*).*/\2/p' "${setup_log}" | head -1`
	[ -n "${tf}" ] && NOTE="${NOTE}、テスト ${tf}"

	# fx_expect_<名前> を持つ fixture は、生成物の grep 検査をする（NG の理由を 1 行ずつ標準出力に出す。空なら OK）。
	if command -v "fx_expect_${fx}" >/dev/null 2>&1; then
		expect_ng=`"fx_expect_${fx}" "$d" "${KATACHI}" 2>>"${setup_log}" | tr '\n' '/' | sed 's/\/$//'`
		if [ -n "${expect_ng}" ]; then
			SCAFFOLD="NG: ${expect_ng}"
			return
		fi
		NOTE="${NOTE}、生成物の検査 OK"
	fi

	# en のときは、doctor / scaffold の出力（### の見出しと git diff より前の行）に日本語が残っていないか。
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

	# fx_toolchain_<名前> を持つ fixture は、生成された build.gradle.kts の jvmToolchain が期待どおりかも見る。
	if command -v "fx_toolchain_${fx}" >/dev/null 2>&1; then
		want_tc=`fx_toolchain_${fx}`
		got_tc=`sed -n 's/^[[:space:]]*jvmToolchain(\([0-9]*\)).*/\1/p' "$d/${MODULE_DIR}/build.gradle.kts" | head -1`
		[ -n "${got_tc}" ] || got_tc="none"
		if [ "${got_tc}" != "${want_tc}" ]; then
			SCAFFOLD="NG: jvmToolchain が ${got_tc}（期待: ${want_tc}）"
			return
		fi
		NOTE="${NOTE:+${NOTE}、}jvmToolchain は ${got_tc}（期待どおり）"
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

	if [ -n "${JDK21_MISSING:-}" ] && [ "${want_tc:-none}" != "none" ]; then
		TEST="飛ばした（JDK 21 が無い。この fixture は Gradle を JDK 21 で動かす前提）"
		return
	fi
	# JAVA_HOME の名前（.../temurin-21/Contents/Home なら temurin-21）を形の欄に残す。
	if [ -n "${JAVA_HOME:-}" ]; then
		jh_name=`echo "${JAVA_HOME}" | sed 's#/Contents/Home$##; s#/libexec/openjdk.jdk$##; s#.*/##'`
		NOTE="${NOTE:+${NOTE}、}JAVA_HOME ${jh_name}"
	fi
	# 手順書どおり katachi agent test で回す（中で ./gradlew <module>:test --console=plain -Dkatachi.maxViolations=all）。
	# katachi agent test の要約は t1_log、Gradle の出力の全文（作業用ディレクトリの tmp/test/latest.log）は t1_gradle に写す。
	run_agent_test "${t1_log}" "${t1_gradle}"
	if [ "${AT_EXIT}" = "1" ] && grep -q "> Task ${MODULE_PATH}:test FAILED" "${t1_gradle}" &&
		grep -qE "Katachi check failed: [0-9]+ violations? \(Unexpected: [0-9]+\)" "${t1_gradle}"; then
		TEST="OK（Unexpected だけで落ちた）"
	elif [ "${AT_EXIT}" = "0" ]; then
		TEST="NG: 通ってしまった（Unexpected が出ていない）"
		return
	else
		reason=`failure_reason "${t1_gradle}"`
		TEST="NG: ${reason}（katachi agent test の終了コード ${AT_EXIT}）"
		return
	fi

	: >"$d/katachi-rerun-probe.txt"
	run_agent_test "${t2_log}" "${t2_gradle}"
	task_line=`grep -E "^> Task ${MODULE_PATH}:test( |$)" "${t2_gradle}" | tail -1`
	case "${task_line}" in
	*UP-TO-DATE* | *FROM-CACHE* | *SKIPPED* | "")
		RERUN="NG: 実行されなかった（${task_line:-test タスクの行が無い}）"
		;;
	*)
		if grep -q "katachi-rerun-probe.txt" "${t2_gradle}"; then
			RERUN="OK"
		else
			RERUN="NG: 実行されたが、足したファイルが出ていない"
		fi
		;;
	esac
}

# katachi agent test を fixture の中で回す。$1 = 要約の写し先、$2 = Gradle の出力の写し先。終了コードは AT_EXIT に入れる。
run_agent_test() {
	rm -f "${workdir}/tmp/test/latest.log"
	(cd "$d" && "${KATACHI}" agent test -- --no-daemon --project-cache-dir "${GRADLE_CACHE}/${fx}") >"$1" 2>&1
	AT_EXIT=$?
	if [ -f "${workdir}/tmp/test/latest.log" ]; then
		cp "${workdir}/tmp/test/latest.log" "$2"
	else
		: >"$2"
	fi
}

for fx in ${FIXTURES}; do
	TOTAL=`expr "${TOTAL}" + 1`
	started=`date +%s`
	echo "== ${fx} =="
	# fx_toolchain_<名前> を持つ fixture は「JDK 21 だけ」の環境を再現する。~/.gradle/jdks に自動取得済みの JDK 17 があると
	# auto-detect=false でも見つかってしまうので、GRADLE_USER_HOME を別に作る（wrapper と caches だけ本物へのリンク）。
	# あわせて Gradle を JDK 21 で動かす（JAVA_HOME が別の版を指していても）。JDK 21 が無ければ、期待が none 以外の fixture は
	# test を飛ばす（NG ではなく「飛ばした」。fixture の前提が満たせないだけで、kit の不具合ではない）。
	JDK21_MISSING=""
	if command -v "fx_toolchain_${fx}" >/dev/null 2>&1; then
		ISO_HOME="${WORK}/gradle-user-home"
		rm -rf "${ISO_HOME}"
		mkdir -p "${ISO_HOME}"
		REAL_GUH="${GRADLE_USER_HOME:-${HOME}/.gradle}"
		ln -s "${REAL_GUH}/wrapper" "${ISO_HOME}/wrapper"
		ln -s "${REAL_GUH}/caches" "${ISO_HOME}/caches"
		# init スクリプト（社内のミラーなど、依存の取り先を差し替えているもの）は持ち込む。JDK の探し先は変えない
		[ -d "${REAL_GUH}/init.d" ] && ln -s "${REAL_GUH}/init.d" "${ISO_HOME}/init.d"
		export GRADLE_USER_HOME="${ISO_HOME}"
		REAL_JAVA_HOME="${JAVA_HOME:-}"
		if jdk21=`find_jdk21`; then
			export JAVA_HOME="${jdk21}"
		else
			JDK21_MISSING="yes"
		fi
	fi
	run_fixture "${fx}"
	if [ -n "${REAL_GUH:-}" ]; then
		if [ "${REAL_GUH}" = "${HOME}/.gradle" ]; then unset GRADLE_USER_HOME; else GRADLE_USER_HOME="${REAL_GUH}"; fi
		REAL_GUH=""
		if [ -n "${REAL_JAVA_HOME:-}" ]; then JAVA_HOME="${REAL_JAVA_HOME}"; else unset JAVA_HOME; fi
		REAL_JAVA_HOME=""
	fi
	elapsed=`date +%s`
	elapsed=`expr "${elapsed}" - "${started}"`
	all="${INIT} ${SCAFFOLD} ${INC} ${TEST} ${RERUN}"
	case "${all}" in
	*NG*) VERDICT="**NG**"; NG=1 ;;
	*飛ばした*) VERDICT="飛ばした" ;;
	*) VERDICT="OK"; OK_COUNT=`expr "${OK_COUNT}" + 1` ;;
	esac
	echo "   init=${INIT} scaffold=${SCAFFOLD} include=${INC} test=${TEST} rerun=${RERUN} -> ${VERDICT}（${elapsed}s）"
	desc=`fx_desc_${fx}`
	[ -n "${NOTE}" ] && desc="${desc}<br>（${NOTE}）"
	logs="[setup](file://${LOGS}/${fx}-setup.log)"
	[ -f "${LOGS}/${fx}-test1.log" ] && logs="${logs} / [test1](file://${LOGS}/${fx}-test1.log)（[Gradle](file://${LOGS}/${fx}-test1-gradle.log)）"
	[ -f "${LOGS}/${fx}-test2.log" ] && logs="${logs} / [test2](file://${LOGS}/${fx}-test2.log)（[Gradle](file://${LOGS}/${fx}-test2-gradle.log)）"
	printf '| %s | %s | %s | %s | %s | %s | %s | %s | %s |\n' \
		"${fx}" "${desc}" "${INIT}" "${SCAFFOLD}" "${INC}" "${TEST}" "${RERUN}" "${VERDICT}" "${logs}" >>"${ROWS}"
done

# ---- 4. 結果 -------------------------------------------------------------------------------
{
	echo "# インストールキットのチェック"
	echo
	echo "- 通った: ${OK_COUNT} / ${TOTAL}"
	echo "- 版: katachi ${VERSION} / Kotlin ${K} / AGP ${AGP}"
	echo "- チェックリストの言語: ${LANG_ARG}"
	echo "- katachi コマンド: ${CLI_CHECK}（\`${KATACHI}\`。[--version](file://${LOGS}/cli-version.log)）"
	echo "- katachi コマンドの build（publishToMavenLocal・cliDist）: ${PUBLISH}（[ログ](file://${LOGS}/publish.log)）"
	echo "- スクリプト: \`.claude/skills/prerelease/scripts/check-install-kit.sh\`（fixture の定義は \`install-kit-fixtures.sh\`）"
	echo
	echo "test（配線）は \`katachi agent test\` が「定義していないファイルが Unexpected として出て落ちる」なら OK。2回目は、ルートにファイルを1つ足して"
	echo "\`--rerun\` なしで走らせ、test タスクが実行されて足したファイルが出れば OK。形の欄の（）に検査用モジュールの置き場所と"
	echo "テストのフレームワーク（scaffold の出力）を写している。"
	echo
	echo "| fixture | 形 | 準備（doctor・作業用ディレクトリ） | scaffold | settings の include | test（配線） | 2回目が走ったか | 結果 | ログ |"
	echo "|---|---|---|---|---|---|---|---|---|"
	cat "${ROWS}"
} >"${RESULT}"
rm -f "${ROWS}"

echo "結果: ${OK_COUNT} / ${TOTAL} OK -> file://${RESULT}"
[ "${NG}" = "0" ] || exit 1
exit 0
