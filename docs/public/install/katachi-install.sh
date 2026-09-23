#!/bin/sh
#
# katachi のインストールを機械的に行うスクリプト。
#
#   sh katachi-install.sh init
#   sh katachi-install.sh scaffold --package com.example.app
#
# 判断が要らないところをすべてここに閉じ込めるためのものです。AI エージェントが
# 手で作ると環境ごとにブレる部分（作業用ディレクトリの決定、Kotlin プラグインの
# バージョン衝突、JUnit の engine、settings への include）はすべてこのスクリプトが
# 決めます。
#
# POSIX sh。bash 固有の機能は使っていません。
# 必要なもの: sh / curl / awk / sed / git（あれば）
# check / uncheck / warn / verify / summary / add / data merge は python3（無ければ jq）も使う。
#
# ネットワークを使うのは init と docs だけ。どちらも curl に接続・転送の
# timeout を設定してあり、ぶら下がったまま止まることはない。

set -eu

# 配信元。ローカルで試すときだけ環境変数で差し替える。
KATACHI_DOCS="${KATACHI_DOCS:-https://tbsten.github.io/katachi}"

# チェックリストとレポートの言語。`init --lang` で決まり、作業用ディレクトリに
# 記録される。以降のコマンドはそこから読むので、毎回指定しなくてよい。
# 既定はサイトの既定ロケールに合わせて en。
KATACHI_LANG="${KATACHI_LANG:-en}"
KATACHI_RELEASES_API="https://api.github.com/repos/TBSten/katachi/releases/latest"
KATACHI_RELEASES_PAGE="https://github.com/TBSten/katachi/releases"

# 生成するモジュールが使う固定値。プロジェクト側の事情で変わらないものだけを置く。
JVM_TOOLCHAIN="21"
JUNIT_VERSION="5.13.4"

MODULE_DIR="architecture-test"

# ---------------------------------------------------------------- 出力

say() { printf '%s\n' "$*"; }
note() { printf '   %s\n' "$*"; }
warn() { printf '\n[!] %s\n' "$*" >&2; }

die() {
	printf '\n[x] %s\n' "$*" >&2
	exit 1
}

usage() {
	cat <<'USAGE'
katachi-install.sh — katachi のインストールのうち機械的にできる部分をまとめて行う

使い方:
  sh katachi-install.sh init [オプション]
      Gradle のルートであることを確かめ、作業用ディレクトリを作り、
      チェックリストとレポートのテンプレートを配置する。
      最後に KEY=VALUE 形式で結果を出力する（後続のステップがこれを読む）。

      --workdir <path>    作業用ディレクトリを明示する（既定: 自動判定）
      --katachi <version> katachi のバージョンを明示する（既定: 最新を取得）
      --offline           ダウンロードを行わない
      --force             記入済みのチェックリスト・レポートも取り直す
      --lang <en|ja>      チェックリストとレポートの言語（既定: en）。
                          作業用ディレクトリに記録され、以降のコマンドが従う

      2回目以降の実行では、記入済みのファイルは上書きしません。
      また自分自身を作業用ディレクトリにコピーするので、以降は
      <作業用ディレクトリ>/katachi-install.sh を使ってください。

  sh katachi-install.sh scaffold --package <com.example.app> [オプション]
      :architecture-test モジュールを作成し、ビルドに組み込む。

      --package <pkg>     アプリのパッケージ名（必須）
      --katachi <version> katachi のバージョン（既定: 最新を取得）
      --kotlin <version>  Kotlin のバージョン（既定: プロジェクトから検出）
      --no-konsist        katachi-konsist を使わない構成で生成する
      --force             既存の architecture-test/ を上書きする
      --dry-run           何も書かずに、何をするかだけ出力する

  sh katachi-install.sh data get <対象> [--id <id>]
      埋め込まれた JSON を標準出力に出す。HTML 本体は読まなくてよい。
      <対象> は check-list / report / ファイルパスのどれか。

  sh katachi-install.sh data set <対象> <json> [--id <id>]
      埋め込まれた JSON を <json> で丸ごと差し替える。

  sh katachi-install.sh data merge <対象> <json> [--id <id>]
      <json> を既存の内容に深くマージする。部分的な更新はこちらが楽。
      配列は丸ごと置き換わる（追記ではない）。

      set / merge はどちらも、書き込む前に JSON として妥当かを検査し、
      元のファイルを .bak に退避し、結果が壊れていれば自動で巻き戻す。

  sh katachi-install.sh add <種類> [--<項目> <値> ...]
      配列に1件追記する。merge と違い、既存の要素を消さない。
      check-list: violation / question / changed
      report:     module / role / tool / excluded

  sh katachi-install.sh docs [--small] [--refresh]
      ドキュメント全文（llms-full.txt）を作業用ディレクトリの cache/ に取得し、
      そのパスを出力する。すでにあれば取りに行かない。

  sh katachi-install.sh check <項目 id>...
  sh katachi-install.sh uncheck <項目 id>...
      チェックリストの項目を完了 / 未完了にする。
      知らない id を渡すと、使える id を並べて止まる。

  sh katachi-install.sh warn <ステップ id> <文言>
  sh katachi-install.sh warn <ステップ id> --clear
      ステップに警告を付ける / 消す。付けたステップは黄色になり、
      完了していても閉じずに表示される。

  sh katachi-install.sh verify
      未記入のフィールドと未完了の項目を列挙する。何も残っていなければ
      最後のステップを完了にして 0 を返す。残っていれば 1 を返す。

  sh katachi-install.sh summary
      ユーザに返す文面を組み立てて出力する。

  sh katachi-install.sh --help

init と scaffold は Gradle のルートディレクトリで実行してください。
data と docs は、作業用ディレクトリに置かれたこのスクリプトから実行してください。
USAGE
}

# ---------------------------------------------------------------- 共通

