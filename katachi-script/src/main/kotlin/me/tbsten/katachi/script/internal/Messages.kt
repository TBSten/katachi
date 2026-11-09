package me.tbsten.katachi.script.internal

/** One message in both languages; [ScriptContext.pick] chooses when it is printed. */
internal class Msg(val ja: String, val en: String)

/** Every text the katachi command prints, in both languages. MessagesSpec holds the Japanese against docs/prh.yml. */
internal object Messages {
    object Add {
        val usageAddKindField: Msg = Msg(
            ja = "使い方: add <種類> --<項目> <値> ...（項目は add <種類> --help）",
            en = "Usage: add <kind> --<field> <value> ... (for the fields: add <kind> --help)",
        )

        val specifyKindViolationQuestion: Msg = Msg(
            ja = "add: 種類を指定してください（violation / question / codebase-question / changed / module / role / tool / excluded / template）",
            en = "add: specify the kind (violation / question / codebase-question / changed / module / role / tool / excluded / template)",
        )

        fun unknownKind(kindName: Any?): Msg = Msg(
            ja = "add: 知らない種類です: $kindName",
            en = "add: unknown kind: $kindName",
        )

        fun fileNotFound(htmlUri: Any?): Msg = Msg(
            ja = "ファイルがありません: $htmlUri",
            en = "File not found: $htmlUri",
        )

        fun pathExcludedGitignoreKatachi(path: Any?): Msg = Msg(
            ja = "そのパスは .gitignore で除外されています。katachi はもともと見ないので、対象外には書きません: $path",
            en = "That path is excluded by .gitignore. katachi never looks at it, so it is not recorded as excluded: $path",
        )

        fun couldNotExtractJson(htmlUri: Any?): Msg = Msg(
            ja = "$htmlUri から JSON を取り出せません。",
            en = "Could not extract the JSON from $htmlUri.",
        )

        fun pathAlreadyRecordedNo(entryIdentity: Any?): Msg = Msg(
            ja = "その path は記録済みです: $entryIdentity（記録し直さなくて構いません。何も変えていません）",
            en = "That path is already recorded: $entryIdentity (no need to record it again; nothing was changed)",
        )

        fun alreadyRecordedNotAppended(identity: Any?, entryIdentity: Any?): Msg = Msg(
            ja = "その $identity はすでに登録されています: $entryIdentity\n二重に記録しないため追記しませんでした。\n書き換えたい場合は data get で取り出し、直してから data set してください。",
            en = "That $identity is already recorded: $entryIdentity\nNot appended, to avoid recording it twice.\nTo change it, take it out with data get, edit it, and write it back with data set.",
        )

        fun appendedEntryTotal(kindArray: Any?, rowsSize: Any?): Msg = Msg(
            ja = "$kindArray に1件追記しました（計 $rowsSize 件）",
            en = "Appended 1 entry to $kindArray ($rowsSize in total)",
        )

        fun countNotAddUp(kindArray: Any?, before: Any?, after: Any?, expected: Any?): Msg = Msg(
            ja = "件数が合いません: $kindArray は書き込み前 $before 件、追記後 $after 件（期待 $expected）。ほかのプロセスが同時に書いていませんか",
            en = "The count does not add up: $kindArray had $before entries before, $after after appending (expected $expected). Is another process writing at the same time?",
        )

        fun usageAddFieldValue(kindName: Any?, kindArray: Any?): Msg = Msg(
            ja = "使い方: add $kindName --<項目> <値> ...（$kindArray に追記）",
            en = "Usage: add $kindName --<field> <value> ... (appends to $kindArray)",
        )

        fun validFields(kindScalars: Any?): Msg = Msg(
            ja = "使える項目: $kindScalars",
            en = "Valid fields: $kindScalars",
        )

        fun fieldsYouCanPass(kindLists: Any?): Msg = Msg(
            ja = "複数回渡せる項目: $kindLists",
            en = "Fields you can pass more than once: $kindLists",
        )

        fun flagsNoValue(kindFlags: Any?): Msg = Msg(
            ja = "値を取らない印: $kindFlags",
            en = "Flags (no value): $kindFlags",
        )

        val groupWhereRoleGoes: Msg = Msg(
            ja = "--group は定義での束ね先（入れ子は . 区切り: feature.ui）。Gradle のビルドファイルは gradle() が受け持つので --layout に書けません（buildSrc / includeBuild したビルドは除く）。制約を付けるための Role なら --constraint-only --note <理由> で記録します。",
            en = "--group is where the Role goes in the definition (nested with dots: feature.ui). Gradle build files cannot be a --layout since gradle() takes care of them (except buildSrc and included builds). A Role that exists only to attach a constraint is recorded with --constraint-only --note <reason>.",
        )

        val deviationHowModuleDiffers: Msg = Msg(
            ja = "--deviation は同じ kind の他のモジュールと構成が違う点（無ければ省略）。",
            en = "--deviation is how this module differs from the other modules of the same kind (omit if none).",
        )

        fun idNumberedAutomaticallyAnswers(p: Any?): Msg = Msg(
            ja = "id は ${p}1, ${p}2, ... と自動で採番されます（ブラウザの一括回答と answers がこの id で答えを結びつけます）。",
            en = "The id is numbered automatically as ${p}1, ${p}2, ... (the answers in the browser and answers use it).",
        )

        fun noFieldsGivenAdd(kindName: Any?, kindScalars: Any?): Msg = Msg(
            ja = "項目を1つも指定していません: add $kindName\nこのまま追記すると中身が空の行ができます。\n使えるもの: $kindScalars",
            en = "No fields given: add $kindName\nAppending as is would add an empty row.\nValid fields: $kindScalars",
        )

        fun needsValue(name: Any?): Msg = Msg(
            ja = "--$name に値がありません",
            en = "--$name needs a value",
        )

        fun mustNumber(name: Any?, value: Any?): Msg = Msg(
            ja = "--$name は数値で指定してください: $value",
            en = "--$name must be a number: $value",
        )

        val whatModuleResponsibleGoes: Msg = Msg(
            ja = "モジュールの受け持ちは --responsibility です。katachi の Role（1種類のファイル）とは別物で、モジュールは定義では group になります。",
            en = "What a module is responsible for goes in --responsibility. It is not a katachi Role (one kind of file); a module becomes a group in the definition.",
        )

        fun cannotUsedValidFields(name: Any?, kindName: Any?, hint: Any?, valid: Any?): Msg = Msg(
            ja = "--$name は $kindName で使えません$hint\n使えるもの: $valid",
            en = "--$name cannot be used with $kindName$hint\nValid fields: $valid",
        )

        val constraintOnlyNeedsNote: Msg = Msg(
            ja = "--constraint-only には --note で理由（何を検査するか）を書いてください",
            en = "--constraint-only needs --note with the reason (what the constraint checks)",
        )

        fun gradleTakesCareGradle(layout: Any?): Msg = Msg(
            ja = "Gradle のビルドファイルは gradle() が受け持ちます。Role 候補には書きません: $layout（buildSrc / includeBuild したビルドは除く。制約を付けるための Role なら --constraint-only --note <理由>）",
            en = "gradle() takes care of Gradle build files. They are not Role candidates: $layout (except buildSrc and included builds; for a Role that exists only to attach a constraint, use --constraint-only --note <reason>)",
        )
    }

    object Answers {
        fun tooManyArgumentsQuote(arg: Any?): Msg = Msg(
            ja = "answers: 引数が多すぎます: $arg（回答に空白があれば引用符で囲んでください）",
            en = "answers: too many arguments: $arg (quote the answer if it contains spaces)",
        )

        val givePathJsonExported: Msg = Msg(
            ja = "answers import: ブラウザで書き出した JSON のパスを指定してください。",
            en = "answers import: give the path of the JSON exported from the browser.",
        )

        fun fileNotFound(firstUri: Any?): Msg = Msg(
            ja = "ファイルがありません: $firstUri",
            en = "File not found: $firstUri",
        )

        val giveIdAnswerExample: Msg = Msg(
            ja = "answers set: <id> <回答> を指定してください（例: answers set q1 \"はい\" --note \"理由\"）",
            en = "answers set: give <id> <answer> (for example: answers set q1 \"yes\" --note \"reason\")",
        )

        fun htmlNotFound(htmlUri: Any?): Msg = Msg(
            ja = "ファイルがありません: $htmlUri",
            en = "File not found: $htmlUri",
        )

        fun couldNotExtractJson(htmlUri: Any?): Msg = Msg(
            ja = "$htmlUri から JSON を取り出せません。",
            en = "Could not extract the JSON from $htmlUri.",
        )

        fun notValidJson(first: Any?, eMessage: Any?): Msg = Msg(
            ja = "JSON として読めません: $first（$eMessage）",
            en = "Not valid JSON: $first ($eMessage)",
        )

        val answersNotDictPass: Msg = Msg(
            ja = "answers が dict ではありません（ブラウザで書き出した JSON をそのまま渡してください）",
            en = "answers is not a dict (pass the JSON exported from the browser as is)",
        )

