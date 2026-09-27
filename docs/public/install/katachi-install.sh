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
# check / uncheck / warn / verify / summary / add / lint / data merge は python3（無ければ jq）も使う。
#
# ネットワークを使うのは init と docs だけ。どちらも curl に接続・転送の
# timeout を設定してあり、ぶら下がったまま止まることはない。
#
# 開発者向け: KATACHI_MAVEN_LOCAL=1
#   リリース前の katachi を ~/.m2 に publish して試すための口。利用者の手順には出さない。
#   init に付けると作業用ディレクトリに記録され、以降の scaffold も従う。
#   - init / scaffold の「Gradle plugin は 0.2.0 以降」の確認を飛ばす（--katachi か
#     init で指定した版のときだけ。GitHub の最新を取ってきた版では飛ばさない）
#   - scaffold が settings の pluginManagement { repositories { } } と
#     dependencyResolutionManagement { repositories { } } に mavenLocal() を足す
#
#     # katachi のリポジトリで。署名の設定が無い手元では -Pkatachi.skipSigning が要る
#     ./gradlew publishToMavenLocal -Pkatachi.skipSigning
#     # 試すプロジェクトで。scaffold は init の --katachi を引き継ぐ
#     KATACHI_MAVEN_LOCAL=1 sh katachi-install.sh init --katachi 0.2.0
#     sh <作業用ディレクトリ>/katachi-install.sh scaffold --package com.example.app

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
# katachi が要求する Kotlin の下限。katachi の成果物は languageVersion 2.2 で
# ビルドしているので、2.2 以降のコンパイラなら metadata を読める。これより古いと
# architecture などのシンボルがすべて Unresolved reference になる。
KOTLIN_MIN_MAJOR="2"
KOTLIN_MIN_MINOR="2"

# katachi の Gradle plugin（me.tbsten.katachi）が最初に出た版。これより前の版には
# plugin が無く、scaffold が生成する build.gradle.kts が解決できない。
KATACHI_MIN_MAJOR="0"
KATACHI_MIN_MINOR="2"
KATACHI_PLUGIN_ID="me.tbsten.katachi"

JVM_TOOLCHAIN="17"
JUNIT_VERSION="5.13.4"

MODULE_DIR="architecture-test"

# ---------------------------------------------------------------- 出力

# メッセージに日本語を混ぜるときは、変数展開を必ず ${VAR} と波括弧で閉じること。
# $VAR の直後に非 ASCII（全角の閉じ括弧など）が続くと、macOS の bash 3.2 は続くバイトを
# 変数名の一部と誤読し、set -u と相まって "unbound variable" で落ちる。メッセージが
# 出ないまま終了するので気づきにくい。これまでに4回踏んでいる。
say() { printf '%s\n' "$*"; }
note() { printf '   %s\n' "$*"; }
warn() { printf '\n[!] %s\n' "$*" >&2; }

die() {
	printf '\n[x] %s\n' "$*" >&2
	exit 1
}

# 人に見せるパスを file:// で始まる絶対 URI にして出す（改行は付けない）。
# ターミナルや IDE がリンクとして拾えるようにするためのもの。KEY=VALUE の値や
# docs の標準出力のような、機械が読むパスには使わないこと（file:// を付けると壊れる）。
#
# - 相対パスは $PWD に連結する。存在するディレクトリ（またはファイルの親）は
#   cd && pwd で正規化する。pwd は -P を付けない論理パスで、シンボリックリンクは解決しない
# - 存在しないパスは連結するだけ
# - % / 空白 / # / ? だけを百分率符号化する。日本語は UTF-8 のまま出す
#   （出力はもともと日本語なので、ASCII に揃える必要はない）
# 同じ規則の Python 版が PYROLES と SUMMARY_PY の中にある。変えるときは揃えること。
file_uri() {
	_fu_p=$1
	case $_fu_p in /*) ;; *) _fu_p="${PWD}/${_fu_p#./}" ;; esac
	if [ -d "$_fu_p" ]; then
		_fu_p=$(CDPATH='' cd -- "$_fu_p" && pwd)
	elif [ -d "${_fu_p%/*}" ]; then
		_fu_p="$(CDPATH='' cd -- "${_fu_p%/*}" && pwd)/${_fu_p##*/}"
	fi
	printf 'file://%s' "$(printf '%s' "$_fu_p" | sed -e 's/%/%25/g' -e 's/ /%20/g' -e 's/#/%23/g' -e 's/?/%3F/g')"
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
      --katachi <version> katachi のバージョン（既定: init が記録した版。
                          無ければ最新を取得）
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
      report:     module / role / tool / excluded / codebase-question / template

  sh katachi-install.sh docs [--small] [--refresh]
      ガイド全文（llms-full.txt）を作業用ディレクトリの cache/ に取得し、
      そのパスを出力する。すでにあれば取りに行かない。

  sh katachi-install.sh docs --api [--refresh]
      API リファレンス（Dokka）のルート索引（api-docs/llms.txt）を取得する。
      主要 API の一覧と、モジュールごとの索引・全文へのリンクを含む。

  sh katachi-install.sh docs --api <katachi|katachi-konsist> [--refresh]
      指定モジュールの API 全文（シグネチャと KDoc）を取得する。
      API のシグネチャや引数を確かめたいときに使う。

  sh katachi-install.sh check <項目 id>...
  sh katachi-install.sh uncheck <項目 id>...
      チェックリストの項目を完了 / 未完了にする。
      知らない id を渡すと、使える id を並べて止まる。

  sh katachi-install.sh warn <ステップ id> <文言>
  sh katachi-install.sh warn <ステップ id> --clear
      ステップに警告を付ける / 消す。付けたステップは黄色になり、
      完了していても閉じずに表示される。

  sh katachi-install.sh lint
      定義（architecture-test/src/test/kotlin）を読み、種類の違うファイルを1つの
      役割に抱えていそうなものを列挙する。check 3-2 / check 3-3 でも自動で走る。
      素朴なテキスト走査なので誤検知があり得る。失敗にはしない（常に 0 を返す）。

  sh katachi-install.sh compare-violations [<前のログ> <後のログ>]
      ./gradlew :architecture-test:test の出力を2つ比べ、検査結果（違反の件数と中身）が
      変わっていないかを確かめる。定義のリファクタリング（手順書 3-3）の前後に使う。
      既定は <作業用ディレクトリ>/tmp/test-before-refactor.log と test-after-refactor.log。
      [MissingDescription] の警告は、説明を書き足せば減るのが正しいので件数だけ出す。
      変わっていなければ 0、変わっていれば差分を出して 1 を返す。

  sh katachi-install.sh verify
      未記入のフィールドと未完了の項目を列挙する。何も残っていなければ
      最後のステップを完了にして 0 を返す。残っていれば 1 を返す。

  sh katachi-install.sh summary
      ユーザに返す文面を組み立てて出力する。

  sh katachi-install.sh --help

開発者向けの環境変数:
  KATACHI_MAVEN_LOCAL=1
      ~/.m2 に publish したリリース前の katachi で試すための口。
      init に付けると作業用ディレクトリに記録され、以降の scaffold も従う。
      「Gradle plugin は 0.2.0 以降」の確認を飛ばし（GitHub の最新を取ってきた
      版では飛ばさない）、scaffold が settings の pluginManagement と
      dependencyResolutionManagement の repositories に mavenLocal() を足す。
      利用者の導入では使わない。~/.m2 へは katachi のリポジトリで
      ./gradlew publishToMavenLocal -Pkatachi.skipSigning として出す。

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
		die "settings.gradle.kts / settings.gradle が見つかりません。Gradle のルートディレクトリで実行してください（いまいるのは $(file_uri "$PWD")）。"
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

# 開発者向けの KATACHI_MAVEN_LOCAL が効いているか。環境変数か、init が作業用ディレクトリに
# 残した記録のどちらかで有効になる。scaffold のたびに付け忘れると、~/.m2 にしか無い版を
# 解決できずに Gradle が落ちるため、init で一度付ければ済むようにしてある。
maven_local_enabled() {
	case "${KATACHI_MAVEN_LOCAL:-}" in
	1 | yes | true) return 0 ;;
	esac
	_ml_wd=$(resolve_workdir) || return 1
	[ -f "$_ml_wd/cache/maven-local" ]
}

# $1 の katachi に Gradle plugin があるかを確かめ、無ければ止まる。
# plugin は 0.2.0 で入った。それより前の版で scaffold まで進むと、Gradle が
# plugin を解決できずに落ち、原因が分かりにくい。KATACHI_MAVEN_LOCAL のときは
# 開発版を任意の版番号で試せるように飛ばす。ただし $2 が latest（GitHub の最新を
# 取ってきた版）なら飛ばさない。人が選んだ版ではないので、~/.m2 の開発版と
# 食い違っていても黙って進んでしまう。
require_plugin_version() {
	[ "${2:-}" != "latest" ] && maven_local_enabled && return 0
	_pv_mm=$(kotlin_major_minor "$1")
	[ -n "$_pv_mm" ] || return 0
	_pv_maj=${_pv_mm% *}
	_pv_min=${_pv_mm#* }
	if [ "$_pv_maj" -lt "$KATACHI_MIN_MAJOR" ] ||
		{ [ "$_pv_maj" -eq "$KATACHI_MIN_MAJOR" ] && [ "$_pv_min" -lt "$KATACHI_MIN_MINOR" ]; }; then
		die "katachi ${1} には Gradle plugin（${KATACHI_PLUGIN_ID}）がありません。このスクリプトが作る構成は Gradle plugin を使うため、katachi ${KATACHI_MIN_MAJOR}.${KATACHI_MIN_MINOR}.0 以降が必要です。
    ${KATACHI_RELEASES_PAGE} で ${KATACHI_MIN_MAJOR}.${KATACHI_MIN_MINOR}.0 以降が出ているかを確かめ、--katachi <version> で指定してやり直してください。"
	fi
}

# プロジェクトが使っている Kotlin のバージョンを推定して標準出力に出す。
# 1. version catalog の kotlin = "..."
# 2. 各 build ファイルの `kotlin("jvm") version "..."` / `org.jetbrains.kotlin...version "..."`
# "2.4.10" -> "2 4"。"2.3.0-RC" のような接尾辞は落とす。読めなければ空を返す。
kotlin_major_minor() {
	printf '%s' "$1" | sed -n 's/^\([0-9][0-9]*\)\.\([0-9][0-9]*\).*/\1 \2/p'
}

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
  手動で取得する場合は、このファイルを $(file_uri "$2") に置いてから同じコマンドをやり直してください。"
}

# ---------------------------------------------------------------- init

