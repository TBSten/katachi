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
# 必要なもの: sh / awk / sed / grep などの基本コマンド / curl / python3 / git（推奨）
# 何が要るかはサブコマンドごとに require_commands で最初に確かめる（REQ_* を参照）。
# `doctor` で全部をまとめて確かめられる。
#
# ネットワークを使うのは init と docs（と、版を決められないときの scaffold）だけ。どれも curl に接続・転送の
# timeout を設定してあり、ぶら下がったまま止まることはない。
#
# 開発者向け: KATACHI_MAVEN_LOCAL=1
#   リリース前の katachi を ~/.m2 に publish して試すための口。利用者の手順には出さない。
#   init に付けると作業用ディレクトリに記録され、以降の scaffold も従う。
#   - init / scaffold の「katachi は 0.3.0 以降」の確認を飛ばす（--katachi か
#     init で指定した版のときだけ。GitHub の最新を取ってきた版では飛ばさない）
#   - scaffold が settings の pluginManagement { repositories { } } と
#     dependencyResolutionManagement { repositories { } } に mavenLocal() を足す
#
#     # katachi のリポジトリで。署名の設定が無い手元では -Pkatachi.skipSigning が要る
#     ./gradlew publishToMavenLocal -Pkatachi.skipSigning
#     # 試すプロジェクトで。scaffold は init の --katachi を引き継ぐ
#     KATACHI_MAVEN_LOCAL=1 sh katachi-install.sh init --katachi 0.3.0
#     sh <作業用ディレクトリ>/tmp/katachi-install.sh scaffold --package com.example.app

set -eu

# 配信元。ローカルで試すときだけ環境変数で差し替える。
# init に渡した値は作業用ディレクトリ（tmp/cache/docs）に記録され、以降のコマンドは環境変数が
# 無ければそれを使う（resolve_docs）。init にだけ渡して docs が黙って本番を取る、を防ぐため。
KATACHI_DOCS_DEFAULT="https://tbsten.github.io/katachi"
KATACHI_DOCS_ENV="${KATACHI_DOCS:-}"
KATACHI_DOCS="${KATACHI_DOCS:-$KATACHI_DOCS_DEFAULT}"

# 言語。チェックリストとレポート、scaffold の生成物のコメント、**このスクリプトの出力**が従う。
# `init --lang` で決まり、作業用ディレクトリ（tmp/cache/lang）に記録される。以降のコマンドは
# そこから読むので、毎回指定しなくてよい。決め方は resolve_lang を参照。
KATACHI_LANG="${KATACHI_LANG:-}"
KATACHI_RELEASES_API="https://api.github.com/repos/TBSten/katachi/releases/latest"
KATACHI_RELEASES_PAGE="https://github.com/TBSten/katachi/releases"

# 生成するモジュールが使う固定値。プロジェクト側の事情で変わらないものだけを置く。
# katachi が要求する Kotlin の下限。katachi の成果物は languageVersion 2.2 で
# ビルドしているので、2.2 以降のコンパイラなら metadata を読める。これより古いと
# architecture などのシンボルがすべて Unresolved reference になる。
KOTLIN_MIN_MAJOR="2"
KOTLIN_MIN_MINOR="2"

# このスクリプトと手順書（index.md）が前提にする katachi の下限。手順書と生成物は 0.3 の機能で書いてある。
# - Role の宣言は "Name".role { }
# - capture の束縛（同じ名前を 2 段に書くと同じ値かを検査し、[CaptureMismatch] にする）
# - -Dkatachi.maxViolations=all（katachi の Gradle plugin が test の JVM に渡す）
# - gradle { buildScript { } } などの hook（lint が Gradle のファイルの制約の移し先として勧める）
# これより前の版では手順書どおりに書いた定義がコンパイルできない・効かないので、init / scaffold で止める。
KATACHI_MIN_MAJOR="0"
KATACHI_MIN_MINOR="3"
KATACHI_PLUGIN_ID="me.tbsten.katachi"

JUNIT_VERSION="5.14.4"
# --test-framework kotest で、プロジェクトの catalog に io.kotest の版が無いときに書く版。
KOTEST_VERSION="6.2.5"

# 検査用モジュールの置き場所（Gradle ルートからの相対パス）の既定。実際の値は
# resolve_module_dir が MODULE_DIR（相対パス）と MODULE_GRADLE_PATH（:a:b の形）に入れる。
# どちらも resolve_module_dir を呼ぶまでは未定義（set -u で落ちる。呼び忘れに気づくため）。
MODULE_DIR_DEFAULT="architecture-test"

# ---------------------------------------------------------------- 出力

# 利用者に見える文言は、日本語と英語を並べて渡す。
#
#   say "作業用ディレクトリ: ${x}" "Working directory: ${x}"
#
# 引数が1つなら言語によらずそのまま出す（KEY=VALUE の行、パスだけの行、空行）。
# 文言はここ以外に持たない。同じ文言を2か所に書かないこと。
#
# メッセージに日本語を混ぜるときは、変数展開を必ず ${VAR} と波括弧で閉じること。
# $VAR の直後に非 ASCII（全角の閉じ括弧など）が続くと、macOS の bash 3.2 は続くバイトを
# 変数名の一部と誤読し、set -u と相まって "unbound variable" で落ちる。メッセージが
# 出ないまま終了するので気づきにくい。これまでに4回踏んでいる。
pick() {
	if [ $# -ge 2 ] && [ "${KATACHI_LANG:-ja}" = "en" ]; then
		printf '%s' "$2"
	else
		printf '%s' "$1"
	fi
}
say() { printf '%s\n' "$(pick "$@")"; }
note() { printf '   %s\n' "$(pick "$@")"; }
warn() { printf '\n[!] %s\n' "$(pick "$@")" >&2; }

die() {
	printf '\n[x] %s\n' "$(pick "$@")" >&2
	exit 1
}

# オプションの値が無ければ止まる。$1 = オプション名, $2 = 値。
# ${2:?...} だとシェルの形式のメッセージになり、言語も合わせられない。
need_value() {
	[ -n "${2:-}" ] || die "${1} に値がありません。" "${1} needs a value."
}

# 人に見せるパスを file:// で始まる絶対 URI にして出す（改行は付けない）。
# ターミナルや IDE がリンクとして拾えるようにするためのもの。KEY=VALUE の値や
# docs の標準出力のような、機械が読むパスには使わないこと（file:// を付けると壊れる）。
#
# - 相対パスは $PWD に連結する。存在するディレクトリ（またはファイルの親）は
#   cd && pwd で正規化する。pwd は -P を付けない論理パスで、シンボリックリンクは解決しない
# - 存在しないパスは連結するだけ
# - % / 空白 / # / ? だけを百分率符号化する。日本語は UTF-8 のまま出す
#   （利用者のパスに日本語があればそのまま出す。ASCII に揃える必要はない）
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
	if [ "${KATACHI_LANG:-ja}" = "en" ]; then
		usage_en
	else
		usage_ja
	fi
}

usage_ja() {
	cat <<'USAGE'
katachi-install.sh — katachi のインストールのうち機械的にできる部分をまとめて行う

使い方:
  sh katachi-install.sh init [オプション]
      Gradle のルートであることを確かめ、作業用ディレクトリを作り、
      チェックリストとレポートのテンプレートを配置する。
      最後に KEY=VALUE 形式で結果を出力する（後続のステップがこれを読む）。

      --workdir <path>    作業用ディレクトリを明示する（既定: 自動判定）
      --katachi <version> katachi のバージョンを明示する（既定: GitHub の最新リリース。やり直しでは
                          前回の init が記録した版を使い続ける）
      --offline           ダウンロードを行わない
      --force             記入済みのチェックリスト・レポートも取り直す
      --lang <en|ja>      チェックリスト・レポート・このスクリプトの出力の言語（既定: en）。
                          作業用ディレクトリに記録され、以降のコマンドが従う
      --module-dir <path> 検査用モジュールの置き場所。Gradle ルートからの相対パス
                          （既定: architecture-test。例: static-analysis/katachi）。
                          チェックリストの meta.moduleDir に記録され、scaffold 以降が従う

      2回目以降の実行では、記入済みのファイルは上書きしません。
      また自分自身を作業用ディレクトリにコピーするので、以降は
      <作業用ディレクトリ>/tmp/katachi-install.sh を使ってください。

  sh katachi-install.sh doctor [--lang <en|ja>]
      このスクリプトが使うコマンド（curl・python3・git など）がそろっているかを確かめる。
      足りないものがあれば、全部と入れ方の手がかりを並べて 1 を返す。
      init もはじめに同じ確認をする。

  sh katachi-install.sh scaffold --package <com.example.app> [オプション]
      検査用モジュール（既定 architecture-test/。--module-dir で変える）を作成し、
      ビルドに組み込む。

      --package <pkg>     アプリのパッケージ名（必須）
      --katachi <version> katachi のバージョン（既定: init が記録した版。
                          無ければ GitHub の最新リリース）
      --kotlin <version>  Kotlin のバージョン（既定: プロジェクトから検出）
      --no-konsist        katachi-konsist を使わない構成で生成する（konsist { } も ktFileContent { } も書けない）。
                          assert() に FileConstraintCheck() を渡さないので、gradle { } の hook に
                          書いた fileConstraint も評価されず、test は [UncheckedFileConstraint] で
                          失敗する（How to fix: Pass FileConstraintCheck() to assert()）
      --module-dir <path> 検査用モジュールの置き場所（既定: init が記録した meta.moduleDir。
                          無ければ architecture-test）。指定すると meta.moduleDir を書き換える
      --test-framework <junit5|kotest>
                          テストの雛形と依存（既定: preferences.testFramework。無ければ
                          kotest の依存がプロジェクトにあれば kotest、なければ junit5）
      --deps <catalog|literal>
                          依存の書き方（既定: preferences.dependencyStyle。無ければ
                          gradle/libs.versions.toml があれば catalog）。catalog は toml に
                          無い項目だけを足し、build.gradle.kts は libs.katachi のように参照する。
                          別名が別の用途で使われていると、明示したときは止まり、
                          既定のときは literal に落とす
      --force             既存の検査用モジュールを上書きする
      --dry-run           何も書かずに、何をするかだけ出力する

  sh katachi-install.sh guide [<節 ID>] [--refresh]
      手順書（index.md）を節ごとに出す。引数なしは目次（<ID>、見出し、行数をタブ区切りで 1 行ずつ）。
      <節 ID> は見出しの番号（0-1、3-2、5、6-A など。6-1〜6-4 は 6-A〜6-D に読み替える）。
      章（1 など）に番号つきの節があれば、前書きと「含む節:」だけを出す。
      各節の最後に次の節のコマンドを付ける。手順書は init が取る。--refresh で取り直す。

  sh katachi-install.sh data get <対象> [<キー>] [--id <id>]
      埋め込まれた JSON を標準出力に出す。HTML 本体は読まなくてよい。
      <対象> は check-list / report / ファイルパスのどれか。
      <キー> はドット区切り（meta.moduleDir、questions）。その部分だけを出し、
      無ければ 1 を返す。文字列はそのまま、ほかは JSON で出す。

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
      id を自動で採番する。question（チェックリスト）は q1, q2, ...、codebase-question（レポート）は cq1, cq2, ...。
      追記の前後で件数を確かめ、合わなければ止まる（並行して書き込んだときの検知）。

      add role --group <group> [--constraint-only --note <理由>]
          --group は定義での束ね先（入れ子は . 区切り: feature.ui）。Gradle のビルドファイルは
          gradle() が受け持つので --layout に書けない（buildSrc / includeBuild したビルドは除く）。
          制約を付けるための Role だけ --constraint-only --note <理由> で記録する。
      add module --deviation <文>
          同じ kind の他のモジュールと構成が違う点（無ければ省略）。
      add excluded は .gitignore で除外済みのパスを拒む（katachi はもともと見ない）。

  sh katachi-install.sh preferences [show]
  sh katachi-install.sh preferences set <key>=<value> ...
      書き方の方針を表示 / 記録する（手順書 1-3）。show は 1 行 1 キーで、値と
      「推奨 / 記録済み / 検出: <根拠>」を出す。知らないキー・値は止まる。
      key: nestedGroups (yes/no) / moduleDir (パス) / testFramework (junit5/kotest) /
           dependencyStyle (catalog/literal) / constraintNames (plain/numbered) /
           oneRolePerFile (yes/no)
      moduleDir はチェックリストの meta.moduleDir に、ほかは preferences に記録される。
      lint が nestedGroups / oneRolePerFile を、scaffold が testFramework / dependencyStyle を読む。

  sh katachi-install.sh answers [show] [--target report|check-list]
  sh katachi-install.sh answers import <json> [--target report|check-list]
  sh katachi-install.sh answers set <id> <回答> [--note <文>] [--target report|check-list]
      確認事項（questions）への回答を表示 / 取り込む。show は --target が無ければ両方を出す。
      set は id で書き込み先が決まる（q<n> はチェックリスト、cq<n> はレポート。--target と食い違えば止まる）。
      import はブラウザで書き出した JSON（{"target": "report", "answers": {"cq1": {"answer": "...", "note": null}}}）を読み、
      知らない id は飛ばして件数を出す。書き込み先は JSON の target、無ければ --target、それも無ければ id から決める。
      回答は answers（id ごと）に残り、summary が回答済みの件数と未回答だけを出す。

  sh katachi-install.sh docs [--small] [--refresh]
      ガイド全文（llms-full.txt）を作業用ディレクトリの tmp/cache/ に取得し、
      そのパスを出力する。すでにあれば取りに行かない。

  sh katachi-install.sh docs --topic <名前> [--refresh]
  sh katachi-install.sh docs --topics
      ガイドを話題（ページ）ごとに切り出して tmp/cache/topics/<名前>.md に置き、
      そのパスを出力する（layout / role / basic-api / kt-file-content など。--topics で一覧）。
      全文を読まずに済ませるためのもの。古い版の写しは含めない。

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
      定義（検査用モジュールの src/test/kotlin）を読み、種類の違うファイルを1つの
      Role に抱えていそうなもの、Gradle のファイルを手書きで宣言した Role（制約を持つものは
      gradle { } の hook への移し方も出す）、平たい group、分けていないファイル、
      documented = false の候補を列挙する。check 3-2 / check 3-3 でも自動で走る。
      素朴なテキスト走査なので誤検知があり得る。点検の結果では失敗にしない（0 を返す）。
      定義のディレクトリが無ければ（scaffold 前、置き場所の食い違い）1 を返す。

  sh katachi-install.sh compare-violations [<前のログ> <後のログ>]
      ./gradlew <module>:test の出力を2つ比べ、検査結果（違反の件数と中身）が
      変わっていないかを確かめる。定義のリファクタリング（手順書 3-3）の前後に使う。
      既定は <作業用ディレクトリ>/tmp/test-before-refactor.log と test-after-refactor.log。
      [MissingDescription] の警告は、説明を書き足せば減るのが正しいので件数だけ出す。
      変わっていなければ 0、変わっていれば差分を出して 1 を返す。

  sh katachi-install.sh test [--save <名前>] [-- <gradle の引数>...]
      検査用モジュールのテスト（./gradlew <module>:test）を実行し、要約だけを出す。
      ログの全文は <作業用ディレクトリ>/tmp/test/<UTC 時刻>.log（latest.log にも写し、
      前の latest.log は previous.log に回す）。--save <名前> で tmp/test-<名前>.log にも写す
      （手順書 3-2 は before-refactor、3-3 は after-refactor。compare-violations の既定と同じ）。
      -- より後ろは ./gradlew にそのまま渡す（-Dkatachi.maxViolations=all など）。
      要約: JDK、ログのパス、結果の行、[MissingDescription] の件数、前回（previous.log）との差分。
      0 = 通った、1 = 検査が落ちた、2 = 検査まで届かない（コンパイルエラー、gradlew 無し、定義ディレクトリ無し）。
      このスクリプトが Gradle を起動するのはこのコマンドだけ。

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
      「katachi は 0.3.0 以降」の確認を飛ばし（GitHub の最新を取ってきた
      版では飛ばさない）、scaffold が settings の pluginManagement と
      dependencyResolutionManagement の repositories に mavenLocal() を足す。
      利用者の導入では使わない。~/.m2 へは katachi のリポジトリで
      ./gradlew publishToMavenLocal -Pkatachi.skipSigning として出す。

  KATACHI_DOCS=<URL>
      手順書・テンプレート・ガイドの配信元（既定: https://tbsten.github.io/katachi）。
      ビルド済みのサイト（docs/dist を配信したもの）か配信 URL に向ける。docs/public には
      llms-full.txt などが無い。init に付けると作業用ディレクトリに記録され、以降の
      docs なども従う（付けずに init し直すと本番に戻る）。docs は取得元を標準エラーに出す。

init と scaffold は Gradle のルートディレクトリで実行してください。
data と docs は、作業用ディレクトリに置かれたこのスクリプトから実行してください。
USAGE
}

usage_en() {
	cat <<'USAGE'
katachi-install.sh - does the mechanical parts of installing katachi

Usage:
  sh katachi-install.sh init [options]
      Checks that this is the Gradle root, creates the working directory, and
      places the check list and the report template.
      Prints the result as KEY=VALUE lines at the end (the later steps read them).

      --workdir <path>    Use this working directory (default: chosen automatically)
      --katachi <version> Use this katachi version (default: the latest GitHub release; on a rerun,
                          the version the previous init recorded)
      --offline           Do not download anything
      --force             Fetch the check list and the report again, even if filled in
      --lang <en|ja>      Language of the check list, the report and this script's
                          output (default: en). Recorded in the working directory;
                          later commands follow it
      --module-dir <path> Where the test module goes, relative to the Gradle root
                          (default: architecture-test; for example static-analysis/katachi).
                          Recorded as meta.moduleDir in the check list; scaffold and later follow it

      On later runs, files that are already filled in are not overwritten.
      The script also copies itself into the working directory, so from then on
      use <working directory>/tmp/katachi-install.sh.

  sh katachi-install.sh doctor [--lang <en|ja>]
      Checks that the commands this script uses (curl, python3, git, ...) are available.
      If anything is missing, lists all of it with hints on how to install it and returns 1.
      init runs the same check first.

  sh katachi-install.sh scaffold --package <com.example.app> [options]
      Creates the test module (default architecture-test/; change it with --module-dir)
      and wires it into the build.

      --package <pkg>     The app's package name (required)
      --katachi <version> katachi version (default: the one init recorded,
                          otherwise the latest release)
      --kotlin <version>  Kotlin version (default: detected from the project)
      --no-konsist        Generate a setup that does not use katachi-konsist (no konsist { } or ktFileContent { }).
                          assert() gets no FileConstraintCheck(), so a fileConstraint written in a
                          gradle { } hook is not evaluated either, and the test fails with
                          [UncheckedFileConstraint] (How to fix: Pass FileConstraintCheck() to assert())
      --module-dir <path> Where the test module goes (default: meta.moduleDir recorded by init,
                          otherwise architecture-test). When given, meta.moduleDir is updated
      --test-framework <junit5|kotest>
                          Test template and dependencies (default: preferences.testFramework;
                          otherwise kotest when the project depends on kotest, else junit5)
      --deps <catalog|literal>
                          How dependencies are written (default: preferences.dependencyStyle;
                          otherwise catalog when gradle/libs.versions.toml exists). catalog adds
                          only the missing entries to the toml, and build.gradle.kts refers to
                          them as libs.katachi and so on. When an alias is already used for
                          something else, an explicit --deps catalog stops; the default falls
                          back to literal
      --force             Overwrite an existing test module
      --dry-run           Write nothing; only print what would be done

  sh katachi-install.sh guide [<section id>] [--refresh]
      Prints the guide (index.md) one section at a time. Without an argument, prints the table of
      contents (one line per section: <id>, heading, number of lines, tab-separated).
      <section id> is the number in the heading (0-1, 3-2, 5, 6-A, ...; 6-1 to 6-4 are read as 6-A to 6-D).
      For a chapter with numbered sections (such as 1), prints only its preamble and "Sections:".
      Each section ends with the command for the next one. init fetches the guide; --refresh fetches it again.

  sh katachi-install.sh data get <target> [<key>] [--id <id>]
      Prints the embedded JSON. You do not need to read the HTML itself.
      <target> is check-list, report, or a file path.
      <key> is dot-separated (meta.moduleDir, questions). Prints only that part, and
      returns 1 if it does not exist. A string is printed as is; anything else as JSON.

  sh katachi-install.sh data set <target> <json> [--id <id>]
      Replaces the embedded JSON with <json> as a whole.

  sh katachi-install.sh data merge <target> <json> [--id <id>]
      Deep-merges <json> into the existing content. Easier for partial updates.
      Arrays are replaced as a whole (not appended to).

      Both set and merge validate the JSON before writing, keep the original
      file as .bak, and roll back automatically if the result is broken.

  sh katachi-install.sh add <kind> [--<field> <value> ...]
      Appends one entry to an array. Unlike merge, existing entries are kept.
      check-list: violation / question / changed
      report:     module / role / tool / excluded / codebase-question / template
      An id is numbered automatically: q1, q2, ... for question (check list), cq1, cq2, ... for codebase-question (report).
      The count is checked before and after appending; a mismatch stops the command
      (detects concurrent writes).

      add role --group <group> [--constraint-only --note <reason>]
          --group is where the Role goes in the definition (nested with dots: feature.ui).
          Gradle build files cannot be a --layout since gradle() takes care of them
          (except buildSrc and included builds). Only a Role that exists to attach a
          constraint is recorded, with --constraint-only --note <reason>.
      add module --deviation <text>
          How this module differs from the other modules of the same kind (omit if none).
      add excluded rejects a path already excluded by .gitignore (katachi never looks at it).

  sh katachi-install.sh preferences [show]
  sh katachi-install.sh preferences set <key>=<value> ...
      Shows / records how the definition should be written (step 1-3 of the guide). show prints
      one key per line with the value and "recommended / recorded / detected: <evidence>".
      An unknown key or value stops the command.
      key: nestedGroups (yes/no) / moduleDir (path) / testFramework (junit5/kotest) /
           dependencyStyle (catalog/literal) / constraintNames (plain/numbered) /
           oneRolePerFile (yes/no)
      moduleDir is recorded as meta.moduleDir of the check list, the others under preferences.
      lint reads nestedGroups / oneRolePerFile; scaffold reads testFramework / dependencyStyle.

  sh katachi-install.sh answers [show] [--target report|check-list]
  sh katachi-install.sh answers import <json> [--target report|check-list]
  sh katachi-install.sh answers set <id> <answer> [--note <text>] [--target report|check-list]
      Shows / imports the answers to the questions. Without --target, show prints both files.
      set writes where the id belongs (q<n> is the check list, cq<n> the report; a contradicting --target stops it).
      import reads the JSON exported from the browser ({"target": "report", "answers": {"cq1": {"answer": "...", "note": null}}}),
      skips unknown ids and prints the counts. It writes to the target in the JSON, otherwise --target, otherwise where the ids belong.
      Answers are kept under answers (per id); summary prints the number answered and lists only the unanswered ones.

  sh katachi-install.sh docs [--small] [--refresh]
      Fetches the full guide (llms-full.txt) into tmp/cache/ in the working directory
      and prints its path. Does not fetch again if it is already there.

  sh katachi-install.sh docs --topic <name> [--refresh]
  sh katachi-install.sh docs --topics
      Cuts one topic (page) out of the guide into tmp/cache/topics/<name>.md and prints
      its path (layout / role / basic-api / kt-file-content and so on; --topics lists them).
      Saves reading the full text. Copies of older versions are left out.

  sh katachi-install.sh docs --api [--refresh]
      Fetches the root index of the API reference (Dokka), api-docs/llms.txt.
      It lists the main APIs and links to each module's index and full text.

  sh katachi-install.sh docs --api <katachi|katachi-konsist> [--refresh]
      Fetches the full API text (signatures and KDoc) of the given module.
      Use it to check an API's signature or parameters.

  sh katachi-install.sh check <item id>...
  sh katachi-install.sh uncheck <item id>...
      Marks check list items as done / not done.
      An unknown id stops the command and lists the ids you can use.

  sh katachi-install.sh warn <step id> <text>
  sh katachi-install.sh warn <step id> --clear
      Attaches a warning to a step / removes it. A step with a warning turns
      yellow and stays open even when it is done.

  sh katachi-install.sh lint
      Reads the definition (src/test/kotlin of the test module) and lists Roles that
      seem to hold files of different kinds, hand-written Roles for Gradle files (ones with
      constraints also get the way to move them into a gradle { } hook), flat groups, files
      not split, and candidates for documented = false.
      Also runs on check 3-2 / check 3-3.
      It is a plain text scan, so false positives are possible. Findings never fail it (returns 0).
      Returns 1 if the definition directory does not exist (before scaffold, or a different location).

  sh katachi-install.sh compare-violations [<log before> <log after>]
      Compares two outputs of ./gradlew <module>:test and checks that the
      check result (the number and content of violations) has not changed. Use it
      before and after refactoring the definition (step 3-3 of the guide).
      Defaults: <working directory>/tmp/test-before-refactor.log and test-after-refactor.log.
      [MissingDescription] warnings should go down as descriptions are added, so only
      their counts are shown.
      Returns 0 if nothing changed; otherwise prints the difference and returns 1.

  sh katachi-install.sh test [--save <name>] [-- <gradle arguments>...]
      Runs the test module's tests (./gradlew <module>:test) and prints only a summary.
      The full log goes to <working directory>/tmp/test/<UTC time>.log (also copied to latest.log;
      the previous latest.log becomes previous.log). --save <name> also copies it to tmp/test-<name>.log
      (before-refactor in step 3-2, after-refactor in step 3-3; the defaults of compare-violations).
      Everything after -- is passed to ./gradlew as is (such as -Dkatachi.maxViolations=all).
      Summary: the JDK, the log path, the result line, the number of [MissingDescription], and the
      difference from the previous run (previous.log).
      0 = passed, 1 = the check failed, 2 = the check was not reached (compile error, no gradlew,
      no definition directory). This is the only command of this script that starts Gradle.

  sh katachi-install.sh verify
      Lists blank fields and unfinished items. If nothing is left, marks the last
      step as done and returns 0. Otherwise returns 1.

  sh katachi-install.sh summary
      Builds and prints the message to give back to the user.

  sh katachi-install.sh --help

Environment variables for developers:
  KATACHI_MAVEN_LOCAL=1
      A hook for trying an unreleased katachi published to ~/.m2.
      Set it on init and it is recorded in the working directory; later scaffold runs follow it.
      Skips the "katachi needs 0.3.0 or later" check (not for a version fetched
      as the latest release from GitHub), and scaffold adds mavenLocal() to the
      repositories of pluginManagement and dependencyResolutionManagement in settings.
      Not for real installations. Publish to ~/.m2 from the katachi repository with
      ./gradlew publishToMavenLocal -Pkatachi.skipSigning

  KATACHI_DOCS=<URL>
      Where the guide, templates and docs are served from (default: https://tbsten.github.io/katachi).
      Point it at a built site (docs/dist, served) or a served URL; docs/public has no
      llms-full.txt and the like. Set it on init and it is recorded in the working directory;
      later commands such as docs follow it (running init again without it goes back to the
      default). docs prints the source URL to standard error.

Run init and scaffold in the Gradle root directory.
Run data and docs with the copy of this script in the working directory.
USAGE
}

# ---------------------------------------------------------------- 前提のコマンド

# サブコマンドごとに要るコマンド。実装が実際に呼んでいるものだけを置く。
# 足すときは、そのコマンドを使うサブコマンドの REQ_* に入れること。
#
# python3 は JSON の検査・マージ・加工に使う。以前は jq でも代われたが、check / add /
# verify / summary が python3 を必須にしていたため、jq だけの環境では導入を最後まで
# 進められなかった。分岐を減らすため python3 に一本化した。
#
# sleep と rmdir は書き込みのロック（lock_acquire / lock_release）が使う。
# scaffold / compare-violations / test は resolve_module_dir がチェックリストの meta.moduleDir を
# python3 で読むので python3 が要る。guide は手順書の切り出しを awk でするので python3 は要らず、
# curl は --refresh（と init が取り損ねたとき）だけが使う。
REQ_BASE="awk sed grep tr cut head tail wc date cp mv rm mkdir cat sort find chmod sleep rmdir"
REQ_INIT="${REQ_BASE} curl python3"
REQ_INIT_OFFLINE="${REQ_BASE} python3"
REQ_SCAFFOLD="${REQ_BASE} python3"
REQ_JSON="${REQ_BASE} python3"
REQ_DOCS="${REQ_BASE} curl"
REQ_GUIDE="${REQ_BASE} curl"
REQ_COMPARE="${REQ_BASE} cmp diff python3"
REQ_TEST="${REQ_BASE} cmp diff python3"
REQ_ALL="${REQ_BASE} cmp diff curl python3"
# 無くても進めるが、あれば使うもの。無ければ警告だけ出す。
REC_ALL="git"

# $1 の入れ方の手がかりを出す（改行は付けない）。
install_hint() {
	case "$1" in
	python3)
		pick "macOS: xcode-select --install か brew install python / Debian・Ubuntu: sudo apt-get install python3 / Fedora: sudo dnf install python3 / Alpine: apk add python3" \
			"macOS: xcode-select --install or brew install python / Debian, Ubuntu: sudo apt-get install python3 / Fedora: sudo dnf install python3 / Alpine: apk add python3"
		;;
	curl)
		pick "macOS: 標準で入っています（無ければ brew install curl）/ Debian・Ubuntu: sudo apt-get install curl / Fedora: sudo dnf install curl / Alpine: apk add curl" \
			"macOS: ships with the OS (otherwise brew install curl) / Debian, Ubuntu: sudo apt-get install curl / Fedora: sudo dnf install curl / Alpine: apk add curl"
		;;
	git)
		pick "macOS: xcode-select --install か brew install git / Debian・Ubuntu: sudo apt-get install git / Fedora: sudo dnf install git / Alpine: apk add git" \
			"macOS: xcode-select --install or brew install git / Debian, Ubuntu: sudo apt-get install git / Fedora: sudo dnf install git / Alpine: apk add git"
		;;
	cmp | diff)
		pick "Debian・Ubuntu: sudo apt-get install diffutils / Fedora: sudo dnf install diffutils / Alpine: apk add diffutils" \
			"Debian, Ubuntu: sudo apt-get install diffutils / Fedora: sudo dnf install diffutils / Alpine: apk add diffutils"
		;;
	*)
		pick "POSIX の基本コマンドです。PATH にあるかを確かめてください（Debian・Ubuntu: coreutils / findutils / sed / gawk か mawk）" \
			"A basic POSIX command. Check that it is on PATH (Debian, Ubuntu: coreutils / findutils / sed / gawk or mawk)"
		;;
	esac
}

# $1 = 必須のコマンド（空白区切り）, $2 = あれば使うコマンド（空白区切り）。
# 足りないものを**全部まとめて**出す。必須が1つでも欠けていれば 1 を返す（止まるかは呼び出し側）。
check_commands() {
	_cc_missing=""
	for _cc_c in $1; do
		command -v "$_cc_c" >/dev/null 2>&1 || _cc_missing="${_cc_missing} ${_cc_c}"
	done
	_cc_rec=""
	for _cc_c in ${2:-}; do
		command -v "$_cc_c" >/dev/null 2>&1 || _cc_rec="${_cc_rec} ${_cc_c}"
	done
	if [ -n "$_cc_rec" ]; then
		{
			printf '\n[!] %s\n' "$(pick "次のコマンドが見つかりません。無くても進めますが、あったほうが確実です:" \
				"These commands were not found. You can go on without them, but it is safer to have them:")"
			for _cc_c in $_cc_rec; do
				printf '    - %s: %s\n' "$_cc_c" "$(install_hint "$_cc_c")"
			done
			case "${_cc_rec} " in
			*" git "*)
				printf '      %s\n' "$(pick "git が無いと Git 管理下かを判定できず、未追跡のファイルの確認も飛ばします。" \
					"Without git, the script cannot tell whether the project is under Git, and skips the check for untracked files.")"
				;;
			esac
		} >&2
	fi
	[ -z "$_cc_missing" ] && return 0
	{
		printf '\n[x] %s\n' "$(pick "このコマンドに必要なものが足りません。次を入れてから、同じコマンドをやり直してください:" \
			"Some commands this needs are missing. Install the following, then run the same command again:")"
		for _cc_c in $_cc_missing; do
			printf '    - %s: %s\n' "$_cc_c" "$(install_hint "$_cc_c")"
		done
		printf '    %s\n' "$(pick "入れたのに見つからない場合は、PATH を確かめてください（いまの PATH: ${PATH}）。" \
			"If you installed them and they are still not found, check PATH (current PATH: ${PATH}).")"
	} >&2
	return 1
}

