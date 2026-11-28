package me.tbsten.katachi.script.internal

/** One message in both languages; [ScriptContext.pick] chooses when it is printed. */
internal class Msg(val ja: String, val en: String)

/** Every text the katachi command prints, in both languages. MessagesSpec holds the Japanese against docs/prh.yml. */
internal object Messages {
    object CommandSpecs {
        fun subcommandsGot(specName: Any?, names: Any?, value: Any?): Msg = Msg(
            ja = "$specName のサブコマンドは $names です（指定されたのは '$value'）",
            en = "The $specName subcommands are $names (got '$value')",
        )

        fun unknownOptionPutArguments(specName: Any?, arg: Any?): Msg = Msg(
            ja = "$specName: 知らないオプションです: $arg（Gradle に渡す引数は -- の後ろに）",
            en = "$specName: unknown option: $arg (put arguments for Gradle after --)",
        )

        fun unknownOption(specName: Any?, arg: Any?): Msg = Msg(
            ja = "$specName: 知らないオプションです: $arg",
            en = "$specName: unknown option: $arg",
        )

        fun valueMustBe(arg: Any?, expected: Msg, value: Any?): Msg = Msg(
            ja = "$arg は ${expected.ja}です: $value",
            en = "$arg must be ${expected.en}: $value",
        )

        fun oneOf(values: List<String>): Msg = if (values.size == 2) {
            Msg(ja = "${values[0]} か ${values[1]} ", en = "${values[0]} or ${values[1]}")
        } else {
            Msg(ja = values.joinToString(" / ") + " のどれか", en = "one of " + values.joinToString(" / "))
        }
    }

    object GradleFiles {
        fun failedEditRestored(fileUri: Any?, eMessage: Any?): Msg = Msg(
            ja = "$fileUri の書き換えに失敗しました。元に戻しました。（$eMessage）",
            en = "Failed to edit $fileUri. It was restored. ($eMessage)",
        )

        fun aliasTakenVersion(alias: Any?, content: Any?): Msg = Msg(
            ja = "別名 $alias が別の用途で使われています（[versions] $alias = \"$content\"）",
            en = "alias $alias is used for something else ([versions] $alias = \"$content\")",
        )

        fun aliasTakenItem(alias: Any?, table: Any?, other: Any?): Msg = Msg(
            ja = "別名 $alias が別の用途で使われています（[$table] $alias → $other）",
            en = "alias $alias is used for something else ([$table] $alias → $other)",
        )
    }

    object Doctor {
        val theseCommandsNotFound: Msg = Msg(
            ja = "次のコマンドが見つかりません。無くても進めますが、あったほうが確実です:",
            en = "These commands were not found. You can go on without them, but it is safer to have them:",
        )

        val xcodeSelectInstallBrew: Msg = Msg(
            ja = "macOS: xcode-select --install か brew install git / Debian・Ubuntu: sudo apt-get install git / Fedora: sudo dnf install git / Alpine: apk add git",
            en = "macOS: xcode-select --install or brew install git / Debian, Ubuntu: sudo apt-get install git / Fedora: sudo dnf install git / Alpine: apk add git",
        )

        val withoutGitCommandCannot: Msg = Msg(
            ja = "git が無いと Git 管理下かを判定できず、未追跡のファイルの確認も飛ばします。",
            en = "Without git, the command cannot tell whether the project is under Git, and skips the check for untracked files.",
        )

        fun doctorUnknownOption(it: Any?): Msg = Msg(
            ja = "doctor: 知らないオプションです: $it",
            en = "doctor: unknown option: $it",
        )

        fun everythingNeedsAvailableKatachi(scriptVersionCurrent: Any?): Msg = Msg(
            ja = "必要なものはそろっています（katachi $scriptVersionCurrent）。",
            en = "Everything this needs is available (katachi $scriptVersionCurrent).",
        )

        fun somethingNeedsAttention(scriptVersionCurrent: Any?): Msg = Msg(
            ja = "対応が必要なものがあります（katachi $scriptVersionCurrent）。下の [!]・[x] を見てください。",
            en = "Something needs attention (katachi $scriptVersionCurrent). See the [!] and [x] below.",
        )

        fun kotlin(version: Any?, fileUri: Any?, tooOld: Boolean): Msg = if (tooOld) {
            Msg(
                ja = "Kotlin: $version（古すぎます。$fileUri から）",
                en = "Kotlin: $version (too old; from $fileUri )",
            )
        } else {
            Msg(
                ja = "Kotlin: $version（$fileUri から）",
                en = "Kotlin: $version (from $fileUri )",
            )
        }

        fun langMustEnJa(value: Any?): Msg = Msg(
            ja = "--lang は en か ja です（指定されたのは '$value'）。",
            en = "--lang must be en or ja (got '$value').",
        )
    }

    object ScriptFile {
        fun unknownInternalSubcommand(argsFirstOrNull: Any?): Msg = Msg(
            ja = "知らない internal サブコマンドです: $argsFirstOrNull",
            en = "Unknown internal subcommand: $argsFirstOrNull",
        )

        fun noSuchScript(fileUriOfScript: Any?): Msg = Msg(
            ja = "スクリプトが見つかりません: $fileUriOfScript ",
            en = "No such script: $fileUriOfScript ",
        )

        val kotlinNotPathInstall: Msg = Msg(
            ja = "kotlin が PATH にありません。brew install kotlin で入れてください。",
            en = "kotlin is not on PATH. Install it with: brew install kotlin",
        )
    }

    object Lint {
        fun unknownOption(it: Any?): Msg = Msg(
            ja = "lint: 知らないオプションです: $it",
            en = "lint: unknown option: $it",
        )

        val aiAgentInstructions: Msg = Msg(
            ja = "AI エージェント向けの指示",
            en = "AI agent instructions",
        )

        val staticAnalysisConfig: Msg = Msg(
            ja = "静的解析の設定",
            en = "static analysis config",
        )

        val filesDifferentKindsShare: Msg = Msg(
            ja = "種類の違うファイルが同居しています: ",
            en = "Files of different kinds share this Role: ",
        )

        val groupsPerToolConfig: Msg = Msg(
            ja = "ツールごとの設定ディレクトリを anyFile() / ignore() でまとめています: ",
            en = "Groups per-tool config directories with anyFile() / ignore(): ",
        )

        val declaresMainTestFiles: Msg = Msg(
            ja = "本体（main）とテスト（test）のファイルを同じ Role で宣言しています",
            en = "Declares main and test files in the same Role",
        )

        fun roleCheckNoRole(rolesSize: Any?): Msg = Msg(
            ja = "Role の点検: 種類の違うファイルを抱えていそうな Role は見つかりませんでした（$rolesSize 件を走査）。",
            en = "Role check: no role seems to hold files of different kinds ($rolesSize scanned).",
        )

        val roleCheckNeverFails: Msg = Msg(
            ja = "== Role の点検（失敗にはしません） ================================",
            en = "== Role check (never fails) ======================================",
        )

        fun definition(uriRoot: Any?): Msg = Msg(
            ja = "定義: $uriRoot",
            en = "Definition: $uriRoot",
        )

        fun rolesSeemHoldFiles(warningsSize: Any?, rolesSize: Any?): Msg = Msg(
            ja = "種類の違うファイルを1つの Role に抱えていそうなものが $warningsSize 件あります（$rolesSize 件を走査）。",
            en = "Roles that seem to hold files of different kinds: $warningsSize ($rolesSize scanned).",
        )

        val roleNameRoleStands: Msg = Msg(
            ja = "Role（\"Name\".role { }）は1種類のファイルを表し、種類の違うものを束ねるのは group です。\n" +
                "katachi skill の setup 3-2 の「1 つの Role = 1 種類のファイル」の3つの問いで見直し、分けるべきものは\n" +
                "種類ごとの Role に分けて group で束ねてください。\n" +
                "見直して1種類だと判断したものは、そのままでかまいません。",
            en = "A Role (\"Name\".role { }) stands for one kind of file; group is what bundles different kinds.\n" +
                "Review them with the three questions of \"one Role = one kind of file\" in step 3-2 of the katachi skill's setup,\n" +
                "and split what should be split into one Role per kind, bundled with group.\n" +
                "If you review a Role and decide it is one kind, leave it.",
        )

