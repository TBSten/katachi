#!/bin/sh
# インストールキットのスモーク（Gradle を起動しない）。check-install-kit.sh（fixture で Gradle まで回す）の手前で、
# docs/public/install/katachi-install.sh のサブコマンドを合成の Gradle プロジェクトに当てて、終了コードと出力の grep で
# 確かめる。1 ケース 1 関数。ケース名は .local の設計（install kit 改善）の「テスト名」に合わせてコメントに書く。
#
# 配信元は <work>/site に組む（docs/public/install の ja / en と、docs/dist/llms-full.txt）。init は --offline ではなく
# 通常の init（file:// から取る）。所要 3〜5 分。
#
# 使い方（リポジトリの直下で）:
#   sh .claude/skills/prerelease/scripts/check-install-kit-smoke.sh
#   sh .claude/skills/prerelease/scripts/check-install-kit-smoke.sh --only guide_toc,lock_wait
#   KSH=dash sh .claude/skills/prerelease/scripts/check-install-kit-smoke.sh        # kit を別のシェルで動かす
#   sh .claude/skills/prerelease/scripts/check-install-kit-smoke.sh --llms-full /path/to/llms-full.txt
#
#   --work       作業場所（既定 .local/tmp/install-kit-smoke。毎回作り直す）
#   --only       走らせるケース（カンマ区切り。名前は case_<名前> の <名前>）
#   --llms-full  docs --topic が読む llms-full.txt（既定 docs/dist/llms-full.txt。無ければ docs で pnpm run build を試す）
#
# 終了コード: 全部 OK なら 0、1 つでも NG なら 1、引数の誤りは 2。
# 決まり: $VAR の直後に日本語を置かない（${VAR} で閉じる。bash 3.2 が食う）。
set -u

usage() {
	cat <<'EOF'
使い方: sh check-install-kit-smoke.sh [--work <dir>] [--only <case>,...] [--llms-full <path>]
  --work       作業場所（既定 .local/tmp/install-kit-smoke）
  --only       走らせるケース（カンマ区切り）
  --llms-full  docs --topic が読む llms-full.txt（既定 docs/dist/llms-full.txt）
EOF
}

WORK=""
ONLY=""
LLMS=""
while [ $# -gt 0 ]; do
	case "$1" in
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
KIT="${REPO}/docs/public/install/katachi-install.sh"
SITE="${WORK}/site"
KSH="${KSH:-sh}"
K=$(sed -n 's/^kotlin[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "${REPO}/gradle/libs.versions.toml" | head -1)
[ -n "${K}" ] || K="2.2.0"
export KATACHI_DOCS="file://${SITE}"
export KATACHI_LANG=ja
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
	if ! (cd "${_d}" && ${KSH} "${KIT}" init --katachi 0.3.0 --workdir .local/install-katachi "$@") >"${WORK}/${_n}.init.log" 2>&1; then
		printf 'init failed (%s): %s\n' "${_n}" "$(tail -3 "${WORK}/${_n}.init.log")" >&2
	fi
	printf '%s\n' "${_d}"
}

cli() { printf '%s/.local/install-katachi/tmp/katachi-install.sh\n' "$1"; }
wd() { printf '%s/.local/install-katachi\n' "$1"; }
lockdir() { printf '%s/.local/install-katachi/tmp/cache/.lock\n' "$1"; }

# $1 = プロジェクト, $2 = 対象（report / check-list）, $3 = python の式（d が JSON）
jget() {
	${KSH} "$(cli "$1")" data get "$2" 2>/dev/null | python3 -c 'import json,sys; d=json.load(sys.stdin); print('"$3"')'
}

add_cq() { (cd "$1" && ${KSH} "$(cli "$1")" add codebase-question --question "$2" --observed o --recommendation "$3") >/dev/null 2>&1; }
add_q() { (cd "$1" && ${KSH} "$(cli "$1")" add question --question "$2" --observed o --recommendation "$3") >/dev/null 2>&1; }

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

# guide は目次に全節の ID を順に出す
case_guide_toc() {
	d=$(inited guide)
	out=$(${KSH} "$(cli "${d}")" guide 2>"${WORK}/guide.toc.err")
	ids=$(printf '%s\n' "${out}" | awk -F'\t' 'NF == 3 { printf "%s ", $1 }')
	want_ids="0 0-1 0-2 0-3 1 1-1 1-2 1-3 2 2-1 2-2 3 3-1 3-2 3-3 4 4-1 4-2 4-3 5 6 6-0 6-A 6-B 6-C 6-D "
	[ "${ids}" = "${want_ids}" ] && ok "guide は目次に全節の ID を順に出す" || ng "guide は目次に全節の ID を順に出す" "ids=${ids}"
	# 手順書冒頭の目次（- <ID> — <見出し>）と同じ並び
	toc_ids=$(sed -n '/^\*\*目次\*\*/,/^## 0\./p' "${SITE}/install/ja/index.md" | sed -n 's/^[[:space:]]*- \([0-9][-0-9A-D]*\) — .*/\1 /p' | tr -d '\n')
	[ "${toc_ids}" = "${ids}" ] && ok "guide の目次は手順書冒頭の目次と一致する" || ng "guide の目次は手順書冒頭の目次と一致する" "toc=${toc_ids}"
	printf '%s\n' "${out}" | tail -n 1 | grep -q '6-A.*6-D.*6-1.*6-4' && ok "guide の目次の末尾に 6-1〜6-4 の案内" || ng "guide の目次の末尾に 6-1〜6-4 の案内" "$(printf '%s\n' "${out}" | tail -n 1)"
	# en でも同じ ID の列
	d_en=$(inited guide-en --lang en)
	ids_en=$(${KSH} "$(cli "${d_en}")" guide 2>/dev/null | awk -F'\t' 'NF == 3 { printf "%s ", $1 }')
	[ "${ids_en}" = "${want_ids}" ] && ok "guide の目次は --lang en でも同じ ID の列" || ng "guide の目次は --lang en でも同じ ID の列" "ids=${ids_en}"
}

# guide はコードフェンスの中の # を見出しに数えない
case_guide_fence() {
	d="${WORK}/guide"
	[ -d "${d}" ] || d=$(inited guide)
	out=$(${KSH} "$(cli "${d}")" guide 0-1 2>&1)
	st=$?
	if [ "${st}" -eq 0 ] && printf '%s\n' "${out}" | grep -q '^# 1\. ネットワーク' && ! printf '%s\n' "${out}" | grep -q '^### 0-2\.'; then
		ok "guide はコードフェンスの中の # を見出しに数えない"
	else
		ng "guide はコードフェンスの中の # を見出しに数えない" "st=${st} $(printf '%s\n' "${out}" | head -n 3)"
	fi
	printf '%s\n' "${out}" | head -n 1 | grep -q '^### 0-1\.' && ok "guide <節> は見出し行から始まる" || ng "guide <節> は見出し行から始まる" "$(printf '%s\n' "${out}" | head -n 1)"
}

# guide <節> は次の節のコマンドで終わる
case_guide_next() {
	d="${WORK}/guide"
	[ -d "${d}" ] || d=$(inited guide)
	c=$(cli "${d}")
	last=$(${KSH} "${c}" guide 3-2 2>&1 | tail -n 1)
	[ "${last}" = "次: sh ${c} guide 3-3" ] && ok "guide <節> は次の節のコマンドで終わる" || ng "guide <節> は次の節のコマンドで終わる" "last=${last}"
	last=$(${KSH} "${c}" guide 6-D 2>&1 | tail -n 1)
	case "${last}" in
	次:*) ng "guide の最後の節には次が付かない" "last=${last}" ;;
	*) ok "guide の最後の節には次が付かない" ;;
	esac
	a=$(${KSH} "${c}" guide 6-1 2>&1)
	b=$(${KSH} "${c}" guide 6-A 2>&1)
	[ -n "${a}" ] && [ "${a}" = "${b}" ] && ok "guide 6-1 は 6-A に読み替える" || ng "guide 6-1 は 6-A に読み替える" "$(printf '%s\n' "${a}" | head -n 2)"
	out=$(${KSH} "${c}" guide 9-9 2>&1)
	st=$?
	[ "${st}" -eq 1 ] && printf '%s\n' "${out}" | grep -q '^0-1	' && ok "guide の知らない ID は目次を出して exit 1" || ng "guide の知らない ID は目次を出して exit 1" "st=${st}"
}

