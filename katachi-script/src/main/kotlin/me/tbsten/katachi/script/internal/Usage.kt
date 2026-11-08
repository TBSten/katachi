package me.tbsten.katachi.script.internal

// The --help text: the install kit's usage, rewritten for the katachi command. Hand-written prose,
// not generated from COMMAND_SPECS (a test checks that it names every option there).
// `katachi --help` lists what people type; `katachi agent --help` lists the steps of the install guide (KATACHI-290).

internal fun ScriptContext.usage(): String = pick(USAGE_JA, USAGE_EN)

/** `katachi agent --help`: the [CommandSpec.agent] commands. */
internal fun ScriptContext.agentUsage(): String = pick(AGENT_USAGE_JA, AGENT_USAGE_EN)

private val USAGE_JA: String = """
katachi — katachi の導入と検査を手伝うコマンド

使い方:
  katachi doctor [--lang <en|ja>]
      Gradle が使う JDK の版、プロジェクトの Kotlin の版、git の有無を確かめる。
      JDK が足りなければ直し方を出して 1 を返す。

  katachi scaffold --package <com.example.app> [オプション]
      検査用モジュール（既定 architecture-test/。--module-dir で変える）を作成し、
      ビルドに組み込む。

      --package <pkg>     アプリのパッケージ名（必須）
      --katachi <version> katachi のバージョン（既定: init が記録した版。
                          無ければこの katachi コマンドの版）
      --kotlin <version>  Kotlin のバージョン（既定: プロジェクトから検出）
      --no-konsist        katachi-konsist を使わない構成で生成する（ktFileContent { } が書けない）。
                          assert() に FileConstraintCheck() を渡さないので、gradle { } の hook に
                          書いた fileConstraint も評価されず、test は [UncheckedFileConstraint] で
                          失敗する（How to fix: Pass FileConstraintCheck() to assert()）
      --module-dir <path> 検査用モジュールの置き場所（既定: init が記録した meta.moduleDir。
                          無ければ architecture-test）。指定すると meta.moduleDir を書き換える
      --test-framework <junit5|kotest>
                          テストの雛形と依存（既定: preferences.testFramework。無ければ
                          kotest の依存がプロジェクトにあれば kotest、なければ junit5）
      --kotest-style <fun-spec|string-spec|should-spec|describe-spec|behavior-spec|
                      word-spec|free-spec|feature-spec|expect-spec|annotation-spec>
                          kotest のテストの書き方（既定: fun-spec）。free-spec なら FreeSpec で
                          書く。--test-framework を省くと kotest にする。junit5 とは一緒に使えない
      --deps <catalog|literal>
                          依存の書き方（既定: preferences.dependencyStyle。無ければ
                          gradle/libs.versions.toml があれば catalog）。catalog は toml に
                          無い項目だけを足し、build.gradle.kts は libs.katachi のように参照する。
                          別名が別の用途で使われていると、明示したときは止まり、
                          既定のときは literal にする
      --force             既存の検査用モジュールを上書きする
      --dry-run           何も書かずに、何をするかだけ出力する

  katachi lint
      定義（検査用モジュールの src/test/kotlin）を読み、種類の違うファイルを1つの
      Role に抱えていそうなもの、Gradle のファイルを手書きで宣言した Role（制約を持つものは
      gradle { } の hook への移し方も出す）、平たい group、分けていないファイル、
      documented = false の候補を列挙する。agent check 3-2 / agent check 3-3 でも自動で走る。
      素朴なテキスト走査なので誤検知があり得る。点検の結果では失敗にしない（0 を返す）。
      定義のディレクトリが無ければ（scaffold 前、置き場所の食い違い）1 を返す。

  katachi <ファイル>.katachi.kts [<引数>...]
      katachi のコマンドを Kotlin で並べたスクリプトを実行する。katachi と
      defaultPackage などの既定値は最初から使える（kotlin コマンドが要る）。
      引数はスクリプトの args に入る。

  katachi agent <サブコマンド> ...
      AI エージェントが導入の手順書（katachi agent guide）に従って使うコマンド:
      init / guide / docs / data / add / preferences / answers / check / uncheck /
      warn / test / compare-violations / verify / summary。一覧は katachi agent --help。

  katachi --version
  katachi --help

開発者向けの環境変数:
  KATACHI_MAVEN_LOCAL=1
      ~/.m2 に publish したリリース前の katachi で試すための口。
      agent init に付けると作業用ディレクトリに記録され、以降の scaffold も従う。
      「katachi は 0.3.0 以降」の確認を飛ばし（GitHub の最新を取ってきた
      版では飛ばさない）、scaffold が settings の pluginManagement と
      dependencyResolutionManagement の repositories に mavenLocal() を足す。
      利用者の導入では使わない。~/.m2 へは katachi のリポジトリで
      ./gradlew publishToMavenLocal -Pkatachi.skipSigning として出す。

  KATACHI_DOCS=<URL>
      agent docs が取りに行くガイドの配信元（既定: https://tbsten.github.io/katachi）。手順書とテンプレートは同梱なので使わない。
      ビルド済みのサイト（docs/dist を配信したもの）か配信 URL に向ける。docs/public には
      llms-full.txt などが無い。agent init に付けると作業用ディレクトリに記録され、以降の
      agent docs なども従う（付けずに agent init し直すと本番に戻る）。agent docs は取得元を標準エラーに出す。

scaffold は Gradle のルートディレクトリで実行してください。
""".trimIndent()

