#!/bin/sh
# インストールキットのスモーク（検査対象のプロジェクトで Gradle を起動しない）。check-install-kit.sh（fixture で Gradle まで回す）の
# 手前で、katachi コマンド（katachi-script の CLI。brew で入るものと同じ形）のサブコマンドを合成の Gradle プロジェクトに当てて、
# 終了コードと出力の grep で確かめる。1 ケース 1 関数。
#
# katachi コマンドは --cli で渡す。省略すると build-katachi-cli.sh が組んだ
# katachi-script/build/cli/katachi-cli-<版>/bin/katachi を使う（無ければ止まる。先に build-katachi-cli.sh を回す）。
# 作業用ディレクトリは katachi skill の手順 0-2 どおり、skills/katachi/assets/<言語>/ のテンプレートを写して meta を埋めて作る。
# 所要 2〜5 分（コマンド 1 回ごとに kotlin の起動がかかる）。
#
# 使い方（リポジトリの直下で）:
#   sh .claude/skills/prerelease/scripts/build-katachi-cli.sh                  # 先に katachi コマンドを組む
#   sh .claude/skills/prerelease/scripts/check-install-kit-smoke.sh
#   sh .claude/skills/prerelease/scripts/check-install-kit-smoke.sh --only removed_commands,lint_checks
#   sh .claude/skills/prerelease/scripts/check-install-kit-smoke.sh --cli /opt/homebrew/bin/katachi   # brew で入れた katachi で
#
#   --cli        katachi コマンド（既定 katachi-script/build/cli/katachi-cli-<版>/bin/katachi）
#   --work       作業場所（既定 .local/tmp/install-kit-smoke。毎回作り直す）
#   --only       走らせるケース（カンマ区切り。名前は case_<名前> の <名前>）
#
# 終了コード: 全部 OK なら 0、1 つでも NG なら 1、引数の誤り・katachi コマンドが無いときは 2。
# 決まり: $VAR の直後に日本語を置かない（${VAR} で閉じる。bash 3.2 が食う）。
set -u

usage() {
	cat <<'USAGE'
使い方: sh check-install-kit-smoke.sh [--cli <path>] [--work <dir>] [--only <case>,...]
  --cli        katachi コマンド（既定 katachi-script/build/cli/katachi-cli-<版>/bin/katachi。build-katachi-cli.sh が組む）
  --work       作業場所（既定 .local/tmp/install-kit-smoke）
  --only       走らせるケース（カンマ区切り）
USAGE
}

WORK=""
ONLY=""
KATACHI=""
while [ $# -gt 0 ]; do
	case "$1" in
	--cli) KATACHI="${2:?--cli に値がありません}"; shift 2 ;;
	--work) WORK="${2:?--work に値がありません}"; shift 2 ;;
	--only) ONLY="${2:?--only に値がありません}"; shift 2 ;;
	-h | --help) usage; exit 0 ;;
	*) usage >&2; exit 2 ;;
	esac
done

REPO=$(git rev-parse --show-toplevel) || exit 2
[ -n "${WORK}" ] || WORK="${REPO}/.local/tmp/install-kit-smoke"
case "${WORK}" in /*) ;; *) WORK="${REPO}/${WORK}" ;; esac
VERSION=$(sed -n 's/^katachi[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "${REPO}/gradle/libs.versions.toml" | head -1)
[ -n "${KATACHI}" ] || KATACHI="${REPO}/katachi-script/build/cli/katachi-cli-${VERSION}/bin/katachi"
case "${KATACHI}" in /*) ;; */*) KATACHI="$(pwd)/${KATACHI}" ;; esac
if ! command -v "${KATACHI}" >/dev/null 2>&1; then
	echo "katachi コマンドがありません: ${KATACHI}" >&2
	echo "  先に sh .claude/skills/prerelease/scripts/build-katachi-cli.sh で組むか、--cli で渡してください" >&2
	exit 2
