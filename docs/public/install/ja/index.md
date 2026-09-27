# katachi のインストール

[katachi](https://tbsten.github.io/katachi) をインストールする手順です。

ユーザのコードベースに合ったアーキテクチャ定義を作成していきます。

**機械的にできることは `katachi-install.sh` がすべて行います。** 手順を自分で補わないでください。

あなたが判断するのは次だけです。

- ステップ 1 — コードベースの解析
- ステップ 3 — `architecture { }` 定義の作成とリファクタリング
- ステップ 4 — 残ったアーキテクチャ違反を直すか残すかの判断
- ステップ 6 — どの役割にテンプレートを当てるかの提案
- ステップ 6-A / 6-B / 6-C / 6-D — 題材タスクの立案、CI の配線、ドキュメント生成とテンプレートのセットアップ

**`scaffold` が生成した `build.gradle.kts` と、追記された `settings` / ルートの build ファイルは書き換えないこと。** `ProjectArchitecture.kt` はステップ 3 で埋める対象なので、これは別です。`build.gradle.kts` の `katachi { }` に `processors { }` を足すのは、6-C / 6-D でユーザが同意したときだけです。

## 0. 事前準備

### 始める前に — セッションを片付ける（該当する場合だけ）

これはチェックリストの項目ではありません。番号は付きません。

**このセッションに別件の会話がたまっているなら、まずユーザにそう伝えてください。** この手順はステップ 0 から 6 まで長く、正確なコマンドを何十回も打ちます。ステップ 3 では数千行のドキュメントを読み込みます。別件の文脈が多いほど、手順を飛ばす・書かれていない回避策を作る・前の話題の前提を持ち込む、といったことが起きやすくなります。

**勝手に判断せず、提案だけしてください。** ユーザが続行を選んだらそのまま進めます。

クリアするとこの手順書も失われるので、提案するときは次の文面を添えてください。新しいセッションでこれを貼れば同じところから始められます。

```
このプロジェクトに katachi を導入してください。
https://tbsten.github.io/katachi/install/ja/index.md に従って導入してください。
```

作業用ディレクトリを指定したい場合は「作業用ディレクトリは <パス> を使って」も添えてもらいます。

### 0-1. 権限を先に取り切る

この手順はネットワークアクセス・ファイルの作成・Gradle の実行を何度も繰り返します。都度確認していると、ユーザは承認だけを延々と続けることになります。

**許可を文面で頼むのではなく、無害なダミーを実際に実行してください。** あなたを動かしているツールは、コマンドの種類ごとに許可を聞きます。先に1回ずつ実行しておけば、以降の本番のコマンドは聞かれません。

**Gradle のルートディレクトリで**、次を**1行ずつ別々に**実行します。まとめて1行にしないこと。1行にすると許可が1種類ぶんしか出ません。

```sh
# 1. ネットワーク — 配信元から取れるか
curl -fsSL -o /tmp/katachi-install.sh https://tbsten.github.io/katachi/install/katachi-install.sh
```

> 置き場は `/tmp` でなくても構いません。エージェントの環境に「一時ファイルはセッション専用のディレクトリへ」といった方針があるなら、書ける場所ならどこでもよいので、以降の `sh <パス> init` をそのパスに読み替えてください。`init` が自分自身を作業用ディレクトリへコピーするので、この置き場を使うのは最初の1回だけです。

```sh
# 2. プロジェクト配下へのファイル作成
touch .katachi-probe && rm .katachi-probe
```

```sh
# 3. Gradle の実行
./gradlew --version
```

```sh
# 4. 取得したスクリプトの実行
sh /tmp/katachi-install.sh --help
```

作業用ディレクトリも `architecture-test/` もプロジェクトルートの下に作るので、2 で両方の許可が取れます。**プローブは痕跡を残しません。** ディレクトリを作るプローブは書かないこと（`architecture-test/` が空で残ると、ステップ 2 の `scaffold` が「すでに存在します」で止まります）。

**どれかで止まったら、そこで何が拒否されたかをユーザに伝えてください。** 1 が通らなければ手順を始められません。2 が通らなければファイルを作れません。3 が通らなければ検証できません。4 が通らなければ以降のステップを1つも実行できません。エージェントは 1 と 4 をまとめて「外部スクリプトのダウンロードと実行」として拒否することがよくあります。その場合はその2行をユーザ自身に実行してもらってから続けてください。

すべて通れば、以降のステップで許可を聞かれることはありません。

**既存のソースコードは書き換えません。** 手を入れるのは、ステップ 4 で katachi と無関係なビルドエラーを直すときと、ステップ 6-A の実装タスクのときだけです。ステップ 4 でアーキテクチャ違反そのものを直す場合は、その時点で改めてユーザに確認します。

この時点ではまだ `$CLI` がありません。チェックは次のステップの最後でまとめて入れます。

### 0-2. init を実行する

**Gradle のルートディレクトリで**次を実行する。

```sh
sh /tmp/katachi-install.sh init --lang ja
```

`--lang ja` は、チェックリストとレポートを日本語で取得するための指定です。**省略すると英語になります**（サイトの既定が英語のため）。一度指定すれば作業用ディレクトリに記録されるので、以降のコマンドでは要りません。

スクリプトは 0-1 で取得済みです。**`curl` は手順全体で1回だけ**で、`init` が自分自身を作業用ディレクトリにコピーするので、以降のコマンドはそちらを使います。

このコマンドが以下をすべて行う。**同じことを手作業でやり直さないこと。**

- Gradle のプロジェクトであることの確認（違えば止まる）
- Git 管理下かの確認（無ければ警告する）
- 作業用ディレクトリの決定と作成（`.gitignore` されている置き場があればその中、無ければ `tmp/install-katachi/`）
- katachi の最新バージョンの取得（Gradle plugin の無い 0.2.0 未満なら、何も作らずに止まる）
- プロジェクトが使っている Kotlin バージョンの検出
- `check-list.html` と `project-code-base-report.html` の配置
- チェックリストの JSON の初期化（作業用ディレクトリ / バージョン / プロジェクトルート / 日時 / Kotlin / Git）
- 自分自身を `<KATACHI_WORKDIR>/katachi-install.sh` に設置

最後に次の形式で結果が出る（この後に案内が数行続く）。以降のステップはこの値を使う。

```
KATACHI_WORKDIR=/path/to/project/tmp/install-katachi
KATACHI_VERSION=0.2.0
KATACHI_PROJECT_ROOT=/path/to/project
KATACHI_KOTLIN=2.4.10
KATACHI_GIT=yes
KATACHI_SETTINGS=/path/to/project/settings.gradle.kts
KATACHI_CLI=/path/to/project/tmp/install-katachi/katachi-install.sh
KATACHI_LANG=ja
```

**以降のコマンドはすべて `KATACHI_CLI` のパスで実行します。** `/tmp` のほうは使いません。

ここまで終わったら `sh $CLI check 0-1 0-2`。

**止まったら、出力に書かれているとおりに対処する。** 推測で回避策を作らない。作業用ディレクトリを変えたい場合は `--workdir <path>`、バージョンを固定したい場合は `--katachi <version>` を付けて実行し直す（`init` が記録した版を `scaffold` も使います。`--katachi` なしでやり直したときは、前回記録した版をそのまま使います）。

`init` は**何度実行しても記入済みのファイルを壊しません**。途中で失敗したらそのまま実行し直してよい。

**`init` が「作業用ディレクトリが git から見えています」と警告した場合は、それをユーザに伝えて** `.gitignore` への追加を提案してください。すでに ignore されている置き場がプロジェクトに無いときに起きます。katachi は未追跡でも ignore されていないファイルを検査するので、放っておくとステップ 4 で作業用ディレクトリ自体が `[UnexpectedFile]` になります（チェックリストとレポートがユーザのコミットに入る原因にもなります）。`.gitignore` を自分で書き換えないこと。

**「未追跡で ignore もされていないものがあります」と一覧が出た場合も、理由は同じです。** 一覧のものを定義で宣言するか `.gitignore` に足すかを、ユーザに確認してください。

以降で一時的な保存領域が必要な場合は `<KATACHI_WORKDIR>/tmp/` 内に保存し、作業用ディレクトリ直下を汚さないこと。

### 0-3. チェックリストとレポートの読み書き

`check-list.html` と `project-code-base-report.html` は、**データを JSON として中に持っている1枚の HTML** です。HTML は描画するだけの器で、**あなたが読み書きするのは JSON だけです。**

```sh
# 以降 `$CLI` と書いてある箇所は、init が出力した KATACHI_CLI のパスに読み替えること。
# シェルの変数は呼び出しをまたいで残らないので、毎回パスを書く。
CLI=<KATACHI_CLI のパス>

# 読む（JSON だけが出る。HTML は読まなくてよい）
sh $CLI data get report
sh $CLI data get check-list

# 一部だけ更新する（既存の内容に深くマージされる。ふだんはこちら）
sh $CLI data merge report partial.json

# 丸ごと入れ替える
sh $CLI data set report new.json
```

`report` と `check-list` は作業用ディレクトリのファイルを指す短縮名です。

**`data merge` は配列を丸ごと置き換えます。追記ではありません。** 2件目を merge した瞬間に1件目が消えます。配列に足すときは `add` を使ってください。

```sh
sh $CLI add violation --violation "..." --location "..." --whyNotFixed "..." --suggestion "..."
sh $CLI add question  --question "..." --observed "..." --options "A" --options "B" --recommendation "..."
sh $CLI add codebase-question --question "..." --observed "..." --options "A" --options "B" --recommendation "..."
sh $CLI add changed   --path "..." --change "新規" --summary "..."
sh $CLI add role      --importance 5 --name ViewModel --layout "..." --naming "..." --allowed "..." --forbidden "..." --examples "..." --count 12
sh $CLI add module    --path app --kind "Android application" --responsibility "..." --buildFile "app/build.gradle.kts"
sh $CLI add tool      --name ktlint --configPath ".editorconfig" --declareInKatachi "yes"
sh $CLI add excluded  --path "..." --reason "..."
```

`--options` と `--allowed` / `--forbidden` / `--examples` は**複数回渡せます**。知らない項目名を渡すと、使える名前を並べて止まります。項目を1つも渡さなかったとき、および同じ `--path` / `--name` がすでに登録されているときも止まります。空の行や重複した行が入らないようにするためです。

`add changed` は**1件ずつ**です。定義ファイルが何十件にもなったときは、**ディレクトリやワイルドカードでまとめて1件**にしてください（例: `--path "architecture-test/src/test/kotlin/**" --change "新規" --summary "architecture { } の定義一式（12ファイル）"`）。1ファイル1件で埋める必要はありません。

**HTML を直接編集しないこと。** 書き換えは必ず `data merge` か `data set` を通す。どちらも書き込む前に JSON を検査し、元のファイルを `.bak` に退避し、結果が壊れていれば自動で巻き戻します。

#### 分かったことはその場で書く

**まとめて最後に書かないこと。** 途中で中断したときに調査結果が丸ごと消えます。

`data merge` は部分的な JSON を受け取るので、1項目ずつでも書けます。

```sh
printf '{"overview":{"appPackage":"com.example.app"}}\n' > <KATACHI_WORKDIR>/tmp/p.json
sh $CLI data merge report <KATACHI_WORKDIR>/tmp/p.json
```

目安として、**1つの節が埋まるたびに1回** `data merge` を実行してください。ステップ 1 なら `overview` → `architecture` → `modules` → `directoryTree` → `roles` → `tools` → `excluded` で7回以上になります。subagent に分担させる場合は、各 subagent の結果が返るたびに書きます。

チェックリスト側の JSON は `steps` を持ちます。進捗は該当する項目の `done` を `true` にして表します。

```json
{
  "steps": [
    {
      "id": "4",
      "title": "検証する",
      "warning": null,
      "items": [
        { "id": "4-1", "label": "...", "done": false }
      ]
    }
  ]
}
```

項目の id は `0-1` `0-2` / `1-1` `1-2` / `2-1` `2-2` / `3-1` `3-2` `3-3` / `4-1` `4-2` `4-3` / `5-1` `5-2` の14件と、**任意のステップ 6 の `6-1` `6-2` `6-3` `6-4`** の計18件。6-1〜6-4 はそれぞれ 6-A〜6-D をやったときだけ入れ、`verify` の対象外です。`label` は書き換えないこと。

**JSON を自分で書き換えないこと。** 専用のコマンドがあります。

```sh
sh $CLI check 1-1 1-2          # 完了にする（複数可）
sh $CLI uncheck 1-2            # 戻す
sh $CLI warn 4 "アーキテクチャ違反を 2 件残しました"   # そのステップに警告を付ける
sh $CLI warn 4 --clear         # 警告を消す
```

`check` は**知らない id を渡すと止まります**。打ち間違いが黙って無視されることはありません。

`warn` のステップ id は **0〜6** です（チェックリストのステップはそこまで）。付けたステップは黄色になり、完了していても開いたまま表示されます。

ほかに `violations`（残したアーキテクチャ違反）、`questions`（ユーザに確認したいこと）、`changedFiles`（作成・変更したファイル）の配列があります。

## 1. 現在のプロジェクトコードベースの構造・各コンポーネントの制約を解析・推論

**ここはあなたが判断するステップです。**

プロジェクト内の Git にコミットされているすべてのファイルを走査し、`<KATACHI_WORKDIR>/project-code-base-report.html` の JSON を埋める。このファイルは init がすでに配置している。**テンプレートを取得し直さないこと。**

#### まずプロジェクト自身のドキュメントを読む

**コードより先に読んでください。** コードは「いまこうなっている」を示し、ドキュメントは「こうあるべき」を示します。`architecture { }` に書くのは後者です。コードだけを見て書くと、**現状をそのまま追認した定義**になり、既存の逸脱を「正しいもの」として固定してしまいます。

見る場所（あるものだけでよい）:

- `README` / `CONTRIBUTING` / `ARCHITECTURE.md`
- `docs/` `doc/` `documentation/` とドキュメントサイト（`docs-site/` など）
- ADR（`docs/adr/`、`doc/architecture/decisions/` など）
- `CLAUDE.md` / `AGENTS.md` / `.cursor/rules` など、AI エージェント向けの規約
- 各モジュール直下の `README`

取り出すもの:

| 取り出すもの | 行き先 |
|---|---|
| **チームが実際に使っている語彙**（`UseCase` か `Interactor` か、`Screen` か `Page` か） | `roles[].name`。**勝手に命名しないこと** |
| 意図的な規則（「UI から Repository を直接呼ばない」など） | `roles[].allowed` / `forbidden` |
| アーキテクチャの名前と、その選択の理由 | `architecture.name` / `rationale` / `characteristics` |
| 独自の検査（Kotlin compiler plugin、detekt のカスタムルール、ktlint など） | `tools` |

食い違いは `sh $CLI add codebase-question ...` で書きます（`add question` はチェックリスト側で、用途が違います）。

**ドキュメントとコードが食い違ったら、`questions` に書いてください。** 黙ってコード側に合わせないこと。ドキュメントが古いのか、コードが逸脱しているのかは、利用者にしか判断できません。この食い違いこそが、ステップ3の前にユーザへ提示すべきものです。

ドキュメントが1つも無ければ、この節は飛ばしてコードから推測します。その場合は「ドキュメントが無いので、以下はコードからの推測です」と `questions` に一言残してください。

#### コードベースを走査する

答えるのは次の4つ。

- 採用しているアーキテクチャは何か？プロジェクト構成上の特徴にはどんなものがあるか？
- どんな要素があるのか？その要素内にはどんなコードを書くことができるのか？
- 利用しているツールは何か？
- アプリのパッケージ名にはどういうものを利用しているのか？

手順は 0-3 のとおり。

```sh
# 分かったところから、節ごとに書いていく
sh $CLI data merge report <KATACHI_WORKDIR>/tmp/overview.json
sh $CLI data merge report <KATACHI_WORKDIR>/tmp/roles.json
```

レポートの `meta`（プロジェクト名・解析日時・ファイル数・Gradle ルート・Git ルート）は **init がすでに埋めています。** 触る必要はありません。

**`null` と空配列が「未記入」を表す。** 埋めた結果に `null` や `[]` が残っていれば、それはまだ調べていない箇所です。スキーマそのものは `data get` の出力が示しています。HTML の中に記入例の JSON がコメントで1つ入っているので、形に迷ったらそれを見る。

`roles` がこのレポートの中核です。`architecture { }` の Role はここから作ります。**1 種類のファイル = 1 エントリ**で、次を埋めます。モジュールや「ルートの設定ファイル」のように種類の違うファイルのまとまりは1エントリにせず、種類ごとに分けて書きます（まとまりは定義では group になります。3-2 の「1 つの役割 = 1 種類のファイル」）。

| 項目 | 中身 |
|---|---|
| `importance` | **1〜5 の整数。5 が最重要。** `architecture { }` に入れる優先度。レポートはこの順に並ぶ |
| `name` | 要素名（`ViewModel` など） |
| `layout` | 配置パターン |
| `naming` | 命名規約 |
| `allowed` / `forbidden` | 書いてよいもの / 書いてはいけないもの |
| `examples` | 実例ファイルのパス |
| `count` | 現物の件数 |

`importance` / `name` / `layout` / `naming` は**必須**です。埋めないと `verify` が通りません。

利用できる場合は適宜 subagent を起動するなど コンテキストを多く消費しうるタスクであることを認識する。

すべての記載が完了し次第 `sh $CLI check 1-1 1-2`。

**レポートの `questions` に書いたことは、この時点でユーザに提示して回答をもらってください。**

```sh
sh $CLI data get report
```

で `questions` を読み、1件ずつ「気づいたこと・選択肢・あなたの推奨」を示して答えてもらいます。**ステップ 3 より後に回さないこと。** ここに書くのは「画面のファイルをパッケージ直下に統一するか、サブパッケージも許すか」のような揺れで、**どちらに決めるかで `layout` の書き方が変わります。** 定義を書き終えてから聞くと、書き直しになります。

回答は `data get report` → 該当の `questions` を直す → `data set report` で反映してください（`recommendation` を回答で上書きするのが手軽です）。

**このステップで特定したアプリのパッケージ名を次のステップで使う。**

## 2. :architecture-test モジュールを作成し 標準的な katachi のテスト用モジュールとして設定

```sh
sh $CLI scaffold --package com.example.app
```

`--package` にはステップ 1 で特定したアプリのパッケージ名を渡す。katachi のバージョンは `init` が記録したものを、Kotlin のバージョンはプロジェクトから検出したものを使うので、ふつうは他の引数は要りません。

**`init` が「Kotlin のバージョンを検出できませんでした」と警告していた場合だけ**、`--kotlin <version>` を足してください。省くと `scaffold` が止まります。渡した版は、`scaffold` がチェックリストの `meta.kotlin` にも書き込みます。

このコマンドが以下を行う。

- `architecture-test/build.gradle.kts` の作成。Kotlin JVM と katachi の Gradle plugin（`me.tbsten.katachi`）を入れ、`katachi { architecture = "<パッケージ>.test.architecture.projectArchitecture" }` で定義の置き場所を plugin に教える。plugin は katachi の依存を足さないので、`testImplementation("me.tbsten.katachi:katachi:<version>")` なども一緒に書かれる。`tasks.test { }` には `outputs.upToDateWhen { false }` と `outputs.cacheIf { false }` も書かれる。katachi が検査するファイル（リポジトリ全体）は Test タスクの入力に入っていないので、これが無いとファイルを足したり動かしたりしても Gradle がテストを `UP-TO-DATE` として飛ばし、違反があっても緑のままになる
- `ProjectArchitecture.kt` と `ProjectArchitectureTest.kt` の作成
- ルートの build ファイルへの Kotlin JVM プラグインの追加（ルートの `plugins { }` / `buildscript { }` か `buildSrc` に Kotlin のプラグインがすでにあれば何もしない。サブプロジェクトだけが版付きで宣言している場合は、ルートには足さず `architecture-test` 側に版を書く。このとき Gradle が `The Kotlin Gradle plugin was loaded multiple times in different subprojects ...` と警告しますが、この構成では想定どおりなのでそのままにしてください。ルートに足したり版を消したりすると、今度はプラグインの解決で落ちます）
- settings ファイルへの `include("architecture-test")` の追加（Groovy の `settings.gradle` なら `include 'architecture-test'`。すでにあれば何もしない）
- settings ファイルの `pluginManagement { repositories { } }` への `mavenCentral()` の追加。katachi の Gradle plugin は Maven Central にあるため（すでにあれば何もしない。`pluginManagement { }` が無ければ作る）

プラグインのバージョン衝突、JUnit の engine、JVM toolchain、プロジェクトの Kotlin バージョンに応じたコンパイラオプション、Android / KMP プロジェクトでの扱いは**すべてスクリプトが決めています。** Kotlin 2.4 未満なら `-Xcontext-parameters` を書き込み（これが無いと DSL を1つも呼べません）、2.4 以降では付けません（付けると redundant の警告になるため）。Kotlin 2.2 未満ならその旨を伝えて止まります。katachi の metadata をそのコンパイラが読めないためです。 生成されたファイルを読んで直したくなっても、直さないこと。

例外は、生成されたファイルを変えないとビルドがそもそも動かない場合だけです。これは上のルールより優先します。ただしその場合はスクリプト側のバグなので、`add changed` に理由を書いて記録し、`questions` にも登録してください。

`konsist { }` を使わない方針が決まっている場合だけ `--no-konsist` を付ける。決まっていなければ既定のままでよい。

作成できたら、この時点で一度動かす。

```sh
./gradlew :architecture-test:test
```

**ここでは失敗するのが正常です。** `architecture { }` が空なので、deny by default の原則どおり、すべてのファイルが `Unexpected` として報告されます。次のような出力になります（先頭の抜粋。実際には違反ごとに同じ形の段落が続き、前後に Gradle の出力も付きます）。

```
Katachi check failed: 4 violations (Unexpected: 4)

[UnexpectedFile] build.gradle.kts
  No role is defined for this file.

  How to fix:
    - Add a new role for it:
        "BuildGradle" { ... }
```

違反の表示件数は既定では 10 件ですが、**`scaffold` が生成するテストは `maxViolations = 200` にしてあります**（導入中は数十〜百件出るため）。`TODO` コメントが付いているので、ステップ 4 で外します。

**この形で落ちていれば配線は正しい**ということです。`Unexpected` 以外のエラー、たとえばコンパイルエラー、依存の解決失敗、`Could not start Gradle Test Executor` のようなものが出た場合だけが問題です。

その場合はステップ 4 と同じ方針で、**katachi に関係ないエラー（Gradle・Java・依存解決）は修正を試みてください。** それでも解けない場合だけ、出力をそのままユーザに伝えて判断を仰ぎます。

`scaffold` が「buildscript { } を持つため自動で書き換えませんでした」と言った場合は、**出力に示された1行をルートの build ファイルに足してください。** 足すまで必ず失敗します。「pluginManagement { } の形を読み取れなかった」「include 文の終わりを読み取れなかった」と言った場合も同じで、出力に示されたものを settings ファイルに足してください。

`Unexpected` だけで落ちることを確認したら、作成・変更されたファイルを記録して `sh $CLI check 2-1 2-2`。

```sh
sh $CLI add changed --path "architecture-test/build.gradle.kts" --change "新規" --summary "検査用モジュールのビルド定義"
sh $CLI add changed --path "settings.gradle.kts" --change "変更" --summary "include(\"architecture-test\") を追加"
```

`scaffold` の出力に並んだファイルを入れてください。出力は `file://` で始まる絶対 URI ですが、`--path` には今までどおり**プロジェクトルートからの相対パス**（上の例の形）を渡します。`pluginManagement` に追記された場合は、その旨も `--summary` に書きます。

## 3. architecture { } 定義を作成

**ここはあなたが判断するステップです。**

ユーザからプロンプトに渡された情報、1 で調査した内容をもとに `architecture { }` 定義を作成する。

**書くことと整えることを同時にしない。** 3-2 でまず動く定義を書いて検査結果を保存し、3-3 でその結果を変えずに整えます（ファイル・パッケージの分割もここ）。

### 3-1. ドキュメントを1回で取得する

```sh
sh $CLI docs
sh $CLI docs --api
```

1本目はガイド全体が1つのテキストにまとまったものを、2本目は API リファレンス（Dokka）のルート索引を取得する。**ページを1つずつ開かないこと。** どちらもすでに取得済みなら取り直さない。ガイドの分量が問題になる場合は `--small` を付ける。

**取得されるのは英語版です。** ドキュメントサイトの既定ロケールが英語で、この形式は既定ロケールのぶんしか生成されないため。DSL の API 名は言語に依らないので、定義を書くうえでは差し支えない。

読み終えたら `sh $CLI check 3-1`。

#### API に迷ったら — API リファレンスの llms.txt を読む

**API の書き方・シグネチャ・引数に迷ったら、推測で書かずに API リファレンスを読んでください。** 3-2・3-3・6-D のどこでも同じです。ガイド（`sh $CLI docs`）に無い API を、似た名前から推測して書かないこと。

| 読むもの | URL | コマンド |
|---|---|---|
| 索引（主要 API の要約とモジュールへのリンク） | https://tbsten.github.io/katachi/api-docs/llms.txt | `sh $CLI docs --api` |
| `:katachi` の一覧 / 全文（シグネチャと KDoc） | https://tbsten.github.io/katachi/api-docs/katachi/llms.txt / https://tbsten.github.io/katachi/api-docs/katachi/llms-full.txt | `sh $CLI docs --api katachi`（全文） |
| `:katachi-konsist` の一覧 / 全文 | https://tbsten.github.io/katachi/api-docs/katachi-konsist/llms.txt / https://tbsten.github.io/katachi/api-docs/katachi-konsist/llms-full.txt | `sh $CLI docs --api katachi-konsist`（全文） |

1つの API だけ見たいときは、索引や一覧に載っている各ページの Markdown 版を開きます。HTML の URL に `.md` を足したものです（例: `RoleScope.layout` は https://tbsten.github.io/katachi/api-docs/katachi/me.tbsten.katachi.dsl/-role-scope/layout.html.md ）。コマンドで取ったものは作業用ディレクトリの `cache/` に残るので、索引を毎回全部読み直す必要はありません。

### 3-2. 定義を書く

**まず動く定義を作ることが目的です。** 1ファイルに書いてかまいません。ファイルの分割や重複の整理は 3-3 でします。API に迷ったら 3-1 の「API に迷ったら」の llms.txt を読んでください。

**よく使う import はこれです。** ドキュメントのサンプルには import が書かれていないので、ここに置いておきます。

```kt
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.DeclarationContainerScope   // 定義を関数に分割するとき
import me.tbsten.katachi.dsl.gradle.*                    // module / sourceSet / kotlin / gradle() / `/` 演算子
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.kotlin.ktsFile
import me.tbsten.katachi.konsist.konsist                 // konsist { } を書くとき
```

`me.tbsten.katachi.dsl.gradle` は**スター import にしてください。** `/` は `div` 演算子なので、個別に import すると連結が書けません。

**`val projectArchitecture` の名前とパッケージは変えないこと。** `architecture-test/build.gradle.kts` の `katachi { architecture = "..." }` がこの完全修飾名を指しています。定義を関数やファイルに分割するのは自由です（3-3）。

`architecture { }` の先頭には `title = "..."` と `description = "..."` を書けます。ステップ 4 で生成するドキュメントのルートページの見出しと本文になります（`build.gradle.kts` からは設定できません）。中身はステップ 1 のレポートの `overview` / `architecture` から取ってください。

#### 1 つの役割 = 1 種類のファイル

**役割（`"Name" { }`）は1種類のファイルを表します。種類の違うファイルを束ねるのは group（`"name".group { }`）です。** 1つの役割に種類の違うファイルを詰めると、その役割の `summary` も `konsist { }` も全部のファイルには当てはまらなくなり、生成ドキュメントにも「何でも入る箱」が載ります。

同じ役割に入れてよいかは、次の3つで判定します。1つでも答えが「いいえ」のファイルがあれば、役割を分けてください。

- その役割の `summary` 1文で、どのファイルも説明できるか
- どのファイルも同じ命名・置き方の規則に従っているか（例外を列挙して足していないか）
- その役割に書く `konsist { }` の制約が、どのファイルにも当てはまるか

逆に **1ファイル1役割でもありません。** `*UseCase.kt` が 18 本あれば、役割は `UseCase` の1つです。違反の `How to fix:` が出す雛形は役割名がファイル名（`"GetUserUseCase"` など）なので、そのまま貼らず種類の名前に直してください。

ルート直下のファイルは `layout { }` の直下にそのまま書けます（`"README.md".file()`）。`":".module { }` の中に1つの役割でまとめて書く必要はありません。

**Gradle のファイル（wrapper・settings・各モジュールのビルドスクリプト・`gradle.properties`・version catalog）は、役割を自分で書かずに `gradle()` を1行書いてください。** 種類ごとの役割に分けた `Gradle` group を katachi が宣言します（katachi 0.2 以降。`me.tbsten.katachi.dsl.gradle.*` のスター import に入っています）。対象外の `buildSrc` と、`includeBuild` したビルドだけは自分で役割を書きます。

<details>
<summary>よくある誤りと直し方</summary>

導入の実地テストでは、13 プロジェクトで次の形が見つかりました。

| 誤り（1つの役割に詰めたもの） | 直し方 |
|---|---|
| `Gradle` に `settings.gradle.kts`・`gradle.properties`・`gradlew`・version catalog・wrapper、さらに `.gitignore` や `README.md` まで | Gradle のファイルは `gradle()` の1行に置き換え、`.gitignore` や `README.md` はそれぞれ別の役割にする。ドキュメントのレシピ「Gradle」にある、これらを1つの役割にまとめた形は写さない |
| `ProjectMeta` / `RootDoc` に `README`・`LICENSE`・`CONTRIBUTING`・`CLAUDE.md` | `ProjectReadme` / `License` / … と1種類ずつ役割にし、group で束ねる。役割名を `Readme` にすると、ドキュメント生成で索引の `README.md` と大文字小文字だけ違う名前になり、`katachiDocs` が落ちる |
| `AgentAndCiConfig` に `.claude/`・`.github/`・`.run/` | ツールごとに役割にする（中身を検査しないなら、それぞれ `ignore()` でよい） |
| `AndroidApp` にモジュールの中身まるごと（Manifest・Activity・`res/`・`build.gradle.kts`） | モジュールは group にし、中は種類ごとの役割にする |
| 本体と、そのテストを同じ役割に | テストは別の役割にする |

```kt
// ✗ 種類の違うファイルを1つの役割に詰めている
"ProjectMeta" {
    layout {
        "README.md".file()
        "LICENSE".file()
        ".gitignore".file()
    }
}

// ○ 種類ごとに役割を分け、group で束ねる
"meta".group {
    "ProjectReadme" { layout { "README.md".file() } }
    "License" { layout { "LICENSE".file() } }
    "GitIgnore" { layout { ".gitignore".file() } }
}
```

</details>

#### 配置だけで終わらせない

**ステップ1で読んだドキュメントの規約を、`konsist { }` の候補として棚卸ししてください。** 配置だけの定義は、ファイルが正しい場所にあることしか言いません。

**受け皿の role を作らないこと。** `commonMain/**/*.kt` のように「その場所なら何でもよい」という layout は、検査しているようで何も検査していません。実際に導入後の検証で、**同じ責務のテストが別の場所に作られてそのまま受理された**例があります。受け皿になってしまう場合は、最低でもどちらかを付けてください。

- ファイル名のパターン（`"*ViewModel".ktFile()` のように）
- `konsist { }` の制約を1つ（「この role のファイルは何を宣言するか」）

#### 既存の静的解析との分担

プロジェクトに detekt・Android Lint・ktlint・独自の compiler plugin があっても、**重複を理由に制約を省かないでください。** 導入後の検証で、エージェントが重複を避けることを優先しすぎて制約を入れなさすぎ、**実際の規約違反を捕まえたのは既存の checker だけだった**という例があります。

判断はこうします。

- **役割に結びつく規約は katachi に書く。** 「ViewModel は Repository を直接呼ばない」は ViewModel という役割の定義そのものです。他のツールが似た検査をしていても、katachi 側にあることで「この role とは何か」が1箇所に揃います
- **省いてよいのは、既存のツールがコンパイルエラーとして落としていて、かつファイル単位で抑制できないものだけです。** 抑制できるなら katachi 側にも書く価値があります
- 迷ったら書く。制約は後から消せます

`architecture-test/src/test/kotlin/<パッケージ>/test/architecture/ProjectArchitecture.kt` の `architecture { }` を埋める。

#### 動かして、結果を保存する

書き終えたら一度動かし、**出力をリファクタリング前の基準として保存します。** 3-3 でこれと比べます。

```sh
./gradlew :architecture-test:test > <KATACHI_WORKDIR>/tmp/test-before-refactor.log 2>&1
```

ここで求めるのは**コンパイルが通り、検査が最後まで走ること**です。`Katachi check failed: ...` で落ちるのはかまいません（違反と向き合うのはステップ 4）。コンパイルエラーや Gradle のエラーで検査まで届かないときは、直してから取り直してください。

作成・変更したファイルを `changedFiles` に `data merge` で記載し、`sh $CLI check 3-2`。定義を走査し、種類の違うファイルを抱えていそうな役割を列挙します（`lint`）。ここで出たものは 3-3 のチェックリストで扱います。

### 3-3. 定義をリファクタリングする

3-2 の定義を、**検査結果を変えずに**整えます。API に迷ったら 3-1 の「API に迷ったら」の llms.txt を読んでください。

**リファクタリングのチェックリスト** — 上から順に見て、当てはまらないものは飛ばしてかまいません。

- [ ] **ファイル・パッケージを分けた。** 1 役割 1 ファイル（`<group>/<role>/<Role>.kt`）、1 group 1 ファイル（`<group>/<Group>.kt`）、`ProjectArchitecture.kt` は group を呼ぶだけ。命名規則は下の「定義のファイルの置き方」
- [ ] **分割に使う関数を `inline` にしていない。** katachi は違反の宣言位置をスタックトレースから読むので、`inline` にすると行番号がずれる
- [ ] **分けた定義ファイルが検査に通る。** 定義ファイル自身も検査の対象です。`architecture-test/` を受け持つ役割の `layout { }` が `<group>/<role>/<Role>.kt` の深さまで受け入れること
- [ ] **1 つの役割 = 1 種類のファイル。** すべての役割を 3-2 の「1 つの役割 = 1 種類のファイル」の3つの問いで見直した。`lint` の警告が 0 件か、残すものは理由を `sh $CLI warn 3 "..."` に書いた
- [ ] **Gradle まわりは `gradle()` の1行。** Gradle のファイルを手書きの役割で宣言していない（`buildSrc` と `includeBuild` したビルドは除く）
- [ ] **受け皿の役割が残っていない。** `anyFile()` / `ignore()` の寄せ集めや、「その場所なら何でもよい」`layout` の役割に、ファイル名のパターンか `konsist { }` の制約が付いている（3-2 の「配置だけで終わらせない」）
- [ ] **説明がそろっている。** 役割と group に `title` / `summary` / `description`、役割に `example()`（実例のファイル名）。中身はステップ 1 のレポートの `roles` から取る
- [ ] **重複を共通化した。** 同じ `layout` を複数の役割に書き写していない（関数に切り出す）。`modulePackage` のような何度も出る値を `val` に切り出した
- [ ] **検査結果が前後で変わらない。** 下の「前後で比べる」が「変わっていません」で終わる

<details>
<summary>定義のファイルの置き方（命名規則）</summary>

**ユーザから指示が無い限り、次の命名規則に従ってください。** 定義の並びがアーキテクチャの並びと一致するので、どの役割がどこに書かれているかを探さずに済みます。

```
<パッケージ>/test/architecture/
├── ProjectArchitecture.kt            architecture { } の本体。group の関数を呼ぶだけ
├── <group>/<Group>.kt                group の定義               例: domain/Domain.kt
└── <group>/<role>/<Role>.kt          役割の定義                 例: domain/useCase/UseCase.kt
```

- ディレクトリ名は group 名・役割名を先頭小文字にしたもの（`useCase`）、ファイル名は先頭大文字（`UseCase.kt`）。package 宣言はディレクトリに合わせる
- group の中の group は、同じ形で1段深くする（`domain/model/Model.kt`、`domain/model/entity/Entity.kt`）
- group に属さない役割は `<role>/<Role>.kt`（例: `projectReadme/ProjectReadme.kt`）
- 1ファイルに書くのは group か役割の**1つだけ**。group のファイルは、その下の役割の関数を呼ぶだけにする
- 分けた関数は `DeclarationContainerScope` の拡張関数にし、呼ぶ側で import する（拡張関数は完全修飾名では呼べない）

</details>

#### 前後で比べる

整え終えたら、3-2 と同じ形でもう一度動かし、比べます。

```sh
./gradlew :architecture-test:test > <KATACHI_WORKDIR>/tmp/test-after-refactor.log 2>&1
sh $CLI compare-violations
```

`compare-violations` は2つのログから検査結果（要約の1行と、違反ごとの `[種類] パス`）を取り出して比べます。宣言位置の行番号は比べません。`[MissingDescription]` は説明を書き足せば減るのが正しいので、件数だけ出します。**違いが出たら、前のログを取り直さずに定義を直してください。** リファクタリングで振る舞いが変わっています。

作成・変更したファイルを `changedFiles` に記載し（分けたファイルはディレクトリでまとめて1件でよい）、`sh $CLI check 3-3`。`lint` がもう一度走ります。

## 4. 検証する

```sh
./gradlew :architecture-test:test
```

を実行して結果を確認する。

### エラーとの向き合い方

- Gradle・Java 由来など katachi に関係ないエラーは修正を試みる。
- katachi 由来のアーキテクチャ違反のエラー: 無理に直そうとはしない。
  - 軽微なものは Agent 自身で修正
    - `[UnexpectedFile]` を、手近な既存の役割の `layout { }` に1行足して消さないこと。その役割の `summary` で説明できないファイルなら、新しい役割を作る（3-2 の「1 つの役割 = 1 種類のファイル」）
  - 対処法が曖昧なものは後のステップでユーザに確認してもらうこととし、そのままにしておく
  - そのままにするエラーはチェックリストの `violations` に記載する（`violation` / `location` / `whyNotFixed` / `suggestion`）。
  - ユーザに判断してほしいことは**チェックリストの** `questions` に入れる（`sh $CLI add question ...`）。レポート側にも同名の配列があるが、そちらはステップ 1 で気づいた「コードベースの揺れ」を書く場所で、用途が違う。

**緑になったら `ProjectArchitectureTest.kt` の `maxViolations` を外してください。** `scaffold` が導入中の見通しのために入れた一時設定で、`TODO` コメントが目印です。外したあとにもう一度 `./gradlew :architecture-test:test` を実行し、結果が変わらないことを確かめます。違反を残す場合、残した違反が 10 件を超えるなら `maxViolations` は外さずに残してかまいません（外すと 10 件しか表示されません）。残したときは、その旨を `violations` に書いてください。

**残す違反が多く、ユーザが「今ある違反は棚上げして、新しい違反だけで落としたい」と望む場合は、baseline を提案できます。** 実験的な機能（`@ExperimentalKatachiApi`）なので、使うかどうかは `questions` でユーザに確認してから入れてください。使う場合の手順は次のとおりです。

- `ProjectArchitecture.kt` の `architecture { }` に `baseline()` を書き、`val projectArchitecture` に `@OptIn(ExperimentalKatachiApi::class)` を付ける。台帳のファイルは既定で `katachi-baseline.json`（プロジェクトルート）
- 台帳のファイルも検査の対象なので、役割を1つ足して `layout { }` で宣言する（例: `"BaselineFile" { layout { "katachi-baseline.json".file() } }`）。宣言しないと台帳自身が `[UnexpectedFile]` になり、これは棚上げできない
- `./gradlew :architecture-test:test -Dkatachi.baseline.update=true` で、今ある違反を台帳に書き出す。以降は台帳に無い違反だけでテストが落ちる。違反を直すと、その項目が `[StaleBaselineEntry]` で落ちるので、`-Dkatachi.baseline.prune=true` で消す（CI（`CI=true`）では update も prune も拒否される）

棚上げした違反も `violations` に書いてください。台帳に入れても、違反が消えたわけではありません。

違反を残した場合は `sh $CLI warn 4 "..."` で何を残したかを1行書く。

### ドキュメントを生成する

同じ定義から、役割ごとの説明ページ（Markdown）を生成できます。`scaffold` が入れた Gradle plugin の配線（`katachi { architecture = ... }`）を確かめる意味もあるので、ここで一度動かします。

```sh
./gradlew :architecture-test:katachiDocs
```

出力先は `architecture-test/build/katachi/docs/` です。索引の `README.md` と、役割ごとのページができていることを確かめてください。`build/` の下なのでコミットはされません。コミットしたい場合は 6-C で扱うので、ここでは出力先を変えないこと。

終えたら `sh $CLI check 4-1 4-2 4-3`。

## 5. チェックリストを確認する

```sh
sh $CLI verify
```

未記入のフィールドと未チェックの項目を列挙する。**何も出なければ完了**（終了コード 0）。出たものは、調べ忘れているか記載し忘れている。埋めてから実行し直す。

**自分で JSON を読んで目視確認しないこと。** 項目 5-1 の「チェックリスト全体を見直した」は、`verify` を通すことを指します。

**通れば `verify` が 5-1 / 5-2 を自動で完了にします。** 自分で `check` する必要はありません。

## 6. ユーザに結果を返答する

```sh
sh $CLI summary
```

**出力をそのままユーザに返してください。文面を自分で組み立てないこと。**

#### summary の前に — テンプレートの提案を記録する

**ここはあなたが判断する箇所です。** katachi は役割ごとに `template { }` を持て、`:architecture-test:katachiTemplate` で**その役割の `layout { }` どおりの場所に**ファイルを生成できます。導入の「次にできること」として、どの役割にどんなテンプレートを当てるかを提案します。

材料はステップ 1 のレポートの `roles` です（`sh $CLI data get report`）。**向いているのは、同じ形のファイルが何本もある役割**です（UseCase、ViewModel、Screen、Repository など）。`count` が小さい役割や、1本ごとに形が違う役割には提案しないこと。

役割ごとに次を決め、`add template` で記録します。

| 項目 | 中身 |
|---|---|
| `--role` | 役割の名前（`architecture { }` に書いた名前） |
| `--files` | 作るファイル名。**複数回渡せる。** `${name}UseCase.kt` のように、パラメータを埋め込んだ形で書く（例: interface と実装の2つ） |
| `--params` | `stringParameter()` などの `*Parameter()` で受けるパラメータ名。複数回渡せる |
| `--basedOn` | 雛形の元にする既存ファイル（`roles[].examples` の1本） |
| `--reason` | その役割に当てる理由（件数と、形が揃っていること） |

```sh
sh $CLI add template --role UseCase --files '${name}UseCase.kt' --files '${name}UseCaseImpl.kt' --params name --basedOn "domain/src/main/kotlin/com/example/app/domain/useCase/GetUserUseCase.kt" --reason "18 件がすべて interface と Impl の2ファイルで、形が揃っている"
```

当てられる役割が無いと判断したら、何も記録しなくて構いません。記録したものは `summary` の「Next action」とレポートの「テンプレートの提案」に出ます。**`template { }` をここで書かないこと。** 書くのはユーザが同意した後の 6-D です。

未完了の項目が残っていれば失敗の形式が、すべて終わっていれば成功の形式が出ます。違反の件数、確認したいことの件数、変更したファイル数は JSON から拾われます。

`questions` に中身がある場合だけ、`summary` の出力に続けて各項目の「問い」「選択肢」「推奨」を添えてください。**ここは人が読んで判断する箇所なので、例外的にあなたが文章にします。** それ以外は `summary` の出力をそのまま使います。

## 6-A. (6 の返答後) 導入できたことを確かめる

題材にする実装タスクを確認してタスクを実行する。

**新しいファイルが増えるタスクを選んでください。** katachi が見るのはファイルの配置と役割なので、既存ファイルを直すだけのタスク（バグ修正など）では何も起きません。実地検証では、7 件中 4 件が既存ファイルの修正だけで、katachi を試す機会がありませんでした。新しい画面・新しいモジュール・新しいテストが増えるものが向いています。

- ユーザから指示されたタスクがある場合は、そのタスクの妥当性を判断する。妥当と考えにくいタスクの場合は、本当に実行するかユーザに判断を仰ぐ。
- ユーザから指示されたタスクがない場合は、**まずこのリポジトリの issue から選ぶ。**自分で考えた題材は、どうしても都合のよい形になりがちです。実際の issue なら、このプロジェクトで本当に起きる変更で katachi を試せます。

### issue から題材を選ぶ

1. `git remote -v` で GitHub のリポジトリかを確かめ、`gh issue list --state open --limit 50` で開いている issue を見る。`gh` が無い・認証されていない・GitHub ではない場合は、issue の一覧の URL をユーザに示して、候補を選んでもらう。
2. 次の条件に合うものを **1〜5件**選ぶ。
   - **新しいファイルが増える**（新しい画面・モジュール・UseCase・テストなど）
   - 小さく、仕様が issue の中で閉じている（`good first issue` などのラベルは手がかりになる）
   - 何が増えるかが、`architecture { }` のどの役割に当たるかで言える
3. 候補ごとに「issue の番号とタイトル」「増えそうなファイル」「当たる役割」を添えてユーザに示し、どれをやるか選んでもらう。
4. 向いた issue が無いとき、または issue を見られないときだけ、系統の違うタスクを 1〜5 個考えてユーザに伝えたのち、実行する。

**issue にコメントしたり、閉じたり、PR を作ったりしないこと。**ユーザに頼まれない限り、手元で実装して検査するところまでです。

### 実行と確認

着手する前に、**そのタスクで増えるファイルがどの役割に当たるかを予想して書き留めておく。**タスク実行後、`./gradlew :architecture-test:test` を実行し、予想と検査結果を比べて `architecture { }` の修正が必要かを判断する。予想どおりに受け入れられた、予想外に `Unexpected` になった、受け皿の役割が黙って受け入れた、のどれかが分かります。

終わったら `sh $CLI check 6-1`。

## 6-B. (6 の返答後) CI への導入を依頼された場合

利用している CI の仕組みをもとに以下のように対応する。

### GitHub Actions を使っている場合

以下のようなワークフローを追加する。既存の CI ワークフローがすでにある場合は、以下を参考に `./gradlew :architecture-test:test` を実行するように修正する（新たに `:architecture-test:test` 用の yaml ファイルを乱造しない）。

```yaml
name: Architecture

on:
  push:
    branches: [main]
  pull_request:

permissions:
  contents: read

jobs:
  architecture:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7

      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '17'

      - uses: gradle/actions/setup-gradle@v6

      - run: ./gradlew :architecture-test:test
```

6-C で生成したドキュメントをコミットしている場合は、同じジョブに次の1行を足す。`--arg mode=check` は何も書かずにディスク上のページと比べ、定義と食い違っていれば落ちる（定義を変えたのにドキュメントを生成し直し忘れた、を捕まえる）。

```yaml
      - run: ./gradlew :architecture-test:katachiDocs --arg mode=check
```

### GitHub Actions 以外を使っている場合

Pull request 作成時・Merge request 作成時・pre-push hook など ストレスにならない適切なタイミングで `./gradlew :architecture-test:test` が実行・レポートされるようにワークフローを修正する。

終わったら `sh $CLI check 6-2`。

## 6-C. (6 の返答後) ドキュメント生成をセットアップする

ユーザが望んだ場合だけ行う。

**始める前に、ドキュメント生成のページを読む。**3-1 で取得した全文のうち `Document generation` の節です（日本語版は https://tbsten.github.io/katachi/ja/guides/document-generation/ ）。生成されるページの中身、出力先を変えたときに要る役割の宣言、`--arg mode=check` の意味はここに書いてあります。全文を取り直さないこと。

1. `architecture { }` の先頭に `title = "..."` と `description = "..."` を書く（3-2 で書いていれば飛ばす）。ルートページの見出しと本文になる。
2. `./gradlew :architecture-test:katachiDocs` で生成し、`architecture-test/build/katachi/docs/` を確かめる。
3. **生成物をコミットするかをユーザに提案する。** 既定の出力先は `build/` の下なのでコミットされない。コミットするなら次の3つを揃える。1つでも欠けると、テストか CI が落ちるか、ドキュメントが黙って古くなる。
   - `architecture-test/build.gradle.kts` の `katachi { }` に出力先を書く（`architecture = ...` の行はそのまま）

     ```kts
     katachi {
         architecture = "..."
         processors {
             docs {
                 outputDir = rootProject.layout.projectDirectory.dir("docs/architecture")
             }
         }
     }
     ```

   - その場所を役割として `layout { }` に宣言する。宣言しないと生成したページが `gitTracked()` に拾われ、`:architecture-test:test` が `[UnexpectedFile]` / `[UnexpectedDirectory]` で落ちる。索引の `README.md` と、役割・group ごとの `*.md` の両方を覆う

     ```kt
     "ArchitectureDocs" {
         description = "katachiDocs が生成するページ。手で書かない"
         layout {
             "docs" {
                 "architecture" {
                     "README.md".file()
                     "**" / "*.md".file()
                 }
             }
         }
     }
     ```

   - CI で `--arg mode=check` を回す（6-B の末尾）

   **出力先は katachi のものになります。** そこにある、今回の生成で作られなかった `*.md` は消されます。手書きの Markdown があるディレクトリを指さないこと。
4. 生成し直し、`./gradlew :architecture-test:test` が通ることを確かめる。

作成・変更したファイルを `add changed` で記録し、`sh $CLI check 6-3`。

## 6-D. (6 の返答後) テンプレートからのコード生成をセットアップする

ステップ 6 で記録した提案に、ユーザが同意した役割だけ行う。

**始める前に、テンプレートからのコード生成のページを読む。**3-1 で取得した全文のうち `Generating code from a template` の節です（日本語版は https://tbsten.github.io/katachi/ja/guides/generate-code-from-template/ ）。`template { }` で書ける語（`file()` と `stringParameter()` などの `*Parameter()`）、`file()` にパスを渡せない理由、既存ファイルがあるときの扱いはここに書いてあります。シグネチャに迷ったら、推測で書かずに 3-1 の「API に迷ったら」の llms.txt を読んでください（`template { }` の入口は https://tbsten.github.io/katachi/api-docs/katachi/me.tbsten.katachi.dsl/-role-scope/template.html.md ）。

1. 提案を確かめる（`sh $CLI data get report` の `templates`）。
2. その役割に `template { }` を書く。**中身は `basedOn` のファイルを元にする。** 生成先のディレクトリは書かない（`layout { }` から決まる）。`file()` にはファイル名だけを渡す

   ```kt
   "UseCase" {
       layout { /* 既存のまま */ }
       template {
           val name by stringParameter()

           file("${name}UseCase.kt") {
               """
                   package com.example.app.domain.useCase

                   interface ${name}UseCase {
                       suspend operator fun invoke()
                   }
               """.trimIndent()
           }
       }
   }
   ```

   使えるのは `stringParameter()` / `booleanParameter()` / `intParameter()`（`default = ...` も可）、`enumParameter()`（`entries` か既定値を渡す）と `file("...") { "中身" }` だけです。これ以外の語を推測で足さないこと。
3. テンプレート独自のパラメータ（`--arg name=...`）は、モジュール側の設定なしにそのまま渡せる。受け付けるのは、名指しした役割の `template { }` が宣言した名前だけで、打ち間違いはこれまでどおり `Unknown processor argument(s): ...` で落ちる。
4. 1本生成し、**直後に検査が通ること**を確かめる。

   ```sh
   ./gradlew :architecture-test:katachiTemplate --arg roleName=UseCase --arg name=Sample
   ./gradlew :architecture-test:test
   ```

   `test` が落ちたら、`template { }` のファイル名が `layout { }` のパターン（`"*UseCase".ktFile()` など）に合っているかを確かめる。
5. **生成したファイルは確認用なので消す**（ユーザが残すと言った場合を除く）。消したあと、もう一度 `./gradlew :architecture-test:test` が通ることを確かめる。

作成・変更したファイルを `add changed` で記録し、`sh $CLI check 6-4`。

**ステップ 6-A〜6-D は任意です。** チェックリストでも任意として扱われ、進捗の分母には入りません。やらなくても `verify` は通ります。