# 必須のコマンドが無ければ、足りないものを全部並べて止まる。
require_commands() {
	check_commands "$1" "${2:-}" || exit 1
}

# ---------------------------------------------------------------- 共通

# このスクリプト自身が置かれているディレクトリ。
script_dir() {
	case "$0" in
	*/*) (cd "${0%/*}" && pwd) ;;
	*) pwd ;;
	esac
}

# 作業用ディレクトリの配置:
#   <作業用ディレクトリ>/check-list.html, project-code-base-report.html   人が開くもの
#   <作業用ディレクトリ>/tmp/katachi-install.sh                           init が写した自分
#   <作業用ディレクトリ>/tmp/cache/                                       lang・docs・MANIFEST・取得物
# 直下を人が開く2つの HTML と tmp/ だけにするため、スクリプトと cache/ は tmp/ の下に置く。
#
# 作業用ディレクトリ。init が自分をそこへコピーするので、2回目以降は
# 「自分の隣」が作業用ディレクトリになる。引数は要らない。
# 旧配置（直下に katachi-install.sh と cache/ がある）で呼ばれたら、見つけたうえで新配置へ移す。
resolve_workdir() {
	_sd=$(script_dir)
	if [ "${_sd##*/}" = "tmp" ] && [ -d "$_sd/cache" ] \
		&& { [ -s "$_sd/cache/lang" ] || [ -f "$_sd/../check-list.html" ]; }; then
		(cd "$_sd/.." && pwd)
		return 0
	fi
	if is_legacy_workdir "$_sd"; then
		migrate_layout "$_sd"
		printf '%s\n' "$_sd"
		return 0
	fi
	return 1
}

# 旧配置（直下に cache/ がある）の作業用ディレクトリか。
is_legacy_workdir() {
	[ -f "$1/check-list.html" ] || [ -s "$1/cache/lang" ] || [ -f "$1/cache/MANIFEST" ]
}

# 旧配置を新配置へ移す。何度呼んでもよい（移すものが無ければ何もしない）。
#   cache/ → tmp/cache/（MANIFEST の置き場所の列も書き換える）、katachi-install.sh → tmp/katachi-install.sh
# 実行中のスクリプト自身が旧位置にいるときは、tmp/ へ写してから旧位置のものを消す
# （実行中のファイルを消しても、このプロセスは読み続けられる）。
migrate_layout() {
	_mg_d="$1"
	[ -d "$_mg_d/cache" ] || [ -f "$_mg_d/katachi-install.sh" ] || return 0
	mkdir -p "$_mg_d/tmp/cache"
	if [ -d "$_mg_d/cache" ]; then
		for _mg_f in "$_mg_d"/cache/* "$_mg_d"/cache/.[!.]*; do
			[ -e "$_mg_f" ] || continue
			[ -e "$_mg_d/tmp/cache/${_mg_f##*/}" ] || mv "$_mg_f" "$_mg_d/tmp/cache/"
		done
		rmdir "$_mg_d/cache" 2>/dev/null || :
	fi
	if [ -s "$_mg_d/tmp/cache/MANIFEST" ]; then
		_mg_tmp="$_mg_d/tmp/cache/.MANIFEST.$$"
		awk -F'\t' -v OFS='\t' '{
			if ($4 != "" && index($4, "/tmp/cache/") == 0) {
				_i = 0; _s = $4
				while ((_p = index(substr(_s, _i + 1), "/cache/")) > 0) _i += _p
				if (_i > 0) $4 = substr($4, 1, _i - 1) "/tmp/cache/" substr($4, _i + 7)
			}
			print
		}' "$_mg_d/tmp/cache/MANIFEST" >"$_mg_tmp" && mv "$_mg_tmp" "$_mg_d/tmp/cache/MANIFEST"
	fi
	if [ -f "$_mg_d/katachi-install.sh" ]; then
		if [ "$(script_dir)" = "$(cd "$_mg_d" && pwd)" ] && [ "${0##*/}" = "katachi-install.sh" ]; then
			cp "$0" "$_mg_d/tmp/katachi-install.sh" && chmod +x "$_mg_d/tmp/katachi-install.sh"
		fi
		rm -f "$_mg_d/katachi-install.sh"
	fi
	printf '   %s\n' "$(pick "作業用ディレクトリの配置を新しくしました: katachi-install.sh と cache/ を tmp/ の下へ移しました（以降は ${_mg_d}/tmp/katachi-install.sh を使ってください）。" \
		"Updated the working directory layout: moved katachi-install.sh and cache/ under tmp/ (from now on use ${_mg_d}/tmp/katachi-install.sh).")" >&2
}

require_workdir() {
	WORKDIR=$(resolve_workdir) || die "作業用ディレクトリが分かりません。先に 'sh katachi-install.sh init' を実行し、以降は <作業用ディレクトリ>/tmp/katachi-install.sh を使ってください。" \
		"Cannot find the working directory. Run 'sh katachi-install.sh init' first, and from then on use <working directory>/tmp/katachi-install.sh."
	MANIFEST="$WORKDIR/tmp/cache/MANIFEST"
}

# ---------------------------------------------------------------- 検査用モジュールの置き場所

# --module-dir の値（Gradle ルートからの相対パス）として使えるかを確かめ、だめなら止まる。
# 先頭の / や ./、.. の段、末尾の /、空は受け付けない。
# init / scaffold の --module-dir と、preferences set moduleDir が同じ検査を通す。
validate_module_dir() {
	printf '%s' "${1:-}" | grep -qE '^[A-Za-z0-9_][A-Za-z0-9_.-]*(/[A-Za-z0-9_][A-Za-z0-9_.-]*)*$' ||
		die "--module-dir は Gradle ルートからの相対パスです（例: static-analysis/katachi）: ${1:-}" \
			"--module-dir must be a path relative to the Gradle root (for example static-analysis/katachi): ${1:-}"
}

# 相対パスを Gradle のプロジェクトパス（:a:b）にして出す。
module_gradle_path_of() {
	printf ':%s\n' "$(printf '%s' "$1" | tr '/' ':')"
}

# チェックリストの meta.<$2> を標準出力に出す（文字列でなければ空）。$1 = check-list.html。
# python3 が無い・ファイルが無い・JSON が読めないときも空（止まらない）。
read_checklist_meta() {
	[ -f "${1:-}" ] || return 0
	command -v python3 >/dev/null 2>&1 || return 0
	extract_json "$1" checklist 2>/dev/null | python3 -c '
import json, sys
try:
    v = (json.load(sys.stdin).get("meta") or {}).get(sys.argv[1])
except Exception:
    v = None
print(v if isinstance(v, str) else "")
' "$2" 2>/dev/null || :
}

# チェックリストの preferences.<$1>（書き方の方針。preferences set が記録する）を標準出力に出す。
# 文字列でなければ空。作業用ディレクトリが無い・python3 が無い・JSON が読めないときも空（止まらない）。
# scaffold（testFramework / dependencyStyle の既定）と preferences show が呼ぶ。
read_preference() {
	_rp_wd="${WORKDIR:-}"
	[ -n "$_rp_wd" ] || _rp_wd=$(resolve_workdir 2>/dev/null) || _rp_wd=""
	[ -n "$_rp_wd" ] && [ -f "$_rp_wd/check-list.html" ] || return 0
	command -v python3 >/dev/null 2>&1 || return 0
	extract_json "$_rp_wd/check-list.html" checklist 2>/dev/null | python3 -c '
import json, sys
try:
    v = (json.load(sys.stdin).get("preferences") or {}).get(sys.argv[1])
except Exception:
    v = None
print(v if isinstance(v, str) else "")
' "$1" 2>/dev/null || :
}

# 検査用モジュールの置き場所を決めて、次の変数に入れる。
#   MODULE_DIR          Gradle ルートからの相対パス（architecture-test、static-analysis/katachi）
#   MODULE_GRADLE_PATH  Gradle のプロジェクトパス（:architecture-test、:static-analysis:katachi）
#   MODULE_DIR_SOURCE   どこから決めたか（arg / checklist / default）
#   MODULE_DIR_RECORDED チェックリストの meta.moduleDir（無ければ空。scaffold が食い違いを知らせるのに使う）
# 優先順: $1（引数）> チェックリストの meta.moduleDir > 既定。
# scaffold / lint / compare-violations / summary / test / preferences の先頭で、require_workdir の
# 直後に呼ぶ（作業用ディレクトリが無い scaffold は引数か既定で決まる）。init は呼ばず、
# 引数か既定を自分で入れる。
resolve_module_dir() {
	_rm_wd="${WORKDIR:-}"
	[ -n "$_rm_wd" ] || _rm_wd=$(resolve_workdir 2>/dev/null) || _rm_wd=""
	MODULE_DIR_RECORDED=""
	[ -n "$_rm_wd" ] && MODULE_DIR_RECORDED=$(read_checklist_meta "$_rm_wd/check-list.html" moduleDir)
	if [ -n "${1:-}" ]; then
		MODULE_DIR="$1"
		MODULE_DIR_SOURCE="arg"
	elif [ -n "$MODULE_DIR_RECORDED" ]; then
		MODULE_DIR="$MODULE_DIR_RECORDED"
		MODULE_DIR_SOURCE="checklist"
	else
		MODULE_DIR="$MODULE_DIR_DEFAULT"
		MODULE_DIR_SOURCE="default"
	fi
	validate_module_dir "$MODULE_DIR"
	MODULE_GRADLE_PATH=$(module_gradle_path_of "$MODULE_DIR")
}

# 定義のディレクトリ（<プロジェクトルート>/<MODULE_DIR>/src/test/kotlin）を標準出力に出す。
# プロジェクトルートは init がチェックリストに記録したもの、無ければカレントディレクトリ。
# resolve_module_dir のあとで呼ぶ。
definition_dir() {
	_dd_wd="${WORKDIR:-}"
	[ -n "$_dd_wd" ] || _dd_wd=$(resolve_workdir 2>/dev/null) || _dd_wd=""
	_dd_root=""
	[ -n "$_dd_wd" ] && _dd_root=$(read_checklist_meta "$_dd_wd/check-list.html" projectRoot)
	[ -n "$_dd_root" ] || _dd_root="$PWD"
	printf '%s/%s/src/test/kotlin\n' "$_dd_root" "$MODULE_DIR"
}

# 定義のディレクトリが無ければ止まる（lint / compare-violations）。test は同じ文面を warn にする。
# summary は呼ばない（scaffold 前に区切ったセッションの再開で使うため）。
require_definition_dir() {
	_rd_dir=$(definition_dir)
	[ -d "$_rd_dir" ] && return 0
	die "定義のディレクトリがありません: $(file_uri "$_rd_dir") 。scaffold をまだ実行していないか、置き場所が違います（記録: meta.moduleDir = ${MODULE_DIR}。変えるなら 'preferences set moduleDir=<path>' か 'scaffold --module-dir <path>'）" \
		"The definition directory does not exist: $(file_uri "$_rd_dir") . Either scaffold has not run yet, or the location differs (recorded: meta.moduleDir = ${MODULE_DIR}; change it with 'preferences set moduleDir=<path>' or 'scaffold --module-dir <path>')"
}

# ---------------------------------------------------------------- 書き込みのロック

# チェックリストとレポートへの書き込み（data set / merge、add、check、warn、verify ...）を
# 1 プロセスずつにする。subagent が並べて add すると、読んで→書くの間に別のプロセスが
# 書いたぶんが消える（8 件中 1 件しか残らなかった）。
#
# 置き場: 対象が作業用ディレクトリの中なら <作業用ディレクトリ>/tmp/cache/.lock（1 つで両方の
# HTML を守る）、外のファイル（data set <任意のパス>）なら <対象>.lock。
# 取り方: mkdir（原子的）。取れたら pid.tmp に自分の pid を書き、mv で pid にする（読む側が
# 空の pid を見ない）。取れなければ 1 秒ごとに再試行し、KATACHI_LOCK_WAIT 秒（既定 30）で止まる。
# 残骸: pid があって kill -0 が失敗する → 残骸。pid が無く mtime が 60 秒以上前 → 残骸
# （mkdir と pid の書き込みの間でクラッシュしたもの）。pid が無く 60 秒未満 → 書き込み中とみなして待つ。
# 再入: 取った lock のパスを KATACHI_LOCK_HELD に持つ。外側（cmd_add など）が取っていれば
# cmd_data は取らない。放すのはトップレベルの trap（EXIT / INT / TERM）と、各コマンドの終わり。
# data_quietly のサブシェルは、親が保持中でなければ自分で取って、抜けるときに自分で放す。
# KATACHI_LOCK_WAIT はテストで短くするための環境変数。usage には書かない。
lock_path_for() {
	_lp_t=$1
	case $_lp_t in (/*) ;; (*) _lp_t="${PWD}/${_lp_t#./}" ;; esac
	_lp_d=${_lp_t%/*}
	[ -d "$_lp_d" ] && _lp_d=$(CDPATH='' cd -- "$_lp_d" && pwd)
	_lp_wd="${WORKDIR:-}"
	[ -n "$_lp_wd" ] || _lp_wd=$(resolve_workdir 2>/dev/null) || _lp_wd=""
	if [ -n "$_lp_wd" ]; then
		case "$_lp_d/" in
		("$_lp_wd"/*)
			printf '%s\n' "$_lp_wd/tmp/cache/.lock"
			return 0
			;;
		esac
	fi
	printf '%s\n' "$_lp_t.lock"
}

# $1 の lock が残骸なら 0。
lock_is_stale() {
	_ls_pid=""
	[ -f "$1/pid" ] && _ls_pid=$(cat "$1/pid" 2>/dev/null)
	case "$_ls_pid" in
	('' | *[!0-9]*)
		# stat は GNU / BSD で引数が違うので、find の -mmin で「60 秒以上前」を見る。
		[ -n "$(find "$1" -maxdepth 0 -mmin +1 2>/dev/null)" ]
		;;
	(*) ! kill -0 "$_ls_pid" 2>/dev/null ;;
	esac
}

# $1 = 書き込む対象のファイル。保持中なら何もしない。
lock_acquire() {
	[ -z "${KATACHI_LOCK_HELD:-}" ] || return 0
	_la_lock=$(lock_path_for "$1")
	_la_wait="${KATACHI_LOCK_WAIT:-30}"
	_la_n=0
	mkdir -p "${_la_lock%/*}"
	while :; do
		if mkdir "$_la_lock" 2>/dev/null; then
			printf '%s' "$$" >"$_la_lock/pid.tmp" && mv "$_la_lock/pid.tmp" "$_la_lock/pid"
			KATACHI_LOCK_HELD="$_la_lock"
			return 0
		fi
		if lock_is_stale "$_la_lock"; then
			lock_remove "$_la_lock"
			continue
		fi
		[ "$_la_n" -lt "$_la_wait" ] ||
			die "作業用ディレクトリのロックを ${_la_wait} 秒待ちましたが取れませんでした: ${_la_lock} 。ほかの katachi-install.sh が書き込み中か、残骸です。残骸なら rm -r で消してやり直してください" \
				"Waited ${_la_wait} seconds for the lock but could not get it: ${_la_lock} . Another katachi-install.sh is writing, or this is left over. If it is left over, remove it with rm -r and try again"
		sleep 1
		_la_n=$((_la_n + 1))
	done
}

# lock のディレクトリを消す（pid を消してから rmdir。別のプロセスが先に消していても失敗にしない）。
lock_remove() {
	rm -f "$1/pid" "$1/pid.tmp" 2>/dev/null || :
	rmdir "$1" 2>/dev/null || :
}

# 持っている lock を放す。持っていなければ何もしない。
lock_release() {
	[ -n "${KATACHI_LOCK_HELD:-}" ] || return 0
	lock_remove "$KATACHI_LOCK_HELD"
	KATACHI_LOCK_HELD=""
}

# 言語を決める。スクリプトの出力もこれに従うので、サブコマンドを振り分ける前に1回だけ呼ぶ。
#   init:   --lang（cmd_init が上書きする）> 環境変数 KATACHI_LANG > en（サイトの既定ロケール）
#   その他: --lang（受け付けるものだけ）> init が作業用ディレクトリに記録した言語
#           > 環境変数 KATACHI_LANG > ja
# init の既定を en にしているのは、チェックリストとレポートの既定に合わせるため（手順書が
# 「省略すると英語」と書いている）。init は必ず記録するので、init 後のコマンドが ja の既定に
# 落ちることはない。落ちるのは init の前に --help などを呼んだときだけ。
resolve_lang() {
	if [ "${1:-}" = "init" ]; then
		KATACHI_LANG="${KATACHI_LANG:-en}"
	else
		_rl_wd=$(resolve_workdir) || _rl_wd=""
		if [ -n "$_rl_wd" ] && [ -s "$_rl_wd/tmp/cache/lang" ]; then
			KATACHI_LANG=$(cat "$_rl_wd/tmp/cache/lang")
		fi
		KATACHI_LANG="${KATACHI_LANG:-ja}"
	fi
	# 記録や環境変数が壊れていても、出力が読めなくならないように ja に倒す。
	# init の --lang の誤りは cmd_init が止める。
	case "$KATACHI_LANG" in
	en | ja) ;;
	*) KATACHI_LANG="ja" ;;
	esac
	# 埋め込みの python（CHECK_PY など）も同じ言語で出すために渡す。
	export KATACHI_LANG
}

# 配信元を決める。環境変数 KATACHI_DOCS > init が作業用ディレクトリに記録した配信元 > 本番。
# init は記録する側なので、環境変数か本番だけを見る。
resolve_docs() {
	[ -n "$KATACHI_DOCS_ENV" ] && return 0
	[ "${1:-}" = "init" ] && return 0
	_rd_wd=$(resolve_workdir) || _rd_wd=""
	if [ -n "$_rd_wd" ] && [ -s "$_rd_wd/tmp/cache/docs" ]; then
		KATACHI_DOCS=$(cat "$_rd_wd/tmp/cache/docs")
	fi
}

# --lang の値を確かめて KATACHI_LANG に入れる。init と doctor が使う。
set_lang_option() {
	case "${1:-}" in
	en | ja)
		KATACHI_LANG="$1"
		KATACHI_LANG_EXPLICIT=yes
		export KATACHI_LANG
		;;
	*) die "--lang は en か ja です（指定されたのは '${1:-}'）。" "--lang must be en or ja (got '${1:-}')." ;;
	esac
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
	relink_fetched_page "$2"
	record_fetch "$1" "$2"
}

# 配信サイトの HTML は、互いを配信上のファイル名（install-check-list.html /
# project-code-base-report-template.html）でリンクする。作業用ディレクトリでは
# check-list.html / project-code-base-report.html という名前で置くので、置き直す。
relink_fetched_page() {
	case "$1" in
	*.html) ;;
	*) return 0 ;;
	esac
	_rl_tmp="$1.relink.$$"
	if sed -e 's#"\./install-check-list\.html"#"./check-list.html"#g' \
		-e 's#"\./project-code-base-report-template\.html"#"./project-code-base-report.html"#g' \
		"$1" >"$_rl_tmp"; then
		mv "$_rl_tmp" "$1"
	else
		rm -f "$_rl_tmp"
	fi
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
		die "settings.gradle.kts / settings.gradle が見つかりません。Gradle のルートディレクトリで実行してください（いまいるのは $(file_uri "$PWD")）。" \
			"settings.gradle.kts / settings.gradle not found. Run this in the Gradle root directory (you are in $(file_uri "$PWD"))."
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
	[ -f "$_ml_wd/tmp/cache/maven-local" ]
}

# $1 の katachi が下限（KATACHI_MIN_MAJOR.KATACHI_MIN_MINOR）以上かを確かめ、足りなければ止まる。
# 下限より前の版で進むと、手順書どおりに書いた定義が 3-2 でコンパイルできず（"Name".role { } が無い）、
# 原因が分かりにくい。KATACHI_MAVEN_LOCAL のときは
# 開発版を任意の版番号で試せるように飛ばす。ただし $2 が latest（GitHub の最新を
# 取ってきた版）なら飛ばさない。人が選んだ版ではないので、~/.m2 の開発版と
# 食い違っていても黙って進んでしまう。
require_katachi_version() {
	[ "${2:-}" != "latest" ] && maven_local_enabled && return 0
	_pv_mm=$(kotlin_major_minor "$1")
	[ -n "$_pv_mm" ] || return 0
	_pv_maj=${_pv_mm% *}
	_pv_min=${_pv_mm#* }
	if [ "$_pv_maj" -lt "$KATACHI_MIN_MAJOR" ] ||
		{ [ "$_pv_maj" -eq "$KATACHI_MIN_MAJOR" ] && [ "$_pv_min" -lt "$KATACHI_MIN_MINOR" ]; }; then
		die "katachi ${1} はこの手順には古すぎます。手順書とこのスクリプトが作る構成は katachi ${KATACHI_MIN_MAJOR}.${KATACHI_MIN_MINOR}.0 の機能（\"Name\".role { } など）で書いてあるため、${KATACHI_MIN_MAJOR}.${KATACHI_MIN_MINOR}.0 以降が必要です。
    ${KATACHI_RELEASES_PAGE} で ${KATACHI_MIN_MAJOR}.${KATACHI_MIN_MINOR}.0 以降が出ているかを確かめ、--katachi <version> で指定してやり直してください。" \
			"katachi ${1} is too old for this guide. The guide and the setup this script creates are written with katachi ${KATACHI_MIN_MAJOR}.${KATACHI_MIN_MINOR}.0 features (such as \"Name\".role { }), so they need ${KATACHI_MIN_MAJOR}.${KATACHI_MIN_MINOR}.0 or later.
    Check ${KATACHI_RELEASES_PAGE} for ${KATACHI_MIN_MAJOR}.${KATACHI_MIN_MINOR}.0 or later, and run again with --katachi <version>."
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
		sed -n \
			-e 's/.*version[[:space:]]*[("'"'"']\{1,2\}\([0-9][0-9.]*[0-9A-Za-z.-]*\).*/\1/p' \
			-e 's/.*kotlin-gradle-plugin:\([0-9][0-9.]*[0-9A-Za-z.-]*\).*/\1/p' |
		head -n 1)
	if [ -n "${_v:-}" ]; then
		printf '%s\n' "$_v"
		return 0
	fi

	# buildscript の時代の書き方: 版は gradle.properties に置き、build ファイルからは
	# `$kotlin_version` などで参照する。build ファイルの行には数字が無いのでここで拾う。
	if [ -f "gradle.properties" ]; then
		_v=$(sed -n -E 's/^[[:space:]]*(kotlin\.version|kotlinVersion|kotlin_version)[[:space:]]*=[[:space:]]*([0-9][0-9.]*[0-9A-Za-z.-]*).*/\2/p' gradle.properties |
			head -n 1)
	fi
	printf '%s\n' "${_v:-}"
}

# ---------------------------------------------------------------- JDK / toolchain

# katachi が要る JDK の最小の major。Gradle を動かす JDK も、jvmToolchain に書く N もこれ以上。
JDK_MIN_MAJOR="17"