# check の次の節は 5-1 と 5-2 を guide 5 に読み替える
case_check_next() {
	d=$(inited chk)
	c=$(cli "${d}")
	out=$(${KSH} "${c}" check 0-1 2>&1)
	printf '%s\n' "${out}" | grep -q "^次: sh ${c} guide 0-2\$" && ok "check の次は未完了の最初の項目の節" || ng "check の次は未完了の最初の項目の節" "${out}"
	${KSH} "${c}" check 0-2 1-1 1-2 1-3 2-1 2-2 3-1 3-2 3-3 >/dev/null 2>&1
	out=$(${KSH} "${c}" check 4-1 4-2 4-3 2>&1)
	printf '%s\n' "${out}" | grep -q "^次: sh ${c} guide 5\$" && ok "check の次の節は 5-1 と 5-2 を guide 5 に読み替える" || ng "check の次の節は 5-1 と 5-2 を guide 5 に読み替える" "${out}"
	out=$(${KSH} "${c}" check 5-1 5-2 2>&1)
	printf '%s\n' "${out}" | grep -q '^次:' && ng "check は未完了が無ければ次を出さない（6-x は数えない）" "${out}" || ok "check は未完了が無ければ次を出さない（6-x は数えない）"
}

# docs --topic layout は古い版の写しを含めない / docs --topics は対応表を出す
case_docs_topic() {
	d="${WORK}/guide"
	[ -d "${d}" ] || d=$(inited guide)
	c=$(cli "${d}")
	p=$(${KSH} "${c}" docs --topic layout 2>"${WORK}/topic.err")
	st=$?
	if [ "${st}" -eq 0 ] && [ -s "${p}" ]; then
		ok "docs --topic は保存先のパスを出す"
	else
		ng "docs --topic は保存先のパスを出す" "st=${st} p=${p} $(cat "${WORK}/topic.err")"
		return
	fi
	head -n 1 "${p}" | grep -q '^# Enforce directory structure' && ok "docs --topic layout はページの見出しから始まる" || ng "docs --topic layout はページの見出しから始まる" "$(head -n 1 "${p}")"
	n=$(awk '/^```/ { c = !c; next } !c && /^# /' "${p}" | wc -l | tr -d ' ')
	[ "${n}" = "1" ] && ok "docs --topic の # の見出しは 1 つ" || ng "docs --topic の # の見出しは 1 つ" "n=${n}"
	grep -q 'Roadmap' "${p}" && ng "docs --topic layout は古い版の写しを含めない" "Roadmap が含まれる" || ok "docs --topic layout は古い版の写しを含めない"
	out=$(${KSH} "${c}" docs --topics 2>&1)
	st=$?
	if [ "${st}" -eq 0 ] && printf '%s\n' "${out}" | grep -q '^layout	' && printf '%s\n' "${out}" | grep -q '^role	'; then
		ok "docs --topics は対応表を出す"
	else
		ng "docs --topics は対応表を出す" "st=${st} $(printf '%s\n' "${out}" | head -n 3)"
	fi
	out=$(${KSH} "${c}" docs --topic nope 2>&1)
	st=$?
	[ "${st}" -eq 1 ] && printf '%s\n' "${out}" | grep -q 'layout' && ok "docs --topic の知らない名前は一覧を出して止まる" || ng "docs --topic の知らない名前は一覧を出して止まる" "st=${st}"
}

