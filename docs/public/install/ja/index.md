# katachi のインストール

[katachi](https://tbsten.github.io/katachi) をインストールする手順です。ユーザのコードベースに合ったアーキテクチャ定義を作成していきます。

**この手順書は全文を一度に読まないこと。** Web 取得ツールの要約は手順を取りこぼすので、原文を保存してからセクションごとに読む: `curl -fsSL -o <保存先>/install.md https://tbsten.github.io/katachi/install/ja/index.md`。0 章は保存したファイルを `sed -n '/^## 0\./,/^## 1\./p'` のように切って読む。`init` のあとは `sh $CLI guide <セクション ID>` がセクションだけを出す（`sh $CLI guide` で目次）。各セクションの終わりに次のセクションのコマンドが出る。

**目次**（セクション ID と見出し。本文は `sh $CLI guide <セクション ID>` で取り出す）

- 0 — 事前準備
  - 0-1 — 権限を先に取り切る
  - 0-2 — init を実行する
  - 0-3 — チェックリストとレポートの読み書き
- 1 — 現在のプロジェクトコードベースの構造・各コンポーネントの制約を解析・推論
  - 1-1 — まずプロジェクト自身のドキュメントを読む
  - 1-2 — コードベースを走査してレポートを埋める
  - 1-3 — 書き方の方針を決める
- 2 — 検査用モジュールを作成する
  - 2-1 — scaffold を実行する
  - 2-2 — 空の定義で一度動かす
- 3 — architecture { } 定義を作成
  - 3-1 — ドキュメントを話題ごとに取得する
  - 3-2 — 定義を書く
  - 3-3 — 定義をリファクタリングする
- 4 — 検証する
  - 4-1 — テストを実行する
  - 4-2 — エラーとの向き合い方
  - 4-3 — ドキュメントを生成する
- 5 — チェックリストを確認する
- 6 — ユーザに結果を返答する
  - 6-0 — レビューを受けて定義を直す
- 6-A — (6 の返答後) 導入できたことを確かめる
- 6-B — (6 の返答後) CI への導入を依頼された場合
- 6-C — (6 の返答後) ドキュメント生成をセットアップする
- 6-D — (6 の返答後) テンプレートからのコード生成をセットアップする

6-A〜6-D は、チェックリストでは 6-1〜6-4 です（`guide 6-1` でも取り出せます）。

**本体と subagent の分担**

subagent を使える環境では、次の表のとおりに分けます。作業用ディレクトリへの書き込みは本体だけが行い、subagent には書かせません。

| セクション | 本体 | subagent に任せる | 本体が受け取るもの |
|---|---|---|---|
| 1-1, 1-2 | 書き込み（`add` / `data merge`、1 プロセスずつ） | 読み取り・走査・Role 候補の下書き | セクションごとの JSON（subagent は書かない） |
| 1-3, 2 | 全部 | — | — |
| 3-1 | `docs --topic` | API リファレンス（`docs --api`）から該当 API を探す | シグネチャと例だけ |
| 3-2, 3-3, 4 | 定義の編集と `sh $CLI test` | — | — |
| 6-A | 題材の選定と判断 | 実装と検証（出力先は subagent ごと） | 違反の件数と差分（`test` の要約） |
| 6-B〜6-D | 全部 | — | — |

## 0. 事前準備

**機械的にできることは `katachi-install.sh` がすべて行います。** 手順を自分で補わないでください。

あなたが判断するのは次だけです。

- ステップ 1: コードベースの解析と、書き方の方針の確認
- ステップ 3: `architecture { }` 定義の作成とリファクタリング
- ステップ 4: 残ったアーキテクチャ違反を直すか残すかの判断
- ステップ 6: どの Role にテンプレートを当てるかの提案と、レビューの反映
- ステップ 6-A / 6-B / 6-C / 6-D: 題材タスクの立案、CI の配線、ドキュメント生成とテンプレートのセットアップ

**`scaffold` が生成した `build.gradle.kts` と、追記された `settings` / ルートの build ファイル / `gradle/libs.versions.toml` は書き換えないこと。** `ProjectArchitecture.kt` はステップ 3 で埋める対象なので、これは別です。`build.gradle.kts` の `katachi { }` に `processors { }` を足すのは、6-C / 6-D でユーザが同意したときだけです。

### 始める前に — セッションを片付ける（該当する場合だけ）

これはチェックリストの項目ではありません。番号は付きません。

**このセッションに別件の会話がたまっているなら、まずユーザにそう伝えてください。** この手順はステップ 0 から 6 まで長く、正確なコマンドを何十回も打ちます。ステップ 3 ではドキュメントを読み込みます。別件の文脈が多いほど、手順を飛ばす・書かれていない回避策を作る・前の話題の前提を持ち込む、といったことが起きやすくなります。

**勝手に判断せず、提案だけしてください。** ユーザが続行を選んだらそのまま進めます。

クリアするとこの手順書も失われるので、提案するときは次の文面を添えてください。新しいセッションでこれを貼れば同じところから始められます。

```
このプロジェクトに katachi を導入してください。
https://tbsten.github.io/katachi/install/ja/index.md に従って導入してください。
```

作業用ディレクトリを指定したい場合は「作業用ディレクトリは <パス> を使って」も添えてもらいます。

この手順には、セッションを終えてよい区切りが 3 か所あります（ステップ 6 の末尾、6-0 の末尾、6-A〜6-D の各末尾）。区切りには再開のしかたが書いてあります。

以前の版の `katachi-install.sh` で作った作業用ディレクトリがあるなら、`init --force` で作り直してください（チェックリストの JSON は消えます。古い HTML のままだと `check 1-3` が「知らない項目 id」で止まります）。

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

> `# PATH の java と別の JDK が要るプロジェクトでは、以降の ./gradlew と sh $CLI の呼び出しすべてに同じ JAVA_HOME=<path> を前置する。シェルの変数は呼び出しをまたいで残らない。doctor と test が「JDK: N（出どころ）」を出すので、出どころが JAVA_HOME なら毎回付ける。`

```sh
# 4. 取得したスクリプトの実行（あわせて、必要なコマンドがそろっているかを確かめる）
sh /tmp/katachi-install.sh doctor --lang ja
```

4 の `doctor` は、この手順が使うコマンド（`curl`・`python3` と、`awk`・`sed` などの基本コマンド。`git` は推奨）がそろっているかを確かめます。**足りなければ、足りないものを全部と入れ方の手がかりを並べて止まります。** その出力をユーザに伝え、入れてもらってから 4 をやり直してください。`init` も最初に同じ確認をします。

作業用ディレクトリも検査用モジュール（デフォルト `architecture-test/`）もプロジェクトルートの下に作るので、2 で両方の許可が取れます。**プローブは痕跡を残しません。** ディレクトリを作るプローブは書かないこと（検査用モジュールのディレクトリが空で残ると、ステップ 2 の `scaffold` が「すでに存在します」で止まります）。

**どれかで止まったら、そこで何が拒否されたかをユーザに伝えてください。** 1 が通らなければ手順を始められません。2 が通らなければファイルを作れません。3 が通らなければ検証できません。4 が通らなければ以降のステップを1つも実行できません。エージェントは 1 と 4 をまとめて「外部スクリプトのダウンロードと実行」として拒否することがよくあります。その場合はその2行をユーザ自身に実行してもらってから続けてください。

すべて通れば、以降のステップで許可を聞かれることはありません。

**既存のソースコードは書き換えません。** 手を入れるのは、ステップ 4 で katachi と無関係なビルドエラーを直すときと、ステップ 6-A の実装タスクのときだけです。ステップ 4 でアーキテクチャ違反そのものを直す場合は、その時点で改めてユーザに確認します。

この時点ではまだ `$CLI` がありません。チェックは次のステップの最後でまとめて入れます。

### 0-2. init を実行する

**Gradle のルートディレクトリで**次を実行する。

```sh
sh /tmp/katachi-install.sh init --lang ja
```

`--lang ja` は、チェックリストとレポートを日本語で取得し、**スクリプトの出力（`summary` の文面を含む）も日本語にする**ための指定です。**省略すると英語になります**（サイトのデフォルトが英語のため）。一度指定すれば作業用ディレクトリに記録されるので、以降のコマンドでは要りません。

検査用モジュールの置き場所をユーザが指定している場合は `--module-dir <Gradle ルートからの相対パス>` を付けます（例: `--module-dir static-analysis/katachi`）。指定が無ければ `architecture-test` です。あとから変えるときはステップ 1-3 の `preferences set moduleDir=<path>` で記録します。

スクリプトは 0-1 で取得済みです。**`curl` は手順全体で1回だけ**で、`init` が自分自身を作業用ディレクトリにコピーするので、以降のコマンドはそちらを使います。

このコマンドが以下をすべて行う。**同じことを手作業でやり直さないこと。**

- 必要なコマンドがそろっているかの確認（足りなければ、何も作らずに止まる）
- Gradle のプロジェクトであることの確認（違えば止まる）
- Git 管理下かの確認（無ければ警告する）
- 作業用ディレクトリの決定と作成（`.gitignore` されている置き場があればその中、無ければ `tmp/install-katachi/`）
- katachi の最新バージョンの取得（この手順が前提にする 0.3.0 より前なら、何も作らずに止まる）
- プロジェクトが使っている Kotlin バージョンの検出
- `check-list.html` と `project-code-base-report.html` の配置
- この手順書の取得（`guide` がセクションごとに出すための原文。取り直すときは `sh $CLI guide --refresh`）
- チェックリストの JSON の初期化（作業用ディレクトリ / バージョン / プロジェクトルート / 日時 / Kotlin / Git / 検査用モジュールの置き場所）
- 自分自身を `<KATACHI_WORKDIR>/tmp/katachi-install.sh` に設置

最後に次の形式で結果が出る（この後に案内が数行続く）。以降のステップはこの値を使う。

```
KATACHI_WORKDIR=/path/to/project/tmp/install-katachi
KATACHI_VERSION=0.3.0
KATACHI_PROJECT_ROOT=/path/to/project
KATACHI_KOTLIN=2.4.10
KATACHI_GIT=yes
KATACHI_SETTINGS=/path/to/project/settings.gradle.kts
KATACHI_CLI=/path/to/project/tmp/install-katachi/tmp/katachi-install.sh
KATACHI_LANG=ja
KATACHI_MODULE_DIR=architecture-test
KATACHI_MODULE_PATH=:architecture-test
```

**以降のコマンドはすべて `KATACHI_CLI` のパスで実行します。** `/tmp` のほうは使いません。この手順書に出てくる `:architecture-test:` は `KATACHI_MODULE_PATH` に、`architecture-test/` は `KATACHI_MODULE_DIR` に読み替えてください（`--module-dir` を変えていなければそのままです）。

ここまで終わったら `sh $CLI check 0-1 0-2`。

**止まったら、出力に書かれているとおりに対処する。** 推測で回避策を作らない。作業用ディレクトリを変えたい場合は `--workdir <path>`、バージョンを固定したい場合は `--katachi <version>` を付けて実行し直す（`init` が記録した版を `scaffold` も使います。`--katachi` なしでやり直したときは、前回記録した版をそのまま使います）。

`init` は**何度実行しても記入済みのファイルを壊しません**。途中で失敗したらそのまま実行し直してよい。

**`init` が「作業用ディレクトリが git から見えています」と警告した場合は、それをユーザに伝えて** `.gitignore` への追加を提案してください。すでに ignore されている置き場がプロジェクトに無いときに起きます。katachi は未追跡でも ignore されていないファイルを検査するので、放っておくとステップ 4 で作業用ディレクトリ自体が `[UnexpectedFile]` になります（チェックリストとレポートがユーザのコミットに入る原因にもなります）。`.gitignore` を自分で書き換えないこと。

**「未追跡で ignore もされていないものがあります」と一覧が出た場合も、理由は同じです。** 一覧のものを定義で宣言するか `.gitignore` に足すかを、ユーザに確認してください。

作業用ディレクトリの直下に置くのは、人が開く `check-list.html` と `project-code-base-report.html`、それに `tmp/` だけです。スクリプトの写し、取得したファイルの置き場（`tmp/cache/`）、メモやログなどの一時ファイルは、すべて `<KATACHI_WORKDIR>/tmp/` の中に置いてください。直下には何も足さないこと。

### 0-3. チェックリストとレポートの読み書き

`check-list.html` と `project-code-base-report.html` は、**データを JSON として中に持っている1枚の HTML** です。HTML は描画するだけの器で、**あなたが読み書きするのは JSON だけです。**

```sh
# 以降 `$CLI` と書いてある箇所は、init が出力した KATACHI_CLI のパスに読み替えること。
# シェルの変数は呼び出しをまたいで残らないので、毎回パスを書く。
CLI=<KATACHI_CLI のパス>

# 読む（JSON だけが出る。HTML は読まなくてよい）
sh $CLI data get report
sh $CLI data get check-list

# 一部だけ読む（キーはドット区切り。無いキーなら止まる）
sh $CLI data get report questions
sh $CLI data get check-list meta.moduleDir

# 一部だけ更新する（既存の内容に深くマージされる。ふだんはこちら）
sh $CLI data merge report partial.json

# 丸ごと入れ替える
sh $CLI data set report new.json
```

`report` と `check-list` は作業用ディレクトリのファイルを指す短縮名です。

**`data merge` は配列を丸ごと置き換えます。追記ではありません。** 2件目を merge すると、1件目が消えます。配列に足すときは `add` を使ってください。

```sh
sh $CLI add violation --violation "..." --location "..." --whyNotFixed "..." --suggestion "..."
sh $CLI add question  --question "..." --observed "..." --options "A" --options "B" --recommendation "..."
sh $CLI add codebase-question --question "..." --observed "..." --options "A" --options "B" --recommendation "..."
sh $CLI add changed   --path "..." --change "新規" --summary "..."
sh $CLI add role      --importance 5 --name ViewModel --group feature.ui --layout "..." --naming "..." --allowed "..." --forbidden "..." --examples "..." --count 12
sh $CLI add module    --path app --kind "Android application" --responsibility "..." --buildFile "app/build.gradle.kts" --deviation "..."
sh $CLI add tool      --name ktlint --configPath ".editorconfig" --declareInKatachi "yes"
sh $CLI add excluded  --path "..." --reason "..."
```

`--options` と `--allowed` / `--forbidden` / `--examples` は**複数回渡せます**。知らない項目名を渡すと、使える名前を並べて止まります。項目を1つも渡さなかったとき、および同じ `--path` / `--name` がすでに登録されているときも止まります。空の行や重複した行が入らないようにするためです。

`add` は、次の 2 つの場合にも止まります。

- `add role` は、`--layout` が Gradle のビルドファイル（`build.gradle.kts`、`settings.gradle.kts`、`gradle.properties`、version catalog など）なら止まります。Gradle のファイルは `gradle()` が受け持つので、Role 候補には書きません（`buildSrc` と `includeBuild` したビルドは除く）。ビルドファイルに制約を付けるためだけの Role なら、`--constraint-only --note <何を検査するか>` を付けて記録します
- `add excluded` は、そのパスが `.gitignore` で除外されていれば止まります。katachi は `.gitignore` されたファイルをもともと見ないので、対象外に書く意味がありません

`add changed` は**1件ずつ**です。定義ファイルが何十件にもなったときは、**ディレクトリやワイルドカードでまとめて1件**にしてください（例: `--path "architecture-test/src/test/kotlin/**" --change "新規" --summary "architecture { } の定義一式（12ファイル）"`）。1ファイル1件で埋める必要はありません。

**HTML を直接編集しないこと。** 書き換えは必ず `data merge` か `data set` を通す。どちらも書き込む前に JSON を検査し、元のファイルを `.bak` に退避し、結果が壊れていれば自動で巻き戻します。

#### 分かったことはその場で書く

**まとめて最後に書かないこと。** 途中で中断したときに調査結果が丸ごと消えます。

`data merge` は部分的な JSON を受け取るので、1項目ずつでも書けます。

```sh
printf '{"overview":{"appPackage":"com.example.app"}}\n' > <KATACHI_WORKDIR>/tmp/p.json
sh $CLI data merge report <KATACHI_WORKDIR>/tmp/p.json
```

目安として、**1つのセクションが埋まるたびに1回** `data merge` を実行してください。ステップ 1 なら `overview` → `architecture` → `modules` → `directoryTree` → `roles` → `tools` → `excluded` で7回以上になります。subagent に分担させる場合は、各 subagent の結果が返るたびに書きます。**書き込みは 1 プロセスずつ。** subagent には書かせず、結果を受け取った本体が順に書く（ロックで直列化されるが、並べて走らせると待つだけで速くならない）。

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

項目の id は `0-1` `0-2` / `1-1` `1-2` `1-3` / `2-1` `2-2` / `3-1` `3-2` `3-3` / `4-1` `4-2` `4-3` / `5-1` `5-2` の15件と、**任意のステップ 6 の `6-1` `6-2` `6-3` `6-4`** の計19件。6-1〜6-4 はそれぞれ 6-A〜6-D をやったときだけ入れ、`verify` の対象外です。`label` は書き換えないこと。

**JSON を自分で書き換えないこと。** 専用のコマンドがあります。

```sh
sh $CLI check 1-1 1-2          # 完了にする（複数可）
sh $CLI uncheck 1-2            # 戻す
sh $CLI warn 4 "アーキテクチャ違反を 2 件残しました"   # そのステップに警告を付ける
sh $CLI warn 4 --clear         # 警告を消す
```

`check` は**知らない id を渡すと止まります**。打ち間違いに気づけないまま進むことはありません。完了にしたあと `次: sh $CLI guide <セクション ID>` を 1 行出すので、次はそのセクションを読みます。

`warn` のステップ id は **0〜6** です（チェックリストのステップはそこまで）。付けたステップは黄色になり、完了していても開いたまま表示されます。

ほかに `violations`（残したアーキテクチャ違反）、`questions`（ユーザに確認したいこと）、`changedFiles`（作成・変更したファイル）の配列と、`preferences`（書き方の方針。1-3）、`answers`（確認したいことへの回答。1-2）があります。

## 1. 現在のプロジェクトコードベースの構造・各コンポーネントの制約を解析・推論

**ここはあなたが判断するステップです。**

プロジェクト内の Git にコミットされているすべてのファイルを走査し、`<KATACHI_WORKDIR>/project-code-base-report.html` の JSON を埋める。このファイルは init がすでに配置している。**テンプレートを取得し直さないこと。**

### 1-1. まずプロジェクト自身のドキュメントを読む

**コードより先に読んでください。** コードは「いまこうなっている」を示し、ドキュメントは「こうあるべき」を示します。`architecture { }` に書くのは後者です。コードだけを見て書くと、**現状をそのまま追認した定義**になり、既存の逸脱を「正しいもの」として固定してしまいます。

見る場所は次のとおりです（あるものだけでよい）。

- `README` / `CONTRIBUTING` / `ARCHITECTURE.md`
- `docs/` `doc/` `documentation/` とドキュメントサイト（`docs-site/` など）
- ADR（`docs/adr/`、`doc/architecture/decisions/` など）
- `CLAUDE.md` / `AGENTS.md` / `.cursor/rules` など、AI エージェント向けの規約
- 各モジュール直下の `README`

取り出すものは次のとおりです。

| 取り出すもの | 行き先 |
|---|---|
| **チームが実際に使っている語彙**（`UseCase` か `Interactor` か、`Screen` か `Page` か） | `roles[].name`。**勝手に命名しないこと** |
| 意図的な規則（「UI から Repository を直接呼ばない」など） | `roles[].allowed` / `forbidden` |
| アーキテクチャの名前と、その選択の理由 | `architecture.name` / `rationale` / `characteristics` |
| 独自の検査（Kotlin compiler plugin、detekt のカスタムルール、ktlint など） | `tools` |

食い違いは `sh $CLI add codebase-question ...` で書きます（`add question` はチェックリスト側で、用途が違います）。id はレポート側が `cq1`・`cq2`…、チェックリスト側が `q1`・`q2`… で、重なりません。

**ドキュメントとコードが食い違ったら、`questions` に書いてください。** 黙ってコード側に合わせないこと。ドキュメントが古いのか、コードが逸脱しているのかは、利用者にしか判断できません。この食い違いこそが、ステップ3の前にユーザへ提示すべきものです。

ドキュメントが1つも無ければ、このセクションは飛ばしてコードから推測します。その場合は「ドキュメントが無いので、以下はコードからの推測です」と `questions` に一言残してください。

読み終えたら `sh $CLI check 1-1`。

### 1-2. コードベースを走査してレポートを埋める

答えるのは次の4つ。

- 採用しているアーキテクチャは何か？プロジェクト構成上の特徴にはどんなものがあるか？
- どんな要素があるのか？その要素内にはどんなコードを書くことができるのか？
- 利用しているツールは何か？
- アプリのパッケージ名にはどういうものを利用しているのか？

手順は 0-3 のとおり。

```sh
# 分かったところから、セクションごとに書いていく
sh $CLI data merge report <KATACHI_WORKDIR>/tmp/overview.json
sh $CLI data merge report <KATACHI_WORKDIR>/tmp/roles.json
```

レポートの `meta`（プロジェクト名・解析日時・ファイル数・Gradle ルート・Git ルート）は **init がすでに埋めています。** 触る必要はありません。

**`null` と空配列が「未記入」を表す。** 埋めた結果に `null` や `[]` が残っていれば、それはまだ調べていない箇所です。スキーマそのものは `data get` の出力が示しています。HTML の中に記入例の JSON がコメントで1つ入っているので、形に迷ったらそれを見る。

`modules` には、モジュールごとに `path` / `kind` / `responsibility` / `buildFile` を書きます。同じ `kind` のほかのモジュールと構成が違う点があれば `--deviation` に書いてください（無ければ省略）。レポートはモジュールを `path` の木として描くので、親子を別に書く必要はありません。

`roles` がこのレポートの中核です。`architecture { }` の Role はここから作ります。**1 種類のファイル = 1 エントリ**で、次を埋めます。モジュールや「ルートの設定ファイル」のように種類の違うファイルのまとまりは1エントリにせず、種類ごとに分けて書きます（まとまりは定義では group になります。3-2 の「1 つの Role = 1 種類のファイル」）。

| 項目 | 中身 |
|---|---|
| `importance` | **1〜5 の整数。5 が最重要。** `architecture { }` に入れる優先度。レポートはこの順に並ぶ |
| `name` | 要素名（`ViewModel` など） |
| `group` | 属する group。入れ子は `.` 区切り（`feature.ui` など） |
| `layout` | 配置パターン |
| `naming` | 命名規約 |
| `allowed` / `forbidden` | 書いてよいもの / 書いてはいけないもの |
| `examples` | 実例ファイルのパス |
| `count` | 現物の件数 |

`importance` / `name` / `group` / `layout` / `naming` は**必須**です。埋めないと `verify` が通りません。

#### Role 候補の洗い出しの指針

- **Role 候補は group の木として書く。** group は「モジュール → 層 → 種類」の順で入れ子にし、1 つの group に Role を 8 個以上並べない（並ぶなら group を切る）
- **`build.gradle.kts` / `settings.gradle.kts` / `gradle.properties` / version catalog は `gradle()` が受け持つ。** モジュール種別ごとの Role を作らない。`add role` も拒む（`buildSrc` と `includeBuild` したビルドは除く）。例外は、ビルドファイルに制約（namespace とパッケージの一致など）を付けたいもので、`add role --constraint-only --note <理由>` で記録する。定義では Role にせず、制約を `gradle { buildScript { } }` などの hook に書く。hook の制約は全モジュールのビルドファイルを受け取るので、1 つのモジュールだけを見るなら制約の中で絞る（3-2 の「1 つの Role = 1 種類のファイル」の Gradle の段落）
- **`documented = false` にするもの:** (1) 生成物（build 出力、コミットされた生成コード）、(2) ビルド・ツールの設定（`.editorconfig`、CI の yaml、lint の設定）、(3) 1 ファイルしかない補助（README、LICENSE、`.gitignore`）。利用者が読んで役に立つ Role だけを生成ドキュメントに載せる
- **Android の定番（`AndroidManifest.xml`、`res/`）は、レシピをコピーして合わせる。** https://tbsten.github.io/katachi/ja/recipes/android-three-layer/ の定義を元にする

このタスクはコンテキストを多く消費しうるので、利用できる場合は適宜 subagent を起動するなどしてください（冒頭の分担表のとおり、subagent は読み取りと下書きまでで、書き込みは本体が行います）。

すべての記載が完了し次第 `sh $CLI check 1-2`。

**レポートの `questions` に書いたことは、この時点でユーザに提示して回答をもらってください。** ここに書くのは「画面のファイルをパッケージ直下に統一するか、サブパッケージも許すか」のような揺れで、**どちらに決めるかで `layout` の書き方が変わります。** 定義を書き終えてから聞くと、書き直しになります。**ステップ 3 より後に回さないこと。**

聞き方は次のとおりです。チャットに問いを並べ直さないこと。

1. `questions` を書き終えたら、レポートの URL（`file://` で始まる `project-code-base-report.html` のパス）を示し、ブラウザの「7. ユーザに確認したいこと」で回答して「回答を書き出す」で JSON を保存してもらう
2. 受け取った JSON を `sh $CLI answers import <json のパス>` で取り込む。取り込めなかった id は警告に出る
3. チャットで答えが来たら、1 件ずつ `sh $CLI answers set <id> "<回答>"` で取り込む（`--note "<補足>"` を付けられる）。書き込み先は id で決まる（`cq` で始まればレポート、`q` で始まればチェックリスト）

`sh $CLI answers` が、チェックリストとレポートのそれぞれについて、回答済みの件数と未回答の一覧を出します。

**このステップで特定したアプリのパッケージ名をステップ 2 で使う。**

### 1-3. 書き方の方針を決める

定義の書き方のうち、ユーザの好みで変わるものを先に決めます。`scaffold` と `lint` がこの記録を読みます。

```sh
sh $CLI preferences
```

出力をそのままユーザに貼り、「推奨から変えたいものだけ答えてください」と 1 回で聞きます。1 項目ずつ聞かないこと。

| key | 問い | 値 | 推奨 | 読む側 |
|---|---|---|---|---|
| `nestedGroups` | group を入れ子にするか | `yes` / `no` | `yes` | `lint` |
| `moduleDir` | 検査用モジュールの置き場所 | Gradle ルートからの相対パス | `architecture-test` | `scaffold` 以降すべて |
| `testFramework` | テストのフレームワーク | `junit5` / `kotest` | プロジェクトから検出 | `scaffold` |
| `dependencyStyle` | 依存の書き方 | `catalog` / `literal` | `gradle/libs.versions.toml` があれば `catalog` | `scaffold` |
| `constraintNames` | 制約名に確認事項の番号を入れるか | `plain` / `numbered` | `plain` | 3-2（あなたが読む） |
| `oneRolePerFile` | 1 Role 1 ファイルで書くか | `yes` / `no` | `yes` | `lint`（3-3） |

回答を記録して完了にします。推奨のままでよいと言われたら、`set` は飛ばして `check` だけでかまいません。

```sh
sh $CLI preferences set nestedGroups=yes testFramework=kotest
sh $CLI check 1-3
```

知らないキーや値を渡すと、使える値を並べて止まります。

## 2. 検査用モジュールを作成する

### 2-1. scaffold を実行する

```sh
sh $CLI scaffold --package com.example.app
```

`--package` にはステップ 1 で特定したアプリのパッケージ名を渡す。katachi のバージョンは `init` が記録したものを、Kotlin のバージョンはプロジェクトから検出したものを使うので、ふつうは他の引数は要りません。検査用モジュールの置き場所・テストのフレームワーク・依存の書き方は 1-3 で記録した方針から読みます。方針を記録せずに決め打ちしたいときだけ `--module-dir <path>` / `--test-framework junit5|kotest` / `--deps catalog|literal` を付けます。

**`init` が「Kotlin のバージョンを検出できませんでした」と警告していた場合だけ**、`--kotlin <version>` を足してください。省くと `scaffold` が止まります。渡した版は、`scaffold` がチェックリストの `meta.kotlin` にも書き込みます。

このコマンドが以下を行う。

- 検査用モジュール（デフォルト `architecture-test/`）の `build.gradle.kts` の作成。Kotlin JVM と katachi の Gradle plugin（`me.tbsten.katachi`）を入れ、`katachi { architecture = "<パッケージ>.test.architecture.projectArchitecture" }` で定義の置き場所を plugin に教える。plugin は katachi の依存を足さないので、`testImplementation("me.tbsten.katachi:katachi:<version>")` なども一緒に書かれる。`tasks.test { }` には `outputs.upToDateWhen { false }` と `outputs.cacheIf { false }` も書かれる。katachi が検査するファイル（リポジトリ全体）は Test タスクの入力に入っていないので、これが無いとファイルを足したり動かしたりしても Gradle がテストを `UP-TO-DATE` として飛ばし、違反があっても緑のままになる
- `ProjectArchitecture.kt` と `ProjectArchitectureTest.kt` の作成（`testFramework` が `kotest` なら `FunSpec` の形）
- `gradle/libs.versions.toml` への追記（`dependencyStyle` が `catalog` のとき）。`katachi` / `katachiKonsist` / JUnit の別名を `[versions]` / `[libraries]` / `[plugins]` に足し、`build.gradle.kts` はそれを `libs.katachi` のように参照する。すでに同じものを指す別名があればそれを使う。Kotlin plugin の行だけは catalog でも `kotlin("jvm")`（版なし）のまま。`alias(libs.plugins.kotlin.jvm)` と書くと、build-logic（includeBuild）経由で Kotlin plugin が classpath にあるとき "already on the classpath with an unknown version" で失敗するため。カタログに寄せるなら `id(libs.plugins.kotlin.jvm.get().pluginId)` の形になる
- ルートの build ファイルへの Kotlin JVM プラグインの追加（ルートの `plugins { }` / `buildscript { }` か `buildSrc` に Kotlin のプラグインがすでにあれば何もしない。サブプロジェクトだけが版付きで宣言している場合は、ルートには足さず検査用モジュール側に版を書く。このとき Gradle が `The Kotlin Gradle plugin was loaded multiple times in different subprojects ...` と警告しますが、この構成では想定どおりなのでそのままにしてください。ルートに足したり版を消したりすると、今度はプラグインの解決で失敗します）
- settings ファイルへの `include(":architecture-test")` の追加（Groovy の `settings.gradle` なら `include ':architecture-test'`。`--module-dir static-analysis/katachi` なら `include(":static-analysis:katachi")`。すでにあれば何もしない）
- settings ファイルの `pluginManagement { repositories { } }` への `mavenCentral()` の追加。katachi の Gradle plugin は Maven Central にあるため（すでにあれば何もしない。`pluginManagement { }` が無ければ作る）

プラグインのバージョン衝突、JUnit の engine、JVM toolchain（下記）、プロジェクトの Kotlin バージョンに応じたコンパイラオプション、Android / KMP プロジェクトでの扱いは**すべてスクリプトが決めています。** Kotlin 2.4 未満なら `-Xcontext-parameters` を書き込み（これが無いと DSL を1つも呼べません）、2.4 以降では付けません（付けると redundant の警告になるため）。Kotlin 2.2 未満ならその旨を伝えて止まります。katachi の metadata をそのコンパイラが読めないためです。生成されたファイルを読んで直したくなっても、直さないこと。

JVM toolchain は、プロジェクトに既にある設定（ほかのモジュールの `jvmToolchain(N)`、`JavaLanguageVersion.of(N)`、buildSrc・build-logic の中の同じ書き方、version catalog や `val` から読む形）と同じ N を使います。katachi は JDK 17 以上が要るので、見つかった N が 17 未満なら使いません。設定が見つからないときは `jvmToolchain` を書かず、Gradle を動かしている JDK をそのまま使います（JDK 17 が無く toolchain の自動取得も無い環境で失敗しないため）。どちらにしたかは scaffold の出力とチェックリストの「JVM toolchain」に残ります。`doctor` と `scaffold` は Gradle が使う JDK が 17 以上かも確かめ、未満なら直し方を出して止まります。

例外は、生成されたファイルを変えないとビルドがそもそも動かない場合だけです。これは上のルールより優先します。ただしその場合はスクリプト側のバグなので、`add changed` に理由を書いて記録し、`questions` にも登録してください。

`ktFileContent { }` を使わない方針が決まっている場合だけ `--no-konsist` を付ける（`ktFileContent { }` は katachi-konsist に入っている）。この構成ではテストが `assert()` に `FileConstraintCheck()` を渡さないので、`gradle { }` の hook に書いた `fileConstraint` も評価されず、test は `[UncheckedFileConstraint]` で失敗する（How to fix は `Pass FileConstraintCheck() to assert()`）。決まっていなければデフォルトのままでよい。

作成できたら `sh $CLI check 2-1`。

### 2-2. 空の定義で一度動かす

```sh
sh $CLI test
```

`test` は検査用モジュールの `test` タスク（`./gradlew :architecture-test:test`）を実行し、結果を要約します。全文のログは `<KATACHI_WORKDIR>/tmp/test/` に残り、要約の `ログ:` の行にパスが出ます。Gradle を直接起動せず、この手順書ではいつも `sh $CLI test` を使ってください（6-B の CI だけは `./gradlew` の形で書きます）。

**ここでは失敗するのが正常です。** `architecture { }` が空なので、deny by default の原則どおり、すべてのファイルが `Unexpected` として報告されます。要約は次のようになります。

```
JDK: 21（JAVA_HOME）
ログ: file:///path/to/project/tmp/install-katachi/tmp/test/2026-10-06T01-02-03Z.log
結果: Katachi check failed: 4 violations (Unexpected: 4)
```

ログの中では、違反ごとに次の形の段落が続きます（ファイルとディレクトリの例）。

```
[UnexpectedFile] file:///path/to/project/.gitignore
  No role is defined for this file.

  How to fix:
    - Delete it if it is not needed
    - Add a new role for it:
        "Gitignore".role {
          summary = "TODO"
          layout {
            ".gitignore".file()
          }
        }

[UnexpectedDirectory] file:///path/to/project/.gemini
  No role is defined for this directory. Nothing below it was checked.

  How to fix:
    - Delete it if it is not needed
    - Declare what belongs in it in the layout of an existing role
    - Add a new role for it:
        "Gemini".role {
          summary = "TODO"
          layout {
            ".gemini" { ignore() }
          }
        }
```

違反の表示件数はデフォルトで 10 件です。導入中は数十〜百件出るので、全件が要るときは `sh $CLI test -- -Dkatachi.maxViolations=all` で出します（`--` より後ろは `./gradlew` にそのまま渡り、katachi の Gradle plugin がテストの JVM に届けます）。テストのコードに `maxViolations` を書く必要はありません。

**この形で失敗していれば配線は正しい**ということです。

Gradle を初めて動かすと、Kotlin 2.x が `.kotlin/` を作ります。プロジェクトの `.gitignore` に無いと `[UnexpectedDirectory] .kotlin` が出ます。Gradle が作る `.gradle/` も同じです。`init` の警告（未追跡で ignore もされていないもの）と同じ扱いで、`.gitignore` に足すかどうかをユーザに確認してください。`.idea/` の中に git が追跡しているファイル（`.idea/codeStyles/` など）があれば、それも検査の対象なので、ステップ 3 で Role を書いて宣言します（中身を検査しないなら `ignore()` でよい）。`.gitignore` を自分で書き換えないこと。`Unexpected` 以外のエラー、たとえばコンパイルエラー、依存の解決失敗、`Could not start Gradle Test Executor` のようなものが出た場合だけが問題です（要約は `結果: 検査まで届いていません` になり、ログの末尾が添えられます）。

その場合はステップ 4 と同じ方針で、**katachi に関係ないエラー（Gradle・Java・依存解決）は修正を試みてください。** それでも解けない場合だけ、出力をそのままユーザに伝えて判断を仰ぎます。

`scaffold` が「buildscript { } を持つため自動で書き換えませんでした」と言った場合は、**出力に示された1行をルートの build ファイルに足してください。** 足すまで必ず失敗します。「pluginManagement { } の形を読み取れなかった」「include 文の終わりを読み取れなかった」と言った場合も同じで、出力に示されたものを settings ファイルに足してください。

`Unexpected` だけで失敗することを確認したら、作成・変更されたファイルを記録して `sh $CLI check 2-2`。

```sh
sh $CLI add changed --path "architecture-test/build.gradle.kts" --change "新規" --summary "検査用モジュールのビルド定義"
sh $CLI add changed --path "settings.gradle.kts" --change "変更" --summary "include(\":architecture-test\") を追加"
sh $CLI add changed --path "gradle/libs.versions.toml" --change "変更" --summary "katachi と JUnit の別名を追加"
```

`scaffold` の出力に並んだファイルを入れてください。出力は `file://` で始まる絶対 URI ですが、`--path` には今までどおり**プロジェクトルートからの相対パス**（上の例の形）を渡します。`pluginManagement` に追記された場合は、その旨も `--summary` に書きます。

## 3. architecture { } 定義を作成

**ここはあなたが判断するステップです。**

ユーザからプロンプトに渡された情報、1 で調査した内容をもとに `architecture { }` 定義を作成する。

**書くことと整えることを同時にしない。** 3-2 でまず動く定義を書いて検査結果を保存し、3-3 でその結果を変えずに整えます（ファイル・パッケージの分割もここ）。

### 3-1. ドキュメントを話題ごとに取得する

```sh
sh $CLI docs --topic layout
sh $CLI docs --topic role
sh $CLI docs --topic basic-api
sh $CLI docs --topic kt-file-content     # ktFileContent { } を使うときだけ
```

ガイドを話題ごとに切り出して取得し、ファイルのパスを出します。そのファイルを読んでください。**ガイド全体（`sh $CLI docs`）を読まないこと。** 定義を書くのに要るのは上の 4 つだけです。`ktFileContent { }` の書き方は `kt-file-content`（`sh $CLI docs --topic kt-file-content`）と、`basic-api` の 6 番のセクション（取得される英語版では "6. Defining the declarations a file may contain with `ktFileContent { }`"）にあります。話題の一覧は `sh $CLI docs --topics` で出ます。どれもすでに取得済みなら取り直しません。

**取得されるのは英語版です。** ドキュメントサイトのデフォルトロケールが英語で、この形式はデフォルトロケールのぶんしか生成されないためです。DSL の API 名は言語に依らないので、定義を書くうえでは差し支えない。

読み終えたら `sh $CLI check 3-1`。

#### API に迷ったら — API リファレンスの llms.txt を読む

**API の書き方・シグネチャ・引数に迷ったら、推測で書かずに API リファレンスを読んでください。** 3-2・3-3・6-D のどこでも同じです。ガイドに無い API を、似た名前から推測して書かないこと。冒頭の分担表のとおり、API リファレンスから該当する API を探す作業は subagent に任せ、本体はシグネチャと例だけを受け取ります。

| 読むもの | URL | コマンド |
|---|---|---|
| 索引（主要 API の要約とモジュールへのリンク） | https://tbsten.github.io/katachi/api-docs/llms.txt | `sh $CLI docs --api` |
| `:katachi` の一覧 / 全文（シグネチャと KDoc） | https://tbsten.github.io/katachi/api-docs/katachi/llms.txt / https://tbsten.github.io/katachi/api-docs/katachi/llms-full.txt | `sh $CLI docs --api katachi`（全文） |
| `:katachi-konsist` の一覧 / 全文 | https://tbsten.github.io/katachi/api-docs/katachi-konsist/llms.txt / https://tbsten.github.io/katachi/api-docs/katachi-konsist/llms-full.txt | `sh $CLI docs --api katachi-konsist`（全文） |

1つの API だけ見たいときは、索引や一覧に載っている各ページの Markdown 版を開きます。HTML の URL に `.md` を足したものです（例: `RoleScope.layout` は https://tbsten.github.io/katachi/api-docs/katachi/me.tbsten.katachi.dsl/-role-scope/layout.html.md ）。コマンドで取ったものは作業用ディレクトリの `tmp/cache/` に残るので、索引を毎回全部読み直す必要はありません。

### 3-2. 定義を書く

**まず動く定義を作ることが目的です。** 1ファイルに書いてかまいません。ファイルの分割や重複の整理は 3-3 でします。API に迷ったら 3-1 の「API に迷ったら」の llms.txt を読んでください。

**よく使う import はこれです。** ドキュメントの断片の例には import が書かれていないので、ここに置いておきます。

```kt
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.DeclarationContainerScope   // 定義を関数に分割するとき
import me.tbsten.katachi.dsl.gradle.*                    // mainSourceSet / testSourceSet / kotlin / gradle() / `/` 演算子
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.kotlin.ktsFile
import me.tbsten.katachi.konsist.declarations.ktFileContent   // ktFileContent { } を書くとき（実験的な API）
```

`me.tbsten.katachi.dsl.gradle` は**スター import にしてください。** `/` は `div` 演算子なので、個別に import すると連結が書けません。

**`capture()`・`fileConstraint`・`FileConstraintFailure`・`ktFileContent { }` は実験的な API です。** 1 つでも使うファイルには、先頭に `@file:OptIn(ExperimentalKatachiApi::class)` を書き、import に `import me.tbsten.katachi.ExperimentalKatachiApi` を足します。書かないと、使った箇所ごとにコンパイルエラーになります。

**`val projectArchitecture` の名前とパッケージは変えないこと。** 検査用モジュールの `build.gradle.kts` の `katachi { architecture = "..." }` がこの完全修飾名を指しています。定義を関数やファイルに分割するのは自由です（3-3）。

`architecture { }` の先頭には `title = "..."` と `description = "..."` を書けます。ステップ 4 で生成するドキュメントのルートページの見出しと本文になります（`build.gradle.kts` からは設定できません）。中身はステップ 1 のレポートの `overview` / `architecture` から取ってください。

#### 1 つの Role = 1 種類のファイル

**Role（`"Name".role { }`）は1種類のファイルを表します。種類の違うファイルを束ねるのは group（`"name".group { }`）です。** 1つの Role に種類の違うファイルを詰めると、その Role の `summary` も `ktFileContent { }` も全部のファイルには当てはまらなくなり、生成ドキュメントにも「何でも入る箱」が載ります。

同じ Role に入れてよいかは、次の3つで判定します。1つでも答えが「いいえ」のファイルがあれば、Role を分けてください。

- その Role の `summary` 1文で、どのファイルも説明できるか
- どのファイルも同じ命名・置き方の規則に従っているか（例外を列挙して足していないか）
- その Role に書く `ktFileContent { }` の制約が、どのファイルにも当てはまるか

逆に **1ファイル1 Role でもありません。** `*UseCase.kt` が 18 本あれば、Role は `UseCase` の1つです。違反の `How to fix:` が出す雛形は Role 名がファイル名（`"GetUserUseCase"` など）なので、そのまま貼らず種類の名前に直してください。

ルート直下のファイルは `layout { }` の直下にそのまま書けます（`"README.md".file()`）。1つの Role にまとめて書く必要はありません。

**Gradle のファイル（wrapper・settings・各モジュールのビルドスクリプト・`gradle.properties`・version catalog）は、Role を自分で書かずに `gradle()` を1行書いてください。** 種類ごとの Role に分けた `Gradle` group を katachi が宣言します（`me.tbsten.katachi.dsl.gradle.*` のスター import に入っています）。対象外の `buildSrc` と、`includeBuild` したビルドだけは自分で Role を書きます。

ビルドファイルの中身を検査したいとき（レポートで `constraintOnly` の印が付いたもの）も、Role は書きません。`gradle { buildScript { fileConstraint("...") { subject -> ... } } }` のように、`gradle()` のブロックの hook に制約を書きます（settings なら `settingsScript { }`、version catalog なら `versionCatalog { }`）。`fileConstraint` と `FileConstraintFailure` は実験的な API なので、上の「実験的な API」の段落のとおり opt-in（`@file:OptIn(ExperimentalKatachiApi::class)` と `import me.tbsten.katachi.ExperimentalKatachiApi`）が要ります。`buildScript { }` の制約は、ルートを含む全モジュールの `build.gradle(.kts)` を `subject.files` に受け取ります。1 つのモジュールだけを見るなら、制約の中で `subject.files` をパスで絞ります（例: `subject.files.filter { it == "app/build.gradle.kts" }`）。`gradle { }` の中に katachi の Role と同じ名前の Role（`"BuildScript".role { }` など）を書くと、重複の例外になります。`gradle { }` の外の Role でビルドファイルを宣言すると、katachi の `BuildScript` と同じファイルを取り合って `[AmbiguousLayout]` になります。

<details>
<summary>よくある誤りと直し方</summary>

導入の実地テストでは、13 プロジェクトで次の形が見つかりました。

| 誤り（1つの Role に詰めたもの） | 直し方 |
|---|---|
| `Gradle` に `settings.gradle.kts`・`gradle.properties`・`gradlew`・version catalog・wrapper、さらに `.gitignore` や `README.md` まで | Gradle のファイルは `gradle()` の1行に置き換え、`.gitignore` や `README.md` はそれぞれ別の Role にする |
| `ProjectMeta` / `RootDoc` に `README`・`LICENSE`・`CONTRIBUTING`・`CLAUDE.md` | `ProjectReadme` / `License` / … と1種類ずつ Role にし、group で束ねる。Role 名を `Readme` にすると、ドキュメント生成で索引の `README.md` と大文字小文字だけ違う名前になり、`katachiDocs` が失敗する |
| `AgentAndCiConfig` に `.claude/`・`.github/`・`.run/` | ツールごとに Role にする（中身を検査しないなら、それぞれ `ignore()` でよい） |
| `AndroidApp` にモジュールの中身まるごと（Manifest・Activity・`res/`・`build.gradle.kts`） | モジュールは group にし、中は種類ごとの Role にする |
| 本体と、そのテストを同じ Role に | テストは別の Role にする |

```kt
// ✗ 種類の違うファイルを1つの Role に詰めている
"ProjectMeta".role {
    layout {
        "README.md".file()
        "LICENSE".file()
        ".gitignore".file()
    }
}

// ○ 種類ごとに Role を分け、group で束ねる
"meta".group {
    "ProjectReadme".role { layout { "README.md".file() } }
    "License".role { layout { "LICENSE".file() } }
    "GitIgnore".role { layout { ".gitignore".file() } }
}
```

</details>

#### 配置だけで終わらせない

**ステップ1で読んだドキュメントの規約を、中身の制約（`ktFileContent { }`）の候補として棚卸ししてください。** 配置だけの定義は、ファイルが正しい場所にあることしか言いません。`ktFileContent { }` は実験的な API なので、書くファイルに opt-in（`@file:OptIn(ExperimentalKatachiApi::class)`）が要ります。書き方は `sh $CLI docs --topic kt-file-content` で取得します。

**受け皿の Role を作らないこと。** `commonMain/**/*.kt` のように「その場所なら何でもよい」という layout は、検査しているようで何も検査していません。実際に導入後の検証で、**同じ責務のテストが別の場所に作られてそのまま受理された**例があります。受け皿になってしまう場合は、最低でもどちらかを付けてください。

- ファイル名のパターン（`"*ViewModel".ktFile()` のように）
- 中身の制約を1つ（「この Role のファイルは何を宣言するか」。`ktFileContent { }` で書く）

ファイルの import を見る制約は、`ktFileContent { }` の `import` に書きます。書いた `import` だけが許可されるので、「これを import しない」は、その import を並べないことで表せます。ドキュメントの規約の多くはこの形です。

```kt
"ViewModel".role {
    layout { /* ... */ }

    ktFileContent {
        `package`("com.example.viewModel")

        // android.** を並べない。import すると違反になる
        `import`("kotlin.**")
        `import`("kotlinx.coroutines.**")
        `import`("androidx.lifecycle.**")
        `import`("com.example.**")

        required(`class`, fileName) {
            allow(anythingElse)
        }
    }
}
```

制約の名前は、1-3 の `constraintNames` に従います。`numbered` なら、その制約の元になった確認事項の id（`q3` や `cq2` など）を名前の先頭に付けます（`"q3: android パッケージを import しないこと".ktFileContent { }`）。`plain` なら上の例のままです。

#### 既存の静的解析との分担

プロジェクトに detekt・Android Lint・ktlint・独自の compiler plugin があっても、**重複を理由に制約を省かないでください。** 導入後の検証で、エージェントが重複を避けることを優先しすぎて制約が足りなくなり、**実際の規約違反を捕まえたのは既存の checker だけだった**という例があります。

判断はこうします。

- **Role に結びつく規約は katachi に書く。** 「ViewModel は Repository を直接呼ばない」は ViewModel という Role の定義そのものです。他のツールが似た検査をしていても、katachi 側にあることで「この Role とは何か」が1箇所に揃います
- **省いてよいのは、既存のツールがコンパイルエラーとして失敗させていて、かつファイル単位で抑制できないものだけです。** 抑制できるなら katachi 側にも書く価値があります
- 迷ったら書く。制約は後から消せます

`<検査用モジュール>/src/test/kotlin/<パッケージ>/test/architecture/ProjectArchitecture.kt` の `architecture { }` を埋める。

#### 動かして、結果を保存する

書き終えたら一度動かし、**出力をリファクタリング前の基準として保存します。** 3-3 でこれと比べます。

```sh
sh $CLI test --save before-refactor
```

ここで求めるのは**コンパイルが通り、検査が最後まで走ること**です。`Katachi check failed: ...` で失敗するのはかまいません（違反と向き合うのはステップ 4）。コンパイルエラーや Gradle のエラーで検査まで届かないときは、直してから取り直してください。

作成・変更したファイルを `changedFiles` に `data merge` で記載し、`sh $CLI check 3-2`。定義を走査し、種類の違うファイルを抱えていそうな Role を列挙します（`lint`）。ここで出たものは 3-3 のチェックリストで扱います。

### 3-3. 定義をリファクタリングする

3-2 の定義を、**検査結果を変えずに**整えます。API に迷ったら 3-1 の「API に迷ったら」の llms.txt を読んでください。

**リファクタリングのチェックリスト** 上から順に見て、当てはまらないものは飛ばしてかまいません。

- [ ] **ファイル・パッケージを分けた。** 1-3 の `oneRolePerFile` が `yes` なら、1 Role 1 ファイル（`<group>/<Role>Role.kt`）、1 group 1 ファイル（`<group>/<Group>Group.kt`）、`ProjectArchitecture.kt` は group を呼ぶだけ。命名規則は下の「定義のファイルの置き方」。`no` なら、この項目は飛ばす
- [ ] **group 名・ディレクトリ名が `.gitignore` に当たっていない。** group 名に `build` / `out` など `.gitignore` に当たる名前を使わない（例: `buildLogic`）。分けたあと `git ls-files --others --ignored --exclude-standard <検査用モジュール>/src` を実行し、何も出力されないこと（定義のファイルが無視されていないこと）を確かめる。無視されたファイルは `gitTracked()` に載らず、定義の抜けに気づけない
- [ ] **分割に使う関数を `inline` にしていない。** katachi は違反の宣言位置をスタックトレースから読むので、`inline` にすると行番号がずれる
- [ ] **分けた定義ファイルが検査に通る。** 定義ファイル自身も検査の対象です。検査用モジュールを受け持つ Role の `layout { }` が、定義のファイルの最大の深さ（`<group>/<sub>/<Role>Role.kt` など。入れ子の group の深さ + 1）まで受け入れること
- [ ] **1 つの Role = 1 種類のファイル。** すべての Role を 3-2 の「1 つの Role = 1 種類のファイル」の3つの問いで見直した。`lint` の警告が 0 件か、残すものは理由を `sh $CLI warn 3 "..."` に書いた
- [ ] **Gradle まわりは `gradle()` の1行。** Gradle のファイルを手書きの Role で宣言していない（`buildSrc` と `includeBuild` したビルドは除く）。ビルドファイルへの制約（レポートで `constraintOnly` の印が付いたもの）は、`gradle { buildScript { } }` などの hook に書いた（1 つのモジュールだけを見る制約は、`subject.files` をパスで絞った）
- [ ] **受け皿の Role が残っていない。** `anyFile()` / `ignore()` の寄せ集めや、「その場所なら何でもよい」`layout` の Role に、ファイル名のパターンか中身の制約（`ktFileContent { }`）が付いている（3-2 の「配置だけで終わらせない」）
- [ ] **説明がそろっている。** Role と group に `title` / `summary` / `description`、Role に `example()`（実例のファイル名）。中身はステップ 1 のレポートの `roles` から取る。1-2 の指針で `documented = false` にすると決めた Role には、そう書いてある
- [ ] **重複を共通化した。** 同じ `layout` を複数の Role に書き写していない（関数に切り出す）。パッケージのディレクトリのような何度も出る値を `val` に切り出した
- [ ] **検査結果が前後で変わらない。** 下の「前後で比べる」が「変わっていません」で終わる

<details>
<summary>定義のファイルの置き方（命名規則）</summary>

**ユーザから指示が無い限り、次の命名規則に従ってください。** 定義の並びがアーキテクチャの並びと一致するので、どの Role がどこに書かれているかを探さずに済みます。

```
<パッケージ>/test/architecture/
├── ProjectArchitecture.kt            architecture { } の本体。group の関数を呼ぶだけ
├── <Role>Role.kt                     group に属さない Role の定義   例: ProjectReadmeRole.kt
├── <group>/<Group>Group.kt           group の定義               例: app/AppGroup.kt
├── <group>/<Role>Role.kt             その group が呼ぶ Role の定義  例: app/EntrypointRole.kt
└── <group>/<sub>/<Sub>Group.kt       入れ子の group の定義       例: domain/model/ModelGroup.kt
```

- ディレクトリ名は group 名を先頭小文字にしたもの（`domain`）。Role にはディレクトリを作らない。ファイル名は group が `<Group>Group.kt`（`AppGroup.kt`）、Role が `<Role>Role.kt`（`EntrypointRole.kt`）。関数名は先頭小文字（`fun DeclarationContainerScope.appGroup()`、`entrypoint()`）。package 宣言はディレクトリに合わせる
- group の中の group は、同じ形で1段深くする（`domain/model/ModelGroup.kt`、その中の Role は `domain/model/EntityRole.kt`）。1-3 の `nestedGroups` が `no` なら group は 1 段だけにし、入れ子の代わりに名前で分ける
- group に属さない Role は定義のルート直下の `<Role>Role.kt`（例: `ProjectReadmeRole.kt`）。Role は、それを呼ぶ group のディレクトリに置く
- 1ファイルに書くのは group か Role の**1つだけ**。group のファイルは、その下の Role の関数を呼ぶだけにする
- 分けた関数は `DeclarationContainerScope` の拡張関数にし、呼ぶ側で import する（拡張関数は完全修飾名では呼べない）

</details>

#### 前後で比べる

整え終えたら、3-2 と同じ形でもう一度動かし、比べます。

```sh
sh $CLI test --save after-refactor
sh $CLI compare-violations
```

`compare-violations` は2つのログから検査結果（要約の1行と、違反ごとの `[種類] パス`）を取り出して比べます。宣言位置の行番号は比べません。`[MissingDescription]` は説明を書き足せば減るのが正しいので、件数だけ出します。**違いが出たら、前のログを取り直さずに定義を直してください。** リファクタリングで振る舞いが変わっています。

**前のログが緑（違反 0 件）のときは、0 件と 0 件の比較になり、分割で定義が緩んだこと（受け皿が増えたなど）は検出できません。** その場合は、分割の後に既知の違反を1つ作って失敗することも確かめてください。どの Role にも当たらないファイルを一時的に置いて `sh $CLI test` を実行し、`[UnexpectedFile]` で失敗したら、そのファイルを消します。

作成・変更したファイルを `changedFiles` に記載し（分けたファイルはディレクトリでまとめて1件でよい）、`sh $CLI check 3-3`。`lint` がもう一度走ります。`lint` は失敗にはしない点検も出します（1-3 の方針に対する「平たい group」「分けていないファイル」、`documented = false` の候補）。出たものは上のチェックリストに照らして直すか、残す理由を `warn 3` に書きます。

## 4. 検証する

### 4-1. テストを実行する

```sh
sh $CLI test
```

要約を読みます。ログを grep しないこと。全文が要るときだけ、要約の `ログ:` の行にあるパスを読みます。要約には、結果の 1 行、`[MissingDescription]` の件数、前回の実行との差分（増えた違反・減った違反）が出ます。違反が 0 件なら `結果: 検査は通りました（違反 0 件）` です。

### 4-2. エラーとの向き合い方

- Gradle・Java 由来など katachi に関係ないエラーは修正を試みる。
- katachi 由来のアーキテクチャ違反のエラー: 無理に直そうとはしない。
  - 軽微なものは Agent 自身で修正
    - `[UnexpectedFile]` を、手近な既存の Role の `layout { }` に1行足して消さないこと。その Role の `summary` で説明できないファイルなら、新しい Role を作る（3-2 の「1 つの Role = 1 種類のファイル」）
  - 対処法が曖昧なものは後のステップでユーザに確認してもらうこととし、そのままにしておく
  - そのままにするエラーはチェックリストの `violations` に記載する（`violation` / `location` / `whyNotFixed` / `suggestion`）。
  - ユーザに判断してほしいことは**チェックリストの** `questions` に入れる（`sh $CLI add question ...`）。レポート側にも同名の配列があるが、そちらはステップ 1 で気づいた「コードベースの揺れ」を書く場所で、用途が違う。

残した違反が 10 件を超えても、`sh $CLI test -- -Dkatachi.maxViolations=all` で全件見られます。テストのコードに `maxViolations` を書く必要はありません。

**残す違反が多く、ユーザが「今ある違反は許容して、新しい違反だけで失敗させたい」と望む場合は、baseline を提案できます。** 実験的な機能（`@ExperimentalKatachiApi`）なので、使うかどうかは `questions` でユーザに確認してから入れてください。使う場合の手順は次のとおりです。

- `ProjectArchitecture.kt` の `architecture { }` に `baseline()` を書き、`val projectArchitecture` に `@OptIn(ExperimentalKatachiApi::class)` を付ける。baseline のファイルはデフォルトで `katachi-baseline.json`（プロジェクトルート）
- baseline のファイルも検査の対象なので、Role を1つ足して `layout { }` で宣言する（例: `"BaselineFile".role { layout { "katachi-baseline.json".file() } }`）。宣言しないと baseline のファイル自身が `[UnexpectedFile]` になり、これは baseline に入れられない
- `sh $CLI test -- -Dkatachi.baseline.update=true` で、今ある違反を baseline のファイルに書き出す。以降は baseline のファイルに無い違反だけでテストが失敗する。違反を直すと、その項目が `[StaleBaselineEntry]` で失敗するので、`sh $CLI test -- -Dkatachi.baseline.prune=true` で消す（CI（`CI=true`）では update も prune も拒否される）

許容した違反も `violations` に書いてください。baseline のファイルに入れても、違反が消えたわけではありません。

違反を残した場合は `sh $CLI warn 4 "..."` で何を残したかを1行書く。

### 4-3. ドキュメントを生成する

同じ定義から、Role ごとの説明ページ（Markdown）を生成できます。`scaffold` が入れた Gradle plugin の配線（`katachi { architecture = ... }`）を確かめる意味もあるので、ここで一度動かします。

```sh
./gradlew :architecture-test:katachiDocs
```

出力先は `architecture-test/build/katachi/docs/` です。索引の `README.md` と、Role ごとのページができていることを確かめてください。`build/` の下なのでコミットはされません。コミットしたい場合は 6-C で扱うので、ここでは出力先を変えないこと。

終えたら `sh $CLI check 4-1 4-2 4-3`。`次:` に `guide 5` が出ます。

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

**出力をそのままユーザに返してください。文面を自分で組み立てないこと。** 出力は `init` の `--lang` で選んだ言語で出ます。訳し直す必要はありません。

#### summary の前に — テンプレートの提案を記録する

**ここはあなたが判断する箇所です。** katachi は `layout { }` のファイルの宣言ごとに `.template { }` を持て、`:architecture-test:katachiTemplate` で**その宣言どおりの場所に**ファイルを生成できます。導入の「次にできること」として、どの Role にどんなテンプレートを当てるかを提案します。

材料はステップ 1 のレポートの `roles` です（`sh $CLI data get report roles`）。**向いているのは、同じ形のファイルが何本もある Role**です（UseCase、ViewModel、Screen、Repository など）。`count` が小さい Role や、1本ごとに形が違う Role には提案しないこと。

**`feature/*/...` のように、生成先のディレクトリがワイルドカードの Role も対象です**（ViewModel、Screen はたいていこの形です）。その `*` に `capture("feature")` で名前を付ければ、`--arg feature=home` で生成先を選べます（6-D の手順 2）。**生成先に残る `*` は、モジュールのディレクトリのものもパッケージのディレクトリのものもすべて名前が要ります。**2 つが同じ値になるなら、1 つの名前を両方に付けます（`capture("feature")` を 2 か所に書く）。名前を付けられないのは `**` だけで（部分一致は `capture()` で名前を付けられます）、生成先が `**` しか無い Role には提案しないこと。

Role ごとに次を決め、`add template` で記録します。

| 項目 | 中身 |
|---|---|
| `--role` | Role の名前（`architecture { }` に書いた名前） |
| `--files` | 作るファイル名。**複数回渡せる。** `${name}UseCase.kt` のように、パラメータを埋め込んだ形で書く（同じ種類のファイルの名前の形が複数あるときだけ。interface と実装のように種類が違うものは Role を分け、`add template` を Role ごとに1回ずつ実行する） |
| `--params` | `stringParameter()` などの `*Parameter()` で受けるパラメータ名と、生成先のワイルドカードに付ける名前（例: `feature`）。複数回渡せる。**生成先の `*` に付けた名前の数だけ並べる**（モジュールのディレクトリとパッケージのディレクトリに同じ名前を付けたなら 1 つ） |
| `--basedOn` | 雛形の元にする既存ファイル（`roles[].examples` の1本） |
| `--reason` | その Role に当てる理由（件数と、形が揃っていること） |

```sh
sh $CLI add template --role UseCase --files '${name}UseCase.kt' --params name --basedOn "domain/src/main/kotlin/com/example/app/domain/useCase/GetUserUseCase.kt" --reason "18 件がすべて interface で、形が揃っている"
sh $CLI add template --role UseCaseImplementation --files '${name}UseCaseImpl.kt' --params name --basedOn "data/src/main/kotlin/com/example/app/data/useCase/GetUserUseCaseImpl.kt" --reason "18 件がすべて UseCase の実装で、形が揃っている"
sh $CLI add template --role ViewModel --files '${name}ViewModel.kt' --params feature --params name --basedOn "feature/home/src/main/kotlin/com/example/app/feature/home/HomeViewModel.kt" --reason "12 件が feature/* の各モジュールに1本ずつあり、形が揃っている。feature/* の * とパッケージのディレクトリの * は同じ名前なので、1 つの名前 feature で生成先を選ぶ"
```

当てられる Role が無いと判断したら、何も記録しなくて構いません。記録したものは `summary` の「Next action」とレポートの「テンプレートの提案」に出ます。**`.template { }` をここで書かないこと。** 書くのはユーザが同意した後の 6-D です。

未完了の項目が残っていれば失敗の形式が、すべて終わっていれば成功の形式が出ます。違反の件数、確認したいことの件数と回答済みの件数、変更したファイル数は JSON から拾われます。

`summary` の出力に続けて文章にするのは、**未回答の確認事項だけ**です（`summary` が `- <id> <問い> → 推奨: <推奨>` の形で列挙します）。回答済みのものは件数のままにします。**ここは人が読んで判断する箇所なので、例外的にあなたが文章にします。** それ以外は `summary` の出力をそのまま使います。回答は、id が `cq` で始まるものはレポート、`q` で始まるものはチェックリストの URL を示してブラウザで書いてもらい、`sh $CLI answers import <json>` で取り込みます（チャットで来た答えは `answers set`。書き込み先は id で決まります。1-2 と同じ）。

> **ここでセッションを終えてよい。** 次のセッションは、この手順書を再取得したあと `sh <KATACHI_CLI> summary` を実行し、出力の「再開」の欄にある次のセクションを `guide` で読む。前のセッションの結果はチェックリストとレポートにある。会話の記憶に頼らない。

### 6-0. レビューを受けて定義を直す

ユーザが `summary` の内容と定義を見て、指摘を返してきたときのセクションです。チェックリストの項目はありません。

やることは 1 つです。ユーザの指摘を、確認事項（`sh $CLI answers`）と残した違反（チェックリストの `violations`）に照らして定義を直し、`sh $CLI test` で件数を確かめます。直した定義で `[UnexpectedFile]` が増えていないか、要約の差分で見てください。

ユーザの回答が確認事項の答えになっているなら、`sh $CLI answers set <id> "<回答>"` で記録してから直します（`--target` は要りません。書き込み先は id で決まります）。指摘が新しい確認事項を生むなら `add question` に足します。

> **ここでセッションを終えてよい。** 次のセッションは、この手順書を再取得したあと `sh <KATACHI_CLI> summary` を実行し、出力の「再開」の欄にある次のセクションを `guide` で読む。前のセッションの結果はチェックリストとレポートにある。会話の記憶に頼らない。

## 6-A. (6 の返答後) 導入できたことを確かめる

題材にする実装タスクを確認してタスクを実行する。

**新しいファイルが増えるタスクを選んでください。** katachi が見るのはファイルの配置と Role なので、既存ファイルを直すだけのタスク（バグ修正など）では何も起きません。実地検証では、7 件中 4 件が既存ファイルの修正だけで、katachi を試す機会がありませんでした。新しい画面・新しいモジュール・新しいテストが増えるものが向いています。

- ユーザから指示されたタスクがある場合は、そのタスクの妥当性を判断する。妥当と考えにくいタスクの場合は、本当に実行するかユーザに判断を仰ぐ。
- ユーザから指示されたタスクがない場合は、**まずこのリポジトリの issue から選ぶ。**自分で考えた題材は、どうしても都合のよい形になりがちです。実際の issue なら、このプロジェクトで本当に起きる変更で katachi を試せます。

### issue から題材を選ぶ

1. `git remote -v` で GitHub のリポジトリかを確かめ、`gh issue list --state open --limit 50` で開いている issue を見る。`gh` が無い・認証されていない・GitHub ではない場合は、issue の一覧の URL をユーザに示して、候補を選んでもらう。
2. 次の条件に合うものを **1〜5件**選ぶ。
   - **新しいファイルが増える**（新しい画面・モジュール・UseCase・テストなど）
   - 小さく、仕様が issue の中で閉じている（`good first issue` などのラベルは手がかりになる）
   - 何が増えるかが、`architecture { }` のどの Role に当たるかで言える
3. 候補ごとに「issue の番号とタイトル」「増えそうなファイル」「当たる Role」を添えてユーザに示し、どれをやるか選んでもらう。
4. 向いた issue が無いとき、または issue を見られないときだけ、系統の違うタスクを 1〜5 個考えてユーザに伝えたのち、実行する。

**issue にコメントしたり、閉じたり、PR を作ったりしないこと。**ユーザに頼まれない限り、手元で実装して検査するところまでです。

### 実行と確認

着手する前に、**そのタスクで増えるファイルがどの Role に当たるかを予想して書き留めておく。**タスク実行後、`sh $CLI test` を実行し、予想と検査結果を比べて `architecture { }` の修正が必要かを判断する。予想どおりに受け入れられた、予想外に `Unexpected` になった、受け皿の Role が何も指摘せずに受け入れた、のどれかが分かります。

過去の PR を題材にするときは、`gh pr view <番号> --json mergeCommit,commits` でマージ方法を確かめ、before は **PR の最初のコミットの親**（`git rev-parse <最初のコミット>^`）、after はマージコミットにします。rebase merge では `mergeCommit^1` が「最後の 1 コミットの手前」になり、PR 全体を比べられません。

並行で検証するときは、出力先と一時ファイルを subagent ごとに `<KATACHI_WORKDIR>/tmp/verify/<名前>/` に分け、作業用ディレクトリへの書き込み（`add` / `data merge`）は親だけが行います。worktree の中からは `.local/` の作業用ディレクトリに届かないので、絶対パスを渡してください。

終わったら `sh $CLI check 6-1`。

> **ここでセッションを終えてよい。** 次のセッションは、この手順書を再取得したあと `sh <KATACHI_CLI> summary` を実行し、出力の「再開」の欄にある次のセクションを `guide` で読む。前のセッションの結果はチェックリストとレポートにある。会話の記憶に頼らない。

## 6-B. (6 の返答後) CI への導入を依頼された場合

利用している CI の仕組みをもとに以下のように対応する。`--module-dir` を変えているなら、以下の `:architecture-test` はそのパス（`KATACHI_MODULE_PATH`）に読み替える。

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
          java-version: '17' # 2-1 で決まった JVM toolchain の N に合わせる（17 以上）

      - uses: gradle/actions/setup-gradle@v6

      - run: ./gradlew :architecture-test:test
```

新しく作ったワークフローのファイル（`.github/workflows/*.yml`）が `architecture { }` の定義に無ければ Role を足し（例: `".github/workflows" / "*.yml".file()`）、`sh $CLI test` を回して通ることを確かめる。

既存の CI が emulator や複数の OS を回す matrix のジョブ 1 本だけのときは、その各要素で毎回 `:architecture-test:test` が走らないように、**matrix の外に別のジョブとして足します。** 新しい yaml ファイルは作らず、同じ workflow ファイルの `jobs:` に上の `architecture` ジョブを足してください。

6-C で生成したドキュメントをコミットしている場合は、同じジョブに次の1行を足す。`--arg mode=check` は何も書かずにディスク上のページと比べ、定義と食い違っていれば失敗する（定義を変えたのにドキュメントを生成し直し忘れた、を捕まえる）。

```yaml
      - run: ./gradlew :architecture-test:katachiDocs --arg mode=check
```

### GitHub Actions 以外を使っている場合

Pull request 作成時・Merge request 作成時・pre-push hook など、ストレスにならない適切なタイミングで `./gradlew :architecture-test:test` が実行・レポートされるようにワークフローを修正する。

終わったら `sh $CLI check 6-2`。

> **ここでセッションを終えてよい。** 次のセッションは、この手順書を再取得したあと `sh <KATACHI_CLI> summary` を実行し、出力の「再開」の欄にある次のセクションを `guide` で読む。前のセッションの結果はチェックリストとレポートにある。会話の記憶に頼らない。

## 6-C. (6 の返答後) ドキュメント生成をセットアップする

ユーザが望んだ場合だけ行う。

**始める前に、ドキュメント生成のページを読む。** `sh $CLI docs --topic document-generation` で取得します（日本語版は https://tbsten.github.io/katachi/ja/guides/document-generation/ ）。生成されるページの中身、出力先を変えたときに要る Role の宣言、`--arg mode=check` の意味はここに書いてあります。ガイド全体を取らないこと。

1. `architecture { }` の先頭に `title = "..."` と `description = "..."` を書く（3-2 で書いていれば飛ばす）。ルートページの見出しと本文になる。
2. `./gradlew :architecture-test:katachiDocs` で生成し、`architecture-test/build/katachi/docs/` を確かめる。
3. **生成物をコミットするかをユーザに提案する。** デフォルトの出力先は `build/` の下なのでコミットされない。コミットするなら次の3つを揃える。1つでも欠けると、テストか CI が失敗するか、ドキュメントが気づかないうちに古くなる。
   - 検査用モジュールの `build.gradle.kts` の `katachi { }` に出力先を書く（`architecture = ...` の行はそのまま）

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

   - その場所を Role として `layout { }` に宣言する。宣言しないと生成したページが `gitTracked()` に拾われ、`:architecture-test:test` が `[UnexpectedFile]` / `[UnexpectedDirectory]` で失敗する。索引の `README.md` と、Role ・group ごとの `*.md` の両方を覆う

     ```kt
     "ArchitectureDocs".role {
         summary = "katachiDocs が生成するページ"
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

   **出力先のディレクトリ名に `build` / `out` など `.gitignore` に当たる名前を使わない**（3-3 と同じ。コミットしたつもりのページが無視される）。

   **出力先は katachi のものになります。** そこにある、今回の生成で作られなかった `*.md` は消されます。手書きの Markdown があるディレクトリを指さないこと。
4. 生成し直し、`sh $CLI test` が通ることを確かめる。

作成・変更したファイルを `add changed` で記録し、`sh $CLI check 6-3`。

> **ここでセッションを終えてよい。** 次のセッションは、この手順書を再取得したあと `sh <KATACHI_CLI> summary` を実行し、出力の「再開」の欄にある次のセクションを `guide` で読む。前のセッションの結果はチェックリストとレポートにある。会話の記憶に頼らない。

## 6-D. (6 の返答後) テンプレートからのコード生成をセットアップする

ステップ 6 で記録した提案に、ユーザが同意した Role だけ行う。

**始める前に、テンプレートからコード生成のページを読む。** `sh $CLI docs --topic template` で取得します（日本語版は https://tbsten.github.io/katachi/ja/guides/generate-code-from-template/ ）。`.template { }` で書ける語（`stringParameter()` などの `*Parameter()`）、生成先のパスを書かない理由、既存ファイルがあるときの扱いはここに書いてあります。シグネチャに迷ったら、推測で書かずに 3-1 の「API に迷ったら」の llms.txt を読んでください（`.template { }` の入口は https://tbsten.github.io/katachi/api-docs/katachi/me.tbsten.katachi.dsl/template.html.md ）。

1. 提案を確かめる（`sh $CLI data get report templates`）。
2. その Role の `layout { }` の、ファイルの宣言に `.template { }` を付ける。**`.template` は拡張関数なので、書くファイルに `import me.tbsten.katachi.dsl.template` を足す**（`captureValue()` などはブロックの中で使えるので import は要らない）。**`.template { }`・`capture()`・`captureValue()` は実験的な API なので、そのファイルに `@file:OptIn(ExperimentalKatachiApi::class)` と `import me.tbsten.katachi.ExperimentalKatachiApi` も足す。** 足さないと `This katachi API is experimental. …` でコンパイルが失敗する（メッセージに付け方が出る）。**中身は `basedOn` のファイルを元にする。** 生成先のパスは `layout { }` の宣言そのものが決めるので、テンプレート側には書かない

   ```kt
   @file:OptIn(ExperimentalKatachiApi::class)

   import me.tbsten.katachi.ExperimentalKatachiApi
   import me.tbsten.katachi.dsl.template

   "UseCase".role {
       layout {
           "useCase" / "${capture("name")}UseCase".ktFile()
               .template {
                   val name = captureValue("name")
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

   **生成先のディレクトリにワイルドカード（`*`）がある Role は、その `*` に `capture()` で名前を付ける。** 検査の結果は変わらない。名前の無い `*` や `**` が残ったまま `.template { }` を付けると、layout を展開する時点（`sh $CLI test` の `assert()`）でエラーになる。値はテンプレートの中で `captureValue("名前")` で読む（`stringParameter()` で宣言し直さないこと。名前がぶつかって失敗する）

   ```kt
   @file:OptIn(ExperimentalKatachiApi::class)

   import me.tbsten.katachi.ExperimentalKatachiApi
   import me.tbsten.katachi.dsl.template

   "ViewModel".role {
       layout {
           // 元は "feature" / "*" / mainSourceSet / kotlin / "com/example/app/feature" / "*" / "*ViewModel".ktFile()
           "feature" / capture("feature") / mainSourceSet / kotlin /
               "com/example/app/feature" / capture("feature") /
               "${capture("name")}ViewModel".ktFile()
                   .template {
                       val feature = captureValue("feature")   // --arg feature=home なら "home"
                       val name = captureValue("name")         // --arg name=Home なら "Home"

                       """
                           package com.example.app.feature.$feature

                           class ${name}ViewModel
                       """.trimIndent()
                   }
       }
   }
   ```

   ディレクトリの `*` は、`"feature" / "*" / ...` の `"*"` を `capture("feature")` に書き換える。`*` が複数あれば、`"feature" / capture("feature") / capture("layer") / ...` のようにそれぞれに `capture()` を書く。

   **生成先のディレクトリ部分に残る `*` は、すべて名前を付ける。** 上の例のようにモジュールのディレクトリの `*` とパッケージのディレクトリの `*` が同じ値になるなら、1 つの名前（`capture("feature")`）を両方に書く。katachi は 2 か所が同じ値かを検査し、食い違うファイルを `[CaptureMismatch]` にする。値が違ってよいなら、それぞれに別の名前を付ける。1つでも名前の無い `*` が残ると、`.template { }` を付けたファイルの layout を展開する時点でエラーになる（`assert()` で分かり、`katachiTemplate` の実行を待たない）。

   **パッケージのディレクトリ名がモジュールのディレクトリ名と違う（モジュール `appConfig` とディレクトリ `appconfig` など）なら、そのディレクトリの `*` にもモジュールとは別の名前を付け、その `captureValue()` を package に使う。** モジュールの名前（`captureValue("feature")`）を package に入れると、ディレクトリと食い違う package が書かれ、検査もコンパイルも通るので気づけない。

   使えるのは `stringParameter()` / `booleanParameter()` / `intParameter()`（`default = ...` も可）、`enumParameter()`（`entries` かデフォルト値を渡す）、それに生成先のワイルドカードに名前を付ける `capture("名前")`（layout のディレクトリ・ファイル名のどこにでも埋め込める）と、その値を読む `captureValue("名前")`（template の中）だけです。これ以外の語を推測で足さないこと。
3. テンプレート独自のパラメータ（`--arg name=...`）は、モジュール側の設定なしにそのまま渡せる。受け付けるのは、名指しした Role の `.template { }` が宣言した名前と、`layout { }` のワイルドカードに付けた名前だけで、打ち間違いはこれまでどおり `Unknown processor argument(s): ...` で失敗する。
4. 1本生成し、**直後に検査が通ること**を確かめる。`--arg template=` には、group があれば `group名.Role 名`、無ければ Role 名を書く。

   ```sh
   ./gradlew :architecture-test:katachiTemplate --arg template=UseCase --arg name=Sample
   sh $CLI test
   ```

   ワイルドカードに名前を付けた Role は、その値も渡す。**ディレクトリの値は、存在しない名前を渡してもそのまま生成先のパスになる**（打ち間違えると、無いディレクトリにファイルを作る）。実在するディレクトリ名を渡すこと。

   ```sh
   ./gradlew :architecture-test:katachiTemplate --arg template=ViewModel --arg feature=home --arg name=Sample
   sh $CLI test
   ```

   `test` が失敗したら、`.template { }` を付けたファイルの宣言のパスが正しいかを確かめる。
5. **生成したファイルは確認用なので消す**（ユーザが残すと言った場合を除く）。消したあと、もう一度 `sh $CLI test` が通ることを確かめる。

作成・変更したファイルを `add changed` で記録し、`sh $CLI check 6-4`。

**ステップ 6-A〜6-D は任意です。** チェックリストでも任意として扱われ、進捗の分母には入りません。やらなくても `verify` は通ります。

> **ここでセッションを終えてよい。** 次のセッションは、この手順書を再取得したあと `sh <KATACHI_CLI> summary` を実行し、出力の「再開」の欄にある次のセクションを `guide` で読む。前のセッションの結果はチェックリストとレポートにある。会話の記憶に頼らない。