        fun gradleFilesDeclaredHand(names: Any?): Msg = Msg(
            ja = "Gradle のファイルを手書きの Role で宣言しています（Role: $names）。",
            en = "Gradle files are declared in hand-written Roles (Roles: $names).",
        )

        val gradleDeclaresWrapperSettings: Msg = Msg(
            ja = "gradle() が wrapper・settings・ビルドスクリプト・gradle.properties・\n" +
                "version catalog を種類ごとの Role に分けて宣言します（import me.tbsten.katachi.dsl.gradle.*）。\n" +
                "これらの Role を消して gradle() の1行に置き換えてください。buildSrc や includeBuild した\n" +
                "ビルドは gradle() の対象外なので、それだけは自分の Role に残します（この点検もその下は数えません）。",
            en = "gradle() declares the wrapper, settings, build scripts, gradle.properties\n" +
                "and the version catalog as one Role per kind (import me.tbsten.katachi.dsl.gradle.*).\n" +
                "Remove these Roles and replace them with the single line gradle(). buildSrc and included\n" +
                "builds are not covered by gradle(), so keep only those in your own Roles (this check skips them too).",
        )

        val gradleFilesInConstrainedRoles: Msg = Msg(
            ja = "Gradle のファイルを手書きの Role で宣言し、その Role に制約を付けています:",
            en = "Gradle files are declared in hand-written Roles that also carry constraints:",
        )

        val replacePlacementSingleLine: Msg = Msg(
            ja = "配置は gradle() の 1 行に置き換え、制約は gradle { buildScript { } }（settings なら settingsScript { }、\n" +
                "catalog なら versionCatalog { }）に移してください。buildScript { } の制約は、ルートを含む全モジュールの\n" +
                "build.gradle(.kts) を subject.files に受け取ります。1 つのモジュールだけを見るなら、制約の中で\n" +
                "subject.files をパスで絞ってください（例: subject.files.filter { it == \"app/build.gradle.kts\" }）。\n" +
                "gradle { } の中に katachi の Role と同じ名前の Role（\"BuildScript\".role { } など）を書くと重複の例外に、\n" +
                "gradle { } の外の Role でビルドファイルを宣言すると BuildScript と同じファイルを取り合って\n" +
                "[AmbiguousLayout] になります。制約は hook に書いてください。",
            en = "Replace the placement with the single line gradle() and move the constraints into\n" +
                "gradle { buildScript { } } (settingsScript { } for settings, versionCatalog { } for the catalog).\n" +
                "A buildScript { } constraint receives the build.gradle(.kts) of every module, the root included, in\n" +
                "subject.files. To look at one module only, narrow subject.files by path inside the constraint\n" +
                "(for example subject.files.filter { it == \"app/build.gradle.kts\" }).\n" +
                "A Role inside gradle { } with the same name as one of katachi's (\"BuildScript\".role { } and so on) is a\n" +
                "duplicate declaration error, and a Role outside gradle { } that declares a build file claims the same\n" +
                "file as BuildScript and becomes [AmbiguousLayout]. Write the constraints in the hook.",
        )

        fun flatGroupsMoreRoles(flatGroupsSize: Any?): Msg = Msg(
            ja = "平たい group（直下に Role が 8 個以上あり、group を含まない）が $flatGroupsSize 件あります（preferences.nestedGroups = yes）。",
            en = "Flat groups (8 or more Roles directly inside, no nested group): $flatGroupsSize (preferences.nestedGroups = yes).",
        )

        fun groupRoles(uriG: Any?, gLine: Any?, gName: Any?, gDirectRoles: Any?): Msg = Msg(
            ja = "  $uriG:$gLine group \"$gName\"（Role $gDirectRoles 個）",
            en = "  $uriG:$gLine group \"$gName\" ($gDirectRoles Roles)",
        )

        val nestGroupsOrderModule: Msg = Msg(
            ja = "group は「モジュール → 層 → 種類」の順で入れ子にし、1 つの group に Role を 8 個以上並べないでください（並ぶなら group を切る）。",
            en = "Nest groups in the order module -> layer -> kind, and do not put 8 or more Roles in one group (split it when they pile up).",
        )

        fun lowercaseNames(size: Any?): Msg = Msg(
            ja = "小文字で始まる Role・group の名前が $size 件あります。",
            en = "Role and group names starting with a lowercase letter: $size.",
        )

        fun lowercaseName(path: Any?, line: Any?, kind: Any?, name: Any?, suggestion: Any?): Msg = Msg(
            ja = "  $path:$line $kind \"$name\"（→ \"$suggestion\"）",
            en = "  $path:$line $kind \"$name\" (-> \"$suggestion\")",
        )

        val nameInUpperCamelCase: Msg = Msg(
            ja = "Role と group の名前は katachi の Gradle group と同じく UpperCamelCase にしてください（\"Feature\".group { }）。\n" +
                "名前は生成ドキュメントや katachiTemplate の template=Group.Role にそのまま出ます。定義を分けているなら、\n" +
                "ファイル名（<Group>Group.kt）と関数名もそろえます。ディレクトリ名は先頭だけ小文字のままです（katachi skill の setup 3-3）。",
            en = "Name Roles and groups in UpperCamelCase, like katachi's own Gradle group (\"Feature\".group { }).\n" +
                "The name shows up as it is in the generated docs and in katachiTemplate's template=Group.Role. If the definition\n" +
                "is split, keep the file names (<Group>Group.kt) and functions in line; directory names keep a lowercase first letter (step 3-3 of the katachi skill's setup).",
        )

        fun filesNotSplitMore(unsplitFilesSize: Any?): Msg = Msg(
            ja = "分けていないファイル（1 ファイルに Role か group が 2 つ以上）が $unsplitFilesSize 件あります（preferences.oneRolePerFile = yes）。",
            en = "Files not split (2 or more Roles or groups in one file): $unsplitFilesSize (preferences.oneRolePerFile = yes).",
        )

        val writeOneRolePer: Msg = Msg(
            ja = "1 Role 1 ファイルで書き、group はディレクトリで表してください（katachi skill の setup 3-3）。",
            en = "Write one Role per file and express groups as directories (step 3-3 of the katachi skill's setup).",
        )

        fun candidatesDocumentedFalseReview(documentedCandidatesSize: Any?): Msg = Msg(
            ja = "documented = false の候補が $documentedCandidatesSize 件あります（生成ドキュメントに載せる価値があるかを見直してください）。",
            en = "Candidates for documented = false: $documentedCandidatesSize (review whether they belong in the generated docs).",
        )

        val setDocumentedFalseGenerated: Msg = Msg(
            ja = "documented = false にするもの: (1) 生成物、(2) ビルド・ツールの設定（.editorconfig、CI の yaml、lint の設定）、\n" +
                "(3) 1 ファイルしかない補助（README、LICENSE、.gitignore）。利用者が読んで役に立つ Role だけを生成ドキュメントに載せます。",
            en = "Set documented = false on: (1) generated output, (2) build and tool configuration (.editorconfig, CI yaml,\n" +
                "lint config), (3) single-file helpers (README, LICENSE, .gitignore). Only Roles a reader benefits from belong in the generated docs.",
        )

        fun kindFiles(kind: Any?, names: Any?): Msg = Msg(
            ja = "$kind（$names）",
            en = "$kind ($names)",
        )

        fun gradleRoleHook(path: Any?, line: Any?, role: Any?, file: Any?, hook: Any?): Msg = Msg(
            ja = "  $path:$line Role \"$role\"（$file → gradle { $hook }）",
            en = "  $path:$line Role \"$role\" ($file -> gradle { $hook })",
        )

