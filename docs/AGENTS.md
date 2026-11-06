## Development

When starting the dev server, use background mode:

```
astro dev --background
```

Manage the background server with `astro dev stop`, `astro dev status`, and `astro dev logs`.

## トップページ

トップページ（`/katachi/` と `/katachi/ja/`）は **content collection の MDX ではなく、`src/pages/` の `.astro`**。
中身のほとんどが HTML の構造で、MDX にすると同じ構造を日英の2ファイルに書くことになるため。

| ファイル | 中身 |
|---|---|
| `src/components/home/Home.astro` | ページの構造（1か所だけ）。「このページの目的」のコメントも frontmatter の中にある |
| `src/components/home/strings.ts` | 文言の辞書。`ja` が原本、`en` が訳。**文言を直すときはここだけを直す** |
| `src/pages/index.astro` / `src/pages/ja/index.astro` | `<Home lang="en" />` / `<Home lang="ja" />` を呼ぶだけ |
| `src/styles/home.css` | 見た目（`customCss` で読む） |

- Starlight の見た目（ヘッダ・検索・言語の切り替え・splash）は `StarlightPage` で保っている。title / description は辞書から渡す
- コードブロックは `<Code>`（expressive-code）。`<Code>` は JSON にできない Expressive Code の設定があると描画できないので、
  設定は `ec.config.mjs` に置く（`astro.config.mjs` 側は `expressiveCode: true` だけ）
- content collection の外なので、`starlight-links-validator` は `/katachi/` と `/katachi/ja/` を知らない。
  `exclude` で外してある。**トップページはサイドバーに出さない**（サイトタイトル / ロゴから辿れるので十分）
- `llms-full.txt` / `llms-small.txt` にトップページの文言は入らない（content collection しか読まないため）

## サイドバーの構成

**ドキュメントの本文は人が書く。** エージェントが用意してよいのは、サイドバーの配線と
空ページ（frontmatter だけ）まで。例外は、人が「下書きを書いて」と頼んだページ。そのときは
本文の先頭（「このページの目的」のコメントと import の直後）に
`<Aside type="note" title="AI が書いた下書き">` を置き、AI エージェントが書いた下書きで人の見直しが
済んでいないことを明記する（英語版も同じ Aside を訳して付ける）。Aside を外すのは見直した人。
新しく足したページなら、サイドバーの表示名（`astro.config.mjs` の `label` と `translations.ja`）と
frontmatter の `title`（ページの見出しになる）の先頭にも `[AI生成]` を付ける（英語は `[AI-generated]`）。
`title` は先頭が `[` なので、YAML の配列と読まれないよう `title: "[AI生成] …"` と引用符で囲む。
既存のページを AI が書き直しただけなら、印は Aside だけにする。印を外すのも見直した人。

**日本語が原本、英語は翻訳。** `src/content/docs/ja/` を人が書き、`src/content/docs/` は
**そこから機械的に訳すだけ**にする。英語側だけで文章を足したり、英語側で先に書いたりしない。

- 節の構成・順序・表の行は**原本と1対1**にする。英語側だけに節を増やさない
- 原本に `TODO` があれば、**訳にも `TODO` のまま残す**。埋めるのは原本が先
- **原本が間違っていたら、訳さずに原本を直してもらう。** 誤りを2言語に増やさない
- コード例の中の日本語（制約名の文字列など）は訳す。訳さないと英語ページに日本語が出る
- 未使用の import のような明らかな無駄は写さなくてよい

原本を直したら**その場で訳も直す。** 溜めると、どちらが新しいか分からなくなる。

**各ページは、frontmatter の直後に「このページの目的」を MDX のコメントで書く。**
ページが何のためにあり、誰に何を伝えるのかを、書き手と次に直す人が最初に読めるようにする。
表示はされない。

```mdx
---
title: ...
...
---
{/*
  このページの目的: ...
*/}

## ...
```

- 1〜3行で書く。ほかのページと役割が重なりそうなら、その切り分けも書く
- 英語の訳にも同じ位置に置き、中身を英語に訳す（MDX のコメントは訳す、の決まりどおり）