fi
ASSETS="${REPO}/skills/katachi/assets"
K=$(sed -n 's/^kotlin[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "${REPO}/gradle/libs.versions.toml" | head -1)
[ -n "${K}" ] || K="2.2.0"
export KATACHI_LANG=ja
# 作業用ディレクトリは cd した先から探させる（スモークの外の KATACHI_WORKDIR を持ち込まない）
unset KATACHI_WORKDIR
PASS=0
FAIL=0
CASES=""

ok() { PASS=$((PASS + 1)); printf 'ok   %s\n' "$1"; }
ng() { FAIL=$((FAIL + 1)); printf 'NG   %s\n       %s\n' "$1" "${2:-}"; }

# ---- 合成プロジェクト ---------------------------------------------------------------------------

# fx_rootjvm 相当（wrapper 無し。gradlew は空ファイル）。git init して、パスを出す。
fresh() {
	_d="${WORK}/${1}"
	rm -rf "${_d}"
	mkdir -p "${_d}/src/main/kotlin/fx"
	printf 'rootProject.name = "fx"\n' >"${_d}/settings.gradle.kts"
	printf 'plugins {\n    kotlin("jvm") version "%s"\n}\nrepositories { mavenCentral() }\n' "${K}" >"${_d}/build.gradle.kts"
	printf 'package fx\nclass A\n' >"${_d}/src/main/kotlin/fx/A.kt"
	printf '.local/\nbuild/\n' >"${_d}/.gitignore"
	: >"${_d}/gradlew"
	chmod +x "${_d}/gradlew"
	(cd "${_d}" && git init -q && git add -A &&
		git -c user.name=katachi-smoke -c user.email=smoke@example.invalid -c commit.gpgsign=false commit -qm init)
	printf '%s\n' "${_d}"
}

# 作業用ディレクトリを手順 0-2 どおりに作ったプロジェクトを作る。$1 = 名前、$2 = moduleDir（省略で architecture-test）、$3 = 言語（省略で ja）。
inited() {
	_d=$(fresh "$1")
	_w="${_d}/.local/install-katachi"
	mkdir -p "${_w}/tmp"
	cp "${ASSETS}/${3:-ja}/check-list.html" "${_w}/check-list.html"
	jset "${_w}/check-list.html" checklist "d['meta'].update(workdir='.local/install-katachi', projectRoot='${_d}', moduleDir='${2:-architecture-test}', version='0.3.0', git='yes')"
	printf '%s\n' "${_d}"
}

wd() { printf '%s/.local/install-katachi\n' "$1"; }

# HTML の JSON ブロックを python の文（d が JSON）で書き換える。$1 = HTML, $2 = ブロックの id, $3 = 文
jset() {
	python3 - "$1" "$2" "$3" <<'PY'
import json, sys
path, block, stmt = sys.argv[1:]
lines = open(path, encoding="utf-8").read().split("\n")
start = lines.index('<script type="application/json" id="%s">' % block)
end = lines.index("</script>", start)
d = json.loads("\n".join(lines[start + 1:end]))
exec(stmt)
lines[start + 1:end] = json.dumps(d, ensure_ascii=False, indent=2).split("\n")
open(path, "w", encoding="utf-8").write("\n".join(lines))
PY
}

# 定義ディレクトリにファイルを置く。$1 = ディレクトリ, $2 = ファイル名, 標準入力 = 中身
write_def() {
	mkdir -p "$1"
	cat >"$1/$2"
}

want() { case ",${ONLY}," in ",,") return 0 ;; *",$1,"*) return 0 ;; *) return 1 ;; esac; }
run_case() {
	CASES="${CASES} $1"
	want "$1" || return 0
	"case_$1"
}

# ---- ケース ----------------------------------------------------------------------------------

