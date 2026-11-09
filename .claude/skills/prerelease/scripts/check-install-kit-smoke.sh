#!/bin/sh
# インストールキットのスモーク（検査対象のプロジェクトで Gradle を起動しない）。check-install-kit.sh（fixture で Gradle まで回す）の
# 手前で、katachi コマンド（katachi-script の CLI。brew で入るものと同じ形）のサブコマンドを合成の Gradle プロジェクトに当てて、
# 終了コードと出力の grep で確かめる。1 ケース 1 関数。
#
# katachi コマンドは --cli で渡す。省略すると build-katachi-cli.sh が組んだ
# katachi-script/build/cli/katachi-cli-<版>/bin/katachi を使う（無ければ止まる。先に build-katachi-cli.sh を回す）。
# 配信元は <work>/site に組む（docs/public/install の ja / en と、docs/dist/llms-full.txt）。手順書とテンプレートは
# katachi-script に同梱なので、配信元から取るのは docs --topic の llms-full.txt だけ。所要 5〜10 分（コマンド 1 回ごとに
# kotlin の起動がかかる）。
#
# 使い方（リポジトリの直下で）:
#   sh .claude/skills/prerelease/scripts/build-katachi-cli.sh                  # 先に katachi コマンドを組む
#   sh .claude/skills/prerelease/scripts/check-install-kit-smoke.sh
#   sh .claude/skills/prerelease/scripts/check-install-kit-smoke.sh --only guide_toc,lock_wait
#   sh .claude/skills/prerelease/scripts/check-install-kit-smoke.sh --cli /opt/homebrew/bin/katachi   # brew で入れた katachi で
#   sh .claude/skills/prerelease/scripts/check-install-kit-smoke.sh --llms-full /path/to/llms-full.txt
#
#   --cli        katachi コマンド（既定 katachi-script/build/cli/katachi-cli-<版>/bin/katachi）
#   --work       作業場所（既定 .local/tmp/install-kit-smoke。毎回作り直す）
#   --only       走らせるケース（カンマ区切り。名前は case_<名前> の <名前>）
#   --llms-full  docs --topic が読む llms-full.txt（既定 docs/dist/llms-full.txt。無ければ docs で pnpm run build を試す）
#
# 終了コード: 全部 OK なら 0、1 つでも NG なら 1、引数の誤り・katachi コマンドが無いときは 2。
# 決まり: $VAR の直後に日本語を置かない（${VAR} で閉じる。bash 3.2 が食う）。
set -u

usage() {
	cat <<'USAGE'
使い方: sh check-install-kit-smoke.sh [--cli <path>] [--work <dir>] [--only <case>,...] [--llms-full <path>]
  --cli        katachi コマンド（既定 katachi-script/build/cli/katachi-cli-<版>/bin/katachi。build-katachi-cli.sh が組む）
  --work       作業場所（既定 .local/tmp/install-kit-smoke）
  --only       走らせるケース（カンマ区切り）
  --llms-full  docs --topic が読む llms-full.txt（既定 docs/dist/llms-full.txt）
USAGE
}

WORK=""
ONLY=""
LLMS=""
KATACHI=""
while [ $# -gt 0 ]; do
	case "$1" in
	--cli) KATACHI="${2:?--cli に値がありません}"; shift 2 ;;
	--work) WORK="${2:?--work に値がありません}"; shift 2 ;;
	--only) ONLY="${2:?--only に値がありません}"; shift 2 ;;
	--llms-full) LLMS="${2:?--llms-full に値がありません}"; shift 2 ;;
	-h | --help) usage; exit 0 ;;
	*) usage >&2; exit 2 ;;
	esac
done

REPO=$(git rev-parse --show-toplevel) || exit 2
[ -n "${WORK}" ] || WORK="${REPO}/.local/tmp/install-kit-smoke"
case "${WORK}" in /*) ;; *) WORK="${REPO}/${WORK}" ;; esac
[ -n "${LLMS}" ] || LLMS="${REPO}/docs/dist/llms-full.txt"
VERSION=$(sed -n 's/^katachi[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "${REPO}/gradle/libs.versions.toml" | head -1)
[ -n "${KATACHI}" ] || KATACHI="${REPO}/katachi-script/build/cli/katachi-cli-${VERSION}/bin/katachi"
case "${KATACHI}" in /*) ;; */*) KATACHI="$(pwd)/${KATACHI}" ;; esac
if ! command -v "${KATACHI}" >/dev/null 2>&1; then
	echo "katachi コマンドがありません: ${KATACHI}" >&2
	echo "  先に sh .claude/skills/prerelease/scripts/build-katachi-cli.sh で組むか、--cli で渡してください" >&2
	exit 2
fi
SITE="${WORK}/site"
K=$(sed -n 's/^kotlin[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "${REPO}/gradle/libs.versions.toml" | head -1)
[ -n "${K}" ] || K="2.2.0"
export KATACHI_DOCS="file://${SITE}"
export KATACHI_LANG=ja
# 作業用ディレクトリは cd した先から探させる（スモークの外の KATACHI_WORKDIR を持ち込まない）
unset KATACHI_WORKDIR
PASS=0
FAIL=0
CASES=""

ok() { PASS=$((PASS + 1)); printf 'ok   %s\n' "$1"; }
ng() { FAIL=$((FAIL + 1)); printf 'NG   %s\n       %s\n' "$1" "${2:-}"; }

# ---- 配信元 ---------------------------------------------------------------------------------

build_site() {
	rm -rf "${SITE}"
	mkdir -p "${SITE}/install"
	cp -R "${REPO}/docs/public/install/ja" "${SITE}/install/ja"
	cp -R "${REPO}/docs/public/install/en" "${SITE}/install/en"
	if [ ! -f "${LLMS}" ] && [ "${LLMS}" = "${REPO}/docs/dist/llms-full.txt" ]; then
		echo "docs/dist/llms-full.txt が無いので docs を build します（pnpm run build）..."
		(cd "${REPO}/docs" && pnpm run build) >"${WORK}/docs-build.log" 2>&1 || echo "docs の build に失敗（${WORK}/docs-build.log）" >&2
	fi
	if [ -f "${LLMS}" ]; then
		cp "${LLMS}" "${SITE}/llms-full.txt"
	else
		echo "llms-full.txt がありません: ${LLMS}（docs --topic のケースは NG になる）" >&2
	fi
}

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

# init を済ませたプロジェクトを作る。$1 = 名前、残り = init の追加引数。
inited() {
	_n="$1"
	shift
	_d=$(fresh "${_n}")
	if ! (cd "${_d}" && "${KATACHI}" agent init --katachi 0.3.0 --workdir .local/install-katachi "$@") >"${WORK}/${_n}.init.log" 2>&1; then
		printf 'init failed (%s): %s\n' "${_n}" "$(tail -3 "${WORK}/${_n}.init.log")" >&2
	fi
	printf '%s\n' "${_d}"
}

wd() { printf '%s/.local/install-katachi\n' "$1"; }
lockdir() { printf '%s/.local/install-katachi/tmp/cache/.lock\n' "$1"; }

# $1 = プロジェクト, $2 = 対象（report / check-list）, $3 = python の式（d が JSON）
jget() {
	(cd "$1" && "${KATACHI}" agent data get "$2") 2>/dev/null | python3 -c 'import json,sys; d=json.load(sys.stdin); print('"$3"')'
}

add_cq() { (cd "$1" && "${KATACHI}" agent add codebase-question --question "$2" --observed o --recommendation "$3") >/dev/null 2>&1; }
add_q() { (cd "$1" && "${KATACHI}" agent add question --question "$2" --observed o --recommendation "$3") >/dev/null 2>&1; }

# 定義ディレクトリにファイルを置く。$1 = ディレクトリ, $2 = ファイル名, 標準入力 = 中身
write_def() {
	mkdir -p "$1"
	cat >"$1/$2"
}

# 偽の gradlew。引数を 1 行目に出し、fake-out.txt を続けて出し、fake-exit の値で終わる。
fake_gradlew() {
	cat >"$1/gradlew" <<EOF
#!/bin/sh
printf 'ARGS: %s\\n' "\$*"
cat "$1/fake-out.txt"
exit \$(cat "$1/fake-exit")
EOF
	chmod +x "$1/gradlew"
}

want() { case ",${ONLY}," in ",,") return 0 ;; *",$1,"*) return 0 ;; *) return 1 ;; esac; }
run_case() {
	CASES="${CASES} $1"
	want "$1" || return 0
	"case_$1"
}

# ---- ケース ----------------------------------------------------------------------------------