        fun skippedQuestionNotHere(id: Any?, other: Msg): Msg = Msg(
            ja = "[!] $id は${other.ja}の確認事項なので、ここには入れずに飛ばしました",
            en = "[!] Skipped $id: it is a question in ${other.en}, not here",
        )

        fun skippedUnknownId(id: Any?): Msg = Msg(
            ja = "[!] 知らない id を飛ばしました: $id",
            en = "[!] Skipped an unknown id: $id",
        )

        fun unknownIdValidIds(first: Any?, ids: Any?): Msg = Msg(
            ja = "知らない id です: $first（使える id: $ids）",
            en = "Unknown id: $first (valid ids: $ids)",
        )

        fun importedAnswersUnanswered(incomingSize: Any?, unanswered: Any?): Msg = Msg(
            ja = "取り込みました: 回答 $incomingSize 件（未回答 $unanswered 件）",
            en = "Imported: $incomingSize answers ($unanswered unanswered)",
        )

        fun answeredUnanswered(answered: Any?, idsSize: Any?): Msg = Msg(
            ja = "回答済み $answered 件 / 未回答 $idsSize 件",
            en = "Answered $answered / unanswered $idsSize",
        )

        fun notQuestionIdQuestions(arg: Any?): Msg = Msg(
            ja = "answers set: 確認事項の id ではありません: $arg（チェックリストの確認事項は q<n>、レポートの確認事項は cq<n>）",
            en = "answers set: not a question id: $arg (questions in the check list are q<n>, in the report cq<n>)",
        )

        fun questionWhichContradictsTarget(arg: Any?, foundLabel: Msg, foundCliName: Any?, givenCliName: Any?): Msg = Msg(
            ja = "answers set: $arg は${foundLabel.ja}（$foundCliName）の確認事項で、--target $givenCliName と食い違うので書きませんでした。--target を外してください。",
            en = "answers set: $arg is a question in ${foundLabel.en} ($foundCliName), which contradicts --target $givenCliName. Nothing was written; drop --target.",
        )

        fun targetJsonContradictsTarget(fromJson: Any?, givenCliName: Any?): Msg = Msg(
            ja = "answers import: JSON の target（$fromJson）と --target $givenCliName が食い違います。どちらに取り込むかを確かめてください。",
            en = "answers import: the target in the JSON ($fromJson) contradicts --target $givenCliName. Check which file the answers belong to.",
        )

        fun targetReportCheckList(fromJson: Any?): Msg = Msg(
            ja = "answers: 対象は report か check-list です: $fromJson",
            en = "answers: the target is report or check-list: $fromJson",
        )

        val jsonHasNoTarget: Msg = Msg(
            ja = "answers import: JSON に target が無く、id からも取り込み先を決められません。--target report か --target check-list を付けてください。",
            en = "answers import: the JSON has no target, and the ids do not tell where it belongs. Pass --target report or --target check-list.",
        )

        fun target(cliName: Any?): Msg = Msg(
            ja = "対象: $cliName",
            en = "Target: $cliName",
        )
    }

    object Bundle {
        fun whichShipsKatachiMissing(path: Any?): Msg = Msg(
            ja = "katachi に同梱されているはずの $path がありません。katachi を入れ直してください（brew reinstall tbsten/tap/katachi）。",
            en = "$path, which ships with katachi, is missing. Reinstall katachi (brew reinstall tbsten/tap/katachi).",
        )
    }

    object Checklist {
        val checkList: Msg = Msg(
            ja = "チェックリスト",
            en = "the check list",
        )

        val report: Msg = Msg(
            ja = "レポート",
            en = "the report",
        )

        fun notFoundRunInit(checkListUri: Any?): Msg = Msg(
            ja = "$checkListUri がありません。先に init を実行してください。",
            en = "$checkListUri not found. Run init first.",
        )

        fun couldNotExtractJson(checkListUri: Any?): Msg = Msg(
            ja = "$checkListUri から JSON を取り出せませんでした。",
            en = "Could not extract the JSON from $checkListUri.",
        )
    }

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

    object Data {
        fun specifyTargetCheckList(action: Any?): Msg = Msg(
            ja = "data $action: 対象を指定してください（check-list / report / ファイルパス）。",
            en = "data $action: specify the target (check-list / report / a file path).",
        )

        fun fileNotFound(htmlUri: Any?): Msg = Msg(
            ja = "ファイルがありません: $htmlUri",
            en = "File not found: $htmlUri",
        )

        fun specifyJsonFile(action: Any?): Msg = Msg(
            ja = "data $action: JSON ファイルを指定してください。",
            en = "data $action: specify the JSON file.",
        )

        fun jsonNotFound(jsonUri: Any?): Msg = Msg(
            ja = "ファイルがありません: $jsonUri",
            en = "File not found: $jsonUri",
        )

        fun unexpectedArgument(action: Any?, it: Any?): Msg = Msg(
            ja = "data $action: 余分な引数です: $it",
            en = "data $action: unexpected argument: $it",
        )

        fun noSuchKey(key: Any?): Msg = Msg(
            ja = "キーがありません: $key",
            en = "No such key: $key",
        )

        fun notValidJsonFix(jsonFileUri: Any?): Msg = Msg(
            ja = "$jsonFileUri が JSON として読めません。直してからやり直してください。",
            en = "$jsonFileUri is not valid JSON. Fix it and try again.",
        )

        fun updatedIdOriginalBak(htmlUri: Any?, id: Any?): Msg = Msg(
            ja = "$htmlUri の id=\"$id\" を更新しました（元は $htmlUri.bak）。",
            en = "Updated id=\"$id\" in $htmlUri (the original is $htmlUri.bak).",
        )

        fun openingClosingTagsMust(noBlock: Msg): Msg = Msg(
            ja = "${noBlock.ja}開始タグと閉じタグは、それぞれ行頭に単独で置かれている必要があります。",
            en = "${noBlock.en} The opening and closing tags must each be alone at the start of a line.",
        )

        val couldNotMergeJson: Msg = Msg(
            ja = "JSON をマージできませんでした。",
            en = "Could not merge the JSON.",
        )

        fun failedEditRestored(htmlUri: Any?): Msg = Msg(
            ja = "$htmlUri の書き換えに失敗しました。元に戻しました。",
            en = "Failed to edit $htmlUri. It was restored.",
        )

        val resultCouldNotRead: Msg = Msg(
            ja = "差し込んだ結果が読み直せなかったため、元に戻しました。JSON の値に </script> のような、HTML を打ち切る文字列が含まれていないか確認してください。",
            en = "The result could not be read back, so it was restored. Check that no JSON value contains a string that cuts the HTML short, such as </script>.",
        )

        fun noJsonBlockId(htmlUri: Any?, id: Any?): Msg = Msg(
            ja = "$htmlUri に id=\"$id\" の JSON ブロックが見つかりません。",
            en = "No JSON block with id=\"$id\" in $htmlUri.",
        )

        fun noJsonBlock(htmlUri: Any?, id: Any?): Msg = Msg(
            ja = "$htmlUri に id=\"$id\" の JSON ブロックが見つかりません。",
            en = "No JSON block with id=\"$id\" in $htmlUri.",
        )
    }

    object Docs {
        fun unknownOption(it: Any?): Msg = Msg(
            ja = "docs: 知らないオプションです: $it",
            en = "docs: unknown option: $it",
        )

        fun sourceUsedCopyAlready(url: Any?): Msg = Msg(
            ja = "取得元: $url（取得済みのものを使いました。取り直すなら --refresh）",
            en = "Source: $url (used the copy already fetched; add --refresh to fetch it again)",
        )

        fun source(url: Any?): Msg = Msg(
            ja = "取得元: $url",
            en = "Source: $url",
        )

        fun unknownTopicAvailableTopics(topic: Any?, docsTopicList: Any?): Msg = Msg(
            ja = "docs --topic: 知らない名前です: $topic 。使える名前:\n$docsTopicList",
            en = "docs --topic: unknown topic: $topic . Available topics:\n$docsTopicList",
        )

        fun noHeadingCopyFetched(fullUri: Any?, title: Any?, topic: Any?): Msg = Msg(
            ja = "$fullUri に見出し「$title」がありません。取得済みの全文が古い可能性があります。--refresh を付けて取り直してください（$AGENT_COMMAND docs --topic $topic --refresh）。",
            en = "No heading \"$title\" in $fullUri . The copy fetched earlier may be old. Fetch it again with --refresh ($AGENT_COMMAND docs --topic $topic --refresh).",
        )

