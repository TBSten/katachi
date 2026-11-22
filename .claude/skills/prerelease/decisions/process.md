# process

チェックの進め方そのものの決まり（オーケストレータが読む）。形は [README.md](README.md)。

## 方針: クラウドで先に回し、Android サンプルと IDE プラグインと 9-2 だけ Mac で回す
- decision: 方針
- decided: 2026-11-09 261109-01 KATACHI-322
- by: 利用者（322 のスレッド）

クラウドで回せる手順を先に回し、届かないもの（`checkSampleAndroid`・10-1 nightly 相当・10-2 E2E・`checkIdePlugin`）と、外部のリポジトリで Gradle を走らせる 9-2 は Mac で回す（9-2 はクラウドの自動承認が「外部のコード」として止める）。Mac に回す分は最初に利用者へ伝える。

## 方針: publish はリリース前チェックの場でしない
- decision: 方針
- decided: 2026-11-10 261109-01 KATACHI-322
- by: 利用者（TODO.html、02:58 JST）

チェックは publish の手順と zip の change-notes を確かめるところまで。publish そのものは、ドキュメントの更新が終わってから別の場で行う。チェックの中で publish を進めたり、「publish してよいか」を聞いたりしない（TODO.html の「4. 操作してほしい」に利用者の作業として載せるのはよい）。

## 方針: 手順 11 の p4 以下は、表ではなく Review の popup で人に選んでもらう
- decision: 方針
- decided: 2026-11-09 261109-01 KATACHI-330
- by: 利用者（322 のスレッド「popup 的に出す viewer」）

細かな docs の指摘は、TODO.html や .md の表に並べず、`<置き場>/review/prerelease-<回>-step11.md` に登録して dev サーバーの Review で見てもらう（形は `docs/src/integrations/feedback/review/README.md`、1 件 1 か所）。登録の前に `decisions.py match --drop` で過去の判断と同じものを外す。

## 方針: Review に登録する指摘には、ユーザ影響の札と選択肢ごとの推奨度を付ける
- decision: 方針
- decided: 2026-11-10 KATACHI-351
- by: 利用者（「review ツールは ユーザ影響の有無をタグ的につける / サジェストされた各選択肢は推奨度を小さく薄い字で出す」）

prerelease check などで Review に指摘を登録するときは、1 件ごとに `- impact: あり|なし`（理由を添える）と `- recommend: <ボタンの名前> 0〜10, …` を必ず書く。
推奨度は popup のボタン（`解決済みにする`・`直さない`・`直してもらう`・人が足した選択肢）のうち、意味のあるものだけに付ける。基準は [references/docs-rules.md](../references/docs-rules.md) の「Review に登録する」。
登録したら `cli.mjs review check` を回し、問題 0 件・「注意」0 件にする。

## 方針: skills/katachi/ への指摘は聞かずに直す
- decision: 方針
- decided: 2026-11-13 261112-01 KATACHI-368
- by: 利用者（368 のスレッド、00:49 JST）

利用者の一言: 「skills/katachi に対する指摘は一律直して」

`skills/katachi/`（SKILL.md・references・assets のチェックリスト）宛ての指摘は、TODO.html や Review で利用者に選ばせず、その回の修正 PR で直す。Review には登録しない（登録済みのものは直したら remove）。docs の本文は人が書く決まりのままで、この方針は skill だけに当てはまる。ガイドと skill のどちらに決まりを置くかという指摘（ルールの置き場）は、skill を直す話ではないのでこの方針に入れない。

## 方針: 探索的検証（手順 13）を prerelease と週替わりの nightly の両方で回す
- decision: 方針
- decided: 2026-11-20 KATACHI-402
- by: 利用者（決定カード「探索的検証をどこから入れる？」で「A と B を同時に始める」）

prerelease の手順 13 として、コンポーネント別のチャーターで利用者目線の探索的検証を回す（クラウド先、IDE plugin は Mac で人が 15 分）。
あわせて、クラウドの定期実行で週に 1 コンポーネントずつ回す（[exploratory-nightly.md](../references/exploratory-nightly.md)。修正 PR は作らず、p7 以上は `fix:` チケットにする）。
提案は A（prerelease だけ・推奨度 9/10）だったが、利用者が費用とノイズの様子見も同時に進めることを選んだ。
一巡（6 週）の集計で続けるか・頻度を変えるかを利用者に聞く。

