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
    - 初めてのアーキテクチャ定義        （雑なインストール込み）
    - FAQ                              （思想・考え方の FAQ。ハウツーは書かない）
- インストール                          （エッジケース込みの詳細）
- コンセプト
    - Deny by default
    - Role                             （group と「役割を持たないファイルは存在しない」を含む）
    - Layout システムでディレクトリ構成を厳守させる  ［サイドバーのラベルは `Layout`］
- ガイド
    - Role 定義を分割する
    - Konsist Integration
    - Processor とそのカスタマイズ
- レシピ
    - Android architecture guide に従った3層アーキテクチャ
    - feature モジュール分割
    - KMP の sourceSet と expect/actual
    - Gradle 周辺（buildSrc / convention plugin / version catalog）
    - Ktor のサーバサイドプロジェクト
    - KSP プロセッサ
    - Kotlin コンパイラプラグイン
    - IntelliJ プラグイン
- API リファレンス（/api-docs/）
- ロードマップ
```

決まっている判断:

- **インストールは二段構え。** 「初めてのアーキテクチャ定義」で雑な手順を示し、
  「インストール」ページでエッジケース込みの詳細を扱う
- **エラーメッセージの読み方はドキュメント化しない。** メッセージそのものを読めば対処が
  分かるべきで、読み方のガイドが要る時点でメッセージ側の敗北。ドキュメントは必ずズレる
- **既存プロジェクトへの段階導入は当面入れない。** 入れるならインストールページに
- **`processor` はコンセプトに出さない。** 内部的な設計の話で、ほとんどの利用者は
  `assert()` の1行しか触らない。コンセプトに置くと「理解しないと使えない」と言ってしまう。
  ガイドのページが自分で短い前提（2〜3行）を持つ
- **ページタイトルとサイドバーのラベルは分けられる**（Starlight の `sidebar.label`）
- **存在しないページにリンクするとビルドが落ちる。** 書けるページから順に出す

インストールページに入れるエッジケース（分かっているもの）:

- **AGP プロジェクトでは `:architecture-test` の Kotlin プラグインにバージョンを書けない。**
  ルートで `alias(libs.plugins.kotlinJvm) apply false` が要る
  （無いと `already on the classpath with an unknown version`）
- version catalog を使わない場合の書き方
- `files = gitTracked()` が効く条件（`git rev-parse --is-inside-work-tree`。
  プロジェクトルートに `.git` が無くてもよい）
- Gradle のルートがリポジトリのルートと違う場合（モノレポ、サブモジュール）

## レシピ

**レシピはサンプルから引用してリンクする。** レシピはまるごと1つの定義なので腐るが、
`docs/` はビルドの検査対象外なので**腐っても誰も気づかない**。一方 `sample/{jvm,android,kmp}` は
CI で毎回回っている。解説 + サンプルの該当箇所へのリンク、という形にすれば、
定義が壊れたときに CI が落ちる。

**サンプルに無いレシピを書くなら、サンプルを先に増やすほうが安全。**

### 現物があるもの（優先）

| レシピ | 教える語彙 | 現物 |
|---|---|---|
| Android architecture guide の3層 | 層による分割 | `sample/android` |
| **feature モジュール分割** | `.module { }` とワイルドカード捕捉。「繰り返される構造」 | `sample/kmp`（`feature/Screen` ほか） |
| **KMP の sourceSet と expect/actual** | `sourceSet`、1つの役割が複数の置き場所を持つ | `sample/kmp`（`data/PlatformImplementation`） |
| **Gradle 周辺**（`buildSrc` / convention plugin / version catalog） | `documented = false`、`":".module { }` | 3サンプルの `build` group |
| Ktor / サーバサイド JVM | （語彙は3層と同じ。立ち位置の証明） | `sample/jvm` |

### プラグイン・ツール系（tbsten の skill 集と対になるもの）

skill の `scaffold.sh` が雛形を作り、**katachi がその形を崩れないように固定する**。
同じ仕事の前半と後半。将来 skill 側に katachi の定義を同梱する。

アプリのレシピと性質が違う点: アプリで katachi が防ぐのは主に「ファイルが変なところにある」
ことだが、**プラグイン系では「必須のファイルが無い → 黙って動かない」を防げる**。
`META-INF/services/` の登録、`plugin.xml`、`ksp/Provider.kt` — どれも欠けても
コンパイルは通り、気づくのは「なぜか動かない」と数時間溶かしたあと。`MissingFile` が
一番効く場面。

| レシピ | 元 skill | 構成 | 新しく教える語彙 |
|---|---|---|---|
| **KSP プロセッサ** | `ksp-plugin-setup.ja.md` | `<name>-runtime`（KMP 全ターゲット・アノテーションのみ）/ `<name>-ksp`（JVM only）/ `test/`（KMP 統合）/ `buildLogic/`（included build）。`ksp/` 直下に `SymbolProcessor.kt` `Provider.kt` `ProcessContext.kt`、`feature/<name>/` `core/` `options/` `util/` | 1リポジトリに複数 artifact があり**ターゲットが違う**構成。必須ファイル3つ |
| **Kotlin コンパイラプラグイン** | `kotlin-compiler-plugin-setup.ja.md` | `buildSrc/` / `compiler-plugin/` / `gradle-plugin/` / `runtime/` / `integration-test/test-jvm/` / `integration-test/test-kmp/`。`META-INF/services/` に `CommandLineProcessor` と `CompilerPluginRegistrar` の登録 | **ネストしたモジュール階層**（`integration-test/test-jvm`）と、**`src/main/resources/` 側のレイアウト**。いまのサンプルはリソースの規約を持っていない |
| **IntelliJ プラグイン** | `intellij-plugin-dev.ja.md` | `src/main/kotlin/` / `src/test/kotlin/` / **`src/preview/kotlin/`**（`PreviewMain.kt` `PreviewChecks.kt`）/ `resources/META-INF/plugin.xml` / `snapshots/preview/`（VRT golden）/ `:icons` | **`main` / `test` 以外の source set**。golden ディレクトリの宣言（v0.3 の「生成物自身も layout に宣言が要る」と同じ話） |

**正直に書くべき限界**: KSP のスキルは `feature → core → util` の一方向依存を Konsist で
強制している。**これは katachi の layout では表せない**（ディレクトリではなく import の話）。
ステップ4 の `konsist { }` の担当になる。つまりこのレシピは
**「layout で配置を固定し、konsist で依存の向きを固定する」を組み合わせる初めての例**になる。

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
