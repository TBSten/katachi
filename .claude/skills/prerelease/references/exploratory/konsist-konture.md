# チャーター: konsist・konture

- 利用者像: 既存の Konsist・Konture のテストを持っていて、katachi に置き換える人
- 場所: クラウド
- 目安: 45 分

## 観点

- 移行: 既存のテストを katachi の `ktFileContent`・Konture 連携で書き直す（リリースノートの BREAKING のうち konsist・konture の行を上から）
- わざと間違える: deny by default を踏む（enum の項目・プロパティ・関数を許可し忘れる）
- 既定のまま: 許可を足さずに書いたとき、報告が何を足せばよいかを示すか
- 環境違い: Kotlin・Konsist・Konture の版が違うときの失敗の仕方

## 入口

- 使ってよい: mavenLocal に publish した `katachi-konsist`・`katachi-konture`（9-1 の `build-katachi-cli.sh` の publish）・ビルド済み docs の `kt-file-content` のページ
- 使わない: リポジトリ内の spec を流用すること（利用者は spec を持っていない）

## やってみること

- ``allow(enum, `class`, "Color")`` のように v0.3 の書き方をそのまま使うと何が起きるか（移行表どおりに直せるか）
- 許可の書き方を 3 通り以上変え、報告のメッセージと許可の書き足し先が一致するか
- 書き換えで検査が緩くなっていないか: 移行の前後で、違反を同じ数だけ検出するか
- konsist と konture で、同じ定義の報告が食い違わないか
- 日本語・英語の報告の文面

## 過去に見つかったもの

- なし（初回）。9-2 で konsist の案内の食い違いが出た前例は PR #108（KATACHI-397）
