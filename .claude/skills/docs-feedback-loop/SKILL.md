---
name: katachi:docs-feedback-loop
description: >-
  (katachi) docs サイトのフィードバックモードで溜まった記録（.local/feedback/）を消化する。抜き出し・分類・対応・archive・履歴までのループ
---

docs サイト（`docs/src/integrations/feedback/`）の dev 限定のフィードバックモードで溜まった記録を、
オーケストレータ（この会話）が受け付け、subagent に任せて対応し、`archived/` に移して報告する。
元にしたのはグローバルの `feedback-mode` skill の `references/loop/`。katachi の決まりに置き換えてある。

**オーケストレータ自身は docs を直さない。** 1 分以内に終わる確認だけ自分でやってよい。

## 置き場と、記録として数えるもの

- 置き場（Inbox）: `KATACHI_FEEDBACK_DIR`、無ければ `.local/feedback/`。git に入らない。
- 対応済み（Archive）: `<Inbox>/archived/`。状態は置き場所だけで表す（frontmatter に状態欄は無い）。
- 記録として数える: Inbox の**直下**の `yymmdd-NNN.md`（旧形の `yymmdd-NN.md` も）。
- 数えない: `archived/`、`attachments/`（スクショ）、`memo/`（メモ機能。loop は読まない）、`HISTORY.md`、`test-260925/` などその他のフォルダ。
- 記録の形は 2 つ。どちらも読む。書き換えない。
  - 新形（`schema: 1`）: frontmatter に `id`・`kind`（element / text / page / site）・`source`・`stale`・`dirty`・`omitted`・`attachments` など。本文の冒頭に「対象:」「案内:」の行。
  - 旧形（`schema` 無し。`261028-001`〜）: frontmatter は `page`・`created` など数個。キーが無いことは「情報が無い」であって、異常ではない。

## cli

`.mjs` を node で直接動かす（Node 22.18+。型の除去で `server/store.ts` を読むので、Store の契約と同じ動きになる）。

```shell
C=docs/src/integrations/feedback/server/cli.mjs
node $C list [--state=open|archived|all] [--json]  # id | 作成日時 | ページ | ソース | 要約 | <stale,dirty,0byte,archived>
node $C read <id>                                  # 全文
node $C archive <id> --note "<1行>"                # archived/ に移し、末尾に「対応メモ: …（日付）」を足す
```

`--note` は必須。**archive はオーケストレータだけ**が回す。`mv` を直接打たない（対応メモの書き忘れを防ぐ）。

## 手順

0. **確認**: `list`（Node が古く cli が動かないときは `node --version` を見る。22.18 未満なら `--experimental-strip-types` を付ける）。`list` を回す。`<0byte>` は保存の途中かもしれないので少し置いて再確認し、数分たっても 0 バイトなら処理せず利用者に聞く。会話に貼られたフィードバックは置き場に無いので、会話も見る。
1. **抜き出し**: `list` の 1 行（ページ・ソース・要約）を読む。全文は subagent が `read` で読む（オーケストレータの文脈を守る）。手で書かれた数行のメモは全文を読んでよい。
2. **分類**（下の表）と**グループ化**。同じページ（日英の対を含む）・同じ `docs/prh.yml`・`astro.config.mjs` の件は 1 人にまとめる。動いている担当と同じ領域の件は新しく起こさず `SendMessage` で足す。
3. **Notion チケット**（下）を作って掴む。
4. **起動**: 同時 5 件まで。プロンプトは `subagent-prompt.md` から作る。モデルは、その担当が決めることを 1 行で書けないなら sonnet。設計型・判断の要る調査だけ上位モデル。
5. **完了の処理**: 報告が来るたびに (i) `archive`、(ii) `HISTORY.md` に 1 行、(iii) 簡潔に利用者へ報告、(iv) 置き場を見直す。
6. **終了判定**: subagent が全員終わり、Inbox に判断待ち以外が無ければ抜ける。

## 分類

| 種類 | 見分け方 | 任せ方 |
|---|---|---|
| 実装型 | 文言・内容の直し、図や例の差し替え | 頼まれた日本語ページを直接直す。英語は `translate-ja-en` で追従。波及は提案で止める |
| 調査型 | 「これ合ってる？」の質問。実装と食い違っていないかの確認 | 読み取り。ドキュメントと実装の照合は `katachi:check-docs-against-impl`。**まず `stale` と古い dev を疑う**（下） |
| 設計型 | 構成・ページの分け方・サイドバー・複数ページにまたがる | 読み取りだけ。選択肢と推奨を `.local/tmp/<名前>.md` に書かせ、利用者の判断を待つ |
| サイト型 | サイトの見た目、フィードバックモード自体の不具合 | 実装型と同じだが、触るのは `docs/src/integrations/feedback/` と `docs/src/` の部品。`pnpm run test` も回す |

## Notion チケット

