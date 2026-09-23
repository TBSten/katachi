## Development

When starting the dev server, use background mode:

```
astro dev --background
```

Manage the background server with `astro dev stop`, `astro dev status`, and `astro dev logs`.

## サイドバーの構成

`index`（トップページ）は**サイドバーに出さない**。サイトタイトル / ロゴから辿れるので十分。

**ドキュメントの本文は人が書く。** エージェントが用意してよいのは、サイドバーの配線と
空ページ（frontmatter だけ）まで。

```
- はじめる
    - モチベーションと katachi の立ち位置
    - 初めてのアーキテクチャ定義        （インストール込み。専用ページは作らない）
    - FAQ                              （思想・考え方の FAQ。ハウツーは書かない）
    - 他ツールとの比較                  （Konsist / detekt / ArchUnit などとの守備範囲）
- ガイド                                （もとはコンセプトとガイドの2節。1つにまとめた）
    - 基本的な API                      （覚える6つ。この節の入口）
    - Role                             （group と「Role 定義を分割する」を含む）
    - Layout システムでディレクトリ構成を厳守させる  ［サイドバーのラベルは `Layout`］
    - Konsist との統合
    - Processor とそのカスタマイズ
- レシピ
    - 一覧                              （`/recipes/`。カードはビルド時に自動収集する）
    - Android architecture guide に従った3層アーキテクチャ
    - Gradle 周辺（buildSrc / convention plugin / version catalog）
    - Ktor のサーバサイドプロジェクト
    - AI Agent
    - ktlint
    - detekt
    - Git
    - GitHub
- API リファレンス（/api-docs/）
- ロードマップ
```

決まっている判断:

- **インストール専用ページは作らない。** 「初めてのアーキテクチャ定義」が手順を全部持つ。
  二段構えにしていた時期があるが、書き分けるほどの中身が無く、下のエッジケースも
  そちらへ入れる
- **エラーメッセージの読み方はドキュメント化しない。** メッセージそのものを読めば対処が
  分かるべきで、読み方のガイドが要る時点でメッセージ側の敗北。ドキュメントは必ずズレる
- **既存プロジェクトへの段階導入は当面入れない。** 入れるなら「初めてのアーキテクチャ定義」に
- **`processor` は前面に出さない。** 内部的な設計の話で、ほとんどの利用者は
  `assert()` の1行しか触らない。目立つ位置に置くと「理解しないと使えない」と言ってしまう。
  ガイドの末尾に置き、ページが自分で短い前提（2〜3行）を持つ
- **ページタイトルとサイドバーのラベルは分けられる**（Starlight の `sidebar.label`）
- **存在しないページにリンクするとビルドが落ちる。** 書けるページから順に出す
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
| `katachi-install.sh` | 機械的にできる工程の**唯一の実装**。`init` と `scaffold` の2サブコマンド |
| `install-check-list.html` | 進捗と結果のチェックリスト。`init` が配置し、`data-fill` 属性の欄を `sed` で埋める |
| `project-code-base-report-template.html` | コードベース解析レポートのテンプレート。`init` が配置する |

守ること:

- **手順を `index.md` に足す前に、スクリプトでできないかを考える。** 「エージェントがよしなに」は
  環境ごとにブレる。判断が要らない工程は `katachi-install.sh` に入れ、`index.md` は
  コマンドを1行示すだけにする
- **生成する内容を2箇所に書かない。** モジュールの雛形はスクリプトの中だけにある
- `install-check-list.html` の `<dd data-fill="...">...</dd>` は**1行に収める。**
  スクリプトが `sed` の行単位の置換で埋めている
- スクリプトを直したら、`sh -n` に加えて**実際の Gradle プロジェクトで動かす。**
  最低限「AGP + version catalog」「ルート build ファイル無し」「Kotlin JVM が既にルートに居る」の3つ
- 配信元は `KATACHI_DOCS` 環境変数で差し替えられる。dev server に向けて試せる

## レシピ

**レシピはサンプルから引用してリンクする。** レシピはまるごと1つの定義なので腐るが、
`docs/` はビルドの検査対象外なので**腐っても誰も気づかない**。一方 `sample/{jvm,android,kmp}` は
CI で毎回回っている。解説 + サンプルの該当箇所へのリンク、という形にすれば、
定義が壊れたときに CI が落ちる。

**サンプルに無いレシピを書くなら、サンプルを先に増やすほうが安全。**

### レシピの一覧

| レシピ | 教える語彙 | 現物 |
|---|---|---|
| Android architecture guide の3層 | 層による分割 | `sample/android` |
| **Gradle 周辺**（`buildSrc` / convention plugin / version catalog） | `documented = false`、`":".module { }` | 3サンプルの `build` group |
| Ktor / サーバサイド JVM | （語彙は3層と同じ。立ち位置の証明） | `sample/jvm` |

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

## Documentation

Full documentation: https://docs.astro.build

Consult these guides before working on related tasks:

- [Adding pages, dynamic routes, or middleware](https://docs.astro.build/en/guides/routing/)
- [Working with Astro components](https://docs.astro.build/en/basics/astro-components/)
- [Using React, Vue, Svelte, or other framework components](https://docs.astro.build/en/guides/framework-components/)
- [Adding or managing content](https://docs.astro.build/en/guides/content-collections/)
- [Adding styles or using Tailwind](https://docs.astro.build/en/guides/styling/)
- [Supporting multiple languages](https://docs.astro.build/en/guides/internationalization/)