```
- はじめる
    - モチベーション                    ［ページタイトルは「モチベーションと katachi の立ち位置」］
    - 初めての定義                      ［ページタイトルは「初めてのアーキテクチャ定義」。インストール込み。専用ページは作らない］
    - FAQ                              （思想・考え方の FAQ。ハウツーは書かない）
    - 他ツールとの比較                  （Konsist / detekt / ArchUnit などとの守備範囲）
- ガイド                                （もとはコンセプトとガイドの2節。1つにまとめた）
    - 基本的な API                      （覚える7つ。この節の入口）
    - Role                             （group と「Role 定義を分割する」を含む）
    - Layout                           ［ページタイトルは「Layout システムでディレクトリ構成を厳守させる」］
    - ファイルの中で定義できる宣言の許可リストを定義する   （`ktFileContent { }`。katachi-konsist のインストールの節もここに置く）
    - Konture との統合                                     （`konture { }`。`ktFileContent { }` と同じ位置に書く Konture の規則。katachi-konsist（`ktFileContent { }`）と同じテストモジュールには置けない）
    - ドキュメント生成
    - テンプレートからコード生成
    - Baseline                         （実験的な機能。processor の手前に置く）
    - ArchitectureProcessor とそのカスタマイズ
- レシピ
    - 一覧                              （`/recipes/`。カードはビルド時に自動収集する）
    - Android の3層アーキテクチャ
    - Gradle                           （`gradle()` と、composite build / buildSrc を自分で宣言する範囲）
    - AI Agent
    - ktlint
    - detekt
    - Git
    - GitHub
- API リファレンス（/api-docs/）
- ロードマップ
```

サイドバーの正は `astro.config.mjs` の `sidebar`。ページを足したり並べ替えたりしたら、この木も直す。

決まっている判断:

- **インストール専用ページは作らない。** 「初めてのアーキテクチャ定義」が手順を全部持つ。
  二段構えにしていた時期があるが、書き分けるほどの中身が無く、下のエッジケースも
  そちらへ入れる
- **エラーメッセージの読み方はドキュメント化しない。** メッセージそのものを読めば対処が
  分かるべきで、読み方のガイドが要るなら、メッセージが分かりにくいということ。ドキュメントは必ずズレる
- **既存プロジェクトへの段階導入は当面入れない。** 入れるなら「初めてのアーキテクチャ定義」に
- **IDE プラグインは、当面 docs で案内しない。** 0.2 の写しにあった「IDE から生成する」の節は最新版には無く、
  README の原本（`README.ja.md`）からも案内を外してある。書くと決めたら、入れ方とテンプレート生成の操作は
  テンプレートのガイドの節（「IDE から生成する」）に置き、ガターの ◆・違反の下線・当たる Role が無いファイルの通知は
  ガイドの末尾の専用ページ「IDE プラグインで定義を見ながら書く」に分ける（専用ページは何ができるか・必要なもの・設定を持ち、
  入れ方は節を指す。入れ方を2か所に書かない）
- **`processor` は前面に出さない。** 内部的な設計の話で、ほとんどの利用者は
  `assert()` の1行しか触らない。目立つ位置に置くと「理解しないと使えない」と言ってしまう。
  ガイドの末尾に置き、ページが自分で短い前提（2〜3行）を持つ
- **ページタイトルとサイドバーのラベルは分けられる**（Starlight の `sidebar.label`）
- **存在しないページにリンクするとビルドが落ちる。** 書けるページから順に出す
- **`@ExperimentalKatachiApi` が要る API を使う箇所に「`@OptIn(ExperimentalKatachiApi::class)` が必要です」と書かない。**
  opt-in が無ければコンパイラがそう言い、`@RequiresOptIn` の message にも注釈名（`ExperimentalKatachiApi`）が入っているので、
  Gradle でも CLI でも、エラーメッセージから直せる。本文とコード例が注記で重くなるだけ
- **ファイル全体を見せる例（`package` や `import` から書く例）には `@file:OptIn(ExperimentalKatachiApi::class)` を書く。断片の例には書かない。**
  ファイル全体の例は利用者がそのまま写してコンパイルするので、opt-in まで揃っている必要がある。
  断片の例は写した先のファイルに opt-in があるかどうかが分からず、書くと本文が注記で重くなるだけ（上の「OptIn が必要です」と書かないのと同じ理由）。
  `@file:OptIn` の後ろに空行を1つ置き、`import me.tbsten.katachi.ExperimentalKatachiApi` を import の並びに入れる。
  1つのコードブロックに複数のファイルを並べる例（`// roles/Git.kt` のように区切る例）では、ファイルごとに書く