# このスクリプト自身が置かれているディレクトリ。
script_dir() {
	case "$0" in
	*/*) (cd "${0%/*}" && pwd) ;;
	*) pwd ;;
	esac
}

# 作業用ディレクトリ。init が自分をそこへコピーするので、2回目以降は
# 「自分の隣」が作業用ディレクトリになる。引数は要らない。
resolve_workdir() {
	_sd=$(script_dir)
	if [ -f "$_sd/check-list.html" ] || [ -d "$_sd/cache" ]; then
		printf '%s\n' "$_sd"
		return 0
	fi
	return 1
}

require_workdir() {
	WORKDIR=$(resolve_workdir) || die "作業用ディレクトリが分かりません。先に 'sh katachi-install.sh init' を実行し、以降は <作業用ディレクトリ>/katachi-install.sh を使ってください。"
	MANIFEST="$WORKDIR/cache/MANIFEST"
	# init が記録した言語を読む。環境変数が明示されていればそちらを優先する。
	if [ -f "$WORKDIR/cache/lang" ] && [ -z "${KATACHI_LANG_EXPLICIT:-}" ]; then
		KATACHI_LANG=$(cat "$WORKDIR/cache/lang")
	fi
}

# 取得したものを1行ずつ記録する。あとから人間が何を落としたのか追えるように。
record_fetch() {
	[ -n "${MANIFEST:-}" ] || return 0
	mkdir -p "${MANIFEST%/*}"
	printf '%s\t%s\t%s bytes\t%s\n' \
		"$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$1" "$(wc -c <"$2" | tr -d ' ')" "$2" \
		>>"$MANIFEST"
}

# すでにあるなら取りに行かない。--refresh 相当を第3引数で渡す。
fetch_once() {
	if [ -s "$2" ] && [ "${3:-no}" = "no" ]; then
		return 0
	fi
	mkdir -p "${2%/*}"
	download "$1" "$2"
	record_fetch "$1" "$2"
}

# Gradle のルートにいることを確かめ、settings ファイル名を SETTINGS_FILE に入れる。
require_gradle_root() {
	if [ -f "settings.gradle.kts" ]; then
		SETTINGS_FILE="settings.gradle.kts"
		SETTINGS_DSL="kts"
	elif [ -f "settings.gradle" ]; then
		SETTINGS_FILE="settings.gradle"
		SETTINGS_DSL="groovy"
	else
		die "settings.gradle.kts / settings.gradle が見つかりません。Gradle のルートディレクトリで実行してください（いまいるのは $(pwd)）。"
	fi
}

# ルートの build ファイル名を ROOT_BUILD_FILE に入れる。無ければ作る前提で名前だけ決める。
resolve_root_build_file() {
	if [ -f "build.gradle.kts" ]; then
		ROOT_BUILD_FILE="build.gradle.kts"
		ROOT_BUILD_DSL="kts"
	elif [ -f "build.gradle" ]; then
		ROOT_BUILD_FILE="build.gradle"
		ROOT_BUILD_DSL="groovy"
	elif [ "${SETTINGS_DSL:-kts}" = "groovy" ]; then
		# ルートの build ファイルが無いので作る。settings が Groovy なら合わせる。
		ROOT_BUILD_FILE="build.gradle"
		ROOT_BUILD_DSL="groovy"
	else
		ROOT_BUILD_FILE="build.gradle.kts"
		ROOT_BUILD_DSL="kts"
	fi
}

is_git_repo() {
	command -v git >/dev/null 2>&1 && git rev-parse --is-inside-work-tree >/dev/null 2>&1
}

# $1 が git から ignore されているか。git が無い場合は .gitignore を素朴に見る。
path_is_ignored() {
	if is_git_repo; then
		git check-ignore -q "$1" 2>/dev/null
	else
		[ -f .gitignore ] && grep -qE "^/?$1/?$" .gitignore
	fi
}

# GitHub の最新リリースからバージョンを取り、標準出力に出す。取れなければ空。
fetch_latest_version() {
	command -v curl >/dev/null 2>&1 || return 0
	# 取れなくても --katachi で先に進めるため、短めに切り上げる。
	curl -fsSL --connect-timeout 5 --max-time 15 --retry 1 \
		"$KATACHI_RELEASES_API" 2>/dev/null |
		sed -n 's/.*"tag_name"[[:space:]]*:[[:space:]]*"v\{0,1\}\([^"]*\)".*/\1/p' |
		head -n 1
}