# guide は目次に全セクションの ID を順に出す
case_guide_toc() {
	d=$(inited guide)
	out=$(cd "${d}" && "${KATACHI}" agent guide 2>"${WORK}/guide.toc.err")
	ids=$(printf '%s\n' "${out}" | awk -F'\t' 'NF == 3 { printf "%s ", $1 }')
	want_ids="0 0-1 0-2 0-3 1 1-1 1-2 1-3 2 2-1 2-2 3 3-1 3-2 3-3 4 4-1 4-2 4-3 5 6 6-0 6-A 6-B 6-C 6-D "
	[ "${ids}" = "${want_ids}" ] && ok "guide は目次に全セクションの ID を順に出す" || ng "guide は目次に全セクションの ID を順に出す" "ids=${ids}"
	# katachi skill の setup の表（| <ID> | <見出し> | <ファイル> |）と同じ並び（KATACHI-318）
	toc_ids=$(sed -n 's/^| \([0-9][-0-9A-D]*\) | .* | \[step.*$/\1 /p' "${REPO}/skills/katachi/references/setup/README.md" | tr -d '\n')
	[ "${toc_ids}" = "${ids}" ] && ok "guide の目次は skill の setup の表と一致する" || ng "guide の目次は skill の setup の表と一致する" "toc=${toc_ids}"
	printf '%s\n' "${out}" | tail -n 1 | grep -q '6-A.*6-D.*6-1.*6-4' && ok "guide の目次の末尾に 6-1〜6-4 の案内" || ng "guide の目次の末尾に 6-1〜6-4 の案内" "$(printf '%s\n' "${out}" | tail -n 1)"
	# en でも同じ ID の列
	d_en=$(inited guide-en --lang en)
	ids_en=$(cd "${d_en}" && "${KATACHI}" agent guide 2>/dev/null | awk -F'\t' 'NF == 3 { printf "%s ", $1 }')
	[ "${ids_en}" = "${want_ids}" ] && ok "guide の目次は --lang en でも同じ ID の列" || ng "guide の目次は --lang en でも同じ ID の列" "ids=${ids_en}"
}

# guide はコードフェンスの中の # を見出しに数えない
case_guide_fence() {
	d="${WORK}/guide"
	[ -d "${d}" ] || d=$(inited guide)
	out=$(cd "${d}" && "${KATACHI}" agent guide 0-1 2>&1)
	st=$?
	if [ "${st}" -eq 0 ] && printf '%s\n' "${out}" | grep -q '^# 1\. Is the katachi command installed' && ! printf '%s\n' "${out}" | grep -q '^### 0-2\.'; then
		ok "guide はコードフェンスの中の # を見出しに数えない"
	else
		ng "guide はコードフェンスの中の # を見出しに数えない" "st=${st} $(printf '%s\n' "${out}" | head -n 3)"
	fi
	printf '%s\n' "${out}" | head -n 1 | grep -q '^### 0-1\.' && ok "guide <セクション> は見出し行から始まる" || ng "guide <セクション> は見出し行から始まる" "$(printf '%s\n' "${out}" | head -n 1)"
}

# guide <セクション> は次のセクションのコマンドで終わる
case_guide_next() {
	d="${WORK}/guide"
	[ -d "${d}" ] || d=$(inited guide)
	last=$(cd "${d}" && "${KATACHI}" agent guide 3-2 2>&1 | tail -n 1)
	[ "${last}" = "次: katachi agent guide 3-3" ] && ok "guide <セクション> は次のセクションのコマンドで終わる" || ng "guide <セクション> は次のセクションのコマンドで終わる" "last=${last}"
	last=$(cd "${d}" && "${KATACHI}" agent guide 6-D 2>&1 | tail -n 1)
	case "${last}" in
	次:*) ng "guide の最後のセクションには次が付かない" "last=${last}" ;;
	*) ok "guide の最後のセクションには次が付かない" ;;
	esac
	a=$(cd "${d}" && "${KATACHI}" agent guide 6-1 2>&1)
	b=$(cd "${d}" && "${KATACHI}" agent guide 6-A 2>&1)
	[ -n "${a}" ] && [ "${a}" = "${b}" ] && ok "guide 6-1 は 6-A に読み替える" || ng "guide 6-1 は 6-A に読み替える" "$(printf '%s\n' "${a}" | head -n 2)"
	out=$(cd "${d}" && "${KATACHI}" agent guide 9-9 2>&1)
	st=$?
	[ "${st}" -eq 1 ] && printf '%s\n' "${out}" | grep -q '^0-1	' && ok "guide の知らない ID は目次を出して exit 1" || ng "guide の知らない ID は目次を出して exit 1" "st=${st}"
}

# check の次のセクションは 5-1 と 5-2 を guide 5 に読み替える
case_check_next() {
	d=$(inited chk)
	out=$(cd "${d}" && "${KATACHI}" agent check 0-1 2>&1)
	printf '%s\n' "${out}" | grep -q "^次: katachi agent guide 0-2\$" && ok "check の次は未完了の最初の項目のセクション" || ng "check の次は未完了の最初の項目のセクション" "${out}"
	# 項目の無いセクション（0-3、章の前書き）を飛ばさず、目次の順で次に読むものを指す
	out=$(cd "${d}" && "${KATACHI}" agent check 0-2 2>&1)
	printf '%s\n' "${out}" | grep -q "^次: katachi agent guide 0-3\$" && ok "check 0-2 の次は項目の無い 0-3" || ng "check 0-2 の次は項目の無い 0-3" "${out}"
	(cd "${d}" && "${KATACHI}" agent check 1-1 1-2 1-3 2-1) >/dev/null 2>&1
	out=$(cd "${d}" && "${KATACHI}" agent check 2-2 2>&1)
	printf '%s\n' "${out}" | grep -q "^次: katachi agent guide 3\$" && ok "check 2-2 の次は章 3 の前書き" || ng "check 2-2 の次は章 3 の前書き" "${out}"
	(cd "${d}" && "${KATACHI}" agent check 3-1 3-2 3-3) >/dev/null 2>&1
	out=$(cd "${d}" && "${KATACHI}" agent check 4-1 4-2 4-3 2>&1)
	printf '%s\n' "${out}" | grep -q "^次: katachi agent guide 5\$" && ok "check の次のセクションは 5-1 と 5-2 を guide 5 に読み替える" || ng "check の次のセクションは 5-1 と 5-2 を guide 5 に読み替える" "${out}"
	out=$(cd "${d}" && "${KATACHI}" agent check 5-1 5-2 2>&1)
	printf '%s\n' "${out}" | grep -q '^次:' && ng "check は未完了が無ければ次を出さない（6-x は数えない）" "${out}" || ok "check は未完了が無ければ次を出さない（6-x は数えない）"
}

# docs --topic layout は古い版の写しを含めない / docs --topics は対応表を出す
case_docs_topic() {
	d="${WORK}/guide"
	[ -d "${d}" ] || d=$(inited guide)
	p=$(cd "${d}" && "${KATACHI}" agent docs --topic layout 2>"${WORK}/topic.err")
	st=$?
	if [ "${st}" -eq 0 ] && [ -s "${p}" ]; then
		ok "docs --topic は保存先のパスを出す"
	else
		ng "docs --topic は保存先のパスを出す" "st=${st} p=${p} $(cat "${WORK}/topic.err")"
		return
	fi
	grep -q '^取得元: file://' "${WORK}/topic.err" && ok "docs --topic は取得元を標準エラーに出す" || ng "docs --topic は取得元を標準エラーに出す" "$(cat "${WORK}/topic.err")"
	head -n 1 "${p}" | grep -q '^# Layout$' && ok "docs --topic layout はページの見出しから始まる" || ng "docs --topic layout はページの見出しから始まる" "$(head -n 1 "${p}")"
	n=$(awk '/^```/ { c = !c; next } !c && /^# /' "${p}" | wc -l | tr -d ' ')
	[ "${n}" = "1" ] && ok "docs --topic の # の見出しは 1 つ" || ng "docs --topic の # の見出しは 1 つ" "n=${n}"
	grep -q 'Roadmap' "${p}" && ng "docs --topic layout は古い版の写しを含めない" "Roadmap が含まれる" || ok "docs --topic layout は古い版の写しを含めない"
	out=$(cd "${d}" && "${KATACHI}" agent docs --topics 2>&1)
	st=$?
	if [ "${st}" -eq 0 ] && printf '%s\n' "${out}" | grep -q '^layout	' && printf '%s\n' "${out}" | grep -q '^role	'; then
		ok "docs --topics は対応表を出す"
	else
		ng "docs --topics は対応表を出す" "st=${st} $(printf '%s\n' "${out}" | head -n 3)"
	fi
	# 全話題が、見出しを見つけて、凍結した版（/0.2/ など）の写しを切り出さずに終わる
	bad=""
	for t in $(cd "${d}" && "${KATACHI}" agent docs --topics 2>/dev/null | cut -f1); do
		tp=$(cd "${d}" && "${KATACHI}" agent docs --topic "${t}" 2>"${WORK}/topic-${t}.err")
		if [ $? -ne 0 ] || [ ! -s "${tp}" ] || grep -Eq '\]\(/[^)/]+/[0-9]+\.[0-9]+/' "${tp}"; then
			bad="${bad} ${t}($(head -n 1 "${WORK}/topic-${t}.err" | cut -c1-80))"
		fi
	done
	[ -z "${bad}" ] && ok "docs --topic は全話題で見出しが見つかり、凍結した版を切り出さない" || ng "docs --topic は全話題で見出しが見つかり、凍結した版を切り出さない" "${bad}"
	out=$(cd "${d}" && "${KATACHI}" agent docs --topic nope 2>&1)
	st=$?
	[ "${st}" -eq 1 ] && printf '%s\n' "${out}" | grep -q 'layout' && ok "docs --topic の知らない名前は一覧を出して止まる" || ng "docs --topic の知らない名前は一覧を出して止まる" "st=${st}"
}

