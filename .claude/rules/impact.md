# 影響範囲の書き方

PR 本文・Notion チケット・調査レポート・チャットの報告で変更の影響範囲を書くときは、[`CONTRIBUTING.ja.md`](../../CONTRIBUTING.ja.md) の「影響範囲を書く」の形に従う。

- 3 段: メインの変更 / 軽微な変更 / テスト・動作確認 › コンポーネント › `new` / `update` / `fix` / `delete`: 箇所 … 内容
- 利用者の書き換えが要る変更は `update!` のように `!` を付け、Before / After を必ず書く
- コンポーネント名は CONTRIBUTING の一覧から選ぶ。katachiDocs・katachiTemplate・katachiDiff は core の中（`katachiDiff › …`）
- ファイル・Role の一覧は katachiDiff のコメントに任せて書かない
- PR は `.github/pull_request_template.md` の枠を埋める

## PR に書くときの例

`.github/pull_request_template.md` の「## 影響範囲」をこう埋める（チケットの直後、詳しい説明の前）。

```md
## 影響範囲

### メインの変更

- CLI
  - new: `katachi <file>.katachi.kts` … スクリプトを実行する
- konsist
  - update!: ktFileContent … enum の項目が deny by default に
    - Before: ``allow(enum, `class`, "Color")``
    - After: ``allow(enum, `class`, "Color") { allow(entry, "*") }``

### 軽微な変更

- core
  - fix: katachiDiff › ignore() の場所 … no role と数えない

### テスト・動作確認

- script: `KatachiScriptSpec` 13 件（CI）
- IDE plugin: プラットフォームのテストは CI で初めて走る（手元では未確認）
```

- コンポーネントの中で箇所が続くときは、種類ごとに 1 行ずつ書く（`new` を 2 行並べてよい）
- 中身が無いグループは「- なし」と書いて見出しを残す