        fun roleWhat(path: Any?, line: Any?, role: Any?, what: Any?): Msg = Msg(
            ja = "  $path:$line Role \"$role\"（$what）",
            en = "  $path:$line Role \"$role\" ($what)",
        )

        fun evaluatingDefinition(command: Any?): Msg = Msg(
            ja = "定義を Gradle で評価しています: $command（初回は時間がかかります）",
            en = "Evaluating the definition with Gradle: $command (the first run takes a while)",
        )

        fun gradlewMissing(rootUri: Any?): Msg = Msg(
            ja = "lint: プロジェクトのルートに gradlew がありません（$rootUri）。lint は定義を Gradle で評価した結果を読むので、" +
                "プロジェクトの ./gradlew が要ります。",
            en = "lint: there is no gradlew in the project root ($rootUri). lint reads the definition as Gradle evaluates it, " +
                "so it needs the project's ./gradlew.",
        )

        fun definitionTaskMissing(module: Any?): Msg = Msg(
            ja = "lint: $module に katachiInternalDefinitionJson のタスクがありません。検査用モジュールに katachi の Gradle プラグイン" +
                "（me.tbsten.katachi、0.3 以降）を適用してください。モジュールの置き場所が違うなら --module-dir <パス> で指定します。",
            en = "lint: $module has no katachiInternalDefinitionJson task. Apply katachi's Gradle plugin " +
                "(me.tbsten.katachi, 0.3 or later) to the verification module. If the module is somewhere else, give it with --module-dir <path>.",
        )

        fun definitionNotEvaluated(command: Any?, status: Any?, details: Any?): Msg = Msg(
            ja = "lint: 定義を Gradle で評価できなかったので、点検していません（$command、終了コード $status）。\n" +
                "$details\n" +
                "定義を直してから、もう一度 katachi lint を実行してください。",
            en = "lint: the definition could not be evaluated by Gradle, so nothing was checked ($command, exit code $status).\n" +
                "$details\n" +
                "Fix the definition, then run katachi lint again.",
        )

        fun compileErrors(count: Any?, lines: Any?): Msg = Msg(
            ja = "コンパイルエラー（$count 件）:\n$lines",
            en = "Compile errors ($count):\n$lines",
        )

        fun moreErrors(count: Any?): Msg = Msg(
            ja = "  ほか $count 件",
            en = "  and $count more",
        )

        fun gradleOutputTail(lines: Any?): Msg = Msg(
            ja = "Gradle の出力（末尾）:\n$lines",
            en = "The end of Gradle's output:\n$lines",
        )

        val jsonNotWritten: Msg = Msg(
            ja = "このコマンドの実行で書かれたファイルがありません",
            en = "no file was written by this run",
        )

        fun definitionJsonUnreadable(path: Any?, reason: Any?): Msg = Msg(
            ja = "lint: Gradle が書いた定義の JSON を読めませんでした（$path）: $reason",
            en = "lint: could not read the definition JSON Gradle wrote ($path): $reason",
        )

        fun definitionJsonTooNew(version: Any?, supported: Any?): Msg = Msg(
            ja = "lint: 定義の JSON のバージョン（schemaVersion $version）がこの katachi コマンドの読めるバージョン（$supported）より新しいです。" +
                "katachi コマンドを更新してください。",
            en = "lint: the definition JSON's schemaVersion ($version) is newer than this katachi command reads ($supported). " +
                "Update the katachi command.",
        )
    }

    object Probe {
        fun settingsGradleKtsSettings(rootUri: Any?): Msg = Msg(
            ja = "settings.gradle.kts / settings.gradle が見つかりません。Gradle のルートディレクトリで実行してください（いまいるのは $rootUri）。",
            en = "settings.gradle.kts / settings.gradle not found. Run this in the Gradle root directory (you are in $rootUri).",
        )

        fun kotlinGradlePluginNeeds(min: Any?): Msg = Msg(
            ja = "Kotlin Gradle Plugin $min は Gradle $KGP_MIN_GRADLE 以上が要ります。Gradle のバージョンを読めなかったので、足りなければ Gradle も上げます。",
            en = "Kotlin Gradle Plugin $min needs Gradle $KGP_MIN_GRADLE or later. The Gradle version could not be read; upgrade Gradle too if it is older.",
        )

        fun gradleNeedsUpgradingToo(min: Any?, gradle: Any?): Msg = Msg(
            ja = "Gradle も上げる必要があります。Kotlin Gradle Plugin $min は Gradle $KGP_MIN_GRADLE 以上が要り、このプロジェクトは $gradle です（gradle/wrapper/gradle-wrapper.properties）。",
            en = "Gradle needs upgrading too. Kotlin Gradle Plugin $min needs Gradle $KGP_MIN_GRADLE or later, and this project uses $gradle (gradle/wrapper/gradle-wrapper.properties).",
        )

        fun gradleRecentEnoughKotlin(gradle: Any?, min: Any?): Msg = Msg(
            ja = "Gradle（$gradle）はそのままで足ります（Kotlin Gradle Plugin $min は Gradle $KGP_MIN_GRADLE 以上）。",
            en = "Gradle ($gradle) is recent enough (Kotlin Gradle Plugin $min needs Gradle $KGP_MIN_GRADLE or later).",
        )

        val askUserWhenKotlin: Msg = Msg(
            ja = "ユーザに確認してください（katachi skill の setup 0-2 の「Kotlin が古いとき」）。",
            en = "Ask the user (\"When Kotlin is too old\" in step 0-2 of the katachi skill's setup).",
        )

        val scaffoldStepWillStop: Msg = Msg(
            ja = "このままでは 2-1 の scaffold で止まります。ステップ 1 の解析に入る前に、ユーザに確認してください（katachi skill の setup 0-2 の「Kotlin が古いとき」）。",
            en = "As it is, scaffold in step 2-1 will stop. Ask the user before starting the analysis in step 1 (\"When Kotlin is too old\" in step 0-2 of the katachi skill's setup).",
        )

        fun projectUsesKotlinKatachi(version: Any?, min: Any?, whenLine: Any?, gradleLine: Any?): Msg = Msg(
            ja = "このプロジェクトの Kotlin は $version です。katachi は Kotlin $min 以降が要ります（古いコンパイラは katachi の metadata を読めず、architecture などがすべて Unresolved reference になる）。\n" +
                "    $whenLine\n" +
                "    Kotlin を上げるのは既存のビルドの変更なので、同意なしに上げないこと。同意を得たら Kotlin を $min 以降に上げます。\n" +
                "    $gradleLine",
            en = "This project uses Kotlin $version. katachi needs Kotlin $min or later (older compilers cannot read katachi's metadata, and every symbol such as architecture becomes an Unresolved reference).\n" +
                "    $whenLine\n" +
                "    Upgrading Kotlin changes the existing build, so do not do it without the user's consent. Once they agree, upgrade Kotlin to $min or later.\n" +
                "    $gradleLine",
        )

        fun projectUsesKotlinReadFrom(version: Any?, fileUri: Any?, min: Any?, whenLine: Any?, gradleLine: Any?): Msg = Msg(
            ja = """
                このプロジェクトの Kotlin は $version です（$fileUri から読みました）。katachi は Kotlin $min 以降が要ります（古いコンパイラは katachi の metadata を読めず、architecture などがすべて Unresolved reference になる）。
                    このファイルがプロジェクトの Kotlin のバージョンを決めていないなら（includeBuild したビルドや buildSrc など）、Kotlin は上げずに、scaffold に --kotlin <バージョン> を付けて実際のバージョンを渡してください。
                    $whenLine
                    Kotlin を上げるのは既存のビルドの変更なので、同意なしに上げないこと。同意を得たら Kotlin を $min 以降に上げます。
                    $gradleLine
            """.trimIndent(),
            en = """
                This project uses Kotlin $version (read from $fileUri ). katachi needs Kotlin $min or later (older compilers cannot read katachi's metadata, and every symbol such as architecture becomes an Unresolved reference).
                    If that file does not set the project's Kotlin version (an included build or buildSrc, for example), pass the actual version to scaffold with --kotlin <version> instead of upgrading Kotlin.
                    $whenLine
                    Upgrading Kotlin changes the existing build, so do not do it without the user's consent. Once they agree, upgrade Kotlin to $min or later.
                    $gradleLine
            """.trimIndent(),
        )