# preferences set は知らないキーで止まる / preferences set moduleDir は meta.moduleDir に入る
case_preferences() {
	d=$(inited pref)
	c=$(cli "${d}")
	(cd "${d}" && ${KSH} "${c}" preferences) >"${WORK}/pref.show.log" 2>&1
	st=$?
	if [ "${st}" -eq 0 ] && grep -q '^nestedGroups = yes（推奨）' "${WORK}/pref.show.log" && grep -q '^moduleDir = architecture-test（記録済み）' "${WORK}/pref.show.log" &&
		grep -q '^testFramework = junit5（推奨）' "${WORK}/pref.show.log" && grep -q '^dependencyStyle = literal（推奨）' "${WORK}/pref.show.log" &&
		grep -q '^constraintNames = plain（推奨）' "${WORK}/pref.show.log" && grep -q '^oneRolePerFile = yes（推奨）' "${WORK}/pref.show.log"; then
		ok "preferences は 6 つのキーを推奨つきで出す"
	else
		ng "preferences は 6 つのキーを推奨つきで出す" "st=${st} $(cat "${WORK}/pref.show.log")"
	fi
	(cd "${d}" && ${KSH} "${c}" preferences set nestedGroups=maybe) >"${WORK}/pref.bad.log" 2>&1
	st=$?
	[ "${st}" -eq 1 ] && grep -q 'maybe' "${WORK}/pref.bad.log" && grep -q 'yes' "${WORK}/pref.bad.log" &&
		ok "preferences set は知らない値で止まり使える値を並べる" || ng "preferences set は知らない値で止まり使える値を並べる" "st=${st} $(cat "${WORK}/pref.bad.log")"
	(cd "${d}" && ${KSH} "${c}" preferences set foo=bar) >"${WORK}/pref.bad2.log" 2>&1
	st=$?
	[ "${st}" -eq 1 ] && grep -q 'foo' "${WORK}/pref.bad2.log" && ok "preferences set は知らないキーで止まる" || ng "preferences set は知らないキーで止まる" "st=${st} $(cat "${WORK}/pref.bad2.log")"
	(cd "${d}" && ${KSH} "${c}" preferences set moduleDir=a/b nestedGroups=no) >"${WORK}/pref.set.log" 2>&1
	st=$?
	m=$(${KSH} "${c}" data get check-list meta.moduleDir 2>/dev/null)
	n=$(jget "${d}" check-list '(d.get("preferences") or {}).get("nestedGroups")')
	[ "${st}" -eq 0 ] && [ "${m}" = "a/b" ] && [ "${n}" = "no" ] && ok "preferences set moduleDir は meta.moduleDir に入る" ||
		ng "preferences set moduleDir は meta.moduleDir に入る" "st=${st} meta.moduleDir=${m} nestedGroups=${n} $(cat "${WORK}/pref.set.log")"
	[ ! -d "$(lockdir "${d}")" ] && ok "preferences set のあとにロックが残らない" || ng "preferences set のあとにロックが残らない" "lock remains"
}

# add question は id を q1 から順に採番する
case_add_question_ids() {
	d=$(inited qid)
	add_cq "${d}" C1 r1
	add_cq "${d}" C2 r2
	add_cq "${d}" C3 r3
	ids=$(jget "${d}" report '" ".join(q.get("id") or "-" for q in d["questions"])')
	[ "${ids}" = "q1 q2 q3" ] && ok "add question は id を q1 から順に採番する" || ng "add question は id を q1 から順に採番する" "ids=${ids}"
}

