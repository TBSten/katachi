# 9. インストールキットのチェック

[SKILL.md](../SKILL.md) の手順 9 の詳細。

AI エージェント向けのインストールキット（`docs/public/install/` の `katachi-install.sh` と手順書）が、利用者のいろいろな形の
Gradle プロジェクトで動くかを確かめる。キットは `docs/` の下にあり CI の検査対象外なので、壊れても誰も気づかない。
docs/AGENTS.md の「`public/install/`」の節が「スクリプトを直したら実際の Gradle プロジェクトで動かす」と求めている3種に、
これまでに不具合が出た形を足して、毎回まとめて動かす。

```shell
pgrep -fl GradleWrapperMain                                                                          # ほかの Gradle が走っていないこと
sh .claude/skills/prerelease/scripts/check-install-kit.sh --release-dir .local/release-v<版>        # 走らせる（15〜20 分）
sh .claude/skills/prerelease/scripts/check-install-kit.sh --release-dir .local/release-v<版> --only agp,rootjvm   # 一部だけ
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