# preferences set は知らないキーで止まる / preferences set moduleDir は meta.moduleDir に入る
case_preferences() {
	d=$(inited pref)
	(cd "${d}" && "${KATACHI}" agent preferences) >"${WORK}/pref.show.log" 2>&1
	st=$?
	if [ "${st}" -eq 0 ] && grep -q '^nestedGroups = yes（推奨）' "${WORK}/pref.show.log" && grep -q '^moduleDir = architecture-test（記録済み）' "${WORK}/pref.show.log" &&
		grep -q '^testFramework = junit5（推奨）' "${WORK}/pref.show.log" && grep -q '^dependencyStyle = literal（推奨）' "${WORK}/pref.show.log" &&
		grep -q '^constraintNames = plain（推奨）' "${WORK}/pref.show.log" && grep -q '^oneRolePerFile = yes（推奨）' "${WORK}/pref.show.log"; then
		ok "preferences は 6 つのキーを推奨つきで出す"
	else
		ng "preferences は 6 つのキーを推奨つきで出す" "st=${st} $(cat "${WORK}/pref.show.log")"
	fi
	(cd "${d}" && "${KATACHI}" agent preferences set nestedGroups=maybe) >"${WORK}/pref.bad.log" 2>&1
	st=$?
	[ "${st}" -eq 1 ] && grep -q 'maybe' "${WORK}/pref.bad.log" && grep -q 'yes' "${WORK}/pref.bad.log" &&
		ok "preferences set は知らない値で止まり使える値を並べる" || ng "preferences set は知らない値で止まり使える値を並べる" "st=${st} $(cat "${WORK}/pref.bad.log")"
	(cd "${d}" && "${KATACHI}" agent preferences set foo=bar) >"${WORK}/pref.bad2.log" 2>&1
	st=$?
	[ "${st}" -eq 1 ] && grep -q 'foo' "${WORK}/pref.bad2.log" && ok "preferences set は知らないキーで止まる" || ng "preferences set は知らないキーで止まる" "st=${st} $(cat "${WORK}/pref.bad2.log")"
	(cd "${d}" && "${KATACHI}" agent preferences set moduleDir=a/b nestedGroups=no) >"${WORK}/pref.set.log" 2>&1
	st=$?
	m=$(cd "${d}" && "${KATACHI}" agent data get check-list meta.moduleDir 2>/dev/null)
	n=$(jget "${d}" check-list '(d.get("preferences") or {}).get("nestedGroups")')
	[ "${st}" -eq 0 ] && [ "${m}" = "a/b" ] && [ "${n}" = "no" ] && ok "preferences set moduleDir は meta.moduleDir に入る" ||
		ng "preferences set moduleDir は meta.moduleDir に入る" "st=${st} meta.moduleDir=${m} nestedGroups=${n} $(cat "${WORK}/pref.set.log")"
	[ ! -d "$(lockdir "${d}")" ] && ok "preferences set のあとにロックが残らない" || ng "preferences set のあとにロックが残らない" "lock remains"
}

# add codebase-question は id を cq1 から順に採番する（チェックリストの q<n> と重ねない）
case_add_question_ids() {
	d=$(inited qid)
	add_cq "${d}" C1 r1
	add_cq "${d}" C2 r2
	add_cq "${d}" C3 r3
	ids=$(jget "${d}" report '" ".join(q.get("id") or "-" for q in d["questions"])')
	[ "${ids}" = "cq1 cq2 cq3" ] && ok "add codebase-question は id を cq1 から順に採番する" || ng "add codebase-question は id を cq1 から順に採番する" "ids=${ids}"
}

# answers import は無い id を飛ばして件数を出す
case_answers_import() {
	d=$(inited ans)
	add_cq "${d}" C1 r1
	add_cq "${d}" C2 r2
	add_cq "${d}" C3 r3
	printf '{"target":"report","answers":{"cq2":{"answer":"A2","note":null},"cq9":{"answer":"nope"}}}\n' >"${WORK}/ans.json"
	(cd "${d}" && "${KATACHI}" agent answers import "${WORK}/ans.json") >"${WORK}/ans.import.log" 2>&1
	st=$?
	[ "${st}" -eq 0 ] && grep -q '取り込みました: 回答 1 件（未回答 2 件）' "${WORK}/ans.import.log" && grep -q 'cq9' "${WORK}/ans.import.log" &&
		ok "answers import は無い id を飛ばして件数を出す" || ng "answers import は無い id を飛ばして件数を出す" "st=${st} $(cat "${WORK}/ans.import.log")"
	a=$(jget "${d}" report '(d["answers"]["cq2"]["answer"], bool(d["answers"]["cq2"].get("answeredAt")), "cq9" in d["answers"])')
	[ "${a}" = "('A2', True, False)" ] && ok "answers import は answers に answeredAt つきで入れ、無い id は入れない" || ng "answers import は answers に answeredAt つきで入れ、無い id は入れない" "a=${a}"
	[ ! -d "$(lockdir "${d}")" ] && ok "answers import のあとにロックが残らない" || ng "answers import のあとにロックが残らない" "lock remains"
}

# summary は回答済みを件数だけ、未回答だけ列挙する
case_summary_answers() {
	d="${WORK}/ans"
	[ -d "${d}" ] || case_answers_import
	# 未完了形: 再開の欄に件数だけ
	(cd "${d}" && "${KATACHI}" agent summary) >"${WORK}/ans.summary.log" 2>&1
	st=$?
	[ "${st}" -eq 0 ] && grep -q '^❌' "${WORK}/ans.summary.log" && grep -q 'レポート 回答済み 1 / 未回答 2' "${WORK}/ans.summary.log" && ! grep -q 'C1' "${WORK}/ans.summary.log" &&
		ok "summary の未完了形は確認事項を件数だけ出す" || ng "summary の未完了形は確認事項を件数だけ出す" "st=${st} $(cat "${WORK}/ans.summary.log")"
	# 完了形: 回答済みは件数、未回答だけ id / 問い / 推奨
	ids=$(cd "${d}" && "${KATACHI}" agent data get check-list 2>/dev/null | python3 -c 'import json,sys; print(" ".join(i["id"] for s in json.load(sys.stdin)["steps"] for i in s["items"]))')
	(cd "${d}" && "${KATACHI}" agent check ${ids}) >/dev/null 2>&1
	(cd "${d}" && "${KATACHI}" agent summary) >"${WORK}/ans.summary2.log" 2>&1
	st=$?
	if [ "${st}" -eq 0 ] && grep -q '^✅' "${WORK}/ans.summary2.log" && grep -q 'レポート.*回答済み 1 件.*未回答 2 件' "${WORK}/ans.summary2.log" &&
		grep -q -- '- cq1 C1 → 推奨: r1' "${WORK}/ans.summary2.log" && grep -q -- '- cq3 C3 → 推奨: r3' "${WORK}/ans.summary2.log" && ! grep -q 'C2' "${WORK}/ans.summary2.log"; then
		ok "summary は回答済みを件数だけ、未回答だけ列挙する"
	else
		ng "summary は回答済みを件数だけ、未回答だけ列挙する" "st=${st} $(cat "${WORK}/ans.summary2.log")"
	fi
	# 成功の形式にも再開の欄（手順書の区切りが読ませる）。6-0 と、未実施の 6-A〜6-C（提案が無いので 6-D は出さない）
	(cd "${d}" && "${KATACHI}" agent uncheck 6-1 6-3 6-4) >/dev/null 2>&1
	(cd "${d}" && "${KATACHI}" agent summary) >"${WORK}/ans.summary3.log" 2>&1
	if grep -q '^再開:' "${WORK}/ans.summary3.log" && grep -q "レビューの指摘を直す: katachi agent guide 6-0\$" "${WORK}/ans.summary3.log" &&
		grep -q "guide 6-A\$" "${WORK}/ans.summary3.log" && grep -q "guide 6-C\$" "${WORK}/ans.summary3.log" &&
		! grep -q "guide 6-B\$" "${WORK}/ans.summary3.log" && ! grep -q "guide 6-D\$" "${WORK}/ans.summary3.log"; then
		ok "summary の成功の形式にも再開の欄（6-0 と未実施の 6-A〜6-D）を出す"
	else
		ng "summary の成功の形式にも再開の欄（6-0 と未実施の 6-A〜6-D）を出す" "$(cat "${WORK}/ans.summary3.log")"
	fi
}

# summary は scaffold 前でも止まらず再開の欄に「まだ無い」を出す
case_summary_resume() {
	d=$(inited resume --module-dir static-analysis/katachi)
	(cd "${d}" && "${KATACHI}" agent check 0-1 0-2 1-1) >/dev/null 2>&1
	(cd "${d}" && "${KATACHI}" agent summary) >"${WORK}/resume.log" 2>&1
	st=$?
	if [ "${st}" -eq 0 ] && grep -q '^再開:' "${WORK}/resume.log" && grep -q '終わった項目: 0-1 0-2 1-1$' "${WORK}/resume.log" &&
		grep -q "次のセクション: katachi agent guide 1-2" "${WORK}/resume.log" &&
		grep -q '定義: まだ無い（scaffold 未実行。meta.moduleDir = static-analysis/katachi）' "${WORK}/resume.log"; then
		ok "summary は scaffold 前でも止まらず再開の欄に「まだ無い」を出す"
	else
		ng "summary は scaffold 前でも止まらず再開の欄に「まだ無い」を出す" "st=${st} $(cat "${WORK}/resume.log")"
	fi
}