- **コード例で `.template { }` を書くときは、ファイルの宣言と `.template` の間で必ず改行する。**
  `.template` がどのファイルの宣言に付いているのかを、1行目で読めるようにするため。
  ```kotlin
  "feature" / capture("feature") / "${capture("name")}Screen".ktFile()
    .template {
      // ...
    }
  ```
- **コード例の Kotlin（`kotlin` / `kt` / `kts` のコードブロック）は 2 スペースで字下げする。** 継続行も +2。三重引用符の文字列の中身も 2 単位にし、生成されるファイルの例と並べたときにそろえる。タブは使わない。
  `<CodeComparison>` や `<Tabs>` の中のコードブロックは、フェンスの字下げの分だけを入れ、コード自体を余計に字下げしない。
  2026-10-29 に利用者の指摘で、全ページのコードブロックを 4 から 2 に直した
- **「次のステップ」の節は `<CardGrid>` + `<LinkCard>` のカードだけにする。** 導入文・締めの文（「ほかのガイドはサイドバーから」など）や、カードの下の箇条書きを足さない。
  ページに関係する良いサンプル（`sample/` の具体的なディレクトリやファイル）があれば、0〜2 枚のカードで足す
- **LinkCard の題（`title`）と説明（`description`）にコードを書かない。** `<code>` もバッククォートも使わず、普通の文で書く。
  カードは読み流す場所で、コード表記はそこで目に引っかかるだけ。textlint・prh はこの 2 つを検査しないが、`docs/test/link-card.test.ts`（vitest）が検出する
- **節の索引ページは `<SectionIndex dir="..." />` で出す。** ページの集合は content
  collection から、並び順はサイドバーから採る。カードを手で並べない

「初めてのアーキテクチャ定義」に入れるエッジケース（分かっているもの）:

- ~~**AGP プロジェクトでは `:architecture-test` の Kotlin プラグインにバージョンを書けない。**
  ルートで `alias(libs.plugins.kotlinJvm) apply false` が要る
  （無いと `already on the classpath with an unknown version`）~~
  2026-10-06 にページから外した（利用者の判断。コード例で十分）。エラーメッセージから直せる
- version catalog を使わない場合の書き方
- `files = gitTracked()` が効く条件（`git rev-parse --is-inside-work-tree`。
  プロジェクトルートに `.git` が無くてもよい）
- Gradle のルートがリポジトリのルートと違う場合（モノレポ、サブモジュール）

## ドキュメントの版

版の切り替えは starlight-versions で出している。版の一覧は `src/lib/versions.mjs` の1か所にある。

- `src/content/docs/` の直下（`0.2/` などの版のディレクトリを除く）が、いま書いている版。URL は `/katachi/guides/...` のままで、ページを直すのはここだけ
- `src/content/docs/0.2/` と `src/content/docs/ja/0.2/`、`src/content/versions/0.2.json` は、v0.2 公開時点の写し（URL は `/katachi/0.2/...` と `/katachi/ja/0.2/...`）。**写しは凍結していて直さない。**
  直すのは、ビルドが落ちる・リンクが切れるといった明らかな壊れだけ。文章の方針や言い換えの辞書が変わっても、写しは合わせない
- 写しは textlint（`.textlintignore`）、translate-ja-en の訳の同期、check-docs-against-impl と prerelease の照合から外してある
- public/ の下（API リファレンス・インストールキット・スライド）は版を持たない。古い版のページからのリンクは、`src/middleware.ts` が最新のものへ向け直す
- いま書いている版のラベルは手で持たない。凍結した版の先頭から「次の minor + `-dev`」を算出する（写しの先頭が `0.3` なら `v0.4-dev`）。
  リリースのビルドだけが `KATACHI_DOCS_VERSION_LABEL`（タグから `v0.4`）で上書きする

### 公開サイトの構成（`.github/workflows/docs.yml`）