cmd_init() {
	init_workdir=""
	init_version=""
	init_version_source="explicit"
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
	note "プロジェクトルート: $(file_uri "$project_root")"
	note "settings:          $(file_uri "$SETTINGS_FILE")"

	# katachi のバージョン。plugin の無い版なら何も作らないうちに止まる。
	if [ -z "$init_version" ] && [ "$init_offline" = "no" ]; then
		init_version=$(fetch_latest_version)
		init_version_source="latest"
	fi
	if [ -z "$init_version" ]; then
		warn "katachi の最新バージョンを取得できませんでした。$KATACHI_RELEASES_PAGE を見て --katachi で指定してください。"
		init_version="UNKNOWN"
	else
		require_plugin_version "$init_version" "$init_version_source"
		note "katachi:            $init_version"
	fi
	if maven_local_enabled; then
		note "mavenLocal:         有効（開発者向け。scaffold が mavenLocal() を足す）"
	fi

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
	# KEY=VALUE の結果には素の絶対パスを出す（後続のステップは相対パスだと
	# 呼び出す場所に縛られる）。判定には指定どおりの init_workdir を使い続ける。
	init_workdir_abs=$(CDPATH='' cd -- "$init_workdir" && pwd)
	init_workdir_uri=$(file_uri "$init_workdir_abs")
	note "作業用ディレクトリ:  ${init_workdir_uri}"

	# katachi は「未追跡だが ignore もされていない」ファイルも検査する
	# （git ls-files --others --exclude-standard 相当）。作業用ディレクトリが
	# それに当たると、ステップ4で必ず [UnexpectedFile] として落ちる。
	# 「提案してください」だけだと後回しにされ、原因不明の失敗になる。
	if ! path_is_ignored "$init_workdir"; then
		warn "${init_workdir_uri} は git から見えています。このままだとステップ4で必ず失敗します。
    katachi は未追跡でも ignore されていないファイルを検査するため、この作業用ディレクトリ
    自体が [UnexpectedFile] になります。ユーザに .gitignore への追加を提案してください
    （勝手に書き換えないこと）。"
	fi

	# 作業用ディレクトリ以外にも、未追跡で ignore もされていないものがあれば同じ問題を起こす。
	# --directory でディレクトリ単位に畳むので、2万件あっても数行で済む。
	if is_git_repo; then
		# 作業用ディレクトリは上で専用の警告を出しているので落とす。git は --directory で
		# 一番浅い未追跡ディレクトリに畳むため、workdir が tmp/install-katachi なら
		# "tmp/" として出てくる。前方一致で両向きに判定する。
		_untracked=$(
			git ls-files --others --exclude-standard --directory --no-empty-directory 2>/dev/null |
				while IFS= read -r _entry; do
					_t=${_entry%/}
					[ "$_t" = "$init_workdir" ] && continue
					# 括弧を開く形にしてあるのは bash 3.2 対策。$( ) の中の case では
					# パターンの ) を置換の終わりと誤認して syntax error になる。
					case "$init_workdir/" in ("$_t"/*) continue ;; esac
					case "$_t/" in ("$init_workdir"/*) continue ;; esac
					printf '%s\n' "$_t"
				done
		)
		if [ -n "$_untracked" ]; then
			warn "未追跡で ignore もされていないものがあります。これらもステップ4で [UnexpectedFile] になります。
    宣言するか、.gitignore に足すかをユーザに確認してください。"
			printf '%s\n' "$_untracked" | while IFS= read -r _u; do
			printf '       %s\n' "$(file_uri "$_u")"
		done
		fi
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
	# KATACHI_MAVEN_LOCAL も同じく記録し、scaffold で付け忘れても効くようにする。
	case "${KATACHI_MAVEN_LOCAL:-}" in
	1 | yes | true) printf 'yes' >"$init_workdir/cache/maven-local" ;;
	esac
	# katachi の版も記録し、scaffold が --katachi なしでも同じ版を使うようにする。
	# 記録しないと scaffold が GitHub の最新を取り直し、init --katachi で固定した版と
	# 黙って食い違う。分からなかった（UNKNOWN）ときは古い記録を残さない。
	#
	# --katachi なしのやり直しでは、前回の記録を残す。最新で上書きすると、前回
	# --katachi で固定した版が黙って変わり、残したチェックリストの meta.version とも食い違う。
	_iv_recorded=""
	[ -s "$init_workdir/cache/version" ] && _iv_recorded=$(cat "$init_workdir/cache/version")
	if [ "$init_version_source" = "latest" ] && [ -n "$_iv_recorded" ] && [ "$init_force" = "no" ]; then
		if [ "$_iv_recorded" != "$init_version" ]; then
			note "                    → 前回の init が記録した ${_iv_recorded} を使い続けます（最新は ${init_version}。変えるなら --katachi を付けて実行し直す）"
		fi
		init_version="$_iv_recorded"
	elif [ "$init_version" = "UNKNOWN" ]; then
		rm -f "$init_workdir/cache/version"
	else
		printf '%s' "$init_version" >"$init_workdir/cache/version"
	fi

	# チェックリストとレポート。**すでにあるものは上書きしない。**
	# init をやり直したときに、記入済みの調査結果を消さないため。
	if [ "$init_offline" = "no" ]; then
		_cl="$init_workdir/check-list.html"
		_rp="$init_workdir/project-code-base-report.html"

		if [ -s "$_cl" ] && [ "$init_force" = "no" ]; then
			note "すでにあるので残しました: $(file_uri "$_cl")"
			# 版だけは今回の値にそろえる（--katachi で変えた場合）。記入済みの欄は触らない。
			[ "$init_version" = "UNKNOWN" ] || update_checklist_meta "$_cl" version "$init_version"
		else
			fetch_once "$KATACHI_DOCS/install/$KATACHI_LANG/install-check-list.html" "$_cl" yes
			write_checklist_data "$_cl" \
				"$init_workdir" "$init_version" "$project_root" \
				"${kotlin_version:-}" "$git_state"
			note "配置しました:        $(file_uri "$_cl")"
		fi

		if [ -s "$_rp" ] && [ "$init_force" = "no" ]; then
			note "すでにあるので残しました: $(file_uri "$_rp")"
		else
			fetch_once "$KATACHI_DOCS/install/$KATACHI_LANG/project-code-base-report-template.html" "$_rp" yes
			write_report_data "$_rp" "$project_root"
			note "配置しました:        $(file_uri "$_rp")"
		fi
	fi

	say ""
	say "== init の結果 =========================================="
	say "KATACHI_WORKDIR=$init_workdir_abs"
	say "KATACHI_VERSION=$init_version"
	say "KATACHI_PROJECT_ROOT=$project_root"
	say "KATACHI_KOTLIN=${kotlin_version:-}"
	say "KATACHI_GIT=$git_state"
	say "KATACHI_SETTINGS=$project_root/$SETTINGS_FILE"
	say "KATACHI_CLI=$init_workdir_abs/katachi-install.sh"
	say "KATACHI_LANG=$KATACHI_LANG"
	say "========================================================"
	say ""
	say "以降は ${init_workdir_uri}/katachi-install.sh を使ってください（再ダウンロードは不要です）。"
	say ""
	say "次: プロジェクトを解析して ${init_workdir_uri}/project-code-base-report.html を埋めてください。"
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
json_number() {
	case "$1" in
	'' | *[!0-9]*) printf 'null' ;;
	*) printf '%s' "$1" ;;
	esac
}

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
		warn "チェックリストのメタ欄を初期化できませんでした（python3 か jq が要ります）。$(file_uri "$_file") を直接確認してください。"
	fi
}

# チェックリストのメタ欄の1つ（$2）を $3 で上書きする。$1 はチェックリストのファイル。
# init のやり直しで残したチェックリストの version と、init が検出できず scaffold が
# --kotlin で受け取った kotlin を書き戻すのに使う。書き戻さないと verify が
# 未記入として数え続けたり、実際に使った版と食い違ったりする。
update_checklist_meta() {
	[ -f "$1" ] && [ -n "$3" ] || return 0
	_um_data="$1.meta.$$"
	printf '{\n  "meta": {\n    "%s": %s\n  }\n}\n' "$2" "$(json_value "$3")" >"$_um_data"
	if cmd_data merge "$1" "$_um_data" --id checklist >/dev/null 2>&1; then
		rm -f "$_um_data" "$1.bak"
	else
		rm -f "$_um_data"
		warn "チェックリストの meta.$2 を ${3} にできませんでした（python3 か jq が要ります）。data merge で直してください。"
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
		printf '    "fileCount": %s,\n' "$(json_number "$_count")"
		printf '    "gradleRoot": %s,\n' "$(json_value "$_root")"
		printf '    "gitRoot": %s\n' "$(json_value "$_gitroot")"
		printf '  }\n'
		printf '}\n'
	} >"$_data"

	if cmd_data merge "$_file" "$_data" --id report >/dev/null 2>&1; then
		rm -f "$_data" "$_file.bak"
	else
		rm -f "$_data"
		warn "レポートのメタ欄を初期化できませんでした。$(file_uri "$_file") を直接確認してください。"
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

	# 生成物のコメントとテスト名は init が記録した言語に合わせる。scaffold は
	# 作業用ディレクトリに置かれたこのスクリプトから呼ばれるので、記録を読める。
	if [ -z "${KATACHI_LANG_EXPLICIT:-}" ] && _sc_wd=$(resolve_workdir) && [ -f "$_sc_wd/cache/lang" ]; then
		KATACHI_LANG=$(cat "$_sc_wd/cache/lang")
	fi

	# katachi の版は --katachi > init が記録した版 > GitHub の最新 の順に決める。
	sc_version_source="explicit"
	if [ -z "$sc_version" ] && _sc_wd=$(resolve_workdir) && [ -s "$_sc_wd/cache/version" ]; then
		sc_version=$(cat "$_sc_wd/cache/version")
		sc_version_source="init"
	fi
	if [ -z "$sc_version" ]; then
		sc_version=$(fetch_latest_version)
		sc_version_source="latest"
	fi
	[ -n "$sc_version" ] ||
		die "katachi のバージョンが分かりません。$KATACHI_RELEASES_PAGE を見て --katachi で指定してください。"
	require_plugin_version "$sc_version" "$sc_version_source"
	if maven_local_enabled; then
		sc_maven_local="yes"
	else
		sc_maven_local="no"
	fi

	[ -n "$sc_kotlin" ] || sc_kotlin=$(detect_kotlin_version)
	[ -n "$sc_kotlin" ] ||
		die "Kotlin のバージョンを検出できませんでした。--kotlin で指定してください。"

	sc_mm=$(kotlin_major_minor "$sc_kotlin")
	[ -n "$sc_mm" ] || die "Kotlin のバージョンを読み取れませんでした: $sc_kotlin"
	sc_kmaj=${sc_mm% *}
	sc_kmin=${sc_mm#* }

	if [ "$sc_kmaj" -lt "$KOTLIN_MIN_MAJOR" ] ||
		{ [ "$sc_kmaj" -eq "$KOTLIN_MIN_MAJOR" ] && [ "$sc_kmin" -lt "$KOTLIN_MIN_MINOR" ]; }; then
		die "katachi $sc_version は Kotlin ${KOTLIN_MIN_MAJOR}.${KOTLIN_MIN_MINOR} 以降が必要です（このプロジェクトは ${sc_kotlin}）。
    それより古いコンパイラは katachi の metadata を読めず、architecture などの
    シンボルがすべて Unresolved reference になります。プロジェクトの Kotlin を
    上げてから、もう一度実行してください。"
	fi

	# context parameters は 2.4 で言語に入った。2.2 / 2.3 系では呼ぶ側に
	# -Xcontext-parameters が要り、2.4 以降で付けると
	# "The argument '-Xcontext-parameters' is redundant" の警告が出る
	# （allWarningsAsErrors な CI を落とす）。だから出し分ける。
	if [ "$sc_kmaj" -eq 2 ] && [ "$sc_kmin" -lt 4 ]; then
		sc_context_flag="yes"
	else
		sc_context_flag="no"
	fi

	if [ -e "$MODULE_DIR" ] && [ "$sc_force" = "no" ]; then
		die "$(file_uri "$MODULE_DIR")/ がすでに存在します。上書きするなら --force を付けてください。"
	fi

	pkg_path=$(printf '%s' "$sc_package" | tr '.' '/')/test/architecture
	src_dir="$MODULE_DIR/src/test/kotlin/$pkg_path"

	# Kotlin Gradle plugin がすでにルートのクラスパスにあるかで、モジュール側の書き方が
	# 変わる。判定は kotlin_plugin_placement を参照。
	#   root:   ルートか buildSrc が持っている。モジュールは版を書かない
	#   add:    誰も持っていない。ルートに apply false で足し、モジュールは版を書かない
	#   module: サブプロジェクトだけが版付きで宣言している。ルートには足さず、
	#           モジュールに版を書く
	kotlin_placement=$(kotlin_plugin_placement)
	if [ "$kotlin_placement" = "add" ]; then
		root_needs_plugin="yes"
	else
		root_needs_plugin="no"
	fi
	if [ "$kotlin_placement" = "module" ]; then
		sc_kotlin_plugin="kotlin(\"jvm\") version \"$sc_kotlin\""
	else
		sc_kotlin_plugin="kotlin(\"jvm\")"
	fi

	if sed 's://.*::' "$SETTINGS_FILE" 2>/dev/null |
		grep -qE "^[[:space:]]*include[[:space:](]+[^)]*[\"']:?$MODULE_DIR[\"']"; then
		settings_needs_include="no"
	else
		settings_needs_include="yes"
	fi

	# katachi の Gradle plugin は Maven Central に出ている。settings の
	# pluginManagement { repositories { } } に mavenCentral() が無いと解決できない。
	plugin_repos_missing=$(plugin_repositories_missing "$sc_maven_local")
	if [ -n "$plugin_repos_missing" ]; then
		settings_needs_plugin_repos="yes"
	else
		settings_needs_plugin_repos="no"
	fi

	say "作成する内容"
	note "パッケージ:   $sc_package"
	case "$sc_version_source" in
	init) note "katachi:      ${sc_version}（init が記録した版）" ;;
	latest) note "katachi:      ${sc_version}（GitHub の最新リリース）" ;;
	*) note "katachi:      $sc_version" ;;
	esac
	note "Kotlin:       $sc_kotlin"
	note "konsist:      $sc_konsist"
	note "モジュール:   $(file_uri "$MODULE_DIR")/"
	note "ソース:       $(file_uri "$src_dir")/"
	note "ルートに追加: $root_needs_plugin ($(file_uri "$ROOT_BUILD_FILE"))"
	[ "$kotlin_placement" = "module" ] &&
		note "              サブプロジェクトが Kotlin プラグインを版付きで宣言しているため、ルートには足さず :$MODULE_DIR に版を書きます
              Gradle が「The Kotlin Gradle plugin was loaded multiple times ...」と警告しますが、この構成では想定どおりです"
	note "include 追加: $settings_needs_include ($(file_uri "$SETTINGS_FILE"))"
	note "pluginManagement に追加: ${settings_needs_plugin_repos}${plugin_repos_missing:+（${plugin_repos_missing}）}"
	[ "$sc_maven_local" = "yes" ] && note "mavenLocal:   有効（開発者向け）"

	if [ "$sc_dry" = "yes" ]; then
		say ""
		say "--dry-run のため何も書きませんでした。"
		return 0
	fi

	mkdir -p "$src_dir"
	write_module_build "$sc_version" "$sc_konsist" "$sc_context_flag" "$sc_package" "$sc_kotlin_plugin"
	# **--force でも定義は上書きしない。** ここには人とエージェントが書いた
	# architecture { } が入っている。やり直しで消えると取り返しがつかない。
	if [ -s "$src_dir/ProjectArchitecture.kt" ]; then
		note "すでにあるので残しました: $(file_uri "$src_dir/ProjectArchitecture.kt")"
	else
		write_architecture_kt "$sc_package" "$src_dir"
	fi
	write_test_kt "$sc_package" "$src_dir" "$sc_konsist"

	ROOT_PLUGIN_MANUAL=""
	SETTINGS_MANUAL=""
	SETTINGS_INCLUDE_MANUAL=""
	[ "$root_needs_plugin" = "yes" ] && add_root_plugin "$sc_kotlin"
	[ "$settings_needs_include" = "yes" ] && add_settings_include
	[ "$settings_needs_plugin_repos" = "yes" ] && add_plugin_repositories "$plugin_repos_missing"
	DEPENDENCY_MAVEN_LOCAL=""
	[ "$sc_maven_local" = "yes" ] && add_dependency_maven_local

	say ""
	say "== 作成しました ========================================"
	sc_root_build_uri=$(file_uri "$ROOT_BUILD_FILE")
	sc_settings_uri=$(file_uri "$SETTINGS_FILE")
	say "$(file_uri "$MODULE_DIR/build.gradle.kts")"
	say "$(file_uri "$src_dir/ProjectArchitecture.kt")"
	say "$(file_uri "$src_dir/ProjectArchitectureTest.kt")"
	[ "$root_needs_plugin" = "yes" ] && say "${sc_root_build_uri} （Kotlin JVM プラグインを apply false で追加）"
	[ "$settings_needs_include" = "yes" ] && [ -z "$SETTINGS_INCLUDE_MANUAL" ] &&
		say "${sc_settings_uri} （include を追加）"
	[ "$settings_needs_plugin_repos" = "yes" ] && [ -z "$SETTINGS_MANUAL" ] &&
		say "${sc_settings_uri} （pluginManagement の repositories に ${plugin_repos_missing} を追加）"
	[ "$DEPENDENCY_MAVEN_LOCAL" = "added" ] &&
		say "${sc_settings_uri} （dependencyResolutionManagement の repositories に mavenLocal() を追加。開発者向け）"
	say "========================================================"
	if [ -n "$SETTINGS_MANUAL" ]; then
		warn "${sc_settings_uri} の pluginManagement { } の形を読み取れなかったため、自動で書き換えませんでした。"
		say ""
		say "${sc_settings_uri} の pluginManagement { repositories { } } に、次を足してください"
		say "（pluginManagement { } が無ければ、ファイルの先頭に作ってください）。"
		say ""
		say "$SETTINGS_MANUAL"
		say ""
		say "katachi の Gradle plugin（${KATACHI_PLUGIN_ID}）は Maven Central にあります。足すまで ./gradlew :$MODULE_DIR:test は失敗します。"
	fi
	if [ -n "$SETTINGS_INCLUDE_MANUAL" ]; then
		warn "${sc_settings_uri} の include 文の終わりを読み取れなかったため、include を自動で足しませんでした。"
		say ""
		say "次の1行を、${sc_settings_uri} の既存の include 文の後ろ（閉じ括弧より後）に足してください。"
		say ""
		say "$SETTINGS_INCLUDE_MANUAL"
		say ""
		say "足すまで ./gradlew :$MODULE_DIR:test は失敗します。"
	fi
	if [ "$DEPENDENCY_MAVEN_LOCAL" = "manual" ]; then
		warn "${sc_settings_uri} に dependencyResolutionManagement { repositories { } } が無いため、依存の解決先に mavenLocal() を足せませんでした（開発者向けの KATACHI_MAVEN_LOCAL）。"
		say "  :$MODULE_DIR が依存を解決している repositories { } に mavenLocal() を手で足してください。"
	fi
	if [ -n "${ROOT_PLUGIN_MANUAL:-}" ]; then
		warn "${sc_root_build_uri} は buildscript { } を持つため、自動で書き換えませんでした。"
		say ""
		say "次の1行を、${sc_root_build_uri} の buildscript { } の**後ろ**にある"
		say "plugins { } の中に足してください（plugins { } が無ければ作ってください）。"
		say ""
		say "$ROOT_PLUGIN_MANUAL"
		say ""
		say "足すまで ./gradlew :$MODULE_DIR:test は失敗します。"
	fi

	# init が Kotlin を検出できず --kotlin で受け取った場合、チェックリストの meta.kotlin が
	# 空のまま残り、verify が未記入として数え続ける。実際に使った版で埋める。
	if _sc_wd=$(resolve_workdir); then
		update_checklist_meta "$_sc_wd/check-list.html" kotlin "$sc_kotlin"
	fi

	say ""
	say "次: ./gradlew :$MODULE_DIR:test"
	say "    architecture { } が空なので、すべてのファイルが Unexpected として報告されて"
	say "    落ちるのが正常です。Unexpected 以外のエラーが出た場合だけが問題です。"
}

write_module_build() {
	_version="$1"
	_konsist="$2"
	_context_flag="$3"
	_package="$4"
	_kotlin_plugin="$5"

	# 生成物は利用者のリポジトリにそのまま残るので、コメントも --lang に合わせる
	# （テスト関数名と同じ理由。write_test_kt を参照）。
	case "$KATACHI_LANG" in
	ja)
		_cache_note='    // このタスクはキャッシュさせない。katachi は実行時にリポジトリ全体を歩くが、
    // Gradle から見える入力はこのモジュールのテストソースと classpath だけ。他の場所で
    // ファイルが増減してもキーが変わらないので、UP-TO-DATE / FROM-CACHE になって
    // 検査が一度も走らない。黙って通るガードは、ガードが無いより悪い。'
		_log_note='        // 違反の一覧は AssertionError のメッセージに入っている。FULL にしないと
        // "KatachiArchitectureAssertionError at ProjectArchitectureTest.kt:12" の1行しか出ず、
        // 中身を見るのに build/test-results/**/*.xml を読む羽目になる（CI のログでも同じ）。'
		_std_note='        // MissingDescription などの警告は stdout に出る。'
		_plugin_note='    // processor ごとのタスク（katachiDocs・katachiTemplate など）を足す。
    // katachi の依存は足さないので、下の dependencies { } は別に要る。'
		_arch_note='    // architecture { } を持つトップレベル val の完全修飾名。名前やパッケージを
    // 変えたらここも直す。'
		;;
	*)
		_cache_note='    // Never let this task be cached. katachi walks the whole repository when the test
    // runs, but the only inputs Gradle can see are the test sources and the classpath of
    // this module. A file added or moved anywhere else leaves the key unchanged, so Gradle
    // answers UP-TO-DATE or FROM-CACHE and the check never runs. A guard that silently
    // passes is worse than no guard at all.'
		_log_note='        // The violation list lives in the AssertionError message. Without FULL you only get
        // "KatachiArchitectureAssertionError at ProjectArchitectureTest.kt:12", and reading the
        // detail means opening build/test-results/**/*.xml (the same goes for CI logs).'
		_std_note='        // Warnings such as MissingDescription are printed to stdout.'
		_plugin_note='    // Adds a task per processor (katachiDocs, katachiTemplate, and so on). It does not add
    // katachi as a dependency, so the dependencies { } block below is still needed.'
		_arch_note='    // The fully qualified name of the top-level val holding architecture { }. Update it
    // if you rename the val or move it to another package.'
		;;
	esac

	if [ "$_context_flag" = "yes" ]; then
		case "$KATACHI_LANG" in
		ja)
			_ctx_note='    // Kotlin 2.4 未満では、context parameters を呼ぶ側にこのオプションが要る。
    // katachi の DSL（module / mainSourceSet / ktFile など）はすべて context parameters
    // なので、無いと1つも書けない。Kotlin を 2.4 以降に上げたらこの2行は消すこと
    // （2.4 以降で付けたままだと redundant の警告が出る）。'
			;;
		*)
			_ctx_note='    // Kotlin before 2.4 needs this on the calling side to use context parameters.
    // Every katachi DSL entry point (module / mainSourceSet / ktFile ...) is a contextual
    // declaration, so without it you cannot write a single one. Drop these two lines once
    // the project moves to Kotlin 2.4 or later - from 2.4 on the flag warns that it is
    // redundant.'
			;;
		esac
		_kotlin_block="kotlin {
    jvmToolchain($JVM_TOOLCHAIN)