        val toolchainversionGradleGradleDaemon: Msg = Msg(
            ja = "gradle/gradle-daemon-jvm.properties の toolchainVersion",
            en = "toolchainVersion in gradle/gradle-daemon-jvm.properties",
        )

        fun javaPath(onPath: Any?): Msg = Msg(
            ja = "PATH の java（$onPath）",
            en = "java on PATH ($onPath)",
        )

        fun javaPathMacOs(onPath: Any?, javaHomeTool: Any?, home: Any?): Msg = Msg(
            ja = "PATH の java（$onPath。$javaHomeTool が選ぶ JDK は $home）",
            en = "java on PATH ($onPath, which runs the JDK $javaHomeTool picks: $home)",
        )

        val couldNotFindJdk: Msg = Msg(
            ja = "Gradle を動かす JDK を見つけられませんでした（JAVA_HOME も PATH の java もありません）。katachi は JDK $JDK_MIN_MAJOR 以上が要ります。",
            en = "Could not find the JDK that runs Gradle (there is no JAVA_HOME and no java on PATH). katachi needs JDK $JDK_MIN_MAJOR or later.",
        )

        fun couldNotFindJdkMacOs(onPath: Any?, javaHomeTool: Any?): Msg = Msg(
            ja = "Gradle を動かす JDK を見つけられませんでした（シェルに JAVA_HOME が無く、PATH の $onPath が動かす JDK も $javaHomeTool で見つかりません）。" +
                "katachi は JDK $JDK_MIN_MAJOR 以上が要ります。",
            en = "Could not find the JDK that runs Gradle (the shell has no JAVA_HOME, and $javaHomeTool finds no JDK for java on PATH ($onPath) to run). " +
                "katachi needs JDK $JDK_MIN_MAJOR or later.",
        )

        fun invalidJavaHome(javaHome: Any?): Msg = Msg(
            ja = "JAVA_HOME（$javaHome）に bin/java がありません。./gradlew も「JAVA_HOME is set to an invalid directory」で止まります。\n" +
                "    JAVA_HOME を JDK $JDK_MIN_MAJOR 以上のディレクトリに向けるか、unset JAVA_HOME してから、もう一度実行してください。",
            en = "JAVA_HOME ($javaHome) has no bin/java, so ./gradlew stops too (\"JAVA_HOME is set to an invalid directory\").\n" +
                "    Point JAVA_HOME at a JDK $JDK_MIN_MAJOR or later directory, or run unset JAVA_HOME, then run this again.",
        )

        fun couldNotReadVersion(from: Any?): Msg = Msg(
            ja = "Gradle を動かす JDK のバージョンを読めませんでした（$from）。katachi は JDK $JDK_MIN_MAJOR 以上が要ります。",
            en = "Could not read the version of the JDK that runs Gradle ($from). katachi needs JDK $JDK_MIN_MAJOR or later.",
        )

        fun jdkRunsGradleKatachi(jdkMajor: Any?, source: Msg): Msg = Msg(
            ja = "Gradle を動かす JDK が $jdkMajor です（${source.ja}）。katachi は JDK $JDK_MIN_MAJOR 以上が要ります。\n" +
                "    JAVA_HOME を JDK $JDK_MIN_MAJOR 以上に向けるか、org.gradle.java.home（gradle.properties）で JDK $JDK_MIN_MAJOR 以上を\n" +
                "    指定してから、もう一度実行してください。",
            en = "The JDK that runs Gradle is $jdkMajor (${source.en}). katachi needs JDK $JDK_MIN_MAJOR or later.\n" +
                "    Point JAVA_HOME at JDK $JDK_MIN_MAJOR or later, or set org.gradle.java.home in gradle.properties to one,\n" +
                "    then run this again.",
        )

        fun katachiRunsOnJdk(message: Msg, major: Any?, home: Any?, quotedHome: Any?): Msg = Msg(
            ja = "${message.ja}\n" +
                "    katachi は JDK $major（$home）で動いています。Gradle にもこの JDK を使うなら、シェルで export JAVA_HOME=$quotedHome としてから実行し直してください。",
            en = "${message.en}\n" +
                "    katachi runs on JDK $major ($home). To use this JDK for Gradle too, run export JAVA_HOME=$quotedHome in the shell, then run this again.",
        )

        fun gradleSJdk(jdkMajor: Any?, source: Msg): Msg = Msg(
            ja = "Gradle の JDK: $jdkMajor（${source.ja}）",
            en = "Gradle's JDK: $jdkMajor (${source.en})",
        )

        fun toolchainFrom(bestFirst: Any?, bestSecond: Any?): Msg = Msg(
            ja = "$bestFirst（$bestSecond の設定から）",
            en = "$bestFirst (from $bestSecond)",
        )

        fun notWrittenExistingToolchain(oldFirst: Any?, oldSecond: Any?): Msg = Msg(
            ja = "書かない（既存の toolchain は $oldFirst（$oldSecond）で、katachi の要る $JDK_MIN_MAJOR 未満のため使わない。Gradle の JDK を使う）",
            en = "not written (the existing toolchain is $oldFirst ($oldSecond), below the $JDK_MIN_MAJOR katachi needs, so it is not used; Gradle's JDK is used)",
        )

        fun notWrittenSetsToolchain(unknown: Any?): Msg = Msg(
            ja = "書かない（$unknown に toolchain の設定はあるが、N を読み取れなかった。Gradle の JDK を使う）",
            en = "not written ($unknown sets a toolchain but N could not be read; Gradle's JDK is used)",
        )

        val notWrittenProjectSets: Msg = Msg(
            ja = "書かない（プロジェクトに toolchain の設定が無い。Gradle の JDK を使う）",
            en = "not written (the project sets no toolchain; Gradle's JDK is used)",
        )

        fun katachiTooOldGuide(version: Any?, min: Any?): Msg = Msg(
            ja = "katachi $version はこの手順には古すぎます。katachi skill の手順とこのコマンドが作る構成は katachi $min の機能（\"Name\".role { } など）で書いてあるため、$min 以降が必要です。\n" +
                "    $KATACHI_RELEASES_PAGE で $min 以降が出ているかを確かめ、--katachi <version> で指定してやり直してください。",
            en = "katachi $version is too old for these steps. The katachi skill's steps and the setup this command creates are written with katachi $min features (such as \"Name\".role { }), so they need $min or later.\n" +
                "    Check $KATACHI_RELEASES_PAGE for $min or later, and run again with --katachi <version>.",
        )

        fun gradleJavaHome(gradleHome: Any?): Msg = Msg(
            ja = "org.gradle.java.home（$gradleHome）",
            en = "org.gradle.java.home ($gradleHome)",
        )

        fun javaHome(javaHome: Any?): Msg = Msg(
            ja = "JAVA_HOME（$javaHome）",
            en = "JAVA_HOME ($javaHome)",
        )
    }

    object RunScript {
        /** `katachi agent`, `katachi agent test`, `katachi test`, `katachi compare-violations` (KATACHI-429). */
        fun removedCheckCommand(command: Any?): Msg = Msg(
            ja = "$command は無くなりました。検査は ./gradlew <検査用モジュール>:test を直接実行します" +
                "（手順は katachi skill: gh skill install TBSten/katachi katachi か npx skills add TBSten/katachi --skill katachi で入れる）",
            en = "$command was removed. Run the check with ./gradlew <verification module>:test directly " +
                "(the steps are in the katachi skill: get it with gh skill install TBSten/katachi katachi or npx skills add TBSten/katachi --skill katachi)",
        )