**公開サイトの root は常に最新のリリースの docs。** main の書きかけは `/katachi/dev/` にだけ出る。
利用者が README から着く先・検索・`llms-small.txt` はリリース済みの内容になり、「古い版」の注意も出ない。

| 置き場所 | ビルド元 | いま書いている版のラベル | 版の切り替えの選択肢 |
|---|---|---|---|
| `/katachi/`（と `/katachi/0.2/` などの写し） | main から辿れる最新の `v*` タグ | タグから（`v0.3`） | 現行・写し・末尾に開発版（`v0.4-dev` → `/katachi/dev/`） |
| `/katachi/dev/`（と `/katachi/dev/0.3/` などの写し） | main | 算出（`v0.4-dev`） | 現行・写し（`/dev/` の中で閉じる） |

docs.yml は 2 つを別々にビルドして 1 つの Pages に合成する。切り替えは環境変数で、`pnpm run build` を手で回すときは何も渡さなくてよい（root 相当のビルドになり、ラベルは算出）。

| 環境変数 | 意味 |
|---|---|
| `KATACHI_DOCS_BASE` | Astro の `base`。開発版のビルドで `/katachi/dev` |
| `KATACHI_DOCS_VERSION_LABEL` | いま書いている版のラベル。リリースのビルドでタグから渡す |
| `KATACHI_DOCS_DEV_BASE` | 版の切り替えの末尾に足す開発版の base（`/katachi/dev`）。リリースのビルドで渡す |

- 本文の絶対リンクは今までどおり公開 URL の `/katachi/...` で書く。開発版のビルドでは `src/lib/satteri-rebase-links.mjs`（Sätteri の mdast プラグイン）が
  `/katachi/dev/...` に付け替えるので、本文に `/katachi/dev/` を書かない。`astro.config.mjs` の `markdown.processor` がその入口
- root が変わるのは `v*` タグを push したとき（と `workflow_dispatch` で `release_ref` を指定したとき）だけ。main への push では `/dev/` しか変わらない
- `/dev/` の中の写し（`/katachi/dev/0.3/`）は root の写しと重複するが、そのままにしている（別のビルドなので外す手段が無い。気になったら dev のビルドで写しを除く）
- リリース済みの docs の誤字は、次のリリースまで root に出ない。急ぐときは `workflow_dispatch` に `release_ref` を渡して再ビルドする（直すのはタグ側でなく main。root に出すなら patch 版のタグを切る）
- `v0.3.0` 以前のタグの docs は上の環境変数を知らないので、root にはそのタグ当時のまま出る（ラベルは当時の値）。`/dev/` の選択肢だけは docs.yml の assemble が合成したあとの HTML に差し込む（`scripts/add-dev-version-option.mjs`。タグが選択肢を持っていれば何もしないので、root が v0.4.0 以降になったら消してよい）

### 新しい版を切る手順（v0.4 を公開するときの例）

**先にタグ、あとで写し。** タグを打つと docs.yml が root をそのタグから作り直す。写しを切るのはその後の main での作業で、root には影響しない。

1. v0.4 の docs を書き終える（`src/content/docs/` 直下）。ラベルは `v0.4-dev` のまま
2. リリース（`v0.4.0` のタグ）。docs.yml の `resolve` が新しいタグを拾い、root = v0.4（ラベルはタグから）、`/katachi/0.3/` `/katachi/0.2/` は写しになる
3. main で `src/lib/versions.mjs` の `archivedVersions` の先頭に `{ slug: '0.4', label: 'v0.4' }` を足す。ラベルの指定は無い（算出で `v0.5-dev` になる）
4. `pnpm run build` を1回回す。ビルドの開始時に、その時点の `src/content/docs/` から `src/content/docs/0.4/`・`src/content/docs/ja/0.4/`・`src/content/versions/0.4.json` ができる。一度に切れる版は1つだけ
5. `src/content/docs/ja/0.4/` の下にできた古い版の写し（`ja/0.4/0.3/` など）を消し、もう一度 `pnpm run build` を回す。starlight-versions は日本語の写しを作るとき、凍結済みの `ja/0.3/` まで写してしまう（英語側の `0.4/` には入らない）。残すと `/katachi/ja/0.4/0.3/...` へのリンクでリンク検査が落ちる
6. ビルド（リンク検査を含む）・`pnpm run lint:text`・`pnpm run test` が通るのを確かめ、写しを生成したままコミットする