$_ctx_note
    compilerOptions.freeCompilerArgs.add(\"-Xcontext-parameters\")
}"
	else
		_kotlin_block="kotlin {
    jvmToolchain($JVM_TOOLCHAIN)
}"
	fi

	if [ "$_konsist" = "yes" ]; then
		_konsist_line="    testImplementation(\"me.tbsten.katachi:katachi-konsist:$_version\")"
	else
		_konsist_line=""
	fi

	cat >"$MODULE_DIR/build.gradle.kts" <<EOF
plugins {
    $_kotlin_plugin
$_plugin_note
    id("$KATACHI_PLUGIN_ID") version "$_version"
}

$_kotlin_block

katachi {
$_arch_note
    architecture = "$_package.test.architecture.projectArchitecture"
}

tasks.test {
$_cache_note
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }

    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
$_log_note
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
$_std_note
        showStandardStreams = true
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

	# テスト名は利用者のリポジトリにそのまま残るので、--lang に合わせる。
	# 英語で進めている利用者に日本語の識別子を置いていかないため。
	case "$KATACHI_LANG" in
	ja)
		_test_name="プロジェクトの構成が定義どおりになっている"
		# 導入中は違反が数十〜百件出る。既定の 10 件で打ち切られると全体が見えず、
		# 定義を書き進められない。書き終えたら外す前提の一時設定なので TODO を付ける。
		_max_note='        // TODO: 導入が落ち着いたら maxViolations を外す（既定は 10 件）。'
		;;
	*)
		_test_name="the project matches its declaration"
		_max_note='        // TODO: drop maxViolations once the definition has settled (the default is 10).'
		;;
	esac

	if [ "$_konsist" = "yes" ]; then
		cat >"$_dir/ProjectArchitectureTest.kt" <<EOF
