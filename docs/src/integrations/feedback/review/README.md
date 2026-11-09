# Review（docs への指摘を、ページの上に印と popup で出す）

`astro dev` のときだけ使える toolbar アプリ。Feedback・Memo の隣の「Review」を押すと、今のページへの指摘が
本文の横の番号の印と枠で出て、印を押すと popup で読める。既定は隠したまま。出しているあいだはタブの中で覚えていて、
ページを移っても出し続ける（もう一度押すか、一覧の × で隠す）。本番ビルドには出ない（`scripts/check-no-dev-tools-in-dist.sh`）。

## 流れ

```mermaid
flowchart LR
  A["agent: 指摘を書く<br/>review/*.md（open）"] --> B["人: ページの popup で<br/>「解決済みにする」（一言つき）"]
  B --> C{"agent: 確かめる<br/>review list --status=resolved"}
  C -- 直っている --> D["review remove &lt;id&gt;"]
  C -- 直っていない --> E["review reopen &lt;id&gt; --note &quot;理由&quot;<br/>（open に戻り、popup に理由が出る）"]
  E --> B
```

## 置き場

`<置き場>/review/<名前>.md`。置き場は `KATACHI_FEEDBACK_DIR`、無ければリポジトリ直下の `.local/feedback/`（git に入らない）。
1 ファイルが指摘のまとまり 1 つ（例: `prerelease-261109-01.md`）。名前は英数字・`.`・`_`・`-` だけ。
ファイルは開くたび（とタブに戻ったとき）に読み直すので、dev サーバーを立て直す必要は無い。

## 形

```md
---
title: v0.4.0 リリース前チェック 手順 11
base: 1a7a072
---

## [p3] dsl.gradle を個別 import している
- file: ja/get-started/first-architecture.mdx
- line: 168
- quote: import me.tbsten.katachi.dsl.gradle.gradle

本文（Markdown）。箇条書き・`code`・**太字**・[リンク](https://…)・コードフェンス・引用が使える。
- 該当: …
- 直し方の案: …
```

- frontmatter は省略できる。`title` は一覧と popup の場所の表示に、`base` は「行番号を数えたコミット」の表示に使う。
- 指摘 1 件は `## ` の見出しから次の `## ` の手前まで（コードフェンスの中の `## ` は見出しにしない）。
- 見出しの先頭の `[pN]` が優先度（0〜。p7 以上は赤、p5〜6 は橙、p3〜4 は黄、それ未満は灰、無ければ藍）。`- priority: N` でもよい。
- 見出しの直後に続く次のキーだけがメタ。ほかの行（`- 該当: …` など）は本文になる。

| キー | 中身 |
|---|---|
| `file` | 必須。リポジトリ直下からのパス。docs の本文は `docs/src/content/docs/` を省いて `ja/guides/role.mdx` と書いてよい。ページの無いファイル（`README.ja.md` など）も書ける（一覧の「ページの無いファイル」に出る） |
| `line` | `168` / `160-186` / `L160〜L186`。無ければページ全体への指摘（ページの見出しのそばに出す） |
| `quote` | その行に含まれる文字列。指摘を書いたあとで行がずれても、いちばん近い quote の行へ動かして出す。`…`・`...` は省略とみなす。line が無く quote だけなら、quote を含む最初の行に出す。範囲の指摘は先頭の行の文字列を書く |
| `status` | `open`（既定）/ `resolved`。popup の「解決済みにする」が書く |
| `resolved-at` / `note` | 解決済みにした時刻と、人の一言。popup が書く |
| `ai-note` | agent の一言（差し戻しの理由）。`review reopen` が書く。popup に「agent から」として出る |

行は今のソースに合わせて出す。quote が見つからない指摘は書かれた行のまま出し、popup に「もう直っているかもしれません」と出す。

## cli

```sh
C=docs/src/integrations/feedback/server/cli.mjs
node $C review list [--status=open|resolved|all] [--json]   # 一覧（id・状態・優先度・今の行・見出し・一言）
node $C review show <id>                                    # 1 件の全部（ページで開くリンク `…/#review=<id>` も出す）
node $C review check                                        # 読めない指摘・無いファイル・quote が見つからない行。問題があれば 1 で終わる
node $C review remove <id>                                  # 確かめた指摘を消す（解決済みのものだけ。未解決も消すなら --force）
node $C review reopen <id> --note "<理由>"                  # 解決済みを差し戻す
```

id は `<まとまりの名前>/<file と見出しから作る 7 文字>`。ほかの指摘を消しても変わらない（見出しか file を書き換えると変わる）。

## 画面

- 一覧（右上。スマホは畳むと右上の札、開くと下）: このページの指摘・ほかのページの件数・ページの無いファイル・読めない指摘。優先度で絞る・解決済みも出す。
- 印を押す・一覧の行を押す → popup。前・次（ページをまたぐときは、そのページへ移ってから開く）、← → でも動く、Esc と × で閉じる。右下に「何件め / 総数」（絞り込み後の全体）。
- 閉じた details・選ばれていないタブ・畳んだコードブロック（Show more）の中の指摘は、開くときに見えるようにする。コードブロックの中は 1 行ずつ光らせる。
- popup を開いたまま docs を直すと（Astro がページを読み直す）、同じ指摘を開き直す。quote で今の行を探し直すので、直して quote が消えれば「もう直っているかもしれません」と出る。
- `<ページ>#review=<id>` のリンクで開くと、隠していても出してその指摘を開く（人に 1 件を見てもらうときに渡す）。
- popup の上の「L174 faq.mdx」を押すと `file:line` をコピー、「開く」でエディタのその行を開く（Astro の dev サーバーの「エディタで開く」。エディタは `LAUNCH_EDITOR` などで選ぶ）。
- 「解決済みにする」は次の指摘へ進む（トーストの「戻す」で取り消せる）。保存中と進んだ直後は押せない（連打で次の指摘まで解決済みにしない）。