写しを切るのは、その版の内容を書き終えてから、次の版の変更を入れる前にする。写しはビルドした時点の docs をそのまま写すので、次の版の変更が混ざると戻せない。

## `public/install/` — AI エージェント向けの配布物

ドキュメントサイトのページではなく、**AI エージェントが `curl` で取りに来る素材**を置く場所。
Astro は `public/` を加工せずそのままコピーするので、`<base-url>/install/...` で生のまま配信される。

| ファイル | 役割 |
|---|---|
| `index.md` | インストール手順そのもの。エージェントはこれを読んで動く |
| `katachi-install.sh` | 機械的にできる工程の**唯一の実装**。`init` が作業用ディレクトリの `tmp/` に自分を写し、`tmp/cache/` に記録と取得物を置く（直下は人が開く2つの HTML と `tmp/` だけ）。`init`・`doctor`・`scaffold` のほか、チェックリストとレポートを読み書きする `data`・`check`・`add`・`verify`・`summary` など（一覧は `--help`）。出力の言語は `init --lang` に従う |
| `install-check-list.html` | 進捗と結果のチェックリスト。`init` が配置し、中の `<script type="application/json" id="checklist">` をスクリプトの `data` 系のサブコマンドが書き換える |
| `project-code-base-report-template.html` | コードベース解析レポートのテンプレート。`init` が配置する |

守ること:

- **手順を `index.md` に足す前に、スクリプトでできないかを考える。** 「エージェントがよしなに」は
  環境ごとにブレる。判断が要らない工程は `katachi-install.sh` に入れ、`index.md` は
  コマンドを1行示すだけにする
- **生成する内容を2箇所に書かない。** モジュールの雛形はスクリプトの中だけにある
- **スクリプトの出力は ja / en の2通りを並べて書く**（`say "日本語" "English"` の形）。片方だけ足さない
- スクリプトが使うコマンドを増やしたら、サブコマンドごとの前提（`REQ_*` と `doctor`）にも足す
- スクリプトを直したら、`sh -n` に加えて**実際の Gradle プロジェクトで動かす。**
  最低限「AGP + version catalog」「ルート build ファイル無し」「Kotlin JVM が既にルートに居る」の3つ
  - これらを含む 11 種の形は `.claude/skills/prerelease/scripts/check-install-kit.sh` でまとめて回せる。
    リリース前には prerelease の手順 9 が、これに加えて手順書どおりに実在のプロジェクトへ導入する統合テストも回す
- 配信元は `KATACHI_DOCS` 環境変数で差し替えられる。dev server に向けて試せる。init に渡すと作業用ディレクトリの `tmp/cache/docs` に記録され、
  以降のコマンドもそこから取る。`docs` サブコマンドが取る `llms-full.txt` はビルドで作られるので、`docs/public` ではなく
  ビルド済みのサイト（`docs/dist` か配信 URL）を指す

## レシピ

**レシピはサンプルから引用してリンクする。** レシピはまるごと1つの定義なので古くなるが、
`docs/` はビルドの検査対象外なので**古くなって合わなくなっても誰も気づかない**。一方 `sample/{jvm,android,kmp}` は
CI で毎回回っている。解説 + サンプルの該当箇所へのリンク、という形にすれば、
定義が壊れたときに CI が落ちる。

**サンプルに無いレシピを書くなら、サンプルを先に増やすほうが安全。**

### レシピの一覧

| レシピ | 教える語彙 | 現物 |
|---|---|---|
| Android の3層アーキテクチャ | 層による分割、`ktFileContent { }` での命名と中身の規約 | `sample/android`（モジュールを分けた構成なので、レシピそのものではない） |
| **Gradle** | `gradle()` の1行、`documented = false`、composite build / buildSrc を自分で宣言する範囲 | 3サンプルの `GradleGroup.kt`（`gradle()` の1行） |
| AI Agent | ツールごとの group と、設定ファイルの種類ごとの Role | 無い |
| ktlint | linter の設定ファイルとベースラインの Role | 無い |
| detekt | 同上 | 無い |
| Git | 1種類のファイルにつき1つの Role | 各サンプルの `GitRole.kt`（`.gitignore` だけ） |
| GitHub | `.github/` 配下を1つの group で宣言する | 無い |