        fun fullGuideHasNo(topic: Any?, title: Any?, fullUri: Any?): Msg = Msg(
            ja = "docs --topic $topic: ガイドの全文に見出し「$title」がありません。katachi コマンドの不具合です（このコマンドが知っている見出しが、ガイドとずれています）。\n" +
                "    ユーザにこの出力を伝えたうえで、全文 $fullUri から、この話題にあたるページ（行頭の \"# \" の見出しから次の \"# \" の見出しの手前まで）だけを読んで進めてください。",
            en = "docs --topic $topic: the full guide has no heading \"$title\". This is a bug in the katachi command (the headings it knows no longer match the guide).\n" +
                "    Tell the user about this output, then read only the page for this topic (from its line-leading \"# \" heading up to the next one) in the full text $fullUri and go on.",
        )

        fun pageCutHeadingCopy(topic: Any?, title: Any?, fullUri: Any?): Msg = Msg(
            ja = "docs --topic $topic: 見出し「$title」で切り出したページが、古いバージョン（/0.2/ など）の写しでした。katachi コマンドの不具合です（このコマンドが知っている見出しが、ガイドの現行ページとずれています）。\n" +
                "    ユーザにこの出力を伝えたうえで、全文 $fullUri の前半（現行のバージョン）から、この話題にあたるページだけを読んで進めてください。",
            en = "docs --topic $topic: the page cut by heading \"$title\" is a copy of an older version (such as /0.2/). This is a bug in the katachi command (the headings it knows no longer match the guide's current pages).\n" +
                "    Tell the user about this output, then read only the page for this topic in the first part (the current version) of the full text $fullUri and go on.",
        )

        fun linesLlmsFullTxt(linesSize: Any?, title: Any?): Msg = Msg(
            ja = "$linesSize 行（llms-full.txt の「$title」）",
            en = "$linesSize lines (\"$title\" in llms-full.txt)",
        )
    }

    object Fetch {
        fun downloadFailedCheckYour(url: Any?, destUri: Any?): Msg = Msg(
            ja = "ダウンロードに失敗しました: $url\n  ネットワークかプロキシの設定を確認してください。\n  手動で取得する場合は、このファイルを $destUri に置いてから同じコマンドをやり直してください。",
            en = "Download failed: $url\n  Check your network or proxy settings.\n  To fetch it by hand, put the file at $destUri and run the same command again.",
        )
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

    object Guide {
        val dCheckListGuide: Msg = Msg(
            ja = "6-A〜6-D はチェックリストでは 6-1〜6-4（guide 6-1 でも取り出せる）",
            en = "6-A to 6-D are 6-1 to 6-4 in the check list (guide 6-1 works too)",
        )

        fun giveOnlyOneSection(first: Any?, second: Any?): Msg = Msg(
            ja = "guide: セクション ID は 1 つだけです（$first と $second）",
            en = "guide: give only one section id ($first and $second)",
        )

        fun unknownSectionIdIds(wanted: Any?): Msg = Msg(
            ja = "知らないセクション ID です: $wanted 。使える ID は目次のとおりです。",
            en = "Unknown section id: $wanted . The ids you can use are in the table of contents.",
        )

        fun next(nextCommand: Any?): Msg = Msg(
            ja = "次: $nextCommand",
            en = "Next: $nextCommand",
        )

        fun langMustEnJa(value: Any?): Msg = Msg(
            ja = "--lang は en か ja です（指定されたのは '$value'）。",
            en = "--lang must be en or ja (got '$value').",
        )

        fun sections(children: Any?): Msg = Msg(
            ja = "含むセクション: $children",
            en = "Sections: $children",
        )
    }

    object Init {
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

        fun unknownOption(it: Any?): Msg = Msg(
            ja = "init: 知らないオプションです: $it",
            en = "init: unknown option: $it",
        )

        val startingKatachiInstallation: Msg = Msg(
            ja = "katachi のインストールを開始します",
            en = "Starting the katachi installation",
        )

        fun projectRoot(rootUri: Any?): Msg = Msg(
            ja = "プロジェクトルート: $rootUri",
            en = "Project root:       $rootUri",
        )

        val couldNotTellKatachi: Msg = Msg(
            ja = "katachi のバージョンが分かりませんでした。$KATACHI_RELEASES_PAGE を見て --katachi で指定してください。",
            en = "Could not tell the katachi version. Look it up at $KATACHI_RELEASES_PAGE and pass it with --katachi.",
        )

        val developersScaffoldAddsMavenlocal: Msg = Msg(
            ja = "mavenLocal:         有効（開発者向け。scaffold が mavenLocal() を足す）",
            en = "mavenLocal:         on (for developers; scaffold adds mavenLocal())",
        )

        val gitUnderVersionControl: Msg = Msg(
            ja = "Git:               管理下にあります",
            en = "Git:                under version control",
        )

        val projectNotUnderGit: Msg = Msg(
            ja = "Git で管理されていません。失敗したときに変更を戻せないので、先にコミットするかバックアップを取ってください。",
            en = "The project is not under Git. You cannot undo the changes if something goes wrong, so commit or back up first.",
        )

        fun workingDirectory(workdirUri: Any?): Msg = Msg(
            ja = "作業用ディレクトリ:  $workdirUri",
            en = "Working directory:  $workdirUri",
        )

        fun thereExistingFilesThey(existing: Any?): Msg = Msg(
            ja = "既存のファイルがあります（$existing 件）。上書きはしません。",
            en = "There are existing files ($existing). They are not overwritten.",
        )

        fun testModuleGradlePath(moduleDir: Any?, modulePath: Any?): Msg = Msg(
            ja = "検査用モジュール:    $moduleDir/（Gradle のパス $modulePath）",
            en = "Test module:        $moduleDir/ (Gradle path $modulePath)",
        )

        fun visibleGitStepWill(workdirUri: Any?): Msg = Msg(
            ja = "$workdirUri は git から見えています。このままだとステップ4で必ず失敗します。\n" +
                "    katachi は未追跡でも ignore されていないファイルを検査するため、この作業用ディレクトリ\n" +
                "    自体が [UnexpectedFile] になります。ユーザに .gitignore への追加を提案してください\n" +
                "    （勝手に書き換えないこと）。",
            en = "$workdirUri is visible to git. As it is, step 4 will fail for sure.\n" +
                "    katachi checks untracked files that are not ignored, so this working directory itself\n" +
                "    becomes an [UnexpectedFile]. Suggest to the user that it be added to .gitignore\n" +
                "    (do not edit .gitignore yourself).",
        )

        val thereUntrackedFilesNot: Msg = Msg(
            ja = "未追跡で ignore もされていないものがあります。これらもステップ4で [UnexpectedFile] になります。\n    宣言するか、.gitignore に足すかをユーザに確認してください。",
            en = "There are untracked files that are not ignored. These also become [UnexpectedFile] in step 4.\n    Ask the user whether to declare them or add them to .gitignore.",
        )

        fun kotlinDetectedProject(kotlin: Any?): Msg = Msg(
            ja = "Kotlin:             $kotlin（プロジェクトから検出）",
            en = "Kotlin:             $kotlin (detected from the project)",
        )

        val couldNotDetectKotlin: Msg = Msg(
            ja = "Kotlin のバージョンを検出できませんでした。scaffold に --kotlin で渡してください。",
            en = "Could not detect the Kotlin version. Pass it to scaffold with --kotlin.",
        )

        fun docsSourceKatachiDocs(docs: Any?): Msg = Msg(
            ja = "配信元:             $docs（KATACHI_DOCS。以降のコマンドもこれを使う）",
            en = "Docs source:        $docs (KATACHI_DOCS; later commands use it too)",
        )

        fun keepingRecordedPreviousInit(recordedVersion: Any?, version: Any?): Msg = Msg(
            ja = "                    → 前回の init が記録した $recordedVersion を使い続けます（今回のデフォルトは $version。変えるなら --katachi を付けて実行し直す）",
            en = "                    → keeping $recordedVersion, recorded by the previous init (this time's default is $version; to change it, run again with --katachi)",
        )

        fun keptAlreadyThere(checkListUri: Any?): Msg = Msg(
            ja = "すでにあるので残しました: $checkListUri",
            en = "Kept (already there): $checkListUri",
        )

        fun placed(checkListUri: Any?): Msg = Msg(
            ja = "配置しました:        $checkListUri",
            en = "Placed:             $checkListUri",
        )

        fun keptReport(reportUri: Any?): Msg = Msg(
            ja = "すでにあるので残しました: $reportUri",
            en = "Kept (already there): $reportUri",
        )

        fun placedReport(reportUri: Any?): Msg = Msg(
            ja = "配置しました:        $reportUri",
            en = "Placed:             $reportUri",
        )

        fun guideGuideTableContents(uri: Any?): Msg = Msg(
            ja = "手順書:             $uri （$AGENT_COMMAND guide で目次）",
            en = "Guide:              $uri ($AGENT_COMMAND guide for the table of contents)",
        )

        val initResult: Msg = Msg(
            ja = "== init の結果 ==========================================",
            en = "== init result =========================================",
        )