# プロジェクトが使っている Kotlin のバージョンを推定して標準出力に出す。
# 1. version catalog の kotlin = "..."
# 2. 各 build ファイルの `kotlin("jvm") version "..."` / `org.jetbrains.kotlin...version "..."`
detect_kotlin_version() {
	if [ -f "gradle/libs.versions.toml" ]; then
		# `[versions]` の中の `kotlin = "..."` だけを見る。`[libraries]` にも
		# `kotlin = "org.jetbrains.kotlin:kotlin-stdlib:..."` があり得るため。
		_v=$(awk '
			/^[[:space:]]*\[/ { section = $0; next }
			section ~ /^[[:space:]]*\[versions\]/ &&
				$0 ~ /^[[:space:]]*kotlin[[:space:]]*=/ {
				sub(/^[^=]*=[[:space:]]*"/, "")
				sub(/".*/, "")
				print
				exit
			}
		' gradle/libs.versions.toml)
		if printf '%s' "${_v:-}" | grep -qE '^[0-9]'; then
			printf '%s\n' "$_v"
			return 0
		fi
	fi

	# build ファイルから拾う。行コメントは先に落とす（コメント内の version を
	# 拾って、存在しないバージョンを書き込むのを避ける）。
	_v=$(find . -maxdepth 3 \( -name 'build.gradle.kts' -o -name 'build.gradle' \) 2>/dev/null |
		while IFS= read -r _f; do
			sed 's://.*::' "$_f"
		done |
		grep -E 'org\.jetbrains\.kotlin|kotlin\("(jvm|android|multiplatform)"\)' |
		sed -n 's/.*version[[:space:]]*[("'"'"']\{1,2\}\([0-9][0-9.]*[0-9A-Za-z.-]*\).*/\1/p' |
		head -n 1)
	printf '%s\n' "${_v:-}"
}

# $1 を $2 にダウンロードする。
download() {
	command -v curl >/dev/null 2>&1 || die "curl が見つかりません。"
	# 接続は 10 秒で見切り、転送は 120 秒まで待つ。一過性の失敗は2回まで自動で
	# やり直す。ここで止まると手順全体が進まないので、バージョン取得より長い。
	curl -fsSL --connect-timeout 10 --max-time 120 --retry 2 --retry-delay 2 \
		-o "$2" "$1" ||
		die "ダウンロードに失敗しました: $1
  ネットワークかプロキシの設定を確認してください。
  手動で取得する場合は、このファイルを $2 に置いてから同じコマンドをやり直してください。"
}

# ---------------------------------------------------------------- init

cmd_init() {
	init_workdir=""
	init_version=""
	init_offline="no"
	init_force="no"

	while [ $# -gt 0 ]; do
		case "$1" in
		--workdir)
			init_workdir="${2:?--workdir に値がありません}"
			shift 2
			;;
		--katachi)
			init_version="${2:?--katachi に値がありません}"
			shift 2
			;;
		--offline)
			init_offline="yes"
			shift
			;;
		--lang)
			KATACHI_LANG="${2:?--lang に値がありません}"
			KATACHI_LANG_EXPLICIT=yes
			shift 2
			;;
		--force)
			init_force="yes"
			shift
			;;
		-h | --help)
			usage
			return 0
			;;
		*) die "init: 知らないオプションです: $1" ;;
		esac
	done

	require_gradle_root
	project_root=$(pwd)

	say "katachi のインストールを開始します"
	note "プロジェクトルート: $project_root"
	note "settings:          $SETTINGS_FILE"

	if is_git_repo; then
		git_state="yes"
		note "Git:               管理下にあります"
	else
		git_state="no"
		warn "Git で管理されていません。失敗したときに変更を戻せないので、先にコミットするかバックアップを取ってください。"
	fi

	# 作業用ディレクトリ。ignore されている置き場があればそこ、無ければ tmp/。
	if [ -z "$init_workdir" ]; then
		init_workdir="tmp/install-katachi"
		for candidate in .local .tmp tmp .scratch; do
			if [ -d "$candidate" ] || path_is_ignored "$candidate"; then
				if path_is_ignored "$candidate"; then
					init_workdir="$candidate/install-katachi"
					break
				fi
			fi
		done
	fi

	mkdir -p "$init_workdir/tmp" "$init_workdir/cache"
	note "作業用ディレクトリ:  $init_workdir"

	if ! path_is_ignored "$init_workdir"; then
		warn "$init_workdir は git から見えています。.gitignore への追加をユーザに提案してください。"
	fi

	# katachi のバージョン。
	if [ -z "$init_version" ] && [ "$init_offline" = "no" ]; then
		init_version=$(fetch_latest_version)
	fi
	if [ -z "$init_version" ]; then
		warn "katachi の最新バージョンを取得できませんでした。$KATACHI_RELEASES_PAGE を見て --katachi で指定してください。"
		init_version="UNKNOWN"
	else
		note "katachi:            $init_version"
	fi

	kotlin_version=$(detect_kotlin_version)
	if [ -n "$kotlin_version" ]; then
		note "Kotlin:             ${kotlin_version}（プロジェクトから検出）"
	else
		warn "Kotlin のバージョンを検出できませんでした。scaffold に --kotlin で渡してください。"
	fi

	# 以降このスクリプトは作業用ディレクトリから使う。ここに自分を置いておけば、
	# セッションが切れても同じものを使い続けられる。
	case "$KATACHI_LANG" in
	en | ja) ;;
	*) die "--lang は en か ja です（指定されたのは '${KATACHI_LANG}'）" ;;
	esac

	MANIFEST="$init_workdir/cache/MANIFEST"
	mkdir -p "$init_workdir/cache"
	install_self "$init_workdir"
	# 以降のコマンドが同じ言語を使えるように記録する。
	printf '%s' "$KATACHI_LANG" >"$init_workdir/cache/lang"

	# チェックリストとレポート。**すでにあるものは上書きしない。**
	# init をやり直したときに、記入済みの調査結果を消さないため。
	if [ "$init_offline" = "no" ]; then
		_cl="$init_workdir/check-list.html"
		_rp="$init_workdir/project-code-base-report.html"

		if [ -s "$_cl" ] && [ "$init_force" = "no" ]; then
			note "すでにあるので残しました: $_cl"
		else
			fetch_once "$KATACHI_DOCS/install/$KATACHI_LANG/install-check-list.html" "$_cl" yes
			write_checklist_data "$_cl" \
				"$init_workdir" "$init_version" "$project_root" \
				"${kotlin_version:-}" "$git_state"
			note "配置しました:        $_cl"
		fi

		if [ -s "$_rp" ] && [ "$init_force" = "no" ]; then
			note "すでにあるので残しました: $_rp"
		else
			fetch_once "$KATACHI_DOCS/install/$KATACHI_LANG/project-code-base-report-template.html" "$_rp" yes
			write_report_data "$_rp" "$project_root"
			note "配置しました:        $_rp"
		fi
	fi

	say ""
	say "== init の結果 =========================================="
	say "KATACHI_WORKDIR=$init_workdir"
	say "KATACHI_VERSION=$init_version"
	say "KATACHI_PROJECT_ROOT=$project_root"
	say "KATACHI_KOTLIN=${kotlin_version:-}"
	say "KATACHI_GIT=$git_state"
	say "KATACHI_SETTINGS=$SETTINGS_FILE"
	say "KATACHI_CLI=$init_workdir/katachi-install.sh"
	say "KATACHI_LANG=$KATACHI_LANG"
	say "========================================================"
	say ""
	say "以降は $init_workdir/katachi-install.sh を使ってください（再ダウンロードは不要です）。"
	say ""
	say "次: プロジェクトを解析して $init_workdir/project-code-base-report.html を埋めてください。"
}

# このスクリプト自身を作業用ディレクトリへ置く。以降のステップはそちらを使う。
install_self() {
	_wd="$1"
	[ -f "$0" ] || return 0
	_here=$(script_dir)
	_there=$(cd "$_wd" && pwd)
	[ "$_here" = "$_there" ] && return 0
	cp "$0" "$_wd/katachi-install.sh" && chmod +x "$_wd/katachi-install.sh"
}

# JSON の文字列値として安全な形に直す。
json_escape() {
	printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'
}

# JSON の値を書く。空なら null、そうでなければ引用符付きの文字列。
json_value() {
	if [ -z "$1" ]; then
		printf 'null'
	else
		printf '"%s"' "$(json_escape "$1")"
	fi
}

# チェックリストのメタ欄を init が分かっている値で埋める。
#
# **`data set`（全置換）を使ってはいけない。** テンプレートが持つ `steps` を消してしまい、
# check / warn / verify / summary がまとめて機能しなくなる。深いマージで `meta` だけを
# 上書きすること。
write_checklist_data() {
	_file="$1"
	_workdir="$2"
	_version="$3"
	_root="$4"
	_kotlin="$5"
	_git="$6"
	_now=$(date '+%Y-%m-%d %H:%M %Z')

	[ -f "$_file" ] || return 0

	_data="$_file.data.$$"
	{
		printf '{\n'
		printf '  "meta": {\n'
		printf '    "workdir": %s,\n' "$(json_value "$_workdir")"
		printf '    "version": %s,\n' "$(json_value "$_version")"
		printf '    "projectRoot": %s,\n' "$(json_value "$_root")"
		printf '    "ranAt": %s,\n' "$(json_value "$_now")"
		printf '    "kotlin": %s,\n' "$(json_value "$_kotlin")"
		printf '    "git": %s\n' "$(json_value "$_git")"
		printf '  }\n'
		printf '}\n'
	} >"$_data"

	if cmd_data merge "$_file" "$_data" --id checklist >/dev/null 2>&1; then
		rm -f "$_data" "$_file.bak"
	else
		rm -f "$_data"
		warn "チェックリストのメタ欄を初期化できませんでした（python3 か jq が要ります）。$_file を直接確認してください。"
	fi
}

# レポートのメタ欄も、機械的に分かるものは init が埋める。
# ここを埋めないと verify が恒常的に5件の未記入を報告し続ける。
write_report_data() {
	_file="$1"
	_root="$2"
	_now=$(date '+%Y-%m-%dT%H:%M:%S%z')

	[ -f "$_file" ] || return 0

	_name=""
	for _sf in settings.gradle.kts settings.gradle; do
		if [ -f "$_sf" ]; then
			_name=$(sed -n 's/^[[:space:]]*rootProject\.name[[:space:]]*=[[:space:]]*["'"'"']\([^"'"'"']*\)["'"'"'].*/\1/p' "$_sf" | head -n 1)
			[ -n "$_name" ] && break
		fi
	done

	_count=""
	_gitroot=""
	if is_git_repo; then
		_count=$(git ls-files | wc -l | tr -d ' ')
		_gitroot=$(git rev-parse --show-toplevel)
	fi

	_data="$_file.data.$$"
	{
		printf '{\n'
		printf '  "meta": {\n'
		printf '    "projectName": %s,\n' "$(json_value "$_name")"
		printf '    "analyzedAt": %s,\n' "$(json_value "$_now")"
		printf '    "fileCount": %s,\n' "$(json_value "$_count")"
		printf '    "gradleRoot": %s,\n' "$(json_value "$_root")"
		printf '    "gitRoot": %s\n' "$(json_value "$_gitroot")"
		printf '  }\n'
		printf '}\n'
	} >"$_data"

	if cmd_data merge "$_file" "$_data" --id report >/dev/null 2>&1; then
		rm -f "$_data" "$_file.bak"
	else
		rm -f "$_data"
		warn "レポートのメタ欄を初期化できませんでした。$_file を直接確認してください。"
	fi
}