「無い」のレシピは、定義が壊れても CI が気づかない。サンプルに入れられたら、ここの「現物」とレシピのリンクを足す。

## テーマ

**starlight-theme-nova**（`starlight-theme-nova`、ocavue）を使っている。

```js
// astro.config.mjs
import starlightThemeNova from 'starlight-theme-nova';
starlight({ plugins: [starlightThemeNova()] })
```

設定はこれだけ。`content.config.ts` は素の `docsSchema()` のままでよい。

**一度 lucode-starlight を入れて戻した。** 実際に使ってみて、次が分かったため。

- **splash テンプレート（トップページ）の本文を箱で包まない。** 本文が viewport の
  全幅に伸び、テキストが左右の端に張り付く。当て物の CSS が要った
- **本文の桁が 640px と狭い。** `CodeComparison` を 2 カラムにすると 1 カラム 312px で、
  コードがまったく読めない
- **サイドバーの長いラベルが折り返して隣の項目と重なる**

nova ではどれも起きない（本文は 792px、splash も素直に出る）。**乗り換えたときに
当て物の CSS を残さないこと。** `splash.css` と `code-comparison.css` の breakout は
どちらも lucode の欠陥に対するもので、削除済み。

## 言い換え（避ける語 → 使う語）

言い換えは `docs/prh.yml` が**唯一の置き場**。新しく見つけたらそこに足す（`expected`・`pattern`・`specs` と、理由のコメント）。
文章を書いたら `pnpm run lint:text` を回す（日本語のページと `README.ja.md`・`public/install/ja/index.md` が対象。CI でも回る）。

辞書に入れないもの: 文脈で正しくも誤りにもなる言い換え。katachi の概念としての「役割」は「Role」と書く
（一般語としての「役割」は可）。そうした判断の要るものは、次の「文章の方針」に書く。

## 文章の方針

**日本語の文章を書いたら・直したら、`yomiyasu` skill で推敲する。** ページの本文、手順書（`public/install/ja/`）、README、
スクリプトの日本語の出力、どれも同じ。AI が書いた文に出やすい癖（非生物主語、比喩的な動詞、過剰な太字・箇条書き・否定の対比など）を、
人が読みやすい文に直すため。`yomiyasu` で直したあとに、下の方針と `pnpm run lint:text` で確かめる。英語の訳は、推敲した日本語から訳す。

機械では拾えない、判断の要る決まり。**原則だけでなく理由と例を読み、似た別の場面にも当てはめる。**
利用者に文章を直されたら、置き換えで済むものは `docs/prh.yml` に、考え方にあたるものはこの節に、その場で1項目足す。
prerelease の手順 11（ドキュメントがガイドのルールに沿っているか）は、この節も照合の対象にする。

- **ガイドのルールを守った見本だけを書く。** コード例は利用者がそのまま写す。ルールに反する例は、悪い形を広める。
  - 🆖 1つの Role に interface と実装を入れた例（`"Repository".role { layout { "*Repository".ktFile(); "*RepositoryImpl".ktFile() } }`）
  - 🆗 `"RepositoryInterface"` と `"RepositoryImplementation"` に分けた例（Role のガイドの「1つの Role には1種類のファイルだけを入れる」）
- **廃止予定の書き方は例に使わない。** `.module { }` は廃止する予定で、API は残っているが、例に使うと利用者がその形を写して、あとで書き直すことになる。ドキュメント・サンプル・リポジトリ自身の定義のどこにも書かない。
  - 🆖 `":core:domain".module { mainSourceSet / kotlin / modulePackage / "useCase" / "*UseCase".ktFile() }`
  - 🆗 `"core/domain" / mainSourceSet / kotlin / "com/example/core/domain/useCase" / "*UseCase".ktFile()`（`modulePackage` や `wildcard()` も `.module { }` の中でしか使えないので書かない。モジュールごとに名前を変えたいときは、ディレクトリの `*` に `capture()` で名前を付ける）