private val USAGE_EN: String = """
katachi - helps you set up and check katachi

Usage:
  katachi doctor [--lang <en|ja>]
      Checks the JDK Gradle uses, the project's Kotlin version and whether git is there.
      If the JDK is too old, says how to fix it and returns 1.

  katachi scaffold --package <com.example.app> [options]
      Creates the test module (default architecture-test/; change it with --module-dir)
      and wires it into the build.

      --package <pkg>     The app's package name (required)
      --katachi <version> katachi version (default: the one init recorded,
                          otherwise the version of this katachi command)
      --kotlin <version>  Kotlin version (default: detected from the project)
      --no-konsist        Generate a setup that does not use katachi-konsist (no ktFileContent { }).
                          assert() gets no FileConstraintCheck(), so a fileConstraint written in a
                          gradle { } hook is not evaluated either, and the test fails with
                          [UncheckedFileConstraint] (How to fix: Pass FileConstraintCheck() to assert())
      --module-dir <path> Where the test module goes (default: meta.moduleDir recorded by init,
                          otherwise architecture-test). When given, meta.moduleDir is updated
      --test-framework <junit5|kotest>
                          Test template and dependencies (default: preferences.testFramework;
                          otherwise kotest when the project depends on kotest, else junit5)
      --kotest-style <fun-spec|string-spec|should-spec|describe-spec|behavior-spec|
                      word-spec|free-spec|feature-spec|expect-spec|annotation-spec>
                          How the Kotest test is written (default: fun-spec); free-spec writes a
                          FreeSpec. Without --test-framework it picks kotest. Not with junit5
      --deps <catalog|literal>
                          How dependencies are written (default: preferences.dependencyStyle;
                          otherwise catalog when gradle/libs.versions.toml exists). catalog adds
                          only the missing entries to the toml, and build.gradle.kts refers to
                          them as libs.katachi and so on. When an alias is already used for
                          something else, an explicit --deps catalog stops; the default falls
                          back to literal
      --force             Overwrite an existing test module
      --dry-run           Write nothing; only print what would be done

  katachi lint
      Reads the definition (src/test/kotlin of the test module) and lists Roles that
      seem to hold files of different kinds, hand-written Roles for Gradle files (ones with
      constraints also get the way to move them into a gradle { } hook), flat groups, files
      not split, and candidates for documented = false.
      Also runs on agent check 3-2 / agent check 3-3.
      It is a plain text scan, so false positives are possible. Findings never fail it (returns 0).
      Returns 1 if the definition directory does not exist (before scaffold, or a different location).

  katachi <file>.katachi.kts [<argument>...]
      Runs a script that lines up katachi commands in Kotlin. katachi and the
      defaults such as defaultPackage are there from the start (needs the kotlin
      command). The arguments are the script's args.

  katachi agent <subcommand> ...
      The commands an AI agent runs as it follows the install guide (katachi agent guide):
      init / guide / docs / data / add / preferences / answers / check / uncheck /
      warn / test / compare-violations / verify / summary. See katachi agent --help.

  katachi --version
  katachi --help

Environment variables for developers:
  KATACHI_MAVEN_LOCAL=1
      A hook for trying an unreleased katachi published to ~/.m2.
      Set it on agent init and it is recorded in the working directory; later scaffold runs follow it.
      Skips the "katachi needs 0.3.0 or later" check (not for a version fetched
      as the latest release from GitHub), and scaffold adds mavenLocal() to the
      repositories of pluginManagement and dependencyResolutionManagement in settings.
      Not for real installations. Publish to ~/.m2 from the katachi repository with
      ./gradlew publishToMavenLocal -Pkatachi.skipSigning

  KATACHI_DOCS=<URL>
      Where agent docs fetches the guide from (default: https://tbsten.github.io/katachi). The guide and the templates ship with the command and do not use it.
      Point it at a built site (docs/dist, served) or a served URL; docs/public has no
      llms-full.txt and the like. Set it on agent init and it is recorded in the working directory;
      later commands such as agent docs follow it (running agent init again without it goes back to the
      default). agent docs prints the source URL to standard error.

Run scaffold in the Gradle root directory.
""".trimIndent()