package $_package.test.architecture

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.assert
import org.junit.jupiter.api.Test

class ProjectArchitectureTest {
    @OptIn(ExperimentalKatachiApi::class)
    @Test
    fun \`$_test_name\`() {
$_max_note
        projectArchitecture.assert(FileConstraintCheck(), maxViolations = 200)
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
    fun \`$_test_name\`() {
$_max_note
        projectArchitecture.assert(maxViolations = 200)
    }
}
EOF
	fi
}

# Kotlin Gradle plugin の本体（org.jetbrains.kotlin:kotlin-gradle-plugin）に入っている
# プラグインの id の末尾。このどれかがクラスパスにあれば、kotlin("jvm") も同じ jar から
# 版なしで解決できる。plugin.serialization や plugin.compose は別の jar なので数えない。
KOTLIN_KGP_IDS="jvm|multiplatform|android|js|kapt"

# gradle/libs.versions.toml から、Kotlin Gradle plugin を指す別名を Kotlin DSL の
# アクセサの正規表現（libs\.plugins\.kotlin\.jvm など）にして1行ずつ出す。
#   $1 = plugins:   [plugins] で org.jetbrains.kotlin.<KOTLIN_KGP_IDS> を指すもの
#   $1 = libraries: [libraries] で kotlin-gradle-plugin を指すもの
# 別名は自由に付けられる（composables-ui は `jvm = { id = "org.jetbrains.kotlin.jvm" }`）
# ので、名前ではなく中身で判定する。
catalog_kotlin_accessors() {
	[ -f "gradle/libs.versions.toml" ] || return 0
	sed 's:#.*::' "gradle/libs.versions.toml" | awk -v want="$1" -v ids="$KOTLIN_KGP_IDS" '
		/^[[:space:]]*\[/ { section = $0; next }
		{
			hit = 0
			if (want == "plugins" && section ~ /^[[:space:]]*\[plugins\]/ &&
				$0 ~ ("[\"\047]org\\.jetbrains\\.kotlin\\.(" ids ")([\"\047:]|$)")) hit = 1
			if (want == "libraries" && section ~ /^[[:space:]]*\[libraries\]/ &&
				($0 ~ /org\.jetbrains\.kotlin:kotlin-gradle-plugin/ ||
				 ($0 ~ /org\.jetbrains\.kotlin["\047]/ && $0 ~ /name[[:space:]]*=[[:space:]]*["\047]kotlin-gradle-plugin["\047]/))) hit = 1
			if (!hit) next
			key = $0
			sub(/^[[:space:]]*/, "", key)
			sub(/[[:space:]]*=.*/, "", key)
			gsub(/["\047]/, "", key)
			# Gradle は - _ . を区切りとしてアクセサの . に変える。
			gsub(/[-_.]/, "\\.", key)
			if (want == "plugins") print "libs\\.plugins\\." key "([^A-Za-z0-9_.]|$)"
			else print "libs\\." key "([^A-Za-z0-9_.]|$)"
		}
	'
}

# catalog_kotlin_accessors の結果を | でつないで1つの正規表現にする。無ければ空。
# 引数は catalog_kotlin_accessors に渡す種類（plugins / libraries）を1つ以上。
catalog_kotlin_accessors_joined() {
	for _cj_kind in "$@"; do
		catalog_kotlin_accessors "$_cj_kind"
	done | tr '\n' '|' | sed 's/|$//'
}

# $1 のトップレベルの plugins { } と buildscript { } の中身だけを出す（行コメントは落とす）。
# 宣言がクラスパスに効くのはこの2か所だけ。subprojects { } の中の
# plugins.withId("org.jetbrains.kotlin.jvm") / pluginManager.withPlugin(...) /
# apply(plugin = "...") は「そのプラグインが当たっていたら」という参照で、載せはしない。
#
# 行コメントは `//` の前が `:` でないものだけ落とす。`uri("https://...")` の `//` から先を
# 消すと閉じ括弧 `}` も消え、括弧の深さがずれて後ろの plugins { } を見落とす。
root_plugin_blocks() {
	sed -E 's#(^|[^:])//.*#\1#' "$1" | awk '
		!inblk && depth == 0 && /^[[:space:]]*(plugins|buildscript)[[:space:]]*\{/ { inblk = 1 }
		{
			line = $0
			if (inblk) print line
			opened = gsub(/\{/, "{", line)
			closed = gsub(/\}/, "}", line)
			depth += opened - closed
			if (depth < 0) depth = 0
			if (inblk && depth == 0) inblk = 0
		}
	'
}

# $1（ルートの build ファイル）が、Kotlin Gradle plugin をクラスパスに載せる宣言を含むか。
# 見るのはトップレベルの plugins { } と buildscript { } の中だけ（root_plugin_blocks）。
#   - id("org.jetbrains.kotlin.jvm|multiplatform|android|js|kapt") / kotlin("...")
#   - version catalog の別名（alias(libs.plugins.<何でも>)）
#   - buildscript { } の classpath の kotlin-gradle-plugin
kotlin_plugin_declared_in() {
	[ -f "$1" ] || return 1
	_kd_pat="org\\.jetbrains\\.kotlin\\.(${KOTLIN_KGP_IDS})([\"']|$)|kotlin\\(\"(${KOTLIN_KGP_IDS})\"\\)|kotlin-gradle-plugin|libs\\.plugins\\.kotlin[.]?([jJ]vm|[mM]ultiplatform|[aA]ndroid)([^A-Za-z0-9_.]|$)"
	_kd_accs=$(catalog_kotlin_accessors_joined plugins libraries)
	[ -n "$_kd_accs" ] && _kd_pat="${_kd_pat}|${_kd_accs}"
	root_plugin_blocks "$1" | grep -qE "$_kd_pat"
}

# buildSrc が Kotlin Gradle plugin を依存に持っているか。buildSrc の実行時クラスパスは
# すべての build スクリプトの親になるので、持っていればルートに足さなくても載っている
# （kilua）。buildSrc 自身の plugins { kotlin("jvm") } は buildSrc のコンパイル用で、
# プロジェクトのクラスパスには載らないので数えない。compileOnly も同じ理由で数えない。
kotlin_plugin_in_buildsrc() {
	for _kb_f in buildSrc/build.gradle.kts buildSrc/build.gradle; do
		[ -f "$_kb_f" ] || continue
		_kb_pat="kotlin-gradle-plugin|kotlin\\(\"gradle-plugin\""
		_kb_accs=$(catalog_kotlin_accessors_joined libraries)
		[ -n "$_kb_accs" ] && _kb_pat="${_kb_pat}|${_kb_accs}"
		sed 's://.*::' "$_kb_f" | grep -E '(implementation|api|runtimeOnly)[[:space:](]' |
			grep -qE "$_kb_pat" && return 0
	done
	return 1
}

# ルート以外のプロジェクトのどれかが、Kotlin Gradle plugin のプラグインを**版付きで**
# 要求しているか（kotlin("multiplatform") version "..." / alias(libs.plugins.<別名>)）。
# このときルートに版付きで足すと、そのプロジェクト側の要求が "already on the classpath
# with an unknown version" で落ちる（kdoctor）。同じ版でも、id が違えば落ちる。
#
# 別の settings を持つディレクトリ（includeBuild される build-logic など）は別のビルドなので見ない。
kotlin_plugin_requested_by_subprojects() {
	_ks_pat="kotlin\\(\"(${KOTLIN_KGP_IDS})\"\\)[[:space:]]+version|id[[:space:](]+[\"']org\\.jetbrains\\.kotlin\\.(${KOTLIN_KGP_IDS})[\"']\\)?[[:space:]]+version"
	_ks_accs=$(catalog_kotlin_accessors_joined plugins)
	[ -n "$_ks_accs" ] && _ks_pat="${_ks_pat}|alias\\((${_ks_accs})"
	# '.?*' は隠しディレクトリ。'.*' だと起点の . 自体に当たって何も見なくなる。
	find . -maxdepth 6 \
		\( -name build -o -name node_modules -o -name buildSrc -o -name '.?*' -o -name "$MODULE_DIR" \) -prune -o \
		\( -name 'build.gradle.kts' -o -name 'build.gradle' \) -print 2>/dev/null |
		while IFS= read -r _ks_f; do
			_ks_d=${_ks_f%/*}
			# ルートの build ファイルは kotlin_plugin_declared_in が見ている。
			[ "$_ks_d" = "." ] && continue
			_ks_other_build="no"
			while [ "$_ks_d" != "." ] && [ -n "$_ks_d" ]; do
				if [ -f "$_ks_d/settings.gradle.kts" ] || [ -f "$_ks_d/settings.gradle" ]; then
					_ks_other_build="yes"
					break
				fi
				_ks_d=${_ks_d%/*}
			done
			[ "$_ks_other_build" = "yes" ] && continue
			if sed 's://.*::' "$_ks_f" | grep -qE "$_ks_pat"; then
				printf '%s\n' "$_ks_f"
				break
			fi
		done | grep -q .
}

# :architecture-test の kotlin("jvm") をどう解決させるかを決めて出す。
#   root   ルートの build ファイル（の plugins { } か buildscript { }）か buildSrc がすでに
#          Kotlin Gradle plugin を持っている
#   add    どこにも無い。ルートに id("org.jetbrains.kotlin.jvm") version ... apply false を足す
#   module サブプロジェクトだけが版付きで持っている。ルートには足さず、モジュールに版を書く
#
# コメントを落としてから見る。`// TODO: org.jetbrains.kotlin.jvm ...` を「宣言済み」と
# 誤判定すると、ルートに何も足されないまま版なしの kotlin("jvm") が書かれ、Gradle が
# plugin not found で落ちる。
kotlin_plugin_placement() {
	if kotlin_plugin_declared_in "$ROOT_BUILD_FILE" || kotlin_plugin_in_buildsrc; then
		echo root
	elif kotlin_plugin_requested_by_subprojects; then
		echo module
	else
		echo add
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
		die "$(file_uri "$ROOT_BUILD_FILE") の書き換えに失敗しました。元に戻しました。"
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
	die "$(file_uri "$ROOT_BUILD_FILE") の書き換えに失敗しました。元に戻しました。"
}

add_settings_include() {
	if [ "$SETTINGS_DSL" = "groovy" ]; then
		_inc_line="include '$MODULE_DIR'"
	else
		_inc_line="include(\"$MODULE_DIR\")"
	fi

	# 既存の include の並びの直後に入れる。素朴に末尾へ足すと
	# dependencyResolutionManagement { } などの後ろに独りで置かれ、並びが崩れる。
	# 手順書が生成物の書き換えを禁じている以上、位置はこちらで合わせる。
	#
	# 入れる位置は最後の include **文の終わり**。include は複数行に渡ることがある
	# （`include(` の次の行から並べる形や、Groovy の `include ':a',` の続き）。文の途中に
	# 入れると settings が構文エラーになる。終わりを判定できなければ書き換えずに指示を出す。
	# include が1つも無ければ末尾に足す。
	_inc_at=$(settings_include_end "$SETTINGS_FILE")

	if [ "$_inc_at" = "?" ]; then
		SETTINGS_INCLUDE_MANUAL="$_inc_line"
	elif [ "$_inc_at" != "0" ]; then
		_inc_tmp="$SETTINGS_FILE.katachi.$$"
		awk -v n="$_inc_at" -v line="$_inc_line" '
			NR == n { print; print line; next }
			{ print }
		' "$SETTINGS_FILE" >"$_inc_tmp" && mv "$_inc_tmp" "$SETTINGS_FILE"
	else
		printf '\n%s\n' "$_inc_line" >>"$SETTINGS_FILE"
	fi
}

# settings の中で、トップレベル（{ } の外）にある最後の include 文が終わる行番号を出す。
# include が無ければ 0、文の終わりを判定できなければ ? を出す。
#
# 文の終わり = 丸括弧の深さが 0 に戻り、行末が `,` でない行。
#   include(":a", ":b")            -> その行
#   include(\n  ":a",\n)            -> `)` の行
#   include ':a',\n    ':b'         -> `':b'` の行（Groovy）
# include の直後の1文字で includeBuild を弾いている（B は [^A-Za-z0-9_] に入らない）。
# { } の中の include（if や forEach の中）は数えない。そこに足すと条件つきになる。
# 行コメントは先に落とす。文字列の中の括弧は数えない。
settings_include_end() {
	sed 's://.*::' "$1" | awk '
		function scan(s,    i, c, q) {
			q = ""
			for (i = 1; i <= length(s); i++) {
				c = substr(s, i, 1)
				if (q != "") {
					if (c == "\\") { i++; continue }
					if (c == q) q = ""
					continue
				}
				if (c == "\"" || c == "\047") { q = c; continue }
				if (c == "(") paren++
				else if (c == ")") paren--
				else if (c == "{") brace++
				else if (c == "}") brace--
			}
		}
		{
			line = $0
			sub(/[[:space:]]+$/, "", line)
			if (!open && brace == 0 && line ~ /^[[:space:]]*include[^A-Za-z0-9_]/) {
				open = 1
				paren = 0
			}
			scan(line)
			if (open && paren <= 0 && line !~ /,$/) {
				open = 0
				last = NR
			}
		}
		END {
			if (open) print "?"
			else print last + 0
		}
	'
}

# settings の中のトップレベルのブロック（pluginManagement / dependencyResolutionManagement）と、
# その直下の repositories { } の位置を調べる。
# 出力: "<開始行> <終了行> <repositories の開始行> <repositories の終了行> <1行ブロック>"
# 見つからないものは 0。`{` と `}` を数えるだけなので、文字列の中の括弧には弱い。
# 1行に収まったブロック（`repositories { mavenCentral() }` など）は書き換えずに指示を出す。
settings_block_info() {
	sed 's://.*::' "$1" | awk -v outer="$2" '
		{
			line = $0
			o = gsub(/\{/, "{", line)
			c = gsub(/\}/, "}", line)
			if (!start && depth == 0 && $0 ~ ("^[[:space:]]*" outer "[[:space:]]*[{]")) {
				start = NR
				if (o > 0 && o == c) oneline = 1
			}
			if (start && !end && !repo && depth == 1 && $0 ~ /^[[:space:]]*repositories[[:space:]]*[{]/) {
				repo = NR
				if (o > 0 && o == c) { oneline = 1; repo_end = NR }
			}
			depth += o - c
			if (repo && !repo_end && NR > repo && depth == 1) repo_end = NR
			if (start && !end && depth == 0) end = NR
		}
		END { printf "%d %d %d %d %d\n", start, end, repo, repo_end, oneline }
	'
}

# $1 の $2 行目から $3 行目に、$4（`mavenCentral()` など）が書かれているか。
settings_range_has() {
	sed -n "${2},${3}p" "$1" | sed 's://.*::' | grep -qF "$4"
}

# pluginManagement { repositories { } } に足りないものを、空白区切りで出す。
# 何も足りなければ空。$1 = mavenLocal() も要るか（yes / no）。
plugin_repositories_missing() {
	_pr_ml="$1"
	set -- $(settings_block_info "$SETTINGS_FILE" pluginManagement)
	_pr_out=""
	for _pr_r in mavenLocal mavenCentral; do
		[ "$_pr_r" = "mavenLocal" ] && [ "$_pr_ml" != "yes" ] && continue
		if [ "$3" -eq 0 ] || ! settings_range_has "$SETTINGS_FILE" "$3" "$4" "${_pr_r}()"; then
			_pr_out="${_pr_out:+$_pr_out }${_pr_r}()"
		fi
	done
	printf '%s\n' "$_pr_out"
}

# pluginManagement { repositories { } } に $1（空白区切り）を足す。
#
# - pluginManagement { } が無い: 先頭（import の後ろ）に作る。repositories { } を書くと
#   既定の Gradle Plugin Portal が外れるので、gradlePluginPortal() も一緒に書く
# - あるが repositories { } が無い: 同じ理由で gradlePluginPortal() と一緒に足す
# - repositories { } がある: mavenLocal() は先頭に（開発版を先に拾うため）、
#   mavenCentral() は末尾に足す
# 形が読めなければ書き換えず、SETTINGS_MANUAL に足すべき行を入れて返す。
add_plugin_repositories() {
	_want="$1"
	set -- $(settings_block_info "$SETTINGS_FILE" pluginManagement)
	_start="$1"
	_repo="$3"
	_repo_end="$4"
	_oneline="$5"

	_has_local="no"
	_has_central="no"
	for _r in $_want; do
		case "$_r" in
		mavenLocal*) _has_local="yes" ;;
		mavenCentral*) _has_central="yes" ;;
		esac
	done

	if [ "$_oneline" = "1" ]; then
		SETTINGS_MANUAL="$_want"
		return 0
	fi

	_tmp="$SETTINGS_FILE.katachi.$$"
	cp "$SETTINGS_FILE" "$SETTINGS_FILE.bak"

	if [ "$_start" -eq 0 ] || [ "$_repo" -eq 0 ]; then
		# 新しく書く repositories { }。pluginManagement { } は settings の最初の文で
		# なければならないので、作るときは import の後ろに置く。
		_last_import=$(grep -n '^import[[:space:]]' "$SETTINGS_FILE" | tail -n 1 | cut -d: -f1)
		awk -v start="$_start" -v at="${_last_import:-0}" -v local="$_has_local" '
			function repos() {
				print "    repositories {"
				if (local == "yes") print "        mavenLocal()"
				print "        gradlePluginPortal()"
				print "        mavenCentral()"
				print "    }"
			}
			function block() { print "pluginManagement {"; repos(); print "}"; print "" }
			start == 0 && at == 0 && NR == 1 { block() }
			{ print }
			start == 0 && at > 0 && NR == at { print ""; block() }
			start > 0 && NR == start { repos() }
			END { if (start == 0 && at == 0 && NR == 0) block() }
		' "$SETTINGS_FILE" >"$_tmp"
	else
		# 既存の repositories { } に足す。インデントは repositories の行に揃える。
		_indent=$(sed -n "${_repo}p" "$SETTINGS_FILE" | sed 's/[^[:space:]].*//')
		awk -v first="$_repo" -v last="$_repo_end" -v ind="$_indent    " \
			-v local="$_has_local" -v central="$_has_central" '
			NR == last && central == "yes" { print ind "mavenCentral()" }
			{ print }
			NR == first && local == "yes" { print ind "mavenLocal()" }
		' "$SETTINGS_FILE" >"$_tmp"
	fi

	if [ -s "$_tmp" ]; then
		mv "$_tmp" "$SETTINGS_FILE"
		rm -f "$SETTINGS_FILE.bak"
	else
		rm -f "$_tmp"
		mv "$SETTINGS_FILE.bak" "$SETTINGS_FILE"
		die "$(file_uri "$SETTINGS_FILE") の書き換えに失敗しました。元に戻しました。"
	fi
}

# 開発者向け（KATACHI_MAVEN_LOCAL）。依存の解決先にも mavenLocal() を足す。
# 足せたら DEPENDENCY_MAVEN_LOCAL=added、すでにあれば present、
# dependencyResolutionManagement { repositories { } } が無い・読めなければ manual。
add_dependency_maven_local() {
	set -- $(settings_block_info "$SETTINGS_FILE" dependencyResolutionManagement)
	if [ "$1" -eq 0 ] || [ "$3" -eq 0 ] || [ "$5" = "1" ]; then
		DEPENDENCY_MAVEN_LOCAL="manual"
		return 0
	fi
	if settings_range_has "$SETTINGS_FILE" "$3" "$4" 'mavenLocal()'; then
		DEPENDENCY_MAVEN_LOCAL="present"
		return 0
	fi
	_indent=$(sed -n "${3}p" "$SETTINGS_FILE" | sed 's/[^[:space:]].*//')
	_tmp="$SETTINGS_FILE.katachi.$$"
	awk -v at="$3" -v ind="$_indent    " '
		{ print }
		NR == at { print ind "mavenLocal()" }
	' "$SETTINGS_FILE" >"$_tmp" && mv "$_tmp" "$SETTINGS_FILE"
	DEPENDENCY_MAVEN_LOCAL="added"
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
	_html_uri=$(file_uri "$_html")
	[ -f "$_html" ] || die "ファイルがありません: ${_html_uri}"

	if [ "$_action" != "get" ]; then
		_json="${1:-}"
		shift 2>/dev/null || true
		[ -n "$_json" ] || die "data $_action: JSON ファイルを指定してください。"
		[ -f "$_json" ] || die "ファイルがありません: $(file_uri "$_json")"
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
			die "${_html_uri} に id=\"$_id\" の JSON ブロックが見つかりません。"
		return 0
	fi

	validate_json "$_json" ||
		die "$(file_uri "$_json") が JSON として読めません。直してからやり直してください。"

	_merged=""
	if [ "$_action" = "merge" ]; then
		_merged="$_json.merged.$$"
		if ! extract_json "$_html" "$_id" >"$_merged.base" 2>/dev/null; then
			rm -f "$_merged.base"
			die "${_html_uri} に id=\"$_id\" の JSON ブロックが見つかりません。開始タグと閉じタグは、それぞれ行頭に単独で置かれている必要があります。"
		fi
		if ! merge_json "$_merged.base" "$_json" "$_merged"; then
			rm -f "$_merged" "$_merged.base"
			die "JSON をマージできませんでした。"
		fi
		rm -f "$_merged.base"
		_json="$_merged"
	fi

	extract_json "$_html" "$_id" >/dev/null ||
		die "${_html_uri} に id=\"$_id\" の JSON ブロックが見つかりません。"

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
		die "${_html_uri} の書き換えに失敗しました。元に戻しました。"
	fi

	_check="$_html.check.$$"
	if extract_json "$_html" "$_id" >"$_check" 2>/dev/null && validate_json "$_check"; then
		rm -f "$_check" ${_merged:+"$_merged"}
		say "${_html_uri} の id=\"$_id\" を更新しました（元は ${_html_uri}.bak）。"
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
	_api="no"
	_api_module=""
	while [ $# -gt 0 ]; do
		case "$1" in
		--small)
			_small="yes"
			shift
			;;
		--api)
			_api="yes"
			shift
			# 次の引数がモジュール名なら消費する。オプション（-- で始まる）ならルート索引のまま。
			case "${1:-}" in
			katachi | katachi-konsist)
				_api_module="$1"
				shift
				;;
			esac
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

	if [ "$_api" = "yes" ]; then
		if [ -n "$_api_module" ]; then
			_name="api-$_api_module-llms-full.txt"
			_url="$KATACHI_DOCS/api-docs/$_api_module/llms-full.txt"
		else
			_name="api-llms.txt"
			_url="$KATACHI_DOCS/api-docs/llms.txt"
		fi
	elif [ "$_small" = "yes" ]; then
		_name="llms-small.txt"
		_url="$KATACHI_DOCS/$_name"
	else
		_name="llms-full.txt"
		_url="$KATACHI_DOCS/$_name"
	fi
	_dest="$WORKDIR/cache/$_name"

	fetch_once "$_url" "$_dest" "$_refresh"
	printf '%s\n' "$_dest"
}

# ---------------------------------------------------------------- 役割の点検

# 定義のソースを読み、種類の違うファイルを1つの役割に抱えていそうなものを列挙する。
# 「1 種類のファイル = 1 役割、種類の違うものを束ねるのは group」を機械的に拾える範囲で
# 拾うためのもの。Kotlin の構文解析はせず、素朴なテキスト走査なので誤検知があり得る。
# だから**失敗にはせず、常に 0 を返す。** check 3-2 / 3-3 のたびに走り、lint で単独でも呼べる。
#
# 拾う規則（どれも実地テストの定義で取り違えを拾い、正しい定義を誤検知しないことを確かめた）:
#   - README・LICENSE・settings.gradle(.kts)・gradlew・.gitignore のような、名前で種類が
#     決まるファイルが、2種類以上同じ役割にある
#   - .claude / .github / .run のようなツールごとの設定ディレクトリを、1つの役割で
#     anyFile() / ignore() している
#   - 同じ役割に本体（main）とテスト（test）の置き場所がある
# あわせて、Gradle のファイルを手書きの役割で宣言していれば gradle() を勧める。
lint_roles() {
	command -v python3 >/dev/null 2>&1 || {
		note "役割の点検は python3 が無いため飛ばしました。"
		return 0
	}
	# プロジェクトルートは init がチェックリストに記録したものを使う。
	_lr_json=""
	_lr_wd=$(resolve_workdir) || _lr_wd=""
	if [ -n "$_lr_wd" ] && [ -f "$_lr_wd/check-list.html" ]; then
		_lr_json="$_lr_wd/cache/.lint-cl.$$"
		extract_json "$_lr_wd/check-list.html" checklist >"$_lr_json" 2>/dev/null || :
	fi
	python3 - "$_lr_json" "$MODULE_DIR" <<'PYROLES' || :
import os, re, sys

# 役割（"Name" { }）ごとに、layout { } の中で宣言しているファイルを集め、
# 種類の違うファイルを抱えていそうなものを列挙する。Kotlin の構文解析はしない。
# 文字列・コメントを飛ばして括弧を数えるだけの素朴な走査なので、誤検知はあり得る。

# 定義のディレクトリ。チェックリストに init が記録したプロジェクトルートから決める。
# 読めなければカレントディレクトリ（init / scaffold と同じく Gradle のルートで呼ばれる前提）。
project_root = os.getcwd()
try:
    import json
    meta = json.load(open(sys.argv[1], encoding="utf-8")).get("meta") or {}
    if meta.get("projectRoot"):
        project_root = meta["projectRoot"]
except Exception:
    pass
root = os.path.join(project_root, sys.argv[2], "src", "test", "kotlin")


def file_uri(path):
    # sh 側の file_uri と同じ規則。人に見せるパスを file:// の絶対 URI にする。
    # シンボリックリンクは解決しない（abspath は論理パスのまま正規化する）。
    p = os.path.abspath(os.path.join(project_root, path))
    for a, b in (("%", "%25"), (" ", "%20"), ("#", "%23"), ("?", "%3F")):
        p = p.replace(a, b)
    return "file://" + p


KINDS = [
    (r"^settings\.gradle(\.kts)?$", "settings.gradle(.kts)", True),
    (r"^build\.gradle(\.kts)?$", "build.gradle(.kts)", True),
    (r"^gradle\.properties$", "gradle.properties", True),
    (r"^gradlew(\.bat)?$", "gradlew", True),
    (r"^gradle-wrapper\.jar$", "gradle-wrapper.jar", True),
    (r"^gradle-wrapper\.properties$", "gradle-wrapper.properties", True),
    (r"\.versions\.toml$", "version catalog", True),
    (r"^local\.properties$", "local.properties", False),
    (r"^readme", "README", False),
    (r"^(licen[cs]e|copying|notice)", "LICENSE", False),
    (r"^contributing", "CONTRIBUTING", False),
    (r"^(changelog|changes|release[-_]?notes)", "CHANGELOG", False),
    (r"^code[-_]of[-_]conduct", "CODE_OF_CONDUCT", False),
    (r"^security\.md$", "SECURITY.md", False),
    (r"^(claude|agents|gemini|copilot-instructions)\.md$", "AI エージェント向けの指示", False),
    (r"^\.gitignore$", ".gitignore", False),
    (r"^\.gitattributes$", ".gitattributes", False),
    (r"^\.editorconfig$", ".editorconfig", False),
    (r"^androidmanifest\.xml$", "AndroidManifest.xml", False),
    (r"^proguard-rules\.pro$", "proguard-rules.pro", False),
    (r"^renovate\.json5?$", "renovate", False),
    (r"^gemfile(\.lock)?$", "Gemfile", False),
    (r"^dangerfile", "Dangerfile", False),
    (r"^(package(-lock)?\.json|yarn\.lock|pnpm-lock\.yaml)$", "npm", False),
    (r"^(detekt|lint)[^/]*\.(yml|yaml|xml)$", "静的解析の設定", False),
]


def kind_of(name):
    base = name.rsplit("/", 1)[-1].lower()
    for pattern, label, gradle in KINDS:
        if re.search(pattern, base):
            return label, gradle
    return None, False


def tokenize(src):
    toks = []
    i, n, line = 0, len(src), 1
    while i < n:
        c = src[i]
        if c == "\n":
            line += 1
            i += 1
        elif c in " \t\r":
            i += 1
        elif src.startswith("//", i):
            j = src.find("\n", i)
            i = n if j < 0 else j
        elif src.startswith("/*", i):
            j = src.find("*/", i + 2)
            j = n if j < 0 else j + 2
            line += src.count("\n", i, j)
            i = j
        elif src.startswith('"""', i):
            j = src.find('"""', i + 3)
            j = n if j < 0 else j + 3
            while j < n and src[j] == '"':
                j += 1
            line += src.count("\n", i, j)
            toks.append(("str", None, line))
            i = j
        elif c == '"':
            j, buf, depth = i + 1, [], 0
            while j < n:
                d = src[j]
                if depth == 0 and d == "\\":
                    buf.append(src[j:j + 2])
                    j += 2
                    continue
                if depth == 0 and d == '"':
                    break
                if d == "\n":
                    break
                if src.startswith("${", j):
                    depth += 1
                    buf.append("${")
                    j += 2
                    continue
                if depth and d == "}":
                    depth -= 1
                buf.append(d)
                j += 1
            toks.append(("str", "".join(buf), line))
            i = j + 1
        elif c == "'":
            j = src.find("'", i + 1 + (1 if src[i + 1:i + 2] == "\\" else 0))
            i = n if j < 0 else j + 1
        elif c.isalpha() or c == "_" or c == "`":
            j = i + 1
            if c == "`":
                j = src.find("`", i + 1) + 1
            else:
                while j < n and (src[j].isalnum() or src[j] == "_"):
                    j += 1
            toks.append(("id", src[i:j], line))
            i = j
        else:
            toks.append(("op", c, line))
            i += 1
    return toks


class Role:
    def __init__(self, name, path, line):
        self.name, self.path, self.line = name, path, line
        self.files = []      # (名前, 行)
        self.catchall = []   # (anyFile / ignore, 対象, 行)
        self.places = []     # 宣言の場所（本体とテストの同居を見る）


def scan(path, roles):
    src = open(path, encoding="utf-8", errors="replace").read()
    toks = tokenize(src)
    stack = []  # (種類, 名前)
    role = None
    for k, (t, v, line) in enumerate(toks):
        prev = toks[k - 1] if k > 0 else (None, None, 0)
        prev2 = toks[k - 2] if k > 1 else (None, None, 0)
        prev3 = toks[k - 3] if k > 2 else (None, None, 0)
        kinds = [s[0] for s in stack]
        in_layout = "layout" in kinds
        if t == "op" and v == "{":
            if prev[0] == "str" and prev[1] is not None:
                if in_layout:
                    stack.append(("dir", prev[1]))
                elif "role" in kinds or "other" in kinds or re.search(r"\s", prev[1]):
                    stack.append(("other", None))
                else:
                    role = Role(prev[1], path, prev[2])
                    roles.append(role)
                    stack.append(("role", prev[1]))
            elif prev == ("id", "group", prev[2]) and prev2[1] == ".":
                stack.append(("group", None))
            elif prev[0] == "id" and prev[1] == "module" and prev2[1] == "." and in_layout:
                stack.append(("dir", prev3[1] or "?"))
            elif prev[0] == "id" and prev[1] == "layout" and "role" in kinds:
                stack.append(("layout", None))
            elif prev[0] == "id" and prev[1] in ("konsist", "template"):
                stack.append(("other", None))
            else:
                stack.append(("block", None))
            continue
        if t == "op" and v == "}":
            if stack:
                popped = stack.pop()
                if popped[0] == "role":
                    role = None
            continue
        if not (in_layout and role is not None):
            continue
        dirs = "/".join(s[1] for s in stack if s[0] == "dir" and s[1])
        # `mainSourceSet / kotlin / "x".ktFile()` の / の連なりも場所として拾う
        chain, b = [], k - 1
        while b >= 0 and (toks[b][1] == "/" or toks[b][1] == "." or toks[b][0] in ("str", "id")):
            if toks[b][0] in ("str", "id") and toks[b][1]:
                chain.append(toks[b][1])
            if toks[b][0] in ("str", "id") and b > 0 and toks[b - 1][1] not in ("/", "."):
                break
            b -= 1
        where = dirs + "/" + "/".join(reversed(chain))
        nxt = toks[k + 1] if k + 1 < len(toks) else (None, None, 0)
        if t == "id" and v in ("file", "ktFile", "ktsFile") and prev[1] == "." and nxt[1] == "(":
            if prev2[0] == "str" and prev2[1] is not None:
                name = prev2[1] + {"file": "", "ktFile": ".kt", "ktsFile": ".kts"}[v]
                role.files.append((name, line))
                role.places.append(where)
        elif t == "id" and v in ("anyFile", "ignore") and nxt[1] == "(":
            target = dirs
            if v == "ignore" and prev[1] == "." and prev2[0] == "str" and prev2[1]:
                target = (dirs + "/" if dirs else "") + prev2[1]
            role.catchall.append((v, target or ".", line))
            role.places.append(where + "/" + (target or ""))


roles = []
if not os.path.isdir(root):
    print("役割の点検: 定義のディレクトリがありません（%s）。scaffold の後に実行してください。" % file_uri(root))
    sys.exit(0)
for d, _, fs in os.walk(root):
    for f in sorted(fs):
        if f.endswith(".kt"):
            scan(os.path.join(d, f), roles)

warnings = []
gradle_hand = []
for r in roles:
    reasons = []
    kinds = {}
    for name, line in r.files:
        label, gradle = kind_of(name)
        if label:
            kinds.setdefault(label, []).append(name.rsplit("/", 1)[-1])
            if gradle:
                gradle_hand.append((r, name))
    if len(kinds) >= 2:
        reasons.append("種類の違うファイルが同居しています: " + " / ".join(
            "%s（%s）" % (label, ", ".join(sorted(set(names)))) for label, names in kinds.items()))
    dots = sorted(set(seg for _, t, _ in r.catchall for seg in t.split("/")[-1:] if seg.startswith(".") and len(seg) > 1))
    if len(dots) >= 2:
        reasons.append("ツールごとの設定ディレクトリを anyFile() / ignore() でまとめています: " + ", ".join(dots))
    TEST = re.compile(r"^(test|testSourceSet|androidTest|androidUnitTest|androidInstrumentedTest|[a-z][A-Za-z]*Test|testFixtures)$")
    MAIN = re.compile(r"^(main|mainSourceSet|[a-z][A-Za-z]*Main)$")
    segs = [set(p.replace("\\", "/").split("/")) for p in r.places]
    has_test = any(any(TEST.match(x) for x in s_) for s_ in segs)
    has_main = any(any(MAIN.match(x) for x in s_) and not any(TEST.match(x) for x in s_) for s_ in segs)
    if has_test and has_main:
        reasons.append("本体（main）とテスト（test）のファイルを同じ役割で宣言しています")
    if reasons:
        warnings.append((r, reasons))

if not warnings and not gradle_hand:
    print("役割の点検: 種類の違うファイルを抱えていそうな役割は見つかりませんでした（%d 件を走査）。" % len(roles))
    sys.exit(0)
print()
print("== 役割の点検（失敗にはしません） ================================")
print("定義: %s" % file_uri(root))
if warnings:
    print()
    print("種類の違うファイルを1つの役割に抱えていそうなものが %d 件あります（%d 件を走査）。" % (len(warnings), len(roles)))
    for r, reasons in warnings:
        print()
        print('  %s:%d 役割 "%s"' % (file_uri(r.path), r.line, r.name))
        for x in reasons:
            print("    - " + x)
    print()
    print("役割（\"Name\" { }）は1種類のファイルを表し、種類の違うものを束ねるのは group です。")
    print("手順書 3-2 の「1 つの役割 = 1 種類のファイル」の3つの問いで見直し、分けるべきものは")
    print("種類ごとの役割に分けて group で束ねてください。テキストの素朴な走査なので誤検知はあり得ます。")
    print("見直して1種類だと判断したものは、そのままでかまいません。")
if gradle_hand:
    names = sorted(set(r.name for r, _ in gradle_hand))
    print()
    print("Gradle のファイルを手書きの役割で宣言しています（役割: %s）。" % ", ".join(names))
    print("katachi 0.2 以降は gradle() が wrapper・settings・ビルドスクリプト・gradle.properties・")
    print("version catalog を種類ごとの役割に分けて宣言します（import me.tbsten.katachi.dsl.gradle.*）。")
    print("これらの役割を消して gradle() の1行に置き換えてください。buildSrc や includeBuild した")
    print("ビルドは gradle() の対象外なので、それだけは自分の役割に残します。")
print("==================================================================")
PYROLES
	[ -n "$_lr_json" ] && rm -f "$_lr_json"
	return 0
}

cmd_lint() {
	lint_roles
}

# ---------------------------------------------------------------- リファクタリング前後の比較

# ./gradlew :architecture-test:test の出力から、検査結果の行だけを取り出して並べ替える。
# 取り出すのは要約の1行（Katachi check failed: ...）と、違反ごとの見出し（[種類] パス）。
# 宣言位置（ProjectArchitecture.kt:42 など）はファイルを分ければ変わるのが正しいので比べない。
# [MissingDescription] は説明を書き足せば減るのが正しいので、比べる対象から外して別に数える。
# 要約の行の「, N warnings」も同じ理由で落とす。
# $1 = ログ, $2 = 出力先
extract_check_result() {
	grep -E '^[[:space:]]*(Katachi check failed:|\[[A-Z][A-Za-z]*\] )' "$1" |
		sed -e 's/^[[:space:]]*//' -e 's/, [0-9][0-9]* warnings*$//' |
		grep -v '^\[MissingDescription\] ' |
		sort >"$2" || :
}

# ログが検査の結果まで届いているか。コンパイルエラーなどで検査が走っていないログを
# 「違反 0 件」と取り違えないためのもの。
has_check_result() {
	grep -q -E 'Katachi check failed:|BUILD SUCCESSFUL' "$1"
}

count_missing_description() {
	grep -c -E '^[[:space:]]*\[MissingDescription\] ' "$1" || :
}

cmd_compare_violations() {
	require_workdir
	_cv_before="${1:-${WORKDIR}/tmp/test-before-refactor.log}"
	_cv_after="${2:-${WORKDIR}/tmp/test-after-refactor.log}"
	for _cv_f in "$_cv_before" "$_cv_after"; do
		[ -f "$_cv_f" ] || die "$(file_uri "$_cv_f") がありません。./gradlew :architecture-test:test --rerun の出力をこのパスに保存してください（手順書 3-2 / 3-3）。"
		has_check_result "$_cv_f" ||
			die "$(file_uri "$_cv_f") に検査の結果がありません。コンパイルエラーなどで検査まで届いていません。直してから取り直してください。"
	done

	mkdir -p "$WORKDIR/cache"
	_cv_a="$WORKDIR/cache/.cv-before.$$"
	_cv_b="$WORKDIR/cache/.cv-after.$$"
	extract_check_result "$_cv_before" "$_cv_a"
	extract_check_result "$_cv_after" "$_cv_b"
	_cv_md_a=$(count_missing_description "$_cv_before")
	_cv_md_b=$(count_missing_description "$_cv_after")
	_cv_n_a=$(grep -c '^\[' "$_cv_a" || :)
	_cv_n_b=$(grep -c '^\[' "$_cv_b" || :)

	say "前: $(file_uri "$_cv_before")"
	say "後: $(file_uri "$_cv_after")"
	say "違反の見出し: 前 ${_cv_n_a} 件 / 後 ${_cv_n_b} 件"
	say "[MissingDescription]（比べない）: 前 ${_cv_md_a} 件 / 後 ${_cv_md_b} 件"
	if cmp -s "$_cv_a" "$_cv_b"; then
		rm -f "$_cv_a" "$_cv_b"
		say ""
		say "検査結果は変わっていません。"
		return 0
	fi
	say ""
	say "検査結果が変わっています（- が前だけ、+ が後だけにある行）。"
	diff "$_cv_a" "$_cv_b" | sed -n -e 's/^< /  - /p' -e 's/^> /  + /p' || :
	rm -f "$_cv_a" "$_cv_b"
	say ""
	say "リファクタリングで振る舞いが変わっています。前のログを取り直すのではなく、定義のほうを直してください。"
	say "分けた定義ファイルが [UnexpectedFile] になっているなら、architecture-test/ を受け持つ役割の layout { } が"
	say "<group>/<role>/<Role>.kt の深さまで受け入れていません。"
	exit 1
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
	[ -f "$_cl" ] || die "$(file_uri "$_cl") がありません。先に init を実行してください。"

	_script="$1"
	shift
	_cur="$_cl.cur.$$"
	extract_json "$_cl" checklist >"$_cur" ||
		die "$(file_uri "$_cl") から JSON を取り出せませんでした。"

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
	# 定義を書き終えた時点と整え終えた時点で、役割の取り違えを拾う。
	# 完了にはしたうえで警告だけ出す。
	for _ck_id in "$@"; do
		case $_ck_id in
		3-2 | 3-3)
			lint_roles
			break
			;;
		esac
	done
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
    # レポート側の questions。チェックリストの question とは行き先が違うだけで形は同じ。
    # ステップ1で気づいた「コードベースの揺れ」を書く場所。
    "codebase-question":
                  ("questions",   ["question", "observed", "recommendation"], ["options"]),
    "changed":    ("changedFiles",["path", "change", "summary"], []),
    # モジュールが何を受け持つか。katachi の Role（1種類のファイル）と紛れないよう
    # role とは呼ばない。モジュールは種類の違うファイルのまとまりで、定義では group になる。
    "module":     ("modules",     ["path", "kind", "responsibility", "buildFile"], []),
    "role":       ("roles",       ["importance", "name", "layout", "naming", "count", "note"],
                                  ["allowed", "forbidden", "examples"]),
    "tool":       ("tools",       ["name", "configPath", "declareInKatachi", "note"], []),
    "excluded":   ("excluded",    ["path", "reason"], []),
    # 導入の最後に出す「次にできること」。どの役割にどんなテンプレートを当てるかは
    # エージェントが判断し、ここは記録するだけ。summary とレポートが表示する。
    "template":   ("templates",   ["role", "basedOn", "reason"], ["files", "params"]),
}

