# 9. インストールキットのチェック

[SKILL.md](../SKILL.md) の手順 9 の詳細。9-1（合成の fixture でスクリプトの配線を確かめる）と 9-2（手順書どおりに実在の
プロジェクトへ導入する統合テスト）の2段。9-1 はスクリプトが壊れていないかしか見ない。手順書の抜け・曖昧さ・スクリプトとの
食い違いは、エージェントが実際に手順書を読んで最後までやらないと出てこないので 9-2 で見る。

## 9-1. fixture でスクリプトの配線を確かめる

AI エージェント向けのインストールキット（`docs/public/install/` の `katachi-install.sh` と手順書）が、利用者のいろいろな形の
Gradle プロジェクトで動くかを確かめる。キットは `docs/` の下にあり CI の検査対象外なので、壊れても誰も気づかない。
docs/AGENTS.md の「`public/install/`」の節が「スクリプトを直したら実際の Gradle プロジェクトで動かす」と求めている3種に、
これまでに不具合が出た形を足して、毎回まとめて動かす。

```shell
pgrep -fl GradleWrapperMain                                                                          # ほかの Gradle が走っていないこと
sh .claude/skills/prerelease/scripts/check-install-kit.sh --release-dir .local/release-v<版>        # 走らせる（15〜20 分）
sh .claude/skills/prerelease/scripts/check-install-kit.sh --release-dir .local/release-v<版> --only agp,rootjvm   # 一部だけ
sh .claude/skills/prerelease/scripts/check-install-kit.sh --release-dir .local/release-v<版> --lang en --only agp,groovyinc   # 英語の出力（日本語が残れば NG）
```

- やること（スクリプトの冒頭のコメントに詳細）:
    1. `sh -n` と `/bin/bash -n`（macOS の bash 3.2）で `katachi-install.sh` の構文を確かめる
    2. リポジトリの katachi を mavenLocal に publish する（`publishToMavenLocal -Pkatachi.skipSigning`）
    3. 合成の Gradle プロジェクト（fixture。定義は `scripts/install-kit-fixtures.sh`）ごとに、順番に
       `KATACHI_DOCS=file://<repo>/docs/public`・`KATACHI_MAVEN_LOCAL=1` で `init --lang ja --katachi <今回の版>` →
       `scaffold` → `./gradlew :architecture-test:test`。そのあとルートにファイルを1つ足して、`--rerun` なしでもう一度 test
- 判定（fixture ごと。1つでも NG の欄があれば NG）:
    - init・scaffold: 終了コードが 0。init が Kotlin の版を検出できなかったときだけ、手順書どおり `--kotlin` を渡す
    - settings の include: `architecture-test` の include が1回だけ、最後の include 文の直後（include が無ければ末尾）に入った
    - test（配線）: **「定義していないファイルが Unexpected として出て落ちる」なら OK**（`architecture { }` が空なので、
      これが正常）。構文エラー・プラグインの衝突・解決できない・Unexpected 以外の違反で落ちたら NG。通ってしまっても NG
    - 2回目が走ったか: test タスクが UP-TO-DATE にならずに実行され、足したファイルが Unexpected として出た
    - settings に `dependencyResolutionManagement` が無い fixture は、scaffold の警告どおり mavenLocal() と mavenCentral() を
      足してから test を走らせる（表の「形」の欄に注記が出る）
- fixture（足すときは `install-kit-fixtures.sh` の `FIXTURES` と3つの関数を揃える）:

  | fixture      | 形                                                                                     |
  |--------------|----------------------------------------------------------------------------------------|
  | agp          | AGP + version catalog                                                                  |
  | noroot       | ルートの build ファイル無し（Kotlin は gradle.properties の版だけ）                    |
  | rootjvm      | Kotlin JVM が既にルートに居る（include 無し）                                          |
  | subonly      | サブプロジェクトだけが版付きで Kotlin を宣言（kdoctor の形）                           |
  | groovyinc    | Groovy DSL の複数行 include                                                            |
  | multiinc     | Kotlin DSL の複数行 `include(`（後ろにブロック）                                       |
  | withid       | ルートに `plugins.withId("org.jetbrains.kotlin.jvm")` の文字列だけ                     |
  | buildscript  | buildscript の classpath に kotlin-gradle-plugin                                       |
  | catalogalias | catalog の別名の Kotlin（`jvm = { id = "org.jetbrains.kotlin.jvm" }`）                 |