# add を 8 並列で走らせても 8 件とも残る
case_add_parallel() {
	d=$(inited par)
	i=1
	while [ "${i}" -le 8 ]; do
		(cd "${d}" && "${KATACHI}" agent add role --name "r${i}" --importance "${i}" --layout "l${i}" --naming n --group g) >"${WORK}/par.${i}.log" 2>&1 &
		i=$((i + 1))
	done
	wait
	n=$(jget "${d}" report 'len(d["roles"])')
	[ "${n}" = "8" ] && ok "add を 8 並列で走らせても 8 件とも残る" || ng "add を 8 並列で走らせても 8 件とも残る" "roles=${n}"
	[ ! -d "$(lockdir "${d}")" ] && ok "add のあとにロックが残らない" || ng "add のあとにロックが残らない" "lock remains"
}

# add は件数が合わなければ止まる
case_add_count_mismatch() {
	d=$(inited cnt)
	add_cq "${d}" C1 r1
	add_cq "${d}" C2 r2
	add_cq "${d}" C3 r3
	(cd "${d}" && KATACHI_ADD_EXPECT=0 "${KATACHI}" agent add codebase-question --question C4 --observed o --recommendation r4) >"${WORK}/cnt.log" 2>&1
	st=$?
	[ "${st}" -eq 1 ] && grep -q '件数が合いません' "${WORK}/cnt.log" && grep -q '書き込み前 0 件、追記後 4 件' "${WORK}/cnt.log" &&
		ok "add は件数が合わなければ止まる" || ng "add は件数が合わなければ止まる" "st=${st} $(cat "${WORK}/cnt.log")"
	[ ! -d "$(lockdir "${d}")" ] && ok "件数検査で止まってもロックが残らない" || ng "件数検査で止まってもロックが残らない" "lock remains"
}

# lock は待ち時間を過ぎたら止まる
case_lock_wait() {
	d=$(inited lockwait)
	lock=$(lockdir "${d}")
	mkdir -p "${lock}"
	printf '%s' "$$" >"${lock}/pid"
	start=$(date +%s)
	(cd "${d}" && KATACHI_LOCK_WAIT=2 "${KATACHI}" agent add role --name r --importance 1 --layout l --naming n --group g) >"${WORK}/lockwait.log" 2>&1
	st=$?
	end=$(date +%s)
	el=$((end - start))
	# 起動（kotlin と JVM）の時間 BOOT を足して見る
	[ "${st}" -eq 1 ] && grep -q "${lock}" "${WORK}/lockwait.log" && [ "${el}" -ge 2 ] && [ "${el}" -le $((BOOT + 6)) ] &&
		ok "lock は待ち時間を過ぎたら止まる（文面に lock のパス）" || ng "lock は待ち時間を過ぎたら止まる（文面に lock のパス）" "st=${st} elapsed=${el} $(cat "${WORK}/lockwait.log")"
	[ -d "${lock}" ] && ok "待って止まったときは他人のロックを消さない" || ng "待って止まったときは他人のロックを消さない" "lock removed"
	rm -rf "${lock}"
}

# lock は死んだ pid の残骸を消して取り直す
case_lock_stale_pid() {
	d=$(inited lockpid)
	lock=$(lockdir "${d}")
	mkdir -p "${lock}"
	printf '999999' >"${lock}/pid"
	start=$(date +%s)
	# 待ち時間を長く取り、それより十分早く終わったら「待たずに」（起動の時間があるので 1 秒以内では見ない）
	(cd "${d}" && KATACHI_LOCK_WAIT=30 "${KATACHI}" agent add role --name r --importance 1 --layout l --naming n --group g) >"${WORK}/lockpid.log" 2>&1
	st=$?
	end=$(date +%s)
	el=$((end - start))
	[ "${st}" -eq 0 ] && [ "$(jget "${d}" report 'len(d["roles"])')" = "1" ] && [ ! -d "${lock}" ] && [ "${el}" -le $((BOOT + 5)) ] &&
		ok "lock は死んだ pid の残骸を待たずに消して取り直す" || ng "lock は死んだ pid の残骸を待たずに消して取り直す" "st=${st} elapsed=${el} boot=${BOOT} $(cat "${WORK}/lockpid.log")"
}

# lock は pid の無い古い残骸を消して取り直す
case_lock_stale_no_pid() {
	d=$(inited locknopid)
	lock=$(lockdir "${d}")
	mkdir -p "${lock}"
	two_min_ago=$(python3 -c 'import time; print(time.strftime("%Y%m%d%H%M", time.localtime(time.time() - 120)))')
	touch -t "${two_min_ago}" "${lock}"
	(cd "${d}" && KATACHI_LOCK_WAIT=2 "${KATACHI}" agent add role --name r --importance 1 --layout l --naming n --group g) >"${WORK}/locknopid.log" 2>&1
	st=$?
	[ "${st}" -eq 0 ] && [ "$(jget "${d}" report 'len(d["roles"])')" = "1" ] && [ ! -d "${lock}" ] &&
		ok "lock は pid の無い古い残骸を消して取り直す" || ng "lock は pid の無い古い残骸を消して取り直す" "st=${st} $(cat "${WORK}/locknopid.log")"
	mkdir -p "${lock}"
	(cd "${d}" && KATACHI_LOCK_WAIT=2 "${KATACHI}" agent add role --name r2 --importance 1 --layout l --naming n --group g) >"${WORK}/locknopid2.log" 2>&1
	st=$?
	[ "${st}" -eq 1 ] && [ -d "${lock}" ] && ok "lock は pid の無い新しいロックを書き込み中とみなして待つ" ||
		ng "lock は pid の無い新しいロックを書き込み中とみなして待つ" "st=${st} $(cat "${WORK}/locknopid2.log")"
	rm -rf "${lock}"
}

# data set の任意のパスは対象の隣にロックを作る
case_lock_arbitrary_path() {
	d=$(inited lockany)
	mkdir -p "${WORK}/lockany-scratch"
	x="${WORK}/lockany-scratch/x.json"
	cp "$(wd "${d}")/project-code-base-report.html" "${x}"
	printf '{"a": 1}\n' >"${WORK}/lockany-scratch/in.json"
	mkdir -p "${x}.lock"
	printf '%s' "$$" >"${x}.lock/pid"
	(cd "${d}" && KATACHI_LOCK_WAIT=2 "${KATACHI}" agent data set "${x}" "${WORK}/lockany-scratch/in.json") >"${WORK}/lockany.log" 2>&1
	st=$?
	[ "${st}" -eq 1 ] && grep -q 'x\.json\.lock' "${WORK}/lockany.log" && ! grep -q 'tmp/cache/\.lock' "${WORK}/lockany.log" &&
		ok "data set の任意のパスは対象の隣にロックを作る" || ng "data set の任意のパスは対象の隣にロックを作る" "st=${st} $(cat "${WORK}/lockany.log")"
	rm -r "${x}.lock"
	(cd "${d}" && "${KATACHI}" agent data set "${x}" "${WORK}/lockany-scratch/in.json") >"${WORK}/lockany2.log" 2>&1
	st=$?
	[ "${st}" -eq 0 ] && [ ! -e "${x}.lock" ] && ok "data set のあとに任意のパスのロックが残らない" || ng "data set のあとに任意のパスのロックが残らない" "st=${st} $(cat "${WORK}/lockany2.log")"
}

# init --workdir は既存のファイルの件数を出して上書きしない（*.bak は数えない）
case_init_workdir_existing() {
	d=$(fresh initwd)
	mkdir -p "${d}/.local/install-katachi/tmp"
	printf 'keep\n' >"${d}/.local/install-katachi/notes.txt"
	: >"${d}/.local/install-katachi/other.md"
	: >"${d}/.local/install-katachi/check-list.html.bak"
	(cd "${d}" && "${KATACHI}" agent init --katachi 0.3.0 --workdir .local/install-katachi) >"${WORK}/initwd.log" 2>&1
	st=$?
	[ "${st}" -eq 0 ] && grep -q '既存のファイルがあります（2 件）' "${WORK}/initwd.log" && [ "$(cat "${d}/.local/install-katachi/notes.txt")" = "keep" ] &&
		ok "init --workdir は既存のファイルの件数を出して上書きしない（*.bak は数えない）" ||
		ng "init --workdir は既存のファイルの件数を出して上書きしない（*.bak は数えない）" "st=${st} $(grep -n '既存' "${WORK}/initwd.log")"
}