# ---------------------------------------------------------------- scaffold

cmd_scaffold() {
	sc_package=""
	sc_version=""
	sc_kotlin=""
	sc_konsist="yes"
	sc_force="no"
	sc_dry="no"

	while [ $# -gt 0 ]; do
		case "$1" in
		--package)
			sc_package="${2:?--package に値がありません}"
			shift 2
			;;
		--katachi)
			sc_version="${2:?--katachi に値がありません}"
			shift 2
			;;
		--kotlin)
			sc_kotlin="${2:?--kotlin に値がありません}"
			shift 2
			;;
		--no-konsist)
			sc_konsist="no"
			shift
			;;
		--force)
			sc_force="yes"
			shift
			;;
		--dry-run)
			sc_dry="yes"
			shift
			;;
		-h | --help)
			usage
			return 0
			;;
		*) die "scaffold: 知らないオプションです: $1" ;;
		esac
	done

	[ -n "$sc_package" ] || die "--package が必要です（例: --package com.example.app）"
	printf '%s' "$sc_package" | grep -qE '^[a-z][a-zA-Z0-9_]*(\.[a-z][a-zA-Z0-9_]*)*$' ||
		die "--package がパッケージ名として不正です: $sc_package"

	require_gradle_root
	resolve_root_build_file

	[ -n "$sc_version" ] || sc_version=$(fetch_latest_version)
	[ -n "$sc_version" ] ||
		die "katachi のバージョンが分かりません。$KATACHI_RELEASES_PAGE を見て --katachi で指定してください。"

	[ -n "$sc_kotlin" ] || sc_kotlin=$(detect_kotlin_version)
	[ -n "$sc_kotlin" ] ||
		die "Kotlin のバージョンを検出できませんでした。--kotlin で指定してください。"

	if [ -e "$MODULE_DIR" ] && [ "$sc_force" = "no" ]; then
		die "$MODULE_DIR/ がすでに存在します。上書きするなら --force を付けてください。"
	fi

	pkg_path=$(printf '%s' "$sc_package" | tr '.' '/')/test/architecture
	src_dir="$MODULE_DIR/src/test/kotlin/$pkg_path"

	# ルートに Kotlin JVM プラグインが宣言済みかどうかで、モジュール側の書き方が変わる。
	# 宣言済み: モジュールはバージョンを書かない（書くと "already on the classpath with
	# an unknown version" で落ちる）。未宣言: ルートに apply false で足して同じ形にする。
	# コメントを落としてから見る。`// TODO: org.jetbrains.kotlin.jvm ...` を
	# 「宣言済み」と誤判定すると、ルートに何も足されないまま versionless の
	# kotlin("jvm") が書かれ、Gradle が plugin not found で落ちる。
	#
	# version catalog の `alias(libs.plugins.kotlin.jvm)` / `libs.plugins.kotlinJvm`
	# も宣言済みとみなす。見落とすと同じプラグインを二重宣言して Gradle が死ぬ。
	if [ -f "$ROOT_BUILD_FILE" ] && sed 's://.*::' "$ROOT_BUILD_FILE" |
		grep -qE 'org\.jetbrains\.kotlin\.jvm|kotlin\("jvm"\)|libs\.plugins\.kotlin[.-]?[jJ]vm|kotlinJvm|kotlin-jvm'; then
		root_needs_plugin="no"
	else
		root_needs_plugin="yes"
	fi

	if sed 's://.*::' "$SETTINGS_FILE" 2>/dev/null |
		grep -qE "^[[:space:]]*include[[:space:](]+[^)]*[\"']:?$MODULE_DIR[\"']"; then
		settings_needs_include="no"
	else
		settings_needs_include="yes"
	fi

	say "作成する内容"
	note "パッケージ:   $sc_package"
	note "katachi:      $sc_version"
	note "Kotlin:       $sc_kotlin"
	note "konsist:      $sc_konsist"
	note "モジュール:   $MODULE_DIR/"
	note "ソース:       $src_dir/"
	note "ルートに追加: $root_needs_plugin ($ROOT_BUILD_FILE)"
	note "include 追加: $settings_needs_include ($SETTINGS_FILE)"

	if [ "$sc_dry" = "yes" ]; then
		say ""
		say "--dry-run のため何も書きませんでした。"
		return 0
	fi

	mkdir -p "$src_dir"
	write_module_build "$sc_version" "$sc_konsist"
	# **--force でも定義は上書きしない。** ここには人とエージェントが書いた
	# architecture { } が入っている。やり直しで消えると取り返しがつかない。
	if [ -s "$src_dir/ProjectArchitecture.kt" ]; then
		note "すでにあるので残しました: $src_dir/ProjectArchitecture.kt"
	else
		write_architecture_kt "$sc_package" "$src_dir"
	fi
	write_test_kt "$sc_package" "$src_dir" "$sc_konsist"

	ROOT_PLUGIN_MANUAL=""
	[ "$root_needs_plugin" = "yes" ] && add_root_plugin "$sc_kotlin"
	[ "$settings_needs_include" = "yes" ] && add_settings_include

	say ""
	say "== 作成しました ========================================"
	say "$MODULE_DIR/build.gradle.kts"
	say "$src_dir/ProjectArchitecture.kt"
	say "$src_dir/ProjectArchitectureTest.kt"
	[ "$root_needs_plugin" = "yes" ] && say "${ROOT_BUILD_FILE}（Kotlin JVM プラグインを apply false で追加）"
	[ "$settings_needs_include" = "yes" ] && say "${SETTINGS_FILE}（include を追加）"
	say "========================================================"
	if [ -n "${ROOT_PLUGIN_MANUAL:-}" ]; then
		warn "$ROOT_BUILD_FILE は buildscript { } を持つため、自動で書き換えませんでした。"
		say ""
		say "次の1行を、$ROOT_BUILD_FILE の buildscript { } の**後ろ**にある"
		say "plugins { } の中に足してください（plugins { } が無ければ作ってください）。"
		say ""
		say "$ROOT_PLUGIN_MANUAL"
		say ""
		say "足すまで ./gradlew :$MODULE_DIR:test は失敗します。"
	fi

	say ""
	say "次: ./gradlew :$MODULE_DIR:test --rerun"
	say "    この時点では architecture { } が空なので、成功するのが正常です。"
}