# $1（java の実行ファイル）の major を出す。読めなければ空。"21.0.9" -> 21、"1.8.0_402" -> 8。
java_major_of() {
	_jm=$("$1" -version 2>&1 | sed -n 's/^.*version "\([0-9][0-9]*\)\.\{0,1\}\([0-9]*\).*".*/\1 \2/p' | head -n 1)
	[ -n "$_jm" ] || return 0
	_jm_a=${_jm% *}
	_jm_b=${_jm#* }
	if [ "$_jm_a" = "1" ] && [ -n "$_jm_b" ]; then
		printf '%s\n' "$_jm_b"
	else
		printf '%s\n' "$_jm_a"
	fi
}

# gradle.properties（プロジェクト、ホームの順）の org.gradle.java.home。あれば Gradle はそれで動く。
gradle_properties_java_home() {
	for _gj_f in gradle.properties "${GRADLE_USER_HOME:-$HOME/.gradle}/gradle.properties"; do
		[ -f "$_gj_f" ] || continue
		_gj=$(sed -n 's/^[[:space:]]*org\.gradle\.java\.home[[:space:]]*=[[:space:]]*//p' "$_gj_f" | head -n 1)
		if [ -n "$_gj" ]; then
			printf '%s\n' "$_gj"
			return 0
		fi
	done
	return 0
}

# Gradle を動かす JDK を調べる。結果は次の変数に入れる。
#   GRADLE_JDK_MAJOR  major（読めなければ空）
#   GRADLE_JDK_FROM   どこから分かったか（ja / en の順に `|` で区切る）
# 順番は Gradle の選び方に合わせる:
#   gradle/gradle-daemon-jvm.properties の toolchainVersion > org.gradle.java.home > JAVA_HOME > PATH の java
detect_gradle_jdk() {
	GRADLE_JDK_MAJOR=""
	GRADLE_JDK_FROM=""
	if [ -f "gradle/gradle-daemon-jvm.properties" ]; then
		_dj=$(sed -n 's/^[[:space:]]*toolchainVersion[[:space:]]*=[[:space:]]*\([0-9][0-9]*\).*/\1/p' gradle/gradle-daemon-jvm.properties | head -n 1)
		if [ -n "$_dj" ]; then
			GRADLE_JDK_MAJOR="$_dj"
			GRADLE_JDK_FROM="gradle/gradle-daemon-jvm.properties の toolchainVersion|toolchainVersion in gradle/gradle-daemon-jvm.properties"
			return 0
		fi
	fi
	_gh=$(gradle_properties_java_home)
	if [ -n "$_gh" ] && [ -x "$_gh/bin/java" ]; then
		GRADLE_JDK_MAJOR=$(java_major_of "$_gh/bin/java")
		GRADLE_JDK_FROM="org.gradle.java.home（${_gh}）|org.gradle.java.home (${_gh})"
		return 0
	fi
	if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
		GRADLE_JDK_MAJOR=$(java_major_of "$JAVA_HOME/bin/java")
		GRADLE_JDK_FROM="JAVA_HOME（${JAVA_HOME}）|JAVA_HOME (${JAVA_HOME})"
		return 0
	fi
	_jp=$(command -v java 2>/dev/null || true)
	if [ -n "$_jp" ]; then
		GRADLE_JDK_MAJOR=$(java_major_of "$_jp")
		GRADLE_JDK_FROM="PATH の java（${_jp}）|java on PATH (${_jp})"
	fi
	return 0
}

# Gradle を動かす JDK が JDK_MIN_MAJOR 以上かを確かめる。未満なら直し方つきで止まる。
# JDK を見つけられない・版を読めないときは、Gradle 自身が知っている可能性があるので警告だけで進む。
check_gradle_jdk() {
	detect_gradle_jdk
	if [ -z "$GRADLE_JDK_FROM" ]; then
		warn "Gradle を動かす JDK を見つけられませんでした（JAVA_HOME も PATH の java もありません）。katachi は JDK ${JDK_MIN_MAJOR} 以上が要ります。" \
			"Could not find the JDK that runs Gradle (there is no JAVA_HOME and no java on PATH). katachi needs JDK ${JDK_MIN_MAJOR} or later."
		return 0
	fi
	if [ -z "$GRADLE_JDK_MAJOR" ]; then
		warn "Gradle を動かす JDK の版を読めませんでした（$(pick "${GRADLE_JDK_FROM%%|*}" "${GRADLE_JDK_FROM#*|}")）。katachi は JDK ${JDK_MIN_MAJOR} 以上が要ります。" \
			"Could not read the version of the JDK that runs Gradle ($(pick "${GRADLE_JDK_FROM%%|*}" "${GRADLE_JDK_FROM#*|}")). katachi needs JDK ${JDK_MIN_MAJOR} or later."
		return 0
	fi
	if [ "$GRADLE_JDK_MAJOR" -lt "$JDK_MIN_MAJOR" ]; then
		die "Gradle を動かす JDK が ${GRADLE_JDK_MAJOR} です（${GRADLE_JDK_FROM%%|*}）。katachi は JDK ${JDK_MIN_MAJOR} 以上が要ります。
    JAVA_HOME を JDK ${JDK_MIN_MAJOR} 以上に向けるか、org.gradle.java.home（gradle.properties）で JDK ${JDK_MIN_MAJOR} 以上を
    指定してから、もう一度実行してください。" \
			"The JDK that runs Gradle is ${GRADLE_JDK_MAJOR} (${GRADLE_JDK_FROM#*|}). katachi needs JDK ${JDK_MIN_MAJOR} or later.
    Point JAVA_HOME at JDK ${JDK_MIN_MAJOR} or later, or set org.gradle.java.home in gradle.properties to one,
    then run this again."
	fi
	note "Gradle の JDK: ${GRADLE_JDK_MAJOR}（${GRADLE_JDK_FROM%%|*}）" \
		"Gradle's JDK: ${GRADLE_JDK_MAJOR} (${GRADLE_JDK_FROM#*|})"
}

# gradle/libs.versions.toml の [versions] から、アクセサ $1（libs.versions.<ここ>）の値の先頭の数字を出す。
# アクセサは - _ . を区切りとして同じ扱いにして突き合わせる。読めなければ何も出さない。
catalog_version_number() {
	[ -f "gradle/libs.versions.toml" ] || return 0
	sed 's:#.*::' "gradle/libs.versions.toml" | awk -v want="$1" '
		function norm(s) { gsub(/[-_.]/, ".", s); return s }
		/^[[:space:]]*\[/ { section = $0; next }
		section ~ /^[[:space:]]*\[versions\]/ && $0 ~ /=/ {
			key = $0
			sub(/[[:space:]]*=.*/, "", key)
			gsub(/[[:space:]"\047]/, "", key)
			if (norm(key) != norm(want)) next
			val = $0
			sub(/^[^=]*=[[:space:]]*/, "", val)
			if (match(val, /^["\047][0-9]+/)) { print substr(val, 2, RLENGTH - 1); exit }
		}
	'
}

# プロジェクトの build ファイル（と buildSrc / build-logic の Kotlin）から、JDK の toolchain の設定を探し、
# 見つけた分を「N<TAB>ファイル」で1行ずつ出す。N が決められなかった設定は「?<TAB>ファイル」。
#   - jvmToolchain(N)（kotlin { } の中も、buildSrc・build-logic の convention plugin の中も）
#   - JavaLanguageVersion.of(N)（java { toolchain { languageVersion = ... } }）
#   - N の代わりに libs.versions.<別名>（.get() / .toInt() が付いていてもよい）。version catalog から読む
#   - N の代わりに識別子（jvmToolchain(jvmTarget)）。同じファイルの `(const )?val jvmTarget(: Int)? = N` から読む
# 生成する $MODULE_DIR 自身と、ビルドの出力・キャッシュは見ない。
jvm_toolchain_candidates() {
	find . \( -path "./$MODULE_DIR" -o -name build -o -name .gradle -o -name .git -o -name .kotlin \
		-o -name .idea -o -name .local -o -name node_modules \) -prune -o \
		-type f \( -name '*.gradle.kts' -o -name '*.gradle' \
		-o \( -name '*.kt' \( -path '*/buildSrc/*' -o -path '*/build-logic/*' -o -path '*/buildLogic/*' \) \) \) \
		-print 2>/dev/null | sort |
		while IFS= read -r _jc_f; do
			sed -E 's#(^|[^:])//.*#\1#' "$_jc_f" | grep -E 'jvmToolchain|JavaLanguageVersion\.of' |
				while IFS= read -r _jc_l; do
					_jc_n=$(printf '%s' "$_jc_l" | sed -n -E \
						-e 's/.*jvmToolchain[[:space:]]*\([[:space:]]*"?([0-9]+)"?[[:space:]]*\).*/\1/p' \
						-e 's/.*JavaLanguageVersion\.of\([[:space:]]*"?([0-9]+)"?[[:space:]]*\).*/\1/p' | head -n 1)
					if [ -z "$_jc_n" ]; then
						_jc_a=$(printf '%s' "$_jc_l" | sed -n -E 's/.*libs\.versions\.([A-Za-z0-9_]+(\.[A-Za-z0-9_]+)*).*/\1/p' |
							head -n 1 | sed -E 's/(\.(get|toInt|toString|asProvider|orNull|getOrNull))+$//')
						[ -n "$_jc_a" ] && _jc_n=$(catalog_version_number "$_jc_a")
					fi
					if [ -z "$_jc_n" ]; then
						# jvmToolchain(jvmTarget) のように識別子で書かれている: 同じファイルの
						# `(const )?val <識別子>(: Int)? = N` を探す（build-logic の convention plugin に多い）。
						_jc_id=$(printf '%s' "$_jc_l" | sed -n -E \
							-e 's/.*jvmToolchain[[:space:]]*\([[:space:]]*([A-Za-z_][A-Za-z0-9_]*)[[:space:]]*\).*/\1/p' \
							-e 's/.*JavaLanguageVersion\.of\([[:space:]]*([A-Za-z_][A-Za-z0-9_]*)[[:space:]]*\).*/\1/p' | head -n 1)
						[ -n "$_jc_id" ] && _jc_n=$(sed -E 's#(^|[^:])//.*#\1#' "$_jc_f" |
							sed -n -E "s/.*(const[[:space:]]+)?val[[:space:]]+${_jc_id}([[:space:]]*:[[:space:]]*Int)?[[:space:]]*=[[:space:]]*\"?([0-9]+)\"?.*/\3/p" |
							head -n 1)
					fi
					printf '%s\t%s\n' "${_jc_n:-?}" "${_jc_f#./}"
				done
		done
}

# jvmToolchain に書く N を決める。結果は次の変数に入れる。
#   SC_TC_N     書く N（書かないなら空）
#   SC_TC_JA    検出の結果（ja。scaffold の出力とチェックリストに残す）
#   SC_TC_EN    同じ内容の英語
# 決め方: 見つかった N のうち最大のものを使う。17 未満は使わない。何も無ければ書かない
# （Gradle を動かしている JDK をそのまま使う）。
detect_jvm_toolchain() {
	SC_TC_N=""
	_tc_list=$(jvm_toolchain_candidates)
	_tc_best=""
	_tc_best_f=""
	_tc_old=""
	_tc_old_en=""
	_tc_unknown=""
	_tc_tab=$(printf '\t')
	_tc_nl='
'
	_tc_oldifs=$IFS
	IFS=$_tc_nl
	for _tc_row in $_tc_list; do
		_tc_n=${_tc_row%%"$_tc_tab"*}
		_tc_f=${_tc_row#*"$_tc_tab"}
		case "$_tc_n" in
		'?') _tc_unknown="${_tc_unknown:-$_tc_f}" ;;
		*)
			if [ "$_tc_n" -lt "$JDK_MIN_MAJOR" ]; then
				_tc_old="${_tc_old:-${_tc_n}（${_tc_f}）}"
				_tc_old_en="${_tc_old_en:-${_tc_n} (${_tc_f})}"
			elif [ -z "$_tc_best" ] || [ "$_tc_n" -gt "$_tc_best" ]; then
				_tc_best="$_tc_n"
				_tc_best_f="$_tc_f"
			fi
			;;
		esac
	done
	IFS=$_tc_oldifs

	if [ -n "$_tc_best" ]; then
		SC_TC_N="$_tc_best"
		SC_TC_JA="${_tc_best}（${_tc_best_f} の設定から）"
		SC_TC_EN="${_tc_best} (from ${_tc_best_f})"
	elif [ -n "$_tc_old" ]; then
		SC_TC_JA="書かない（既存の toolchain は ${_tc_old} で、katachi の要る ${JDK_MIN_MAJOR} 未満のため使わない。Gradle の JDK を使う）"
		SC_TC_EN="not written (the existing toolchain is ${_tc_old_en}, below the ${JDK_MIN_MAJOR} katachi needs, so it is not used; Gradle's JDK is used)"
	elif [ -n "$_tc_unknown" ]; then
		SC_TC_JA="書かない（${_tc_unknown} に toolchain の設定はあるが、N を読み取れなかった。Gradle の JDK を使う）"
		SC_TC_EN="not written (${_tc_unknown} sets a toolchain but N could not be read; Gradle's JDK is used)"
	else
		SC_TC_JA="書かない（プロジェクトに toolchain の設定が無い。Gradle の JDK を使う）"
		SC_TC_EN="not written (the project sets no toolchain; Gradle's JDK is used)"
	fi
}

# $1 を $2 にダウンロードする。
download() {
	require_commands curl
	# 接続は 10 秒で見切り、転送は 120 秒まで待つ。一過性の失敗は2回まで自動で
	# やり直す。ここで止まると手順全体が進まないので、バージョン取得より長い。
	curl -fsSL --connect-timeout 10 --max-time 120 --retry 2 --retry-delay 2 \
		-o "$2" "$1" ||
		die "ダウンロードに失敗しました: ${1}
  ネットワークかプロキシの設定を確認してください。
  手動で取得する場合は、このファイルを $(file_uri "$2") に置いてから同じコマンドをやり直してください。" \
			"Download failed: ${1}
  Check your network or proxy settings.
  To fetch it by hand, put the file at $(file_uri "$2") and run the same command again."
}

# ---------------------------------------------------------------- init

cmd_init() {
	init_workdir=""
	init_version=""
	init_version_source="explicit"
	init_offline="no"
	init_force="no"
	init_module_dir=""

	while [ $# -gt 0 ]; do
		case "$1" in
		--workdir)
			need_value "$1" "${2:-}"
			init_workdir="$2"
			shift 2
			;;
		--module-dir)
			need_value "$1" "${2:-}"
			validate_module_dir "$2"
			init_module_dir="$2"
			shift 2
			;;
		--katachi)
			need_value "$1" "${2:-}"
			# v0.3.0 のようなタグの形でも受ける。v を残すと版の判定を素通りし、build.gradle.kts に
			# version "v0.3.0" と書かれて解決できない。
			init_version="${2#v}"
			shift 2
			;;
		--offline)
			init_offline="yes"
			shift
			;;
		--lang)
			need_value "$1" "${2:-}"
			set_lang_option "$2"
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
		*) die "init: 知らないオプションです: ${1}" "init: unknown option: ${1}" ;;
		esac
	done

	# 要るコマンドを、ネットワークにもファイルにも触る前に確かめる。足りないものは
	# まとめて1回で言う。python3 は init 自身もチェックリストの初期化に使い、以降の
	# check / add / verify / summary も使うので、--offline でも要る。
	if [ "$init_offline" = "yes" ]; then
		require_commands "$REQ_INIT_OFFLINE" "$REC_ALL"
	else
		require_commands "$REQ_INIT" "$REC_ALL"
	fi

	require_gradle_root
	project_root=$(pwd)

	say "katachi のインストールを開始します" "Starting the katachi installation"
	note "プロジェクトルート: $(file_uri "$project_root")" \
		"Project root:       $(file_uri "$project_root")"
	note "settings:          $(file_uri "$SETTINGS_FILE")" \
		"settings:           $(file_uri "$SETTINGS_FILE")"

	# katachi のバージョン。下限（0.3.0）より前の版なら何も作らないうちに止まる。
	if [ -z "$init_version" ] && [ "$init_offline" = "no" ]; then
		init_version=$(fetch_latest_version)
		init_version_source="latest"
	fi
	if [ -z "$init_version" ]; then
		warn "katachi の最新バージョンを取得できませんでした。${KATACHI_RELEASES_PAGE} を見て --katachi で指定してください。" \
			"Could not get the latest katachi version. Look it up at ${KATACHI_RELEASES_PAGE} and pass it with --katachi."
		init_version="UNKNOWN"
	else
		require_katachi_version "$init_version" "$init_version_source"
		note "katachi:            ${init_version}"
	fi
	if maven_local_enabled; then
		note "mavenLocal:         有効（開発者向け。scaffold が mavenLocal() を足す）" \
			"mavenLocal:         on (for developers; scaffold adds mavenLocal())"
	fi

	if is_git_repo; then
		git_state="yes"
		note "Git:               管理下にあります" \
			"Git:                under version control"
	else
		git_state="no"
		warn "Git で管理されていません。失敗したときに変更を戻せないので、先にコミットするかバックアップを取ってください。" \
			"The project is not under Git. You cannot undo the changes if something goes wrong, so commit or back up first."
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

	mkdir -p "$init_workdir/tmp/cache"
	# 旧配置（直下に cache/ と katachi-install.sh）の作業用ディレクトリは、新配置へ移す。
	migrate_layout "$init_workdir"
	# KEY=VALUE の結果には素の絶対パスを出す（後続のステップは相対パスだと
	# 呼び出す場所に縛られる）。判定には指定どおりの init_workdir を使い続ける。
	init_workdir_abs=$(CDPATH='' cd -- "$init_workdir" && pwd)
	init_workdir_uri=$(file_uri "$init_workdir_abs")
	note "作業用ディレクトリ:  ${init_workdir_uri}" \
		"Working directory:  ${init_workdir_uri}"
	# --workdir で既存のディレクトリを指されたとき、katachi が作るもの以外があれば知らせる。
	# 消しも上書きもしない（利用者のファイルかもしれない）。
	_init_existing=$(find "$init_workdir" -mindepth 1 -maxdepth 1 \
		! -name check-list.html ! -name project-code-base-report.html ! -name '*.bak' ! -name tmp 2>/dev/null | wc -l | tr -d ' ')
	if [ "${_init_existing:-0}" -gt 0 ]; then
		note "既存のファイルがあります（${_init_existing} 件）。上書きはしません。" \
			"There are existing files (${_init_existing}). They are not overwritten."
	fi

	# 検査用モジュールの置き場所。--module-dir > 残したチェックリストの meta.moduleDir > 既定。
	# やり直し（引数なし）で既定に戻すと、scaffold --module-dir で記録した値が黙って変わる。
	_init_recorded_module_dir=$(read_checklist_meta "$init_workdir/check-list.html" moduleDir)
	if [ -n "$init_module_dir" ]; then
		MODULE_DIR="$init_module_dir"
	elif [ -n "$_init_recorded_module_dir" ] && [ "$init_force" = "no" ]; then
		MODULE_DIR="$_init_recorded_module_dir"
		validate_module_dir "$MODULE_DIR"
	else
		MODULE_DIR="$MODULE_DIR_DEFAULT"
	fi
	MODULE_GRADLE_PATH=$(module_gradle_path_of "$MODULE_DIR")
	note "検査用モジュール:    ${MODULE_DIR}/（Gradle のパス ${MODULE_GRADLE_PATH}）" \
		"Test module:        ${MODULE_DIR}/ (Gradle path ${MODULE_GRADLE_PATH})"

	# katachi は「未追跡だが ignore もされていない」ファイルも検査する
	# （git ls-files --others --exclude-standard 相当）。作業用ディレクトリが
	# それに当たると、ステップ4で必ず [UnexpectedFile] として落ちる。
	# 「提案してください」だけだと後回しにされ、原因不明の失敗になる。
	if ! path_is_ignored "$init_workdir"; then
		warn "${init_workdir_uri} は git から見えています。このままだとステップ4で必ず失敗します。
    katachi は未追跡でも ignore されていないファイルを検査するため、この作業用ディレクトリ
    自体が [UnexpectedFile] になります。ユーザに .gitignore への追加を提案してください
    （勝手に書き換えないこと）。" \
			"${init_workdir_uri} is visible to git. As it is, step 4 will fail for sure.
    katachi checks untracked files that are not ignored, so this working directory itself
    becomes an [UnexpectedFile]. Suggest to the user that it be added to .gitignore
    (do not edit .gitignore yourself)."
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
    宣言するか、.gitignore に足すかをユーザに確認してください。" \
				"There are untracked files that are not ignored. These also become [UnexpectedFile] in step 4.
    Ask the user whether to declare them or add them to .gitignore."
			printf '%s\n' "$_untracked" | while IFS= read -r _u; do
			printf '       %s\n' "$(file_uri "$_u")"
		done
		fi
	fi

	kotlin_version=$(detect_kotlin_version)
	if [ -n "$kotlin_version" ]; then
		note "Kotlin:             ${kotlin_version}（プロジェクトから検出）" \
			"Kotlin:             ${kotlin_version} (detected from the project)"
	else
		warn "Kotlin のバージョンを検出できませんでした。scaffold に --kotlin で渡してください。" \
			"Could not detect the Kotlin version. Pass it to scaffold with --kotlin."
	fi

	# 以降このスクリプトは作業用ディレクトリから使う。ここに自分を置いておけば、
	# セッションが切れても同じものを使い続けられる。
	MANIFEST="$init_workdir/tmp/cache/MANIFEST"
	mkdir -p "$init_workdir/tmp/cache"
	install_self "$init_workdir"
	# 以降のコマンドが同じ言語を使えるように記録する（出力の言語もこれに従う）。
	printf '%s' "$KATACHI_LANG" >"$init_workdir/tmp/cache/lang"
	# 配信元も記録し、以降のコマンド（docs など）が環境変数なしでも同じ配信元を使うようにする。
	# 本番（既定）なら記録を消す。前回ローカルに向けた記録が残ると、黙ってそちらを取り続ける。
	if [ "$KATACHI_DOCS" = "$KATACHI_DOCS_DEFAULT" ]; then
		rm -f "$init_workdir/tmp/cache/docs"
	else
		printf '%s' "$KATACHI_DOCS" >"$init_workdir/tmp/cache/docs"
		note "配信元:             ${KATACHI_DOCS}（KATACHI_DOCS。以降のコマンドもこれを使う）" \
			"Docs source:        ${KATACHI_DOCS} (KATACHI_DOCS; later commands use it too)"
	fi
	# KATACHI_MAVEN_LOCAL も同じく記録し、scaffold で付け忘れても効くようにする。
	case "${KATACHI_MAVEN_LOCAL:-}" in
	1 | yes | true) printf 'yes' >"$init_workdir/tmp/cache/maven-local" ;;
	esac
	# katachi の版も記録し、scaffold が --katachi なしでも同じ版を使うようにする。
	# 記録しないと scaffold が GitHub の最新を取り直し、init --katachi で固定した版と
	# 黙って食い違う。分からなかった（UNKNOWN）ときは古い記録を残さない。
	#
	# --katachi なしのやり直しでは、前回の記録を残す。最新で上書きすると、前回
	# --katachi で固定した版が黙って変わり、残したチェックリストの meta.version とも食い違う。
	_iv_recorded=""
	[ -s "$init_workdir/tmp/cache/version" ] && _iv_recorded=$(cat "$init_workdir/tmp/cache/version")
	if [ "$init_version_source" = "latest" ] && [ -n "$_iv_recorded" ] && [ "$init_force" = "no" ]; then
		if [ "$_iv_recorded" != "$init_version" ]; then
			note "                    → 前回の init が記録した ${_iv_recorded} を使い続けます（最新は ${init_version}。変えるなら --katachi を付けて実行し直す）" \
				"                    → keeping ${_iv_recorded}, recorded by the previous init (the latest is ${init_version}; to change it, run again with --katachi)"
		fi
		init_version="$_iv_recorded"
	elif [ "$init_version" = "UNKNOWN" ]; then
		rm -f "$init_workdir/tmp/cache/version"
	else
		printf '%s' "$init_version" >"$init_workdir/tmp/cache/version"
	fi

	# チェックリストとレポート。**すでにあるものは上書きしない。**
	# init をやり直したときに、記入済みの調査結果を消さないため。
	if [ "$init_offline" = "no" ]; then
		_cl="$init_workdir/check-list.html"
		_rp="$init_workdir/project-code-base-report.html"

		if [ -s "$_cl" ] && [ "$init_force" = "no" ]; then
			note "すでにあるので残しました: $(file_uri "$_cl")" \
				"Kept (already there): $(file_uri "$_cl")"
			# 版と置き場所だけは今回の値にそろえる（--katachi / --module-dir で変えた場合）。記入済みの欄は触らない。
			[ "$init_version" = "UNKNOWN" ] || update_checklist_meta "$_cl" version "$init_version"
			[ "$MODULE_DIR" = "$_init_recorded_module_dir" ] || update_checklist_meta "$_cl" moduleDir "$MODULE_DIR"
		else
			fetch_once "$KATACHI_DOCS/install/$KATACHI_LANG/install-check-list.html" "$_cl" yes
			write_checklist_data "$_cl" \
				"$init_workdir" "$init_version" "$project_root" \
				"${kotlin_version:-}" "$git_state" "$MODULE_DIR"
			note "配置しました:        $(file_uri "$_cl")" \
				"Placed:             $(file_uri "$_cl")"
		fi

		if [ -s "$_rp" ] && [ "$init_force" = "no" ]; then
			note "すでにあるので残しました: $(file_uri "$_rp")" \
				"Kept (already there): $(file_uri "$_rp")"
		else
			fetch_once "$KATACHI_DOCS/install/$KATACHI_LANG/project-code-base-report-template.html" "$_rp" yes
			write_report_data "$_rp" "$project_root"
			note "配置しました:        $(file_uri "$_rp")" \
				"Placed:             $(file_uri "$_rp")"
		fi

		# 手順書も取っておく。以降は guide <節 ID> が節ごとに出す（全文を一度に読ませないため）。
		# 言語を変えて init し直したときに前の言語のものが残らないよう、毎回取り直す。
		fetch_once "$KATACHI_DOCS/install/$KATACHI_LANG/index.md" "$init_workdir/tmp/cache/index.md" yes
		note "手順書:             $(file_uri "$init_workdir/tmp/cache/index.md") （sh <CLI> guide で目次）" \
			"Guide:              $(file_uri "$init_workdir/tmp/cache/index.md") (sh <CLI> guide for the table of contents)"
	fi

	say ""
	say "== init の結果 ==========================================" \
		"== init result ========================================="
	say "KATACHI_WORKDIR=$init_workdir_abs"
	say "KATACHI_VERSION=$init_version"
	say "KATACHI_PROJECT_ROOT=$project_root"
	say "KATACHI_KOTLIN=${kotlin_version:-}"
	say "KATACHI_GIT=$git_state"
	say "KATACHI_SETTINGS=$project_root/$SETTINGS_FILE"
	say "KATACHI_CLI=$init_workdir_abs/tmp/katachi-install.sh"
	say "KATACHI_LANG=$KATACHI_LANG"
	say "KATACHI_MODULE_DIR=$MODULE_DIR"
	say "KATACHI_MODULE_PATH=$MODULE_GRADLE_PATH"
	say "========================================================"
	say ""
	say "以降は ${init_workdir_uri}/tmp/katachi-install.sh を使ってください（再ダウンロードは不要です）。" \
		"From now on, use ${init_workdir_uri}/tmp/katachi-install.sh (no need to download it again)."
	say ""
	say "次: プロジェクトを解析して ${init_workdir_uri}/project-code-base-report.html を埋めてください。" \
		"Next: analyze the project and fill in ${init_workdir_uri}/project-code-base-report.html."
}

# このスクリプト自身を作業用ディレクトリへ置く。以降のステップはそちらを使う。
install_self() {
	_wd="$1"
	[ -f "$0" ] || return 0
	_here=$(script_dir)
	_there=$(cd "$_wd/tmp" && pwd)
	[ "$_here" = "$_there" ] && return 0
	cp "$0" "$_wd/tmp/katachi-install.sh" && chmod +x "$_wd/tmp/katachi-install.sh"
}

# cmd_data を、スクリプトごと終わらせずに呼ぶ。成功なら 0。
#
# cmd_data は失敗すると die（exit 1）する。if の条件に置いても exit はスクリプト全体を
# 終わらせるので、`if cmd_data ... >/dev/null 2>&1` と書くと、else の警告に届かないまま
# 何も言わずに止まる（python3 の無い環境で init が黙って exit 1 していた）。
# サブシェルで呼び、失敗の理由（stderr）は DATA_ERR に改行つきで入れて警告に添える。
data_quietly() {
	DATA_ERR=""
	# サブシェルには親の trap が引き継がれないので、親がロックを保持していなければ、
	# このサブシェルの中で取ったロックをサブシェルの終わりに自分で放す（die で抜けても残さない）。
	if _dq_err=$( (
		[ -n "${KATACHI_LOCK_HELD:-}" ] || trap lock_release EXIT
		cmd_data "$@" >/dev/null
	) 2>&1); then
		return 0
	fi
	[ -n "$_dq_err" ] && DATA_ERR="
${_dq_err}"
	return 1
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
	_module_dir="${7:-}"
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
		printf '    "git": %s,\n' "$(json_value "$_git")"
		printf '    "moduleDir": %s\n' "$(json_value "$_module_dir")"
		printf '  }\n'
		printf '}\n'
	} >"$_data"

	if data_quietly merge "$_file" "$_data" --id checklist; then
		rm -f "$_data" "$_file.bak"
	else
		rm -f "$_data"
		warn "チェックリストのメタ欄を初期化できませんでした。$(file_uri "$_file") を直接確認してください。${DATA_ERR}" \
			"Could not initialize the meta fields of the check list. Check $(file_uri "$_file") directly.${DATA_ERR}"
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
	if data_quietly merge "$1" "$_um_data" --id checklist; then
		rm -f "$_um_data" "$1.bak"
	else
		rm -f "$_um_data"
		warn "チェックリストの meta.${2} を ${3} にできませんでした。data merge で直してください。${DATA_ERR}" \
			"Could not set meta.${2} of the check list to ${3}. Fix it with data merge.${DATA_ERR}"
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

	if data_quietly merge "$_file" "$_data" --id report; then
		rm -f "$_data" "$_file.bak"
	else
		rm -f "$_data"
		warn "レポートのメタ欄を初期化できませんでした。$(file_uri "$_file") を直接確認してください。${DATA_ERR}" \
			"Could not initialize the meta fields of the report. Check $(file_uri "$_file") directly.${DATA_ERR}"
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
	sc_module_dir=""
	sc_tf=""
	sc_deps=""
	SC_DEPS_EXPLICIT="no"

	while [ $# -gt 0 ]; do
		case "$1" in
		--package)
			need_value "$1" "${2:-}"
			sc_package="$2"
			shift 2
			;;
		--module-dir)
			need_value "$1" "${2:-}"
			sc_module_dir="$2"
			shift 2
			;;
		--test-framework)
			need_value "$1" "${2:-}"
			case "$2" in
			junit5 | kotest) sc_tf="$2" ;;
			*) die "--test-framework は junit5 か kotest です: ${2}" "--test-framework must be junit5 or kotest: ${2}" ;;
			esac
			shift 2
			;;
		--deps)
			need_value "$1" "${2:-}"
			case "$2" in
			catalog | literal) sc_deps="$2" ;;
			*) die "--deps は catalog か literal です: ${2}" "--deps must be catalog or literal: ${2}" ;;
			esac
			SC_DEPS_EXPLICIT="yes"
			shift 2
			;;
		--katachi)
			need_value "$1" "${2:-}"
			# init と同じく、先頭の v は落とす。
			sc_version="${2#v}"
			shift 2
			;;
		--kotlin)
			need_value "$1" "${2:-}"
			sc_kotlin="$2"
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
		*) die "scaffold: 知らないオプションです: ${1}" "scaffold: unknown option: ${1}" ;;
		esac
	done

	require_commands "$REQ_SCAFFOLD"

	[ -n "$sc_package" ] || die "--package が必要です（例: --package com.example.app）" \
		"--package is required (for example: --package com.example.app)"
	printf '%s' "$sc_package" | grep -qE '^[a-z][a-zA-Z0-9_]*(\.[a-z][a-zA-Z0-9_]*)*$' ||
		die "--package がパッケージ名として不正です: ${sc_package}" \
			"--package is not a valid package name: ${sc_package}"

	require_gradle_root
	resolve_root_build_file

	# 検査用モジュールの置き場所。--module-dir > init が記録した meta.moduleDir > 既定。
	# 引数で変えたときは、記録との食い違いを知らせたうえで、書き込み後に記録を書き換える。
	resolve_module_dir "$sc_module_dir"
	if [ "$MODULE_DIR_SOURCE" = "arg" ] && [ -n "$MODULE_DIR_RECORDED" ] && [ "$MODULE_DIR_RECORDED" != "$MODULE_DIR" ]; then
		note "--module-dir ${MODULE_DIR} は init の記録（meta.moduleDir = ${MODULE_DIR_RECORDED}）と違います。記録を ${MODULE_DIR} に書き換えます。" \
			"--module-dir ${MODULE_DIR} differs from what init recorded (meta.moduleDir = ${MODULE_DIR_RECORDED}). The record is updated to ${MODULE_DIR}."
	fi

	# Gradle を動かす JDK が 17 以上か（未満なら直し方つきで止まる）と、jvmToolchain に書く N。
	check_gradle_jdk
	detect_jvm_toolchain

	# 生成物のコメントとテスト名も KATACHI_LANG に従う。scaffold は作業用ディレクトリに
	# 置かれたこのスクリプトから呼ばれるので、resolve_lang が init の記録を読んでいる。

	# katachi の版は --katachi > init が記録した版 > GitHub の最新 の順に決める。
	sc_version_source="explicit"
	if [ -z "$sc_version" ] && _sc_wd=$(resolve_workdir) && [ -s "$_sc_wd/tmp/cache/version" ]; then
		sc_version=$(cat "$_sc_wd/tmp/cache/version")
		sc_version_source="init"
	fi
	if [ -z "$sc_version" ]; then
		# 版を GitHub から取るときだけ curl が要る。
		require_commands curl
		sc_version=$(fetch_latest_version)
		sc_version_source="latest"
	fi
	[ -n "$sc_version" ] ||
		die "katachi のバージョンが分かりません。${KATACHI_RELEASES_PAGE} を見て --katachi で指定してください。" \
			"Cannot tell the katachi version. Look it up at ${KATACHI_RELEASES_PAGE} and pass it with --katachi."
	require_katachi_version "$sc_version" "$sc_version_source"
	if maven_local_enabled; then
		sc_maven_local="yes"
	else
		sc_maven_local="no"
	fi

	[ -n "$sc_kotlin" ] || sc_kotlin=$(detect_kotlin_version)
	[ -n "$sc_kotlin" ] ||
		die "Kotlin のバージョンを検出できませんでした。--kotlin で指定してください。" \
			"Could not detect the Kotlin version. Pass it with --kotlin."

	sc_mm=$(kotlin_major_minor "$sc_kotlin")
	[ -n "$sc_mm" ] || die "Kotlin のバージョンを読み取れませんでした: ${sc_kotlin}" \
		"Could not read the Kotlin version: ${sc_kotlin}"
	sc_kmaj=${sc_mm% *}
	sc_kmin=${sc_mm#* }

	if [ "$sc_kmaj" -lt "$KOTLIN_MIN_MAJOR" ] ||
		{ [ "$sc_kmaj" -eq "$KOTLIN_MIN_MAJOR" ] && [ "$sc_kmin" -lt "$KOTLIN_MIN_MINOR" ]; }; then
		die "katachi ${sc_version} は Kotlin ${KOTLIN_MIN_MAJOR}.${KOTLIN_MIN_MINOR} 以降が必要です（このプロジェクトは ${sc_kotlin}）。
    それより古いコンパイラは katachi の metadata を読めず、architecture などの
    シンボルがすべて Unresolved reference になります。プロジェクトの Kotlin を
    上げてから、もう一度実行してください。" \
			"katachi ${sc_version} needs Kotlin ${KOTLIN_MIN_MAJOR}.${KOTLIN_MIN_MINOR} or later (this project uses ${sc_kotlin}).
    Older compilers cannot read katachi's metadata, and every symbol such as
    architecture becomes an Unresolved reference. Upgrade the project's Kotlin,
    then run this again."
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
		die "$(file_uri "$MODULE_DIR")/ がすでに存在します。上書きするなら --force を付けてください。" \
			"$(file_uri "$MODULE_DIR")/ already exists. Add --force to overwrite it."
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

	# テストのフレームワーク: --test-framework > preferences.testFramework > 検出（kotest の依存があるか）> junit5。
	detect_test_framework
	sc_tf_from=""
	if [ -n "$sc_tf" ]; then
		sc_tf_from="--test-framework|--test-framework"
	else
		sc_tf=$(read_preference testFramework)
		case "$sc_tf" in
		junit5 | kotest) sc_tf_from="preferences.testFramework|preferences.testFramework" ;;
		"") sc_tf="" ;;
		*)
			warn "preferences.testFramework の値を知りません（${sc_tf}。junit5 か kotest）。検出した値を使います。" \
				"Unknown value in preferences.testFramework (${sc_tf}; expected junit5 or kotest). Using the detected value instead."
			sc_tf=""
			;;
		esac
		if [ -z "$sc_tf" ]; then
			sc_tf="$SC_TF_DETECTED"
			if [ -n "$SC_TF_FROM" ]; then
				sc_tf_from="検出: ${SC_TF_FROM}|detected from ${SC_TF_FROM}"
			else
				sc_tf_from="既定。kotest の依存が見つからない|the default; no kotest dependency found"
			fi
		fi
	fi

	# 依存の書き方: --deps > preferences.dependencyStyle > gradle/libs.versions.toml があれば catalog > literal。
	# catalog に決めても、書けない事情（別名の衝突、settings で別のファイルを libs にしている）があれば
	# literal に落とす。--deps catalog を明示したときだけ、衝突は止まる（黙って literal にしない）。
	sc_deps_from=""
	if [ -n "$sc_deps" ]; then
		sc_deps_from="--deps|--deps"
	else
		sc_deps=$(read_preference dependencyStyle)
		case "$sc_deps" in
		catalog | literal) sc_deps_from="preferences.dependencyStyle|preferences.dependencyStyle" ;;
		"") sc_deps="" ;;
		*)
			warn "preferences.dependencyStyle の値を知りません（${sc_deps}。catalog か literal）。検出した値を使います。" \
				"Unknown value in preferences.dependencyStyle (${sc_deps}; expected catalog or literal). Using the detected value instead."
			sc_deps=""
			;;
		esac
		if [ -z "$sc_deps" ]; then
			if [ -f "$CATALOG_FILE" ]; then
				sc_deps="catalog"
				sc_deps_from="検出: ${CATALOG_FILE}|detected: ${CATALOG_FILE}"
			else
				sc_deps="literal"
				sc_deps_from="${CATALOG_FILE} が無い|no ${CATALOG_FILE}"
			fi
		fi
	fi
	sc_deps_note=""
	if [ "$sc_deps" = "catalog" ]; then
		if [ ! -f "$CATALOG_FILE" ]; then
			sc_deps_note="${CATALOG_FILE} が無い|no ${CATALOG_FILE}"
		elif catalog_is_remapped; then
			sc_deps_note="$(file_uri "$SETTINGS_FILE") の versionCatalogs { } が ${CATALOG_FILE} 以外を使っている|versionCatalogs { } in $(file_uri "$SETTINGS_FILE") uses something other than ${CATALOG_FILE}"
		else
			plan_catalog_entries "$sc_version" "$sc_konsist" "$sc_tf"
			sc_deps_note="$SC_CAT_FALLBACK"
		fi
		if [ -n "$sc_deps_note" ]; then
			if [ "$SC_DEPS_EXPLICIT" = "yes" ]; then
				die "${sc_deps_note%%|*}。--deps literal にするか、カタログの別名を直してください。" \
					"${sc_deps_note#*|}. Use --deps literal, or fix the alias in the catalog."
			fi
			sc_deps="literal"
			sc_deps_from="${sc_deps_from%%|*}。catalog には書かない|${sc_deps_from#*|}; not written to the catalog"
		fi
	fi
	# placement が module（サブプロジェクトだけが版付き）で catalog に Kotlin JVM の別名があれば、
	# 版を直書きせずその別名で apply する（同じ版でも id が違えば落ちるので、別名から取る）。
	sc_kotlin_plugin_from=""
	if [ "$sc_deps" = "catalog" ] && [ "$kotlin_placement" = "module" ]; then
		_sc_kalias=$(catalog_alias_for plugins "org.jetbrains.kotlin.jvm")
		if [ -n "$_sc_kalias" ]; then
			sc_kotlin_plugin="alias($(catalog_accessor_of_alias plugins "$_sc_kalias"))"
			sc_kotlin_plugin_from="catalog"
		fi
	fi
	sc_kotest_version=""
	[ "$sc_tf" = "kotest" ] && sc_kotest_version=$(kotest_version_literal)

	# include 済みの判定。include は Gradle パスの形（":static-analysis:katachi"）で書くが、
	# 1 階層のときは "architecture-test" と ":architecture-test" の両方に当てる。
	# 正規表現では . が任意の 1 文字なので、置き場所の . は \. にしてから当てる。
	_sc_inc_pat=$(printf '%s' "$MODULE_GRADLE_PATH" | sed -e 's/^://' -e 's/\./\\./g')
	if sed 's://.*::' "$SETTINGS_FILE" 2>/dev/null |
		grep -qE "^[[:space:]]*include[[:space:](]+[^)]*[\"']:?${_sc_inc_pat}[\"']"; then
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

	say "作成する内容" "What will be created"
	note "パッケージ:   ${sc_package}" \
		"Package:      ${sc_package}"
	case "$sc_version_source" in
	init) note "katachi:      ${sc_version}（init が記録した版）" \
		"katachi:      ${sc_version} (recorded by init)" ;;
	latest) note "katachi:      ${sc_version}（GitHub の最新リリース）" \
		"katachi:      ${sc_version} (the latest release on GitHub)" ;;
	*) note "katachi:      ${sc_version}" ;;
	esac
	note "Kotlin:       ${sc_kotlin}"
	note "JVM toolchain: ${SC_TC_JA}" \
		"JVM toolchain: ${SC_TC_EN}"
	note "konsist:      ${sc_konsist}"
	note "テスト:       ${sc_tf}（${sc_tf_from%%|*}）" \
		"Test framework: ${sc_tf} (${sc_tf_from#*|})"
	if [ "$sc_deps" = "catalog" ]; then
		note "依存:         catalog（${sc_deps_from%%|*}。$(file_uri "$CATALOG_FILE") に追記）" \
			"Dependencies: catalog (${sc_deps_from#*|}; appended to $(file_uri "$CATALOG_FILE"))"
		if [ -n "$SC_CATALOG_ADDED" ]; then
			catalog_additions | sed 's/^/                /'
		else
			note "              追記なし（必要な項目はすべてある）" \
				"              nothing to append (every entry is already there)"
		fi
	else
		note "依存:         literal（${sc_deps_from%%|*}）" \
			"Dependencies: literal (${sc_deps_from#*|})"
		[ -n "$sc_deps_note" ] && note "              ${sc_deps_note%%|*}" "              ${sc_deps_note#*|}"
	fi
	case "$MODULE_DIR_SOURCE" in
	arg) note "モジュール:   $(file_uri "$MODULE_DIR")/（${MODULE_GRADLE_PATH}。--module-dir）" \
		"Module:       $(file_uri "$MODULE_DIR")/ (${MODULE_GRADLE_PATH}; from --module-dir)" ;;
	checklist) note "モジュール:   $(file_uri "$MODULE_DIR")/（${MODULE_GRADLE_PATH}。init が記録した meta.moduleDir）" \
		"Module:       $(file_uri "$MODULE_DIR")/ (${MODULE_GRADLE_PATH}; meta.moduleDir recorded by init)" ;;
	*) note "モジュール:   $(file_uri "$MODULE_DIR")/（${MODULE_GRADLE_PATH}。既定）" \
		"Module:       $(file_uri "$MODULE_DIR")/ (${MODULE_GRADLE_PATH}; the default)" ;;
	esac
	note "ソース:       $(file_uri "$src_dir")/" \
		"Sources:      $(file_uri "$src_dir")/"
	note "ルートに追加: ${root_needs_plugin} ($(file_uri "$ROOT_BUILD_FILE"))" \
		"Add to root:  ${root_needs_plugin} ($(file_uri "$ROOT_BUILD_FILE"))"
	[ "$kotlin_placement" = "module" ] &&
		note "              サブプロジェクトが Kotlin プラグインを版付きで宣言しているため、ルートには足さず ${MODULE_GRADLE_PATH} に版を書きます（${sc_kotlin_plugin}）
              Gradle が「The Kotlin Gradle plugin was loaded multiple times ...」と警告しますが、この構成では想定どおりです" \
			"              A subproject declares the Kotlin plugin with a version, so it is not added to the root; the version goes into ${MODULE_GRADLE_PATH} (${sc_kotlin_plugin})
              Gradle warns \"The Kotlin Gradle plugin was loaded multiple times ...\"; that is expected with this setup"
	note "include 追加: ${settings_needs_include} ($(file_uri "$SETTINGS_FILE"))" \
		"Add include:  ${settings_needs_include} ($(file_uri "$SETTINGS_FILE"))"
	note "pluginManagement に追加: ${settings_needs_plugin_repos}${plugin_repos_missing:+（${plugin_repos_missing}）}" \
		"Add to pluginManagement: ${settings_needs_plugin_repos}${plugin_repos_missing:+ (${plugin_repos_missing})}"
	[ "$sc_maven_local" = "yes" ] && note "mavenLocal:   有効（開発者向け）" \
		"mavenLocal:   on (for developers)"

	if [ "$sc_dry" = "yes" ]; then
		say ""
		say "--dry-run のため何も書きませんでした。" "Nothing was written (--dry-run)."
		return 0
	fi

	mkdir -p "$src_dir"
	# catalog を先に書く。build.gradle.kts が参照するアクセサは、ここで足した項目から作られる。
	[ "$sc_deps" = "catalog" ] && write_catalog_entries
	write_module_build "$sc_version" "$sc_konsist" "$sc_context_flag" "$sc_package" "$sc_kotlin_plugin" "$SC_TC_N" "$sc_deps" "$sc_tf" "$sc_kotest_version"
	# **--force でも定義は上書きしない。** ここには人とエージェントが書いた
	# architecture { } が入っている。やり直しで消えると取り返しがつかない。
	if [ -s "$src_dir/ProjectArchitecture.kt" ]; then
		note "すでにあるので残しました: $(file_uri "$src_dir/ProjectArchitecture.kt")" \
			"Kept (already there): $(file_uri "$src_dir/ProjectArchitecture.kt")"
	else
		write_architecture_kt "$sc_package" "$src_dir"
	fi
	write_test_kt "$sc_package" "$src_dir" "$sc_konsist" "$sc_tf"

	ROOT_PLUGIN_MANUAL=""
	SETTINGS_MANUAL=""
	SETTINGS_INCLUDE_MANUAL=""
	[ "$root_needs_plugin" = "yes" ] && add_root_plugin "$sc_kotlin"
	[ "$settings_needs_include" = "yes" ] && add_settings_include
	[ "$settings_needs_plugin_repos" = "yes" ] && add_plugin_repositories "$plugin_repos_missing"
	DEPENDENCY_MAVEN_LOCAL=""
	[ "$sc_maven_local" = "yes" ] && add_dependency_maven_local

	say ""
	say "== 作成しました ========================================" \
		"== Created ============================================="
	sc_root_build_uri=$(file_uri "$ROOT_BUILD_FILE")
	sc_settings_uri=$(file_uri "$SETTINGS_FILE")
	say "$(file_uri "$MODULE_DIR/build.gradle.kts")"
	say "$(file_uri "$src_dir/ProjectArchitecture.kt")"
	say "$(file_uri "$src_dir/ProjectArchitectureTest.kt")"
	if [ "$sc_deps" = "catalog" ]; then
		if [ -n "$SC_CATALOG_ADDED" ]; then
			say "$(file_uri "$CATALOG_FILE") （次の項目を追記）" \
				"$(file_uri "$CATALOG_FILE") (appended the following)"
			catalog_additions | sed 's/^/    /'
		else
			say "$(file_uri "$CATALOG_FILE") （追記なし。必要な項目はすべてあった）" \
				"$(file_uri "$CATALOG_FILE") (nothing appended; every entry was already there)"
		fi
	fi
	[ "$root_needs_plugin" = "yes" ] && say "${sc_root_build_uri} （Kotlin JVM プラグインを apply false で追加）" \
		"${sc_root_build_uri} (added the Kotlin JVM plugin with apply false)"
	[ "$settings_needs_include" = "yes" ] && [ -z "$SETTINGS_INCLUDE_MANUAL" ] &&
		say "${sc_settings_uri} （include を追加）" \
			"${sc_settings_uri} (added the include)"
	[ "$settings_needs_plugin_repos" = "yes" ] && [ -z "$SETTINGS_MANUAL" ] &&
		say "${sc_settings_uri} （pluginManagement の repositories に ${plugin_repos_missing} を追加）" \
			"${sc_settings_uri} (added ${plugin_repos_missing} to the repositories of pluginManagement)"
	[ "$DEPENDENCY_MAVEN_LOCAL" = "added" ] &&
		say "${sc_settings_uri} （dependencyResolutionManagement の repositories に mavenLocal() を追加。開発者向け）" \
			"${sc_settings_uri} (added mavenLocal() to the repositories of dependencyResolutionManagement; for developers)"
	say "========================================================"
	sc_fails_until="足すまで ./gradlew ${MODULE_GRADLE_PATH}:test は失敗します。"
	sc_fails_until_en="./gradlew ${MODULE_GRADLE_PATH}:test fails until you add it."
	if [ -n "$SETTINGS_MANUAL" ]; then
		warn "${sc_settings_uri} の pluginManagement { } の形を読み取れなかったため、自動で書き換えませんでした。" \
			"Could not read the shape of pluginManagement { } in ${sc_settings_uri}, so it was not edited automatically."
		say ""
		say "${sc_settings_uri} の pluginManagement { repositories { } } に、次を足してください