- 出力: `.local/release-v<版>/install-kit.md` に結果の表（fixture / 形 / init / scaffold / settings の include /
  test（配線）/ 2回目が走ったか / 結果 / ログ）。fixture とログは `.local/release-v<版>/tmp/install-kit/`。
  1つでも NG なら終了コード 1
- **オーケストレータが自分で走らせてよい**（判断の要らない作業。background で起動し、終わったら結果の表だけ読む）
- Gradle を使う。8・6-1・5 のサンプルを動かす作業とは重ねない。fixture の project cache は
  `.local/tmp/gradle-cache/prerelease-install/<fixture>`
- `docs/public/install/` の中身は直さない。NG の原因がキットの不具合なら警告として報告する
- 1つでも NG なら priority 10 の警告にする（利用者がインストールで詰まる状態）。NG の欄とログの末尾から原因を1〜2行で書く
- prerelease-check-list.md には、通った数 / 全体と、NG の fixture だけを書く

## 9-2. 統合テスト（手順書どおりに実在のプロジェクトへ導入する）

9-1 が通ってから（mavenLocal に今回の版がある状態で）始める。6-1 で配信しているサイト（`<配信元>/install/...`）を手順書の
配信元に使うので、**9-2 が終わるまで 6-1 の配信を止めない。**

subagent（model: opus。手順書を読んで判断しながら進める）を並列に3体。どれも
[install-kit-e2e-brief.md](install-kit-e2e-brief.md) を読ませ、`<配信元>` を実際の URL に置き換えて渡す。

| 担当 | やること | 対象 |
|---|---|---|
| ja で導入 | 利用者に「このプロジェクトに katachi を入れて。手順は `<配信元>/install/ja/index.md`」と頼まれたエージェントとして、手順書を書いてあるとおりに最後まで（6 の返答、6-C・6-D も） | `.local/sample-app/backup/` の KMP のプロジェクトの写し（例: kmp-app-template） |
| en で導入 | 同じく英語の手順書で最後まで（6-B は CI のファイルを書くまで、6-C も）。英語版に日本語が残っていないか、原本とのずれも見る | `.local/sample-app/backup/` の Android のプロジェクトの写し（例: nav3-recipes） |
| 手順書のレビュー | 読むだけ。手順書とスクリプト（usage・引数・出力・エラー）、手順書とチェックリストの項目、迷う・誤解する箇所、古い API、ja と en の対応 | `docs/public/install/` 一式 |

- 写しは `.local/release-v<版>/tmp/install-e2e/<担当>/project` に `rsync -a --exclude build --exclude .gradle --exclude .kotlin`
  で作り、中で `git init`（無いと `gitTracked()` が 0 件になる）。原本には触らない
- 対象のプロジェクトは毎回同じでなくてよい。前回と違う形（モジュールの数、Groovy / Kotlin DSL、version catalog の有無）を選ぶと
  取りこぼしが減る。前回の結果は前回の作業場所の `install-e2e.md`
- 各担当は `tmp/install-e2e/<担当>/report.md` に所見を `### [WARN / priority n/10]` の形で書く（スクリプトの不具合・手順書の問題・
  訳の問題を分ける）。**直させない**。subagent がハーネスの制約でファイルを書けないことがあるので、報告の本文に所見を全部
  載せさせ、書けなかったときはオーケストレータがその本文を report.md に保存する
- 1体 30〜45 分（コードベースを読んで定義を書く工程があるので、プロジェクトの大きさでぶれる）。2体の Gradle は別のプロジェクトなので
  重なってよいが、8・9-1・5 のサンプルを動かす作業とは重ねない
- オーケストレータ（または Sonnet 1体）が3つの report.md を `.local/release-v<版>/install-e2e.md` にまとめる（同じ原因は1件に、
  priority の高い順、担当ごとの「最後まで行けたか」の表を冒頭に）
- priority 7 以上は重要な警告として 7. 報告に出す。prerelease-check-list.md には、担当ごとの「最後まで行けたか」と件数、
  priority 7 以上の警告だけを書く