# answers import は無い id を飛ばして件数を出す
case_answers_import() {
	d=$(inited ans)
	c=$(cli "${d}")
	add_cq "${d}" C1 r1
	add_cq "${d}" C2 r2
	add_cq "${d}" C3 r3
	printf '{"target":"report","answers":{"q2":{"answer":"A2","note":null},"q9":{"answer":"nope"}}}\n' >"${WORK}/ans.json"
	(cd "${d}" && ${KSH} "${c}" answers import "${WORK}/ans.json") >"${WORK}/ans.import.log" 2>&1
	st=$?
	[ "${st}" -eq 0 ] && grep -q '取り込みました: 回答 1 件（未回答 2 件）' "${WORK}/ans.import.log" && grep -q 'q9' "${WORK}/ans.import.log" &&
		ok "answers import は無い id を飛ばして件数を出す" || ng "answers import は無い id を飛ばして件数を出す" "st=${st} $(cat "${WORK}/ans.import.log")"
	a=$(jget "${d}" report '(d["answers"]["q2"]["answer"], bool(d["answers"]["q2"].get("answeredAt")), "q9" in d["answers"])')
	[ "${a}" = "('A2', True, False)" ] && ok "answers import は answers に answeredAt つきで入れ、無い id は入れない" || ng "answers import は answers に answeredAt つきで入れ、無い id は入れない" "a=${a}"
	[ ! -d "$(lockdir "${d}")" ] && ok "answers import のあとにロックが残らない" || ng "answers import のあとにロックが残らない" "lock remains"
}

# summary は回答済みを件数だけ、未回答だけ列挙する
case_summary_answers() {
	d="${WORK}/ans"
	[ -d "${d}" ] || case_answers_import
	c=$(cli "${d}")
	# 未完了形: 再開の欄に件数だけ
	(cd "${d}" && ${KSH} "${c}" summary) >"${WORK}/ans.summary.log" 2>&1
	st=$?
	[ "${st}" -eq 0 ] && grep -q '^❌' "${WORK}/ans.summary.log" && grep -q 'レポート 回答済み 1 / 未回答 2' "${WORK}/ans.summary.log" && ! grep -q 'C1' "${WORK}/ans.summary.log" &&
		ok "summary の未完了形は確認事項を件数だけ出す" || ng "summary の未完了形は確認事項を件数だけ出す" "st=${st} $(cat "${WORK}/ans.summary.log")"
	# 完了形: 回答済みは件数、未回答だけ id / 問い / 推奨
	ids=$(${KSH} "${c}" data get check-list 2>/dev/null | python3 -c 'import json,sys; print(" ".join(i["id"] for s in json.load(sys.stdin)["steps"] for i in s["items"]))')
	(cd "${d}" && ${KSH} "${c}" check ${ids}) >/dev/null 2>&1
	(cd "${d}" && ${KSH} "${c}" summary) >"${WORK}/ans.summary2.log" 2>&1
	st=$?
	if [ "${st}" -eq 0 ] && grep -q '^✅' "${WORK}/ans.summary2.log" && grep -q 'レポート.*回答済み 1 件.*未回答 2 件' "${WORK}/ans.summary2.log" &&
		grep -q -- '- q1 C1 → 推奨: r1' "${WORK}/ans.summary2.log" && grep -q -- '- q3 C3 → 推奨: r3' "${WORK}/ans.summary2.log" && ! grep -q 'C2' "${WORK}/ans.summary2.log" &&
		! grep -q '^再開:' "${WORK}/ans.summary2.log"; then
		ok "summary は回答済みを件数だけ、未回答だけ列挙する"
	else
		ng "summary は回答済みを件数だけ、未回答だけ列挙する" "st=${st} $(cat "${WORK}/ans.summary2.log")"
	fi
}

# summary は scaffold 前でも止まらず再開の欄に「まだ無い」を出す
case_summary_resume() {
	d=$(inited resume --module-dir static-analysis/katachi)
	c=$(cli "${d}")
	${KSH} "${c}" check 0-1 0-2 1-1 >/dev/null 2>&1
	(cd "${d}" && ${KSH} "${c}" summary) >"${WORK}/resume.log" 2>&1
	st=$?
	if [ "${st}" -eq 0 ] && grep -q '^再開:' "${WORK}/resume.log" && grep -q '終わった項目: 0-1 0-2 1-1$' "${WORK}/resume.log" &&
		grep -q "次の節: sh ${c} guide 1-2" "${WORK}/resume.log" &&
		grep -q '定義: まだ無い（scaffold 未実行。meta.moduleDir = static-analysis/katachi）' "${WORK}/resume.log"; then
		ok "summary は scaffold 前でも止まらず再開の欄に「まだ無い」を出す"
	else
		ng "summary は scaffold 前でも止まらず再開の欄に「まだ無い」を出す" "st=${st} $(cat "${WORK}/resume.log")"
	fi
}

# add を 8 並列で走らせても 8 件とも残る
case_add_parallel() {
	d=$(inited par)
	c=$(cli "${d}")
	i=1
	while [ "${i}" -le 8 ]; do
		(cd "${d}" && ${KSH} "${c}" add role --name "r${i}" --importance "${i}" --layout "l${i}" --naming n --group g) >"${WORK}/par.${i}.log" 2>&1 &
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
	c=$(cli "${d}")
	add_cq "${d}" C1 r1
	add_cq "${d}" C2 r2
	add_cq "${d}" C3 r3
	(cd "${d}" && KATACHI_ADD_EXPECT=0 ${KSH} "${c}" add codebase-question --question C4 --observed o --recommendation r4) >"${WORK}/cnt.log" 2>&1
	st=$?
	[ "${st}" -eq 1 ] && grep -q '件数が合いません' "${WORK}/cnt.log" && grep -q '書き込み前 0 件、追記後 4 件' "${WORK}/cnt.log" &&
		ok "add は件数が合わなければ止まる" || ng "add は件数が合わなければ止まる" "st=${st} $(cat "${WORK}/cnt.log")"
	[ ! -d "$(lockdir "${d}")" ] && ok "件数検査で止まってもロックが残らない" || ng "件数検査で止まってもロックが残らない" "lock remains"
}

