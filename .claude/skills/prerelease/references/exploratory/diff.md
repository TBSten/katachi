# チャーター: diff（katachiDiff・Actions）

- 利用者像: PR にアーキテクチャの差分レポートを出したい人
- 場所: クラウド（Actions は GitHub 上）
- 目安: 45 分

## 観点

- 初めて: `guides/diff-report` だけを頼りに、自分のプロジェクトでレポートを出す
- わざと間違える: 巨大な差分・リネーム・バイナリ・空の差分・基準のコミットが無い
- 既定のまま: 設定を足さずに出したレポートが読めるか（重み・ヒートの意味が分かるか）
- 環境違い: shallow clone・detached HEAD・別のデフォルトブランチ名

## 入口

- 使ってよい: mavenLocal の katachi と Gradle plugin・本リポジトリの**過去の PR の差分**（`git log` から選ぶ。リポジトリは読むだけ・写しの中で動かす）・composite action（`diff-report`・`diff-report-comment`）
- 使わない: 本リポジトリのブランチへの push（Actions は fork または使い捨てのリポジトリで）

## やってみること

- 過去の PR から、巨大（1000 行超）・リネームだけ・バイナリだけ・テストだけ・空の 5 種を選び、4 形式（HTML・Markdown・JSON・YAML）で出す。人の目で読んで「どこが重要か」が分かるか
- 出した HTML を幅 390px と大きい画面で開く。ライト・ダークの両方で読めるか
- 同じ入力で 2 回出して、出力が同じか（順序・時刻が混ざらないか）
- Markdown を PR コメントに貼ったときの長さ・リンク・折りたたみ（`pr=` の GitHub リンク）が崩れないか
- Actions を使い捨てのリポジトリで動かし、権限不足（fork の PR）のときの失敗の仕方を見る

## 過去に見つかったもの

- diff-report のガイドの files-by-role.md の食い違い（261119 の p7。PR #105）
