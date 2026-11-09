package me.tbsten.katachi.script.internal

// The --help text: the install kit's usage, rewritten for the katachi command. Hand-written prose,
// not generated from COMMAND_SPECS (a test checks that it names every option there).
// `katachi --help` lists what people type; `katachi agent --help` lists what the katachi skill's steps run (KATACHI-290 / 329).

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
      --katachi <version> katachi のバージョン（既定: この katachi コマンドの版）
      --kotlin <version>  Kotlin のバージョン（既定: プロジェクトから検出）
      --no-konsist        katachi-konsist を使わない構成で生成する（ktFileContent { } が書けない）。
                          assert() に FileConstraintCheck() を渡さないので、gradle { } の hook に
                          書いた fileConstraint も評価されず、test は [UncheckedFileConstraint] で
                          失敗する（How to fix: Pass FileConstraintCheck() to assert()）
      --module-dir <path> 検査用モジュールの置き場所（既定: 作業用ディレクトリのチェックリストの
                          meta.moduleDir。無ければ architecture-test）
      --test-framework <junit5|kotest|kotest-<style>>
                          テストの雛形と依存（既定: preferences.testFramework。無ければ
                          kotest の依存がプロジェクトにあれば kotest、なければ junit5）。
                          kotest は FunSpec で書く。kotest-<style> なら kotest の別の書き方で、
                          style は fun / string / should / describe / behavior / word / free /
                          feature / expect / annotation（例: kotest-free で FreeSpec）
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
      documented = false の候補を列挙する。
      素朴なテキスト走査なので誤検知があり得る。点検の結果では失敗にしない（0 を返す）。
      定義のディレクトリが無ければ（scaffold 前、置き場所の食い違い）1 を返す。

  katachi <ファイル>.katachi.kts [<引数>...]
      katachi のコマンドを Kotlin で並べたスクリプトを実行する。katachi と
      defaultPackage などの既定値は最初から使える（kotlin コマンドが要る）。
      引数はスクリプトの args に入る。

  katachi agent <サブコマンド> ...
      AI エージェントが katachi skill の手順で使うコマンド: test / compare-violations。
      一覧は katachi agent --help。

  katachi --version
  katachi --help

scaffold は Gradle のルートディレクトリで実行してください。
""".trimIndent()

/*
 * Environment variables for developers, kept out of --help (CLAUDE.md: what is for development is not shown in
 * the product). The prerelease skill's scripts set them; people installing katachi never do.
 *
 * KATACHI_MAVEN_LOCAL=1
 *     A hook for trying an unreleased katachi published to ~/.m2. Set it on scaffold. Skips the "katachi needs 0.3.0 or later" check (not for a
 *     version fetched as the latest release from GitHub), and scaffold adds mavenLocal() to the repositories of
 *     pluginManagement and dependencyResolutionManagement in settings. Publish to ~/.m2 from the katachi repository
 *     with ./gradlew publishToMavenLocal -Pkatachi.skipSigning

 */
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
      --katachi <version> katachi version (default: the version of this katachi command)
      --kotlin <version>  Kotlin version (default: detected from the project)
      --no-konsist        Generate a setup that does not use katachi-konsist (no ktFileContent { }).
                          assert() gets no FileConstraintCheck(), so a fileConstraint written in a
                          gradle { } hook is not evaluated either, and the test fails with
                          [UncheckedFileConstraint] (How to fix: Pass FileConstraintCheck() to assert())
      --module-dir <path> Where the test module goes (default: meta.moduleDir in the check list of
                          the working directory, otherwise architecture-test)
      --test-framework <junit5|kotest|kotest-<style>>
                          Test template and dependencies (default: preferences.testFramework;
                          otherwise kotest when the project depends on kotest, else junit5).
                          kotest writes a FunSpec; kotest-<style> writes another Kotest style,
                          one of fun / string / should / describe / behavior / word / free /
                          feature / expect / annotation (kotest-free writes a FreeSpec)
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
      It is a plain text scan, so false positives are possible. Findings never fail it (returns 0).
      Returns 1 if the definition directory does not exist (before scaffold, or a different location).

  katachi <file>.katachi.kts [<argument>...]
      Runs a script that lines up katachi commands in Kotlin. katachi and the
      defaults such as defaultPackage are there from the start (needs the kotlin
      command). The arguments are the script's args.

  katachi agent <subcommand> ...
      The commands an AI agent runs for the katachi skill's steps: test / compare-violations.
      See katachi agent --help.

  katachi --version
  katachi --help

Run scaffold in the Gradle root directory.
""".trimIndent()