# lock は待ち時間を過ぎたら止まる
case_lock_wait() {
	d=$(inited lockwait)
	c=$(cli "${d}")
	lock=$(lockdir "${d}")
	mkdir -p "${lock}"
	printf '%s' "$$" >"${lock}/pid"
	start=$(date +%s)
	(cd "${d}" && KATACHI_LOCK_WAIT=2 ${KSH} "${c}" add role --name r --importance 1 --layout l --naming n --group g) >"${WORK}/lockwait.log" 2>&1
	st=$?
	end=$(date +%s)
	el=$((end - start))
	[ "${st}" -eq 1 ] && grep -q "${lock}" "${WORK}/lockwait.log" && [ "${el}" -ge 2 ] && [ "${el}" -le 6 ] &&
		ok "lock は待ち時間を過ぎたら止まる（文面に lock のパス）" || ng "lock は待ち時間を過ぎたら止まる（文面に lock のパス）" "st=${st} elapsed=${el} $(cat "${WORK}/lockwait.log")"
	[ -d "${lock}" ] && ok "待って止まったときは他人のロックを消さない" || ng "待って止まったときは他人のロックを消さない" "lock removed"
	rm -rf "${lock}"
}

# lock は死んだ pid の残骸を消して取り直す
case_lock_stale_pid() {
	d=$(inited lockpid)
	c=$(cli "${d}")
	lock=$(lockdir "${d}")
	mkdir -p "${lock}"
	printf '999999' >"${lock}/pid"
	start=$(date +%s)
	(cd "${d}" && KATACHI_LOCK_WAIT=2 ${KSH} "${c}" add role --name r --importance 1 --layout l --naming n --group g) >"${WORK}/lockpid.log" 2>&1
	st=$?
	end=$(date +%s)
	el=$((end - start))
	[ "${st}" -eq 0 ] && [ "$(jget "${d}" report 'len(d["roles"])')" = "1" ] && [ ! -d "${lock}" ] && [ "${el}" -le 1 ] &&
		ok "lock は死んだ pid の残骸を待たずに消して取り直す" || ng "lock は死んだ pid の残骸を待たずに消して取り直す" "st=${st} elapsed=${el} $(cat "${WORK}/lockpid.log")"
}

# lock は pid の無い古い残骸を消して取り直す
case_lock_stale_no_pid() {
	d=$(inited locknopid)
	c=$(cli "${d}")
	lock=$(lockdir "${d}")
	mkdir -p "${lock}"
	two_min_ago=$(python3 -c 'import time; print(time.strftime("%Y%m%d%H%M", time.localtime(time.time() - 120)))')
	touch -t "${two_min_ago}" "${lock}"
	(cd "${d}" && KATACHI_LOCK_WAIT=2 ${KSH} "${c}" add role --name r --importance 1 --layout l --naming n --group g) >"${WORK}/locknopid.log" 2>&1
	st=$?
	[ "${st}" -eq 0 ] && [ "$(jget "${d}" report 'len(d["roles"])')" = "1" ] && [ ! -d "${lock}" ] &&
		ok "lock は pid の無い古い残骸を消して取り直す" || ng "lock は pid の無い古い残骸を消して取り直す" "st=${st} $(cat "${WORK}/locknopid.log")"
	mkdir -p "${lock}"
	(cd "${d}" && KATACHI_LOCK_WAIT=2 ${KSH} "${c}" add role --name r2 --importance 1 --layout l --naming n --group g) >"${WORK}/locknopid2.log" 2>&1
	st=$?
	[ "${st}" -eq 1 ] && [ -d "${lock}" ] && ok "lock は pid の無い新しいロックを書き込み中とみなして待つ" ||
		ng "lock は pid の無い新しいロックを書き込み中とみなして待つ" "st=${st} $(cat "${WORK}/locknopid2.log")"
	rm -rf "${lock}"
}

# data set の任意のパスは対象の隣にロックを作る
case_lock_arbitrary_path() {
	d=$(inited lockany)
	c=$(cli "${d}")
	mkdir -p "${WORK}/lockany-scratch"
	x="${WORK}/lockany-scratch/x.json"
	cp "$(wd "${d}")/project-code-base-report.html" "${x}"
	printf '{"a": 1}\n' >"${WORK}/lockany-scratch/in.json"
	mkdir -p "${x}.lock"
	printf '%s' "$$" >"${x}.lock/pid"
	(cd "${d}" && KATACHI_LOCK_WAIT=2 ${KSH} "${c}" data set "${x}" "${WORK}/lockany-scratch/in.json") >"${WORK}/lockany.log" 2>&1
	st=$?
	[ "${st}" -eq 1 ] && grep -q 'x\.json\.lock' "${WORK}/lockany.log" && ! grep -q 'tmp/cache/\.lock' "${WORK}/lockany.log" &&
		ok "data set の任意のパスは対象の隣にロックを作る" || ng "data set の任意のパスは対象の隣にロックを作る" "st=${st} $(cat "${WORK}/lockany.log")"
	rm -r "${x}.lock"
	(cd "${d}" && ${KSH} "${c}" data set "${x}" "${WORK}/lockany-scratch/in.json") >"${WORK}/lockany2.log" 2>&1
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
	(cd "${d}" && ${KSH} "${KIT}" init --katachi 0.3.0 --workdir .local/install-katachi) >"${WORK}/initwd.log" 2>&1
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
	m=$(${KSH} "$(cli "${d}")" data get check-list meta.moduleDir 2>/dev/null)
	[ "${m}" = "static-analysis/katachi" ] && ok "init --module-dir が meta.moduleDir に入る" || ng "init --module-dir が meta.moduleDir に入る" "meta.moduleDir=${m}"
	d2=$(fresh initbad)
	(cd "${d2}" && ${KSH} "${KIT}" init --katachi 0.3.0 --workdir .local/install-katachi --module-dir ../up) >"${WORK}/initbad.log" 2>&1
	st=$?
	[ "${st}" -eq 1 ] && grep -q 'Gradle ルートからの相対パス' "${WORK}/initbad.log" && ok "init --module-dir は不正な値で止まる" || ng "init --module-dir は不正な値で止まる" "st=${st} $(tail -2 "${WORK}/initbad.log")"
}