        fun removedForTheSkill(command: Any?): Msg = Msg(
            ja = "$command は無くなりました。導入と定義の更新は katachi skill の手順で進めます" +
                "（gh skill install TBSten/katachi katachi か npx skills add TBSten/katachi --skill katachi で入れる）",
            en = "$command was removed. Installing katachi and updating the definition follow the katachi skill's steps now " +
                "(get it with gh skill install TBSten/katachi katachi or npx skills add TBSten/katachi --skill katachi)",
        )

        fun scriptNeedsExtension(file: Any?): Msg = Msg(
            ja = "$file はスクリプトとして実行できません。katachi のスクリプトのファイル名は .katachi.kts で終わる必要があります（例: ${CLI_COMMAND}-setup.katachi.kts）",
            en = "$file cannot run as a script. The file name of a katachi script has to end with .katachi.kts (for example: $CLI_COMMAND-setup.katachi.kts)",
        )

        fun langEnvNotReadable(value: Any?, used: Any?): Msg = Msg(
            ja = "KATACHI_LANG=$value は読めません（ja か en を指定してください）。$used で出力します。",
            en = "KATACHI_LANG=$value is not readable (use ja or en). Printing in $used.",
        )

        fun unknownSubcommandSeeHelp(command: Any?): Msg = Msg(
            ja = "知らないサブコマンドです: $command（--help を見てください）",
            en = "Unknown subcommand: $command (see --help)",
        )
    }

    object Scaffold {
        fun unknownOption(it: Any?): Msg = Msg(
            ja = "scaffold: 知らないオプションです: $it",
            en = "scaffold: unknown option: $it",
        )

        val packageRequiredExamplePackage: Msg = Msg(
            ja = "--package が必要です（例: --package com.example.app）",
            en = "--package is required (for example: --package com.example.app)",
        )

        fun packageNotValidPackage(pkg: Any?): Msg = Msg(
            ja = "--package がパッケージ名として不正です: $pkg",
            en = "--package is not a valid package name: $pkg",
        )

        fun packageHasKotlinKeyword(pkg: Any?, keyword: Any?): Msg = Msg(
            ja = "--package に Kotlin の予約語（$keyword）が含まれています: $pkg。パッケージ名の途中には使えないので、別の名前にしてください。",
            en = "--package has the Kotlin keyword $keyword in it: $pkg. A keyword cannot be a package name segment, so use another name.",
        )

        fun moduleDirBlockedByFile(blockedUri: Any?, moduleDirUri: Any?): Msg = Msg(
            ja = "検査用モジュールを $moduleDirUri/ に作れません。$blockedUri がディレクトリではないためです（--force を付けても上書きしません）。そのファイルを動かすか、--module-dir で別の置き場所を指定してください。",
            en = "Cannot create the verification module in $moduleDirUri/: $blockedUri is not a directory (--force does not overwrite it). Move that file, or pass another place with --module-dir.",
        )

        fun fileBlockedByDirectory(blockedUri: Any?, moduleDirUri: Any?): Msg = Msg(
            ja = "$blockedUri はディレクトリなので、$moduleDirUri/ の検査用モジュールのファイルとして書けません（--force を付けても上書きしません）。そのディレクトリを動かすか、--module-dir で別の置き場所を指定してください。",
            en = "$blockedUri is a directory, so it cannot be written as a file of the verification module in $moduleDirUri/ (--force does not overwrite it). Move that directory, or pass another place with --module-dir.",
        )

        fun moduleDirDiffersWhat(moduleDir: Any?, moduleRecorded: Any?): Msg = Msg(
            ja = "--module-dir $moduleDir はチェックリストの記録（meta.moduleDir = $moduleRecorded）と違います。チェックリストのほうも $moduleDir に直してください。",
            en = "--module-dir $moduleDir differs from the check list (meta.moduleDir = $moduleRecorded). Change the check list to $moduleDir too.",
        )

        val cannotTellKatachiVersion: Msg = Msg(
            ja = "katachi のバージョンが分かりません。$KATACHI_RELEASES_PAGE を見て --katachi で指定してください。",
            en = "Cannot tell the katachi version. Look it up at $KATACHI_RELEASES_PAGE and pass it with --katachi.",
        )

        val couldNotDetectKotlin: Msg = Msg(
            ja = "Kotlin のバージョンを検出できませんでした。--kotlin で指定してください。",
            en = "Could not detect the Kotlin version. Pass it with --kotlin.",
        )

        val couldNotDetectEmbeddedKotlin: Msg = Msg(
            ja = "Kotlin のバージョンを検出できませんでした。buildSrc は Gradle に同梱の Kotlin（embeddedKotlin）を使っていて、" +
                "そのバージョンは ./gradlew --version の Kotlin: の行に出ます。--kotlin で指定してください。",
            en = "Could not detect the Kotlin version. buildSrc uses the Kotlin that comes with Gradle (embeddedKotlin), " +
                "whose version ./gradlew --version prints on its Kotlin: line. Pass it with --kotlin.",
        )

        fun couldNotReadKatachi(katachi: Any?): Msg = Msg(
            ja = "katachi のバージョンを読み取れませんでした: $katachi（0.4.0 のような形で指定してください）",
            en = "Could not read the katachi version: $katachi (give it in the form 0.4.0)",
        )

        fun catalogKeepsKatachiVersion(catalogUri: Any?, alias: Any?, existing: Any?, version: Any?): Msg = Msg(
            ja = "$catalogUri の katachi（$alias）は $existing のままです。カタログに無い項目だけ足すので、$version には変わらず、生成したモジュールは $existing を使います。$version を使うなら、カタログの $alias を直してください。",
            en = "The katachi entry ($alias) in $catalogUri stays at $existing. Only entries the catalog lacks are added, so it does not change to $version and the generated module uses $existing. To use $version, change $alias in the catalog.",
        )

        fun otherPackageTestsRemain(files: Any?): Msg = Msg(
            ja = "別のパッケージで作った検査用テストが残っています:\n    $files\n    このまま検査すると、古い定義も一緒に動きます。要らなければ、そのパッケージのディレクトリを消してください。",
            en = "Tests generated for another package are still there:\n    $files\n    They run together with the new definition. Delete that package's directory if you no longer need it.",
        )

        fun couldNotReadKotlin(kotlin: Any?): Msg = Msg(
            ja = "Kotlin のバージョンを読み取れませんでした: $kotlin",
            en = "Could not read the Kotlin version: $kotlin",
        )

        fun afterUpgradingRunScaffold(message: Any?): Msg = Msg(
            ja = "$message\n    上げてから、scaffold をもう一度実行してください。",
            en = "$message\n    After upgrading, run scaffold again.",
        )

        fun alreadyExistsAddForce(moduleDirUri: Any?): Msg = Msg(
            ja = "$moduleDirUri/ がすでに存在します。上書きするなら --force を付けてください。",
            en = "$moduleDirUri/ already exists. Add --force to overwrite it.",
        )

        fun unknownValuePreferencesTestframework(pref: Any?): Msg = Msg(
            ja = "preferences.testFramework の値を知りません（$pref。junit5 / kotest / kotest-free など）。チェックリストの値を直すか、消して検出した値に任せてから、もう一度実行してください。",
            en = "Unknown value in preferences.testFramework ($pref; expected junit5, kotest, kotest-free and so on). Fix the value in the checklist, or remove it to use the detected one, then run again.",
        )

        fun detected(detectedTfFrom: Any?): Msg = Msg(
            ja = "検出: $detectedTfFrom",
            en = "detected from $detectedTfFrom",
        )

        val defaultNoKotestDependency: Msg = Msg(
            ja = "デフォルト。kotest の依存が見つからない",
            en = "the default; no kotest dependency found",
        )

        fun unknownValuePreferencesDependencystyle(pref: Any?): Msg = Msg(
            ja = "preferences.dependencyStyle の値を知りません（$pref。catalog か literal）。チェックリストの値を直すか、消して検出した値に任せてから、もう一度実行してください。",
            en = "Unknown value in preferences.dependencyStyle ($pref; expected catalog or literal). Fix the value in the checklist, or remove it to use the detected one, then run again.",
        )