（pluginManagement { } が無ければ、ファイルの先頭に作ってください）。" \
			"Add the following to pluginManagement { repositories { } } in ${sc_settings_uri}
(if there is no pluginManagement { }, create one at the top of the file)."
		say ""
		say "$SETTINGS_MANUAL"
		say ""
		say "katachi の Gradle plugin（${KATACHI_PLUGIN_ID}）は Maven Central にあります。${sc_fails_until}" \
			"katachi's Gradle plugin (${KATACHI_PLUGIN_ID}) is on Maven Central. ${sc_fails_until_en}"
	fi
	if [ -n "$SETTINGS_INCLUDE_MANUAL" ]; then
		warn "${sc_settings_uri} の include 文の終わりを読み取れなかったため、include を自動で足しませんでした。" \
			"Could not find where the include statement in ${sc_settings_uri} ends, so the include was not added automatically."
		say ""
		say "次の1行を、${sc_settings_uri} の既存の include 文の後ろ（閉じ括弧より後）に足してください。" \
			"Add the following line after the existing include statement in ${sc_settings_uri} (after its closing parenthesis)."
		say ""
		say "$SETTINGS_INCLUDE_MANUAL"
		say ""
		say "$sc_fails_until" "$sc_fails_until_en"
	fi
	if [ "$DEPENDENCY_MAVEN_LOCAL" = "manual" ]; then
		warn "${sc_settings_uri} に dependencyResolutionManagement { repositories { } } が無いため、依存の解決先に mavenLocal() を足せませんでした（開発者向けの KATACHI_MAVEN_LOCAL）。" \
			"${sc_settings_uri} has no dependencyResolutionManagement { repositories { } }, so mavenLocal() could not be added to where dependencies are resolved (KATACHI_MAVEN_LOCAL, for developers)."
		say "  ${MODULE_GRADLE_PATH} が依存を解決している repositories { } に mavenLocal() を手で足してください。" \
			"  Add mavenLocal() by hand to the repositories { } that ${MODULE_GRADLE_PATH} resolves its dependencies from."
	fi
	if [ -n "${ROOT_PLUGIN_MANUAL:-}" ]; then
		warn "${sc_root_build_uri} は buildscript { } を持つため、自動で書き換えませんでした。" \
			"${sc_root_build_uri} has a buildscript { } block, so it was not edited automatically."
		say ""
		say "次の1行を、${sc_root_build_uri} の buildscript { } の**後ろ**にある
plugins { } の中に足してください（plugins { } が無ければ作ってください）。" \
			"Add the following line inside the plugins { } block that comes **after** buildscript { }
in ${sc_root_build_uri} (create plugins { } if there is none)."
		say ""
		say "$ROOT_PLUGIN_MANUAL"
		say ""
		say "$sc_fails_until" "$sc_fails_until_en"
	fi

	# init が Kotlin を検出できず --kotlin で受け取った場合、チェックリストの meta.kotlin が
	# 空のまま残り、verify が未記入として数え続ける。実際に使った版で埋める。
	# 置き場所も記録する（--module-dir で変えたとき、以降の lint / summary / test が従う）。
	if _sc_wd=$(resolve_workdir); then
		update_checklist_meta "$_sc_wd/check-list.html" kotlin "$sc_kotlin"
		update_checklist_meta "$_sc_wd/check-list.html" jvmToolchain "$(pick "$SC_TC_JA" "$SC_TC_EN")"
		[ "$MODULE_DIR" = "$MODULE_DIR_RECORDED" ] || update_checklist_meta "$_sc_wd/check-list.html" moduleDir "$MODULE_DIR"
	fi

	# 手順書の 2-2 と同じく、Gradle を直接起動せず CLI の test を勧める。
	if _sc_wd=$(resolve_workdir); then
		_sc_cli="$_sc_wd/tmp/katachi-install.sh"
	else
		_sc_cli="$0"
	fi
	say ""
	say "次: sh ${_sc_cli} test
    architecture { } が空なので、すべてのファイルが Unexpected として報告されて
    落ちるのが正常です。Unexpected 以外のエラーが出た場合だけが問題です。" \
		"Next: sh ${_sc_cli} test
    architecture { } is empty, so every file is reported as Unexpected and the test
    fails. That is expected. Only errors other than Unexpected are a problem."
}

# $MODULE_DIR/build.gradle.kts を書く。
#   $1 katachi の版   $2 konsist（yes/no）   $3 -Xcontext-parameters が要るか（yes/no）
#   $4 パッケージ     $5 Kotlin plugin の行   $6 jvmToolchain に書く N（空なら書かない）
#   $7 依存の書き方（catalog/literal）  $8 テストのフレームワーク（junit5/kotest）
#   $9 literal で書く Kotest の版（kotest のときだけ）
# catalog のときのアクセサは plan_catalog_entries が SC_ACC_* に入れている。
write_module_build() {
	_version="$1"
	_konsist="$2"
	_context_flag="$3"
	_package="$4"
	_kotlin_plugin="$5"
	_toolchain="${6:-}"
	_deps="${7:-literal}"
	_tf="${8:-junit5}"
	_kotest_version="${9:-$KOTEST_VERSION}"

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
    // katachi の DSL（mainSourceSet / ktFile など）はすべて context parameters
    // なので、無いと1つも書けない。Kotlin を 2.4 以降に上げたらこの2行は消すこと
    // （2.4 以降で付けたままだと redundant の警告が出る）。'
			;;
		*)
			_ctx_note='    // Kotlin before 2.4 needs this on the calling side to use context parameters.
    // Every katachi DSL entry point (mainSourceSet / ktFile ...) is a contextual
    // declaration, so without it you cannot write a single one. Drop these two lines once
    // the project moves to Kotlin 2.4 or later - from 2.4 on the flag warns that it is
    // redundant.'
			;;
		esac
		_tc_block=""
		[ -n "$_toolchain" ] && _tc_block="    jvmToolchain($_toolchain)

"
		_kotlin_block="kotlin {
$_tc_block$_ctx_note
    compilerOptions.freeCompilerArgs.add(\"-Xcontext-parameters\")
}

"
	elif [ -n "$_toolchain" ]; then
		_kotlin_block="kotlin {
    jvmToolchain($_toolchain)
}

"
	else
		# 書くものが無いので kotlin { } ごと省く。jvmToolchain を書かないと、Gradle を動かしている
		# JDK でコンパイルされる（JDK 17 が無く toolchain の自動取得も無い環境で、解決に失敗しない）。
		_kotlin_block=""
	fi

	# 依存と plugin の行。catalog ならアクセサ（plan_catalog_entries の SC_ACC_*）、literal なら座標を直書き。
	# JUnit 5 は jupiter + launcher、Kotest は runner + launcher（jupiter は書かない）。bom はどちらも。
	if [ "$_deps" = "catalog" ]; then
		_plugin_line="    alias(${SC_ACC_PLUGIN})"
		_katachi_line="    testImplementation(${SC_ACC_KATACHI})"
		_konsist_line="    testImplementation(${SC_ACC_KONSIST})"
		_bom_line="    testImplementation(platform(${SC_ACC_JUNIT_BOM}))"
		_engine_line="    testImplementation(${SC_ACC_JUNIT_JUPITER})"
		[ "$_tf" = "kotest" ] && _engine_line="    testImplementation(${SC_ACC_KOTEST})"
		_launcher_line="    testRuntimeOnly(${SC_ACC_JUNIT_LAUNCHER})"
	else
		_plugin_line="    id(\"$KATACHI_PLUGIN_ID\") version \"$_version\""
		_katachi_line="    testImplementation(\"me.tbsten.katachi:katachi:$_version\")"
		_konsist_line="    testImplementation(\"me.tbsten.katachi:katachi-konsist:$_version\")"
		_bom_line="    testImplementation(platform(\"org.junit:junit-bom:$JUNIT_VERSION\"))"
		_engine_line="    testImplementation(\"org.junit.jupiter:junit-jupiter\")"
		[ "$_tf" = "kotest" ] && _engine_line="    testImplementation(\"io.kotest:kotest-runner-junit5:$_kotest_version\")"
		_launcher_line="    testRuntimeOnly(\"org.junit.platform:junit-platform-launcher\")"
	fi
	[ "$_konsist" = "yes" ] || _konsist_line=""

	# Kotlin plugin の行は catalog でも版なしの kotlin("jvm") のまま（placement が module のときは
	# 版付きか catalog の別名）。catalog に寄せたくなった人が alias(libs.plugins.kotlin.jvm) に
	# 書き換えて落ちないように、理由と書き方をコメントで残す。
	_kotlin_plugin_note=""
	if [ "$_deps" = "catalog" ] && [ "$_kotlin_plugin" = 'kotlin("jvm")' ]; then
		case "$KATACHI_LANG" in
		ja)
			_kotlin_plugin_note='    // alias(libs.plugins.kotlin.jvm) と書かない。build-logic（includeBuild）経由で Kotlin plugin が
    // classpath にあると "already on the classpath with an unknown version" で落ちる。版なしの
    // kotlin("jvm") か、カタログに寄せるなら id(libs.plugins.kotlin.jvm.get().pluginId) を使う。
'
			;;
		*)
			_kotlin_plugin_note='    // Do not write alias(libs.plugins.kotlin.jvm). When the Kotlin plugin is already on the
    // classpath through build-logic (includeBuild), that fails with "already on the classpath
    // with an unknown version". Use the version-less kotlin("jvm"), or
    // id(libs.plugins.kotlin.jvm.get().pluginId) if you want it to come from the catalog.
'
			;;
		esac
	fi

	cat >"$MODULE_DIR/build.gradle.kts" <<EOF
plugins {
${_kotlin_plugin_note}    $_kotlin_plugin
$_plugin_note
$_plugin_line
}

${_kotlin_block}katachi {
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
$_katachi_line
$_konsist_line
$_bom_line
$_engine_line
$_launcher_line
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

# ProjectArchitectureTest.kt を書く。$1 パッケージ、$2 置き場所、$3 konsist（yes/no）、$4 junit5 / kotest。
# 違反の件数の上限（maxViolations）は書かない。導入中に全件を見たいときは、テストを書き換えずに
# -Dkatachi.maxViolations=all で出せる（katachi の Gradle plugin が test の JVM に渡す）。
write_test_kt() {
	_package="$1"
	_dir="$2"
	_konsist="$3"
	_tf="${4:-junit5}"

	# テスト名は利用者のリポジトリにそのまま残るので、--lang に合わせる。
	# 英語で進めている利用者に日本語の識別子を置いていかないため。
	case "$KATACHI_LANG" in
	ja) _test_name="プロジェクトの構成が定義どおりになっている" ;;
	*) _test_name="the project matches its declaration" ;;
	esac

	# konsist あり: FileConstraintCheck() を渡す（渡さないと konsist { } などの制約が評価されない）。
	# FileConstraintCheck は実験的 API なので opt-in が要る。
	if [ "$_konsist" = "yes" ]; then
		_assert_line="        projectArchitecture.assert(FileConstraintCheck())"
	else
		_assert_line="        projectArchitecture.assert()"
	fi

	if [ "$_tf" = "kotest" ]; then
		if [ "$_konsist" = "yes" ]; then
			cat >"$_dir/ProjectArchitectureTest.kt" <<EOF
@file:OptIn(ExperimentalKatachiApi::class)

package $_package.test.architecture

import io.kotest.core.spec.style.FunSpec
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.assert

class ProjectArchitectureTest : FunSpec({
    test("$_test_name") {
$_assert_line
    }
})
EOF
		else
			cat >"$_dir/ProjectArchitectureTest.kt" <<EOF
package $_package.test.architecture

import io.kotest.core.spec.style.FunSpec
import me.tbsten.katachi.check.assert

class ProjectArchitectureTest : FunSpec({
    test("$_test_name") {
$_assert_line
    }
})
EOF
		fi
	elif [ "$_konsist" = "yes" ]; then
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
$_assert_line
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
$_assert_line
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

# ---------------------------------------------------------------- version catalog への追記
#
# scaffold --deps catalog は gradle/libs.versions.toml に katachi / JUnit / Kotest の項目を足し、
# build.gradle.kts からは alias(libs.plugins.katachi) / libs.katachi のようにアクセサで参照する。
# ここにあるのはその読み取り（別名を中身で引く、アクセサを作る、衝突を見る）と書き込み。
# 対象は gradle/libs.versions.toml だけ。読むのは 1 行で閉じた項目だけ（複数行の { } は見ない）。

CATALOG_FILE="gradle/libs.versions.toml"

# [$1] の項目を「別名<TAB>中身<TAB>version.ref」で 1 行ずつ出す（version.ref は無ければ空）。
# 中身は libraries なら group:name、plugins なら id、versions なら値。コメントと空の項目は飛ばす。
catalog_entries() {
	[ -f "$CATALOG_FILE" ] || return 0
	sed 's:#.*::' "$CATALOG_FILE" | awk -v want="$1" '
		function unq(s) { gsub(/^[[:space:]"\047]+|[[:space:]"\047]+$/, "", s); return s }
		function field(s, name,   re, m) {
			re = "(^|[{,[:space:]])" name "[[:space:]]*=[[:space:]]*[\"\047][^\"\047]*[\"\047]"
			if (!match(s, re)) return ""
			m = substr(s, RSTART, RLENGTH)
			sub(/^[^=]*=[[:space:]]*/, "", m)
			return unq(m)
		}
		/^[[:space:]]*\[/ { section = $0; gsub(/[][[:space:]]/, "", section); next }
		section != want || $0 !~ /=/ { next }
		{
			key = $0
			sub(/[[:space:]]*=.*/, "", key)
			gsub(/[[:space:]"\047]/, "", key)
			val = $0
			sub(/^[^=]*=[[:space:]]*/, "", val)
			content = ""
			ref = ""
			if (val ~ /^["\047]/) {
				v = unq(val)
				if (want == "versions") content = v
				else {
					n = split(v, parts, ":")
					content = (want == "libraries" && n >= 2) ? parts[1] ":" parts[2] : parts[1]
				}
			} else if (want == "libraries") {
				content = field(val, "module")
				if (content == "") {
					g = field(val, "group")
					nm = field(val, "name")
					if (g != "" && nm != "") content = g ":" nm
				}
				ref = field(val, "version.ref")
			} else if (want == "plugins") {
				content = field(val, "id")
				ref = field(val, "version.ref")
			}
			if (key != "" && content != "") print key "\t" content "\t" ref
		}
	'
}

# 別名を Gradle のアクセサの区切りに揃える（- _ . を . に。camelCase はそのまま）。
catalog_norm_alias() {
	printf '%s\n' "$1" | tr '_-' '..'
}

# [$1] で中身が $2 の項目の別名を出す（無ければ空）。同じものを指す項目が既にあれば、
# 名前が違っても（katachi_konsist でも katachiKonsist でも）それを使うため。
catalog_alias_for() {
	catalog_entries "$1" | awk -F'\t' -v c="$2" '$2 == c { print $1; exit }'
}

# [$1] で中身が $2 の項目の version.ref を出す（無ければ空）。
catalog_version_ref_for() {
	catalog_entries "$1" | awk -F'\t' -v c="$2" '$2 == c { print $3; exit }'
}

# [libraries] で group が $1（io.kotest など）の項目のうち、最初の version.ref を出す（無ければ空）。
catalog_version_ref_for_group() {
	catalog_entries libraries | awk -F'\t' -v g="$1:" 'index($2, g) == 1 && $3 != "" { print $3; exit }'
}

# [$1] で別名 $2（区切りの違いは同一視）の中身を出す（無ければ空）。
catalog_alias_content() {
	catalog_entries "$1" | awk -F'\t' -v a="$(catalog_norm_alias "$2")" '
		function norm(s) { gsub(/[-_]/, ".", s); return s }
		norm($1) == a { print $2; exit }
	'
}

# [$1] で別名 $2（区切りの違いは同一視）の、ファイルに書かれているとおりの別名を出す（無ければ空）。
catalog_alias_key() {
	catalog_entries "$1" | awk -F'\t' -v a="$(catalog_norm_alias "$2")" '
		function norm(s) { gsub(/[-_]/, ".", s); return s }
		norm($1) == a { print $1; exit }
	'
}

# [$1] に別名 $2 が**別の中身**で使われていれば 0（衝突）。無い・同じ中身なら 1。
catalog_alias_conflict() {
	_cac=$(catalog_alias_content "$1" "$2")
	[ -n "$_cac" ] && [ "$_cac" != "$3" ]
}

# [$1] の既存の別名が $2 の接頭辞（- _ . 区切りの先頭部分）になっていれば 0。
# junit があるところに junit-bom を足すと、libs.junit が group になって既存の参照が壊れるので、
# その別名は使わず camelCase（junitBom）に逃がす。
catalog_alias_has_prefix_in() {
	catalog_entries "$1" | awk -F'\t' -v a="$(catalog_norm_alias "$2")" '
		function norm(s) { gsub(/[-_]/, ".", s); return s }
		{ k = norm($1); if (k != a && index(a, k ".") == 1) { found = 1; exit } }
		END { exit found ? 0 : 1 }
	'
}

# - _ . 区切りの別名を camelCase にする（junit-bom → junitBom。区切りが無ければそのまま）。
catalog_alias_camel() {
	printf '%s\n' "$1" | awk '{
		n = split($0, p, /[-_.]/)
		out = p[1]
		for (i = 2; i <= n; i++) out = out toupper(substr(p[i], 1, 1)) substr(p[i], 2)
		print out
	}'
}

# [$1] の別名 $2 を Kotlin DSL のアクセサにして出す（libs.junit.bom / libs.plugins.katachi）。
# 同じ節に $2 を接頭辞とする別名（katachi に対する katachi-konsist）があれば、Gradle は
# libs.katachi を group にするので、.asProvider() を付けた形で出す。
catalog_accessor_of_alias() {
	_coa=$(catalog_norm_alias "$2")
	_coa_prefix="libs."
	[ "$1" = "plugins" ] && _coa_prefix="libs.plugins."
	if catalog_entries "$1" | awk -F'\t' -v a="$_coa" '
		function norm(s) { gsub(/[-_]/, ".", s); return s }
		{ k = norm($1); if (k != a && index(k, a ".") == 1) { found = 1; exit } }
		END { exit found ? 0 : 1 }
	'; then
		printf '%s%s.asProvider()\n' "$_coa_prefix" "$_coa"
	else
		printf '%s%s\n' "$_coa_prefix" "$_coa"
	fi
}

# settings の versionCatalogs { } が、既定の gradle/libs.versions.toml 以外を libs にしているか。
# そのときは toml に足しても libs から見えないので、scaffold は literal に落とす。
catalog_is_remapped() {
	[ -f "${SETTINGS_FILE:-}" ] || return 1
	_cir=$(sed 's://.*::' "$SETTINGS_FILE")
	printf '%s' "$_cir" | grep -q 'versionCatalogs[[:space:]]*{' || return 1
	printf '%s' "$_cir" | grep -qE "[\"'](\./)?gradle/libs\.versions\.toml[\"']" && return 1
	return 0
}

# scaffold が catalog に足す項目を決める。結果は次の変数に入れる。
#   SC_CAT_<節>      追記する行（改行区切り。無ければ空）。節は VERSIONS / LIBRARIES / PLUGINS
#   SC_CATALOG_ADDED 追記する行が 1 つでもあれば yes（一覧は catalog_additions が出す）
#   SC_CAT_FALLBACK  catalog に書けない理由（ja|en）。空なら書ける
#   SC_ACC_*         build.gradle.kts に書くアクセサ（PLUGIN / KATACHI / KONSIST / JUNIT_BOM /
#                    JUNIT_JUPITER / JUNIT_LAUNCHER / KOTEST）
# 引数: $1 = katachi の版, $2 = konsist（yes/no）, $3 = テストのフレームワーク（junit5/kotest）
#
# 決め方（項目ごと）: 同じ中身の項目があればその別名を使う → 同じ別名が別の中身なら衝突
# （SC_CAT_FALLBACK）→ 既存の別名の接頭辞になるなら camelCase に逃がす → 足す。
# 版は、同じ中身の既存の項目に version.ref があればそれを使い、無ければ [versions] に足す。
plan_catalog_entries() {
	_pc_version="$1"
	_pc_konsist="$2"
	_pc_tf="$3"
	SC_CAT_VERSIONS=""
	SC_CAT_LIBRARIES=""
	SC_CAT_PLUGINS=""
	SC_CATALOG_ADDED=""
	SC_CAT_FALLBACK=""
	SC_ACC_PLUGIN=""
	SC_ACC_KATACHI=""
	SC_ACC_KONSIST=""
	SC_ACC_JUNIT_BOM=""
	SC_ACC_JUNIT_JUPITER=""
	SC_ACC_JUNIT_LAUNCHER=""
	SC_ACC_KOTEST=""

	# katachi の version.ref: 既存の katachi の項目（me.tbsten.katachi の library か plugin）が持っていればそれ。
	_pc_ref=$(catalog_version_ref_for_group me.tbsten.katachi)
	[ -n "$_pc_ref" ] || _pc_ref=$(catalog_version_ref_for plugins "$KATACHI_PLUGIN_ID")
	if [ -z "$_pc_ref" ]; then
		plan_catalog_version katachi "$_pc_version" || return 0
		_pc_ref="$PC_REF"
	fi
	plan_catalog_item plugins katachi "$KATACHI_PLUGIN_ID" "id = \"${KATACHI_PLUGIN_ID}\", version.ref = \"${_pc_ref}\"" || return 0
	SC_ACC_PLUGIN="$PC_ACCESSOR"
	plan_catalog_item libraries katachi "me.tbsten.katachi:katachi" "module = \"me.tbsten.katachi:katachi\", version.ref = \"${_pc_ref}\"" || return 0
	SC_ACC_KATACHI="$PC_ACCESSOR"
	if [ "$_pc_konsist" = "yes" ]; then
		plan_catalog_item libraries katachiKonsist "me.tbsten.katachi:katachi-konsist" "module = \"me.tbsten.katachi:katachi-konsist\", version.ref = \"${_pc_ref}\"" || return 0
		SC_ACC_KONSIST="$PC_ACCESSOR"
	fi

	# JUnit: bom と launcher は常に。jupiter は JUnit 5 のときだけ。版は bom（か jupiter）の version.ref。
	_pc_jref=$(catalog_version_ref_for libraries "org.junit:junit-bom")
	[ -n "$_pc_jref" ] || _pc_jref=$(catalog_version_ref_for libraries "org.junit.jupiter:junit-jupiter")
	if [ -z "$_pc_jref" ]; then
		plan_catalog_version junit "$JUNIT_VERSION" || return 0
		_pc_jref="$PC_REF"
	fi
	plan_catalog_item libraries junit-bom "org.junit:junit-bom" "module = \"org.junit:junit-bom\", version.ref = \"${_pc_jref}\"" || return 0
	SC_ACC_JUNIT_BOM="$PC_ACCESSOR"
	if [ "$_pc_tf" = "junit5" ]; then
		plan_catalog_item libraries junit-jupiter "org.junit.jupiter:junit-jupiter" "module = \"org.junit.jupiter:junit-jupiter\"" || return 0
		SC_ACC_JUNIT_JUPITER="$PC_ACCESSOR"
	fi
	plan_catalog_item libraries junit-platform-launcher "org.junit.platform:junit-platform-launcher" "module = \"org.junit.platform:junit-platform-launcher\"" || return 0
	SC_ACC_JUNIT_LAUNCHER="$PC_ACCESSOR"

	if [ "$_pc_tf" = "kotest" ]; then
		_pc_kref=$(catalog_version_ref_for_group io.kotest)
		if [ -z "$_pc_kref" ]; then
			plan_catalog_version kotest "$KOTEST_VERSION" || return 0
			_pc_kref="$PC_REF"
		fi
		plan_catalog_item libraries kotest-runner-junit5 "io.kotest:kotest-runner-junit5" "module = \"io.kotest:kotest-runner-junit5\", version.ref = \"${_pc_kref}\"" || return 0
		SC_ACC_KOTEST="$PC_ACCESSOR"
	fi
}

# [versions] に別名 $1 = "$2" を用意し、使う別名（version.ref に書く名前）を PC_REF に入れる。
# 同じ別名が同じ値なら既存のものを使う（値だけが同じ別の別名 — kotlin = "2.3.0" など — は使わない。
# version.ref で結ぶと、その別名の更新に katachi の版が引きずられる）。別の値なら衝突として
# SC_CAT_FALLBACK を入れて 1 を返す。$( ) の中で呼ばないこと（SC_CAT_* が失われる）。
plan_catalog_version() {
	PC_REF=""
	_pv_have=$(catalog_alias_content versions "$1")
	if [ -n "$_pv_have" ]; then
		if [ "$_pv_have" = "$2" ]; then
			PC_REF=$(catalog_alias_key versions "$1")
			return 0
		fi
		SC_CAT_FALLBACK="別名 ${1} が別の用途で使われています（[versions] ${1} = \"${_pv_have}\"）|alias ${1} is used for something else ([versions] ${1} = \"${_pv_have}\")"
		return 1
	fi
	SC_CAT_VERSIONS="${SC_CAT_VERSIONS}${SC_CAT_VERSIONS:+
}${1} = \"${2}\""
	SC_CATALOG_ADDED="yes"
	PC_REF="$1"
}

# [$1] に別名 $2（中身 $3、本文 $4）を用意し、アクセサを PC_ACCESSOR に入れる。
# 衝突なら SC_CAT_FALLBACK を入れて 1 を返す。
plan_catalog_item() {
	_pi_section="$1"
	_pi_alias="$2"
	_pi_content="$3"
	_pi_body="$4"
	_pi_have=$(catalog_alias_for "$_pi_section" "$_pi_content")
	if [ -n "$_pi_have" ]; then
		PC_ACCESSOR=$(catalog_accessor_of_alias "$_pi_section" "$_pi_have")
		return 0
	fi
	if catalog_alias_conflict "$_pi_section" "$_pi_alias" "$_pi_content"; then
		_pi_other=$(catalog_alias_content "$_pi_section" "$_pi_alias")
		SC_CAT_FALLBACK="別名 ${_pi_alias} が別の用途で使われています（[${_pi_section}] ${_pi_alias} → ${_pi_other}）|alias ${_pi_alias} is used for something else ([${_pi_section}] ${_pi_alias} → ${_pi_other})"
		return 1
	fi
	if catalog_alias_has_prefix_in "$_pi_section" "$_pi_alias"; then
		_pi_camel=$(catalog_alias_camel "$_pi_alias")
		if [ "$_pi_camel" != "$_pi_alias" ]; then
			if catalog_alias_conflict "$_pi_section" "$_pi_camel" "$_pi_content"; then
				_pi_other=$(catalog_alias_content "$_pi_section" "$_pi_camel")
				SC_CAT_FALLBACK="別名 ${_pi_camel} が別の用途で使われています（[${_pi_section}] ${_pi_camel} → ${_pi_other}）|alias ${_pi_camel} is used for something else ([${_pi_section}] ${_pi_camel} → ${_pi_other})"
				return 1
			fi
			_pi_alias="$_pi_camel"
		fi
	fi
	_pi_line="${_pi_alias} = { ${_pi_body} }"
	case "$_pi_section" in
	libraries) SC_CAT_LIBRARIES="${SC_CAT_LIBRARIES}${SC_CAT_LIBRARIES:+
}${_pi_line}" ;;
	plugins) SC_CAT_PLUGINS="${SC_CAT_PLUGINS}${SC_CAT_PLUGINS:+
}${_pi_line}" ;;
	esac
	SC_CATALOG_ADDED="yes"
	# 足す前なので、接頭辞の判定は追記後の形で行う（この別名の下に既存の別名があれば asProvider）。
	PC_ACCESSOR=$(catalog_accessor_of_alias "$_pi_section" "$_pi_alias")
}

