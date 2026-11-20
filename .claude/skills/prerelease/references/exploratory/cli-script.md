# チャーター: CLI・script

- 利用者像: brew で katachi コマンドを入れた人。`*.katachi.kts` を書く人
- 場所: クラウド
- 目安: 45 分

## 観点

- 初めて: `katachi --help` と各サブコマンドの `--help` だけで使えるか
- わざと間違える: 打ち間違い・オプション無し・存在しないファイル・存在しないディレクトリ・矛盾するオプション
- 既定のまま: オプションを何も付けずに、プロジェクトの直下・サブディレクトリ・git の外で実行する
- 環境違い: `kotlin` が無い・古い Kotlin・日本語と英語（`KATACHI_LANG`）

## 入口

- 使ってよい: 9-1 の `build-katachi-cli.sh` が組んだ `bin/katachi`（brew の tarball と同じ形）。呼ぶたびに絶対パスで
- 使わない: リポジトリの `katachi-script/src` から直接動かす近道（brew で入れた人と同じ入口にする）。クラウドに `kotlin` が無いときだけ、classpath から直接動かしてよい（[exploratory-nightly.md](../exploratory-nightly.md) の手順 4。結果に入口を書く。`--version` が `0.0.0-unknown`・`scaffold` が `--katachi` を求めるのは環境の都合で発見にしない）

## やってみること

- サブコマンド（`scaffold`・`doctor`・`lint`）ごとに、**引数を 1 つずつ外す・間違える**。終了コード（0・1・2）とメッセージが使い方の誤りと違反を区別しているか
- 消した `agent` / `test` / `compare-violations`（KATACHI-429）を打ったとき、「無くなりました」と `./gradlew` を直接打つ案内を出して止まるか
- `*.katachi.kts` を書き、わざと構文エラー・型エラー・存在しない API を呼ぶ。エラーの行が分かるか。実行が止まったように見えないか（Maven Central が遅いときの待ち）
- 出力をパイプ・ファイルにリダイレクトしたとき（色・進捗の表示が崩れないか）
- `katachi scaffold` を 2 回続けて走らせる・既存のファイルがあるときの上書き・衝突
- スクリプトの結果を CI で使う想定で、終了コードだけで成否を判断できるか

## 過去に見つかったもの

- KATACHI_WORKDIR を worktree から使うとメインを検査して黙って通る（261110 の p9。KATACHI-353）
- scaffold の引数の誤りで終了コードが 1（失敗）と 2（使い方の誤り）に割れる（KATACHI-402 の試し回し。p4・意図かもしれない）