- **Role の宣言は `"名前".role { }` で書く。** v0.2 までの `"名前" { }` は v0.3 からコンパイルエラーになるので、例に書かない。`.role` を付けるのは Role の宣言だけで、`layout { }` の中のディレクトリの宣言 `"src" { }` はそのまま。
  - 🆖 `"UseCase" { layout { "useCase" / "*UseCase".ktFile() } }`
  - 🆗 `"UseCase".role { layout { "useCase" / "*UseCase".ktFile() } }`（`layout { }` の中で `"useCase" { "*UseCase".ktFile() }` と書くのはディレクトリの宣言なので、`.role` を付けない）
- **katachi の概念は用語で書く。** 読み手がガイドの説明と結びつけられるように。
  - 🆖 「役割はたとえば次のようになるでしょう」 → 🆗 「Role はたとえば次のようになるでしょう」
  - 一般語の「役割」（「固有の役割を持つファイル」）はそのままでよい。初めて出すところは「役割（Role）」と添えてもよい
- **「効く」と書かず、何が起きるかを書く。** 「効く」は、値が渡るのか・検査されるのか・設定が使われるのかを言わない。`docs/prh.yml` が検出するので、出たら文脈に合わせて言い換える（自動修正はしない）。
  - 🆖 「`--arg name=User` の1回で両方に効きます」 → 🆗 「`--arg name=User` の1回で両方に値が渡ります」
  - 🆖 「`onExisting` は組全体に効きます」 → 🆗 「`onExisting` の値が組のすべてのテンプレートに使われます」
  - 🆖 「プロジェクト全体に一律で効かせたいなら detekt」 → 🆗 「プロジェクト全体に一律で規則をかけたいなら detekt」
- **言い切れないことは、推奨として書く。** katachi は使い方を強制しない。できることと、おすすめの使い方を分ける。
  - 🆖 「いいえ、併用します。」 → 🆗 「いいえ、併用するのがおすすめの使い方です。」
- **指示語ではなく、指すものを書く。** 節を移したり消したりすると、「これら」「この例」「上の例」が指す先が変わって宙に浮く。
  - 🆖 「これらは deny by default の逃げ道です」 → 🆗 「`anyFile()` と `ignore()` は…deny by default の逃げ道です」
  - 🆖 「サブディレクトリは許されません」（前の例の話なのに一般則に読める） → 🆗 「この例ではサブディレクトリは許されません」
- **何と何の話かを、最初の一文で言う。**
  - 🆖 「見ている範囲が違います。」 → 🆗 「katachi と Konsist・ArchUnit では、見ている範囲が違います。」
- **利用者が最初に知らなくてよい細部は書かない。** 例外の出る時点、エラーの細かい条件、内部の仕組み、出力の読み方、
  同じことの言い換えや重複した補足は、ページを長くするだけで、読み手の判断を助けない。迷ったら書かず、要るなら API リファレンス（KDoc）に任せる。
  - 例: テンプレートのガイドから、`id`・ループで id を作る・`wildcard()`・名前の付け方の細則・`Captures` の出力の読み方を削った
  - 例: 「初めてのアーキテクチャ定義」の手順5から、`gradle()` で宣言できる Gradle のファイルの説明（と Gradle のレシピへの案内）と、baseline で既存の違反を許容できるという案内を削った。手順の途中で別のページへ誘わない
  - 「エラーメッセージの読み方はドキュメント化しない」（上の「決まっている判断」）も同じ考え方
- **コード例で分かる設定を、本文で説明し直さない。** 写せば動く設定の理由は、コード例の中のコメント1行で足りる。本文で1行ずつ説明し直すと、手順のページが説明で長くなり、写す手が止まる。
  - 例: 「初めてのアーキテクチャ定義」の手順2から、`outputs.upToDateWhen { false }`・`testLogging { … }`・`katachi { architecture = … }` を1項目ずつ説明した箇条書きと、「Android プロジェクトの場合」の Aside を削った
- **推奨の書き方を先に見せる。** 同じことを2通り書けるなら、コードブロックには推奨の形を置き、もう一方は一文で添える。
  - 🆗 コードブロックは `"build".ignore()`、文で「`"build" { ignore() }` とブロックの中に書くこともできます」