        fun nowRunCommandsSubcommand(workdir: Any?): Msg = Msg(
            ja = "以降のコマンド（'$AGENT_COMMAND <サブコマンド>'、'$CLI_COMMAND scaffold' など）は、このプロジェクトのルートで実行してください（作業用ディレクトリは自動で見つけます。別の場所からは KATACHI_WORKDIR=$workdir を付ける）。",
            en = "From now on, run the commands ('$AGENT_COMMAND <subcommand>', '$CLI_COMMAND scaffold' and so on) in the root of this project (they find the working directory themselves; from elsewhere, set KATACHI_WORKDIR=$workdir).",
        )

        fun nextAskUserWhether(kotlin: Any?): Msg = Msg(
            ja = "次: Kotlin $kotlin を上げてよいかをユーザに確認してください（上の [!]。手順書 0-2 の「Kotlin が古いとき」）。上げるまでステップ 1 に進まないこと。",
            en = "Next: ask the user whether Kotlin $kotlin may be upgraded (the [!] above; \"When Kotlin is too old\" in step 0-2 of the guide). Do not go on to step 1 until it is upgraded.",
        )

        val nextCheckReadSection: Msg = Msg(
            ja = "次: $AGENT_COMMAND check 0-1 0-2（続けて、出力の「次:」のセクションを読む）",
            en = "Next: $AGENT_COMMAND check 0-1 0-2 (then read the section its \"Next:\" line names)",
        )

        fun couldNotInitializeMeta(fileUri: Any?, error: Any?): Msg = Msg(
            ja = "チェックリストのメタ欄を初期化できませんでした。$fileUri を直接確認してください。\n$error",
            en = "Could not initialize the meta fields of the check list. Check $fileUri directly.\n$error",
        )

        fun couldNotInitializeReportMeta(fileUri: Any?, error: Any?): Msg = Msg(
            ja = "レポートのメタ欄を初期化できませんでした。$fileUri を直接確認してください。\n$error",
            en = "Could not initialize the meta fields of the report. Check $fileUri directly.\n$error",
        )

        fun doctorUnknownOption(it: Any?): Msg = Msg(
            ja = "doctor: 知らないオプションです: $it",
            en = "doctor: unknown option: $it",
        )

        fun everythingNeedsAvailableKatachi(scriptVersionCurrent: Any?): Msg = Msg(
            ja = "必要なものはそろっています（katachi $scriptVersionCurrent。ガイドとテンプレートは同梱）。",
            en = "Everything this needs is available (katachi $scriptVersionCurrent; the guide and the templates ship with it).",
        )