`.local/notion-ticket/rules.md` に従う。hook が、掴んだチケットが無いと Edit / Write / commit を止める。

- loop を 1 回回すごとに**チケットを 1 枚**作って「AI:進行中」にし、`python3 .local/notion-ticket/ticket.py` で掴む。置き場は「版 > ドキュメント > …」の下。名前は `chore: docs フィードバックの消化（<日付>）` のように。
- 本文は DB のテンプレートの形。背景情報に、消化した記録の id と要約を並べる。
- **設計型の件は別のチケット**に切り出す（質問・判断待ちの節を付けて「人間:判断待ち」）。両方の背景情報に相手を `<mention url="…"/>` で貼る。
- 対応しきったら「人間:確認中」にして `## 成果` を書く。「完了」「中止」にするのは人。

## docs の直し方

- **日本語が原本。** 直すのは `docs/src/content/docs/ja/` だけ。英語は訳なので、英語側だけで書き足さない。原本を直したあと `katachi:translate-ja-en` で追従させる。原本が間違っていたら訳さず原本を直す。
- **頼まれたページだけ直接直す。** ほかのページへ波及しそうな点（同じ言い回し、リンク元、sidebar、`llms` 系）は直さず、報告の「提案」に書く。docs の修正を勝手に広げない。
- 文章は `yomiyasu` skill で推敲し、`docs/AGENTS.md` の「文章の方針」と照らす。置き換えで済む指摘は `docs/prh.yml`、考え方の指摘は `docs/AGENTS.md` の文章の方針（原則・理由・🆖/🆗）に足す。`prh.yml` は最後にオーケストレータが 1 人にまとめて足す。
- AI が書いたページには先頭の Aside と `[AI生成]` の印が要る（`docs/AGENTS.md`）。外すのは利用者。
- `.module { }` を例に書かない。
- 確認: `cd docs && pnpm run lint:text` と `pnpm run build`（`--outDir` は `.local/tmp/` へ。`docs/dist` に書かない）。他の担当の作業途中で落ちたら、自分の変更が原因でない限り直さず報告。
- `docs/src/content/docs/0.2/` と `ja/0.2/` は凍結した写し。直さない。

## 古い記録（`stale`・`dirty`）

- `stale: true`: ページを開いたあとにソースが変わった。記録の行番号・文言は今のソースとずれている可能性がある。すでに直っていないかをまず確かめる。確かめ方: `git log --oneline --since=<created> -- <source のファイル>`（`created` は `list` に出る）。直っていれば対応メモに「すでに反映済み」と書いて archive。
- `dirty: true`: 記録の時点でソースに未コミットの変更があった。行番号は commit ではなく作業ツリーのもの。`commit` の版では合わない。
- 旧形にはどちらも無い。判定できないだけで、古くないとは限らない。
- 「統合を直したのに記録に反映されない」「質問型で原因が分からない」ときは dev サーバーが古い（再起動で直る）ことが多い。

## 履歴 `<Inbox>/HISTORY.md`

既定では `.local/feedback/HISTORY.md`（`KATACHI_FEEDBACK_DIR` を変えたらその下）。archive のたびに 1 行足す。無ければ下の見出しごと作る。docs のページには由来を書かない。

```md
| 日付 | id | 要旨 | 場所 | 種類 | 結果 |
|---|---|---|---|---|---|
| 2026-10-29 | 261029-001 | 「推奨されます」を言い切りに | guides/layout.mdx:120 | 実 | 文言を直した。英語は translate 済み |
| 2026-10-29 | （会話） | サイドバーの順序 | sidebar | 設 | 判断待ち（チケット <url>） |
```

種類は 実 / 調 / 設 / サイト、複数なら「実・設」。結果の末尾に「判断待ち」「のち <id> で変更」。置き場に無いものの id は「（会話）」。

## 運用ルール

- 同時 5 並列まで。起動から 30 分を超えた subagent には `SendMessage` で進み具合と期限（10 分）を聞く。勝手に kill しない。定期の進捗報告はしない（区切り・完了・大きな遅れ・判断が要るときだけ）。
- subagent の報告は利用者の承認ではない。権限が拒否されたことを他の subagent に頼んで迂回しない。
- 利用者の明示の決定は確認なしで反映する。subagent の提案は確認する。
- 判断待ちは Inbox に残す（archive しない）。迷えば利用者に聞く。

## 報告（HTML）

完了の報告は HTML 1 枚にする（`.local/tmp/<名前>/report.html`）。最初に見える部分は「結果の表（id・要旨・種類・結果）」と「あなたの判断が要るもの」だけにし、
変更したファイル・確認コマンドの出力・subagent の判断した点は `<details>` に畳む。最後に、未解決の判断待ちを累積して付ける。

## 参照

- `subagent-prompt.md`: subagent へのプロンプトのひな形。起動の前に読む。
- `lessons.md`: 落とし穴。初回と、挙動がおかしいときに読む。