private val AGENT_USAGE_JA: String = """
katachi agent — AI エージェントが導入の手順書に従って使うコマンド

手順書（katachi agent guide）の各ステップがこれらを呼びます。人が導入するときは
katachi doctor / katachi scaffold / katachi lint（katachi --help）を使ってください。

使い方:
  katachi agent init [オプション]
      Gradle のルートであることを確かめ、作業用ディレクトリを作り、
      チェックリストとレポートのテンプレートを配置する。
      最後に KEY=VALUE 形式で結果を出力する（後続のステップがこれを読む）。

      --workdir <path>    作業用ディレクトリを明示する（既定: 自動判定）
      --katachi <version> katachi のバージョンを明示する（既定: この katachi コマンドの版。やり直しでは
                          前回の init が記録した版を使い続ける）
      --offline           ネットワークに出ない（テンプレートと手順書は同梱なので、無くても揃う）
      --force             記入済みのチェックリスト・レポートも取り直す
      --lang <en|ja>      チェックリスト・レポート・このコマンドの出力の言語（既定: en）。
                          作業用ディレクトリに記録され、以降のコマンドが従う
      --module-dir <path> 検査用モジュールの置き場所。Gradle ルートからの相対パス
                          （既定: architecture-test。例: static-analysis/katachi）。
                          チェックリストの meta.moduleDir に記録され、scaffold 以降が従う

      2回目以降の実行では、記入済みのファイルは上書きしません。
      以降のコマンドはプロジェクトのルートで katachi agent <サブコマンド> として実行します
      （作業用ディレクトリは自動で見つける。別の場所からは KATACHI_WORKDIR で指定する）。

  katachi agent guide [<セクション ID>] [--refresh]
      手順書（index.md）をセクションごとに出す。引数なしは目次（<ID>、見出し、行数をタブ区切りで 1 行ずつ）。
      <セクション ID> は見出しの番号（0-1、3-2、5、6-A など。6-1〜6-4 は 6-A〜6-D に読み替える）。
      章（1 など）に番号つきのセクションがあれば、前書きと「含むセクション:」だけを出す。
      各セクションの最後に次のセクションのコマンドを付ける。手順書は katachi コマンドに同梱されている
      （init の前でも読める）。--lang で言語を選ぶ。--refresh で作業用ディレクトリの tmp/cache/index.md を書き直す。

  katachi agent data get <対象> [<キー>] [--id <id>]
      埋め込まれた JSON を標準出力に出す。HTML 本体は読まなくてよい。
      <対象> は check-list / report / ファイルパスのどれか。
      <キー> はドット区切り（meta.moduleDir、questions）。その部分だけを出し、
      無ければ 1 を返す。文字列はそのまま、ほかは JSON で出す。

  katachi agent data set <対象> <json> [--id <id>]
      埋め込まれた JSON を <json> で丸ごと差し替える。

  katachi agent data merge <対象> <json> [--id <id>]
      <json> を既存の内容に深くマージする。部分的な更新はこちらが楽。
      配列は丸ごと置き換わる（追記ではない）。

      set / merge はどちらも、書き込む前に JSON として妥当かを検査し、
      元のファイルを .bak に退避し、結果が壊れていれば自動で巻き戻す。

  katachi agent add <種類> [--<項目> <値> ...]
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

  katachi agent preferences [show]
  katachi agent preferences set <key>=<value> ...
      書き方の方針を表示 / 記録する（手順書 1-3）。show は 1 行 1 キーで、値と
      「推奨 / 記録済み / 検出: <根拠>」を出す。知らないキー・値は止まる。
      key: nestedGroups (yes/no) / moduleDir (パス) / testFramework (junit5/kotest) /
           dependencyStyle (catalog/literal) / constraintNames (plain/numbered) /
           oneRolePerFile (yes/no)
      moduleDir はチェックリストの meta.moduleDir に、ほかは preferences に記録される。
      lint が nestedGroups / oneRolePerFile を、scaffold が testFramework / dependencyStyle を読む。

  katachi agent answers [show] [--target report|check-list]
  katachi agent answers import <json> [--target report|check-list]
  katachi agent answers set <id> <回答> [--note <文>] [--target report|check-list]
      確認事項（questions）への回答を表示 / 取り込む。show は --target が無ければ両方を出す。
      set は id で書き込み先が決まる（q<n> はチェックリスト、cq<n> はレポート。--target と食い違えば止まる）。
      import はブラウザで書き出した JSON（{"target": "report", "answers": {"cq1": {"answer": "...", "note": null}}}）を読み、
      知らない id は飛ばして件数を出す。書き込み先は JSON の target、無ければ --target、それも無ければ id から決める。
      回答は answers（id ごと）に残り、summary が回答済みの件数と未回答だけを出す。

  katachi agent docs [--small] [--refresh]
      ガイド全文（llms-full.txt）を作業用ディレクトリの tmp/cache/ に取得し、
      そのパスを出力する。すでにあれば取りに行かない。

  katachi agent docs --topic <名前> [--refresh]
  katachi agent docs --topics
      ガイドを話題（ページ）ごとに切り出して tmp/cache/topics/<名前>.md に置き、
      そのパスを出力する（layout / role / basic-api / kt-file-content など。--topics で一覧）。
      全文を読まずに済ませるためのもの。古い版の写しは含めない。

  katachi agent docs --api [--refresh]
      API リファレンス（Dokka）のルート索引（api-docs/llms.txt）を取得する。
      主要 API の一覧と、モジュールごとの索引・全文へのリンクを含む。

  katachi agent docs --api <katachi|katachi-konsist> [--refresh]
      指定モジュールの API 全文（シグネチャと KDoc）を取得する。
      API のシグネチャや引数を確かめたいときに使う。

  katachi agent check <項目 id>...
  katachi agent uncheck <項目 id>...
      チェックリストの項目を完了 / 未完了にする。
      知らない id を渡すと、使える id を並べて止まる。

  katachi agent warn <ステップ id> <文言>
  katachi agent warn <ステップ id> --clear
      ステップに警告を付ける / 消す。付けたステップは黄色になり、
      完了していても閉じずに表示される。

  katachi agent compare-violations [<前のログ> <後のログ>]
      ./gradlew <module>:test の出力を2つ比べ、検査結果（違反の件数と中身）が
      変わっていないかを確かめる。定義のリファクタリング（手順書 3-3）の前後に使う。
      既定は <作業用ディレクトリ>/tmp/test-before-refactor.log と test-after-refactor.log。
      [MissingDescription] の警告は、説明を書き足せば減るのが正しいので件数だけ出す。
      変わっていなければ 0、変わっていれば差分を出して 1 を返す。
      どちらかが違反を先頭の数件だけ出したログ（Showing first ...）なら、比べずに 2 を返す。

  katachi agent test [--save <名前>] [-- <gradle の引数>...]
      検査用モジュールのテスト（./gradlew <module>:test）を実行し、要約だけを出す。
      ログの全文は <作業用ディレクトリ>/tmp/test/<UTC 時刻>.log（latest.log にも写し、
      前の latest.log は previous.log に回す）。--save <名前> で tmp/test-<名前>.log にも写す
      （手順書 3-2 は before-refactor、3-3 は after-refactor。compare-violations の既定と同じ）。
      -- より後ろは ./gradlew にそのまま渡す（-Dkatachi.baseline.update=true など）。
      違反がいつも全件ログに出るよう、-Dkatachi.maxViolations=all も渡す（-- の後ろで
      -Dkatachi.maxViolations を渡せば、そちらを使う）。
      要約: JDK、ログのパス、結果の行、[MissingDescription] の件数、通ったときの警告の件数と見出し、
      前回（previous.log）との差分。
      0 = 通った、1 = 検査が失敗した、2 = 検査まで届かない（コンパイルエラー、gradlew 無し、定義ディレクトリ無し）。
      katachi コマンドが Gradle を起動するのはこのコマンドだけ。

  katachi agent verify
      未記入のフィールドと未完了の項目を列挙する。何も残っていなければ
      最後のステップを完了にして 0 を返す。残っていれば 1 を返す。

  katachi agent summary
      ユーザに返す文面を組み立てて出力する。

agent init と scaffold は Gradle のルートディレクトリで実行してください。
ほかの agent コマンドも同じ場所で実行すれば、作業用ディレクトリを自動で見つけます。
""".trimIndent()