private val AGENT_USAGE_JA: String = """
katachi agent — AI エージェントが katachi skill の手順で使うコマンド

katachi skill（gh skill install TBSten/katachi katachi）の手順がこれらを呼びます。人が導入するときは
katachi doctor / katachi scaffold / katachi lint（katachi --help）を使ってください。

使い方:
  katachi agent compare-violations [<前のログ> <後のログ>]
      ./gradlew <module>:test の出力を2つ比べ、検査結果（違反の件数と中身）が
      変わっていないかを確かめる。定義のリファクタリング（katachi skill の setup 3-3）の前後に使う。
      既定は test --save before-refactor / after-refactor で残したログ
      （<作業用ディレクトリ>/tmp/ か、作業用ディレクトリが無ければ build/katachi/ の
      test-before-refactor.log と test-after-refactor.log）。
      [MissingDescription] の警告は、説明を書き足せば減るのが正しいので件数だけ出す。
      変わっていなければ 0、変わっていれば差分を出して 1 を返す。
      どちらかが違反を先頭の数件だけ出したログ（Showing first ...）なら、比べずに 2 を返す。

  katachi agent test [--save <名前>] [-- <gradle の引数>...]
      検査用モジュールのテスト（./gradlew <module>:test）を実行し、要約だけを出す。
      ログの全文は <ログの置き場>/test/<UTC 時刻>.log（latest.log にも写し、前の latest.log は
      previous.log に回す）。ログの置き場は作業用ディレクトリの tmp/、無ければ build/katachi/。
      --save <名前> で <ログの置き場>/test-<名前>.log にも写す
      （setup 3-2 は before-refactor、3-3 は after-refactor。compare-violations の既定と同じ）。
      -- より後ろは ./gradlew にそのまま渡す（-Dkatachi.baseline.update=true など）。
      違反がいつも全件ログに出るよう、-Dkatachi.maxViolations=all も渡す（-- の後ろで
      -Dkatachi.maxViolations を渡せば、そちらを使う）。
      要約: JDK、ログのパス、結果の行、[MissingDescription] の件数、通ったときの警告の件数と見出し、
      前回（previous.log）との差分。
      0 = 通った、1 = 検査が失敗した、2 = 検査まで届かない（コンパイルエラー、gradlew 無し、定義ディレクトリ無し）。
      katachi コマンドが Gradle を起動するのはこのコマンドだけ。

作業用ディレクトリ（katachi skill の手順が作る、check-list.html と tmp/ を持つディレクトリ）は
このコマンドを実行した場所の下から自動で見つけます。別の場所からは KATACHI_WORKDIR で指定します。
無くても動きます。
""".trimIndent()

private val AGENT_USAGE_EN: String = """
katachi agent - the commands an AI agent runs for the katachi skill's steps

The steps of the katachi skill (gh skill install TBSten/katachi katachi) call these. When people set
katachi up themselves, they use katachi doctor / katachi scaffold / katachi lint (katachi --help).

Usage:
  katachi agent compare-violations [<log before> <log after>]
      Compares two outputs of ./gradlew <module>:test and checks that the
      check result (the number and content of violations) has not changed. Use it
      before and after refactoring the definition (step 3-3 of the katachi skill's setup).
      Defaults: the logs test --save before-refactor / after-refactor kept
      (test-before-refactor.log and test-after-refactor.log in <working directory>/tmp/,
      or in build/katachi/ without a working directory).
      [MissingDescription] warnings should go down as descriptions are added, so only
      their counts are shown.
      Returns 0 if nothing changed; otherwise prints the difference and returns 1.
      If either log shows only the first few violations (Showing first ...), returns 2 without comparing.

  katachi agent test [--save <name>] [-- <gradle arguments>...]
      Runs the test module's tests (./gradlew <module>:test) and prints only a summary.
      The full log goes to <log place>/test/<UTC time>.log (also copied to latest.log; the previous
      latest.log becomes previous.log). The log place is tmp/ of the working directory, otherwise
      build/katachi/. --save <name> also copies it to <log place>/test-<name>.log
      (before-refactor in setup 3-2, after-refactor in 3-3; the defaults of compare-violations).
      Everything after -- is passed to ./gradlew as is (such as -Dkatachi.baseline.update=true).
      It also passes -Dkatachi.maxViolations=all so that the log always holds every violation
      (give -Dkatachi.maxViolations after -- to use your own value instead).
      Summary: the JDK, the log path, the result line, the number of [MissingDescription], the number
      and headings of warnings when the check passes, and the difference from the previous run (previous.log).
      0 = passed, 1 = the check failed, 2 = the check was not reached (compile error, no gradlew,
      no definition directory). This is the only command of katachi that starts Gradle.

The working directory (the one with check-list.html and tmp/ that the katachi skill's steps make)
is found below the directory this runs in. From elsewhere, point KATACHI_WORKDIR at it.
Both commands work without it.
""".trimIndent()