## 方針: 9-2 で Mac の Notion チケットの hook が書き込みを止めたら、Mac 側でその回のチケットを掴んでよい
- decision: 方針
- decided: 2026-11-20 261120-01 KATACHI-404
- by: 利用者（判断カード「Mac の 9-2 で止まった書き込みをどう通しますか?」で「Mac 側で掴んでよい」）

Mac の `~/dev/katachi` には、いま掴まれている Notion チケット（`.local/notion-ticket/` の状態）を見て Write・Edit を止める hook がある。9-2（手順書どおりの導入）の subagent が定義を書くところでこれに止められたら、`ticket.py set <その回の prerelease チケット> --status AI:実装中` でその回のチケットを掴んで続け、9-2 が終わったら元のチケットに戻す（元のチケットのステータスは変えない）。聞き直さない。
Mac の auto mode は `ticket.py set` の書き込みも止めるので、そのときは利用者にターミナルで実行してもらう（コマンドをそのまま渡す）。9-2 を始める前に掴んでおけば、途中で止まらない。

## このまま: `katachi <file>.katachi.kts` が、中のコマンドの失敗を終了コードで伝えない
- decision: このまま
- file: exploratory:cli-script
- quote: 終了コードは 0
- decided: 2026-11-20 261120-01 KATACHI-404
- by: 利用者（スレッド、22:35 JST、TODO.html の問いへの答え）

手順 13（探索的検証 1 周目 CLI/script）の p7。スクリプトの中の `execute()`・`executeCommands()` は終了コードを返すだけで、`exitProcess` を書かないと `katachi ci.katachi.kts` は 0 で終わる（CI に書くと、検査が失敗しても緑になる）。
(a) 最後の式が数値ならそれを katachi の終了コードにする / (b) docs の katachi-script のページに `exitProcess(executeCommands(…))` の書き方を足す / (c) このまま、と並べて聞き、(b) になった。katachi の挙動は変えない。docs は人が書くので、書き方の案を Review（prerelease-5-docs.md）に載せた。
次のチェックでは、この挙動を発見として出さない。docs に書き方が無いままなら、docs の指摘としてだけ出す。

## このまま: Kotlin 2.2 のプロジェクトに katachi-konture を入れるとコンパイルが通らない
- decision: このまま
- file: exploratory:konsist-konture
- quote: The binary version of its metadata is 2.4.0, expected version is 2.2.0.
- decided: 2026-11-20 261120-01 KATACHI-404
- by: 利用者（スレッド、22:35 JST「これはまあこのままでいいや」）
- revisit: katachi-konture が持ち込む Konture の版を変えたとき

手順 13（探索的検証 1 周目 konsist/konture）の p7。katachi-konture が持ち込む Konture 1.0.1 は Kotlin 2.4 のメタデータで作られていて、Kotlin 2.2 では読めない（2.3 では通る）。README とリリースノートは「Kotlin 2.2+」のままで、konture の下限は Module.md にだけある。
(a) README・`konture { }` の KDoc・リリースノートにも「Kotlin 2.3 以上」と書く / (b) Konture の版を下げる / (c) このまま、と並べて聞き、(c) になった。理由の一言なし。次のチェックでは出さない。

## このまま: gradle-plugin: ルートの `subprojects { }` が `test` を空にして別の Test タスクへ回すプロジェクト（Exposed の `testDb`）や、タグで絞るプロジェクトでは、katachi の検査が 1 度も走らないのに BUILD SUCCESSFUL になり、`agent test` は「検査は通りました（違反 0 件）」で 0 を返す
- decision: このまま
- file: exploratory:realworld
- quote: 結果: 検査は通りました（違反 0 件）
- decided: 2026-11-21 261120-01 KATACHI-404
- by: 利用者（TODO.html、02:07 JST）

手順 13（探索的検証 2 周目 実在の OSS）の p9。Exposed のように `test` を filter で空にして DB ごとの Test タスクへ回すビルドや、ルートで `useJUnitPlatform { includeTags("unit") }` を掛けるビルドに scaffold で入れると、katachi のテストが 0 件のまま BUILD SUCCESSFUL になる（CI の `./gradlew build` も緑）。
(a) katachi のテストのある Test タスクで検査が 1 度も走らなかったら、そのタスクを失敗にする / (b) 警告だけ出す / (c) plugin は変えない、と並べて聞き、(c) になった。理由の一言なし。`agent test` の側は、別のスレッドの PR #129 で `agent test` 自体が無くなる。
次のチェックでは、この挙動を発見として出さない。

