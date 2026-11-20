# 週替わりの nightly 探索

[exploratory.md](exploratory.md)（prerelease の手順 13）と同じ探索的検証を、リリースを待たずに**週に 1 回・1 コンポーネントだけ**クラウドの定期実行で回す（KATACHI-402）。
劣化を早く拾うのが目的で、prerelease の 13 の代わりではない（リリース直前には 13 を回す）。IDE plugin は Mac が要るので回さない
（IDE は ide-plugin-nightly.yml の property-based tests と実 IDE の smoke が見ている）。

- 回す単位: `exploratory.py week` が返すチャーター 1 つ（`ROTATION` の 6 つを ISO 週の番号で順に。6 週で一巡）
- 頻度: 週 1 回、土曜の夜（JST 03:17 ごろ）。クラウドの定期実行（scheduled task）で、毎回まっさらなセッションを立てる
- 上限: 1 回 40 分。越えそうなら途中で切り上げ、触れなかったところを書く
- GitHub Actions では回さない（API キーとエージェント実行の管理が要るため。巡回と同じクラウドの定期実行に寄せる）

## してよいこと・してはいけないこと

- してよい: リポジトリの読み取り・`.local/` と作業用ディレクトリへの書き込み・`mavenLocal` への publish・Notion の読み書き（定期実行ログ・チケットの起票）
- してはいけない: **リポジトリへのコミット・push・PR・ブランチ作成・既存ブランチへの force push**（修正 PR は作らない。見つけたものはチケットに書く）、`katachi/`・`katachi-script/`・`docs/` などの本物のコードの書き換え、本番のサイトへの書き込み、Mac・利用者の手元に触ること
- 同じ週に 2 回起動されても、結果を二重に書かない（定期実行ログに同じ週の行があれば、その行に追記して終える）

## 手順

1. **チャーターを決める**: `python3 .claude/skills/prerelease/scripts/exploratory.py week`（`<名前>\t<パス>`）。定期実行の本文でチャーターの名前を指定されたら、そちらを使う
2. **直近の結果を読む**: Notion の「定期実行ログ」DB（data source `40f187be-ecfc-80fd-9055-000b402b833f`）の直近 6 件ほどを読み、前回までの探索の発見と重ならないようにする（起票済みの発見は新しく起票しない）
3. **環境を整える**（クラウド。9-1 をクラウドで回したときの要領）:
   - `git fetch --unshallow --tags origin`（浅い clone だと版の判定や差分が黙って欠ける）
   - JDK 17: `apt-get update && apt-get install -y openjdk-17-jdk-headless`、`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`。Gradle は `LC_ALL=C.UTF-8` で走らせる
   - Maven Central が 429 を返すときは、`~/.gradle/init.d/mirror.gradle` で `repo.maven.apache.org` を `https://maven-central.storage-download.googleapis.com/maven2/` に差し替える（pluginManagement・dependencyResolutionManagement・buildscript・repositories の 4 か所）。`beforeSettings` で pluginManagement にミラーを足すと既定のプラグインポータルが外れるので、`gradlePluginPortal()` をミラーの後ろに足す（足し忘れると settings の `plugins { }` が解決できずに落ちる）。コミットしない
   - クラウドで届かないもの（Android SDK・JetBrains）と root で走らせると落ちる spec は [decisions/environment.md](../decisions/environment.md)。環境要因は発見にしない
4. **配布物を組む**: `sh .claude/skills/prerelease/scripts/build-katachi-cli.sh`（`bin/katachi` と mavenLocal への publish。`kotlin` が要る）。`katachi --version` がリポジトリの版かを確かめる
   - クラウドに `kotlin` が無く入れられないとき（9-1 では kotlinc を GitHub から落として `kotlin` のラッパーを作った）は、ブランチから直接動かしてよい。init script で `:katachi-script` の
     `sourceSets.main.runtimeClasspath` を出し（`printCp` タスク）、`java -cp <classpath> me.tbsten.katachi.script.MainKt <サブコマンド>` で呼ぶ（`-Porg.gradle.java.installations.paths=<JDK 17>` を付ける）。
     これは配布物ではないので、結果に「入口: classpath から直接」と書く。この入口だと `--version` が `0.0.0-unknown` になり、`scaffold` は `--katachi <版>` が要る（どちらも環境の都合で、発見にしない）
5. **チャーターを開く**: [exploratory/<名前>.md](exploratory/) の観点・入口・やってみることに従い、**利用者として**触る。**例にない操作を最低 3 つ試す**。作業は `.local/exploratory-nightly/<日付>-<名前>/` の下で、プロジェクトの写しを使う
6. **発見を書く**: [exploratory.md](exploratory.md) の「出力の形」で `.local/exploratory-nightly/<日付>-<名前>/exploratory-<名前>.md` に書く（再現手順が書けないものは発見に数えない）。
   書き終えたら `python3 .claude/skills/prerelease/scripts/decisions.py match <そのファイル> --drop` で過去の判断と同じものを外す
7. **Notion に書く**:
   - 定期実行ログに 1 行（ハイライト = 「探索 <名前>: p7 以上 n 件・p6 以下 m 件」。日付・重要度・関連チケット）。本文に、チャーター・かかった時間・発見の一覧（見出しと priority だけ）・捨てた件数・触れなかったところ・次回の観点
   - **p7 以上の発見は `fix:` チケットを起票する**（名前は `fix(<対象>): <症状>`、再現手順を本文に、重要度は p9 以上なら 80・p7〜8 なら 60、親は開いている適切な版の箱。既に同じ症状のチケットがあれば起票せず、そこに追記する）。**修正 PR は作らない**
   - p6 以下は定期実行ログの本文だけ（チケットにしない）
8. **一巡したら**（6 回目）: 定期実行ログに「探索 一巡の集計」を 1 行書く（発見の件数・p7 以上の件数・起票したチケット・かかった時間の合計・続けるかどうかの所感）。続けるか・頻度を変えるかは利用者が決める

## 効果の測り方

prerelease の 13 と同じ指標で見る（[exploratory.md](exploratory.md) の「効果の測り方」）。一巡（6 週）の集計で、p7 以上がほぼ 0 件なら、チャーターを見直すか頻度を月 1 に下げる。
毎回出るなら、増やす（コンポーネントを週 2 つにする）より先に、見つけたものを検査へ昇格させる（9-1 の fixture・spec に足す）。

## 定期実行の本文（scheduled task に貼るもの）

````md
katachi（TBSten/katachi）の週替わりの探索的検証を 1 回行う。

1. リポジトリの main を最新にし、`.claude/skills/prerelease/references/exploratory-nightly.md` を読む。
2. その「手順」の 1〜8 のとおりに、今週のチャーターを 1 つ、40 分以内で探索する。
3. してはいけないこと（コミット・push・PR・ブランチ・本物のコードの書き換え）は、その「してよいこと・してはいけないこと」のとおり。
4. 終わったら、定期実行ログへの 1 行と、p7 以上の発見の `fix:` チケットで報告を終える。ユーザー向けの文でチケット番号を出すときは Notion へのリンクにする。
````