# lint は定義ディレクトリが無ければ exit 1
case_lint_without_definition() {
	d=$(inited lintno --module-dir static-analysis/katachi)
	c=$(cli "${d}")
	(cd "${d}" && ${KSH} "${c}" lint) >"${WORK}/lintno.log" 2>&1
	st=$?
	[ "${st}" -eq 1 ] && grep -q 'preferences set moduleDir' "${WORK}/lintno.log" && grep -q 'static-analysis/katachi' "${WORK}/lintno.log" &&
		ok "lint は定義ディレクトリが無ければ exit 1" || ng "lint は定義ディレクトリが無ければ exit 1" "st=${st} $(cat "${WORK}/lintno.log")"
}

# lint は制約の無い手書き Gradle Role を gradle() の 1 行に置き換えるよう指摘する
# lint は制約を持つ手書き Gradle Role に、置いたファイルに合う hook への移し方を出す
# lint は平たい group と分けていないファイルと documented の候補を出す
case_lint_checks() {
	d=$(inited lint)
	c=$(cli "${d}")
	(cd "${d}" && ${KSH} "${c}" scaffold --package com.example.app) >"${WORK}/lint.scaffold.log" 2>&1 || {
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
	(cd "${d}" && ${KSH} "${c}" lint) >"${WORK}/lint.log" 2>&1
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
	c=$(cli "${d}")
	(cd "${d}" && ${KSH} "${c}" add role --name r --importance 1 --layout app/build.gradle.kts --naming n --group g) >"${WORK}/rej.role.log" 2>&1
	st=$?
	[ "${st}" -eq 1 ] && grep -q 'gradle() が受け持ちます' "${WORK}/rej.role.log" && ok "add role は Gradle のビルドファイルを拒む" ||
		ng "add role は Gradle のビルドファイルを拒む" "st=${st} $(cat "${WORK}/rej.role.log")"
	(cd "${d}" && ${KSH} "${c}" add role --name r --importance 1 --layout app/build.gradle.kts --naming n --group g --constraint-only) >"${WORK}/rej.role2.log" 2>&1
	st=$?
	[ "${st}" -eq 1 ] && grep -q -- '--note' "${WORK}/rej.role2.log" && ok "--constraint-only は --note が無ければ止まる" ||
		ng "--constraint-only は --note が無ければ止まる" "st=${st} $(cat "${WORK}/rej.role2.log")"
	(cd "${d}" && ${KSH} "${c}" add role --name r --importance 1 --layout app/build.gradle.kts --naming n --group g --constraint-only --note "namespace とパッケージの一致") >"${WORK}/rej.role3.log" 2>&1
	st=$?
	v=$(jget "${d}" report '[r.get("constraintOnly") for r in d["roles"]]')
	[ "${st}" -eq 0 ] && [ "${v}" = "[True]" ] && ok "add role --constraint-only --note は constraintOnly: true で受け付ける" ||
		ng "add role --constraint-only --note は constraintOnly: true で受け付ける" "st=${st} v=${v} $(cat "${WORK}/rej.role3.log")"
	# .gitignore の `build/` はディレクトリだけに当たる書き方なので、実在するディレクトリで確かめる（git check-ignore の規則）
	mkdir -p "${d}/build"
	(cd "${d}" && ${KSH} "${c}" add excluded --path build --reason r) >"${WORK}/rej.ex.log" 2>&1
	st=$?
	[ "${st}" -eq 1 ] && grep -q '\.gitignore で除外' "${WORK}/rej.ex.log" && [ "$(jget "${d}" report 'len(d["excluded"])')" = "0" ] &&
		ok "add excluded は .gitignore 済みのパスを拒む" || ng "add excluded は .gitignore 済みのパスを拒む" "st=${st} $(cat "${WORK}/rej.ex.log")"
	(cd "${d}" && ${KSH} "${c}" add excluded --path src/gen --reason r) >"${WORK}/rej.ex2.log" 2>&1
	st=$?
	[ "${st}" -eq 0 ] && [ "$(jget "${d}" report 'len(d["excluded"])')" = "1" ] && ok "add excluded は ignore されていないパスを受け付ける" ||
		ng "add excluded は ignore されていないパスを受け付ける" "st=${st} $(cat "${WORK}/rej.ex2.log")"
}

# scaffold --deps catalog は別名の衝突で止まり、未指定なら literal に落ちる
case_scaffold_deps_conflict() {
	d=$(inited deps)
	c=$(cli "${d}")
	mkdir -p "${d}/gradle"
	cat >"${d}/gradle/libs.versions.toml" <<EOF
[versions]
kotlin = "${K}"

[libraries]
katachi = { module = "com.example:other", version = "1.0" }
EOF
	(cd "${d}" && ${KSH} "${c}" scaffold --package com.example.app --dry-run --deps catalog) >"${WORK}/deps.catalog.log" 2>&1
	st=$?
	[ "${st}" -eq 1 ] && grep -q '別名 katachi' "${WORK}/deps.catalog.log" && ok "scaffold --deps catalog は別名の衝突で止まる" ||
		ng "scaffold --deps catalog は別名の衝突で止まる" "st=${st} $(cat "${WORK}/deps.catalog.log")"
	(cd "${d}" && ${KSH} "${c}" scaffold --package com.example.app --dry-run) >"${WORK}/deps.auto.log" 2>&1
	st=$?
	[ "${st}" -eq 0 ] && grep -qE '依存:[[:space:]]+literal' "${WORK}/deps.auto.log" && grep -q '別名 katachi' "${WORK}/deps.auto.log" &&
		ok "scaffold は --deps 未指定なら literal に落ちて note を出す" || ng "scaffold は --deps 未指定なら literal に落ちて note を出す" "st=${st} $(cat "${WORK}/deps.auto.log")"
	grep -q 'me\.tbsten' "${d}/gradle/libs.versions.toml" && ng "dry-run は toml を触らない" "toml が変わった" || ok "dry-run は toml を触らない"
}

# scaffold は1行の pluginManagement { repositories { mavenCentral() } } を読み、「次:」に sh <CLI> test を出す
case_scaffold_oneline_plugin_management() {
	d=$(inited oneline)
	c=$(cli "${d}")
	printf 'pluginManagement { repositories { mavenCentral() } }\nrootProject.name = "fx"\n' >"${d}/settings.gradle.kts"
	(cd "${d}" && ${KSH} "${c}" scaffold --package com.example.app) >"${WORK}/oneline.log" 2>&1
	st=$?
	[ "${st}" -eq 0 ] && grep -q 'pluginManagement に追加: no' "${WORK}/oneline.log" && ! grep -q '足すまで' "${WORK}/oneline.log" &&
		ok "scaffold は1行の pluginManagement { repositories { mavenCentral() } } を読める" ||
		ng "scaffold は1行の pluginManagement { repositories { mavenCentral() } } を読める" "st=${st} $(grep -n 'pluginManagement\|足す' "${WORK}/oneline.log")"
	grep -qF 'pluginManagement { repositories { mavenCentral() } }' "${d}/settings.gradle.kts" && ok "1行の pluginManagement は書き換えない" || ng "1行の pluginManagement は書き換えない" "$(head -n 3 "${d}/settings.gradle.kts")"
	grep -q "^次: sh ${c} test\$" "${WORK}/oneline.log" && ! grep -q 'gradlew :architecture-test:test' "${WORK}/oneline.log" &&
		ok "scaffold の「次:」は sh <CLI> test を勧める" || ng "scaffold の「次:」は sh <CLI> test を勧める" "$(grep -n '^次:' "${WORK}/oneline.log")"
	# mavenCentral() が無い1行は、足す指示を出す
	d2=$(inited oneline-miss)
	c2=$(cli "${d2}")
	printf 'pluginManagement { repositories { gradlePluginPortal() } }\nrootProject.name = "fx"\n' >"${d2}/settings.gradle.kts"
	(cd "${d2}" && ${KSH} "${c2}" scaffold --package com.example.app --dry-run) >"${WORK}/oneline-miss.log" 2>&1
	grep -q 'pluginManagement に追加: yes（mavenCentral()）' "${WORK}/oneline-miss.log" && ok "mavenCentral() が無い1行の pluginManagement は不足と判定する" ||
		ng "mavenCentral() が無い1行の pluginManagement は不足と判定する" "$(grep -n 'pluginManagement' "${WORK}/oneline-miss.log")"
}

# test は gradlew の無い場所で exit 2 / 要約とログのパスを出し、--save で名前付きのログを写す / 前回との差分を出す
case_test() {
	d=$(inited tst)
	c=$(cli "${d}")
	w=$(wd "${d}")
	rm -f "${d}/gradlew"
	out=$(${KSH} "${c}" test 2>&1)
	st=$?
	[ "${st}" -eq 2 ] && printf '%s\n' "${out}" | grep -q 'gradlew' && ok "test は gradlew の無い場所で exit 2" || ng "test は gradlew の無い場所で exit 2" "st=${st} ${out}"
	fake_gradlew "${d}"
	out=$(${KSH} "${c}" test 2>&1)
	st=$?
	[ "${st}" -eq 2 ] && printf '%s\n' "${out}" | grep -q 'scaffold' && ok "test は定義ディレクトリが無ければ exit 2" || ng "test は定義ディレクトリが無ければ exit 2" "st=${st} ${out}"
	mkdir -p "${d}/architecture-test/src/test/kotlin/fx"
	printf 'package fx\n' >"${d}/architecture-test/src/test/kotlin/fx/ProjectArchitecture.kt"
	printf '> Task :architecture-test:test\nKatachi check failed: 2 violations (Unexpected: 2)\n  [UnexpectedFile] app/src/main/kotlin/Foo.kt\n  [UnexpectedFile] app/src/main/kotlin/Bar.kt\nBUILD FAILED\n' >"${d}/fake-out.txt"
	printf '1\n' >"${d}/fake-exit"
	out=$(${KSH} "${c}" test --save before-refactor 2>&1)
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
	out=$(${KSH} "${c}" test 2>&1)
	printf '%s\n' "${out}" | grep -q '増えた 1 件' && printf '%s\n' "${out}" | grep -q '^  + \[UnexpectedFile\] app/src/main/kotlin/Baz.kt' &&
		ok "test は前回との差分を出す" || ng "test は前回との差分を出す" "${out}"
	printf '> Task :architecture-test:test\nBUILD SUCCESSFUL in 3s\n' >"${d}/fake-out.txt"
	printf '0\n' >"${d}/fake-exit"
	out=$(${KSH} "${c}" test 2>&1)
	st=$?
	[ "${st}" -eq 0 ] && printf '%s\n' "${out}" | grep -q '^結果: 検査は通りました（違反 0 件）' && ok "test は違反 0 件で exit 0" || ng "test は違反 0 件で exit 0" "st=${st} ${out}"
	# Gradle のテスト出力の形（例外のクラス名が前に付き、件数が単数）でも検査が落ちたと判定する
	printf 'ProjectArchitectureTest > the project layout matches the definition() FAILED\n    me.tbsten.katachi.check.KatachiArchitectureAssertionError: Katachi check failed: 1 violation (Unexpected: 1)\n\n    [UnexpectedFile] file:///x/settings.gradle.kts.bak\n      No role is defined for this file.\n\n1 tests completed, 1 failed\n> Task :architecture-test:test FAILED\nBUILD FAILED in 19s\n' >"${d}/fake-out.txt"
	printf '1\n' >"${d}/fake-exit"
	out=$(${KSH} "${c}" test 2>&1)
	st=$?
	[ "${st}" -eq 1 ] && printf '%s\n' "${out}" | grep -q '^結果: Katachi check failed: 1 violation (Unexpected: 1)' && printf '%s\n' "${out}" | grep -q '^  + \[UnexpectedFile\] file:///x/settings.gradle.kts.bak' &&
		ok "test は例外のクラス名つきの単数の結果行でも検査が落ちたと判定する" || ng "test は例外のクラス名つきの単数の結果行でも検査が落ちたと判定する" "st=${st} ${out}"
	# 検査の違反は無いが、検査用モジュールのほかのテストが落ちた（コンパイルエラーとは別の文面で exit 1）
	printf 'ProjectRootSpec > walking up from the working directory FAILED\n    org.opentest4j.AssertionFailedError: expected:<sample> but was:<copy>\n\nProjectArchitectureTest > the project layout matches the definition() PASSED\n\n2 tests completed, 1 failed\n> Task :architecture-test:test FAILED\nFAILURE: Build failed with an exception.\n> There were failing tests. See the report at: file:///x/index.html\nBUILD FAILED in 19s\n' >"${d}/fake-out.txt"
	out=$(${KSH} "${c}" test 2>&1)
	st=$?
	[ "${st}" -eq 1 ] && printf '%s\n' "${out}" | grep -q '^結果: 検査の違反は出ていませんが、検査用モジュールのほかのテストが落ちています' && printf '%s\n' "${out}" | grep -q '^ProjectRootSpec > walking up from the working directory FAILED' &&
		ok "test はほかのテストが落ちたときは検査まで届いていないと言わずに exit 1" || ng "test はほかのテストが落ちたときは検査まで届いていないと言わずに exit 1" "st=${st} ${out}"
}

# data get のキー指定は部分だけ出す
case_data_get_key() {
	d="${WORK}/guide"
	[ -d "${d}" ] || d=$(inited guide)
	c=$(cli "${d}")
	v=$(${KSH} "${c}" data get report meta.gradleRoot 2>&1)
	st=$?
	[ "${st}" -eq 0 ] && [ "${v}" = "${d}" ] && ok "data get のキー指定は部分だけ出す" || ng "data get のキー指定は部分だけ出す" "st=${st} v=${v}"
	out=$(${KSH} "${c}" data get report nope 2>&1)
	st=$?
	[ "${st}" -eq 1 ] && printf '%s\n' "${out}" | grep -q 'nope' && ok "data get の無いキーは exit 1" || ng "data get の無いキーは exit 1" "st=${st} ${out}"
}

# ---- 実行 ----------------------------------------------------------------------------------

mkdir -p "${WORK}"
syntax_ng=""
sh -n "${KIT}" || syntax_ng="${syntax_ng} sh"
/bin/bash -n "${KIT}" || syntax_ng="${syntax_ng} bash"
if command -v dash >/dev/null 2>&1; then dash -n "${KIT}" || syntax_ng="${syntax_ng} dash"; fi
[ -z "${syntax_ng}" ] && ok "sh -n / bash -n / dash -n" || ng "sh -n / bash -n / dash -n" "failed:${syntax_ng}"

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