## このまま: core: `.module { }` を移行表どおり `"core/data" { }`・`"feature" / capture("feature") / "api" { }` と `gradle()` に直すと、「そこが Gradle のモジュールであること」の検査が黙って消える（固定キーのモジュールを消しても、build script の無いディレクトリを置いても緑）
- decision: このまま
- file: exploratory:migration
- quote: `.module { }` also declared each module's `build.gradle(.kts)` and `build/`: declare them with `gradle()`
- decided: 2026-11-21 261120-01 KATACHI-404
- by: 利用者（TODO.html、02:09 JST）

手順 13（探索的検証 2 周目 v0.3 からの移行）の p9。v0.3 の `":core:data".module { }` はモジュールの build script を必須にしていたが、0.4 の `gradle()` は見つかったモジュールの build script を「置いてよい」と宣言するだけなので、固定の名前のモジュールを消しても緑になる。
(a) リリースノートの `.module` の段落に「`gradle()` はモジュールの build script を必須にしない」と書く（`gradle()` の「モジュールの一覧を 2 度書かない」設計のまま）/ (b) Role の layout に「このディレクトリは Gradle のモジュール」と書ける口を足す / (c) 固定の名前で書いたモジュールの build script と `gradle()` が重なっても `[AmbiguousLayout]` にしない、と並べて聞き、(a) になった。リリースノートの下書きには書き足し済み。
次のチェックでは、この挙動を発見として出さない。リリースノートの段落が消えていたら、リリースノートの指摘としてだけ出す。

## 直さない: CLI・script: ルートの `subprojects { }` が `release = 8`・`jvmTarget = 1.8` を全モジュールに掛けるプロジェクトでは、scaffold した検査用モジュールが最初の実行で「katachi は JVM 17 以上」と言って解決できない
- decision: 直さない
- file: exploratory:realworld
- quote: Dependency resolution is looking for a library compatible with JVM runtime version 8, but 'me.tbsten.katachi:katachi' is only compatible with JVM runtime version 17 or newer.
- decided: 2026-11-22 261120-01 KATACHI-404
- by: 利用者（チャット、00:32 JST「これはこれで許容として直さないし今後も直す予定はない」。直前に出した p7 の詳細の件）

手順 13（探索的検証 3 周目 実在の OSS）の p7。moshi・clikt・kotlinpoet のように Java 8 互換を保つライブラリは、ルートの `subprojects { }` で全モジュールに 8 向けの設定を掛ける。scaffold が作る `architecture-test` にも掛かり、katachi が JVM 17 以上向けなので、最初の `./gradlew :architecture-test:test` が依存の解決で止まる。
(a) scaffold が生成する build.gradle.kts に release・jvmTarget を 17 に戻す設定を最初から書く / (b) ルートの設定を見つけたときだけ書く / (c) 書かずに止まる文面で案内する、を並べて聞いたが、どれも選ばず「許容して直さない」になった。理由の一言は「これはこれで許容」。
回避策は、検査用モジュールの build.gradle.kts に `release`・`jvmTarget` を 17 に戻す設定を足すこと（Mac で試して、解決・コンパイル・検査の実行まで通った）。再現は Mac の tmp/exploratory/wide-codex-r4/projects/jvm8-repro、結果は logs/repro8.out。
次のチェックでは、この挙動を発見として出さない。

## 直さない: konture: Konture 単体の inbound の規則（`onlyBeAccessedByAnyPackage`・`notBeAccessedByAnyPackage`）を Role の `konture { }` へ移すと、Role の外からの参照が見えず黙って緑になる
- decision: 直さない
- file: exploratory:konsist-konture
- quote: onlyBeAccessedByAnyPackage
- decided: 2026-11-23 261120-01 KATACHI-404
- by: 利用者（チャット、00:58 JST「まあこれは治さずでok」。直前の判断カードで選んだ (a) は、`ClassesShould`・`ClassesRuleBuilder` が final で shadow できず実装できないと伝えたあとの答え）

手順 13（探索的検証 3 周目 konsist/konture）の p7。Role の `konture { }` は覆ったファイルしか見ないので、inbound の規則は Role の外からの参照を数えられず、Konture 単体なら落ちる違反が緑になる。
(a) `rule(id)` などと同じく shadow してコンパイルエラーにする（実装できない）/ (b) inbound だけ全ファイルのグラフで数える / (c) Module.md と移行の案内に書くだけ、を並べ、直さないことになった。理由の一言は「まあこれは治さずで」。
次のチェックでは、この挙動を発見として出さない。