- **コード例のハイライトは、読んでほしい行を指す。** 例を直したら、ハイライトの行番号も直す。空行・閉じかっこ・外枠（`architecture {` や Role の開き）は指さない。
  - 例外: Role を分けたことを見せる例（🆗 の `"RepositoryInterface"` と `"RepositoryImplementation"` など）では、Role の開きを指してよい。分けたこと自体が読んでほしい点なので
- **読み飛ばされては困る注意だけを Aside にする。読まなくても先に進める補足は `<details>` に畳んでよい。** 補足を Aside に入れると、注意と見分けがつかず本文が細切れになる。一文で済むなら地の文に書く。
  - 🆖 「katachi は detekt との統合を持っていません。…」を Aside に → 🆗 「katachi は detekt との統合を持っていませんが、役割分担としては問題なく機能します。」と地の文に
  - 🆖 サンプルやスライドへの案内を Aside に → 🆗 地の文と LinkCard に
  - 🆗 `anyFile()` / `ignore()` は deny by default の逃げ道、という注意は Aside（caution）に
  - 🆗 Role のガイドの「説明を書く」「置き場所を宣言する」のような、読まなくても先へ進める詳しい説明は `<details>` に
- **挙動を比喩で言わず、何が起きるかを書く。** 「〜側へ倒れる」は日本語として引っかかり、何が起きるのかも言わない。
  「テストが通る」「検査を通ってしまう」のような「通る」も、検査の結果の話としては不自然に読まれる。何が許容され、何が違反になるのかを書く。
  - 🆖 「抜けたときに、通ってしまう側へ倒れます」 → 🆖 「抜けたものは、気づかないうちに通ってしまいます」 → 🆗 「抜けたものがあると、本来望ましくない状態を気づかないうちに許容してしまいます」
  - 🆖 「抜けたものは「通らない」ので、その場で止まります」 → 🆗 「抜けたものは違反になるので、その場で気づけます」
  - 🆖 「ドキュメントを別に書くと腐ります」 → 🆗 「実装を変えたときにドキュメントの更新が漏れて、内容が実装と食い違っていきます」
- **検査やテストの結果を「落ちる」「落とす」と言わない。** 「落ちる」は何が起きるのかを言わず、読み手によっては、どこかへ落下する、抜け落ちるとも取れる。テストなら「失敗する」、宣言やファイルが検査に引っかかるなら「違反になる」と書く。`docs/prh.yml` で検出する。
  - 🆖 「テストが落ちます」 → 🆗 「テストが失敗します」
  - 🆖 「記録した違反はテストを落としません」 → 🆗 「記録した違反はテストを失敗させません」
  - 🆖 「宣言が落ちる: 当たる行が無い」「Bar.kt 落ちる」 → 🆗 「違反: 当たる行が無い」「Bar.kt 違反」
- **表にするのは、複数のものを複数の観点で比べるときだけ。** 1 行しかない表や、見出しの直後に結論を 1 つ置くだけの表は、読む手間が増えるだけで本文の一文と同じことしか言わない。
  - 🆖 節の頭に「併用 | 🆗 可能」の 1 行の表 → 🆗 本文で「katachi は detekt との統合を持っていませんが、役割分担としては問題なく機能します。」
- **2 つのものを比べる節は、最初に比べる軸を言ってから、片方ずつ説明する。** 片方の説明が長く続くと、何と何を比べている節なのかが分からなくなる。
  - 🆖 Konsist の説明を長く続けてから katachi の話に移る → 🆗 「katachi と Konsist・ArchUnit では、検証する範囲が違います。」と言ってから「まず Konsist」「次に katachi」と書く

## Documentation

Full documentation: https://docs.astro.build

Consult these guides before working on related tasks:

- [Adding pages, dynamic routes, or middleware](https://docs.astro.build/en/guides/routing/)
- [Working with Astro components](https://docs.astro.build/en/basics/astro-components/)
- [Using React, Vue, Svelte, or other framework components](https://docs.astro.build/en/guides/framework-components/)
- [Adding or managing content](https://docs.astro.build/en/guides/content-collections/)
- [Adding styles or using Tailwind](https://docs.astro.build/en/guides/styling/)
- [Supporting multiple languages](https://docs.astro.build/en/guides/internationalization/)