# plan_catalog_entries が足すと決めた行を「[節] 行」の形で、節の順（versions / libraries / plugins）に出す。
# 「作成する内容」と「作成しました」が使う。
catalog_additions() {
	[ -z "$SC_CAT_VERSIONS" ] || printf '%s\n' "$SC_CAT_VERSIONS" | sed 's/^/[versions] /'
	[ -z "$SC_CAT_LIBRARIES" ] || printf '%s\n' "$SC_CAT_LIBRARIES" | sed 's/^/[libraries] /'
	[ -z "$SC_CAT_PLUGINS" ] || printf '%s\n' "$SC_CAT_PLUGINS" | sed 's/^/[plugins] /'
}

# plan_catalog_entries の結果（SC_CAT_*）を gradle/libs.versions.toml に書く。
# 追記は節の末尾（次の [ の直前。末尾の空行より前）。節が無ければファイルの末尾に作る。
# .bak に退避 → awk → mv。失敗したら戻して止まる。足すものが無ければ何もしない。
write_catalog_entries() {
	[ -n "${SC_CAT_VERSIONS}${SC_CAT_LIBRARIES}${SC_CAT_PLUGINS}" ] || return 0
	cp "$CATALOG_FILE" "$CATALOG_FILE.bak"
	_wc_tmp="$CATALOG_FILE.tmp.$$"
	# 追記する行は改行を含むので -v ではなく環境変数で渡す（macOS の awk は -v の値に改行を入れられない）。
	if KATACHI_ADD_VERSIONS="$SC_CAT_VERSIONS" KATACHI_ADD_LIBRARIES="$SC_CAT_LIBRARIES" KATACHI_ADD_PLUGINS="$SC_CAT_PLUGINS" awk '
		function pending(name) {
			if (name == "versions") return ENVIRON["KATACHI_ADD_VERSIONS"]
			if (name == "libraries") return ENVIRON["KATACHI_ADD_LIBRARIES"]
			if (name == "plugins") return ENVIRON["KATACHI_ADD_PLUGINS"]
			return ""
		}
		function flush_section() {
			if (section != "" && pending(section) != "" && !done[section]) { print pending(section); done[section] = 1 }
			# 節の末尾の空行は、追記した行の後ろに戻す
			printf "%s", blanks
			blanks = ""
		}
		/^[[:space:]]*\[/ {
			flush_section()
			section = $0
			gsub(/[][[:space:]]/, "", section)
			print
			next
		}
		/^[[:space:]]*$/ { blanks = blanks $0 "\n"; next }
		{ printf "%s", blanks; blanks = ""; print }
		END {
			flush_section()
			n = split("versions libraries plugins", order, " ")
			for (i = 1; i <= n; i++) {
				name = order[i]
				if (pending(name) != "" && !done[name]) { print ""; print "[" name "]"; print pending(name); done[name] = 1 }
			}
		}
	' "$CATALOG_FILE" >"$_wc_tmp"; then
		mv "$_wc_tmp" "$CATALOG_FILE"
		rm -f "$CATALOG_FILE.bak"
		return 0
	fi
	rm -f "$_wc_tmp"
	mv "$CATALOG_FILE.bak" "$CATALOG_FILE"
	die "$(file_uri "$CATALOG_FILE") の書き換えに失敗しました。元に戻しました。" \
		"Failed to edit $(file_uri "$CATALOG_FILE"). It was restored."
}

# テストのフレームワークを検出する。結果は次の変数に入れる。
#   SC_TF_DETECTED  junit5 / kotest
#   SC_TF_FROM      kotest と判断した根拠のファイル（junit5 のときは空）
# catalog の [libraries] に io.kotest: があるか、ルート以外の build.gradle(.kts) に io.kotest が
# あれば kotest。どちらも無ければ junit5。preferences show も同じものを出す。
detect_test_framework() {
	SC_TF_DETECTED="junit5"
	SC_TF_FROM=""
	if catalog_entries libraries | awk -F'\t' 'index($2, "io.kotest:") == 1 { found = 1; exit } END { exit found ? 0 : 1 }'; then
		SC_TF_DETECTED="kotest"
		SC_TF_FROM="$CATALOG_FILE"
		return 0
	fi
	_dtf=$(find . -maxdepth 6 \
		\( -name build -o -name node_modules -o -name '.?*' -o -path "./${MODULE_DIR:-$MODULE_DIR_DEFAULT}" \) -prune -o \
		\( -name 'build.gradle.kts' -o -name 'build.gradle' \) -print 2>/dev/null | sort |
		while IFS= read -r _dtf_f; do
			[ "${_dtf_f%/*}" = "." ] && continue
			if sed 's://.*::' "$_dtf_f" | grep -q 'io\.kotest'; then
				printf '%s\n' "${_dtf_f#./}"
				break
			fi
		done | head -n 1)
	if [ -n "$_dtf" ]; then
		SC_TF_DETECTED="kotest"
		SC_TF_FROM="$_dtf"
	fi
}

# literal で書く Kotest の版。catalog の io.kotest の version.ref が指す値があればそれ、無ければ KOTEST_VERSION。
kotest_version_literal() {
	_kvl_ref=$(catalog_version_ref_for_group io.kotest)
	_kvl=""
	[ -n "$_kvl_ref" ] && _kvl=$(catalog_entries versions | awk -F'\t' -v a="$_kvl_ref" '$1 == a { print $2; exit }')
	printf '%s\n' "${_kvl:-$KOTEST_VERSION}"
}

# $1 のトップレベルの plugins { } と buildscript { } の中身だけを出す（行コメントは落とす）。
# 宣言がクラスパスに効くのはこの2か所だけ。subprojects { } の中の
# plugins.withId("org.jetbrains.kotlin.jvm") / pluginManager.withPlugin(...) /
# apply(plugin = "...") は「そのプラグインが当たっていたら」という参照で、載せはしない。
#
# 行コメントは `//` の前が `:` でないものだけ落とす。`uri("https://...")` の `//` から先を
# 消すと閉じ括弧 `}` も消え、括弧の深さがずれて後ろの plugins { } を見落とす。
# 文字列の中の `{` `}` も同じ理由で数えない。
root_plugin_blocks() {
	sed -E 's#(^|[^:])//.*#\1#' "$1" | awk '
		!inblk && depth == 0 && /^[[:space:]]*(plugins|buildscript)[[:space:]]*\{/ { inblk = 1 }
		{
			line = $0
			if (inblk) print line
			# 括弧の数え方から、文字列・文字リテラル・1行のブロックコメントの中身は外す。
			# `val s = "{"` のような行が plugins { } より前にあると、深さがずれて見落とす。
			gsub(/\/\*[^*]*\*\//, "", line)
			gsub(/"([^"\\]|\\.)*"/, "", line)
			gsub(/'"'"'([^'"'"'\\]|\\.)'"'"'/, "", line)
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

# settings で includeBuild したビルドのディレクトリ（build-logic など）を 1 行ずつ出す。
# 相対パスのまま。`..` で始まるもの（リポジトリの外）と `.` は出さない。buildSrc は出さない
# （要るところで足す）。$1 にプロジェクトルートを渡すとそこの settings を読み、無ければ
# require_gradle_root が入れた SETTINGS_FILE（cwd）を読む。
# lint の PYROLES にも同じ抽出（included_builds）がある。変えるときは揃えること。
included_build_dirs() {
	_ib_f="${SETTINGS_FILE:-}"
	if [ -n "${1:-}" ]; then
		_ib_f=""
		for _ib_c in "$1/settings.gradle.kts" "$1/settings.gradle"; do
			[ -f "$_ib_c" ] && _ib_f="$_ib_c" && break
		done
	fi
	[ -n "$_ib_f" ] && [ -f "$_ib_f" ] || return 0
	sed 's://.*::' "$_ib_f" |
		grep -oE "includeBuild[[:space:]]*\(?[[:space:]]*[\"'][^\"']+[\"']" |
		sed -E "s/^includeBuild[[:space:]]*\(?[[:space:]]*[\"']//; s/[\"']$//" |
		sed -e 's#\\#/#g' -e 's#^\./##' -e 's#/$##' |
		grep -vE '^(\.|\.\..*)?$' || :
}

# ルートの build ファイルが、版なしの id("...") でプラグインを apply しているか（apply false も含む）。
# 版なしの id は buildSrc か includeBuild のビルドから来る convention plugin の印。
# 核のプラグイン（base / java-library のように . を含まない id）と Kotlin / Android の id は数えない
# （Kotlin の id は kotlin_plugin_declared_in が見る）。
root_applies_convention_plugin() {
	[ -f "${1:-}" ] || return 1
	root_plugin_blocks "$1" | grep -E "id[[:space:](]+[\"'][^\"']*\.[^\"']*[\"']" |
		grep -vE 'version[[:space:]]+' |
		grep -qvE "[\"'](org\.jetbrains\.kotlin|com\.android)\."
}

# buildSrc か includeBuild したビルド（build-logic など）が Kotlin Gradle plugin を依存に持っているか。
# buildSrc の実行時クラスパスはすべての build スクリプトの親になるので、持っていればルートに
# 足さなくても載っている（kilua）。buildSrc 自身の plugins { kotlin("jvm") } は buildSrc の
# コンパイル用で、プロジェクトのクラスパスには載らないので数えない。compileOnly も同じ理由で数えない。
#
# includeBuild のビルドは buildSrc と違い、ルートがそこの plugin を apply して初めてクラスパスに
# 載る。build-logic に依存があってもルートが何も apply していなければ、版なしの kotlin("jvm") は
# plugin not found で落ちるので、root_applies_convention_plugin も見る。
kotlin_plugin_in_buildsrc() {
	_kb_pat="kotlin-gradle-plugin|kotlin\\(\"gradle-plugin\""
	_kb_accs=$(catalog_kotlin_accessors_joined libraries)
	[ -n "$_kb_accs" ] && _kb_pat="${_kb_pat}|${_kb_accs}"
	for _kb_f in buildSrc/build.gradle.kts buildSrc/build.gradle; do
		[ -f "$_kb_f" ] || continue
		sed 's://.*::' "$_kb_f" | grep -E '(implementation|api|runtimeOnly)[[:space:](]' |
			grep -qE "$_kb_pat" && return 0
	done
	root_applies_convention_plugin "${ROOT_BUILD_FILE:-}" || return 1
	for _kb_d in $(included_build_dirs); do
		for _kb_f in "$_kb_d/build.gradle.kts" "$_kb_d/build.gradle"; do
			[ -f "$_kb_f" ] || continue
			sed 's://.*::' "$_kb_f" | grep -E '(implementation|api|runtimeOnly)[[:space:](]' |
				grep -qE "$_kb_pat" && return 0
		done
	done
	return 1
}

# ルート以外のプロジェクトのどれかが、Kotlin Gradle plugin のプラグインを**版付きで**
# 要求しているか（kotlin("multiplatform") version "..." / alias(libs.plugins.<別名>)）。
# このときルートに版付きで足すと、そのプロジェクト側の要求が "already on the classpath
# with an unknown version" で落ちる（kdoctor）。同じ版でも、id が違えば落ちる。
#
# 別の settings を持つディレクトリ（includeBuild される build-logic など）は別のビルドなので
# ここでは見ない（そこに Kotlin Gradle plugin の依存があるかは kotlin_plugin_in_buildsrc が見る）。
kotlin_plugin_requested_by_subprojects() {
	_ks_pat="kotlin\\(\"(${KOTLIN_KGP_IDS})\"\\)[[:space:]]+version|id[[:space:](]+[\"']org\\.jetbrains\\.kotlin\\.(${KOTLIN_KGP_IDS})[\"']\\)?[[:space:]]+version"
	_ks_accs=$(catalog_kotlin_accessors_joined plugins)
	[ -n "$_ks_accs" ] && _ks_pat="${_ks_pat}|alias\\((${_ks_accs})"
	# '.?*' は隠しディレクトリ。'.*' だと起点の . 自体に当たって何も見なくなる。
	find . -maxdepth 6 \
		\( -name build -o -name node_modules -o -name buildSrc -o -name '.?*' -o -path "./$MODULE_DIR" \) -prune -o \
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
		die "$(file_uri "$ROOT_BUILD_FILE") の書き換えに失敗しました。元に戻しました。" \
			"Failed to edit $(file_uri "$ROOT_BUILD_FILE"). It was restored."
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
	die "$(file_uri "$ROOT_BUILD_FILE") の書き換えに失敗しました。元に戻しました。" \
		"Failed to edit $(file_uri "$ROOT_BUILD_FILE"). It was restored."
}

add_settings_include() {
	# 常に Gradle パスの形（":static-analysis:katachi"。1 階層でも ":architecture-test"）。
	# 親ディレクトリに build ファイルが無くても Gradle が中間プロジェクトを作るので、親には何も書かない。
	if [ "$SETTINGS_DSL" = "groovy" ]; then
		_inc_line="include '$MODULE_GRADLE_PATH'"
	else
		_inc_line="include(\"$MODULE_GRADLE_PATH\")"
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
				if (o > 0 && o == c) {
					oneline = 1
					# `pluginManagement { repositories { mavenCentral() } }` のように1行に
					# 収まっていれば、その行が repositories { } の範囲でもある。
					if ($0 ~ /[{][[:space:]]*repositories[[:space:]]*[{]/) { repo = NR; repo_end = NR }
				}
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
		die "$(file_uri "$SETTINGS_FILE") の書き換えに失敗しました。元に戻しました。" \
			"Failed to edit $(file_uri "$SETTINGS_FILE"). It was restored."
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

# JSON として妥当かを見る。python3 は呼び出し側の require_commands で確かめてある。
# 検査せずに書き込むと、壊れた JSON に気づけないまま HTML が表示されなくなる。
validate_json() {
	python3 -m json.tool <"$1" >/dev/null
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
}

# data get <対象> <キー>。$1 = JSON のファイル, $2 = ドット区切りのキー。
# 文字列はそのまま（引用符なし）、ほかは JSON で出す。無いキー（途中で辞書でなくなるのも含む）は 1。
# 配列の添字（questions.0）も辿れる。
DATA_GET_PY='
import json, sys
src, key = sys.argv[1], sys.argv[2]
node = json.load(open(src, encoding="utf-8"))
for part in key.split("."):
    if isinstance(node, dict) and part in node:
        node = node[part]
    elif isinstance(node, list) and part.isdigit() and int(part) < len(node):
        node = node[int(part)]
    else:
        sys.stderr.write(t("キーがありません: %s\n", "No such key: %s\n") % key)
        sys.exit(1)
if isinstance(node, str):
    print(node)
else:
    print(json.dumps(node, ensure_ascii=False, indent=2))
'

cmd_data() {
	_action="${1:-}"
	shift 2>/dev/null || true
	_html="${1:-}"
	shift 2>/dev/null || true

	case "$_action" in
	get) require_commands "$REQ_BASE" ;;
	set | merge) require_commands "$REQ_JSON" ;;
	*) die "data のサブコマンドは get / set / merge です（指定されたのは '${_action}'）" \
		"The data subcommands are get / set / merge (got '${_action}')" ;;
	esac
	[ -n "$_html" ] || die "data ${_action}: 対象を指定してください（check-list / report / ファイルパス）。" \
		"data ${_action}: specify the target (check-list / report / a file path)."
	_html=$(resolve_data_target "$_html")
	_html_uri=$(file_uri "$_html")
	[ -f "$_html" ] || die "ファイルがありません: ${_html_uri}" "File not found: ${_html_uri}"

	if [ "$_action" != "get" ]; then
		_json="${1:-}"
		shift 2>/dev/null || true
		[ -n "$_json" ] || die "data ${_action}: JSON ファイルを指定してください。" \
			"data ${_action}: specify the JSON file."
		[ -f "$_json" ] || die "ファイルがありません: $(file_uri "$_json")" "File not found: $(file_uri "$_json")"
	fi

	_id=""
	_key=""
	while [ $# -gt 0 ]; do
		case "$1" in
		--id)
			need_value "$1" "${2:-}"
			_id="$2"
			shift 2
			;;
		-*) die "data: 知らないオプションです: ${1}" "data: unknown option: ${1}" ;;
		*)
			# get の 2 つめの位置引数はキー（ドット区切り）。set / merge には位置引数は無い。
			[ "$_action" = "get" ] && [ -z "$_key" ] ||
				die "data ${_action}: 余分な引数です: ${1}" "data ${_action}: unexpected argument: ${1}"
			_key="$1"
			shift
			;;
		esac
	done
	[ -n "$_id" ] || _id=$(guess_block_id "$_html")
	_no_block="${_html_uri} に id=\"${_id}\" の JSON ブロックが見つかりません。"
	_no_block_en="No JSON block with id=\"${_id}\" in ${_html_uri}."

	if [ "$_action" = "get" ]; then
		if [ -z "$_key" ]; then
			extract_json "$_html" "$_id" || die "$_no_block" "$_no_block_en"
			return 0
		fi
		require_commands "$REQ_JSON"
		_dg="$_html.get.$$"
		extract_json "$_html" "$_id" >"$_dg" || {
			rm -f "$_dg"
			die "$_no_block" "$_no_block_en"
		}
		# キーで辿る。無ければ 1（止まるが die の形ではない。呼ぶ側が「無い」を分岐に使えるように）。
		if printf '%s\n%s' "$PY_LANG" "$DATA_GET_PY" | python3 - "$_dg" "$_key"; then
			rm -f "$_dg"
			return 0
		fi
		rm -f "$_dg"
		return 1
	fi

	validate_json "$_json" ||
		die "$(file_uri "$_json") が JSON として読めません。直してからやり直してください。" \
			"$(file_uri "$_json") is not valid JSON. Fix it and try again."

	_merged=""
	if [ "$_action" = "merge" ]; then
		_merged="$_json.merged.$$"
		if ! extract_json "$_html" "$_id" >"$_merged.base" 2>/dev/null; then
			rm -f "$_merged.base"
			die "${_no_block}開始タグと閉じタグは、それぞれ行頭に単独で置かれている必要があります。" \
				"${_no_block_en} The opening and closing tags must each be alone at the start of a line."
		fi
		if ! merge_json "$_merged.base" "$_json" "$_merged"; then
			rm -f "$_merged" "$_merged.base"
			die "JSON をマージできませんでした。" "Could not merge the JSON."
		fi
		rm -f "$_merged.base"
		_json="$_merged"
	fi

	extract_json "$_html" "$_id" >/dev/null || die "$_no_block" "$_no_block_en"

	# JSON の値に `</script>` が入っていると、ブラウザの HTML パーサがそこで
	# ブロックを打ち切る。JSON としては等価な `<\/script>` に置き換えて無害化する。
	_safe="$_json.safe.$$"
	sed 's|</script|<\\/script|g' "$_json" >"$_safe"

	# 書く前にロックを取る。外側（cmd_add / edit_checklist / cmd_verify）が読む前に取っていれば
	# そのまま（lock_acquire は保持中なら何もしない）。自分で取ったときだけ最後に放す。
	# 失敗（die）で抜けるときはトップレベルか data_quietly のサブシェルの trap が放す。
	_dl_mine="no"
	if [ -z "${KATACHI_LOCK_HELD:-}" ]; then
		lock_acquire "$_html"
		_dl_mine="yes"
	fi

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
		die "${_html_uri} の書き換えに失敗しました。元に戻しました。" \
			"Failed to edit ${_html_uri}. It was restored."
	fi

	_check="$_html.check.$$"
	if extract_json "$_html" "$_id" >"$_check" 2>/dev/null && validate_json "$_check"; then
		rm -f "$_check" ${_merged:+"$_merged"}
		[ "$_dl_mine" = "yes" ] && lock_release
		say "${_html_uri} の id=\"${_id}\" を更新しました（元は ${_html_uri}.bak）。" \
			"Updated id=\"${_id}\" in ${_html_uri} (the original is ${_html_uri}.bak)."
	else
		rm -f "$_check" ${_merged:+"$_merged"}
		mv "$_html.bak" "$_html"
		die "差し込んだ結果が読み直せなかったため、元に戻しました。JSON の値に </script> のような、HTML を打ち切る文字列が含まれていないか確認してください。" \
			"The result could not be read back, so it was restored. Check that no JSON value contains a string that cuts the HTML short, such as </script>."
	fi
}

# ---------------------------------------------------------------- docs

# docs --topic の名前。llms-full.txt のページ見出し（`# ...`）に対応する。並びはガイドのサイドバーの順。
# 名前はドキュメントの URL の末尾（guides/layout → layout）に合わせる。手順書の 3-1 / 6-C / 6-D が
# layout / role / basic-api / kt-file-content / document-generation / template を指す。
# konsist { } の説明は role（「中身に制約をかける」）と basic-api にあり、専用のページは無い。
DOCS_TOPICS="motivation first-architecture faq comparison basic-api role layout kt-file-content document-generation template baseline processor recipes android-three-layer gradle ai-agent ktlint detekt git github roadmap"

# $1（topic）に対応するページ見出しの文言を出す。知らない名前なら何も出さず 1 を返す。
# llms-full.txt の見出しは `# <文言>` か、AI が書いた下書きの印つきの `# [AI-generated] <文言>`。
# 見出しの文言を変えたらここも直す（docs --topic <名前> が「見出しが無い」で止まるので気づける）。
docs_topic_title() {
	case "$1" in
	motivation) printf '%s\n' "Motivation and where katachi fits" ;;
	first-architecture) printf '%s\n' "Your first architecture definition" ;;
	faq) printf '%s\n' "FAQ" ;;
	comparison) printf '%s\n' "Comparison with other tools" ;;
	basic-api) printf '%s\n' "Basic API" ;;
	role) printf '%s\n' "Role" ;;
	layout) printf '%s\n' "Enforce directory structure with the Layout system" ;;
	kt-file-content) printf '%s\n' "ktFileContent" ;;
	document-generation) printf '%s\n' "Document generation" ;;
	template) printf '%s\n' "Generating code from a template" ;;
	baseline) printf '%s\n' "Temporarily disable existing violations with baseline" ;;
	processor) printf '%s\n' "ArchitectureProcessor and customizing it" ;;
	recipes) printf '%s\n' "Recipes" ;;
	android-three-layer) printf '%s\n' "Three-layer architecture following the Android architecture guide" ;;
	gradle) printf '%s\n' "Gradle" ;;
	ai-agent) printf '%s\n' "AI Agent" ;;
	ktlint) printf '%s\n' "ktlint" ;;
	detekt) printf '%s\n' "detekt" ;;
	git) printf '%s\n' "Git" ;;
	github) printf '%s\n' "GitHub" ;;
	roadmap) printf '%s\n' "Roadmap" ;;
	*) return 1 ;;
	esac
}

# docs --topics の一覧（<名前>\t<見出し>）。
print_docs_topics() {
	for _pt in $DOCS_TOPICS; do
		printf '%s\t%s\n' "$_pt" "$(docs_topic_title "$_pt")"
	done
}

