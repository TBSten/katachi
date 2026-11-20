# チャーター: core（DSL・検査）

- 利用者像: 初めてアーキテクチャ定義を書く人 / v0.3 から移行する人
- 場所: クラウド
- 目安: 60 分

## 観点

- 初めて: 入門（`get-started/first-architecture`）だけを頼りに、自分のプロジェクトの写しに定義を書く
- 移行: リリースノートの BREAKING を上から実際に書き換える（移行前のコードは `v<基準>` のタグの sample から取る）
- わざと間違える: 定義の誤りが、エラーメッセージだけで直せるか
- 既定のまま: 設定を足さずに `architecture { }` を書いて検査が走るか

## 入口

- 使ってよい: mavenLocal に publish した katachi（9-1 の `build-katachi-cli.sh`）・`sample/jvm` の写し（`includeBuild` ではなく mavenLocal の katachi を使う形に直す）・ビルド済み docs
- 使わない: リポジトリの `katachi/build/`・`katachi/src` の直読（挙動の確かめはコードでなく実行結果でする）

## やってみること

- layout の指定漏れ・過剰（`ignore()` の場所、no role と数えるもの、Unexpected になるもの）を意図的に作り、報告が直し方まで示すか
- enum の項目・`sealed`・`data class` など、種類ごとの Role を書き、deny by default に当たったときの案内を読む
- baseline を作って、ファイルを足す・消す・名前を変えるたびに検査と baseline の更新がどう振る舞うか
- 検査が黙って通っていないか: 違反するコードを足して、**必ず落ちる**ことを確かめる（黙って効かないのは p9 以上）
- 日本語・英語の出力（`KATACHI_LANG`）の食い違い

## 過去に見つかったもの

- 作業用ディレクトリを 2 つ渡すと黙って既定に戻る（261110 の p7）
- 作業用ディレクトリの指定が worktree だと、メインを検査して黙って通る（261110 の p9）