# lint は定義ディレクトリが無ければ exit 1
case_lint_without_definition() {
	d=$(inited lintno static-analysis/katachi)
	(cd "${d}" && "${KATACHI}" lint) >"${WORK}/lintno.log" 2>&1
	st=$?
	[ "${st}" -eq 1 ] && grep -q 'meta.moduleDir' "${WORK}/lintno.log" && grep -q 'static-analysis/katachi' "${WORK}/lintno.log" &&
		ok "lint は定義ディレクトリが無ければ exit 1" || ng "lint は定義ディレクトリが無ければ exit 1" "st=${st} $(cat "${WORK}/lintno.log")"
}

# lint は制約の無い手書き Gradle Role を gradle() の 1 行に置き換えるよう指摘する
# lint は制約を持つ手書き Gradle Role に、置いたファイルに合う hook への移し方を出す
# lint は平たい group と分けていないファイルと documented の候補を出す
# lint は知らないオプションを使い方の誤り（exit 2）にし、点検を走らせない
case_lint_checks() {
	d=$(inited lint)
	(cd "${d}" && "${KATACHI}" scaffold --package com.example.app) >"${WORK}/lint.scaffold.log" 2>&1 || {
		ng "lint の下ごしらえ（scaffold）" "$(tail -3 "${WORK}/lint.scaffold.log")"
		return
	}
	def="${d}/architecture-test/src/test/kotlin/com/example/app/test/architecture"
	# 1. Gradle ファイル + fileConstraint の Role（hook への移し方を出す。settings なので settingsScript { }）
	write_def "${def}" "GradleConstrained.kt" <<'EOF'
package com.example.app.test.architecture

val gradleConstrained = architecture {
    "ConstrainedBuildScripts".role {
        layout { "settings.gradle".ktsFile() }
        fileConstraint("namespace follows module path") { subject -> emptyList() }
    }
}
EOF
	# 2. 制約の無い手書き Gradle Role（gradle() の 1 行に置き換えるよう指摘する）
	write_def "${def}" "GradleHand.kt" <<'EOF'
package com.example.app.test.architecture

val gradleHand = architecture {
    "PlainBuildScripts".role {
        layout { "build.gradle".ktsFile() }
    }
}
EOF
	# 3. 平たい group（Role 8 個）と、1 ファイル 2 Role（group と Role）
	write_def "${def}" "FlatGroup.kt" <<'EOF'
package com.example.app.test.architecture

val flat = architecture {
    "Flat".group {
        "R1".role { layout { "a".file() } }
        "R2".role { layout { "b".file() } }
        "R3".role { layout { "c".file() } }
        "R4".role { layout { "d".file() } }
        "R5".role { layout { "e".file() } }
        "R6".role { layout { "f".file() } }
        "R7".role { layout { "g".file() } }
        "R8".role { layout { "h".file() } }
    }
    "Extra".role { layout { "i".file() } }
}
EOF
	# 4. README だけの Role（documented = false の候補）
	write_def "${def}" "Readme.kt" <<'EOF'
package com.example.app.test.architecture

val readme = architecture {
    "Readme".role {
        layout { "README.md".file() }
    }
}
EOF
	(cd "${d}" && "${KATACHI}" lint) >"${WORK}/lint.log" 2>&1
	st=$?
	# 制約の無い Role の一覧（「…宣言しています（Role: …）」の行）には PlainBuildScripts だけが載る
	if [ "${st}" -eq 0 ] && grep 'を手書きの Role で宣言しています（Role: ' "${WORK}/lint.log" | grep -q 'PlainBuildScripts' &&
		! grep 'を手書きの Role で宣言しています（Role: ' "${WORK}/lint.log" | grep -q 'ConstrainedBuildScripts' &&
		grep -q 'gradle() の1行に置き換えてください' "${WORK}/lint.log"; then
		ok "lint は制約の無い手書き Gradle Role を gradle() の 1 行に置き換えるよう指摘する"
	else
		ng "lint は制約の無い手書き Gradle Role を gradle() の 1 行に置き換えるよう指摘する" "st=${st} $(cat "${WORK}/lint.log")"
	fi
	# 制約を持つ Role は、その Role の行に置いたファイルに合う hook（settings なら settingsScript { }）が出て、
	# 移し方の文面（buildScript { } の制約が受け取るファイルと、[AmbiguousLayout]）が続く
	if grep 'Role "ConstrainedBuildScripts"' "${WORK}/lint.log" | grep -q 'gradle { settingsScript { } }' &&
		grep -q '制約は gradle { buildScript { } }' "${WORK}/lint.log" &&
		grep -q 'subject.files をパスで絞って' "${WORK}/lint.log" &&
		grep -q 'AmbiguousLayout' "${WORK}/lint.log"; then
		ok "lint は制約を持つ手書き Gradle Role に、置いたファイルに合う hook への移し方を出す"
	else
		ng "lint は制約を持つ手書き Gradle Role に、置いたファイルに合う hook への移し方を出す" "$(cat "${WORK}/lint.log")"
	fi
	if grep -q '平たい group' "${WORK}/lint.log" && grep -q '"Flat"' "${WORK}/lint.log" &&
		grep -q '分けていないファイル' "${WORK}/lint.log" && grep -q 'FlatGroup.kt' "${WORK}/lint.log" &&
		grep -q 'documented = false の候補' "${WORK}/lint.log" && grep -q '"Readme"' "${WORK}/lint.log"; then
		ok "lint は平たい group と分けていないファイルと documented の候補を出す"
	else
		ng "lint は平たい group と分けていないファイルと documented の候補を出す" "$(cat "${WORK}/lint.log")"
	fi
	out=$(cd "${d}" && "${KATACHI}" lint --bogus 2>&1)
	st=$?
	[ "${st}" -eq 2 ] && printf '%s\n' "${out}" | grep -q 'lint: 知らないオプションです: --bogus' && ! printf '%s\n' "${out}" | grep -q 'Role の点検' &&
		ok "lint は知らないオプションを使い方の誤り（exit 2）にする" || ng "lint は知らないオプションを使い方の誤り（exit 2）にする" "st=${st} ${out}"
}