# llms-full.txt（$1）から見出し $2 のページを切り出して $3 に書く。行数を標準出力に出す。
# 見出しは行頭の `# ` で、コードフェンス（行頭の ```）の内側は数えない。最初に見つけた見出しから、
# 次のページ見出しの直前まで。llms-full.txt には古い版の写し（/0.2/ 以下）が後ろに続き同じ見出しが
# もう一度出るので、次の見出しで止めれば写しは入らない。見つからなければ 0 を出す。
extract_docs_topic() {
	awk -v title="$2" -v dest="$3" '
		/^```/ { fence = !fence }
		!fence && /^# / {
			if (inside) exit
			if ($0 == "# " title || $0 == "# [AI-generated] " title) inside = 1
		}
		inside { print >dest; n++ }
		END { print n + 0 }
	' "$1"
}

cmd_docs() {
	_small="no"
	_refresh="no"
	_api="no"
	_api_module=""
	_topic=""
	while [ $# -gt 0 ]; do
		case "$1" in
		--small)
			_small="yes"
			shift
			;;
		--topics)
			print_docs_topics
			return 0
			;;
		--topic)
			need_value "$1" "${2:-}"
			_topic="$2"
			shift 2
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
		*) die "docs: 知らないオプションです: ${1}" "docs: unknown option: ${1}" ;;
		esac
	done

	require_commands "$REQ_DOCS"
	require_workdir

	if [ -n "$_topic" ]; then
		_topic_title=$(docs_topic_title "$_topic") ||
			die "docs --topic: 知らない名前です: ${_topic} 。使える名前:
$(print_docs_topics)" \
				"docs --topic: unknown topic: ${_topic} . Available topics:
$(print_docs_topics)"
		_name="llms-full.txt"
		_url="$KATACHI_DOCS/$_name"
		_dest="$WORKDIR/tmp/cache/$_name"
		fetch_once "$_url" "$_dest" "$_refresh"
		_topic_dest="$WORKDIR/tmp/cache/topics/$_topic.md"
		mkdir -p "${_topic_dest%/*}"
		_topic_lines=$(extract_docs_topic "$_dest" "$_topic_title" "$_topic_dest")
		if [ "${_topic_lines:-0}" -eq 0 ]; then
			rm -f "$_topic_dest"
			die "$(file_uri "$_dest") に見出し「${_topic_title}」がありません。ガイドの見出しが変わったか、取得物が古いかです（--refresh で取り直す）。" \
				"No heading \"${_topic_title}\" in $(file_uri "$_dest") . The guide's heading may have changed, or the copy is old (add --refresh)."
		fi
		# 行数と見出しは標準エラーに出す。標準出力はパスだけ（機械が読む）。
		printf '%s\n' "$(pick "${_topic_lines} 行（llms-full.txt の「${_topic_title}」）" \
			"${_topic_lines} lines (\"${_topic_title}\" in llms-full.txt)")" >&2
		printf '%s\n' "$_topic_dest"
		return 0
	fi

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
	_dest="$WORKDIR/tmp/cache/$_name"

	if [ -s "$_dest" ] && [ "$_refresh" = "no" ]; then
		_cached="yes"
	else
		_cached="no"
	fi
	fetch_once "$_url" "$_dest" "$_refresh"
	# 取得元は標準エラーに出す。標準出力はパスだけ（機械が読む）。
	if [ "$_cached" = "yes" ]; then
		printf '%s\n' "$(pick "取得元: ${_url}（取得済みのものを使いました。取り直すなら --refresh）" \
			"Source: ${_url} (used the copy already fetched; add --refresh to fetch it again)")" >&2
	else
		printf '%s\n' "$(pick "取得元: ${_url}" "Source: ${_url}")" >&2
	fi
	printf '%s\n' "$_dest"
}

# ---------------------------------------------------------------- guide（手順書を節ごとに）

# 手順書（index.md）を節に切る awk。python は使わない（guide は python3 を要らないままにする）。
#   -v mode=toc           目次。1 行 = <ID>\t<見出し>\t<行数>
#   -v mode=body -v want=<ID>   その節の本文。見つからなければ exit 1
#   -v mode=next -v want=<ID>   目次の順で次の ID（最後なら何も出さない）
# 節になる見出しは `## N.`、`## 6-A.`〜`## 6-D.`、`### N-M.` だけ。番号の無い見出し（`### 始める前に`、
# `#### ...`）は節にならず、直前の番号つき節に含まれる。コードフェンス（行頭の ```）の内側は見出しに数えない。
# 章（H2）に番号つきの節（H3）があるときは、章の本文は前書き（最初の H3 の直前まで）＋「含む節:」の 1 行。
# この変数の中に \' を書かないこと（sh の単引用符）。
GUIDE_AWK='
/^```/ { fence = !fence }
{ L[NR] = $0 }
!fence && (/^## [0-9]+\. / || /^## 6-[A-D]\. / || /^### [0-9]+-[0-9]+\. /) {
	n++
	split($0, a, " ")
	id[n] = a[2]
	sub(/\.$/, "", id[n])
	lvl[n] = ($0 ~ /^### /) ? 3 : 2
	start[n] = NR
	t = $0
	sub(/^#+ [^ ]+ /, "", t)
	title[n] = t
}
END {
	for (i = 1; i <= n; i++) {
		# 本文の終わり: H3 は次の番号つき見出しの直前、H2 は次の H2 の直前。ただし H2 の直後に H3 が
		# 続く（子の節がある）なら前書きだけ（最初の H3 の直前まで）にして、含む節を並べる。
		e = NR
		kids[i] = ""
		if (i < n) {
			if (lvl[i] == 3 || lvl[i + 1] == 3) e = start[i + 1] - 1
			if (lvl[i] == 2 && lvl[i + 1] == 3) {
				for (j = i + 1; j <= n && lvl[j] == 3; j++) kids[i] = kids[i] (kids[i] == "" ? "" : ", ") id[j]
			}
			if (lvl[i] == 2 && lvl[i + 1] == 2) e = start[i + 1] - 1
		}
		while (e > start[i] && L[e] ~ /^[[:space:]]*$/) e--
		stop[i] = e
	}
	if (mode == "toc") {
		for (i = 1; i <= n; i++) printf "%s\t%s\t%d\n", id[i], title[i], stop[i] - start[i] + 1
		exit 0
	}
	for (i = 1; i <= n; i++) if (id[i] == want) break
	if (i > n) exit 1
	if (mode == "next") {
		if (i < n) print id[i + 1]
		exit 0
	}
	for (k = start[i]; k <= stop[i]; k++) print L[k]
	if (kids[i] != "") {
		print ""
		print inc ": " kids[i]
	}
}
'

# チェックリストの 6-1〜6-4 を手順書の 6-A〜6-D に読み替える。
guide_section_id() {
	case "$1" in
	6-1) printf '6-A\n' ;;
	6-2) printf '6-B\n' ;;
	6-3) printf '6-C\n' ;;
	6-4) printf '6-D\n' ;;
	*) printf '%s\n' "$1" ;;
	esac
}

# 目次を出す（末尾に 6-A〜6-D の読み替えの 1 行）。$1 = index.md
print_guide_toc() {
	awk -v mode=toc "$GUIDE_AWK" "$1"
	say "6-A〜6-D はチェックリストでは 6-1〜6-4（guide 6-1 でも取り出せる）" \
		"6-A to 6-D are 6-1 to 6-4 in the check list (guide 6-1 works too)"
}

cmd_guide() {
	_gd_id=""
	_gd_refresh="no"
	while [ $# -gt 0 ]; do
		case "$1" in
		--refresh)
			_gd_refresh="yes"
			shift
			;;
		-h | --help)
			usage
			return 0
			;;
		-*) die "guide: 知らないオプションです: ${1}" "guide: unknown option: ${1}" ;;
		*)
			[ -z "$_gd_id" ] || die "guide: 節 ID は 1 つだけです（${_gd_id} と ${1}）" \
				"guide: give only one section id (${_gd_id} and ${1})"
			_gd_id="$1"
			shift
			;;
		esac
	done

	require_workdir
	_gd_cli="$WORKDIR/tmp/katachi-install.sh"
	_gd_file="$WORKDIR/tmp/cache/index.md"
	if [ "$_gd_refresh" = "yes" ]; then
		require_commands "$REQ_GUIDE"
		fetch_once "$KATACHI_DOCS/install/$KATACHI_LANG/index.md" "$_gd_file" yes
	else
		require_commands "$REQ_BASE"
	fi
	[ -s "$_gd_file" ] || die "手順書が取得されていません。'sh ${_gd_cli} guide --refresh' で取得してください。" \
		"The guide has not been fetched. Run 'sh ${_gd_cli} guide --refresh'."

	if [ -z "$_gd_id" ]; then
		print_guide_toc "$_gd_file"
		return 0
	fi
	_gd_id=$(guide_section_id "$_gd_id")
	if ! awk -v mode=body -v want="$_gd_id" -v inc="$(pick "含む節" "Sections")" "$GUIDE_AWK" "$_gd_file"; then
		warn "知らない節 ID です: ${_gd_id} 。使える ID は目次のとおりです。" \
			"Unknown section id: ${_gd_id} . The ids you can use are in the table of contents."
		print_guide_toc "$_gd_file"
		exit 1
	fi
	_gd_next=$(awk -v mode=next -v want="$_gd_id" "$GUIDE_AWK" "$_gd_file")
	if [ -n "$_gd_next" ]; then
		say ""
		say "次: sh ${_gd_cli} guide ${_gd_next}" "Next: sh ${_gd_cli} guide ${_gd_next}"
	fi
}

# ---------------------------------------------------------------- Role の点検

# 定義のソースを読み、種類の違うファイルを1つの Role に抱えていそうなものを列挙する。
# 「1 種類のファイル = 1 Role、種類の違うものを束ねるのは group」を機械的に拾える範囲で
# 拾うためのもの。Kotlin の構文解析はせず、素朴なテキスト走査なので誤検知があり得る。
# だから**失敗にはせず、常に 0 を返す。** check 3-2 / 3-3 のたびに走り、lint で単独でも呼べる。
#
# 拾う規則（どれも実地テストの定義で取り違えを拾い、正しい定義を誤検知しないことを確かめた）:
#   - README・LICENSE・settings.gradle(.kts)・gradlew・.gitignore のような、名前で種類が
#     決まるファイルが、2種類以上同じ Role にある
#   - .claude / .github / .run のようなツールごとの設定ディレクトリを、1つの Role で
#     anyFile() / ignore() している
#   - 同じ Role に本体（main）とテスト（test）の置き場所がある
# あわせて、Gradle のファイルを手書きの Role で宣言していれば gradle() を勧める。
lint_roles() {
	command -v python3 >/dev/null 2>&1 || {
		note "Role の点検は python3 が無いため飛ばしました。" \
			"Skipped the Role check because python3 is not available."
		return 0
	}
	# 定義のディレクトリが無ければ止まる（scaffold 前、--module-dir の食い違い）。点検の結果では
	# 失敗にしないが、読む場所が無いのは別（黙って 0 を返すと「問題なし」と取り違える）。
	resolve_module_dir
	require_definition_dir
	# プロジェクトルートは init がチェックリストに記録したものを使う。
	_lr_json=""
	_lr_wd=$(resolve_workdir) || _lr_wd=""
	if [ -n "$_lr_wd" ] && [ -f "$_lr_wd/check-list.html" ]; then
		_lr_json="$_lr_wd/tmp/cache/.lint-cl.$$"
		extract_json "$_lr_wd/check-list.html" checklist >"$_lr_json" 2>/dev/null || :
	fi
	{
		printf '%s\n' "$PY_LANG"
		cat <<'PYROLES'
import os, re, sys

# Role（"Name".role { }）ごとに、layout { } の中で宣言しているファイルを集め、
# 種類の違うファイルを抱えていそうなものを列挙する。Kotlin の構文解析はしない。
# 文字列・コメントを飛ばして括弧を数えるだけの素朴な走査なので、誤検知はあり得る。

# 定義のディレクトリ。チェックリストに init が記録したプロジェクトルートから決める。
# 読めなければカレントディレクトリ（init / scaffold と同じく Gradle のルートで呼ばれる前提）。
project_root = os.getcwd()
# 書き方の方針（preferences。design 4）。nestedGroups / oneRolePerFile が no なら、その点検はしない。
prefs = {}
try:
    import json
    checklist = json.load(open(sys.argv[1], encoding="utf-8"))
    meta = checklist.get("meta") or {}
    if meta.get("projectRoot"):
        project_root = meta["projectRoot"]
    prefs = checklist.get("preferences") or {}
except Exception:
    pass
module_dir = sys.argv[2]
root = os.path.join(project_root, module_dir, "src", "test", "kotlin")


def included_builds():
    # buildSrc と、settings で includeBuild したビルドのディレクトリ（プロジェクトルートからの相対）。
    # これらのビルドは gradle() の対象外なので、手書きの Role で宣言していても gradle() を勧めない。
    found = {"buildSrc"}
    for name in ("settings.gradle.kts", "settings.gradle"):
        path = os.path.join(project_root, name)
        if not os.path.isfile(path):
            continue
        src = open(path, encoding="utf-8", errors="replace").read()
        for m in re.finditer(r"""\bincludeBuild\s*\(?\s*["']([^"']+)["']""", src):
            d = os.path.normpath(m.group(1)).replace("\\", "/")
            if d not in (".", "") and not d.startswith(".."):
                found.add(d)
    return found


INCLUDED = included_builds()


def in_included_build(where):
    # where は "/build-logic/build.gradle.kts" や ":build-logic/..." の形。先頭の区切りを落として比べる
    w = where.replace("\\", "/").replace(":", "/").lstrip("/")
    return any(w == d or w.startswith(d + "/") for d in INCLUDED)


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
    (r"^(claude|agents|gemini|copilot-instructions)\.md$", t("AI エージェント向けの指示", "AI agent instructions"), False),
    (r"^\.gitignore$", ".gitignore", False),
    (r"^\.gitattributes$", ".gitattributes", False),
    (r"^\.editorconfig$", ".editorconfig", False),
    (r"^androidmanifest\.xml$", "AndroidManifest.xml", False),
    (r"^proguard-rules\.pro$", "proguard-rules.pro", False),
    (r"^renovate\.json5?$", "renovate", False),
    (r"^gemfile(\.lock)?$", "Gemfile", False),
    (r"^dangerfile", "Dangerfile", False),
    (r"^(package(-lock)?\.json|yarn\.lock|pnpm-lock\.yaml)$", "npm", False),
    (r"^(detekt|lint)[^/]*\.(yml|yaml|xml)$", t("静的解析の設定", "static analysis config"), False),
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
                # 閉じの ` が無ければ末尾まで。find の -1 に 1 を足すと j が 0 に戻り、無限に回る
                j = src.find("`", i + 1)
                j = n if j < 0 else j + 1
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
    def __init__(self, name, path, line, group):
        self.name, self.path, self.line = name, path, line
        self.group = group          # 直近の group（無ければ None）
        self.files = []             # (名前, 行, 宣言の場所)
        self.catchall = []          # (anyFile / ignore, 対象, 行)
        self.places = []            # 宣言の場所（本体とテストの同居を見る）
        self.has_constraint = False # 本体（layout の外）に fileConstraint / konsist / ktFileContent / konture がある
        self.documented = False     # documented = ... を自分で書いている

    def documented_somewhere(self):
        # 自分か、外側の group のどれかが documented を書いていれば True（group の false は中の Role にも効く）
        g = self.group
        while g is not None:
            if g.documented:
                return True
            g = g.parent
        return self.documented


class Group:
    def __init__(self, name, path, line, parent):
        self.name, self.path, self.line, self.parent = name, path, line, parent
        self.direct_roles = 0       # 直下の Role の数
        self.has_group = False      # 直下に group がある
        self.documented = False


# hook（gradle { buildScript { } } など）は `識別子 {` なので block 扱いになり、Role にならない。
# この性質を壊さないこと（gradle-role-constraints の設計 5 の条件 1）。
CONSTRAINT_CALLS = ("fileConstraint", "konsist", "ktFileContent", "konture")


def scan(path, roles, groups, decls):
    src = open(path, encoding="utf-8", errors="replace").read()
    toks = tokenize(src)
    stack = []  # (種類, 名前 / Group)
    role = None
    for k, (t, v, line) in enumerate(toks):
        prev = toks[k - 1] if k > 0 else (None, None, 0)
        prev2 = toks[k - 2] if k > 1 else (None, None, 0)
        prev3 = toks[k - 3] if k > 2 else (None, None, 0)
        nxt = toks[k + 1] if k + 1 < len(toks) else (None, None, 0)
        kinds = [s[0] for s in stack]
        in_layout = "layout" in kinds
        group = next((s[1] for s in reversed(stack) if s[0] == "group"), None)
        if t == "op" and v == "{":
            # Role の宣言は "Name".role { }。古い書き方の "Name" { }（今はコンパイルエラー）も、layout { } の外にあれば Role として読む
            role_call = prev == ("id", "role", prev[2]) and prev2[1] == "." and prev3[0] == "str" and prev3[1] is not None
            if role_call or (prev[0] == "str" and prev[1] is not None):
                name_tok = prev3 if role_call else prev
                if in_layout:
                    stack.append(("dir", name_tok[1]))
                elif "role" in kinds or "other" in kinds or re.search(r"\s", name_tok[1]):
                    stack.append(("other", None))
                else:
                    role = Role(name_tok[1], path, name_tok[2], group)
                    roles.append(role)
                    decls.setdefault(path, []).append(("Role", role.name, role.line))
                    if group is not None:
                        group.direct_roles += 1
                    stack.append(("role", name_tok[1]))
            elif prev == ("id", "group", prev[2]) and prev2[1] == ".":
                g = Group(prev3[1] if prev3[0] == "str" else "?", path, prev3[2], group)
                groups.append(g)
                decls.setdefault(path, []).append(("group", g.name, g.line))
                if group is not None:
                    group.has_group = True
                stack.append(("group", g))
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
        if not in_layout and t == "id":
            # layout の外: 制約の呼び出しと documented = ... を Role / group に記録する
            if role is not None and v in CONSTRAINT_CALLS and nxt[1] in ("(", "{"):
                role.has_constraint = True
            elif v == "documented" and nxt[1] == "=":
                if role is not None:
                    role.documented = True
                elif group is not None:
                    group.documented = True
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
        if t == "id" and v in ("file", "ktFile", "ktsFile") and prev[1] == "." and nxt[1] == "(":
            if prev2[0] == "str" and prev2[1] is not None:
                name = prev2[1] + {"file": "", "ktFile": ".kt", "ktsFile": ".kts"}[v]
                role.files.append((name, line, where))
                role.places.append(where)
        elif t == "id" and v in ("anyFile", "ignore") and nxt[1] == "(":
            target = dirs
            if v == "ignore" and prev[1] == "." and prev2[0] == "str" and prev2[1]:
                target = (dirs + "/" if dirs else "") + prev2[1]
            role.catchall.append((v, target or ".", line))
            role.places.append(where + "/" + (target or ""))


roles = []
groups = []
decls = {}  # ファイル → [(Role / group, 名前, 行)]
# 定義のディレクトリの有無は sh 側（require_definition_dir）が先に確かめて、無ければ止めている。
for d, _, fs in os.walk(root):
    for f in sorted(fs):
        if f.endswith(".kt"):
            scan(os.path.join(d, f), roles, groups, decls)

warnings = []
gradle_hand = []
gradle_constrained = []
for r in roles:
    reasons = []
    kinds = {}
    for name, line, where in r.files:
        label, gradle = kind_of(name)
        if label:
            kinds.setdefault(label, []).append(name.rsplit("/", 1)[-1])
            # buildSrc と includeBuild したビルドの下は gradle() の対象外なので勧めない
            if gradle and not in_included_build(where):
                if not r.has_constraint:
                    gradle_hand.append((r, name))
                else:
                    # 制約の行き先（gradle { } の hook）まで言う
                    gradle_constrained.append((r, name, label))
    if len(kinds) >= 2:
        reasons.append(t("種類の違うファイルが同居しています: ", "Files of different kinds share this Role: ") + " / ".join(
            t("%s（%s）", "%s (%s)") % (label, ", ".join(sorted(set(names)))) for label, names in kinds.items()))
    dots = sorted(set(seg for _, t, _ in r.catchall for seg in t.split("/")[-1:] if seg.startswith(".") and len(seg) > 1))
    if len(dots) >= 2:
        reasons.append(t("ツールごとの設定ディレクトリを anyFile() / ignore() でまとめています: ",
                         "Groups per-tool config directories with anyFile() / ignore(): ") + ", ".join(dots))
    TEST = re.compile(r"^(test|testSourceSet|androidTest|androidUnitTest|androidInstrumentedTest|[a-z][A-Za-z]*Test|testFixtures)$")
    MAIN = re.compile(r"^(main|mainSourceSet|[a-z][A-Za-z]*Main)$")
    segs = [set(p.replace("\\", "/").split("/")) for p in r.places]
    has_test = any(any(TEST.match(x) for x in s_) for s_ in segs)
    has_main = any(any(MAIN.match(x) for x in s_) and not any(TEST.match(x) for x in s_) for s_ in segs)
    if has_test and has_main:
        reasons.append(t("本体（main）とテスト（test）のファイルを同じ Role で宣言しています",
                         "Declares main and test files in the same Role"))
    if reasons:
        warnings.append((r, reasons))

# 3 つの点検（design 7）。どれも失敗にはしない。
nested_groups = prefs.get("nestedGroups") != "no"
one_role_per_file = prefs.get("oneRolePerFile") != "no"
# 平たい group: 直下に Role が 8 個以上あり、group を含まない
flat_groups = [g for g in groups if g.direct_roles >= 8 and not g.has_group] if nested_groups else []
# 分けていないファイル: 1 ファイルに Role か group が 2 つ以上
unsplit_files = [(p, ds) for p, ds in sorted(decls.items()) if len(ds) >= 2] if one_role_per_file else []
# documented = false の候補: documented が無く、layout のファイルが全部 KINDS の非 Gradle の種類か、ignore() だけ
documented_candidates = []
for r in roles:
    if r.documented_somewhere():
        continue
    if r.files:
        labels = [kind_of(name) for name, _, _ in r.files]
        if all(label and not gradle for label, gradle in labels):
            documented_candidates.append((r, ", ".join(sorted(set(label for label, _ in labels)))))
    elif r.catchall and all(v == "ignore" for v, _, _ in r.catchall):
        documented_candidates.append((r, "ignore()"))

HOOK_OF = {
    "settings.gradle(.kts)": "settingsScript { }",
    "build.gradle(.kts)": "buildScript { }",
    "gradle.properties": "gradleProperties { }",
    "gradlew": "launcherScript { }",
    "gradle-wrapper.jar": "wrapperJar { }",
    "gradle-wrapper.properties": "wrapperProperties { }",
    "version catalog": "versionCatalog { }",
}

if not warnings and not gradle_hand and not gradle_constrained and not flat_groups and not unsplit_files and not documented_candidates:
    print(t("Role の点検: 種類の違うファイルを抱えていそうな Role は見つかりませんでした（%d 件を走査）。",
            "Role check: no role seems to hold files of different kinds (%d scanned).") % len(roles))
    sys.exit(0)
print()
print(t("== Role の点検（失敗にはしません） ================================",
        "== Role check (never fails) ======================================"))
print(t("定義: %s", "Definition: %s") % file_uri(root))
if warnings:
    print()
    print(t("種類の違うファイルを1つの Role に抱えていそうなものが %d 件あります（%d 件を走査）。",
            "Roles that seem to hold files of different kinds: %d (%d scanned).") % (len(warnings), len(roles)))
    for r, reasons in warnings:
        print()
        print(t('  %s:%d Role "%s"', '  %s:%d Role "%s"') % (file_uri(r.path), r.line, r.name))
        for x in reasons:
            print("    - " + x)
    print()
    print(t("""Role（"Name".role { }）は1種類のファイルを表し、種類の違うものを束ねるのは group です。
手順書 3-2 の「1 つの Role = 1 種類のファイル」の3つの問いで見直し、分けるべきものは
種類ごとの Role に分けて group で束ねてください。テキストの素朴な走査なので誤検知はあり得ます。
見直して1種類だと判断したものは、そのままでかまいません。""",
            """A Role ("Name".role { }) stands for one kind of file; group is what bundles different kinds.
Review them with the three questions of "one Role = one kind of file" in step 3-2 of the guide,
and split what should be split into one Role per kind, bundled with group. This is a plain text
scan, so false positives are possible. If you review a Role and decide it is one kind, leave it."""))
if gradle_hand:
    names = sorted(set(r.name for r, _ in gradle_hand))
    print()
    print(t("Gradle のファイルを手書きの Role で宣言しています（Role: %s）。",
            "Gradle files are declared in hand-written Roles (Roles: %s).") % ", ".join(names))
    print(t("""gradle() が wrapper・settings・ビルドスクリプト・gradle.properties・
version catalog を種類ごとの Role に分けて宣言します（import me.tbsten.katachi.dsl.gradle.*）。
これらの Role を消して gradle() の1行に置き換えてください。buildSrc や includeBuild した
ビルドは gradle() の対象外なので、それだけは自分の Role に残します（この点検もその下は数えません）。""",
            """gradle() declares the wrapper, settings, build scripts, gradle.properties
and the version catalog as one Role per kind (import me.tbsten.katachi.dsl.gradle.*).
Remove these Roles and replace them with the single line gradle(). buildSrc and included
builds are not covered by gradle(), so keep only those in your own Roles (this check skips them too)."""))
if gradle_constrained:
    print()
    print(t("Gradle のファイルを手書きの Role で宣言し、その Role に制約を付けています:",
            "Gradle files are declared in hand-written Roles that also carry constraints:"))
    for r, name, label in sorted(set(gradle_constrained), key=lambda x: (x[0].path, x[0].line)):
        print(t('  %s:%d Role "%s"（%s → gradle { %s }）', '  %s:%d Role "%s" (%s -> gradle { %s })')
              % (file_uri(r.path), r.line, r.name, name.rsplit("/", 1)[-1], HOOK_OF.get(label, "buildScript { }")))
    print(t("""配置は gradle() の 1 行に置き換え、制約は gradle { buildScript { } }（settings なら settingsScript { }、
catalog なら versionCatalog { }）に移してください。buildScript { } の制約は、ルートを含む全モジュールの
build.gradle(.kts) を subject.files に受け取ります。1 つのモジュールだけを見るなら、制約の中で
subject.files をパスで絞ってください（例: subject.files.filter { it == "app/build.gradle.kts" }）。
gradle { } の中に katachi の Role と同じ名前の Role（"BuildScript".role { } など）を書くと重複の例外に、
gradle { } の外の Role でビルドファイルを宣言すると BuildScript と同じファイルを取り合って
[AmbiguousLayout] になります。制約は hook に書いてください。""",
            """Replace the placement with the single line gradle() and move the constraints into
gradle { buildScript { } } (settingsScript { } for settings, versionCatalog { } for the catalog).
A buildScript { } constraint receives the build.gradle(.kts) of every module, the root included, in
subject.files. To look at one module only, narrow subject.files by path inside the constraint
(for example subject.files.filter { it == "app/build.gradle.kts" }).
A Role inside gradle { } with the same name as one of katachi's ("BuildScript".role { } and so on) is a
duplicate declaration error, and a Role outside gradle { } that declares a build file claims the same
file as BuildScript and becomes [AmbiguousLayout]. Write the constraints in the hook."""))
if flat_groups:
    print()
    print(t("平たい group（直下に Role が 8 個以上あり、group を含まない）が %d 件あります（preferences.nestedGroups = yes）。",
            "Flat groups (8 or more Roles directly inside, no nested group): %d (preferences.nestedGroups = yes).") % len(flat_groups))
    for g in flat_groups:
        print(t('  %s:%d group "%s"（Role %d 個）', '  %s:%d group "%s" (%d Roles)') % (file_uri(g.path), g.line, g.name, g.direct_roles))
    print(t("group は「モジュール → 層 → 種類」の順で入れ子にし、1 つの group に Role を 8 個以上並べないでください（並ぶなら group を切る）。",
            "Nest groups in the order module -> layer -> kind, and do not put 8 or more Roles in one group (split it when they pile up)."))
if unsplit_files:
    print()
    print(t("分けていないファイル（1 ファイルに Role か group が 2 つ以上）が %d 件あります（preferences.oneRolePerFile = yes）。",
            "Files not split (2 or more Roles or groups in one file): %d (preferences.oneRolePerFile = yes).") % len(unsplit_files))
    for p, ds in unsplit_files:
        print("  %s: %s" % (file_uri(p), ", ".join('%s "%s"' % (k, n) for k, n, _ in ds)))
    print(t("1 Role 1 ファイルで書き、group はディレクトリで表してください（手順書 3-3）。",
            "Write one Role per file and express groups as directories (step 3-3 of the guide)."))
if documented_candidates:
    print()
    print(t("documented = false の候補が %d 件あります（生成ドキュメントに載せる価値があるかを見直してください）。",
            "Candidates for documented = false: %d (review whether they belong in the generated docs).") % len(documented_candidates))
    for r, what in documented_candidates:
        print(t('  %s:%d Role "%s"（%s）', '  %s:%d Role "%s" (%s)') % (file_uri(r.path), r.line, r.name, what))
    print(t("""documented = false にするもの: (1) 生成物、(2) ビルド・ツールの設定（.editorconfig、CI の yaml、lint の設定）、
(3) 1 ファイルしかない補助（README、LICENSE、.gitignore）。利用者が読んで役に立つ Role だけを生成ドキュメントに載せます。""",
            """Set documented = false on: (1) generated output, (2) build and tool configuration (.editorconfig, CI yaml,
lint config), (3) single-file helpers (README, LICENSE, .gitignore). Only Roles a reader benefits from belong in the generated docs."""))
print("==================================================================")
PYROLES
	} | python3 - "$_lr_json" "$MODULE_DIR" || :
	[ -n "$_lr_json" ] && rm -f "$_lr_json"
	return 0
}

cmd_lint() {
	lint_roles
}

# ---------------------------------------------------------------- リファクタリング前後の比較

# ./gradlew :architecture-test:test の出力から、検査結果の行だけを取り出して並べ替える。
# 取り出すのは要約の1行（Katachi check failed: ...）と、違反ごとの見出し（[種類] パス）。
# 見出しは katachi が出す種類（KATACHI_VIOLATION_LABELS）だけを拾う。Gradle も
# `[Incubating] Problems report is available at: file://...` のような `[大文字の語] ` の行を出すので、
# 形だけで拾うと違反 0 件でも「増えた 1 件」になる。利用者が自分で定義した違反の種類は比べない。
# 宣言位置（ProjectArchitecture.kt:42 など）はファイルを分ければ変わるのが正しいので比べない。
# [MissingDescription] は説明を書き足せば減るのが正しいので、比べる対象から外して別に数える。
# 要約の行の「, N warnings」も同じ理由で落とす。
# $1 = ログ, $2 = 出力先
# 「Katachi check failed:」の行は、Gradle のテスト出力では例外のクラス名が前に付く
# （`    me.tbsten.katachi.check.KatachiArchitectureAssertionError: Katachi check failed: ...`）。
# その接頭辞は付いていても付いていなくても同じ行として扱う。
CHECK_FAILED_LINE='^[[:space:]]*([A-Za-z0-9_.$]*(Error|Exception): )?Katachi check failed:'
# katachi の違反の種類（katachi/src/main/kotlin/me/tbsten/katachi/check の Violation の label）。
# katachi が種類を足したら、ここにも足す（check-install-kit-smoke.sh の check_result_headings が突き合わせる）。
KATACHI_VIOLATION_LABELS='UnexpectedFile|UnexpectedDirectory|CaptureMismatch|MissingFile|UncheckedFile|UncheckedDirectory|UncheckedCheck|UnsatisfiedFileConstraint|UncheckedFileConstraint|AmbiguousLayout|MissingDescription|StaleBaselineEntry'
VIOLATION_HEADING_LINE="^[[:space:]]*\\[(${KATACHI_VIOLATION_LABELS})\\] "
extract_check_result() {
	grep -E "${CHECK_FAILED_LINE}|${VIOLATION_HEADING_LINE}" "$1" |
		sed -E -e 's/^[[:space:]]*([A-Za-z0-9_.$]*(Error|Exception): )?//' -e 's/, [0-9][0-9]* warnings*$//' |
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

# extract_check_result で揃えた 2 つ（$1 = 前, $2 = 後）の差分を出す。compare-violations と test が共有する。
#   増えた N 件 / 減った M 件
#     + <後だけにある行>
#     - <前だけにある行>
# 行は PRINT_CHECK_DIFF_MAX 件まで。超えたら「ほか N 件（ログを見る）」。同じなら何も出さず 0、違えば 1。
PRINT_CHECK_DIFF_MAX=50
print_check_diff() {
	cmp -s "$1" "$2" && return 0
	_pd="$1.diff.$$"
	diff "$1" "$2" | sed -n -e 's/^> /  + /p' -e 's/^< /  - /p' >"$_pd" || :
	_pd_add=$(grep -c '^  + ' "$_pd" || :)
	_pd_del=$(grep -c '^  - ' "$_pd" || :)
	_pd_n=$((_pd_add + _pd_del))
	say "増えた ${_pd_add} 件 / 減った ${_pd_del} 件" "Added ${_pd_add} / removed ${_pd_del}"
	head -n "$PRINT_CHECK_DIFF_MAX" "$_pd"
	if [ "$_pd_n" -gt "$PRINT_CHECK_DIFF_MAX" ]; then
		say "  ほか $((_pd_n - PRINT_CHECK_DIFF_MAX)) 件（ログを見る）" \
			"  and $((_pd_n - PRINT_CHECK_DIFF_MAX)) more (see the log)"
	fi
	rm -f "$_pd"
	return 1
}

cmd_compare_violations() {
	require_commands "$REQ_COMPARE"
	require_workdir
	resolve_module_dir
	require_definition_dir
	_cv_before="${1:-${WORKDIR}/tmp/test-before-refactor.log}"
	_cv_after="${2:-${WORKDIR}/tmp/test-after-refactor.log}"
	for _cv_f in "$_cv_before" "$_cv_after"; do
		[ -f "$_cv_f" ] || die "$(file_uri "$_cv_f") がありません。./gradlew ${MODULE_GRADLE_PATH}:test の出力をこのパスに保存してください（手順書 3-2 / 3-3）。" \
			"$(file_uri "$_cv_f") not found. Save the output of ./gradlew ${MODULE_GRADLE_PATH}:test to this path (steps 3-2 / 3-3 of the guide)."
		has_check_result "$_cv_f" ||
			die "$(file_uri "$_cv_f") に検査の結果がありません。コンパイルエラーなどで検査まで届いていません。直してから取り直してください。" \
				"$(file_uri "$_cv_f") has no check result. The build did not get as far as the check (a compile error, for example). Fix it and capture the log again."
	done

	mkdir -p "$WORKDIR/tmp/cache"
	_cv_a="$WORKDIR/tmp/cache/.cv-before.$$"
	_cv_b="$WORKDIR/tmp/cache/.cv-after.$$"
	extract_check_result "$_cv_before" "$_cv_a"
	extract_check_result "$_cv_after" "$_cv_b"
	_cv_md_a=$(count_missing_description "$_cv_before")
	_cv_md_b=$(count_missing_description "$_cv_after")
	_cv_n_a=$(grep -c '^\[' "$_cv_a" || :)
	_cv_n_b=$(grep -c '^\[' "$_cv_b" || :)

	say "前: $(file_uri "$_cv_before")" "Before: $(file_uri "$_cv_before")"
	say "後: $(file_uri "$_cv_after")" "After:  $(file_uri "$_cv_after")"
	say "違反の見出し: 前 ${_cv_n_a} 件 / 後 ${_cv_n_b} 件" \
		"Violation headings: before ${_cv_n_a} / after ${_cv_n_b}"
	say "[MissingDescription]（比べない）: 前 ${_cv_md_a} 件 / 後 ${_cv_md_b} 件" \
		"[MissingDescription] (not compared): before ${_cv_md_a} / after ${_cv_md_b}"
	if cmp -s "$_cv_a" "$_cv_b"; then
		rm -f "$_cv_a" "$_cv_b"
		say ""
		say "検査結果は変わっていません。" "The check result has not changed."
		return 0
	fi
	say ""
	say "検査結果が変わっています（- が前だけ、+ が後だけにある行）。" \
		"The check result has changed (- lines only in before, + lines only in after)."
	print_check_diff "$_cv_a" "$_cv_b" || :
	rm -f "$_cv_a" "$_cv_b"
	say ""
	say "リファクタリングで振る舞いが変わっています。前のログを取り直すのではなく、定義のほうを直してください。
分けた定義ファイルが [UnexpectedFile] になっているなら、${MODULE_DIR}/ を受け持つ Role の layout { } が
定義のファイルの最大の深さ（<group>/<sub>/<Role>Role.kt など）まで受け入れていません。" \
		"The refactoring changed the behavior. Fix the definition instead of capturing the before log again.
If the split definition files show up as [UnexpectedFile], the layout { } of the Role that owns
${MODULE_DIR}/ does not accept files as deep as the deepest definition file (e.g. <group>/<sub>/<Role>Role.kt)."
	exit 1
}

# ---------------------------------------------------------------- test（検査用モジュールのテスト）

# 検査用モジュールのテストを走らせ、ログは全文を残し、標準出力には要約だけを出す。
# エージェントがログを grep して回らないようにするためのもの。Gradle を起動するのはこのコマンドだけ。
#   0 = 通った / 1 = 検査が落ちた / 2 = 検査まで届かない（gradlew 無し、projectRoot 無し、定義ディレクトリ無し、
#   コンパイルエラーなど）。2 の理由は warn で出し、die は使わない（終了コードを 2 に揃えるため）。
# 環境変数（JAVA_HOME を含む）はそのまま継ぐ。どの JDK で動くかは detect_gradle_jdk の結果を先頭に出す。
cmd_test() {
	_ts_save=""
	while [ $# -gt 0 ]; do
		case "$1" in
		--save)
			need_value "$1" "${2:-}"
			_ts_save="$2"
			shift 2
			;;
		--)
			shift
			break
			;;
		-h | --help)
			usage
			return 0
			;;
		*) die "test: 知らないオプションです: ${1}（Gradle に渡す引数は -- の後ろに）" \
			"test: unknown option: ${1} (put arguments for Gradle after --)" ;;
		esac
	done

	require_commands "$REQ_TEST"
	require_workdir
	resolve_module_dir
	_ts_root=$(read_checklist_meta "$WORKDIR/check-list.html" projectRoot)
	if [ -z "$_ts_root" ] || [ ! -d "$_ts_root" ]; then
		warn "プロジェクトルートが分かりません（チェックリストの meta.projectRoot: '${_ts_root}'）。init をやり直してください。" \
			"Cannot find the project root (meta.projectRoot in the check list: '${_ts_root}'). Run init again."
		return 2
	fi
	if [ ! -f "$_ts_root/gradlew" ]; then
		warn "gradlew がありません: $(file_uri "$_ts_root/gradlew") 。Gradle wrapper の無いプロジェクトでは、./gradlew ${MODULE_GRADLE_PATH}:test を自分で実行してログを tmp/test-<名前>.log に保存してください。" \
			"gradlew not found: $(file_uri "$_ts_root/gradlew") . Without the Gradle wrapper, run ./gradlew ${MODULE_GRADLE_PATH}:test yourself and save the log as tmp/test-<name>.log."
		return 2
	fi
	_ts_def=$(definition_dir)
	if [ ! -d "$_ts_def" ]; then
		warn "定義のディレクトリがありません: $(file_uri "$_ts_def") 。scaffold をまだ実行していないか、置き場所が違います（記録: meta.moduleDir = ${MODULE_DIR}。変えるなら 'preferences set moduleDir=<path>' か 'scaffold --module-dir <path>'）" \
			"The definition directory does not exist: $(file_uri "$_ts_def") . Either scaffold has not run yet, or the location differs (recorded: meta.moduleDir = ${MODULE_DIR}; change it with 'preferences set moduleDir=<path>' or 'scaffold --module-dir <path>')"
		return 2
	fi

	_ts_dir="$WORKDIR/tmp/test"
	mkdir -p "$_ts_dir"
	_ts_stamp=$(date -u '+%Y-%m-%dT%H-%M-%SZ')
	_ts_log="$_ts_dir/$_ts_stamp.log"
	# 同じ秒に 2 回走ったら上書きしない。
	[ -e "$_ts_log" ] && _ts_log="$_ts_dir/$_ts_stamp.$$.log"
	# 前回のログを回す（latest → previous）。差分は previous と今回を比べる。
	[ -f "$_ts_dir/latest.log" ] && mv "$_ts_dir/latest.log" "$_ts_dir/previous.log"

	# JDK は Gradle のルートで調べる（gradle/gradle-daemon-jvm.properties と gradle.properties を見るため）。
	cd "$_ts_root"
	detect_gradle_jdk
	if [ -n "$GRADLE_JDK_MAJOR" ]; then
		say "JDK: ${GRADLE_JDK_MAJOR}（${GRADLE_JDK_FROM%%|*}）" "JDK: ${GRADLE_JDK_MAJOR} (${GRADLE_JDK_FROM#*|})"
	elif [ -n "$GRADLE_JDK_FROM" ]; then
		say "JDK: 不明（${GRADLE_JDK_FROM%%|*}。版を読めませんでした）" "JDK: unknown (${GRADLE_JDK_FROM#*|}; could not read the version)"
	else
		say "JDK: 不明（JAVA_HOME も PATH の java もありません。Gradle 自身の設定に任せます）" \
			"JDK: unknown (no JAVA_HOME and no java on PATH; leaving it to Gradle)"
	fi
	say "ログ: $(file_uri "$_ts_log")" "Log: $(file_uri "$_ts_log")"

	_ts_status=0
	if [ -x "./gradlew" ]; then
		./gradlew "${MODULE_GRADLE_PATH}:test" --console=plain "$@" >"$_ts_log" 2>&1 || _ts_status=$?
	else
		sh ./gradlew "${MODULE_GRADLE_PATH}:test" --console=plain "$@" >"$_ts_log" 2>&1 || _ts_status=$?
	fi
	cp "$_ts_log" "$_ts_dir/latest.log"
	if [ -n "$_ts_save" ]; then
		cp "$_ts_log" "$WORKDIR/tmp/test-$_ts_save.log"
		say "写し: $(file_uri "$WORKDIR/tmp/test-$_ts_save.log")" "Copy: $(file_uri "$WORKDIR/tmp/test-$_ts_save.log")"
	fi

	# 検査まで届いていなければ、要約のしようがない。ログの末尾だけ見せて 2 で返す。
	if ! has_check_result "$_ts_log"; then
		# 検査の違反は出ていないが、検査用モジュールのほかのテスト（利用者が足した spec など）が落ちた。
		# コンパイルエラーと取り違えないよう、別の文面で 1（検査用モジュールの test が落ちた）を返す。
		if grep -q 'There were failing tests' "$_ts_log"; then
			say "結果: 検査の違反は出ていませんが、検査用モジュールのほかのテストが落ちています（Gradle の終了コード ${_ts_status}）。ログの FAILED の行:" \
				"Result: no violations, but other tests in the verification module failed (Gradle exit code ${_ts_status}). FAILED lines in the log:"
			grep -E 'FAILED$' "$_ts_log" | head -n 30
			return 1
		fi
		say "結果: 検査まで届いていません（コンパイルエラーなど。Gradle の終了コード ${_ts_status}）。ログの末尾:" \
			"Result: the check was not reached (a compile error, for example; Gradle exit code ${_ts_status}). Tail of the log:"
		tail -n 30 "$_ts_log"
		return 2
	fi

	_ts_result=$(grep -E "${CHECK_FAILED_LINE}" "$_ts_log" | sed -E 's/^[[:space:]]*([A-Za-z0-9_.$]*(Error|Exception): )?//' | head -n 1)
	if [ -n "$_ts_result" ]; then
		say "結果: ${_ts_result}" "Result: ${_ts_result}"
		_ts_rc=1
	else
		say "結果: 検査は通りました（違反 0 件）" "Result: the check passed (0 violations)"
		_ts_rc=0
	fi
	_ts_md=$(count_missing_description "$_ts_log")
	if [ "${_ts_md:-0}" -gt 0 ]; then
		say "[MissingDescription]: ${_ts_md} 件（説明を書けば減る）" "[MissingDescription]: ${_ts_md} (goes down as descriptions are written)"
	fi

	# 前回（previous.log）との差分。前回が無い・検査まで届いていなければ出さない。
	if [ -f "$_ts_dir/previous.log" ] && has_check_result "$_ts_dir/previous.log"; then
		_ts_a="$_ts_dir/.prev.$$"
		_ts_b="$_ts_dir/.cur.$$"
		# 件数は違反の見出し（[種類] ...）だけで数える。「Katachi check failed: N violations」の行は
		# 件数が変われば必ず変わるので、差分に混ぜると 1 件ぶん水増しになる（compare-violations では比べる）。
		extract_check_result "$_ts_dir/previous.log" "$_ts_a.all"
		extract_check_result "$_ts_log" "$_ts_b.all"
		grep '^\[' "$_ts_a.all" >"$_ts_a" || :
		grep '^\[' "$_ts_b.all" >"$_ts_b" || :
		rm -f "$_ts_a.all" "$_ts_b.all"
		if cmp -s "$_ts_a" "$_ts_b"; then
			say "前回との差分（previous.log 比）: 無し" "Difference from the previous run (vs previous.log): none"
		else
			printf '%s' "$(pick "前回との差分（previous.log 比）: " "Difference from the previous run (vs previous.log): ")"
			print_check_diff "$_ts_a" "$_ts_b" || :
		fi
		rm -f "$_ts_a" "$_ts_b"
	fi

	# 打ち切りの行（katachi が先頭 N 件だけ出したとき）。全件の出し方を添える。
	_ts_omit=$(grep -E '^[[:space:]]*Showing first ' "$_ts_log" | sed -e 's/^[[:space:]]*//' | head -n 1)
	if [ -n "$_ts_omit" ]; then
		say "省略: ${_ts_omit}。全件は -- -Dkatachi.maxViolations=all" \
			"Omitted: ${_ts_omit}. For everything, pass -- -Dkatachi.maxViolations=all"
	fi
	return "$_ts_rc"
}