        val detectedCatalog: Msg = Msg(
            ja = "検出: $CATALOG_FILE",
            en = "detected: $CATALOG_FILE",
        )

        val noCatalogFile: Msg = Msg(
            ja = "$CATALOG_FILE が無い",
            en = "no $CATALOG_FILE",
        )

        fun useDepsLiteralFix(reason: Msg): Msg = Msg(
            ja = "${reason.ja}。--deps literal にするか、カタログの別名を直してください。",
            en = "${reason.en}. Use --deps literal, or fix the alias in the catalog.",
        )

        fun notWrittenCatalog(source: Msg): Msg = Msg(
            ja = "${source.ja}。catalog には書かない",
            en = "${source.en}; not written to the catalog",
        )

        val whatWillCreated: Msg = Msg(
            ja = "作成する内容",
            en = "What will be created",
        )

        fun packageName(pkg: Any?): Msg = Msg(
            ja = "パッケージ:   $pkg",
            en = "Package:      $pkg",
        )

        fun versionKatachiCommand(version: Any?): Msg = Msg(
            ja = "katachi:      $version（この katachi コマンドのバージョン）",
            en = "katachi:      $version (the version of this katachi command)",
        )

        fun latestReleaseGithub(version: Any?): Msg = Msg(
            ja = "katachi:      $version（GitHub の最新リリース）",
            en = "katachi:      $version (the latest release on GitHub)",
        )

        fun testFrameworkKotest(kotestSpecClassKotestStyle: Any?, source: Msg): Msg = Msg(
            ja = "テスト:       kotest の $kotestSpecClassKotestStyle（${source.ja}）",
            en = "Test framework: kotest, $kotestSpecClassKotestStyle (${source.en})",
        )

        fun testFramework(tf: Any?, source: Msg): Msg = Msg(
            ja = "テスト:       $tf（${source.ja}）",
            en = "Test framework: $tf (${source.en})",
        )

        fun dependenciesCatalogAppended(source: Msg, catalogUri: Any?): Msg = Msg(
            ja = "依存:         catalog（${source.ja}。$catalogUri に追記）",
            en = "Dependencies: catalog (${source.en}; appended to $catalogUri)",
        )

        val nothingAppendEveryEntry: Msg = Msg(
            ja = "              追記なし（必要な項目はすべてある）",
            en = "              nothing to append (every entry is already there)",
        )

        fun dependenciesLiteral(source: Msg): Msg = Msg(
            ja = "依存:         literal（${source.ja}）",
            en = "Dependencies: literal (${source.en})",
        )

        fun moduleModuleDir(moduleDirUri: Any?, moduleGradlePath: Any?): Msg = Msg(
            ja = "モジュール:   $moduleDirUri/（$moduleGradlePath。--module-dir）",
            en = "Module:       $moduleDirUri/ ($moduleGradlePath; from --module-dir)",
        )

        fun moduleMetaModuledirRecorded(moduleDirUri: Any?, moduleGradlePath: Any?): Msg = Msg(
            ja = "モジュール:   $moduleDirUri/（$moduleGradlePath。チェックリストの meta.moduleDir）",
            en = "Module:       $moduleDirUri/ ($moduleGradlePath; meta.moduleDir in the check list)",
        )

        fun moduleDefault(moduleDirUri: Any?, moduleGradlePath: Any?): Msg = Msg(
            ja = "モジュール:   $moduleDirUri/（$moduleGradlePath。デフォルト）",
            en = "Module:       $moduleDirUri/ ($moduleGradlePath; the default)",
        )

        fun sources(srcDirUri: Any?): Msg = Msg(
            ja = "ソース:       $srcDirUri/",
            en = "Sources:      $srcDirUri/",
        )

        fun ignoreBuild(ignored: Boolean, gitignoreUri: Any?): Msg = Msg(
            ja = "build/ を無視: " + if (ignored) "済み（.gitignore で除外済み）" else "$gitignoreUri に /build を書く",
            en = "Ignore build/: " + if (ignored) "already (excluded by .gitignore)" else "write /build to $gitignoreUri",
        )

        fun addRoot(yesNoRootNeedsPlugin: Any?, rootBuildNameUri: Any?): Msg = Msg(
            ja = "ルートに追加: $yesNoRootNeedsPlugin ($rootBuildNameUri)",
            en = "Add to root:  $yesNoRootNeedsPlugin ($rootBuildNameUri)",
        )

        val addRootNoRootBuildFile: Msg = Msg(
            ja = "ルートに追加: no（ルートの build ファイルはありません）",
            en = "Add to root:  no (there is no root build file)",
        )

        fun subprojectDeclaresKotlinPlugin(moduleGradlePath: Any?, kotlinPlugin: Any?): Msg = Msg(
            ja = "              サブプロジェクトが Kotlin プラグインをバージョン付きで宣言しているため、ルートには足さず $moduleGradlePath にバージョンを書きます（$kotlinPlugin）\n" +
                "                 Gradle が「The Kotlin Gradle plugin was loaded multiple times ...」と警告しますが、この構成では想定どおりです",
            en = "              A subproject declares the Kotlin plugin with a version, so it is not added to the root; the version goes into $moduleGradlePath ($kotlinPlugin)\n" +
                "                 Gradle warns \"The Kotlin Gradle plugin was loaded multiple times ...\"; that is expected with this setup",
        )

        fun addInclude(yesNoSettingsNeedsInclude: Any?, settingsNameUri: Any?): Msg = Msg(
            ja = "include 追加: $yesNoSettingsNeedsInclude ($settingsNameUri)",
            en = "Add include:  $yesNoSettingsNeedsInclude ($settingsNameUri)",
        )

        fun addPluginmanagement(needed: Any?, missing: String?): Msg = Msg(
            ja = "pluginManagement に追加: $needed" + (missing?.let { "（$it）" } ?: ""),
            en = "Add to pluginManagement: $needed" + (missing?.let { " ($it)" } ?: ""),
        )

        val developers: Msg = Msg(
            ja = "mavenLocal:   有効（開発者向け）",
            en = "mavenLocal:   on (for developers)",
        )

        val nothingWrittenDryRun: Msg = Msg(
            ja = "--dry-run のため何も書きませんでした。",
            en = "Nothing was written (--dry-run).",
        )

        fun keptAlreadyThere(architectureKtUri: Any?): Msg = Msg(
            ja = "すでにあるので残しました: $architectureKtUri",
            en = "Kept (already there): $architectureKtUri",
        )

        val created: Msg = Msg(
            ja = "== 作成しました ========================================",
            en = "== Created =============================================",
        )

        fun addedBuild(uri: Any?): Msg = Msg(
            ja = "$uri （/build を追加）",
            en = "$uri (added /build)",
        )

        fun appendedFollowing(catalogUri: Any?): Msg = Msg(
            ja = "$catalogUri （次の項目を追記）",
            en = "$catalogUri (appended the following)",
        )

        fun nothingAppendedEveryEntry(catalogUri: Any?): Msg = Msg(
            ja = "$catalogUri （追記なし。必要な項目はすべてあった）",
            en = "$catalogUri (nothing appended; every entry was already there)",
        )

        fun addedKotlinJvmPlugin(rootBuildUri: Any?): Msg = Msg(
            ja = "$rootBuildUri （Kotlin JVM プラグインを apply false で追加）",
            en = "$rootBuildUri (added the Kotlin JVM plugin with apply false)",
        )

        fun addedInclude(settingsUri: Any?): Msg = Msg(
            ja = "$settingsUri （include を追加）",
            en = "$settingsUri (added the include)",
        )

        fun addedRepositoriesPluginmanagement(settingsUri: Any?, missingText: Any?): Msg = Msg(
            ja = "$settingsUri （pluginManagement の repositories に $missingText を追加）",
            en = "$settingsUri (added $missingText to the repositories of pluginManagement)",
        )