# scaffold --deps catalog は別名の衝突で止まり、未指定なら literal に落ちる
case_scaffold_deps_conflict() {
	d=$(inited deps)
	mkdir -p "${d}/gradle"
	cat >"${d}/gradle/libs.versions.toml" <<EOF
[versions]
kotlin = "${K}"

[libraries]
katachi = { module = "com.example:other", version = "1.0" }
EOF
	(cd "${d}" && "${KATACHI}" scaffold --package com.example.app --dry-run --deps catalog) >"${WORK}/deps.catalog.log" 2>&1
	st=$?
	[ "${st}" -eq 1 ] && grep -q '別名 katachi' "${WORK}/deps.catalog.log" && ok "scaffold --deps catalog は別名の衝突で止まる" ||
		ng "scaffold --deps catalog は別名の衝突で止まる" "st=${st} $(cat "${WORK}/deps.catalog.log")"
	(cd "${d}" && "${KATACHI}" scaffold --package com.example.app --dry-run) >"${WORK}/deps.auto.log" 2>&1
	st=$?
	[ "${st}" -eq 0 ] && grep -qE '依存:[[:space:]]+literal' "${WORK}/deps.auto.log" && grep -q '別名 katachi' "${WORK}/deps.auto.log" &&
		ok "scaffold は --deps 未指定なら literal に落ちて note を出す" || ng "scaffold は --deps 未指定なら literal に落ちて note を出す" "st=${st} $(cat "${WORK}/deps.auto.log")"
	grep -q 'me\.tbsten' "${d}/gradle/libs.versions.toml" && ng "dry-run は toml を触らない" "toml が変わった" || ok "dry-run は toml を触らない"
}