# 二重登録を弾くためのキー。violation と question は同じ内容を2回書く理由が
# あり得るので入れない。
IDENTITY = {
    "changed":  "path",
    "module":   "path",
    "excluded": "path",
    "role":     "name",
    "tool":     "name",
    "template": "role",
}

if kind not in FIELDS:
    sys.stderr.write("知らない種類です: %s\n" % kind)
    sys.stderr.write("使えるもの: %s\n" % ", ".join(FIELDS))
    sys.exit(1)

key, scalars, lists = FIELDS[kind]

if not args:
    sys.stderr.write("項目を1つも指定していません: add %s\n" % kind)
    sys.stderr.write("このまま追記すると中身が空の行ができます。\n")
    sys.stderr.write("使えるもの: %s\n" % ", ".join(scalars + lists))
    sys.exit(1)

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
        if kind == "module" and name == "role":
            sys.stderr.write("モジュールの受け持ちは --responsibility です。katachi の Role（1種類のファイル）とは別物で、モジュールは定義では group になります。\n")
        sys.stderr.write("使えるもの: %s\n" % ", ".join(scalars + lists))
        sys.exit(1)

data = json.load(open(src, encoding="utf-8"))
rows = data.setdefault(key, [])

idf = IDENTITY.get(kind)
if idf is not None and entry.get(idf) is not None:
    for row in rows:
        if row.get(idf) == entry[idf]:
            sys.stderr.write("その %s はすでに登録されています: %s\n" % (idf, entry[idf]))
            sys.stderr.write("二重に記録しないため追記しませんでした。\n")
            sys.stderr.write("書き換えたい場合は data get で取り出し、直してから data set してください。\n")
            sys.exit(1)