        fun addedMavenlocalRepositoriesDependencyresolutionmanagement(settingsUri: Any?): Msg = Msg(
            ja = "$settingsUri （dependencyResolutionManagement の repositories に mavenLocal() を追加。開発者向け）",
            en = "$settingsUri (added mavenLocal() to the repositories of dependencyResolutionManagement; for developers)",
        )

        fun couldNotReadShape(settingsUri: Any?): Msg = Msg(
            ja = "$settingsUri の pluginManagement { } の形を読み取れなかったため、自動で書き換えませんでした。",
            en = "Could not read the shape of pluginManagement { } in $settingsUri, so it was not edited automatically.",
        )

        fun addFollowingPluginmanagementRepositories(settingsUri: Any?): Msg = Msg(
            ja = "$settingsUri の pluginManagement { repositories { } } に、次を足してください\n（pluginManagement { } が無ければ、ファイルの先頭に作ってください）。",
            en = "Add the following to pluginManagement { repositories { } } in $settingsUri\n(if there is no pluginManagement { }, create one at the top of the file).",
        )

        fun katachiSGradlePlugin(fails: Msg): Msg = Msg(
            ja = "katachi の Gradle plugin（$KATACHI_PLUGIN_ID）は Maven Central にあります。${fails.ja}",
            en = "katachi's Gradle plugin ($KATACHI_PLUGIN_ID) is on Maven Central. ${fails.en}",
        )

        fun couldNotFindWhere(settingsUri: Any?): Msg = Msg(
            ja = "$settingsUri の include 文の終わりを読み取れなかったため、include を自動で足しませんでした。",
            en = "Could not find where the include statement in $settingsUri ends, so the include was not added automatically.",
        )

        fun addFollowingLineAfter(settingsUri: Any?): Msg = Msg(
            ja = "次の1行を、$settingsUri の既存の include 文の後ろ（閉じ括弧より後）に足してください。",
            en = "Add the following line after the existing include statement in $settingsUri (after its closing parenthesis).",
        )

        fun hasNoDependencyresolutionmanagementRepositories(settingsUri: Any?): Msg = Msg(
            ja = "$settingsUri に dependencyResolutionManagement { repositories { } } が無いため、依存の解決先に mavenLocal() を足せませんでした（開発者向けの KATACHI_MAVEN_LOCAL）。",
            en = "$settingsUri has no dependencyResolutionManagement { repositories { } }, so mavenLocal() could not be added to where dependencies are resolved (KATACHI_MAVEN_LOCAL, for developers).",
        )

        fun addMavenlocalHandRepositories(moduleGradlePath: Any?): Msg = Msg(
            ja = "  $moduleGradlePath が依存を解決している repositories { } に mavenLocal() を手で足してください。",
            en = "  Add mavenLocal() by hand to the repositories { } that $moduleGradlePath resolves its dependencies from.",
        )

        fun hasBuildscriptBlockNot(rootBuildUri: Any?): Msg = Msg(
            ja = "$rootBuildUri は buildscript { } を持つため、自動で書き換えませんでした。",
            en = "$rootBuildUri has a buildscript { } block, so it was not edited automatically.",
        )

        fun addFollowingLineInside(rootBuildUri: Any?): Msg = Msg(
            ja = "次の1行を、$rootBuildUri の buildscript { } の**後ろ**にある\nplugins { } の中に足してください（plugins { } が無ければ作ってください）。",
            en = "Add the following line inside the plugins { } block that comes **after** buildscript { }\nin $rootBuildUri (create plugins { } if there is none).",
        )

        fun nextTestArchitectureEmpty(modulePath: Any?): Msg = Msg(
            ja = "次: ./gradlew $modulePath:test\n    architecture { } が空なので、すべてのファイルが Unexpected として報告されて\n    失敗するのが正常です。Unexpected 以外のエラーが出た場合だけが問題です。",
            en = "Next: ./gradlew $modulePath:test\n    architecture { } is empty, so every file is reported as Unexpected and the test\n    fails. That is expected. Only errors other than Unexpected are a problem.",
        )

        val projectMatchesDeclaration: Msg = Msg(
            ja = "プロジェクトの構成が定義どおりになっている",
            en = "the project matches its declaration",
        )

        fun testFailsUntilAdded(modulePath: Any?): Msg = Msg(
            ja = "足すまで ./gradlew $modulePath:test は失敗します。",
            en = "./gradlew $modulePath:test fails until you add it.",
        )

        fun catalogRemapped(settingsUri: Any?): Msg = Msg(
            ja = "$settingsUri の versionCatalogs { } が $CATALOG_FILE 以外を使っている",
            en = "versionCatalogs { } in $settingsUri uses something other than $CATALOG_FILE",
        )
    }

    object Context {
        fun needsValue(option: Any?): Msg = Msg(
            ja = "$option に値がありません。",
            en = "$option needs a value.",
        )
    }

    object Workdir {
        fun moduleDirMustPath(value: Any?): Msg = Msg(
            ja = "--module-dir は Gradle ルートからの相対パスです（例: static-analysis/katachi）: $value",
            en = "--module-dir must be a path relative to the Gradle root (for example static-analysis/katachi): $value",
        )

        fun definitionDirMissing(dirUri: Any?, moduleDir: Any?): Msg = Msg(
            ja = "定義のディレクトリがありません: $dirUri 。scaffold をまだ実行していないか、置き場所が違います" +
                "（$moduleDir。違うなら --module-dir <パス> で指定するか、チェックリストの meta.moduleDir を直す）",
            en = "The definition directory does not exist: $dirUri . Either scaffold has not run yet, or the location differs " +
                "($moduleDir; if it is wrong, pass --module-dir <path>, or fix meta.moduleDir in the check list)",
        )

        fun ambiguous(candidates: Any?): Msg = Msg(
            ja = "作業用ディレクトリ（check-list.html と tmp/ のあるディレクトリ）が 2 つ以上あり、どれを使うか決められません:\n$candidates" +
                "使うほうを KATACHI_WORKDIR=<パス> で指定するか、使わないほうを検索範囲（いまの場所から 4 階層）の外に移してください。",
            en = "There are two or more working directories (directories with check-list.html and tmp/), and katachi cannot tell which one to use:\n$candidates" +
                "Pick one with KATACHI_WORKDIR=<path>, or move the others out of the search (4 levels below the current directory).",
        )

        fun misconfigured(configured: Any?, pathUri: Any?): Msg = Msg(
            ja = "KATACHI_WORKDIR（$configured）は作業用ディレクトリではありません（$pathUri に読める check-list.html がない）。" +
                "check-list.html のあるディレクトリを指すように直すか、KATACHI_WORKDIR を外してください。",
            en = "KATACHI_WORKDIR ($configured) is not a working directory (there is no readable check-list.html in $pathUri). " +
                "Point it at the directory with check-list.html, or unset it.",
        )

        fun unreadableCheckListsSkipped(fileUris: Any?): Msg = Msg(
            ja = "読めない check-list.html は、作業用ディレクトリとして使いません: $fileUris ",
            en = "Not using a check-list.html that cannot be read as a working directory: $fileUris ",
        )

        fun projectRootInOtherWorkTree(recordedUri: Any?, hereUri: Any?): Msg = Msg(
            ja = "チェックリストの meta.projectRoot（$recordedUri）は、いまいる場所とは別の作業ツリー（git worktree）です。" +
                "いまいる作業ツリーの $hereUri を検査し、ログもそこの build/katachi/ に置きます。記録した側を検査するときは、そちらで実行してください。",
            en = "meta.projectRoot in the check list ($recordedUri) is in another work tree (git worktree) than the current directory. " +
                "Checking $hereUri in this work tree instead, with the logs in its build/katachi/. To check the recorded one, run this from there.",
        )