# scaffold は1行の pluginManagement { repositories { mavenCentral() } } を読み、「次:」に ./gradlew <module>:test を出す
case_scaffold_oneline_plugin_management() {
	d=$(inited oneline)
	printf 'pluginManagement { repositories { mavenCentral() } }\nrootProject.name = "fx"\n' >"${d}/settings.gradle.kts"
	(cd "${d}" && "${KATACHI}" scaffold --package com.example.app) >"${WORK}/oneline.log" 2>&1
	st=$?
	[ "${st}" -eq 0 ] && grep -q 'pluginManagement に追加: no' "${WORK}/oneline.log" && ! grep -q '足すまで' "${WORK}/oneline.log" &&
		ok "scaffold は1行の pluginManagement { repositories { mavenCentral() } } を読める" ||
		ng "scaffold は1行の pluginManagement { repositories { mavenCentral() } } を読める" "st=${st} $(grep -n 'pluginManagement\|足す' "${WORK}/oneline.log")"
	grep -qF 'pluginManagement { repositories { mavenCentral() } }' "${d}/settings.gradle.kts" && ok "1行の pluginManagement は書き換えない" || ng "1行の pluginManagement は書き換えない" "$(head -n 3 "${d}/settings.gradle.kts")"
	grep -q "^次: ./gradlew :architecture-test:test\$" "${WORK}/oneline.log" && ! grep -q 'katachi agent' "${WORK}/oneline.log" &&
		ok "scaffold の「次:」は ./gradlew :architecture-test:test を勧める" || ng "scaffold の「次:」は ./gradlew :architecture-test:test を勧める" "$(grep -n '^次:' "${WORK}/oneline.log")"
	# mavenCentral() が無い1行は、足す指示を出す
	d2=$(inited oneline-miss)
	printf 'pluginManagement { repositories { gradlePluginPortal() } }\nrootProject.name = "fx"\n' >"${d2}/settings.gradle.kts"
	(cd "${d2}" && "${KATACHI}" scaffold --package com.example.app --dry-run) >"${WORK}/oneline-miss.log" 2>&1
	grep -q 'pluginManagement に追加: yes（mavenCentral()）' "${WORK}/oneline-miss.log" && ok "mavenCentral() が無い1行の pluginManagement は不足と判定する" ||
		ng "mavenCentral() が無い1行の pluginManagement は不足と判定する" "$(grep -n 'pluginManagement' "${WORK}/oneline-miss.log")"
}

# doctor は Kotlin 2.2 未満を警告する（Gradle の版も見る）
case_kotlin_too_old() {
	d=$(fresh oldkt)
	sed -i.orig 's/version "[^"]*"/version "1.9.10"/' "${d}/build.gradle.kts" && rm -f "${d}/build.gradle.kts.orig"
	mkdir -p "${d}/gradle/wrapper"
	printf 'distributionUrl=https\\://services.gradle.org/distributions/gradle-7.5.1-bin.zip\n' >"${d}/gradle/wrapper/gradle-wrapper.properties"
	out=$(cd "${d}" && "${KATACHI}" doctor 2>&1)
	st=$?
	[ "${st}" -eq 0 ] && printf '%s\n' "${out}" | grep -q 'Kotlin は 1.9.10 です' && printf '%s\n' "${out}" | grep -q 'Gradle も上げる必要があります.*7.5.1' &&
		ok "doctor は Kotlin 2.2 未満を警告し、Gradle 7.6.3 未満なら Gradle も上げると言う" || ng "doctor は Kotlin 2.2 未満を警告し、Gradle 7.6.3 未満なら Gradle も上げると言う" "st=${st} ${out}"
}

