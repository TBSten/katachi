# katachi のインストール

[katachi](https://tbsten.github.io/katachi) をインストールする手順です。

ユーザのコードベースに合ったアーキテクチャ定義を作成していきます。

**機械的にできることは `katachi-install.sh` がすべて行います。** 手順を自分で補わないでください。

あなたが判断するのは次だけです。

- ステップ 1 — コードベースの解析
- ステップ 3 — `architecture { }` 定義の作成
- ステップ 4 — 残ったアーキテクチャ違反を直すか残すかの判断
- ステップ 6-A / 6-B — 題材タスクの立案、CI の配線

**`scaffold` が生成した `build.gradle.kts` と、追記された `settings` / ルートの build ファイルは書き換えないこと。** `ProjectArchitecture.kt` はステップ 3 で埋める対象なので、これは別です。

## 0. 事前準備

### 始める前に — セッションを片付ける（該当する場合だけ）

これはチェックリストの項目ではありません。番号は付きません。

**このセッションに別件の会話がたまっているなら、まずユーザにそう伝えてください。** この手順はステップ 0 から 6 まで長く、正確なコマンドを何十回も打ちます。ステップ 3 では 3500 行のドキュメントを読み込みます。別件の文脈が多いほど、手順を飛ばす・書かれていない回避策を作る・前の話題の前提を持ち込む、といったことが起きやすくなります。

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
- katachi の最新バージョンの取得
- プロジェクトが使っている Kotlin バージョンの検出
- `check-list.html` と `project-code-base-report.html` の配置
- チェックリストの JSON の初期化（作業用ディレクトリ / バージョン / プロジェクトルート / 日時 / Kotlin / Git）
- 自分自身を `<KATACHI_WORKDIR>/katachi-install.sh` に設置

最後に次の形式で結果が出る（この後に案内が数行続く）。以降のステップはこの値を使う。

```
KATACHI_WORKDIR=tmp/install-katachi
KATACHI_VERSION=0.1.1
KATACHI_PROJECT_ROOT=/path/to/project
KATACHI_KOTLIN=2.4.10
KATACHI_GIT=yes
KATACHI_SETTINGS=settings.gradle.kts
KATACHI_CLI=tmp/install-katachi/katachi-install.sh
KATACHI_LANG=ja
```

**以降のコマンドはすべて `KATACHI_CLI` のパスで実行します。** `/tmp` のほうは使いません。

ここまで終わったら `sh $CLI check 0-1 0-2`。

**止まったら、出力に書かれているとおりに対処する。** 推測で回避策を作らない。作業用ディレクトリを変えたい場合は `--workdir <path>`、バージョンを固定したい場合は `--katachi <version>` を付けて実行し直す。

`init` は**何度実行しても記入済みのファイルを壊しません**。途中で失敗したらそのまま実行し直してよい。

**`init` が「作業用ディレクトリが git から見えています」と警告した場合は、それをユーザに伝えて** `.gitignore` への追加を提案してください。すでに ignore されている置き場がプロジェクトに無いときに起きます。放っておくとチェックリストとレポートがユーザのコミットに入ります。`.gitignore` を自分で書き換えないこと。

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
sh $CLI add module    --path app --kind "Android application" --role "..." --buildFile "app/build.gradle.kts"
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

項目の id は `0-1` `0-2` / `1-1` `1-2` / `2-1` `2-2` / `3-1` `3-2` / `4-1` `4-2` / `5-1` `5-2` の12件と、**任意のステップ 6 の `6-1` `6-2`** の計14件。6-1 / 6-2 は 6-A / 6-B をやったときだけ入れ、`verify` の対象外です。`label` は書き換えないこと。

**JSON を自分で書き換えないこと。** 専用のコマンドがあります。

```sh
sh $CLI check 1-1 1-2          # 完了にする（複数可）
sh $CLI uncheck 1-2            # 戻す
sh $CLI warn 4 "アーキテクチャ違反を 2 件残しました"   # そのステップに警告を付ける
sh $CLI warn 4 --clear         # 警告を消す
```

`check` は**知らない id を渡すと止まります**。打ち間違いが黙って無視されることはありません。

`warn` のステップ id は **0〜5** です（チェックリストのステップはそこまで）。付けたステップは黄色になり、完了していても開いたまま表示されます。

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

`roles` がこのレポートの中核です。`architecture { }` の Role はここから作ります。**1要素 = 1エントリ**で、次を埋めます。

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

`--package` にはステップ 1 で特定したアプリのパッケージ名を渡す。katachi と Kotlin のバージョンは検出されるので、ふつうは他の引数は要りません。

**`init` が「Kotlin のバージョンを検出できませんでした」と警告していた場合だけ**、`--kotlin <version>` を足してください。省くと `scaffold` が止まります。

このコマンドが以下を行う。

- `architecture-test/build.gradle.kts` の作成
- `ProjectArchitecture.kt` と `ProjectArchitectureTest.kt` の作成
- ルートの build ファイルへの Kotlin JVM プラグインの追加（すでにあれば何もしない）
- settings ファイルへの `include("architecture-test")` の追加（Groovy の `settings.gradle` なら `include 'architecture-test'`。すでにあれば何もしない）

プラグインのバージョン衝突、JUnit の engine、JVM toolchain、プロジェクトの Kotlin バージョンに応じたコンパイラオプション、Android / KMP プロジェクトでの扱いは**すべてスクリプトが決めています。** Kotlin 2.4 未満なら `-Xcontext-parameters` を書き込み（これが無いと DSL を1つも呼べません）、2.4 以降では付けません（付けると redundant の警告になるため）。Kotlin 2.2 未満ならその旨を伝えて止まります。katachi の metadata をそのコンパイラが読めないためです。 生成されたファイルを読んで直したくなっても、直さないこと。

例外は、生成されたファイルを変えないとビルドがそもそも動かない場合だけです。これは上のルールより優先します。ただしその場合はスクリプト側のバグなので、`add changed` に理由を書いて記録し、`questions` にも登録してください。

`konsist { }` を使わない方針が決まっている場合だけ `--no-konsist` を付ける。決まっていなければ既定のままでよい。

作成できたら、この時点で一度動かす。

```sh
./gradlew :architecture-test:test --rerun
```

**ここでは失敗するのが正常です。** `architecture { }` が空なので、deny by default の原則どおり、すべてのファイルが `Unexpected` として報告されます。次のような出力になります。

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

`scaffold` が「buildscript { } を持つため自動で書き換えませんでした」と言った場合は、**出力に示された1行をルートの build ファイルに足してください。** 足すまで必ず失敗します。

`Unexpected` だけで落ちることを確認したら、作成・変更されたファイルを記録して `sh $CLI check 2-1 2-2`。

```sh
sh $CLI add changed --path "architecture-test/build.gradle.kts" --change "新規" --summary "検査用モジュールのビルド定義"
sh $CLI add changed --path "settings.gradle.kts" --change "変更" --summary "include(\"architecture-test\") を追加"
```

`scaffold` の出力に並んだファイルをそのまま入れてください。

## 3. architecture { } 定義を作成

**ここはあなたが判断するステップです。**

ユーザからプロンプトに渡された情報、1 で調査した内容をもとに `architecture { }` 定義を作成する。

### 3-1. ドキュメントを1回で取得する

```sh
sh $CLI docs
```

ドキュメントサイト全体が1つのテキストにまとまったものを取得し、そのパスを出力する。**ページを1つずつ開かないこと。** すでに取得済みなら取り直さない。分量が問題になる場合は `--small` を付ける。

**取得されるのは英語版です。** ドキュメントサイトの既定ロケールが英語で、この形式は既定ロケールのぶんしか生成されないため。DSL の API 名は言語に依らないので、定義を書くうえでは差し支えない。

読み終えたら `sh $CLI check 3-1`。

### 3-2. 定義を書く

**よく使う import はこれです。** ドキュメントのサンプルには import が書かれていないので、ここに置いておきます。

```kt
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.DeclarationContainerScope   // 定義を関数に分割するとき
import me.tbsten.katachi.dsl.gradle.*                    // module / sourceSet / kotlin / `/` 演算子
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.kotlin.ktsFile
import me.tbsten.katachi.konsist.konsist                 // konsist { } を書くとき
```

`me.tbsten.katachi.dsl.gradle` は**スター import にしてください。** `/` は `div` 演算子なので、個別に import すると連結が書けません。

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

作成・変更したファイルを `changedFiles` に `data merge` で記載し、`sh $CLI check 3-2`。

## 4. 検証する

```sh
./gradlew :architecture-test:test --rerun
```

を実行して結果を確認する。

### エラーとの向き合い方

- Gradle・Java 由来など katachi に関係ないエラーは修正を試みる。
- katachi 由来のアーキテクチャ違反のエラー: 無理に直そうとはしない。
  - 軽微なものは Agent 自身で修正
  - 対処法が曖昧なものは後のステップでユーザに確認してもらうこととし、そのままにしておく
  - そのままにするエラーはチェックリストの `violations` に記載する（`violation` / `location` / `whyNotFixed` / `suggestion`）。
  - ユーザに判断してほしいことは**チェックリストの** `questions` に入れる（`sh $CLI add question ...`）。レポート側にも同名の配列があるが、そちらはステップ 1 で気づいた「コードベースの揺れ」を書く場所で、用途が違う。

**緑になったら `ProjectArchitectureTest.kt` の `maxViolations` を外してください。** `scaffold` が導入中の見通しのために入れた一時設定で、`TODO` コメントが目印です。外したあとにもう一度 `./gradlew :architecture-test:test --rerun` を実行し、結果が変わらないことを確かめます。違反を残す場合は、残した件数が 10 件を超えるならそのまま残してよいので、その旨を `violations` に書いてください。

終えたら `sh $CLI check 4-1 4-2`。違反を残した場合は `sh $CLI warn 4 "..."` で何を残したかを1行書く。

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

未完了の項目が残っていれば失敗の形式が、すべて終わっていれば成功の形式が出ます。違反の件数、確認したいことの件数、変更したファイル数は JSON から拾われます。

`questions` に中身がある場合だけ、`summary` の出力に続けて各項目の「問い」「選択肢」「推奨」を添えてください。**ここは人が読んで判断する箇所なので、例外的にあなたが文章にします。** それ以外は `summary` の出力をそのまま使います。

## 6-A. (6 の返答後) 導入できたことを確かめる

題材にする実装タスクを確認してタスクを実行する。

**新しいファイルが増えるタスクを選んでください。** katachi が見るのはファイルの配置と役割なので、既存ファイルを直すだけのタスク（バグ修正など）では何も起きません。実地検証では、7 件中 4 件が既存ファイルの修正だけで、katachi を試す機会がありませんでした。新しい画面・新しいモジュール・新しいテストが増えるものが向いています。

- ユーザから指示されたタスクがない場合は 系統の違うタスクを 1〜5 個程度考えてユーザに伝えたのち、実行する。
- ユーザから指示されたタスクがある場合は そのタスクの妥当性を判断する。妥当と考えにくいタスクの場合は本当に実行するかユーザに判断を仰ぐ。

タスク実行後、`./gradlew :architecture-test:test` を実行し `architecture { }` の修正が必要かを判断する。

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

### GitHub Actions 以外を使っている場合

Pull request 作成時・Merge request 作成時・pre-push hook など ストレスにならない適切なタイミングで `./gradlew :architecture-test:test` が実行・レポートされるようにワークフローを修正する。

終わったら `sh $CLI check 6-2`。

**ステップ 6-A と 6-B は任意です。** チェックリストでも任意として扱われ、進捗の分母には入りません。やらなくても `verify` は通ります。