# init --module-dir の入れ子パスが KEY=VALUE と meta.moduleDir に出る
case_init_module_dir() {
	d=$(inited initmod --module-dir static-analysis/katachi)
	log="${WORK}/initmod.init.log"
	grep -q '^KATACHI_MODULE_DIR=static-analysis/katachi$' "${log}" && grep -q '^KATACHI_MODULE_PATH=:static-analysis:katachi$' "${log}" &&
		ok "init --module-dir の入れ子パスが KEY=VALUE に出る" || ng "init --module-dir の入れ子パスが KEY=VALUE に出る" "$(grep KATACHI_MODULE "${log}")"
	m=$(cd "${d}" && "${KATACHI}" agent data get check-list meta.moduleDir 2>/dev/null)
	[ "${m}" = "static-analysis/katachi" ] && ok "init --module-dir が meta.moduleDir に入る" || ng "init --module-dir が meta.moduleDir に入る" "meta.moduleDir=${m}"
	d2=$(fresh initbad)
	(cd "${d2}" && "${KATACHI}" agent init --katachi 0.3.0 --workdir .local/install-katachi --module-dir ../up) >"${WORK}/initbad.log" 2>&1
	st=$?
	[ "${st}" -eq 1 ] && grep -q 'Gradle ルートからの相対パス' "${WORK}/initbad.log" && ok "init --module-dir は不正な値で止まる" || ng "init --module-dir は不正な値で止まる" "st=${st} $(tail -2 "${WORK}/initbad.log")"
}

# lint は定義ディレクトリが無ければ exit 1
case_lint_without_definition() {
	d=$(inited lintno --module-dir static-analysis/katachi)
	(cd "${d}" && "${KATACHI}" lint) >"${WORK}/lintno.log" 2>&1
	st=$?
	[ "${st}" -eq 1 ] && grep -q 'preferences set moduleDir' "${WORK}/lintno.log" && grep -q 'static-analysis/katachi' "${WORK}/lintno.log" &&
		ok "lint は定義ディレクトリが無ければ exit 1" || ng "lint は定義ディレクトリが無ければ exit 1" "st=${st} $(cat "${WORK}/lintno.log")"
}

# lint は制約の無い手書き Gradle Role を gradle() の 1 行に置き換えるよう指摘する
# lint は制約を持つ手書き Gradle Role に、置いたファイルに合う hook への移し方を出す
# lint は平たい group と分けていないファイルと documented の候補を出す
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
}