# ---------------------------------------------------------------- 進捗と点検

need_python() {
	require_commands "$REQ_JSON"
}

# チェックリストの JSON を python に渡して加工し、書き戻す。
# $1 = python スクリプト, 残り = そのスクリプトへの引数
edit_checklist() {
	need_python
	require_workdir
	_cl="$WORKDIR/check-list.html"
	[ -f "$_cl" ] || die "$(file_uri "$_cl") がありません。先に init を実行してください。" \
		"$(file_uri "$_cl") not found. Run init first."

	_script="$1"
	shift
	# 読んで→書くの間に別のプロセスが書くと、そのぶんが消える。読む前にロックを取る
	# （cmd_data は保持中なら取らない）。失敗して exit するときはトップレベルの trap が放す。
	lock_acquire "$_cl"
	_cur="$_cl.cur.$$"
	extract_json "$_cl" checklist >"$_cur" ||
		die "$(file_uri "$_cl") から JSON を取り出せませんでした。" \
			"Could not extract the JSON from $(file_uri "$_cl")."

	_out="$_cl.new.$$"
	if printf '%s\n%s' "$PY_LANG" "$_script" | python3 - "$_cur" "$_out" "$@"; then
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
	lock_release
}

# 埋め込みの python すべての先頭に付ける。出力の言語を sh 側（resolve_lang）と揃える。
# 文言は t("日本語", "English") で並べる。**この変数の中に \' を書かないこと**（sh の単引用符）。
PY_LANG='
import os

# 出力の言語。sh 側の resolve_lang が決めて環境変数 KATACHI_LANG で渡す。
EN = os.environ.get("KATACHI_LANG") == "en"


def t(ja, en):
    return en if EN else ja
'

# チェックリストの項目 ID → 手順書の節 ID（guide <節 ID>）。check の「次」（CHECK_PY）と
# summary の「再開」（SUMMARY_PY）が同じ対応表を使うために、PY_LANG と同じ流儀で両方の先頭に流し込む。
#   0-1 .. 4-3 はそのまま / 5-1, 5-2 は 5（5 章は小見出しに番号が無い）/ 6-1 .. 6-4 は None
#   （6-A〜6-D は「次」の計算に入れない。summary の完了形が Next action で示す）
SECTION_OF_PY='

def section_of(item_id):
    if item_id in ("5-1", "5-2"):
        return "5"
    if item_id.startswith("6-"):
        return None
    return item_id
'

# 確認事項（questions）の id。チェックリストは q<n>、レポートは cq<n>（codebase-question）と接頭辞を分ける。
# 同じ番号を両方に持たせると、answers set q1 がどちらの q1 なのか決まらず、もう一方に黙って入る。
# ADD_PY（採番）・ANSWERS_PY（回答）・SUMMARY_PY（未回答の一覧）と cmd_answers の先頭に、PY_LANG と同じ流儀で流し込む。
# HTML 側の questionId（id の無い要素を位置から補う）も同じ接頭辞を使う（JS は共有できない）。
QUESTION_ID_PY='
import re as _question_re

QUESTION_ID_PREFIX = {"check-list": "q", "report": "cq"}
QUESTION_TARGET_NAME = {"check-list": ("チェックリスト", "the check list"), "report": ("レポート", "the report")}


def question_id(target, q, pos):
    # id が無い古い要素は位置から補う（HTML の questionId と同じ規則）
    return q.get("id") or "%s%d" % (QUESTION_ID_PREFIX[target], pos + 1)


def question_number(target, qid):
    # target の接頭辞の id なら番号を、違えば None を返す（q1 と cq1 は互いに当たらない）
    m = _question_re.match(r"^%s([0-9]+)$" % QUESTION_ID_PREFIX[target], str(qid or ""))
    return int(m.group(1)) if m else None


def target_of_question_id(qid):
    for target in QUESTION_ID_PREFIX:
        if question_number(target, qid) is not None:
            return target
    return None
'

CHECK_PY='
import json, sys
src, dest, done = sys.argv[1], sys.argv[2], sys.argv[3] == "true"
ids = sys.argv[4:]
data = json.load(open(src, encoding="utf-8"))
known = {i["id"]: i for s in data.get("steps", []) for i in s.get("items", [])}
unknown = [i for i in ids if i not in known]
if unknown:
    sys.stderr.write(t("知らない項目 id です: %s\n", "Unknown item id: %s\n") % ", ".join(unknown))
    sys.stderr.write(t("使える id: %s\n", "Valid ids: %s\n") % ", ".join(known))
    sys.exit(1)
for i in ids:
    known[i]["done"] = done
json.dump(data, open(dest, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
if done:
    print(t("完了にしました: %s", "Marked as done: %s") % ", ".join(ids))
    # 次に読む節。done でない最初の項目（チェックリストの順 = 手順書の目次の順）を section_of で
    # 節 ID にする。None（6-x）か未完了が無ければ出さない。CLI のパスは sh 側が環境変数で渡す。
    cli = os.environ.get("KATACHI_CHECK_CLI")
    pending = [i for i in known if not known[i].get("done")]
    section = section_of(pending[0]) if pending else None
    if cli and section:
        print(t("次: sh %s guide %s", "Next: sh %s guide %s") % (cli, section))
else:
    print(t("未完了に戻しました: %s", "Marked as not done: %s") % ", ".join(ids))
'

cmd_check() {
	[ $# -gt 0 ] || die "check: 項目 id を1つ以上指定してください（例: check 1-1 1-2）" \
		"check: specify one or more item ids (for example: check 1-1 1-2)"
	require_workdir
	# 完了後の「次: sh <CLI> guide <節 ID>」を CHECK_PY が組み立てるために、CLI のパスを渡す。
	KATACHI_CHECK_CLI="$WORKDIR/tmp/katachi-install.sh"
	export KATACHI_CHECK_CLI
	edit_checklist "$SECTION_OF_PY
$CHECK_PY" true "$@"
	# 定義を書き終えた時点と整え終えた時点で、Role の取り違えを拾う。
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
	[ $# -gt 0 ] || die "uncheck: 項目 id を1つ以上指定してください。" \
		"uncheck: specify one or more item ids."
	edit_checklist "$CHECK_PY" false "$@"
}

WARN_PY='
import json, sys
src, dest, step_id = sys.argv[1], sys.argv[2], sys.argv[3]
text = sys.argv[4] if len(sys.argv) > 4 else None
data = json.load(open(src, encoding="utf-8"))
steps = {s["id"]: s for s in data.get("steps", [])}
if step_id not in steps:
    sys.stderr.write(t("知らないステップ id です: %s\n", "Unknown step id: %s\n") % step_id)
    sys.stderr.write(t("使える id: %s\n", "Valid ids: %s\n") % ", ".join(steps))
    sys.exit(1)
steps[step_id]["warning"] = text
json.dump(data, open(dest, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
if text is None:
    print(t("ステップ %s の警告を消しました", "Cleared the warning on step %s") % step_id)
else:
    print(t("ステップ %s の警告を設定しました", "Set a warning on step %s") % step_id)
'

cmd_warn() {
	[ $# -gt 0 ] || die "warn: ステップ id を指定してください（例: warn 4 \"違反を2件残しました\"）" \
		"warn: specify the step id (for example: warn 4 \"Left 2 violations\")"
	_step="$1"
	shift
	if [ "${1:-}" = "--clear" ]; then
		edit_checklist "$WARN_PY" "$_step"
	else
		[ -n "${1:-}" ] || die "warn: 警告の文言を指定してください（消すなら --clear）" \
			"warn: specify the warning text (or --clear to remove it)"
		edit_checklist "$WARN_PY" "$_step" "$1"
	fi
}

# ---------------------------------------------------------------- add

# 配列への追記。`data merge` は配列を丸ごと置き換えるので、2件目を足すと
# 1件目が黙って消える。追記はこちらを使う。
# argv: 1 = 読む JSON, 2 = 書く JSON, 3 = 対象（check-list / report）, 4 = 種類,
#       5 = buildSrc と includeBuild したビルドのディレクトリ（改行区切り。role の Gradle ファイルの判定に使う）,
#       6.. = --<項目> <値> ...
# 追記前の件数を標準エラーに before=N で出す（sh 側が追記後に N+1 を確かめる。design 10）。
ADD_PY='
import json, re, sys

src, dest, target, kind = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
included_builds = [d for d in (sys.argv[5] if len(sys.argv) > 5 else "").split("\n") if d]
args = sys.argv[6:]

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
    # deviation は同じ kind の他のモジュールと構成が違う点（無ければ null）。
    "module":     ("modules",     ["path", "kind", "responsibility", "buildFile", "deviation"], []),
    # group は定義での束ね先（入れ子は . 区切り: feature.ui）。verify が必須に数える。
    "role":       ("roles",       ["importance", "name", "layout", "naming", "count", "note", "group"],
                                  ["allowed", "forbidden", "examples"]),
    "tool":       ("tools",       ["name", "configPath", "declareInKatachi", "note"], []),
    "excluded":   ("excluded",    ["path", "reason"], []),
    # 導入の最後に出す「次にできること」。どの Role にどんなテンプレートを当てるかは
    # エージェントが判断し、ここは記録するだけ。summary とレポートが表示する。
    "template":   ("templates",   ["role", "basedOn", "reason"], ["files", "params"]),
}

# 値を取らない印。role の --constraint-only は「Gradle のビルドファイルに制約を付けるためだけの
# Role」で、Gradle ファイルの拒否を通す代わりに --note（何を検査するか）が要る（design 7）。
FLAGS = {
    "role": {"constraint-only": "constraintOnly"},
}

# question / codebase-question は id を採番する（ブラウザの一括回答が id で答えを返す）。
# 接頭辞は行き先で分ける（チェックリストは q<n>、レポートは cq<n>。QUESTION_ID_PY）。
NUMBERED = ("question", "codebase-question")

# gradle() が受け持つファイル（lint の KINDS の Gradle 行と同じ）。add role の --layout の末尾がこれなら、
# buildSrc / includeBuild したビルドの下でない限り拒む。
GRADLE_FILE = re.compile(r"(^|/)(settings\.gradle(\.kts)?|build\.gradle(\.kts)?|gradle\.properties|[^/]*\.versions\.toml|gradlew(\.bat)?|gradle-wrapper\.[A-Za-z0-9]+)$")


def in_included_build(path):
    p = path.replace("\\", "/").replace(":", "/").lstrip("./")
    return any(p == d or p.startswith(d + "/") for d in included_builds)

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
    sys.stderr.write(t("知らない種類です: %s\n", "Unknown kind: %s\n") % kind)
    sys.stderr.write(t("使えるもの: %s\n", "Valid kinds: %s\n") % ", ".join(FIELDS))
    sys.exit(1)

key, scalars, lists = FIELDS[kind]
flags = FLAGS.get(kind, {})

# --help: 使える項目を並べて終わる。ファイルには触らない（init 前でも答えられるように）
if any(a in ("-h", "--help") for a in args):
    print(t("使い方: add %s --<項目> <値> ...（%s に追記）", "Usage: add %s --<field> <value> ... (appends to %s)") % (kind, key))
    print(t("使える項目: %s", "Valid fields: %s") % ", ".join("--" + f for f in scalars + lists))
    if lists:
        print(t("複数回渡せる項目: %s", "Fields you can pass more than once: %s") % ", ".join("--" + f for f in lists))
    if flags:
        print(t("値を取らない印: %s", "Flags (no value): %s") % ", ".join("--" + f for f in flags))
    if kind == "role":
        print(t("--group は定義での束ね先（入れ子は . 区切り: feature.ui）。Gradle のビルドファイルは gradle() が受け持つので --layout に書けません（buildSrc / includeBuild したビルドは除く）。制約を付けるための Role なら --constraint-only --note <理由> で記録します。",
                "--group is where the Role goes in the definition (nested with dots: feature.ui). Gradle build files cannot be a --layout since gradle() takes care of them (except buildSrc and included builds). A Role that exists only to attach a constraint is recorded with --constraint-only --note <reason>."))
    if kind == "module":
        print(t("--deviation は同じ kind の他のモジュールと構成が違う点（無ければ省略）。",
                "--deviation is how this module differs from the other modules of the same kind (omit if none)."))
    if kind in NUMBERED:
        pre = QUESTION_ID_PREFIX[target]
        print(t("id は %s1, %s2, ... と自動で採番されます（ブラウザの一括回答と answers がこの id で答えを結びつけます）。",
                "The id is numbered automatically as %s1, %s2, ... (the answers in the browser and answers use it).") % (pre, pre))
    sys.exit(0)

if not args:
    sys.stderr.write(t("項目を1つも指定していません: add %s\n", "No fields given: add %s\n") % kind)
    sys.stderr.write(t("このまま追記すると中身が空の行ができます。\n", "Appending as is would add an empty row.\n"))
    sys.stderr.write(t("使えるもの: %s\n", "Valid fields: %s\n") % ", ".join(scalars + lists))
    sys.exit(1)

entry = {}
for f in scalars:
    entry[f] = None
for f in lists:
    entry[f] = []
for f in flags.values():
    entry[f] = False

i = 0
while i < len(args):
    name = args[i].lstrip("-")
    if name in flags:
        entry[flags[name]] = True
        i += 1
        continue
    if i + 1 >= len(args):
        sys.stderr.write(t("--%s に値がありません\n", "--%s needs a value\n") % name)
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
                sys.stderr.write(t("--%s は数値で指定してください: %s\n", "--%s must be a number: %s\n") % (name, value))
                sys.exit(1)
        entry[name] = value
    else:
        sys.stderr.write(t("--%s は %s で使えません\n", "--%s cannot be used with %s\n") % (name, kind))
        if kind == "module" and name == "role":
            sys.stderr.write(t("モジュールの受け持ちは --responsibility です。katachi の Role（1種類のファイル）とは別物で、モジュールは定義では group になります。\n",
                               "What a module is responsible for goes in --responsibility. It is not a katachi Role (one kind of file); a module becomes a group in the definition.\n"))
        sys.stderr.write(t("使えるもの: %s\n", "Valid fields: %s\n") % ", ".join(scalars + lists))
        sys.exit(1)

# Gradle のビルドファイルは gradle() が受け持つ。Role 候補には書かない（design 7）。
# 例外は、制約を付けるためだけの Role（--constraint-only。--note で何を検査するかを書く）。
if kind == "role":
    layout = entry.get("layout") or ""
    if entry["constraintOnly"] and not entry.get("note"):
        sys.stderr.write(t("--constraint-only には --note で理由（何を検査するか）を書いてください\n",
                           "--constraint-only needs --note with the reason (what the constraint checks)\n"))
        sys.exit(1)
    if GRADLE_FILE.search(layout) and not in_included_build(layout) and not entry["constraintOnly"]:
        sys.stderr.write(t("Gradle のビルドファイルは gradle() が受け持ちます。Role 候補には書きません: %s（buildSrc / includeBuild したビルドは除く。制約を付けるための Role なら --constraint-only --note <理由>）\n",
                           "gradle() takes care of Gradle build files. They are not Role candidates: %s (except buildSrc and included builds; for a Role that exists only to attach a constraint, use --constraint-only --note <reason>)\n") % layout)
        sys.exit(1)

data = json.load(open(src, encoding="utf-8"))
rows = data.setdefault(key, [])

idf = IDENTITY.get(kind)
if idf is not None and entry.get(idf) is not None:
    for row in rows:
        if row.get(idf) == entry[idf]:
            sys.stderr.write(t("その %s はすでに登録されています: %s\n", "That %s is already recorded: %s\n") % (idf, entry[idf]))
            sys.stderr.write(t("二重に記録しないため追記しませんでした。\n", "Not appended, to avoid recording it twice.\n"))
            sys.stderr.write(t("書き換えたい場合は data get で取り出し、直してから data set してください。\n",
                               "To change it, take it out with data get, edit it, and write it back with data set.\n"))
            sys.exit(1)

# id の採番（design 5）: <接頭辞><n>、n は既存の同じ接頭辞の id の最大 + 1。id の無い既存要素があれば先に位置順で、
# 使われていない最小の番号から埋める（ブラウザの一括回答が id で答えを返すので、全部に要る）。
if kind in NUMBERED:
    pre = QUESTION_ID_PREFIX[target]
    used = set()
    for row in rows:
        num = question_number(target, row.get("id"))
        if num is not None:
            used.add(num)
    n = 1
    for row in rows:
        if not row.get("id"):
            while n in used:
                n += 1
            row["id"] = "%s%d" % (pre, n)
            used.add(n)
    entry = dict([("id", "%s%d" % (pre, (max(used) if used else 0) + 1))] + list(entry.items()))

# 追記前の件数。sh 側が追記後に N+1 になったことを確かめる（並行書き込みの検知）。
sys.stderr.write("before=%d\n" % len(rows))
sys.stderr.write("array=%s\n" % key)

rows.append(entry)
json.dump(data, open(dest, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print(t("%s に1件追記しました（計 %d 件）", "Appended 1 entry to %s (%d in total)") % (key, len(data[key])))
if kind in NUMBERED:
    print("id=%s" % entry["id"])
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
	case "$_kind" in
	-h | --help)
		say "使い方: add <種類> --<項目> <値> ...（項目は add <種類> --help）" \
			"Usage: add <kind> --<field> <value> ... (for the fields: add <kind> --help)"
		say "  check-list: violation / question / changed"
		say "  report:     module / role / tool / excluded / codebase-question / template"
		return 0
		;;
	esac
	[ -n "$_kind" ] || die "add: 種類を指定してください（violation / question / codebase-question / changed / module / role / tool / excluded / template）" \
		"add: specify the kind (violation / question / codebase-question / changed / module / role / tool / excluded / template)"
	shift

	_target=$(add_target_of "$_kind")
	[ -n "$_target" ] || die "add: 知らない種類です: ${_kind}" "add: unknown kind: ${_kind}"

	# --help なら使える項目を並べて終わる。チェックリストやレポートには触らない。
	if wants_help "$@"; then
		printf '%s\n%s\n%s' "$PY_LANG" "$QUESTION_ID_PY" "$ADD_PY" | python3 - "" "" "$_target" "$_kind" "" --help
		return $?
	fi

	require_workdir
	_html=$(resolve_data_target "$_target")
	[ -f "$_html" ] || die "ファイルがありません: $(file_uri "$_html")" "File not found: $(file_uri "$_html")"
	_id=$(guess_block_id "$_html")
	_root=$(read_checklist_meta "$WORKDIR/check-list.html" projectRoot)
	[ -n "$_root" ] || _root="$PWD"

	# excluded: .gitignore で除外されたパスは katachi がもともと見ないので、対象外には書かない（design 6）。
	# cwd に関わらずプロジェクトルートで判定する（git が無ければ検査しない）。
	if [ "$_kind" = "excluded" ]; then
		_ex_path=""
		_ex_prev=""
		for _ex_a in "$@"; do
			[ "$_ex_prev" = "--path" ] && _ex_path="$_ex_a"
			_ex_prev="$_ex_a"
		done
		if [ -n "$_ex_path" ] && [ -d "$_root" ] && (cd "$_root" && path_is_ignored "$_ex_path"); then
			die "そのパスは .gitignore で除外されています。katachi はもともと見ないので、対象外には書きません: ${_ex_path}" \
				"That path is excluded by .gitignore. katachi never looks at it, so it is not recorded as excluded: ${_ex_path}"
		fi
	fi

	# buildSrc と includeBuild したビルド（scaffold 側の included_build_dirs と同じ抽出。ADD_PY の argv[5]）。
	_included=""
	[ "$_kind" = "role" ] && _included=$(printf 'buildSrc\n' && included_build_dirs "$_root")

	# 読む前にロックを取る（並べて add しても 1 件ずつ追記される）。cmd_data は保持中なら取らない。
	lock_acquire "$_html"
	_cur="$_html.cur.$$"
	extract_json "$_html" "$_id" >"$_cur" || {
		rm -f "$_cur"
		die "$(file_uri "$_html") から JSON を取り出せません。" \
			"Could not extract the JSON from $(file_uri "$_html")."
	}

	# ADD_PY の標準エラーは一度ファイルに受ける。before=N（追記前の件数）と array=<配列名> を
	# 抜き取り、残りをそのまま標準エラーに流す。
	_new="$_html.new.$$"
	_err="$_html.err.$$"
	if printf '%s\n%s\n%s' "$PY_LANG" "$QUESTION_ID_PY" "$ADD_PY" | python3 - "$_cur" "$_new" "$_target" "$_kind" "$_included" "$@" 2>"$_err"; then
		grep -v '^before=\|^array=' "$_err" >&2 || :
		_before=$(sed -n 's/^before=//p' "$_err" | head -n 1)
		_array=$(sed -n 's/^array=//p' "$_err" | head -n 1)
		rm -f "$_cur" "$_err"
		# 件数の検査（design 10）: 追記前 N 件 → 追記後 N+1 件。KATACHI_ADD_EXPECT は検査そのものを
		# 確かめるためのテスト用（usage には書かない）。
		[ -n "${KATACHI_ADD_EXPECT:-}" ] && _before="$KATACHI_ADD_EXPECT"
		if cmd_data set "$_html" "$_new" --id "$_id" >/dev/null; then
			rm -f "$_new" "$_html.bak"
		else
			rm -f "$_new"
			exit 1
		fi
		_after=$(extract_json "$_html" "$_id" 2>/dev/null | python3 -c '
import json, sys
print(len(json.load(sys.stdin).get(sys.argv[1]) or []))
' "$_array" 2>/dev/null || printf '?')
		case "${_before:-x}${_after:-x}" in
		*[!0-9]*) ;;
		*)
			[ "$_after" -eq "$((_before + 1))" ] ||
				die "件数が合いません: ${_array} は書き込み前 ${_before} 件、追記後 ${_after} 件（期待 $((_before + 1))）。ほかのプロセスが同時に書いていませんか" \
					"The count does not add up: ${_array} had ${_before} entries before, ${_after} after appending (expected $((_before + 1))). Is another process writing at the same time?"
			;;
		esac
	else
		grep -v '^before=\|^array=' "$_err" >&2 || :
		rm -f "$_cur" "$_new" "$_err"
		exit 1
	fi
	lock_release
}

# ---------------------------------------------------------------- preferences

# 書き方の方針（design 4）。キーはチェックリスト JSON の preferences、moduleDir だけは meta.moduleDir。
# $1 = キー。使える値を空白区切りで出す（moduleDir は path）。知らないキーなら空。
preference_values_of() {
	case "$1" in
	nestedGroups | oneRolePerFile) printf 'yes no\n' ;;
	moduleDir) printf 'path\n' ;;
	testFramework) printf 'junit5 kotest\n' ;;
	dependencyStyle) printf 'catalog literal\n' ;;
	constraintNames) printf 'plain numbered\n' ;;
	*) printf '\n' ;;
	esac
}
PREFERENCE_KEYS="nestedGroups moduleDir testFramework dependencyStyle constraintNames oneRolePerFile"

# $1 = キー。問いを出す（show の行の末尾に添える）。
preference_question_of() {
	case "$1" in
	nestedGroups) pick "group を入れ子にするか" "Nest groups?" ;;
	moduleDir) pick "検査用モジュールの置き場所" "Where the test module goes" ;;
	testFramework) pick "テストのフレームワーク" "Test framework" ;;
	dependencyStyle) pick "依存の書き方" "How dependencies are written" ;;
	constraintNames) pick "制約名に確認事項の番号を入れるか" "Put the question number in constraint names?" ;;
	oneRolePerFile) pick "1 Role 1 ファイルで書くか" "One Role per file?" ;;
	esac
}