# skill のテンプレートは ja / en とも JSON が読め、手順が使う項目 id をそろえて持つ
case_skill_assets() {
	for l in ja en; do
		out=$(python3 - "${ASSETS}/${l}" <<'PY' 2>&1
import json, sys
def block(path, bid):
    lines = open(path, encoding="utf-8").read().split("\n")
    start = lines.index('<script type="application/json" id="%s">' % bid)
    return json.loads("\n".join(lines[start + 1:lines.index("</script>", start)]))
c = block(sys.argv[1] + "/check-list.html", "checklist")
block(sys.argv[1] + "/check-list.html", "report")
print(" ".join(i["id"] for st in c["steps"] for i in st["items"]))
PY
)
		want="0-1 0-2 1-1 1-2 1-3 2-1 2-2 3-1 3-2 3-3 3-4 4-1 4-2 4-3 5-1 5-2 6-1 6-2 6-3 6-4"
		[ "${out}" = "${want}" ] && ok "skill のテンプレート（${l}）は JSON が読め、項目 id が手順と合う" ||
			ng "skill のテンプレート（${l}）は JSON が読め、項目 id が手順と合う" "${out}"
	done
}

# skill が置き換えたサブコマンドと、消した agent（test / compare-violations）は無く、skill の入れ方か ./gradlew を案内する（KATACHI-329 / 429）
case_removed_commands() {
	d=$(fresh removed)
	for c in init guide docs data check summary; do
		out=$(cd "${d}" && "${KATACHI}" agent "${c}" 2>&1)
		st=$?
		[ "${st}" -ne 0 ] && printf '%s\n' "${out}" | grep -q 'gh skill install TBSten/katachi katachi' &&
			ok "katachi agent ${c} は無く、skill の入れ方を出す" || ng "katachi agent ${c} は無く、skill の入れ方を出す" "st=${st} ${out}"
	done
	for c in "agent" "agent --help" "agent test" "agent compare-violations" "test" "compare-violations"; do
		# shellcheck disable=SC2086
		out=$(cd "${d}" && "${KATACHI}" ${c} 2>&1)
		st=$?
		[ "${st}" -ne 0 ] && printf '%s\n' "${out}" | grep -q './gradlew <検査用モジュール>:test を直接実行します' &&
			ok "katachi ${c} は無く、./gradlew を直接打つよう案内する" || ng "katachi ${c} は無く、./gradlew を直接打つよう案内する" "st=${st} ${out}"
	done
	out=$("${KATACHI}" --help 2>&1)
	printf '%s\n' "${out}" | grep -q 'katachi agent' &&
		ng "katachi --help に agent は出ない" "${out}" || ok "katachi --help に agent は出ない"
}

# 出力の言語は写したチェックリストの <html lang> に従う
case_lang_from_checklist() {
	d=$(inited langen architecture-test en)
	(cd "${d}" && "${KATACHI}" lint) >"${WORK}/langen.log" 2>&1
	grep -q 'The definition directory does not exist' "${WORK}/langen.log" &&
		ok "en のチェックリストなら英語で答える" || ng "en のチェックリストなら英語で答える" "$(cat "${WORK}/langen.log")"
}

# ---- 実行 ----------------------------------------------------------------------------------

mkdir -p "${WORK}"
# katachi コマンドが今のリポジトリの版か（古い build や brew の別の版を回していないか）
v=$("${KATACHI}" --version 2>&1)
st=$?
[ "${st}" -eq 0 ] && printf '%s\n' "${v}" | grep -qF "${VERSION}" && ok "katachi --version はリポジトリの版（${VERSION}）" ||
	ng "katachi --version はリポジトリの版（${VERSION}）" "st=${st} ${v}"

run_case skill_assets
run_case removed_commands
run_case lang_from_checklist
run_case lint_without_definition
run_case lint_checks
run_case scaffold_deps_conflict
run_case scaffold_oneline_plugin_management
run_case kotlin_too_old

if [ -n "${ONLY}" ]; then
	for one in $(echo "${ONLY}" | tr ',' ' '); do
		case " ${CASES} " in
		*" ${one} "*) ;;
		*) echo "未知のケース: ${one}（あるのは${CASES}）" >&2; exit 2 ;;
		esac
	done
fi

printf '\npass %s / fail %s（%s）\n' "${PASS}" "${FAIL}" "${WORK}"
[ "${FAIL}" -eq 0 ] || exit 1
exit 0