# add role は Gradle のビルドファイルを拒み、--constraint-only --note なら受け付ける
# add excluded は .gitignore 済みのパスを拒む
case_add_rejects() {
	d=$(inited rej)
	(cd "${d}" && "${KATACHI}" agent add role --name r --importance 1 --layout app/build.gradle.kts --naming n --group g) >"${WORK}/rej.role.log" 2>&1
	st=$?
	[ "${st}" -eq 1 ] && grep -q 'gradle() が受け持ちます' "${WORK}/rej.role.log" && ok "add role は Gradle のビルドファイルを拒む" ||
		ng "add role は Gradle のビルドファイルを拒む" "st=${st} $(cat "${WORK}/rej.role.log")"
	(cd "${d}" && "${KATACHI}" agent add role --name r --importance 1 --layout app/build.gradle.kts --naming n --group g --constraint-only) >"${WORK}/rej.role2.log" 2>&1
	st=$?
	[ "${st}" -eq 1 ] && grep -q -- '--note' "${WORK}/rej.role2.log" && ok "--constraint-only は --note が無ければ止まる" ||
		ng "--constraint-only は --note が無ければ止まる" "st=${st} $(cat "${WORK}/rej.role2.log")"
	(cd "${d}" && "${KATACHI}" agent add role --name r --importance 1 --layout app/build.gradle.kts --naming n --group g --constraint-only --note "namespace とパッケージの一致") >"${WORK}/rej.role3.log" 2>&1
	st=$?
	v=$(jget "${d}" report '[r.get("constraintOnly") for r in d["roles"]]')
	[ "${st}" -eq 0 ] && [ "${v}" = "[True]" ] && ok "add role --constraint-only --note は constraintOnly: true で受け付ける" ||
		ng "add role --constraint-only --note は constraintOnly: true で受け付ける" "st=${st} v=${v} $(cat "${WORK}/rej.role3.log")"
	# .gitignore の `build/` はディレクトリだけに当たる書き方なので、実在するディレクトリで確かめる（git check-ignore の規則）
	mkdir -p "${d}/build"
	(cd "${d}" && "${KATACHI}" agent add excluded --path build --reason r) >"${WORK}/rej.ex.log" 2>&1
	st=$?
	[ "${st}" -eq 1 ] && grep -q '\.gitignore で除外' "${WORK}/rej.ex.log" && [ "$(jget "${d}" report 'len(d["excluded"])')" = "0" ] &&
		ok "add excluded は .gitignore 済みのパスを拒む" || ng "add excluded は .gitignore 済みのパスを拒む" "st=${st} $(cat "${WORK}/rej.ex.log")"
	(cd "${d}" && "${KATACHI}" agent add excluded --path src/gen --reason r) >"${WORK}/rej.ex2.log" 2>&1
	st=$?
	[ "${st}" -eq 0 ] && [ "$(jget "${d}" report 'len(d["excluded"])')" = "1" ] && ok "add excluded は ignore されていないパスを受け付ける" ||
		ng "add excluded は ignore されていないパスを受け付ける" "st=${st} $(cat "${WORK}/rej.ex2.log")"
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

# scaffold は1行の pluginManagement { repositories { mavenCentral() } } を読み、「次:」に katachi agent test を出す
case_scaffold_oneline_plugin_management() {
	d=$(inited oneline)
	printf 'pluginManagement { repositories { mavenCentral() } }\nrootProject.name = "fx"\n' >"${d}/settings.gradle.kts"
	(cd "${d}" && "${KATACHI}" scaffold --package com.example.app) >"${WORK}/oneline.log" 2>&1
	st=$?
	[ "${st}" -eq 0 ] && grep -q 'pluginManagement に追加: no' "${WORK}/oneline.log" && ! grep -q '足すまで' "${WORK}/oneline.log" &&
		ok "scaffold は1行の pluginManagement { repositories { mavenCentral() } } を読める" ||
		ng "scaffold は1行の pluginManagement { repositories { mavenCentral() } } を読める" "st=${st} $(grep -n 'pluginManagement\|足す' "${WORK}/oneline.log")"
	grep -qF 'pluginManagement { repositories { mavenCentral() } }' "${d}/settings.gradle.kts" && ok "1行の pluginManagement は書き換えない" || ng "1行の pluginManagement は書き換えない" "$(head -n 3 "${d}/settings.gradle.kts")"
	grep -q "^次: katachi agent test\$" "${WORK}/oneline.log" && ! grep -q 'gradlew :architecture-test:test' "${WORK}/oneline.log" &&
		ok "scaffold の「次:」は katachi agent test を勧める" || ng "scaffold の「次:」は katachi agent test を勧める" "$(grep -n '^次:' "${WORK}/oneline.log")"
	# mavenCentral() が無い1行は、足す指示を出す
	d2=$(inited oneline-miss)
	printf 'pluginManagement { repositories { gradlePluginPortal() } }\nrootProject.name = "fx"\n' >"${d2}/settings.gradle.kts"
	(cd "${d2}" && "${KATACHI}" scaffold --package com.example.app --dry-run) >"${WORK}/oneline-miss.log" 2>&1
	grep -q 'pluginManagement に追加: yes（mavenCentral()）' "${WORK}/oneline-miss.log" && ok "mavenCentral() が無い1行の pluginManagement は不足と判定する" ||
		ng "mavenCentral() が無い1行の pluginManagement は不足と判定する" "$(grep -n 'pluginManagement' "${WORK}/oneline-miss.log")"
}

# test は gradlew の無い場所で exit 2 / 要約とログのパスを出し、--save で名前付きのログを写す / 前回との差分を出す
case_test() {
	d=$(inited tst)
	w=$(wd "${d}")
	rm -f "${d}/gradlew"
	out=$(cd "${d}" && "${KATACHI}" agent test 2>&1)
	st=$?
	[ "${st}" -eq 2 ] && printf '%s\n' "${out}" | grep -q 'gradlew' && ok "test は gradlew の無い場所で exit 2" || ng "test は gradlew の無い場所で exit 2" "st=${st} ${out}"
	fake_gradlew "${d}"
	out=$(cd "${d}" && "${KATACHI}" agent test 2>&1)
	st=$?
	[ "${st}" -eq 2 ] && printf '%s\n' "${out}" | grep -q 'scaffold' && ok "test は定義ディレクトリが無ければ exit 2" || ng "test は定義ディレクトリが無ければ exit 2" "st=${st} ${out}"
	mkdir -p "${d}/architecture-test/src/test/kotlin/fx"
	printf 'package fx\n' >"${d}/architecture-test/src/test/kotlin/fx/ProjectArchitecture.kt"
	printf '> Task :architecture-test:test\nKatachi check failed: 2 violations (Unexpected: 2)\n  [UnexpectedFile] app/src/main/kotlin/Foo.kt\n  [UnexpectedFile] app/src/main/kotlin/Bar.kt\nBUILD FAILED\n' >"${d}/fake-out.txt"
	printf '1\n' >"${d}/fake-exit"
	out=$(cd "${d}" && "${KATACHI}" agent test --save before-refactor 2>&1)
	st=$?
	[ "${st}" -eq 1 ] && ok "test は検査が落ちたら exit 1" || ng "test は検査が落ちたら exit 1" "st=${st} ${out}"
	printf '%s\n' "${out}" | head -n 1 | grep -q '^JDK: ' && ok "test の先頭は JDK の行" || ng "test の先頭は JDK の行" "$(printf '%s\n' "${out}" | head -n 1)"
	printf '%s\n' "${out}" | grep -q '^ログ: file://' && printf '%s\n' "${out}" | grep -q '^結果: Katachi check failed: 2 violations' &&
		ok "test は要約（ログのパスと結果）を出す" || ng "test は要約（ログのパスと結果）を出す" "${out}"
	[ -s "${w}/tmp/test/latest.log" ] && [ -s "${w}/tmp/test-before-refactor.log" ] && ok "test は latest.log と --save の写しを置く" ||
		ng "test は latest.log と --save の写しを置く" "$(ls "${w}/tmp" "${w}/tmp/test" 2>&1)"
	grep -q '^ARGS: :architecture-test:test --console=plain' "${w}/tmp/test/latest.log" && ok "test は :<module>:test を gradlew に渡す" ||
		ng "test は :<module>:test を gradlew に渡す" "$(head -n 1 "${w}/tmp/test/latest.log")"
	printf '> Task :architecture-test:test\nKatachi check failed: 3 violations (Unexpected: 3)\n  [UnexpectedFile] app/src/main/kotlin/Foo.kt\n  [UnexpectedFile] app/src/main/kotlin/Bar.kt\n  [UnexpectedFile] app/src/main/kotlin/Baz.kt\nBUILD FAILED\n' >"${d}/fake-out.txt"
	out=$(cd "${d}" && "${KATACHI}" agent test 2>&1)
	printf '%s\n' "${out}" | grep -q '増えた 1 件' && printf '%s\n' "${out}" | grep -q '^  + \[UnexpectedFile\] app/src/main/kotlin/Baz.kt' &&
		ok "test は前回との差分を出す" || ng "test は前回との差分を出す" "${out}"
	printf '> Task :architecture-test:test\nBUILD SUCCESSFUL in 3s\n' >"${d}/fake-out.txt"
	printf '0\n' >"${d}/fake-exit"
	out=$(cd "${d}" && "${KATACHI}" agent test 2>&1)
	st=$?
	[ "${st}" -eq 0 ] && printf '%s\n' "${out}" | grep -q '^結果: 検査は通りました（違反 0 件）' && ok "test は違反 0 件で exit 0" || ng "test は違反 0 件で exit 0" "st=${st} ${out}"
	# Gradle のテスト出力の形（例外のクラス名が前に付き、件数が単数）でも検査が落ちたと判定する
	printf 'ProjectArchitectureTest > the project layout matches the definition() FAILED\n    me.tbsten.katachi.check.KatachiArchitectureAssertionError: Katachi check failed: 1 violation (Unexpected: 1)\n\n    [UnexpectedFile] file:///x/settings.gradle.kts.bak\n      No role is defined for this file.\n\n1 tests completed, 1 failed\n> Task :architecture-test:test FAILED\nBUILD FAILED in 19s\n' >"${d}/fake-out.txt"
	printf '1\n' >"${d}/fake-exit"
	out=$(cd "${d}" && "${KATACHI}" agent test 2>&1)
	st=$?
	[ "${st}" -eq 1 ] && printf '%s\n' "${out}" | grep -q '^結果: Katachi check failed: 1 violation (Unexpected: 1)' && printf '%s\n' "${out}" | grep -q '^  + \[UnexpectedFile\] file:///x/settings.gradle.kts.bak' &&
		ok "test は例外のクラス名つきの単数の結果行でも検査が落ちたと判定する" || ng "test は例外のクラス名つきの単数の結果行でも検査が落ちたと判定する" "st=${st} ${out}"
	# 検査の違反は無いが、検査用モジュールのほかのテストが失敗した（コンパイルエラーとは別の文面で exit 1）
	printf 'ProjectRootSpec > walking up from the working directory FAILED\n    org.opentest4j.AssertionFailedError: expected:<sample> but was:<copy>\n\nProjectArchitectureTest > the project layout matches the definition() PASSED\n\n2 tests completed, 1 failed\n> Task :architecture-test:test FAILED\nFAILURE: Build failed with an exception.\n> There were failing tests. See the report at: file:///x/index.html\nBUILD FAILED in 19s\n' >"${d}/fake-out.txt"
	out=$(cd "${d}" && "${KATACHI}" agent test 2>&1)
	st=$?
	[ "${st}" -eq 1 ] && printf '%s\n' "${out}" | grep -q '^結果: 検査の違反は出ていませんが、検査用モジュールのほかのテストが失敗しています' && printf '%s\n' "${out}" | grep -q '^ProjectRootSpec > walking up from the working directory FAILED' &&
		ok "test はほかのテストが失敗したときは検査まで届いていないと言わずに exit 1" || ng "test はほかのテストが失敗したときは検査まで届いていないと言わずに exit 1" "st=${st} ${out}"
}

# data get のキー指定は部分だけ出す
case_data_get_key() {
	d="${WORK}/guide"
	[ -d "${d}" ] || d=$(inited guide)
	v=$(cd "${d}" && "${KATACHI}" agent data get report meta.gradleRoot 2>&1)
	st=$?
	[ "${st}" -eq 0 ] && [ "${v}" = "${d}" ] && ok "data get のキー指定は部分だけ出す" || ng "data get のキー指定は部分だけ出す" "st=${st} v=${v}"
	out=$(cd "${d}" && "${KATACHI}" agent data get report nope 2>&1)
	st=$?
	[ "${st}" -eq 1 ] && printf '%s\n' "${out}" | grep -q 'nope' && ok "data get の無いキーは exit 1" || ng "data get の無いキーは exit 1" "st=${st} ${out}"
}

# test と compare-violations は katachi の違反の見出しだけを数え、Gradle の [Incubating] の行を数えない
case_check_result_headings() {
	d=$(inited chk)
	w=$(wd "${d}")
	fake_gradlew "${d}"
	mkdir -p "${d}/architecture-test/src/test/kotlin/fx"
	printf 'package fx\n' >"${d}/architecture-test/src/test/kotlin/fx/ProjectArchitecture.kt"
	printf '0\n' >"${d}/fake-exit"
	printf '> Task :architecture-test:test\nBUILD SUCCESSFUL in 3s\n' >"${d}/fake-out.txt"
	(cd "${d}" && "${KATACHI}" agent test --save before-refactor) >/dev/null 2>&1
	# ビルドファイルを変えた・configuration cache が切れたときに Gradle 9 が出す行
	printf '> Task :architecture-test:test\n\n[Incubating] Problems report is available at: file:///x/build/reports/problems/problems-report.html\n\nBUILD SUCCESSFUL in 3s\n' >"${d}/fake-out.txt"
	out=$(cd "${d}" && "${KATACHI}" agent test --save after-refactor 2>&1)
	st=$?
	[ "${st}" -eq 0 ] && printf '%s\n' "${out}" | grep -q '^前回との差分（previous.log 比）: 無し' && ! printf '%s\n' "${out}" | grep -q 'Incubating' &&
		ok "test は Gradle の [Incubating] の行を前回との差分に数えない" || ng "test は Gradle の [Incubating] の行を前回との差分に数えない" "st=${st} ${out}"
	out=$(cd "${d}" && "${KATACHI}" agent compare-violations 2>&1)
	st=$?
	[ "${st}" -eq 0 ] && printf '%s\n' "${out}" | grep -q '^違反の見出し: 前 0 件 / 後 0 件' && printf '%s\n' "${out}" | grep -q '^検査結果は変わっていません。' &&
		ok "compare-violations は Gradle の [Incubating] の行を違反に数えない" || ng "compare-violations は Gradle の [Incubating] の行を違反に数えない" "st=${st} ${out}"
	# katachi の違反の見出しは全種類を拾う。種類は katachi/src/main の check パッケージの label から集める
	# （katachi が種類を足したのにキットが拾わなければ、ここで件数が合わなくなる）。KDoc の例の行（ * で始まる）は除く。
	labels=$(grep -rhoE '^[[:space:]]+override val label: String( get\(\))? = "[A-Za-z]+"' "${REPO}/katachi/src/main/kotlin/me/tbsten/katachi/check" | sed -E 's/.*"([A-Za-z]+)"$/\1/' | sort -u)
	n=0
	{
		printf 'ProjectArchitectureTest > the project layout matches the definition() FAILED\n'
		printf '    me.tbsten.katachi.check.KatachiArchitectureAssertionError: Katachi check failed: 99 violations\n'
		for l in ${labels}; do
			printf '\n    [%s] file:///x/%s.kt\n      text\n' "${l}" "${l}"
		done
		printf '\n[Incubating] Problems report is available at: file:///x/build/reports/problems/problems-report.html\n[Deprecated] Something else: file:///x/y\nBUILD FAILED in 19s\n'
	} >"${w}/tmp/test-after-refactor.log"
	for l in ${labels}; do [ "${l}" = MissingDescription ] || n=$((n + 1)); done
	out=$(cd "${d}" && "${KATACHI}" agent compare-violations 2>&1)
	st=$?
	if [ "${st}" -eq 1 ] && [ "${n}" -ge 10 ] && printf '%s\n' "${out}" | grep -q "^違反の見出し: 前 0 件 / 後 ${n} 件" &&
		printf '%s\n' "${out}" | grep -q '^\[MissingDescription\]（比べない）: 前 0 件 / 後 1 件' &&
		! printf '%s\n' "${out}" | grep -q 'Incubating\|Deprecated'; then
		ok "compare-violations は katachi の違反の見出しを全種類（${n} 種）拾い、ほかの [Xxx] の行は拾わない"
	else
		ng "compare-violations は katachi の違反の見出しを全種類拾い、ほかの [Xxx] の行は拾わない" "st=${st} n=${n} labels=$(echo ${labels}) ${out}"
	fi
}

# チェックリストとレポートの確認事項は id が重ならず、answers は id の側に書く（黙ってもう一方に入れない）
case_answers_namespaces() {
	d=$(inited ansns)
	add_q "${d}" Q1 rq1
	add_cq "${d}" C1 rc1
	ids="$(jget "${d}" check-list '" ".join(q.get("id") or "-" for q in d["questions"])') / $(jget "${d}" report '" ".join(q.get("id") or "-" for q in d["questions"])')"
	[ "${ids}" = "q1 / cq1" ] && ok "チェックリストの確認事項は q<n>、レポートの確認事項は cq<n> で採番する" ||
		ng "チェックリストの確認事項は q<n>、レポートの確認事項は cq<n> で採番する" "ids=${ids}"
	out=$(cd "${d}" && "${KATACHI}" agent answers set q1 "A-check" 2>&1)
	st=$?
	a="$(jget "${d}" check-list '(d.get("answers") or {}).get("q1", {}).get("answer")') / $(jget "${d}" report 'sorted((d.get("answers") or {}).keys())')"
	[ "${st}" -eq 0 ] && [ "${a}" = "A-check / []" ] && ok "answers set q1 は --target なしでもチェックリストに入れ、レポートに触らない" ||
		ng "answers set q1 は --target なしでもチェックリストに入れ、レポートに触らない" "st=${st} a=${a} ${out}"
	out=$(cd "${d}" && "${KATACHI}" agent answers set cq1 "A-report" 2>&1)
	st=$?
	a="$(jget "${d}" check-list 'sorted((d.get("answers") or {}).keys())') / $(jget "${d}" report '(d.get("answers") or {}).get("cq1", {}).get("answer")')"
	[ "${st}" -eq 0 ] && [ "${a}" = "['q1'] / A-report" ] && ok "answers set cq1 はレポートに入れ、チェックリストに触らない" ||
		ng "answers set cq1 はレポートに入れ、チェックリストに触らない" "st=${st} a=${a} ${out}"
	out=$(cd "${d}" && "${KATACHI}" agent answers set q1 "wrong" --target report 2>&1)
	st=$?
	a="$(jget "${d}" check-list '(d.get("answers") or {}).get("q1", {}).get("answer")') / $(jget "${d}" report 'sorted((d.get("answers") or {}).keys())')"
	[ "${st}" -ne 0 ] && [ "${a}" = "A-check / ['cq1']" ] && printf '%s\n' "${out}" | grep -q 'check-list' &&
		ok "answers set は id と --target が食い違えば止まり、どちらにも書かない" || ng "answers set は id と --target が食い違えば止まり、どちらにも書かない" "st=${st} a=${a} ${out}"
	printf '{"answers":{"q1":{"answer":"A-import","note":null}}}\n' >"${WORK}/ansns.json"
	out=$(cd "${d}" && "${KATACHI}" agent answers import "${WORK}/ansns.json" 2>&1)
	st=$?
	a="$(jget "${d}" check-list '(d.get("answers") or {}).get("q1", {}).get("answer")') / $(jget "${d}" report 'sorted((d.get("answers") or {}).keys())')"
	[ "${st}" -eq 0 ] && [ "${a}" = "A-import / ['cq1']" ] && ok "answers import は target の無い JSON を id の側に入れる" ||
		ng "answers import は target の無い JSON を id の側に入れる" "st=${st} a=${a} ${out}"
	out=$(cd "${d}" && "${KATACHI}" agent answers 2>&1)
	st=$?
	[ "${st}" -eq 0 ] && [ "$(printf '%s\n' "${out}" | grep -c '^対象: ')" -eq 2 ] && ok "answers（show）は --target なしならチェックリストとレポートの両方を出す" ||
		ng "answers（show）は --target なしならチェックリストとレポートの両方を出す" "st=${st} ${out}"
	out=$(cd "${d}" && "${KATACHI}" agent answers --target report 2>&1)
	st=$?
	[ "${st}" -eq 0 ] && [ "$(printf '%s\n' "${out}" | grep -c '^対象: ')" -eq 1 ] && printf '%s\n' "${out}" | grep -q '^対象: report' &&
		ok "answers --target report は show を省略してレポートだけを出す" || ng "answers --target report は show を省略してレポートだけを出す" "st=${st} ${out}"
	out=$(cd "${d}" && "${KATACHI}" agent summary 2>&1)
	printf '%s\n' "${out}" | grep -q 'レポート 回答済み 1 / 未回答 0、チェックリスト 回答済み 1 / 未回答 0' &&
		ok "summary はチェックリストの回答をチェックリストの回答済みに数える" || ng "summary はチェックリストの回答をチェックリストの回答済みに数える" "${out}"
	[ ! -d "$(lockdir "${d}")" ] && ok "answers のあとにロックが残らない" || ng "answers のあとにロックが残らない" "lock remains"
}

# test はいつも -Dkatachi.maxViolations=all を渡し、打ち切りのログは差分も compare-violations も比べない（11 件目以降を見逃さない）
case_test_truncated() {
	d=$(inited trunc)
	w=$(wd "${d}")
	fake_gradlew "${d}"
	mkdir -p "${d}/architecture-test/src/test/kotlin/fx"
	printf 'package fx\n' >"${d}/architecture-test/src/test/kotlin/fx/ProjectArchitecture.kt"
	printf '1\n' >"${d}/fake-exit"
	# 全件のログ（12 件）。12 件目だけを入れ替えた 2 本を作る
	full() {
		printf '> Task :architecture-test:test\nKatachi check failed: 12 violations (Unexpected: 12)\n'
		for n in 01 02 03 04 05 06 07 08 09 10 11; do printf '  [UnexpectedFile] file:///x/f%s.txt\n' "${n}"; done
		printf '  [UnexpectedFile] file:///x/%s.txt\nBUILD FAILED\n' "$1"
	}
	full zz-before >"${d}/fake-out.txt"
	out=$(cd "${d}" && "${KATACHI}" agent test --save before-refactor 2>&1)
	grep -q '^ARGS: .* -Dkatachi.maxViolations=all' "${w}/tmp/test/latest.log" && ok "test は -Dkatachi.maxViolations=all を Gradle に渡す" ||
		ng "test は -Dkatachi.maxViolations=all を Gradle に渡す" "$(head -n 1 "${w}/tmp/test/latest.log")"
	(cd "${d}" && "${KATACHI}" agent test -- -Dkatachi.maxViolations=3) >/dev/null 2>&1
	grep -q '^ARGS: .*-Dkatachi.maxViolations=3' "${w}/tmp/test/latest.log" && ! grep -q 'maxViolations=all' "${w}/tmp/test/latest.log" &&
		ok "test は -- の後ろの -Dkatachi.maxViolations を優先し、all を足さない" || ng "test は -- の後ろの -Dkatachi.maxViolations を優先し、all を足さない" "$(head -n 1 "${w}/tmp/test/latest.log")"
	full zz-after >"${d}/fake-out.txt"
	(cd "${d}" && "${KATACHI}" agent test --save after-refactor) >/dev/null 2>&1
	out=$(cd "${d}" && "${KATACHI}" agent compare-violations 2>&1)
	st=$?
	[ "${st}" -eq 1 ] && printf '%s\n' "${out}" | grep -q '増えた 1 件 / 減った 1 件' && printf '%s\n' "${out}" | grep -q 'zz-after' &&
		ok "compare-violations は全件のログなら 12 件目の入れ替わりを見つける" || ng "compare-violations は全件のログなら 12 件目の入れ替わりを見つける" "st=${st} ${out}"
	# 打ち切りのログ（先頭 10 件）。katachi の既定の形
	{
		printf '> Task :architecture-test:test\nKatachi check failed: 12 violations (Unexpected: 12)\n'
		for n in 01 02 03 04 05 06 07 08 09 10; do printf '  [UnexpectedFile] file:///x/f%s.txt\n' "${n}"; done
		printf '\n  Showing first 10 (2 more)\n    To show every block, run with -Dkatachi.maxViolations=all, or set maxViolations in assert().\nBUILD FAILED\n'
	} >"${d}/fake-out.txt"
	out=$(cd "${d}" && "${KATACHI}" agent test --save after-refactor 2>&1)
	printf '%s\n' "${out}" | grep -q '^前回との差分（previous.log 比）: 比べません' && ! printf '%s\n' "${out}" | grep -q '減った' &&
		printf '%s\n' "${out}" | grep -q '^省略: Showing first 10 (2 more)' &&
		ok "test は打ち切りのログと全件のログを比べない（偽の差分を出さない）" || ng "test は打ち切りのログと全件のログを比べない（偽の差分を出さない）" "${out}"
	out=$(cd "${d}" && "${KATACHI}" agent compare-violations 2>&1)
	st=$?
	[ "${st}" -eq 2 ] && printf '%s\n' "${out}" | grep -q '比べません' && printf '%s\n' "${out}" | grep -q 'test-after-refactor.log : Showing first 10 (2 more)' &&
		! printf '%s\n' "${out}" | grep -q '変わっていません' &&
		ok "compare-violations は打ち切りのログがあれば比べずに exit 2" || ng "compare-violations は打ち切りのログがあれば比べずに exit 2" "st=${st} ${out}"
}

# test は通ったときも警告の件数と見出しを要約に出す
case_test_warnings() {
	d=$(inited warns)
	fake_gradlew "${d}"
	mkdir -p "${d}/architecture-test/src/test/kotlin/fx"
	printf 'package fx\n' >"${d}/architecture-test/src/test/kotlin/fx/ProjectArchitecture.kt"
	printf '0\n' >"${d}/fake-exit"
	printf '> Task :architecture-test:test\n\nKatachi check found 2 warnings. Warnings never fail the check.\n\n[AmbiguousLayout] file:///x/Diagnostic.kt\n  text\n\n[MissingDescription] file:///x/Role.kt\n  text\n\nBUILD SUCCESSFUL in 3s\n' >"${d}/fake-out.txt"
	out=$(cd "${d}" && "${KATACHI}" agent test 2>&1)
	st=$?
	[ "${st}" -eq 0 ] && printf '%s\n' "${out}" | grep -q '^結果: 検査は通りました（違反 0 件）' && printf '%s\n' "${out}" | grep -q '^警告: 2 件' &&
		printf '%s\n' "${out}" | grep -q '^  \[AmbiguousLayout\] file:///x/Diagnostic.kt' && ! printf '%s\n' "${out}" | grep -q '^  \[MissingDescription\]' &&
		ok "test は通ったときも警告の件数と見出しを出す" || ng "test は通ったときも警告の件数と見出しを出す" "st=${st} ${out}"
}

# init と doctor は Kotlin 2.2 未満を警告し（Gradle の版も見る）、init の「次:」をユーザへの確認に差し替える
case_kotlin_too_old() {
	d=$(fresh oldkt)
	sed -i.orig 's/version "[^"]*"/version "1.9.10"/' "${d}/build.gradle.kts" && rm -f "${d}/build.gradle.kts.orig"
	mkdir -p "${d}/gradle/wrapper"
	printf 'distributionUrl=https\\://services.gradle.org/distributions/gradle-7.5.1-bin.zip\n' >"${d}/gradle/wrapper/gradle-wrapper.properties"
	out=$(cd "${d}" && "${KATACHI}" doctor 2>&1)
	st=$?
	[ "${st}" -eq 0 ] && printf '%s\n' "${out}" | grep -q 'Kotlin は 1.9.10 です' && printf '%s\n' "${out}" | grep -q 'Gradle も上げる必要があります.*7.5.1' &&
		ok "doctor は Kotlin 2.2 未満を警告し、Gradle 7.6.3 未満なら Gradle も上げると言う" || ng "doctor は Kotlin 2.2 未満を警告し、Gradle 7.6.3 未満なら Gradle も上げると言う" "st=${st} ${out}"
	out=$(cd "${d}" && "${KATACHI}" agent init --katachi 0.3.0 --workdir .local/install-katachi 2>&1)
	st=$?
	[ "${st}" -eq 0 ] && printf '%s\n' "${out}" | grep -q 'ユーザに確認してください' && printf '%s\n' "${out}" | grep -q '同意なしに上げないこと' &&
		printf '%s\n' "${out}" | tail -n 1 | grep -q '^次: Kotlin 1.9.10 を上げてよいかをユーザに確認してください' &&
		ok "init は Kotlin 2.2 未満を警告し、次の行をユーザへの確認にする" || ng "init は Kotlin 2.2 未満を警告し、次の行をユーザへの確認にする" "st=${st} ${out}"
	# 上げてから init し直すと、チェックリストの Kotlin の記録も新しい版になる
	sed -i.orig "s/version \"1.9.10\"/version \"${K}\"/" "${d}/build.gradle.kts" && rm -f "${d}/build.gradle.kts.orig"
	out=$(cd "${d}" && "${KATACHI}" agent init --katachi 0.3.0 --workdir .local/install-katachi 2>&1)
	m=$(cd "${d}" && "${KATACHI}" agent data get check-list meta.kotlin 2>/dev/null)
	[ "${m}" = "${K}" ] && printf '%s\n' "${out}" | tail -n 1 | grep -q "^次: katachi agent check 0-1 0-2" &&
		ok "init のやり直しは meta.kotlin を新しい版にし、次は check 0-1 0-2" || ng "init のやり直しは meta.kotlin を新しい版にし、次は check 0-1 0-2" "meta.kotlin=${m} $(printf '%s\n' "${out}" | tail -n 1)"
}

# add changed は記録済みのパスなら何もせず 0 で終わる（記録し直さなくてよい）
case_add_changed_dup() {
	d=$(inited chg)
	(cd "${d}" && "${KATACHI}" agent add changed --path a.kts --change "新規" --summary s1) >/dev/null 2>&1
	out=$(cd "${d}" && "${KATACHI}" agent add changed --path a.kts --change "変更" --summary s2 2>&1)
	st=$?
	n=$(jget "${d}" check-list '(len(d["changedFiles"]), d["changedFiles"][0]["summary"])')
	[ "${st}" -eq 0 ] && [ "${n}" = "(1, 's1')" ] && printf '%s\n' "${out}" | grep -q '記録済みです: a.kts' && [ ! -d "$(lockdir "${d}")" ] &&
		ok "add changed は記録済みのパスなら何もせず 0 で終わる" || ng "add changed は記録済みのパスなら何もせず 0 で終わる" "st=${st} n=${n} ${out}"
}

# docs --topic の見出しが全文に無いのは、katachi コマンドの不具合として止まる（内部の関数名で直し方を言わない）
case_docs_topic_kit_bug() {
	s2="${WORK}/site-renamed"
	rm -rf "${s2}"
	mkdir -p "${s2}"
	cp -R "${SITE}/install" "${s2}/install"
	sed 's/^# Layout$/# Layout (renamed)/' "${SITE}/llms-full.txt" >"${s2}/llms-full.txt"
	d=$(fresh topicbug)
	(cd "${d}" && KATACHI_DOCS="file://${s2}" "${KATACHI}" agent init --katachi 0.3.0 --workdir .local/install-katachi) >/dev/null 2>&1
	# スモークは KATACHI_DOCS を export しているので（init の記録より環境変数が勝つ）、こちらにも付ける
	out=$(cd "${d}" && KATACHI_DOCS="file://${s2}" "${KATACHI}" agent docs --topic layout 2>&1)
	st=$?
	[ "${st}" -eq 1 ] && printf '%s\n' "${out}" | grep -q 'katachi コマンドの不具合です' && printf '%s\n' "${out}" | grep -q 'ユーザにこの出力を伝えたうえで' &&
		! printf '%s\n' "${out}" | grep -q 'docs_topic_title' &&
		ok "docs --topic は見出しが無ければ katachi コマンドの不具合として止まり、関数名を言わない" || ng "docs --topic は見出しが無ければ katachi コマンドの不具合として止まり、関数名を言わない" "st=${st} ${out}"
}

# ---- 実行 ----------------------------------------------------------------------------------

mkdir -p "${WORK}"
# katachi コマンドが今のリポジトリの版か（古い build や brew の別の版を回していないか）
v=$("${KATACHI}" --version 2>&1)
st=$?
[ "${st}" -eq 0 ] && printf '%s\n' "${v}" | grep -qF "${VERSION}" && ok "katachi --version はリポジトリの版（${VERSION}）" ||
	ng "katachi --version はリポジトリの版（${VERSION}）" "st=${st} ${v}"
# 起動（kotlin と JVM）にかかる秒数。時間を測るケース（lock_*）はこれを足して見る。1 回目はスクリプトのコンパイルが入るので 2 回目で測る
t0=$(date +%s)
"${KATACHI}" --version >/dev/null 2>&1
t1=$(date +%s)
BOOT=$((t1 - t0))

build_site
run_case guide_toc
run_case guide_fence
run_case guide_next
run_case check_next
run_case docs_topic
run_case preferences
run_case add_question_ids
run_case answers_import
run_case summary_answers
run_case summary_resume
run_case add_parallel
run_case add_count_mismatch
run_case lock_wait
run_case lock_stale_pid
run_case lock_stale_no_pid
run_case lock_arbitrary_path
run_case init_workdir_existing
run_case init_module_dir
run_case lint_without_definition
run_case lint_checks
run_case add_rejects
run_case scaffold_deps_conflict
run_case scaffold_oneline_plugin_management
run_case test
run_case data_get_key
run_case check_result_headings
run_case answers_namespaces
run_case test_truncated
run_case test_warnings
run_case kotlin_too_old
run_case add_changed_dup
run_case docs_topic_kit_bug

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