cmd_preferences() {
	_pf_action="${1:-show}"
	case "$_pf_action" in
	-h | --help)
		usage
		return 0
		;;
	show | set) [ $# -gt 0 ] && shift ;;
	*=*) _pf_action="set" ;;
	*) die "preferences のサブコマンドは show / set です（指定されたのは '${_pf_action}'）" \
		"The preferences subcommands are show / set (got '${_pf_action}')" ;;
	esac
	need_python
	require_workdir
	resolve_module_dir
	_pf_cl="$WORKDIR/check-list.html"
	[ -f "$_pf_cl" ] || die "$(file_uri "$_pf_cl") がありません。先に init を実行してください。" \
		"$(file_uri "$_pf_cl") not found. Run init first."

	if [ "$_pf_action" = "set" ]; then
		[ $# -gt 0 ] || die "preferences set: key=value を 1 つ以上指定してください（例: preferences set nestedGroups=no）" \
			"preferences set: give one or more key=value (for example: preferences set nestedGroups=no)"
		# 全部を先に検査してから書く（途中で止まって半分だけ記録しない）。
		_pf_patch=""
		_pf_module_dir=""
		for _pf_kv in "$@"; do
			case "$_pf_kv" in
			*=*) ;;
			*) die "preferences set は key=value の形です: ${_pf_kv}" "preferences set takes key=value: ${_pf_kv}" ;;
			esac
			_pf_k="${_pf_kv%%=*}"
			_pf_v="${_pf_kv#*=}"
			_pf_vals=$(preference_values_of "$_pf_k")
			[ -n "$_pf_vals" ] || die "知らないキーです: ${_pf_k}（使えるキー: ${PREFERENCE_KEYS}）" \
				"Unknown key: ${_pf_k} (valid keys: ${PREFERENCE_KEYS})"
			if [ "$_pf_vals" = "path" ]; then
				validate_module_dir "$_pf_v"
				_pf_module_dir="$_pf_v"
				continue
			fi
			_pf_ok="no"
			for _pf_c in $_pf_vals; do
				[ "$_pf_c" = "$_pf_v" ] && _pf_ok="yes"
			done
			[ "$_pf_ok" = "yes" ] || die "${_pf_k} の値が違います: ${_pf_v}（使える値: $(printf '%s' "$_pf_vals" | tr ' ' '/')）" \
				"Invalid value for ${_pf_k}: ${_pf_v} (valid values: $(printf '%s' "$_pf_vals" | tr ' ' '/'))"
			_pf_patch="${_pf_patch}${_pf_patch:+,}\"${_pf_k}\": \"${_pf_v}\""
		done
		# 書き込みはロック付きの data merge（dict なので追記になる）。moduleDir は meta に書く。
		lock_acquire "$_pf_cl"
		if [ -n "$_pf_patch" ]; then
			_pf_file="$WORKDIR/tmp/cache/.preferences.$$"
			printf '{"preferences": {%s}}\n' "$_pf_patch" >"$_pf_file"
			if cmd_data merge "$_pf_cl" "$_pf_file" --id checklist >/dev/null; then
				rm -f "$_pf_file" "$_pf_cl.bak"
			else
				rm -f "$_pf_file"
				exit 1
			fi
		fi
		if [ -n "$_pf_module_dir" ]; then
			update_checklist_meta "$_pf_cl" moduleDir "$_pf_module_dir"
			MODULE_DIR="$_pf_module_dir"
			MODULE_DIR_SOURCE="checklist"
		fi
		lock_release
		say "記録しました: $*" "Recorded: $*"
		return 0
	fi

	# show: 1 行 1 キー `key = 値（推奨 / 記録済み / 検出: <根拠>）`。検出はプロジェクトルートで行う
	# （testFramework は S2 の detect_test_framework、dependencyStyle は gradle/libs.versions.toml の有無）。
	_pf_root=$(read_checklist_meta "$_pf_cl" projectRoot)
	[ -n "$_pf_root" ] && [ -d "$_pf_root" ] && cd "$_pf_root"
	say "書き方の方針（推奨から変えたいものだけ 'preferences set key=value' で記録してください）:" \
		"Preferences (record only what you want to change from the recommendation with 'preferences set key=value'):"
	for _pf_k in $PREFERENCE_KEYS; do
		_pf_state=""
		if [ "$_pf_k" = "moduleDir" ]; then
			_pf_v="$MODULE_DIR"
			[ "$MODULE_DIR_SOURCE" = "checklist" ] && _pf_state=$(pick "記録済み" "recorded") || _pf_state=$(pick "推奨" "recommended")
		else
			_pf_v=$(read_preference "$_pf_k")
			if [ -n "$_pf_v" ]; then
				_pf_state=$(pick "記録済み" "recorded")
			else
				_pf_state=$(pick "推奨" "recommended")
				case "$_pf_k" in
				nestedGroups | oneRolePerFile) _pf_v="yes" ;;
				constraintNames) _pf_v="plain" ;;
				testFramework)
					SC_TF_DETECTED=""
					SC_TF_FROM=""
					detect_test_framework
					_pf_v="${SC_TF_DETECTED:-junit5}"
					[ -n "$SC_TF_FROM" ] && _pf_state=$(pick "検出: ${SC_TF_FROM}" "detected: ${SC_TF_FROM}")
					;;
				dependencyStyle)
					if [ -f gradle/libs.versions.toml ]; then
						_pf_v="catalog"
						_pf_state=$(pick "検出: gradle/libs.versions.toml" "detected: gradle/libs.versions.toml")
					else
						_pf_v="literal"
					fi
					;;
				esac
			fi
		fi
		printf '%s = %s%s%s%s   # %s (%s)\n' "$_pf_k" "$_pf_v" "$(pick "（" " (")" "$_pf_state" "$(pick "）" ")")" \
			"$(preference_question_of "$_pf_k")" "$(preference_values_of "$_pf_k" | tr ' ' '/')"
	done
}

# ---------------------------------------------------------------- answers

# 確認事項への回答（design 5）。answers は dict: { "<id>": { "answer", "note", "answeredAt" } }。
# argv: 1 = 動作（show / import / set）, 2 = 対象（check-list / report）, 3 = 対象の JSON, 4 = 書き出すパッチ（show は空）,
#       import: 5 = 取り込む JSON / set: 5 = id, 6 = 回答, 7 = note（空なら null）
# import / set はパッチ（answers だけ）を 4 に書く。sh が data merge で深くマージする。
ANSWERS_PY='
import datetime, json, re, sys

action, target, src, patch = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
args = sys.argv[5:]
data = json.load(open(src, encoding="utf-8"))
questions = data.get("questions") or []
answers = data.get("answers") or {}
if not isinstance(answers, dict):
    answers = {}


def qid(q, pos):
    return question_id(target, q, pos)


def other_side(i):
    # もう一方のファイルの id なら、その名前（取り違えを黙って飛ばさないため）
    other = target_of_question_id(i)
    return QUESTION_TARGET_NAME[other] if other and other != target else None


ids = [qid(q, n) for n, q in enumerate(questions)]
now = datetime.datetime.now().astimezone().replace(microsecond=0).isoformat()

if action == "show":
    answered = [i for i in ids if answers.get(i)]
    print(t("回答済み %d 件 / 未回答 %d 件", "Answered %d / unanswered %d") % (len(answered), len(ids) - len(answered)))
    for n, q in enumerate(questions):
        i = qid(q, n)
        if answers.get(i):
            continue
        rec = q.get("recommendation")
        line = "- %s %s" % (i, q.get("question") or t("(未記入)", "(blank)"))
        if rec:
            line += t(" → 推奨: %s", " → recommended: %s") % rec
        print(line)
    sys.exit(0)

incoming = {}
if action == "import":
    try:
        doc = json.load(open(args[0], encoding="utf-8"))
    except Exception as e:
        sys.stderr.write(t("JSON として読めません: %s（%s）\n", "Not valid JSON: %s (%s)\n") % (args[0], e))
        sys.exit(1)
    raw = doc.get("answers") if isinstance(doc, dict) else None
    if not isinstance(raw, dict):
        sys.stderr.write(t("answers が dict ではありません（ブラウザで書き出した JSON をそのまま渡してください）\n",
                           "answers is not a dict (pass the JSON exported from the browser as is)\n"))
        sys.exit(1)
    for i, a in raw.items():
        if i not in ids:
            side = other_side(i)
            if side:
                sys.stderr.write(t("[!] %s は%sの確認事項なので、ここには入れずに飛ばしました\n",
                                   "[!] Skipped %s: it is a question in %s, not here\n") % (i, t(*side)))
            else:
                sys.stderr.write(t("[!] 知らない id を飛ばしました: %s\n", "[!] Skipped an unknown id: %s\n") % i)
            continue
        if isinstance(a, dict):
            ans = a.get("answer")
            note = a.get("note")
        else:
            ans, note = a, None
        if ans is None or str(ans).strip() == "":
            continue
        incoming[i] = {"answer": str(ans), "note": (str(note) if note not in (None, "") else None),
                       "answeredAt": a.get("answeredAt") if isinstance(a, dict) and a.get("answeredAt") else now}
else:
    i, ans, note = args[0], args[1], args[2] if len(args) > 2 else ""
    if i not in ids:
        sys.stderr.write(t("知らない id です: %s（使える id: %s）\n", "Unknown id: %s (valid ids: %s)\n") % (i, ", ".join(ids) or "-"))
        sys.exit(1)
    incoming[i] = {"answer": ans, "note": note or None, "answeredAt": now}

merged = dict(answers)
merged.update(incoming)
json.dump({"answers": incoming}, open(patch, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
unanswered = [i for i in ids if not merged.get(i)]
print(t("取り込みました: 回答 %d 件（未回答 %d 件）", "Imported: %d answers (%d unanswered)") % (len(incoming), len(unanswered)))
'

# answers set / import の書き込み先を決める（QUESTION_ID_PY を前に付けて流す）。
# argv: 1 = 動作（set / import）, 2 = --target の値（空なら無し）, 3 = set なら id / import なら JSON のパス
# 決まった対象（check-list / report）を出す。決まらない・食い違うなら理由を標準エラーに出して 1。
#   set:    id の接頭辞で決める（q<n> はチェックリスト、cq<n> はレポート）。--target と食い違えば止まる
#   import: JSON の target > --target > JSON の id の接頭辞（全部が同じ側のときだけ）。target と --target が食い違えば止まる
ANSWERS_TARGET_PY='
import json, sys

action, given, arg = sys.argv[1], sys.argv[2], sys.argv[3]


def name(target):
    return t(*QUESTION_TARGET_NAME[target])


def fail(ja, en):
    sys.stderr.write("\n[x] %s\n" % t(ja, en))
    sys.exit(1)


if action == "set":
    found = target_of_question_id(arg)
    if found is None:
        fail("answers set: 確認事項の id ではありません: %s（チェックリストの確認事項は q<n>、レポートの確認事項は cq<n>）" % arg,
             "answers set: not a question id: %s (questions in the check list are q<n>, in the report cq<n>)" % arg)
    if given and given != found:
        fail("answers set: %s は%s（%s）の確認事項で、--target %s と食い違うので書きませんでした。--target を外してください。"
             % (arg, name(found), found, given),
             "answers set: %s is a question in %s (%s), which contradicts --target %s. Nothing was written; drop --target."
             % (arg, name(found), found, given))
    print(found)
    sys.exit(0)

try:
    doc = json.load(open(arg, encoding="utf-8"))
except Exception:
    # 読めない JSON は ANSWERS_PY が理由つきで止める。ここでは対象だけ返す。
    print(given or "report")
    sys.exit(0)
from_json = doc.get("target") if isinstance(doc, dict) and isinstance(doc.get("target"), str) else ""
if from_json == "checklist":
    from_json = "check-list"
if from_json and given and from_json != given:
    fail("answers import: JSON の target（%s）と --target %s が食い違います。どちらに取り込むかを確かめてください。" % (from_json, given),
         "answers import: the target in the JSON (%s) contradicts --target %s. Check which file the answers belong to." % (from_json, given))
if from_json or given:
    print(from_json or given)
    sys.exit(0)
raw = doc.get("answers") if isinstance(doc, dict) else None
sides = set(target_of_question_id(i) for i in raw) if isinstance(raw, dict) else set()
if len(sides) == 1 and None not in sides:
    print(sides.pop())
    sys.exit(0)
fail("answers import: JSON に target が無く、id からも取り込み先を決められません。--target report か --target check-list を付けてください。",
     "answers import: the JSON has no target, and the ids do not tell where it belongs. Pass --target report or --target check-list.")
'

# answers show の 1 ファイルぶん。$1 = 対象（check-list / report）
answers_show() {
	_as_html=$(resolve_data_target "$1")
	[ -f "$_as_html" ] || die "ファイルがありません: $(file_uri "$_as_html")" "File not found: $(file_uri "$_as_html")"
	_as_id=$(guess_block_id "$_as_html")
	_as_cur="$_as_html.cur.$$"
	extract_json "$_as_html" "$_as_id" >"$_as_cur" || {
		rm -f "$_as_cur"
		die "$(file_uri "$_as_html") から JSON を取り出せません。" \
			"Could not extract the JSON from $(file_uri "$_as_html")."
	}
	say "$(pick "対象" "Target"): ${1}"
	if printf '%s\n%s\n%s' "$PY_LANG" "$QUESTION_ID_PY" "$ANSWERS_PY" | python3 - show "$1" "$_as_cur" ""; then
		rm -f "$_as_cur"
	else
		rm -f "$_as_cur"
		exit 1
	fi
}

cmd_answers() {
	_an_action="${1:-show}"
	case "$_an_action" in
	-h | --help)
		usage
		return 0
		;;
	show | import | set) [ $# -gt 0 ] && shift ;;
	# show は省略できる（answers --target report）。オプションは下の while が読む。
	-*) _an_action="show" ;;
	*) die "answers のサブコマンドは show / import / set です（指定されたのは '${_an_action}'）" \
		"The answers subcommands are show / import / set (got '${_an_action}')" ;;
	esac
	need_python
	_an_target=""
	_an_note=""
	_an_1=""
	_an_2=""
	_an_n=0
	while [ $# -gt 0 ]; do
		case "$1" in
		--target)
			need_value "$1" "${2:-}"
			_an_target="$2"
			shift 2
			;;
		--note)
			need_value "$1" "${2:-}"
			_an_note="$2"
			shift 2
			;;
		-*) die "answers: 知らないオプションです: ${1}" "answers: unknown option: ${1}" ;;
		*)
			_an_n=$((_an_n + 1))
			case "$_an_n" in
			1) _an_1="$1" ;;
			2) _an_2="$1" ;;
			*) die "answers: 引数が多すぎます: ${1}（回答に空白があれば引用符で囲んでください）" \
				"answers: too many arguments: ${1} (quote the answer if it contains spaces)" ;;
			esac
			shift
			;;
		esac
	done
	[ "$_an_target" = "checklist" ] && _an_target="check-list"
	case "$_an_target" in
	"" | report | check-list) ;;
	*) die "answers: 対象は report か check-list です: ${_an_target}" "answers: the target is report or check-list: ${_an_target}" ;;
	esac

	# show: --target が無ければ両方（チェックリスト → レポート）。どちらにも確認事項がある。
	if [ "$_an_action" = "show" ]; then
		if [ -n "$_an_target" ]; then
			answers_show "$_an_target"
		else
			answers_show check-list
			say ""
			answers_show report
		fi
		return 0
	fi

	if [ "$_an_action" = "import" ]; then
		[ -n "$_an_1" ] || die "answers import: ブラウザで書き出した JSON のパスを指定してください。" \
			"answers import: give the path of the JSON exported from the browser."
		[ -f "$_an_1" ] || die "ファイルがありません: $(file_uri "$_an_1")" "File not found: $(file_uri "$_an_1")"
	else
		[ -n "$_an_1" ] && [ -n "$_an_2" ] || die "answers set: <id> <回答> を指定してください（例: answers set q1 \"はい\" --note \"理由\"）" \
			"answers set: give <id> <answer> (for example: answers set q1 \"yes\" --note \"reason\")"
	fi
	# 書き込み先は id（set）か JSON（import）から決める。既定の対象は置かない（黙ってもう一方に入れないため）。
	_an_target=$(printf '%s\n%s\n%s' "$PY_LANG" "$QUESTION_ID_PY" "$ANSWERS_TARGET_PY" | python3 - "$_an_action" "$_an_target" "$_an_1") || exit 1
	case "$_an_target" in
	report | check-list) ;;
	*) die "answers: 対象は report か check-list です: ${_an_target}" "answers: the target is report or check-list: ${_an_target}" ;;
	esac
	_an_html=$(resolve_data_target "$_an_target")
	[ -f "$_an_html" ] || die "ファイルがありません: $(file_uri "$_an_html")" "File not found: $(file_uri "$_an_html")"
	_an_id=$(guess_block_id "$_an_html")

	# 読む前にロックを取る（cmd_data は保持中なら取らない）。
	lock_acquire "$_an_html"
	_an_cur="$_an_html.cur.$$"
	extract_json "$_an_html" "$_an_id" >"$_an_cur" || {
		rm -f "$_an_cur"
		die "$(file_uri "$_an_html") から JSON を取り出せません。" \
			"Could not extract the JSON from $(file_uri "$_an_html")."
	}
	say "$(pick "対象" "Target"): ${_an_target}"
	_an_patch="$_an_html.answers.$$"
	if printf '%s\n%s\n%s' "$PY_LANG" "$QUESTION_ID_PY" "$ANSWERS_PY" | python3 - "$_an_action" "$_an_target" "$_an_cur" "$_an_patch" "$_an_1" "$_an_2" "$_an_note"; then
		rm -f "$_an_cur"
		if cmd_data merge "$_an_html" "$_an_patch" --id "$_an_id" >/dev/null; then
			rm -f "$_an_patch" "$_an_html.bak"
		else
			rm -f "$_an_patch"
			exit 1
		fi
	else
		rm -f "$_an_cur" "$_an_patch"
		exit 1
	fi
	lock_release
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
    missing.append(t("check-list: steps がありません（テンプレートが壊れています）",
                     "check-list: no steps (the template is broken)"))

for s in steps:
    # 任意のステップは数えない。やらなくても完了とみなす
    if s["id"] == final or s.get("optional") is True:
        continue
    for i in s.get("items", []):
        if not i.get("done"):
            missing.append(t("check-list: 未チェック %s %s", "check-list: not done %s %s") % (i["id"], i.get("label", "")))

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
    # group は定義での束ね先（design 6）。preferences / answers は未記入に数えない。
    for n, r in enumerate(rp.get("roles") or []):
        for k in ("importance", "name", "layout", "naming", "group"):
            if blank(r.get(k)):
                missing.append("report: roles[%d].%s" % (n, k))

warnings = [(s["id"], s["warning"]) for s in steps if s.get("warning")]

if missing:
    print(t("未完了・未記入が %d 件あります。", "Unfinished or blank: %d") % len(missing))
    print()
    for m in missing:
        print("  - %s" % m)
else:
    for s in steps:
        if s["id"] == final:
            for i in s.get("items", []):
                i["done"] = True
    json.dump(cl, open(out_path, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print(t("未完了・未記入はありません。ステップ %s を完了にしました。",
            "Nothing is unfinished or blank. Marked step %s as done.") % final)

if warnings:
    print()
    print(t("警告つきのステップ:", "Steps with warnings:"))
    for sid, text in warnings:
        print(t("  - ステップ %s: %s", "  - step %s: %s") % (sid, text))

sys.exit(1 if missing else 0)
'

cmd_verify() {
	need_python
	require_workdir
	_cl="$WORKDIR/check-list.html"
	_rp="$WORKDIR/project-code-base-report.html"
	[ -f "$_cl" ] || die "$(file_uri "$_cl") がありません。先に init を実行してください。" \
		"$(file_uri "$_cl") not found. Run init first."

	_a="$WORKDIR/tmp/cache/.verify-cl.$$"
	_out="$WORKDIR/tmp/cache/.verify-out.$$"
	# 通ったときに最後のステップを完了にして書き戻すので、読む前にロックを取る。
	lock_acquire "$_cl"
	extract_json "$_cl" checklist >"$_a" || die "チェックリストの JSON を読めません。" \
		"Cannot read the JSON of the check list."

	_b=""
	if [ -f "$_rp" ]; then
		_b="$WORKDIR/tmp/cache/.verify-rp.$$"
		if ! extract_json "$_rp" report >"$_b"; then
			rm -f "$_a" "$_b"
			die "レポートの JSON を読めません。開始タグと閉じタグは、それぞれ行頭に単独で置かれている必要があります。" \
				"Cannot read the JSON of the report. The opening and closing tags must each be alone at the start of a line."
		fi
	fi

	# `set -e` の下では、条件に置かないとここで抜けて後始末が走らない。
	if printf '%s\n%s' "$PY_LANG" "$VERIFY_PY" | python3 - "$_a" "$_out" ${_b:+"$_b"}; then
		_rc=0
	else
		_rc=1
	fi

	# 通ったときだけ、最後のステップを完了にしたものが書き出されている。
	if [ "$_rc" = "0" ] && [ -s "$_out" ]; then
		cmd_data set "$_cl" "$_out" --id checklist >/dev/null && rm -f "$_cl.bak"
	fi

	rm -f "$_a" "$_out" ${_b:+"$_b"}
	lock_release
	return $_rc
}

# argv: 1 = チェックリストの JSON, 2 = レポートの JSON, 3 = MODULE_DIR（相対パス）, 4 = MODULE_GRADLE_PATH（:a:b）,
#       5 = このスクリプトのパス（再開欄の `sh <CLI> guide <節>`）, 6 = 定義のディレクトリ, 7 = その有無（yes / no）
# 先頭に PY_LANG と SECTION_OF_PY（section_of）を流し込む。
SUMMARY_PY='
import json, os, sys

cl = json.load(open(sys.argv[1], encoding="utf-8"))
module_dir = sys.argv[3] if len(sys.argv) > 3 and sys.argv[3] else "architecture-test"
module_path = sys.argv[4] if len(sys.argv) > 4 and sys.argv[4] else ":" + module_dir.replace("/", ":")
cli = sys.argv[5] if len(sys.argv) > 5 and sys.argv[5] else "katachi-install.sh"
def_dir = sys.argv[6] if len(sys.argv) > 6 else ""
def_exists = len(sys.argv) > 7 and sys.argv[7] == "yes"
try:
    report = json.load(open(sys.argv[2], encoding="utf-8"))
except Exception:
    report = {}
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
        return t("<作業用ディレクトリ>/", "<working directory>/") + name
    return file_uri(os.path.join(workdir, name))

violations = cl.get("violations") or []
changed = cl.get("changedFiles") or []
steps = cl.get("steps", [])
items = [i for s in steps if s.get("optional") is not True for i in s.get("items", [])]
done = [i for i in items if i.get("done")]
optional_items = [i for s in steps if s.get("optional") is True for i in s.get("items", [])]


def answer_counts(doc, target):
    # 確認事項（questions）と回答（answers: { "<id>": {...} }）。id の無い古い要素は位置から補う（QUESTION_ID_PY）。
    qs = doc.get("questions") or []
    ans = doc.get("answers") or {}
    if not isinstance(ans, dict):
        ans = {}
    ids = [question_id(target, q, n) for n, q in enumerate(qs)]
    answered = [i for i in ids if ans.get(i)]
    unanswered = [(i, q) for i, q in zip(ids, qs) if not ans.get(i)]
    return len(answered), unanswered


report_answered, report_unanswered = answer_counts(report, "report")
cl_answered, cl_unanswered = answer_counts(cl, "check-list")

if len(done) < len(items):
    print(t("❌ katachi のセットアップができませんでした", "❌ Could not set up katachi"))
    print()
    print(t("終わっていない項目:", "Unfinished items:"))
    for i in items:
        if not i.get("done"):
            print("- %s %s" % (i["id"], i.get("label", "")))
    # 再開の欄（design 3）: 次のセッションが会話の記憶に頼らずに続けられるように。
    print()
    print(t("再開:", "Resume:"))
    print(t("  作業用ディレクトリ: %s", "  Working directory: %s")
          % (file_uri(workdir) if workdir else t("<作業用ディレクトリ>", "<working directory>")))
    print(t("  終わった項目: %s", "  Done: %s") % (" ".join(i["id"] for i in done) or "-"))
    first = next((i for i in items if not i.get("done")), None)
    section = section_of(first["id"]) if first else None
    if section:
        print(t("  次の節: sh %s guide %s", "  Next section: sh %s guide %s") % (cli, section))
    if def_exists:
        print(t("  定義: %s", "  Definition: %s") % file_uri(def_dir))
    else:
        print(t("  定義: まだ無い（scaffold 未実行。meta.moduleDir = %s）",
                "  Definition: not yet (scaffold has not run; meta.moduleDir = %s)") % module_dir)
    print(t("  確認事項: レポート 回答済み %d / 未回答 %d、チェックリスト 回答済み %d / 未回答 %d",
            "  Questions: report answered %d / unanswered %d, check list answered %d / unanswered %d")
          % (report_answered, len(report_unanswered), cl_answered, len(cl_unanswered)))
    print()
    print(t("チェックリスト:", "Check list:"))
    print(in_workdir("check-list.html"))
    sys.exit(0)


def print_questions(label, where, answered, unanswered):
    # 回答済みは件数だけ、未回答だけ id / 問い / 推奨 で列挙する（design 5）。
    if not answered and not unanswered:
        return
    print(t("- %s: 回答済み %d 件 / 未回答 %d 件（%s）", "- %s: answered %d / unanswered %d (%s)")
          % (label, answered, len(unanswered), where))
    for i, q in unanswered:
        text = q.get("question") or t("(未記入)", "(blank)")
        rec = q.get("recommendation")
        line = "    - %s %s" % (i, text)
        if rec:
            line += t(" → 推奨: %s", " → recommended: %s") % rec
        print(line)

if violations:
    print(t("⚠️ katachi のセットアップを行いました", "⚠️ Set up katachi"))
else:
    print(t("✅ katachi のセットアップを行いました", "✅ Set up katachi"))
print()
print(t("**生成されたコードはあなたのレビューが必要不可欠** です。内容を確認してください。",
        "**The generated code needs your review.** Please check what it contains."))
print()
print(t("- チェックリスト: %s", "- Check list: %s") % in_workdir("check-list.html"))
print(t("- コードベース レポート: %s", "- Codebase report: %s") % in_workdir("project-code-base-report.html"))
if violations:
    print(t("- `%s:test` の実行結果: ⚠️ %d 件のアーキテクチャ違反を残しています",
            "- `%s:test` result: ⚠️ architecture violations left: %d") % (module_path, len(violations)))
else:
    print(t("- `%s:test` の実行結果: ✅ All green", "- `%s:test` result: ✅ All green") % module_path)
print_questions(t("確認したいこと（チェックリスト）", "Questions for you (check list)"),
                t("チェックリストの「ユーザに確認したいこと」", "\"Questions for the user\" in the check list"),
                cl_answered, cl_unanswered)
print_questions(t("コードベースの揺れ（レポート）", "Inconsistencies in the codebase (report)"),
                t("レポートの questions", "questions in the report"),
                report_answered, report_unanswered)
print(t("- 変更したファイル: %d 件", "- Changed files: %d") % len(changed))
print("- Next action:")
print(t("    - %s/ 以下の ProjectArchitecture.kt を**レビュー**してください",
        "    - **Review** ProjectArchitecture.kt under %s/") % file_uri(module_dir))
# 任意のステップで未実施のものを、これからやることとして出す。
# label は「〜した」の完了形なので、そのまま出すと嘘になる。
HINT = {
    "6-1": t("実際の実装タスクを1つ回して、定義が機能することを確かめてください",
             "Run one real implementation task and confirm that the definition works"),
    "6-2": t("CI に `%s:test` を組み込んでください",
             "Add `%s:test` to CI") % module_path,
    "6-3": t("ドキュメント生成をセットアップできます（`architecture { title / description }` を書き、`./gradlew %s:katachiDocs` で生成。生成物をコミットするかを決めてください）",
             "You can set up documentation generation (write `architecture { title / description }` and generate with `./gradlew %s:katachiDocs`; decide whether to commit the output)") % module_path,
    "6-4": t("テンプレートからのコード生成をセットアップできます。提案は次の %d 件です（レポートの「テンプレートの提案」）",
             "You can set up code generation from templates. Suggestions: %d (\"Template suggestions\" in the report)"),
}
for i in optional_items:
    if i.get("done"):
        continue
    if i["id"] == "6-4":
        # 当てる Role が無いと判断したなら、何も勧めない
        if not report_templates:
            continue
        print("    - %s" % (HINT["6-4"] % len(report_templates)))
        for tpl in report_templates:
            files = ", ".join(tpl.get("files") or []) or t("(未記入)", "(blank)")
            params = ", ".join(tpl.get("params") or []) or t("なし", "none")
            print(t("        - %s: %s（パラメータ: %s）", "        - %s: %s (parameters: %s)")
                  % (tpl.get("role") or t("(未記入)", "(blank)"), files, params))
        continue
    print("    - %s" % HINT.get(i["id"], i.get("label", i["id"])))
'

cmd_summary() {
	need_python
	require_workdir
	# 置き場所は読むだけ。定義のディレクトリが無くても止めない（scaffold 前に区切ったセッションの再開で使う）。
	resolve_module_dir
	_cl="$WORKDIR/check-list.html"
	[ -f "$_cl" ] || die "$(file_uri "$_cl") がありません。先に init を実行してください。" \
		"$(file_uri "$_cl") not found. Run init first."
	_a="$WORKDIR/tmp/cache/.summary.$$"
	extract_json "$_cl" checklist >"$_a" || die "チェックリストの JSON を読めません。" \
		"Cannot read the JSON of the check list."

	# レポート側の questions も出す。ステップ1で書いたものがユーザに一度も
	# 届かないまま終わる事故があったため（手順書ではステップ3の前に提示させている）。
	_b="$WORKDIR/tmp/cache/.summary-report.$$"
	_rp="$WORKDIR/project-code-base-report.html"
	if [ -f "$_rp" ]; then
		extract_json "$_rp" report >"$_b" 2>/dev/null || printf '{}\n' >"$_b"
	else
		printf '{}\n' >"$_b"
	fi

	# 再開の欄に出す、このスクリプトのパスと定義のディレクトリの有無。
	_cli="$(script_dir)/${0##*/}"
	_def=$(definition_dir)
	_def_exists="no"
	[ -d "$_def" ] && _def_exists="yes"

	# `set -e` の下では、条件に置かないと python の失敗で後始末に届かない。
	if printf '%s\n%s\n%s\n%s' "$PY_LANG" "$SECTION_OF_PY" "$QUESTION_ID_PY" "$SUMMARY_PY" | python3 - "$_a" "$_b" "$MODULE_DIR" "$MODULE_GRADLE_PATH" "$_cli" "$_def" "$_def_exists"; then
		rm -f "$_a" "$_b"
	else
		rm -f "$_a" "$_b"
		die "チェックリストの JSON を読めませんでした。data get check-list で中身を確認してください。" \
			"Could not read the JSON of the check list. Check its content with data get check-list."
	fi
}

# ---------------------------------------------------------------- doctor

# このスクリプトが使うコマンドがそろっているかを、導入を始める前にまとめて確かめる。
# 手順書の 0-1（権限を先に取り切る）がこれを呼ぶ。init も同じ確認を最初にする。
cmd_doctor() {
	while [ $# -gt 0 ]; do
		case "$1" in
		--lang)
			need_value "$1" "${2:-}"
			set_lang_option "$2"
			shift 2
			;;
		-h | --help)
			usage
			return 0
			;;
		*) die "doctor: 知らないオプションです: ${1}" "doctor: unknown option: ${1}" ;;
		esac
	done
	require_commands "$REQ_ALL" "$REC_ALL"
	say "必要なコマンドはそろっています（${REQ_ALL}）。" \
		"All required commands are available (${REQ_ALL})."
	# java はコマンドの有無ではなく、Gradle が使う JDK の版を確かめる（JAVA_HOME だけのこともあるため REQ_* には入れない）。
	check_gradle_jdk
}

# ---------------------------------------------------------------- entry

# 引数のどれかが -h / --help か。自前で --help を読まないサブコマンドの入口で使う。
wants_help() {
	for _wh in "$@"; do
		case "$_wh" in
		-h | --help) return 0 ;;
		esac
	done
	return 1
}

resolve_lang "${1:-}"

# 書き込みのロックは、どのコマンドがどこで止まっても放す。trap はここで 1 回だけ張る
# （関数の中では張らない。張り直すと先の trap が消える）。INT / TERM では放してから終わる。
trap lock_release EXIT
trap 'lock_release; exit 130' INT TERM

# 自前で --help を読まないサブコマンドは、ここで使い方を出す。値として --help を
# 読んで「値がありません」と落ちたり、作業用ディレクトリが無いと言って止まったりしないように。
case "${1:-}" in
data | check | uncheck | warn | lint | compare-violations | verify | summary | guide | test | preferences | answers)
	if wants_help "$@"; then
		usage
		exit 0
	fi
	;;
esac
resolve_docs "${1:-}"

case "${1:-}" in
doctor)
	shift
	cmd_doctor "$@"
	;;
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
guide)
	shift
	cmd_guide "$@"
	;;
test)
	shift
	# 2 = 検査まで届かない、1 = 検査が落ちた。return の値をそのまま終了コードにする。
	cmd_test "$@" || exit $?
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
preferences)
	shift
	cmd_preferences "$@"
	;;
answers)
	shift
	cmd_answers "$@"
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
	die "知らないサブコマンドです: ${1}（--help を見てください）" \
		"Unknown subcommand: ${1} (see --help)"
	;;
esac