private val AGENT_USAGE_EN: String = """
katachi agent - the commands an AI agent runs as it follows the install guide

Each step of the guide (katachi agent guide) calls them. To set katachi up by hand,
use katachi doctor / katachi scaffold / katachi lint (katachi --help).

Usage:
  katachi agent init [options]
      Checks that this is the Gradle root, creates the working directory, and
      places the check list and the report template.
      Prints the result as KEY=VALUE lines at the end (the later steps read them).

      --workdir <path>    Use this working directory (default: chosen automatically)
      --katachi <version> Use this katachi version (default: the version of this katachi command; on a rerun,
                          the version the previous init recorded)
      --offline           Never go to the network (the templates and the guide ship with the command)
      --force             Fetch the check list and the report again, even if filled in
      --lang <en|ja>      Language of the check list, the report and this command's
                          output (default: en). Recorded in the working directory;
                          later commands follow it
      --module-dir <path> Where the test module goes, relative to the Gradle root
                          (default: architecture-test; for example static-analysis/katachi).
                          Recorded as meta.moduleDir in the check list; scaffold and later follow it

      On later runs, files that are already filled in are not overwritten.
      Later commands run as katachi agent <subcommand> in the project root (they find the
      working directory themselves; from elsewhere, point KATACHI_WORKDIR at it).

  katachi agent guide [<section id>] [--refresh]
      Prints the guide (index.md) one section at a time. Without an argument, prints the table of
      contents (one line per section: <id>, heading, number of lines, tab-separated).
      <section id> is the number in the heading (0-1, 3-2, 5, 6-A, ...; 6-1 to 6-4 are read as 6-A to 6-D).
      For a chapter with numbered sections (such as 1), prints only its preamble and "Sections:".
      Each section ends with the command for the next one. The guide ships with the katachi command
      (readable before init). --lang picks the language; --refresh rewrites tmp/cache/index.md in the working directory.

  katachi agent data get <target> [<key>] [--id <id>]
      Prints the embedded JSON. You do not need to read the HTML itself.
      <target> is check-list, report, or a file path.
      <key> is dot-separated (meta.moduleDir, questions). Prints only that part, and
      returns 1 if it does not exist. A string is printed as is; anything else as JSON.

  katachi agent data set <target> <json> [--id <id>]
      Replaces the embedded JSON with <json> as a whole.

  katachi agent data merge <target> <json> [--id <id>]
      Deep-merges <json> into the existing content. Easier for partial updates.
      Arrays are replaced as a whole (not appended to).

      Both set and merge validate the JSON before writing, keep the original
      file as .bak, and roll back automatically if the result is broken.

  katachi agent add <kind> [--<field> <value> ...]
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

  katachi agent preferences [show]
  katachi agent preferences set <key>=<value> ...
      Shows / records how the definition should be written (step 1-3 of the guide). show prints
      one key per line with the value and "recommended / recorded / detected: <evidence>".
      An unknown key or value stops the command.
      key: nestedGroups (yes/no) / moduleDir (path) / testFramework (junit5/kotest) /
           dependencyStyle (catalog/literal) / constraintNames (plain/numbered) /
           oneRolePerFile (yes/no)
      moduleDir is recorded as meta.moduleDir of the check list, the others under preferences.
      lint reads nestedGroups / oneRolePerFile; scaffold reads testFramework / dependencyStyle.

  katachi agent answers [show] [--target report|check-list]
  katachi agent answers import <json> [--target report|check-list]
  katachi agent answers set <id> <answer> [--note <text>] [--target report|check-list]
      Shows / imports the answers to the questions. Without --target, show prints both files.
      set writes where the id belongs (q<n> is the check list, cq<n> the report; a contradicting --target stops it).
      import reads the JSON exported from the browser ({"target": "report", "answers": {"cq1": {"answer": "...", "note": null}}}),
      skips unknown ids and prints the counts. It writes to the target in the JSON, otherwise --target, otherwise where the ids belong.
      Answers are kept under answers (per id); summary prints the number answered and lists only the unanswered ones.

  katachi agent docs [--small] [--refresh]
      Fetches the full guide (llms-full.txt) into tmp/cache/ in the working directory
      and prints its path. Does not fetch again if it is already there.

  katachi agent docs --topic <name> [--refresh]
  katachi agent docs --topics
      Cuts one topic (page) out of the guide into tmp/cache/topics/<name>.md and prints
      its path (layout / role / basic-api / kt-file-content and so on; --topics lists them).
      Saves reading the full text. Copies of older versions are left out.

  katachi agent docs --api [--refresh]
      Fetches the root index of the API reference (Dokka), api-docs/llms.txt.
      It lists the main APIs and links to each module's index and full text.

  katachi agent docs --api <katachi|katachi-konsist> [--refresh]
      Fetches the full API text (signatures and KDoc) of the given module.
      Use it to check an API's signature or parameters.

  katachi agent check <item id>...
  katachi agent uncheck <item id>...
      Marks check list items as done / not done.
      An unknown id stops the command and lists the ids you can use.

  katachi agent warn <step id> <text>
  katachi agent warn <step id> --clear
      Attaches a warning to a step / removes it. A step with a warning turns
      yellow and stays open even when it is done.

  katachi agent compare-violations [<log before> <log after>]
      Compares two outputs of ./gradlew <module>:test and checks that the
      check result (the number and content of violations) has not changed. Use it
      before and after refactoring the definition (step 3-3 of the guide).
      Defaults: <working directory>/tmp/test-before-refactor.log and test-after-refactor.log.
      [MissingDescription] warnings should go down as descriptions are added, so only
      their counts are shown.
      Returns 0 if nothing changed; otherwise prints the difference and returns 1.
      If either log shows only the first few violations (Showing first ...), returns 2 without comparing.

  katachi agent test [--save <name>] [-- <gradle arguments>...]
      Runs the test module's tests (./gradlew <module>:test) and prints only a summary.
      The full log goes to <working directory>/tmp/test/<UTC time>.log (also copied to latest.log;
      the previous latest.log becomes previous.log). --save <name> also copies it to tmp/test-<name>.log
      (before-refactor in step 3-2, after-refactor in step 3-3; the defaults of compare-violations).
      Everything after -- is passed to ./gradlew as is (such as -Dkatachi.baseline.update=true).
      It also passes -Dkatachi.maxViolations=all so that the log always holds every violation
      (give -Dkatachi.maxViolations after -- to use your own value instead).
      Summary: the JDK, the log path, the result line, the number of [MissingDescription], the number
      and headings of warnings when the check passes, and the difference from the previous run (previous.log).
      0 = passed, 1 = the check failed, 2 = the check was not reached (compile error, no gradlew,
      no definition directory). This is the only command of katachi that starts Gradle.

  katachi agent verify
      Lists blank fields and unfinished items. If nothing is left, marks the last
      step as done and returns 0. Otherwise returns 1.

  katachi agent summary
      Builds and prints the message to give back to the user.

Run agent init and scaffold in the Gradle root directory.
Run the other agent commands in the same place and they find the working directory themselves.
""".trimIndent()