rows.append(entry)
json.dump(data, open(dest, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print("%s に1件追記しました（計 %d 件）" % (key, len(data[key])))
'

# どの種類がどちらのファイルに属するか。
add_target_of() {
	case "$1" in
	violation | question | changed) printf 'check-list\n' ;;
	module | role | tool | excluded | codebase-question | template) printf 'report\n' ;;
	*) printf '\n' ;;
	esac
}

cmd_add() {
	need_python
	_kind="${1:-}"
	[ -n "$_kind" ] || die "add: 種類を指定してください（violation / question / codebase-question / changed / module / role / tool / excluded / template）"
	shift

	_target=$(add_target_of "$_kind")
	[ -n "$_target" ] || die "add: 知らない種類です: $_kind"

	_html=$(resolve_data_target "$_target")
	[ -f "$_html" ] || die "ファイルがありません: $(file_uri "$_html")"
	_id=$(guess_block_id "$_html")

	_cur="$_html.cur.$$"
	extract_json "$_html" "$_id" >"$_cur" || {
		rm -f "$_cur"
		die "$(file_uri "$_html") から JSON を取り出せません。"
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
	[ -f "$_cl" ] || die "$(file_uri "$_cl") がありません。先に init を実行してください。"

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
import json, os, sys

cl = json.load(open(sys.argv[1], encoding="utf-8"))
try:
    report = json.load(open(sys.argv[2], encoding="utf-8"))
except Exception:
    report = {}
report_questions = report.get("questions") or []
report_templates = report.get("templates") or []
meta = cl.get("meta") or {}
workdir = meta.get("workdir")


def file_uri(path):
    # sh 側の file_uri と同じ規則。workdir は相対でも絶対でもよい（join は絶対パスを優先する）。
    p = os.path.abspath(os.path.join(meta.get("projectRoot") or os.getcwd(), path))
    for a, b in (("%", "%25"), (" ", "%20"), ("#", "%23"), ("?", "%3F")):
        p = p.replace(a, b)
    return "file://" + p


def in_workdir(name):
    if not workdir:
        return "<作業用ディレクトリ>/" + name
    return file_uri(os.path.join(workdir, name))

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
    print(in_workdir("check-list.html"))
    sys.exit(0)

if violations:
    print("⚠️ katachi のセットアップを行いました")
else:
    print("✅ katachi のセットアップを行いました")
print()
print("**生成されたコードはあなたのレビューが必要不可欠** です。内容を確認してください。")
print()
print("- チェックリスト: %s" % in_workdir("check-list.html"))
print("- コードベース レポート: %s" % in_workdir("project-code-base-report.html"))
if violations:
    print("- `:architecture-test:test` の実行結果: ⚠️ %d 件のアーキテクチャ違反を残しています" % len(violations))
else:
    print("- `:architecture-test:test` の実行結果: ✅ All green")
if questions:
    print("- 確認したいことが %d 件あります（チェックリストの「ユーザに確認したいこと」）" % len(questions))
if report_questions:
    print("- コードベースの揺れが %d 件あります（レポートの questions）" % len(report_questions))
    for q in report_questions:
        text = q.get("question") or "(未記入)"
        rec = q.get("recommendation")
        if rec:
            print("    - %s → 推奨: %s" % (text, rec))
        else:
            print("    - %s" % text)
print("- 変更したファイル: %d 件" % len(changed))
print("- Next action:")
print("    - %s/ 以下の ProjectArchitecture.kt を**レビュー**してください" % file_uri("architecture-test"))
# 任意のステップで未実施のものを、これからやることとして出す。
# label は「〜した」の完了形なので、そのまま出すと嘘になる。
HINT = {
    "6-1": "実際の実装タスクを1つ回して、定義が機能することを確かめてください",
    "6-2": "CI に `:architecture-test:test` を組み込んでください",
    "6-3": "ドキュメント生成をセットアップできます（`architecture { title / description }` を書き、`./gradlew :architecture-test:katachiDocs` で生成。生成物をコミットするかを決めてください）",
    "6-4": "テンプレートからのコード生成をセットアップできます。提案は次の %d 件です（レポートの「テンプレートの提案」）",
}
for i in optional_items:
    if i.get("done"):
        continue
    if i["id"] == "6-4":
        # 当てる役割が無いと判断したなら、何も勧めない
        if not report_templates:
            continue
        print("    - %s" % (HINT["6-4"] % len(report_templates)))
        for t in report_templates:
            files = ", ".join(t.get("files") or []) or "(未記入)"
            params = ", ".join(t.get("params") or []) or "なし"
            print("        - %s: %s（パラメータ: %s）" % (t.get("role") or "(未記入)", files, params))
        continue
    print("    - %s" % HINT.get(i["id"], i.get("label", i["id"])))
'

cmd_summary() {
	need_python
	require_workdir
	_cl="$WORKDIR/check-list.html"
	[ -f "$_cl" ] || die "$(file_uri "$_cl") がありません。先に init を実行してください。"
	_a="$WORKDIR/cache/.summary.$$"
	extract_json "$_cl" checklist >"$_a" || die "チェックリストの JSON を読めません。"

	# レポート側の questions も出す。ステップ1で書いたものがユーザに一度も
	# 届かないまま終わる事故があったため（手順書ではステップ3の前に提示させている）。
	_b="$WORKDIR/cache/.summary-report.$$"
	_rp="$WORKDIR/project-code-base-report.html"
	if [ -f "$_rp" ]; then
		extract_json "$_rp" report >"$_b" 2>/dev/null || printf '{}\n' >"$_b"
	else
		printf '{}\n' >"$_b"
	fi

	# `set -e` の下では、条件に置かないと python の失敗で後始末に届かない。
	if printf '%s' "$SUMMARY_PY" | python3 - "$_a" "$_b"; then
		rm -f "$_a" "$_b"
	else
		rm -f "$_a" "$_b"
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
lint)
	shift
	cmd_lint "$@"
	;;
compare-violations)
	shift
	cmd_compare_violations "$@"
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
