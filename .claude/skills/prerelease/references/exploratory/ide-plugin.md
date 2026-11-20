# チャーター: IDE plugin（IDEA・Android Studio）

- 利用者像: IDE で katachi を使う人（IntelliJ IDEA・Android Studio、日本語の IDE も）
- 場所: Mac のみ
- 目安: 人 15 分（AI は環境と観点カードを用意）

実 IDE のウィンドウを使う。AI が画面を操作して回すのは、13 を 1〜2 回回して効果を見てから考える（[exploratory.md](../exploratory.md) の「IDE plugin」）。

## 観点

- 初めて: プラグインを入れて、katachi のプロジェクトを開いて、最初に何が見えるか
- 既定のまま: Android Studio の既定の設定（Gradle sync でタスク一覧を作らない）のまま使う（363 型）
- 環境違い: IDEA と Android Studio・IDE の言語が日本語・Gradle sync の前後
- わざと間違える: 定義のないプロジェクト・壊れた定義・モジュールが無いプロジェクトを開く

## 入口

- 使ってよい: 今回の版のプラグイン（10-3 の zip）を入れた IDEA と Android Studio・`sample/jvm` の写し
- 使わない: 開発用に build した IDE（`runIde`）での確認の代用（配る zip を入れる）

## やってみること（観点カードの種）

AI は、その版で変わった箇所に関係するものを 5 つ選んで TODO.html の「4. 操作してほしい」に載せる。

- ガター（行頭のアイコン）が出る・消える・クリックで何が起きるか
- ツールウィンドウの開閉・再起動後の状態・検査の実行と結果の表示
- 通知（no role の通知など）が出る条件と、文面が日本語の IDE で自然か
- 定義の追加・編集の流れ（定義ファイルの作成から検査の実行まで）
- 定義モジュールが見つからないときの案内が、直し方まで示すか

## 過去に見つかったもの

- Android Studio の既定設定で定義モジュールが見つからない（261119 の p10。KATACHI-363 の再発、PR #110）
- change-notes に 337 の変更が無い（261119 の IDE 再チェック。PR #113）
