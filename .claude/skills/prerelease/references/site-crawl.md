# 6. ドキュメントサイトと API リファレンスを Playwright で巡回する

[SKILL.md](../SKILL.md) の手順 6 の詳細。

ビルドが通ることと、ページが正しく見えることは別。実際のブラウザで全ページを開いて確かめる。

1. ビルドして配信する（リポジトリの直下で）。ログは `.local/release-v0.0.0/tmp/logs/` に書く。

   ```shell
   ./gradlew generateApiDocs      # docs/public/api-docs を作り直す（verifyApiDocs 込み）
   cd docs && pnpm run build      # リンク検査込み。落ちたら、その内容を警告にする
   cd docs && pnpm preview        # http://localhost:4321/katachi/ で配信（background で起動し、最後に止める）
   ```

2. 巡回するページを列挙する。一覧をファイルに書くなら `.local/release-v0.0.0/tmp/pages-<区分>.txt` に置く。

   ```shell
   python3 .claude/skills/prerelease/scripts/list-site-pages.py                 # 区分ごとの件数
   python3 .claude/skills/prerelease/scripts/list-site-pages.py --group ja      # en / ja / api-docs
   ```

3. 区分（en / ja / api-docs）ごとに1体ずつ、並列な subagent（model: sonnet）に Playwright MCP で巡回させる。
   渡すもの: その区分の URL の一覧と、下の「見ること」。スクリーンショットは `.local/release-v0.0.0/screenshots/<区分>/`
   に置かせる。巡回の生データ（ページごとの記録・コンソールの出力など）は `.local/release-v0.0.0/tmp/crawl/<区分>/` に置かせる。
   **ページを直させない。**見つけたものは警告として報告させる。

   見ること（ページごと）:
    - 開けるか（404 やビルドの取り残しが無いか）、コンソールのエラー・警告
    - ページ内のリンク（サイト内のもの）が開けるか。API リファレンスへのリンクも含める
    - 見た目が崩れていないか: `<Tabs>`（ラベルの折り返し）、`<FileTree>`、`<CodeComparison>`、Mermaid、表、コードブロック、
      `<details>`。
      スクリーンショットを撮って目で見る
    - 画面幅 390px で横にはみ出さないか（`document.documentElement.scrollWidth` が画面幅を超えないか）
    - ja と en の切り替え: 言語の切り替えで対応するページへ移れるか。日本語のページに英語が、英語のページに日本語が混ざっていないか
      （katachi が出力する固定の日本語の文字列は除く）
    - 本文に残った `TODO` や、書きかけの文

   API リファレンス（api-docs）で追加で見ること:
    - サイドバー: 各モジュールの先頭が「⭐️ Featured」、その下に package の階層（`me.tbsten.katachi` 以下）が並ぶか。
      選択中のページの強調と自動展開
    - トップとモジュールのページの Featured の節、検索（Cmd+K）
    - `llms.txt`、`llms-full.txt`、各ページの `.md`（HTML の URL に `.md` を足したもの）が開けるか
    - `.internal` パッケージのページが出ていないか

4. 終わったら配信を止め、`.playwright-mcp/` などの一時ファイルを消す。

- 区分ごとの結果（ページ数・問題の一覧）は `.local/release-v0.0.0/site-crawl.md` にまとめる。
- prerelease-check-list.md には、区分ごとのページ数と見つけた問題の件数、priority 7 以上の警告だけを書く。