        fun projectRootNotHoldingWorkdir(recordedUri: Any?, workdirUri: Any?, hereUri: Any?): Msg = Msg(
            ja = "チェックリストの meta.projectRoot は $recordedUri で、この作業用ディレクトリ $workdirUri のあるプロジェクトではありません。" +
                "プロジェクトを作業用ディレクトリごと写したか、移したようです。作業用ディレクトリのある $hereUri を検査します。" +
                "記録した側を検査するときは、そちらで実行してください。",
            en = "meta.projectRoot in the check list is $recordedUri , not the project that holds this working directory $workdirUri : " +
                "the project seems to have been copied or moved along with it. Checking $hereUri , which holds the working directory, instead. " +
                "To check the recorded one, run this from there.",
        )
    }

    object ScaffoldBuildFile {
        val kotlinPluginNote: Msg = Msg(
            ja = "    // alias(libs.plugins.kotlin.jvm) と書かない。build-logic（includeBuild）経由で Kotlin plugin が\n" +
                "    // classpath にあると \"already on the classpath with an unknown version\" で失敗する。バージョンなしの\n" +
                "    // kotlin(\"jvm\") か、カタログに寄せるなら id(libs.plugins.kotlin.jvm.get().pluginId) を使う。\n",
            en = "    // Do not write alias(libs.plugins.kotlin.jvm). When the Kotlin plugin is already on the\n" +
                "    // classpath through build-logic (includeBuild), that fails with \"already on the classpath\n" +
                "    // with an unknown version\". Use the version-less kotlin(\"jvm\"), or\n" +
                "    // id(libs.plugins.kotlin.jvm.get().pluginId) if you want it to come from the catalog.\n",
        )
    }

    object Usage {
        val top: Msg = Msg(
            ja = """
katachi — katachi の導入と検査を手伝うコマンド

使い方:
  katachi doctor [--lang <en|ja>]
      Gradle が使う JDK のバージョン、プロジェクトの Kotlin のバージョン、git の有無を確かめる。
      JDK が足りなければ直し方を出して 1 を返す。

  katachi scaffold --package <com.example.app> [オプション]
      検査用モジュール（デフォルト architecture-test/。--module-dir で変える）を作成し、
      ビルドに組み込む。

      --package <pkg>     アプリのパッケージ名（必須）
      --katachi <version> katachi のバージョン（デフォルト: この katachi コマンドのバージョン）
      --kotlin <version>  Kotlin のバージョン（デフォルト: プロジェクトから検出）
      --no-konsist        katachi-konsist を使わない構成で生成する（ktFileContent { } が書けない）。
                          assert() に FileConstraintCheck() を渡さないので、gradle { } の hook に
                          書いた fileConstraint も評価されず、test は [UncheckedFileConstraint] で
                          失敗する（How to fix: Pass FileConstraintCheck() to assert()）
      --module-dir <path> 検査用モジュールの置き場所（デフォルト: 作業用ディレクトリのチェックリストの
                          meta.moduleDir。無ければ architecture-test）
      --test-framework <junit5|kotest|kotest-<style>>
                          テストの雛形と依存（デフォルト: preferences.testFramework。無ければ
                          kotest の依存がプロジェクトにあれば kotest、なければ junit5）。
                          kotest は FunSpec で書く。kotest-<style> なら kotest の別の書き方で、
                          style は fun / string / should / describe / behavior / word / free /
                          feature / expect / annotation（例: kotest-free で FreeSpec）
      --deps <catalog|literal>
                          依存の書き方（デフォルト: preferences.dependencyStyle。無ければ
                          gradle/libs.versions.toml があれば catalog）。catalog は toml に
                          無い項目だけを足し、build.gradle.kts は libs.katachi のように参照する。
                          別名が別の用途で使われていると、明示したときは止まり、
                          デフォルトのときは literal にする
      --force             既存の検査用モジュールを上書きする
      --dry-run           何も書かずに、何をするかだけ出力する

  katachi lint [--module-dir <path>]
      Gradle で評価した定義（検査用モジュールの katachiInternalDefinitionJson の JSON）を読み、
      種類の違うファイルを1つの Role に抱えていそうなもの、Gradle のファイルを手書きで宣言した Role（制約を持つものは
      gradle { } の hook への移し方も出す）、平たい group、小文字で始まる Role・group の名前、
      分けていないファイル、documented = false の候補を列挙する。
      プロジェクトの ./gradlew で定義を評価するので、定義がコンパイルできないときは点検せず、
      理由を出して 1 を返す。点検の結果では失敗にしない（0 を返す）。
      定義のディレクトリが無ければ（scaffold 前、置き場所の食い違い）1 を返す。
      --module-dir は検査用モジュールの置き場所で、scaffold の --module-dir と同じ（デフォルト:
      作業用ディレクトリのチェックリストの meta.moduleDir。無ければ architecture-test）。

  katachi <ファイル>.katachi.kts [<引数>...]
      katachi のコマンドを Kotlin で並べたスクリプトを実行する。katachi と
      defaultPackage などのデフォルト値は最初から使える（kotlin コマンドが要る）。
      引数はスクリプトの args に入る。コマンドはスクリプトのあるディレクトリで実行される
      （シェルのカレントではない）。execute() の戻り値は、捨てるとスクリプトの終了コードに
      反映されない。失敗を伝えるなら exitProcess(executeCommands(...)) と書く。

  katachi --version
  katachi --help

scaffold は Gradle のルートディレクトリで実行してください。

出力の言語: doctor の --lang、作業用ディレクトリのチェックリストの言語、KATACHI_LANG（ja / en）、
ロケール（LC_ALL / LC_MESSAGES / LANG）の順に決める。どれも無ければ日本語。
""".trimIndent(),
            en = """
katachi - helps you set up and check katachi

Usage:
  katachi doctor [--lang <en|ja>]
      Checks the JDK Gradle uses, the project's Kotlin version and whether git is there.
      If the JDK is too old, says how to fix it and returns 1.

  katachi scaffold --package <com.example.app> [options]
      Creates the verification module (default architecture-test/; change it with --module-dir)
      and wires it into the build.

      --package <pkg>     The app's package name (required)
      --katachi <version> katachi version (default: the version of this katachi command)
      --kotlin <version>  Kotlin version (default: detected from the project)
      --no-konsist        Generate a setup that does not use katachi-konsist (no ktFileContent { }).
                          assert() gets no FileConstraintCheck(), so a fileConstraint written in a
                          gradle { } hook is not evaluated either, and the test fails with
                          [UncheckedFileConstraint] (How to fix: Pass FileConstraintCheck() to assert())
      --module-dir <path> Where the verification module goes (default: meta.moduleDir in the check list of
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
      --force             Overwrite an existing verification module
      --dry-run           Write nothing; only print what would be done

  katachi lint [--module-dir <path>]
      Reads the definition evaluated by Gradle (the JSON of the verification module's
      katachiInternalDefinitionJson) and lists Roles that seem to hold files of different kinds, hand-written Roles for Gradle files (ones with
      constraints also get the way to move them into a gradle { } hook), flat groups, Role and
      group names starting with a lowercase letter, files not split, and candidates for
      documented = false.
      It evaluates the definition with the project's ./gradlew, so when the definition does not compile
      it checks nothing, prints why and returns 1. Findings never fail it (returns 0).
      Returns 1 if the definition directory does not exist (before scaffold, or a different location).
      --module-dir is where the verification module is, the same as scaffold's --module-dir (default:
      meta.moduleDir in the check list of the working directory, otherwise architecture-test).

  katachi <file>.katachi.kts [<argument>...]
      Runs a script that lines up katachi commands in Kotlin. katachi and the
      defaults such as defaultPackage are there from the start (needs the kotlin
      command). The arguments are the script's args. The commands run in the
      script's own directory (not the shell's current one). A return value of execute()
      that the script drops is not the script's exit status. To report a failure,
      write exitProcess(executeCommands(...)).

  katachi --version
  katachi --help

Run scaffold in the Gradle root directory.

Output language: --lang of doctor, then the language of the working directory's check list, then
KATACHI_LANG (ja / en), then the locale (LC_ALL / LC_MESSAGES / LANG). Japanese if none says.
""".trimIndent(),
        )
    }
}
