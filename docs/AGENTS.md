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
空ページ（frontmatter だけ）まで。

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
    - 基本的な API                      （覚える6つ。この節の入口）
    - Role                             （group と「Role 定義を分割する」を含む）
    - Layout                           ［ページタイトルは「Layout システムでディレクトリ構成を厳守させる」］
    - Konsist との統合
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
  分かるべきで、読み方のガイドが要る時点でメッセージ側の敗北。ドキュメントは必ずズレる
- **既存プロジェクトへの段階導入は当面入れない。** 入れるなら「初めてのアーキテクチャ定義」に
- **IDE プラグインの説明はテンプレートのガイドの節（「IDE から生成する」）に置く。専用ページは作らない。**
  プラグインの機能はテンプレート生成だけで、入れ方も3手順で済む。機能が増えてガイドの節に
  収まらなくなったら、ガイドの末尾にページを分ける
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
- **節の索引ページは `<SectionIndex dir="..." />` で出す。** ページの集合は content
  collection から、並び順はサイドバーから採る。カードを手で並べない

「初めてのアーキテクチャ定義」に入れるエッジケース（分かっているもの）:

- **AGP プロジェクトでは `:architecture-test` の Kotlin プラグインにバージョンを書けない。**
  ルートで `alias(libs.plugins.kotlinJvm) apply false` が要る
  （無いと `already on the classpath with an unknown version`）
- version catalog を使わない場合の書き方
- `files = gitTracked()` が効く条件（`git rev-parse --is-inside-work-tree`。
  プロジェクトルートに `.git` が無くてもよい）
- Gradle のルートがリポジトリのルートと違う場合（モノレポ、サブモジュール）

## `public/install/` — AI エージェント向けの配布物

ドキュメントサイトのページではなく、**AI エージェントが `curl` で取りに来る素材**を置く場所。
Astro は `public/` を加工せずそのままコピーするので、`<base-url>/install/...` で生のまま配信される。

| ファイル | 役割 |
|---|---|
| `index.md` | インストール手順そのもの。エージェントはこれを読んで動く |
| `katachi-install.sh` | 機械的にできる工程の**唯一の実装**。`init`・`doctor`・`scaffold` のほか、チェックリストとレポートを読み書きする `data`・`check`・`add`・`verify`・`summary` など（一覧は `--help`）。出力の言語は `init --lang` に従う |
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
- 配信元は `KATACHI_DOCS` 環境変数で差し替えられる。dev server に向けて試せる。init に渡すと作業用ディレクトリに記録され、
  以降のコマンドもそこから取る。`docs` サブコマンドが取る `llms-full.txt` はビルドで作られるので、`docs/public` ではなく
  ビルド済みのサイト（`docs/dist` か配信 URL）を指す

## レシピ

**レシピはサンプルから引用してリンクする。** レシピはまるごと1つの定義なので腐るが、
`docs/` はビルドの検査対象外なので**腐っても誰も気づかない**。一方 `sample/{jvm,android,kmp}` は
CI で毎回回っている。解説 + サンプルの該当箇所へのリンク、という形にすれば、
定義が壊れたときに CI が落ちる。

**サンプルに無いレシピを書くなら、サンプルを先に増やすほうが安全。**

### レシピの一覧

| レシピ | 教える語彙 | 現物 |
|---|---|---|
| Android の3層アーキテクチャ | 層による分割、`konsist { }` での命名と中身の規約 | `sample/android`（モジュールを分けた構成なので、レシピそのものではない） |
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
  - 🆖 1つの Role に interface と実装を入れた例（`"Repository" { layout { "*Repository".ktFile(); "*RepositoryImpl".ktFile() } }`）
  - 🆗 `"RepositoryInterface"` と `"RepositoryImplementation"` に分けた例（Role のガイドの「1つの Role には1種類のファイルだけを入れる」）
- **廃止予定の書き方は例に使わない。** `.module { }` は廃止する予定で、API は残っているが、例に使うと利用者がその形を写して、あとで書き直すことになる。ドキュメント・サンプル・リポジトリ自身の定義のどこにも書かない。
  - 🆖 `":core:domain".module { mainSourceSet / kotlin / modulePackage / "useCase" / "*UseCase".ktFile() }`
  - 🆗 `"core/domain" / mainSourceSet / kotlin / "com/example/core/domain/useCase" / "*UseCase".ktFile()`（`modulePackage` や `wildcard()` も `.module { }` の中でしか使えないので書かない。モジュールごとに名前を変えたいときは、ディレクトリの `*` に `capture()` で名前を付ける）
- **katachi の概念は用語で書く。** 読み手がガイドの説明と結びつけられるように。
  - 🆖 「役割はたとえば次のようになるでしょう」 → 🆗 「Role はたとえば次のようになるでしょう」
  - 一般語の「役割」（「固有の役割を持つファイル」）はそのままでよい。初めて出すところは「役割（Role）」と添えてもよい
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
  - 「エラーメッセージの読み方はドキュメント化しない」（上の「決まっている判断」）も同じ考え方
- **推奨の書き方を先に見せる。** 同じことを2通り書けるなら、コードブロックには推奨の形を置き、もう一方は一文で添える。
  - 🆗 コードブロックは `"build".ignore()`、文で「`"build" { ignore() }` とブロックの中に書くこともできます」
- **コード例のハイライトは、読んでほしい行を指す。** 例を直したら、ハイライトの行番号も直す。空行・閉じかっこ・外枠（`architecture {` や Role の開き）は指さない。
  - 例外: Role を分けたことを見せる例（🆗 の `"RepositoryInterface"` と `"RepositoryImplementation"` など）では、Role の開きを指してよい。分けたこと自体が読んでほしい点なので
- **読み飛ばされては困る注意だけを Aside にする。読まなくても先に進める補足は `<details>` に畳んでよい。** 補足を Aside に入れると、注意と見分けがつかず本文が細切れになる。一文で済むなら地の文に書く。
  - 🆖 「katachi は detekt との統合を持っていません。…」を Aside に → 🆗 「katachi は detekt との統合を持っていませんが、役割分担としては問題なく機能します。」と地の文に
  - 🆖 サンプルやスライドへの案内を Aside に → 🆗 地の文と LinkCard に
  - 🆗 `anyFile()` / `ignore()` は deny by default の逃げ道、という注意は Aside（caution）に
  - 🆗 Role のガイドの「説明を書く」「置き場所を宣言する」のような、読まなくても先へ進める詳しい説明は `<details>` に

## Documentation

Full documentation: https://docs.astro.build

Consult these guides before working on related tasks:

- [Adding pages, dynamic routes, or middleware](https://docs.astro.build/en/guides/routing/)
- [Working with Astro components](https://docs.astro.build/en/basics/astro-components/)
- [Using React, Vue, Svelte, or other framework components](https://docs.astro.build/en/guides/framework-components/)
- [Adding or managing content](https://docs.astro.build/en/guides/content-collections/)
- [Adding styles or using Tailwind](https://docs.astro.build/en/guides/styling/)
- [Supporting multiple languages](https://docs.astro.build/en/guides/internationalization/)