write_module_build() {
	_version="$1"
	_konsist="$2"

	if [ "$_konsist" = "yes" ]; then
		_konsist_line="    testImplementation(\"me.tbsten.katachi:katachi-konsist:$_version\")"
	else
		_konsist_line=""
	fi

	cat >"$MODULE_DIR/build.gradle.kts" <<EOF
plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain($JVM_TOOLCHAIN)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}

dependencies {
    testImplementation("me.tbsten.katachi:katachi:$_version")
$_konsist_line
    testImplementation(platform("org.junit:junit-bom:$JUNIT_VERSION"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
EOF
	# repositories は宣言しない。Android のように FAIL_ON_PROJECT_REPOS が
	# 設定されたプロジェクトでは、モジュール側の repositories が失敗になるため。
}

write_architecture_kt() {
	_package="$1"
	_dir="$2"

	cat >"$_dir/ProjectArchitecture.kt" <<EOF
package $_package.test.architecture

import me.tbsten.katachi.dsl.architecture

val projectArchitecture = architecture {
}
EOF
}

write_test_kt() {
	_package="$1"
	_dir="$2"
	_konsist="$3"

	if [ "$_konsist" = "yes" ]; then
		cat >"$_dir/ProjectArchitectureTest.kt" <<EOF
package $_package.test.architecture

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.check.KonsistCheck
import me.tbsten.katachi.check.assert
import org.junit.jupiter.api.Test

class ProjectArchitectureTest {
    @OptIn(ExperimentalKatachiApi::class)
    @Test
    fun \`プロジェクトの構成が定義どおりになっている\`() {
        projectArchitecture.assert(KonsistCheck())
    }
}
EOF
	else
		cat >"$_dir/ProjectArchitectureTest.kt" <<EOF
package $_package.test.architecture

import me.tbsten.katachi.check.assert
import org.junit.jupiter.api.Test

class ProjectArchitectureTest {
    @Test
    fun \`プロジェクトの構成が定義どおりになっている\`() {
        projectArchitecture.assert()
    }
}
EOF
	fi
}

# ルートの build ファイルに Kotlin JVM プラグインを apply false で宣言する。
#
# **利用者のファイルなので、形が想定と違ったら書き換えずに指示を出す。**
# 壊して黙って進むより、止まって「この1行をここに足してください」と言うほうがよい。
add_root_plugin() {
	_kotlin="$1"

	if [ "$ROOT_BUILD_DSL" = "groovy" ]; then
		_line="    id 'org.jetbrains.kotlin.jvm' version '$_kotlin' apply false"
	else
		_line="    id(\"org.jetbrains.kotlin.jvm\") version \"$_kotlin\" apply false"
	fi

	# ファイルが無い: 作る。
	if [ ! -f "$ROOT_BUILD_FILE" ]; then
		cat >"$ROOT_BUILD_FILE" <<EOF
plugins {
$_line
}
EOF
		return 0
	fi

	cp "$ROOT_BUILD_FILE" "$ROOT_BUILD_FILE.bak"

	# トップレベルの plugins { } がある: その直後に足す。
	# インデントされた `plugins {`（subprojects { } の中など）は対象外。
	if grep -qE '^plugins[[:space:]]*\{' "$ROOT_BUILD_FILE"; then
		_tmp="$ROOT_BUILD_FILE.tmp.$$"
		if awk -v line="$_line" '
			!done && /^plugins[[:space:]]*\{/ { print; print line; done = 1; next }
			{ print }
		' "$ROOT_BUILD_FILE" >"$_tmp"; then
			mv "$_tmp" "$ROOT_BUILD_FILE"
			rm -f "$ROOT_BUILD_FILE.bak"
			return 0
		fi
		rm -f "$_tmp"
		mv "$ROOT_BUILD_FILE.bak" "$ROOT_BUILD_FILE"
		die "$ROOT_BUILD_FILE の書き換えに失敗しました。元に戻しました。"
	fi

	# トップレベルの buildscript { } がある: plugins { } はその後ろに来る必要があり、
	# 位置の判定が当てにならない。触らずに指示を出す。
	if grep -qE '^buildscript[[:space:]]*\{' "$ROOT_BUILD_FILE"; then
		rm -f "$ROOT_BUILD_FILE.bak"
		ROOT_PLUGIN_MANUAL="$_line"
		return 0
	fi

	# plugins { } が無い: 先頭の import 群の後ろに新しいブロックを作る。
	_tmp="$ROOT_BUILD_FILE.tmp.$$"
	if awk -v line="$_line" '
		BEGIN { placed = 0 }
		!placed && /^import[[:space:]]/ { print; seen_import = 1; next }
		!placed && seen_import && /^[[:space:]]*$/ { print; next }
		!placed {
			print "plugins {"
			print line
			print "}"
			print ""
			placed = 1
		}
		{ print }
		END { if (!placed) { print "plugins {"; print line; print "}" } }
	' "$ROOT_BUILD_FILE" >"$_tmp"; then
		mv "$_tmp" "$ROOT_BUILD_FILE"
		rm -f "$ROOT_BUILD_FILE.bak"
		return 0
	fi
	rm -f "$_tmp"
	mv "$ROOT_BUILD_FILE.bak" "$ROOT_BUILD_FILE"
	die "$ROOT_BUILD_FILE の書き換えに失敗しました。元に戻しました。"
}

add_settings_include() {
	if [ "$SETTINGS_DSL" = "groovy" ]; then
		printf "\ninclude '%s'\n" "$MODULE_DIR" >>"$SETTINGS_FILE"
	else
		printf '\ninclude("%s")\n' "$MODULE_DIR" >>"$SETTINGS_FILE"
	fi
}

# ---------------------------------------------------------------- data

# 埋め込み JSON のブロック id を、ファイル名から決める。
guess_block_id() {
	case "$1" in
	*check-list*) printf 'checklist\n' ;;
	*report*) printf 'report\n' ;;
	*) printf 'report\n' ;;
	esac
}

# HTML から <script type="application/json" id="..."> の中身だけを取り出す。
# 開始タグと閉じタグが行頭に単独で置かれている前提（テンプレートがそう書いている）。
extract_json() {
	awk -v open_tag="<script type=\"application/json\" id=\"$2\">" '
		$0 == open_tag { inside = 1; found = 1; next }
		inside && $0 == "</script>" { inside = 0; closed = 1; next }
		inside { print }
		END {
			if (!found) exit 3
			# 閉じタグを行頭単独で見つけられなかった。ここで成功を返すと、
			# 書き込み側が「次の </script> まで」を消してしまう。
			if (!closed) exit 4
		}
	' "$1"
}

# JSON として妥当かを見る。python3 も jq も無ければ検査を飛ばす。
validate_json() {
	if command -v python3 >/dev/null 2>&1; then
		python3 -m json.tool <"$1" >/dev/null
	elif command -v jq >/dev/null 2>&1; then
		jq empty <"$1"
	else
		die "JSON を検査できる道具がありません（python3 か jq が要ります）。検査せずに書き込むと、壊れた JSON に気づけないまま HTML が表示されなくなるので中断します。"
	fi
}

# check-list / report という短い名前でも指定できるようにする。
resolve_data_target() {
	case "$1" in
	check-list | checklist)
		require_workdir
		printf '%s\n' "$WORKDIR/check-list.html"
		;;
	report)
		require_workdir
		printf '%s\n' "$WORKDIR/project-code-base-report.html"
		;;
	*) printf '%s\n' "$1" ;;
	esac
}

# $1 に $2 を深くマージして $3 に書く。配列は丸ごと置き換える（追記しない）。
merge_json() {
	if command -v python3 >/dev/null 2>&1; then
		python3 - "$1" "$2" "$3" <<'PYMERGE'
import json, sys

def deep(base, patch):
    for key, value in patch.items():
        if isinstance(value, dict) and isinstance(base.get(key), dict):
            deep(base[key], value)
        else:
            base[key] = value
    return base

base = json.load(open(sys.argv[1], encoding="utf-8"))
patch = json.load(open(sys.argv[2], encoding="utf-8"))
with open(sys.argv[3], "w", encoding="utf-8") as out:
    json.dump(deep(base, patch), out, ensure_ascii=False, indent=2)
    out.write("\n")
PYMERGE
	elif command -v jq >/dev/null 2>&1; then
		jq -s '.[0] * .[1]' "$1" "$2" >"$3"
	else
		die "data merge には python3 か jq が必要です。get と set は使えます。"
	fi
}

cmd_data() {
	_action="${1:-}"
	shift 2>/dev/null || true
	_html="${1:-}"
	shift 2>/dev/null || true

	case "$_action" in
	get | set | merge) ;;
	*) die "data のサブコマンドは get / set / merge です（指定されたのは '${_action}'）" ;;
	esac
	[ -n "$_html" ] || die "data $_action: 対象を指定してください（check-list / report / ファイルパス）。"
	_html=$(resolve_data_target "$_html")
	[ -f "$_html" ] || die "ファイルがありません: $_html"

	if [ "$_action" != "get" ]; then
		_json="${1:-}"
		shift 2>/dev/null || true
		[ -n "$_json" ] || die "data $_action: JSON ファイルを指定してください。"
		[ -f "$_json" ] || die "ファイルがありません: $_json"
	fi

	_id=""
	while [ $# -gt 0 ]; do
		case "$1" in
		--id)
			_id="${2:?--id に値がありません}"
			shift 2
			;;
		*) die "data: 知らないオプションです: $1" ;;
		esac
	done
	[ -n "$_id" ] || _id=$(guess_block_id "$_html")

	if [ "$_action" = "get" ]; then
		extract_json "$_html" "$_id" ||
			die "$_html に id=\"$_id\" の JSON ブロックが見つかりません。"
		return 0
	fi

	validate_json "$_json" ||
		die "$_json が JSON として読めません。直してからやり直してください。"

	_merged=""
	if [ "$_action" = "merge" ]; then
		_merged="$_json.merged.$$"
		if ! extract_json "$_html" "$_id" >"$_merged.base" 2>/dev/null; then
			rm -f "$_merged.base"
			die "$_html に id=\"$_id\" の JSON ブロックが見つかりません。開始タグと閉じタグは、それぞれ行頭に単独で置かれている必要があります。"
		fi
		if ! merge_json "$_merged.base" "$_json" "$_merged"; then
			rm -f "$_merged" "$_merged.base"
			die "JSON をマージできませんでした。"
		fi
		rm -f "$_merged.base"
		_json="$_merged"
	fi

	extract_json "$_html" "$_id" >/dev/null ||
		die "$_html に id=\"$_id\" の JSON ブロックが見つかりません。"

	# JSON の値に `</script>` が入っていると、ブラウザの HTML パーサがそこで
	# ブロックを打ち切る。JSON としては等価な `<\/script>` に置き換えて無害化する。
	_safe="$_json.safe.$$"
	sed 's|</script|<\\/script|g' "$_json" >"$_safe"

	cp "$_html" "$_html.bak"
	_tmp="$_html.tmp.$$"
	if awk -v open_tag="<script type=\"application/json\" id=\"$_id\">" -v src="$_safe" '
		$0 == open_tag {
			print
			while ((getline line < src) > 0) print line
			close(src)
			skipping = 1
			next
		}
		skipping && $0 == "</script>" { skipping = 0; print; next }
		skipping { next }
		{ print }
	' "$_html" >"$_tmp"; then
		mv "$_tmp" "$_html"
		rm -f "$_safe"
	else
		rm -f "$_tmp" "$_safe"
		mv "$_html.bak" "$_html"
		die "$_html の書き換えに失敗しました。元に戻しました。"
	fi

	_check="$_html.check.$$"
	if extract_json "$_html" "$_id" >"$_check" 2>/dev/null && validate_json "$_check"; then
		rm -f "$_check" ${_merged:+"$_merged"}
		say "$_html の id=\"$_id\" を更新しました（元は $_html.bak）。"
	else
		rm -f "$_check" ${_merged:+"$_merged"}
		mv "$_html.bak" "$_html"
		die "差し込んだ結果が読み直せなかったため、元に戻しました。JSON の値に </script> のような、HTML を打ち切る文字列が含まれていないか確認してください。"
	fi
}

# ---------------------------------------------------------------- docs

cmd_docs() {
	_small="no"
	_refresh="no"
	while [ $# -gt 0 ]; do
		case "$1" in
		--small)
			_small="yes"
			shift
			;;
		--refresh)
			_refresh="yes"
			shift
			;;
		-h | --help)
			usage
			return 0
			;;
		*) die "docs: 知らないオプションです: $1" ;;
		esac
	done

	require_workdir
	if [ "$_small" = "yes" ]; then
		_name="llms-small.txt"
	else
		_name="llms-full.txt"
	fi
	_dest="$WORKDIR/cache/$_name"

	fetch_once "$KATACHI_DOCS/$_name" "$_dest" "$_refresh"
	printf '%s\n' "$_dest"
}

# ---------------------------------------------------------------- 進捗と点検

need_python() {
	command -v python3 >/dev/null 2>&1 ||
		die "このコマンドには python3 が必要です。data get / set / merge は python3 なしでも使えます。"
}

# チェックリストの JSON を python に渡して加工し、書き戻す。
# $1 = python スクリプト, 残り = そのスクリプトへの引数
edit_checklist() {
	need_python
	require_workdir
	_cl="$WORKDIR/check-list.html"
	[ -f "$_cl" ] || die "$_cl がありません。先に init を実行してください。"

	_script="$1"
	shift
	_cur="$_cl.cur.$$"
	extract_json "$_cl" checklist >"$_cur" ||
		die "$_cl から JSON を取り出せませんでした。"

	_out="$_cl.new.$$"
	if printf '%s' "$_script" | python3 - "$_cur" "$_out" "$@"; then
		rm -f "$_cur"
		if cmd_data set "$_cl" "$_out" --id checklist >/dev/null; then
			rm -f "$_out" "$_cl.bak"
		else
			rm -f "$_out"
			exit 1
		fi
	else
		rm -f "$_cur" "$_out"
		exit 1
	fi
}

CHECK_PY='
import json, sys
src, dest, done = sys.argv[1], sys.argv[2], sys.argv[3] == "true"
ids = sys.argv[4:]
data = json.load(open(src, encoding="utf-8"))
known = {i["id"]: i for s in data.get("steps", []) for i in s.get("items", [])}
unknown = [i for i in ids if i not in known]
if unknown:
    sys.stderr.write("知らない項目 id です: %s\n" % ", ".join(unknown))
    sys.stderr.write("使える id: %s\n" % ", ".join(known))
    sys.exit(1)
for i in ids:
    known[i]["done"] = done
json.dump(data, open(dest, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print("%s: %s" % ("完了にしました" if done else "未完了に戻しました", ", ".join(ids)))
'

cmd_check() {
	[ $# -gt 0 ] || die "check: 項目 id を1つ以上指定してください（例: check 1-1 1-2）"
	edit_checklist "$CHECK_PY" true "$@"
}

cmd_uncheck() {
	[ $# -gt 0 ] || die "uncheck: 項目 id を1つ以上指定してください。"
	edit_checklist "$CHECK_PY" false "$@"
}

WARN_PY='
import json, sys
src, dest, step_id = sys.argv[1], sys.argv[2], sys.argv[3]
text = sys.argv[4] if len(sys.argv) > 4 else None
data = json.load(open(src, encoding="utf-8"))
steps = {s["id"]: s for s in data.get("steps", [])}
if step_id not in steps:
    sys.stderr.write("知らないステップ id です: %s\n" % step_id)
    sys.stderr.write("使える id: %s\n" % ", ".join(steps))
    sys.exit(1)
steps[step_id]["warning"] = text
json.dump(data, open(dest, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print("ステップ %s の警告を%s" % (step_id, "消しました" if text is None else "設定しました"))
'

cmd_warn() {
	[ $# -gt 0 ] || die "warn: ステップ id を指定してください（例: warn 4 \"違反を2件残しました\"）"
	_step="$1"
	shift
	if [ "${1:-}" = "--clear" ]; then
		edit_checklist "$WARN_PY" "$_step"
	else
		[ -n "${1:-}" ] || die "warn: 警告の文言を指定してください（消すなら --clear）"
		edit_checklist "$WARN_PY" "$_step" "$1"
	fi
}

# ---------------------------------------------------------------- add

# 配列への追記。`data merge` は配列を丸ごと置き換えるので、2件目を足すと
# 1件目が黙って消える。追記はこちらを使う。
ADD_PY='
import json, sys

src, dest, target, kind = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
args = sys.argv[5:]

FIELDS = {
    "violation":  ("violations",  ["violation", "location", "whyNotFixed", "suggestion"], []),
    "question":   ("questions",   ["question", "observed", "recommendation"], ["options"]),
    "changed":    ("changedFiles",["path", "change", "summary"], []),
    "module":     ("modules",     ["path", "kind", "role", "buildFile"], []),
    "role":       ("roles",       ["importance", "name", "layout", "naming", "count", "note"],
                                  ["allowed", "forbidden", "examples"]),
    "tool":       ("tools",       ["name", "configPath", "declareInKatachi", "note"], []),
    "excluded":   ("excluded",    ["path", "reason"], []),
}

if kind not in FIELDS:
    sys.stderr.write("知らない種類です: %s\n" % kind)
    sys.stderr.write("使えるもの: %s\n" % ", ".join(FIELDS))
    sys.exit(1)

key, scalars, lists = FIELDS[kind]
entry = {}
for f in scalars:
    entry[f] = None
for f in lists:
    entry[f] = []

i = 0
while i < len(args):
    name = args[i].lstrip("-")
    if i + 1 >= len(args):
        sys.stderr.write("--%s に値がありません\n" % name)
        sys.exit(1)
    value = args[i + 1]
    i += 2
    if name in lists:
        entry[name].append(value)
    elif name in scalars:
        if name in ("importance", "count"):
            try:
                value = int(value)
            except ValueError:
                sys.stderr.write("--%s は数値で指定してください: %s\n" % (name, value))
                sys.exit(1)
        entry[name] = value
    else:
        sys.stderr.write("--%s は %s で使えません\n" % (name, kind))
        sys.stderr.write("使えるもの: %s\n" % ", ".join(scalars + lists))
        sys.exit(1)

data = json.load(open(src, encoding="utf-8"))
data.setdefault(key, []).append(entry)
json.dump(data, open(dest, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print("%s に1件追記しました（計 %d 件）" % (key, len(data[key])))
'

# どの種類がどちらのファイルに属するか。
add_target_of() {
	case "$1" in
	violation | question | changed) printf 'check-list\n' ;;
	module | role | tool | excluded) printf 'report\n' ;;
	*) printf '\n' ;;
	esac
}

cmd_add() {
	need_python
	_kind="${1:-}"
	[ -n "$_kind" ] || die "add: 種類を指定してください（violation / question / changed / module / role / tool / excluded）"
	shift

	_target=$(add_target_of "$_kind")
	[ -n "$_target" ] || die "add: 知らない種類です: $_kind"

	_html=$(resolve_data_target "$_target")
	[ -f "$_html" ] || die "ファイルがありません: $_html"
	_id=$(guess_block_id "$_html")

	_cur="$_html.cur.$$"
	extract_json "$_html" "$_id" >"$_cur" || {
		rm -f "$_cur"
		die "$_html から JSON を取り出せません。"
	}

	_new="$_html.new.$$"
	if printf '%s' "$ADD_PY" | python3 - "$_cur" "$_new" "$_target" "$_kind" "$@"; then
		rm -f "$_cur"
		cmd_data set "$_html" "$_new" --id "$_id" >/dev/null &&
			rm -f "$_new" "$_html.bak"
	else
		rm -f "$_cur" "$_new"
		exit 1
	fi
}

# ---------------------------------------------------------------- verify / summary

VERIFY_PY='
import json, sys

cl_path, out_path = sys.argv[1], sys.argv[2]
rp_path = sys.argv[3] if len(sys.argv) > 3 and sys.argv[3] else None

cl = json.load(open(cl_path, encoding="utf-8"))
rp = json.load(open(rp_path, encoding="utf-8")) if rp_path else None

# 最後のステップは「verify を通すこと」そのものなので、自分で自分を未完了に
# 数えない。数えると verify が永久に通らなくなる。
steps = cl.get("steps", [])
# 「verify を通すこと」自体のステップ。任意のステップは対象外
required = [s for s in steps if s.get("optional") is not True]
final = required[-1]["id"] if required else None

missing = []

def blank(v):
    return v is None or v == "" or v == []

for k, v in (cl.get("meta") or {}).items():
    if blank(v):
        missing.append("check-list: meta.%s" % k)

if not steps:
    missing.append("check-list: steps がありません（テンプレートが壊れています）")

for s in steps:
    # 任意のステップは数えない。やらなくても完了とみなす
    if s["id"] == final or s.get("optional") is True:
        continue
    for i in s.get("items", []):
        if not i.get("done"):
            missing.append("check-list: 未チェック %s %s" % (i["id"], i.get("label", "")))

if rp is not None:
    for k, v in (rp.get("meta") or {}).items():
        if blank(v):
            missing.append("report: meta.%s" % k)
    for k, v in (rp.get("overview") or {}).items():
        if blank(v):
            missing.append("report: overview.%s" % k)
    arch = rp.get("architecture") or {}
    if blank(arch.get("name")):
        missing.append("report: architecture.name")
    if blank(arch.get("rationale")):
        missing.append("report: architecture.rationale")
    for key in ("modules", "directoryTree", "roles"):
        if blank(rp.get(key)):
            missing.append("report: %s" % key)
    for n, r in enumerate(rp.get("roles") or []):
        for k in ("importance", "name", "layout", "naming"):
            if blank(r.get(k)):
                missing.append("report: roles[%d].%s" % (n, k))

warnings = [(s["id"], s["warning"]) for s in steps if s.get("warning")]

if missing:
    print("未完了・未記入が %d 件あります。" % len(missing))
    print()
    for m in missing:
        print("  - %s" % m)
else:
    for s in steps:
        if s["id"] == final:
            for i in s.get("items", []):
                i["done"] = True
    json.dump(cl, open(out_path, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print("未完了・未記入はありません。ステップ %s を完了にしました。" % final)

if warnings:
    print()
    print("警告つきのステップ:")
    for sid, text in warnings:
        print("  - ステップ %s: %s" % (sid, text))

sys.exit(1 if missing else 0)
'

cmd_verify() {
	need_python
	require_workdir
	_cl="$WORKDIR/check-list.html"
	_rp="$WORKDIR/project-code-base-report.html"
	[ -f "$_cl" ] || die "$_cl がありません。先に init を実行してください。"

	_a="$WORKDIR/cache/.verify-cl.$$"
	_out="$WORKDIR/cache/.verify-out.$$"
	extract_json "$_cl" checklist >"$_a" || die "チェックリストの JSON を読めません。"

	_b=""
	if [ -f "$_rp" ]; then
		_b="$WORKDIR/cache/.verify-rp.$$"
		if ! extract_json "$_rp" report >"$_b"; then
			rm -f "$_a" "$_b"
			die "レポートの JSON を読めません。開始タグと閉じタグは、それぞれ行頭に単独で置かれている必要があります。"
		fi
	fi

	# `set -e` の下では、条件に置かないとここで抜けて後始末が走らない。
	if printf '%s' "$VERIFY_PY" | python3 - "$_a" "$_out" ${_b:+"$_b"}; then
		_rc=0
	else
		_rc=1
	fi

	# 通ったときだけ、最後のステップを完了にしたものが書き出されている。
	if [ "$_rc" = "0" ] && [ -s "$_out" ]; then
		cmd_data set "$_cl" "$_out" --id checklist >/dev/null && rm -f "$_cl.bak"
	fi

	rm -f "$_a" "$_out" ${_b:+"$_b"}
	return $_rc
}

SUMMARY_PY='
import json, sys

cl = json.load(open(sys.argv[1], encoding="utf-8"))
meta = cl.get("meta") or {}
workdir = meta.get("workdir") or "<作業用ディレクトリ>"
violations = cl.get("violations") or []
questions = cl.get("questions") or []
changed = cl.get("changedFiles") or []
steps = cl.get("steps", [])
items = [i for s in steps if s.get("optional") is not True for i in s.get("items", [])]
done = [i for i in items if i.get("done")]
optional_items = [i for s in steps if s.get("optional") is True for i in s.get("items", [])]

if len(done) < len(items):
    print("❌ katachi のセットアップができませんでした")
    print()
    print("終わっていない項目:")
    for i in items:
        if not i.get("done"):
            print("- %s %s" % (i["id"], i.get("label", "")))
    print()
    print("チェックリスト:")
    print("./%s/check-list.html" % workdir)
    sys.exit(0)

if violations:
    print("⚠️ katachi のセットアップを行いました")
else:
    print("✅ katachi のセットアップを行いました")
print()
print("**生成されたコードはあなたのレビューが必要不可欠** です。内容を確認してください。")
print()
print("- チェックリスト: ./%s/check-list.html" % workdir)
print("- コードベース レポート: ./%s/project-code-base-report.html" % workdir)
if violations:
    print("- `:architecture-test:test` の実行結果: ⚠️ %d 件のアーキテクチャ違反を残しています" % len(violations))
else:
    print("- `:architecture-test:test` の実行結果: ✅ All green")
if questions:
    print("- 確認したいことが %d 件あります（チェックリストの「ユーザに確認したいこと」）" % len(questions))
print("- 変更したファイル: %d 件" % len(changed))
print("- Next action:")
print("    - architecture-test/ 以下の ProjectArchitecture.kt を**レビュー**してください")
# 任意のステップで未実施のものを、これからやることとして出す。
# label は「〜した」の完了形なので、そのまま出すと嘘になる。
HINT = {
    "6-1": "実際の実装タスクを1つ回して、定義が機能することを確かめてください",
    "6-2": "CI に `:architecture-test:test` を組み込んでください",
}
for i in optional_items:
    if not i.get("done"):
        print("    - %s" % HINT.get(i["id"], i.get("label", i["id"])))
'

cmd_summary() {
	need_python
	require_workdir
	_cl="$WORKDIR/check-list.html"
	[ -f "$_cl" ] || die "$_cl がありません。先に init を実行してください。"
	_a="$WORKDIR/cache/.summary.$$"
	extract_json "$_cl" checklist >"$_a" || die "チェックリストの JSON を読めません。"
	# `set -e` の下では、条件に置かないと python の失敗で後始末に届かない。
	if printf '%s' "$SUMMARY_PY" | python3 - "$_a"; then
		rm -f "$_a"
	else
		rm -f "$_a"
		die "チェックリストの JSON を読めませんでした。data get check-list で中身を確認してください。"
	fi
}

# ---------------------------------------------------------------- entry

case "${1:-}" in
init)
	shift
	cmd_init "$@"
	;;
scaffold)
	shift
	cmd_scaffold "$@"
	;;
data)
	shift
	cmd_data "$@"
	;;
docs)
	shift
	cmd_docs "$@"
	;;
check)
	shift
	cmd_check "$@"
	;;
uncheck)
	shift
	cmd_uncheck "$@"
	;;
warn)
	shift
	cmd_warn "$@"
	;;
add)
	shift
	cmd_add "$@"
	;;
verify)
	shift
	cmd_verify "$@"
	;;
summary)
	shift
	cmd_summary "$@"
	;;
-h | --help | "")
	usage
	;;
*)
	die "知らないサブコマンドです: $1（--help を見てください）"
	;;
esac
