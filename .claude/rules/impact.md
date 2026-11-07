# 影響範囲の書き方

PR 本文・Notion チケット・調査レポート・チャットの報告で変更の影響範囲を書くときは、[`CONTRIBUTING.ja.md`](../../CONTRIBUTING.ja.md) の「影響範囲を書く」の形に従う。

- 3 段: メインの変更 / 軽微な変更 / テスト・動作確認 › コンポーネント › `new` / `update` / `fix` / `delete`: 箇所 … 内容
- 利用者の書き換えが要る変更は `update!` のように `!` を付け、Before / After を必ず書く
- コンポーネント名は CONTRIBUTING の一覧から選ぶ。katachiDocs・katachiTemplate・katachiDiff は core の中（`katachiDiff › …`）
- ファイル・Role の一覧は katachiDiff のコメントに任せて書かない
- PR は `.github/pull_request_template.md` の枠を埋める