        fun settings(settingsUri: Any?): Msg = Msg(
            ja = "settings:          $settingsUri",
            en = "settings:           $settingsUri",
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
                "手順書 3-2 の「1 つの Role = 1 種類のファイル」の3つの問いで見直し、分けるべきものは\n" +
                "種類ごとの Role に分けて group で束ねてください。テキストの素朴な走査なので誤検知はあり得ます。\n" +
                "見直して1種類だと判断したものは、そのままでかまいません。",
            en = "A Role (\"Name\".role { }) stands for one kind of file; group is what bundles different kinds.\n" +
                "Review them with the three questions of \"one Role = one kind of file\" in step 3-2 of the guide,\n" +
                "and split what should be split into one Role per kind, bundled with group. This is a plain text\n" +
                "scan, so false positives are possible. If you review a Role and decide it is one kind, leave it.",
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

        fun filesNotSplitMore(unsplitFilesSize: Any?): Msg = Msg(
            ja = "分けていないファイル（1 ファイルに Role か group が 2 つ以上）が $unsplitFilesSize 件あります（preferences.oneRolePerFile = yes）。",
            en = "Files not split (2 or more Roles or groups in one file): $unsplitFilesSize (preferences.oneRolePerFile = yes).",
        )

        val writeOneRolePer: Msg = Msg(
            ja = "1 Role 1 ファイルで書き、group はディレクトリで表してください（手順書 3-3）。",
            en = "Write one Role per file and express groups as directories (step 3-3 of the guide).",
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
    }

    object Lock {
        fun waitedSecondsLockBut(wait: Any?, directory: Any?): Msg = Msg(
            ja = "作業用ディレクトリのロックを $wait 秒待ちましたが取れませんでした: $directory 。ほかの katachi が書き込み中か、残骸です。残骸なら rm -r で消してやり直してください",
            en = "Waited $wait seconds for the lock but could not get it: $directory . Another katachi is writing, or this is left over. If it is left over, remove it with rm -r and try again",
        )
    }

    object Preferences {
        val nestGroups: Msg = Msg(
            ja = "group を入れ子にするか",
            en = "Nest groups?",
        )

        val whereTestModuleGoes: Msg = Msg(
            ja = "検査用モジュールの置き場所",
            en = "Where the test module goes",
        )

        val testFramework: Msg = Msg(
            ja = "テストのフレームワーク",
            en = "Test framework",
        )

        val howDependenciesWritten: Msg = Msg(
            ja = "依存の書き方",
            en = "How dependencies are written",
        )

        val putQuestionNumberConstraint: Msg = Msg(
            ja = "制約名に確認事項の番号を入れるか",
            en = "Put the question number in constraint names?",
        )

        val oneRolePerFile: Msg = Msg(
            ja = "1 Role 1 ファイルで書くか",
            en = "One Role per file?",
        )

        fun couldNotSetMeta(key: Any?, value: Any?, error: Any?): Msg = Msg(
            ja = "チェックリストの meta.$key を $value にできませんでした。data merge で直してください。\n$error",
            en = "Could not set meta.$key of the check list to $value. Fix it with data merge.\n$error",
        )

        fun preferencesSubcommandsShowSet(first: Any?): Msg = Msg(
            ja = "preferences のサブコマンドは show / set です（指定されたのは '$first'）",
            en = "The preferences subcommands are show / set (got '$first')",
        )

        fun notFoundRunInit(checkListUri: Any?): Msg = Msg(
            ja = "$checkListUri がありません。先に init を実行してください。",
            en = "$checkListUri not found. Run init first.",
        )

        val giveOneMoreKey: Msg = Msg(
            ja = "preferences set: key=value を 1 つ以上指定してください（例: preferences set nestedGroups=no）",
            en = "preferences set: give one or more key=value (for example: preferences set nestedGroups=no)",
        )

        fun preferencesSetTakesKey(kv: Any?): Msg = Msg(
            ja = "preferences set は key=value の形です: $kv",
            en = "preferences set takes key=value: $kv",
        )

        fun unknownKeyValidKeys(key: Any?, pREFERENCE_KEYS: Any?): Msg = Msg(
            ja = "知らないキーです: $key（使えるキー: $pREFERENCE_KEYS）",
            en = "Unknown key: $key (valid keys: $pREFERENCE_KEYS)",
        )

        fun invalidValueValidValues(key: Any?, value: Any?, validValues: Any?): Msg = Msg(
            ja = "$key の値が違います: $value（使える値: $validValues）",
            en = "Invalid value for $key: $value (valid values: $validValues)",
        )

        fun recorded(rest: Any?): Msg = Msg(
            ja = "記録しました: $rest",
            en = "Recorded: $rest",
        )

        val preferencesRecordOnlyWhat: Msg = Msg(
            ja = "書き方の方針（推奨から変えたいものだけ '$AGENT_COMMAND preferences set key=value' で記録してください）:",
            en = "Preferences (record only what you want to change from the recommendation with '$AGENT_COMMAND preferences set key=value'):",
        )

        val recordedLower: Msg = Msg(
            ja = "記録済み",
            en = "recorded",
        )

        val recommended: Msg = Msg(
            ja = "推奨",
            en = "recommended",
        )

        fun detectedFrom(from: Any?): Msg = Msg(
            ja = "検出: $from",
            en = "detected: $from",
        )

        val detectedCatalog: Msg = Msg(
            ja = "検出: $CATALOG_FILE",
            en = "detected: $CATALOG_FILE",
        )

        fun preferenceLine(key: Any?, value: Any?, state: Any?, question: Any?, values: Any?): Msg = Msg(
            ja = "$key = $value（$state）   # $question ($values)",
            en = "$key = $value ($state)   # $question ($values)",
        )
    }

    object Progress {
        val specifyOneMoreItem: Msg = Msg(
            ja = "check: 項目 id を1つ以上指定してください（例: check 1-1 1-2）",
            en = "check: specify one or more item ids (for example: check 1-1 1-2)",
        )

        val uncheckSpecifyItems: Msg = Msg(
            ja = "uncheck: 項目 id を1つ以上指定してください。",
            en = "uncheck: specify one or more item ids.",
        )

        fun unknownItemIdValid(unknown: Any?, known: Any?): Msg = Msg(
            ja = "知らない項目 id です: $unknown\n使える id: $known",
            en = "Unknown item id: $unknown\nValid ids: $known",
        )

        fun markedDone(ids: Any?): Msg = Msg(
            ja = "完了にしました: $ids",
            en = "Marked as done: $ids",
        )

        fun nextGuide(section: Any?): Msg = Msg(
            ja = "次: $AGENT_COMMAND guide $section",
            en = "Next: $AGENT_COMMAND guide $section",
        )

        fun markedNotDone(ids: Any?): Msg = Msg(
            ja = "未完了に戻しました: $ids",
            en = "Marked as not done: $ids",
        )

        val specifyStepIdExample: Msg = Msg(
            ja = "warn: ステップ id を指定してください（例: warn 4 \"違反を2件残しました\"）",
            en = "warn: specify the step id (for example: warn 4 \"Left 2 violations\")",
        )

        val specifyWarningTextClear: Msg = Msg(
            ja = "warn: 警告の文言を指定してください（消すなら --clear）",
            en = "warn: specify the warning text (or --clear to remove it)",
        )

        fun unknownStepIdValid(step: Any?, steps: Any?): Msg = Msg(
            ja = "知らないステップ id です: $step\n使える id: $steps",
            en = "Unknown step id: $step\nValid ids: $steps",
        )

        fun clearedWarningStep(step: Any?): Msg = Msg(
            ja = "ステップ $step の警告を消しました",
            en = "Cleared the warning on step $step",
        )

        fun setWarningStep(step: Any?): Msg = Msg(
            ja = "ステップ $step の警告を設定しました",
            en = "Set a warning on step $step",
        )

        fun notFoundRunInit(checkListPathUri: Any?): Msg = Msg(
            ja = "$checkListPathUri がありません。先に init を実行してください。",
            en = "$checkListPathUri not found. Run init first.",
        )

        val cannotReadJsonCheck: Msg = Msg(
            ja = "チェックリストの JSON を読めません。",
            en = "Cannot read the JSON of the check list.",
        )

        val cannotReadJsonReport: Msg = Msg(
            ja = "レポートの JSON を読めません。開始タグと閉じタグは、それぞれ行頭に単独で置かれている必要があります。",
            en = "Cannot read the JSON of the report. The opening and closing tags must each be alone at the start of a line.",
        )

        val noStepsTemplateBroken: Msg = Msg(
            ja = "check-list: steps がありません（テンプレートが壊れています）",
            en = "check-list: no steps (the template is broken)",
        )

        fun notDone(pyStrItem: Any?, label: Any?): Msg = Msg(
            ja = "check-list: 未チェック $pyStrItem $label",
            en = "check-list: not done $pyStrItem $label",
        )

        fun unfinishedBlank(missingSize: Any?): Msg = Msg(
            ja = "未完了・未記入が $missingSize 件あります。",
            en = "Unfinished or blank: $missingSize",
        )

        fun nothingUnfinishedBlankMarked(pyStrFinal: Any?): Msg = Msg(
            ja = "未完了・未記入はありません。ステップ $pyStrFinal を完了にしました。",
            en = "Nothing is unfinished or blank. Marked step $pyStrFinal as done.",
        )

        val stepsWarnings: Msg = Msg(
            ja = "警告つきのステップ:",
            en = "Steps with warnings:",
        )

        fun step(id: Any?, text: Any?): Msg = Msg(
            ja = "  - ステップ $id: $text",
            en = "  - step $id: $text",
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
            ja = "ユーザに確認してください（手順書 0-2 の「Kotlin が古いとき」）。",
            en = "Ask the user (\"When Kotlin is too old\" in step 0-2 of the guide).",
        )

        val scaffoldStepWillStop: Msg = Msg(
            ja = "このままでは 2-1 の scaffold で止まります。ステップ 1 の解析に入る前に、ユーザに確認してください（手順書 0-2 の「Kotlin が古いとき」）。",
            en = "As it is, scaffold in step 2-1 will stop. Ask the user before starting the analysis in step 1 (\"When Kotlin is too old\" in step 0-2 of the guide).",
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

        val toolchainversionGradleGradleDaemon: Msg = Msg(
            ja = "gradle/gradle-daemon-jvm.properties の toolchainVersion",
            en = "toolchainVersion in gradle/gradle-daemon-jvm.properties",
        )

        fun javaPath(onPath: Any?): Msg = Msg(
            ja = "PATH の java（$onPath）",
            en = "java on PATH ($onPath)",
        )

        val couldNotFindJdk: Msg = Msg(
            ja = "Gradle を動かす JDK を見つけられませんでした（JAVA_HOME も PATH の java もありません）。katachi は JDK $JDK_MIN_MAJOR 以上が要ります。",
            en = "Could not find the JDK that runs Gradle (there is no JAVA_HOME and no java on PATH). katachi needs JDK $JDK_MIN_MAJOR or later.",
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
            ja = "katachi $version はこの手順には古すぎます。手順書とこのコマンドが作る構成は katachi $min の機能（\"Name\".role { } など）で書いてあるため、$min 以降が必要です。\n" +
                "    $KATACHI_RELEASES_PAGE で $min 以降が出ているかを確かめ、--katachi <version> で指定してやり直してください。",
            en = "katachi $version is too old for this guide. The guide and the setup this command creates are written with katachi $min features (such as \"Name\".role { }), so they need $min or later.\n" +
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
        fun commandsAiAgentRuns(command: Any?): Msg = Msg(
            ja = "$command は '$AGENT_COMMAND $command' です（AI エージェントが手順書に従って使うコマンドは agent の下にあります）",
            en = "$command is '$AGENT_COMMAND $command' (the commands an AI agent runs for the install guide sit under agent)",
        )

        fun notSubcommandSeeHelp(command: Any?): Msg = Msg(
            ja = "$AGENT_COMMAND のサブコマンドではありません: $command（$AGENT_COMMAND --help を見てください）",
            en = "Not a subcommand of $AGENT_COMMAND: $command (see $AGENT_COMMAND --help)",
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

        fun moduleDirDiffersWhat(moduleDir: Any?, moduleRecorded: Any?): Msg = Msg(
            ja = "--module-dir $moduleDir は init の記録（meta.moduleDir = $moduleRecorded）と違います。記録を $moduleDir に書き換えます。",
            en = "--module-dir $moduleDir differs from what init recorded (meta.moduleDir = $moduleRecorded). The record is updated to $moduleDir.",
        )

        val cannotTellKatachiVersion: Msg = Msg(
            ja = "katachi のバージョンが分かりません。$KATACHI_RELEASES_PAGE を見て --katachi で指定してください。",
            en = "Cannot tell the katachi version. Look it up at $KATACHI_RELEASES_PAGE and pass it with --katachi.",
        )

        val couldNotDetectKotlin: Msg = Msg(
            ja = "Kotlin のバージョンを検出できませんでした。--kotlin で指定してください。",
            en = "Could not detect the Kotlin version. Pass it with --kotlin.",
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
            ja = "preferences.testFramework の値を知りません（$pref。junit5 / kotest / kotest-free など）。検出した値を使います。",
            en = "Unknown value in preferences.testFramework ($pref; expected junit5, kotest, kotest-free and so on). Using the detected value instead.",
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
            ja = "preferences.dependencyStyle の値を知りません（$pref。catalog か literal）。検出した値を使います。",
            en = "Unknown value in preferences.dependencyStyle ($pref; expected catalog or literal). Using the detected value instead.",
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

        fun recordedInit(version: Any?): Msg = Msg(
            ja = "katachi:      $version（init が記録したバージョン）",
            en = "katachi:      $version (recorded by init)",
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
            ja = "モジュール:   $moduleDirUri/（$moduleGradlePath。init が記録した meta.moduleDir）",
            en = "Module:       $moduleDirUri/ ($moduleGradlePath; meta.moduleDir recorded by init)",
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

        fun subprojectDeclaresKotlinPlugin(moduleGradlePath: Any?, kotlinPlugin: Any?): Msg = Msg(
            ja = "              サブプロジェクトが Kotlin プラグインをバージョン付きで宣言しているため、ルートには足さず $moduleGradlePath にバージョンを書きます（$kotlinPlugin）\n" +
                "              Gradle が「The Kotlin Gradle plugin was loaded multiple times ...」と警告しますが、この構成では想定どおりです",
            en = "              A subproject declares the Kotlin plugin with a version, so it is not added to the root; the version goes into $moduleGradlePath ($kotlinPlugin)\n" +
                "              Gradle warns \"The Kotlin Gradle plugin was loaded multiple times ...\"; that is expected with this setup",
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

        val nextTestArchitectureEmpty: Msg = Msg(
            ja = "次: $AGENT_COMMAND test\n    architecture { } が空なので、すべてのファイルが Unexpected として報告されて\n    失敗するのが正常です。Unexpected 以外のエラーが出た場合だけが問題です。",
            en = "Next: $AGENT_COMMAND test\n    architecture { } is empty, so every file is reported as Unexpected and the test\n    fails. That is expected. Only errors other than Unexpected are a problem.",
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

    object Summary {
        fun notFoundRunInit(checkListPathUri: Any?): Msg = Msg(
            ja = "$checkListPathUri がありません。先に init を実行してください。",
            en = "$checkListPathUri not found. Run init first.",
        )

        val cannotReadJsonCheck: Msg = Msg(
            ja = "チェックリストの JSON を読めません。",
            en = "Cannot read the JSON of the check list.",
        )

        val workingDirectory: Msg = Msg(
            ja = "<作業用ディレクトリ>/",
            en = "<working directory>/",
        )

        val workingDirectoryS: Msg = Msg(
            ja = "  作業用ディレクトリ: %s",
            en = "  Working directory: %s",
        )

        val workdirPlaceholder: Msg = Msg(
            ja = "<作業用ディレクトリ>",
            en = "<working directory>",
        )

        val couldNotSetUp: Msg = Msg(
            ja = "❌ katachi のセットアップができませんでした",
            en = "❌ Could not set up katachi",
        )

        val unfinishedItems: Msg = Msg(
            ja = "終わっていない項目:",
            en = "Unfinished items:",
        )

        val resume: Msg = Msg(
            ja = "再開:",
            en = "Resume:",
        )

        val doneS: Msg = Msg(
            ja = "  終わった項目: %s",
            en = "  Done: %s",
        )

        fun nextSectionGuide(section: Any?): Msg = Msg(
            ja = "  次のセクション: $AGENT_COMMAND guide $section",
            en = "  Next section: $AGENT_COMMAND guide $section",
        )

        fun definition(fileUriOfDefinition: Any?): Msg = Msg(
            ja = "  定義: $fileUriOfDefinition",
            en = "  Definition: $fileUriOfDefinition",
        )

        fun definitionNotYetScaffold(moduleDir: Any?): Msg = Msg(
            ja = "  定義: まだ無い（scaffold 未実行。meta.moduleDir = $moduleDir）",
            en = "  Definition: not yet (scaffold has not run; meta.moduleDir = $moduleDir)",
        )

        fun questionsReportAnsweredUnanswered(reportAnswered: Any?, reportUnansweredSize: Any?, clAnswered: Any?, clUnansweredSize: Any?): Msg = Msg(
            ja = "  確認事項: レポート 回答済み $reportAnswered / 未回答 $reportUnansweredSize、チェックリスト 回答済み $clAnswered / 未回答 $clUnansweredSize",
            en = "  Questions: report answered $reportAnswered / unanswered $reportUnansweredSize, check list answered $clAnswered / unanswered $clUnansweredSize",
        )

        val checkList: Msg = Msg(
            ja = "チェックリスト:",
            en = "Check list:",
        )

        val setUpKatachi: Msg = Msg(
            ja = "⚠️ katachi のセットアップを行いました",
            en = "⚠️ Set up katachi",
        )

        val setUpKatachiDone: Msg = Msg(
            ja = "✅ katachi のセットアップを行いました",
            en = "✅ Set up katachi",
        )

        val generatedCodeNeedsYour: Msg = Msg(
            ja = "**生成されたコードはあなたのレビューが必要不可欠** です。内容を確認してください。",
            en = "**The generated code needs your review.** Please check what it contains.",
        )

        fun checkListLine(checkListPath: Any?): Msg = Msg(
            ja = "- チェックリスト: $checkListPath",
            en = "- Check list: $checkListPath",
        )

        fun codebaseReport(inWorkdirREPORT_FILE: Any?): Msg = Msg(
            ja = "- コードベース レポート: $inWorkdirREPORT_FILE",
            en = "- Codebase report: $inWorkdirREPORT_FILE",
        )

        fun testResultArchitectureViolations(modulePath: Any?, violationsSize: Any?): Msg = Msg(
            ja = "- `$modulePath:test` の実行結果: ⚠️ $violationsSize 件のアーキテクチャ違反を残しています",
            en = "- `$modulePath:test` result: ⚠️ architecture violations left: $violationsSize",
        )

        fun testResultAllGreen(modulePath: Any?): Msg = Msg(
            ja = "- `$modulePath:test` の実行結果: ✅ All green",
            en = "- `$modulePath:test` result: ✅ All green",
        )

        val questionsYouCheckList: Msg = Msg(
            ja = "確認したいこと（チェックリスト）",
            en = "Questions for you (check list)",
        )

        val questionsUserCheckList: Msg = Msg(
            ja = "チェックリストの「ユーザに確認したいこと」",
            en = "\"Questions for the user\" in the check list",
        )

        val inconsistenciesCodebaseReport: Msg = Msg(
            ja = "コードベースの揺れ（レポート）",
            en = "Inconsistencies in the codebase (report)",
        )

        val questionsReport: Msg = Msg(
            ja = "レポートの questions",
            en = "questions in the report",
        )

        fun changedFiles(changedSize: Any?): Msg = Msg(
            ja = "- 変更したファイル: $changedSize 件",
            en = "- Changed files: $changedSize",
        )

        fun reviewProjectarchitectureKtUnder(uriModule: Any?): Msg = Msg(
            ja = "    - $uriModule/ 以下の ProjectArchitecture.kt を**レビュー**してください",
            en = "    - **Review** ProjectArchitecture.kt under $uriModule/",
        )

        val runOneRealImplementation: Msg = Msg(
            ja = "実際の実装タスクを1つ回して、定義が機能することを確かめてください",
            en = "Run one real implementation task and confirm that the definition works",
        )

        fun addTestCi(modulePath: Any?): Msg = Msg(
            ja = "CI に `$modulePath:test` を組み込んでください",
            en = "Add `$modulePath:test` to CI",
        )

        fun youCanSetUp(modulePath: Any?): Msg = Msg(
            ja = "ドキュメント生成をセットアップできます（`architecture { title / description }` を書き、`./gradlew $modulePath:katachiDocs` で生成。生成物をコミットするかを決めてください）",
            en = "You can set up documentation generation (write `architecture { title / description }` and generate with `./gradlew $modulePath:katachiDocs`; decide whether to commit the output)",
        )

        fun youCanSetUpTemplates(templatesSize: Any?): Msg = Msg(
            ja = "    - テンプレートからのコード生成をセットアップできます。提案は次の $templatesSize 件です（レポートの「テンプレートの提案」）",
            en = "    - You can set up code generation from templates. Suggestions: $templatesSize (\"Template suggestions\" in the report)",
        )

        val blank: Msg = Msg(
            ja = "(未記入)",
            en = "(blank)",
        )

        val noParams: Msg = Msg(
            ja = "なし",
            en = "none",
        )

        fun parameters(role: Any?, files: Any?, params: Any?): Msg = Msg(
            ja = "        - $role: $files（パラメータ: $params）",
            en = "        - $role: $files (parameters: $params)",
        )

        val confirmInstallationWorks: Msg = Msg(
            ja = "導入できたことを確かめる",
            en = "confirm that the installation works",
        )

        val addCi: Msg = Msg(
            ja = "CI に組み込む",
            en = "add it to CI",
        )

        val setUpDocumentationGeneration: Msg = Msg(
            ja = "ドキュメント生成をセットアップする",
            en = "set up documentation generation",
        )

        val setUpCodeGeneration: Msg = Msg(
            ja = "テンプレートからのコード生成をセットアップする",
            en = "set up code generation from templates",
        )

        val nextSectionPickOne: Msg = Msg(
            ja = "  次のセクション（ユーザの依頼に合わせて選ぶ）:",
            en = "  Next section (pick the one the user asked for):",
        )

        val fixWhatReviewPointed: Msg = Msg(
            ja = "    - レビューの指摘を直す: $AGENT_COMMAND guide 6-0",
            en = "    - Fix what the review pointed out: $AGENT_COMMAND guide 6-0",
        )

        fun answeredUnanswered(label: Any?, answered: Any?, unansweredSize: Any?, where: Any?): Msg = Msg(
            ja = "- $label: 回答済み $answered 件 / 未回答 $unansweredSize 件（$where）",
            en = "- $label: answered $answered / unanswered $unansweredSize ($where)",
        )

        fun recommended(recommendation: Any?): Msg = Msg(
            ja = " → 推奨: $recommendation",
            en = " → recommended: $recommendation",
        )
    }

    object Test {
        fun addedRemoved(added: Any?, removed: Any?): Msg = Msg(
            ja = "増えた $added 件 / 減った $removed 件",
            en = "Added $added / removed $removed",
        )

        fun moreSeeLog(diffSize: Any?): Msg = Msg(
            ja = "  ほか $diffSize 件（ログを見る）",
            en = "  and $diffSize more (see the log)",
        )

        fun notFoundSaveOutput(logUri: Any?, moduleGradlePath: Any?): Msg = Msg(
            ja = "$logUri がありません。./gradlew $moduleGradlePath:test の出力をこのパスに保存してください（手順書 3-2 / 3-3）。",
            en = "$logUri not found. Save the output of ./gradlew $moduleGradlePath:test to this path (steps 3-2 / 3-3 of the guide).",
        )

        fun hasNoCheckResult(logUri: Any?): Msg = Msg(
            ja = "$logUri に検査の結果がありません。コンパイルエラーなどで検査まで届いていません。直してから取り直してください。",
            en = "$logUri has no check result. The build did not get as far as the check (a compile error, for example). Fix it and capture the log again.",
        )

        fun notComparingLogShows(truncated: Any?): Msg = Msg(
            ja = "違反を先頭の数件だけ出したログがあるので、比べません。ログに出ていない違反が入れ替わっても気づけず、全件のログと比べると偽の差分が出るためです。\n" +
                "$truncated    打ち切られたほうを、全件で取り直してください。$AGENT_COMMAND test --save <名前> はいつも全件を出します（-Dkatachi.maxViolations=all を Gradle に渡す）。\n" +
                "    前のログ（before-refactor）を取り直すときは、定義をリファクタリング前に戻してから取ります。\n" +
                "    取り直しても打ち切られるなら、テストのコードの assert() に maxViolations が書いてあります（-D より優先される）。外してから取り直してください。",
            en = "Not comparing: a log shows only the first few violations. Violations missing from the log could swap without being noticed, and comparing with a full log shows a false difference.\n" +
                "$truncated    Capture the cut-off one again with every violation. $AGENT_COMMAND test --save <name> always prints them all (it passes -Dkatachi.maxViolations=all to Gradle).\n" +
                "    To capture the before log (before-refactor) again, put the definition back to how it was before the refactoring first.\n" +
                "    If it is still cut off, the test code passes maxViolations to assert() (that wins over -D). Remove it and capture again.",
        )

        fun before(beforeUri: Any?): Msg = Msg(
            ja = "前: $beforeUri",
            en = "Before: $beforeUri",
        )

        fun after(afterUri: Any?): Msg = Msg(
            ja = "後: $afterUri",
            en = "After:  $afterUri",
        )

        fun violationHeadingsBeforeAfter(a: Any?, b: Any?): Msg = Msg(
            ja = "違反の見出し: 前 $a 件 / 後 $b 件",
            en = "Violation headings: before $a / after $b",
        )

        fun missingdescriptionNotComparedBefore(countMissingDescriptionBefore: Any?, countMissingDescriptionAfter: Any?): Msg = Msg(
            ja = "[MissingDescription]（比べない）: 前 $countMissingDescriptionBefore 件 / 後 $countMissingDescriptionAfter 件",
            en = "[MissingDescription] (not compared): before $countMissingDescriptionBefore / after $countMissingDescriptionAfter",
        )

        val checkResultHasNot: Msg = Msg(
            ja = "検査結果は変わっていません。",
            en = "The check result has not changed.",
        )

        val checkResultHasChanged: Msg = Msg(
            ja = "検査結果が変わっています（- が前だけ、+ が後だけにある行）。",
            en = "The check result has changed (- lines only in before, + lines only in after).",
        )

        fun refactoringChangedBehaviorFix(moduleDir: Any?): Msg = Msg(
            ja = "リファクタリングで振る舞いが変わっています。前のログを取り直すのではなく、定義のほうを直してください。\n" +
                "分けた定義ファイルが [UnexpectedFile] になっているなら、$moduleDir/ を受け持つ Role の layout { } が\n" +
                "定義のファイルの最大の深さ（<group>/<sub>/<Role>Role.kt など）まで受け入れていません。",
            en = "The refactoring changed the behavior. Fix the definition instead of capturing the before log again.\n" +
                "If the split definition files show up as [UnexpectedFile], the layout { } of the Role that owns\n" +
                "$moduleDir/ does not accept files as deep as the deepest definition file (e.g. <group>/<sub>/<Role>Role.kt).",
        )

        fun unknownOptionPutArguments(it: Any?): Msg = Msg(
            ja = "test: 知らないオプションです: $it（Gradle に渡す引数は -- の後ろに）",
            en = "test: unknown option: $it (put arguments for Gradle after --)",
        )

        fun cannotFindProjectRoot(recordedRoot: Any?): Msg = Msg(
            ja = "プロジェクトルートが分かりません（チェックリストの meta.projectRoot: '$recordedRoot'）。init をやり直してください。",
            en = "Cannot find the project root (meta.projectRoot in the check list: '$recordedRoot'). Run init again.",
        )

        fun withoutGradleWrapperRun(gradlewUri: Any?, moduleGradlePath: Any?): Msg = Msg(
            ja = "gradlew がありません: $gradlewUri 。Gradle wrapper の無いプロジェクトでは、./gradlew $moduleGradlePath:test を自分で実行してログを tmp/test-<名前>.log に保存してください。",
            en = "gradlew not found: $gradlewUri . Without the Gradle wrapper, run ./gradlew $moduleGradlePath:test yourself and save the log as tmp/test-<name>.log.",
        )

        fun jdkUnknownCouldNot(source: Msg): Msg = Msg(
            ja = "JDK: 不明（${source.ja}。バージョンを読めませんでした）",
            en = "JDK: unknown (${source.en}; could not read the version)",
        )

        val jdkUnknownNoJava: Msg = Msg(
            ja = "JDK: 不明（JAVA_HOME も PATH の java もありません。Gradle 自身の設定に任せます）",
            en = "JDK: unknown (no JAVA_HOME and no java on PATH; leaving it to Gradle)",
        )

        fun log(logUri: Any?): Msg = Msg(
            ja = "ログ: $logUri",
            en = "Log: $logUri",
        )

        fun copy(copyUri: Any?): Msg = Msg(
            ja = "写し: $copyUri",
            en = "Copy: $copyUri",
        )

        fun resultNoViolationsBut(status: Any?): Msg = Msg(
            ja = "結果: 検査の違反は出ていませんが、検査用モジュールのほかのテストが失敗しています（Gradle の終了コード $status）。ログの FAILED の行:",
            en = "Result: no violations, but other tests in the verification module failed (Gradle exit code $status). FAILED lines in the log:",
        )

        fun resultCheckNotReached(status: Any?): Msg = Msg(
            ja = "結果: 検査まで届いていません（コンパイルエラーなど。Gradle の終了コード $status）。ログの末尾:",
            en = "Result: the check was not reached (a compile error, for example; Gradle exit code $status). Tail of the log:",
        )

        fun result(result: Any?): Msg = Msg(
            ja = "結果: $result",
            en = "Result: $result",
        )

        val resultCheckPassedViolations: Msg = Msg(
            ja = "結果: 検査は通りました（違反 0 件）",
            en = "Result: the check passed (0 violations)",
        )

        fun missingdescriptionGoesDownDescriptions(missing: Any?): Msg = Msg(
            ja = "[MissingDescription]: $missing 件（説明を書けば減る）",
            en = "[MissingDescription]: $missing (goes down as descriptions are written)",
        )

        fun warningsTheyNeverFail(warnings: Any?): Msg = Msg(
            ja = "警告: $warnings 件（検査は失敗させない。内容を見て、直すか残すかを決める）",
            en = "Warnings: $warnings (they never fail the check; read them and decide whether to fix or keep each)",
        )

        val differencePreviousRunVs: Msg = Msg(
            ja = "前回との差分（previous.log 比）: 比べません（違反を先頭の数件だけ出したログがあるため）",
            en = "Difference from the previous run (vs previous.log): not compared (a log shows only the first few violations)",
        )

        val diffFromPreviousNone: Msg = Msg(
            ja = "前回との差分（previous.log 比）: 無し",
            en = "Difference from the previous run (vs previous.log): none",
        )

        val diffFromPreviousPrefix: Msg = Msg(
            ja = "前回との差分（previous.log 比）: ",
            en = "Difference from the previous run (vs previous.log): ",
        )

        fun omittedEitherDkatachiMaxviolations(omitted: Any?): Msg = Msg(
            ja = "省略: $omitted。-Dkatachi.maxViolations に数を渡したか、テストの assert() に maxViolations が書いてあります（全件を出すには、どちらも外す）",
            en = "Omitted: $omitted. Either -Dkatachi.maxViolations was given a number, or the test passes maxViolations to assert() (remove both to see everything)",
        )

        fun jdk(major: Any?, source: Msg): Msg = Msg(
            ja = "JDK: $major（${source.ja}）",
            en = "JDK: $major (${source.en})",
        )
    }

    object Workdir {
        val cannotFindWorkingDirectory: Msg = Msg(
            ja = "作業用ディレクトリが分かりません。先に '$AGENT_COMMAND init' を実行してください。別の場所から実行するときは環境変数 KATACHI_WORKDIR で指定してください。",
            en = "Cannot find the working directory. Run '$AGENT_COMMAND init' first. When running from somewhere else, point KATACHI_WORKDIR at it.",
        )

        fun moduleDirMustPath(value: Any?): Msg = Msg(
            ja = "--module-dir は Gradle ルートからの相対パスです（例: static-analysis/katachi）: $value",
            en = "--module-dir must be a path relative to the Gradle root (for example static-analysis/katachi): $value",
        )

        fun definitionDirMissing(dirUri: Any?, moduleDir: Any?): Msg = Msg(
            ja = "定義のディレクトリがありません: $dirUri 。scaffold をまだ実行していないか、置き場所が違います（記録: meta.moduleDir = $moduleDir。変えるなら 'preferences set moduleDir=<path>' か 'scaffold --module-dir <path>'）",
            en = "The definition directory does not exist: $dirUri . Either scaffold has not run yet, or the location differs (recorded: meta.moduleDir = $moduleDir; change it with 'preferences set moduleDir=<path>' or 'scaffold --module-dir <path>')",
        )

        fun workdirAmbiguous(candidates: Any?): Msg = Msg(
            ja = "作業用ディレクトリの候補が複数あります。環境変数 KATACHI_WORKDIR でどれかを指定してください: $candidates",
            en = "More than one working directory was found. Point KATACHI_WORKDIR at one of them: $candidates",
        )
    }

    object ScaffoldBuildFile {
        val cacheNote: Msg = Msg(
            ja = "    // このタスクはキャッシュさせない。katachi は実行時にリポジトリ全体を歩くが、\n" +
                "    // Gradle から見える入力はこのモジュールのテストソースと classpath だけ。他の場所で\n" +
                "    // ファイルが増減してもキーが変わらないので、UP-TO-DATE / FROM-CACHE になって\n" +
                "    // 検査が一度も走らない。黙って通るガードは、ガードが無いより悪い。",
            en = "    // Never let this task be cached. katachi walks the whole repository when the test\n" +
                "    // runs, but the only inputs Gradle can see are the test sources and the classpath of\n" +
                "    // this module. A file added or moved anywhere else leaves the key unchanged, so Gradle\n" +
                "    // answers UP-TO-DATE or FROM-CACHE and the check never runs. A guard that silently\n" +
                "    // passes is worse than no guard at all.",
        )

        val logNote: Msg = Msg(
            ja = "        // 違反の一覧は AssertionError のメッセージに入っている。FULL にしないと\n" +
                "        // \"KatachiArchitectureAssertionError at ProjectArchitectureTest.kt:12\" の1行しか出ず、\n" +
                "        // 中身を見るのに build/test-results/**/*.xml を読む羽目になる（CI のログでも同じ）。",
            en = "        // The violation list lives in the AssertionError message. Without FULL you only get\n" +
                "        // \"KatachiArchitectureAssertionError at ProjectArchitectureTest.kt:12\", and reading the\n" +
                "        // detail means opening build/test-results/**/*.xml (the same goes for CI logs).",
        )

        val stdNote: Msg = Msg(
            ja = "        // MissingDescription などの警告は stdout に出る。",
            en = "        // Warnings such as MissingDescription are printed to stdout.",
        )

        val pluginNote: Msg = Msg(
            ja = "    // processor ごとのタスク（katachiDocs・katachiTemplate など）を足す。\n    // katachi の依存は足さないので、下の dependencies { } は別に要る。",
            en = "    // Adds a task per processor (katachiDocs, katachiTemplate, and so on). It does not add\n    // katachi as a dependency, so the dependencies { } block below is still needed.",
        )

        val archNote: Msg = Msg(
            ja = "    // architecture { } を持つトップレベル val の完全修飾名。名前やパッケージを\n    // 変えたらここも直す。",
            en = "    // The fully qualified name of the top-level val holding architecture { }. Update it\n    // if you rename the val or move it to another package.",
        )

        val contextNote: Msg = Msg(
            ja = "    // Kotlin 2.4 未満では、context parameters を呼ぶ側にこのオプションが要る。\n" +
                "    // katachi の DSL（mainSourceSet / ktFile など）はすべて context parameters\n" +
                "    // なので、無いと1つも書けない。Kotlin を 2.4 以降に上げたらこの2行は消すこと\n" +
                "    // （2.4 以降で付けたままだと redundant の警告が出る）。",
            en = "    // Kotlin before 2.4 needs this on the calling side to use context parameters.\n" +
                "    // Every katachi DSL entry point (mainSourceSet / ktFile ...) is a contextual\n" +
                "    // declaration, so without it you cannot write a single one. Drop these two lines once\n" +
                "    // the project moves to Kotlin 2.4 or later - from 2.4 on the flag warns that it is\n" +
                "    // redundant.",
        )

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
      --katachi <version> katachi のバージョン（デフォルト: init が記録したバージョン。
                          無ければこの katachi コマンドのバージョン）
      --kotlin <version>  Kotlin のバージョン（デフォルト: プロジェクトから検出）
      --no-konsist        katachi-konsist を使わない構成で生成する（ktFileContent { } が書けない）。
                          assert() に FileConstraintCheck() を渡さないので、gradle { } の hook に
                          書いた fileConstraint も評価されず、test は [UncheckedFileConstraint] で
                          失敗する（How to fix: Pass FileConstraintCheck() to assert()）
      --module-dir <path> 検査用モジュールの置き場所（デフォルト: init が記録した meta.moduleDir。
                          無ければ architecture-test）。指定すると meta.moduleDir を書き換える
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

  katachi lint
      定義（検査用モジュールの src/test/kotlin）を読み、種類の違うファイルを1つの
      Role に抱えていそうなもの、Gradle のファイルを手書きで宣言した Role（制約を持つものは
      gradle { } の hook への移し方も出す）、平たい group、分けていないファイル、
      documented = false の候補を列挙する。agent check 3-2 / agent check 3-3 でも自動で走る。
      素朴なテキスト走査なので誤検知があり得る。点検の結果では失敗にしない（0 を返す）。
      定義のディレクトリが無ければ（scaffold 前、置き場所の食い違い）1 を返す。

  katachi <ファイル>.katachi.kts [<引数>...]
      katachi のコマンドを Kotlin で並べたスクリプトを実行する。katachi と
      defaultPackage などのデフォルト値は最初から使える（kotlin コマンドが要る）。
      引数はスクリプトの args に入る。

  katachi agent <サブコマンド> ...
      AI エージェントが導入の手順書（katachi agent guide）に従って使うコマンド:
      init / guide / docs / data / add / preferences / answers / check / uncheck /
      warn / test / compare-violations / verify / summary。一覧は katachi agent --help。

  katachi --version
  katachi --help

scaffold は Gradle のルートディレクトリで実行してください。
""".trimIndent(),
            en = """
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

Run scaffold in the Gradle root directory.
""".trimIndent(),
        )

        val agent: Msg = Msg(
            ja = """
katachi agent — AI エージェントが導入の手順書に従って使うコマンド

手順書（katachi agent guide）の各ステップがこれらを呼びます。人が導入するときは
katachi doctor / katachi scaffold / katachi lint（katachi --help）を使ってください。

使い方:
  katachi agent init [オプション]
      Gradle のルートであることを確かめ、作業用ディレクトリを作り、
      チェックリストとレポートのテンプレートを配置する。
      最後に KEY=VALUE 形式で結果を出力する（後続のステップがこれを読む）。

      --workdir <path>    作業用ディレクトリを明示する（デフォルト: 自動判定）
      --katachi <version> katachi のバージョンを明示する（デフォルト: この katachi コマンドのバージョン。やり直しでは
                          前回の init が記録したバージョンを使い続ける）
      --offline           ネットワークに出ない（テンプレートと手順書は同梱なので、無くても揃う）
      --force             記入済みのチェックリスト・レポートも取り直す
      --lang <en|ja>      チェックリスト・レポート・このコマンドの出力の言語（デフォルト: en）。
                          作業用ディレクトリに記録され、以降のコマンドが従う
      --module-dir <path> 検査用モジュールの置き場所。Gradle ルートからの相対パス
                          （デフォルト: architecture-test。例: static-analysis/katachi）。
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
      key: nestedGroups (yes/no) / moduleDir (パス) / testFramework (junit5/kotest/kotest-<style>) /
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
      全文を読まずに済ませるためのもの。古いバージョンの写しは含めない。

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
      デフォルトは <作業用ディレクトリ>/tmp/test-before-refactor.log と test-after-refactor.log。
      [MissingDescription] の警告は、説明を書き足せば減るのが正しいので件数だけ出す。
      変わっていなければ 0、変わっていれば差分を出して 1 を返す。
      どちらかが違反を先頭の数件だけ出したログ（Showing first ...）なら、比べずに 2 を返す。

  katachi agent test [--save <名前>] [-- <gradle の引数>...]
      検査用モジュールのテスト（./gradlew <module>:test）を実行し、要約だけを出す。
      ログの全文は <作業用ディレクトリ>/tmp/test/<UTC 時刻>.log（latest.log にも写し、
      前の latest.log は previous.log に回す）。--save <名前> で tmp/test-<名前>.log にも写す
      （手順書 3-2 は before-refactor、3-3 は after-refactor。compare-violations のデフォルトと同じ）。
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
""".trimIndent(),
            en = """
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
      key: nestedGroups (yes/no) / moduleDir (path) / testFramework (junit5/kotest/kotest-<style>) /
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
""".trimIndent(),
        )
    }
}
