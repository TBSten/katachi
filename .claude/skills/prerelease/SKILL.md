---
name: prerelease
description: >-
  リリース前の準備。
---

- @./prerelease-check-list.md を .local/release-v0.0.0/prerelease-check-list.md にコピーし適宜記載していく。
    - コピーは「1. 前提チェック」の `prepare.py` が、版の欄を埋めて行う（既にあれば上書きしない）。
    - 警告すべきことがあれば memo の details tag 内に記載する。警告すべきことがないのなら雑な警告は出さないように心がける。
- 並列な subagent に以下のタスクを任せる。prerelease-check-list.md は subagent ではなくオーケストレータであるあなたが記入する。
    - subagent は終了時の報告内容に警告内容を含めさせるようにする。
- 警告は以下の形式で報告する。
    ```md
    ### [WARN / priority 10/10] {警告内容タイトル}

    {警告内容詳細}
    ```

## 1. 前提チェック

スクリプトは `.claude/skills/prerelease/scripts/` にある。リポジトリの直下で、python3（標準ライブラリだけ）で実行する。

```shell
python3 .claude/skills/prerelease/scripts/prepare.py
```

- 今回の版（`gradle/libs.versions.toml` の `katachi`）と、公開済みの直前の版（git の `vX.Y.Z` タグと Maven Central の
  maven-metadata.xml。Maven Central に届かなければタグだけ）を出す。
- 両者が同じ（または Maven Central に公開済み）なら `STOP:` を出して終了コード 1 で止まり、作業場所は作らない。
  作業を中断し、ユーザに本当にこのバージョンで publish するのか尋ねる。
    - バージョンの更新忘れである可能性が高い。
    - ユーザが「この版で進める」と言ったら `--force` を付けて作り直す。
- 止まらなければ `.local/release-v<版>/` を作り、チェックリストを写して 1 の版の欄と、作業場所の中のファイルのパスの欄（3〜6）を埋める。
  出力の `WARN:`（タグと Maven Central の食い違い）は memo に書く。
- 以降の手順の `<基準>` は、出力の「基準（直前のタグ）」。

## 2. 日本語ドキュメントを 英語ドキュメントへ翻訳同期する

- translate-ja-en skill を実行する。

## 3. 公開範囲（可視性）のチェック

ライブラリ（`:katachi` と `:katachi-konsist`）の公開 API を、次の3つに分けて **すべて**列挙し、1つずつ妥当かを判断する。
判断の基準は `docs/internal/kotlin/kotlin.md` の「可視性」の節（`.internal` パッケージの決まりを含む）。

| 区分                               | 何のためのものか                                                                                                   | 妥当でない例                                                                                                                                              |
|------------------------------------|--------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| public（注釈なし）                 | 利用者が書く・読む・実装する安定した API                                                                           | 実装の配管が見えている／別の public API の別名にすぎない（既定値を言い直すだけの関数など）／誰も使わない語彙／`Katachi` prefix の無い例外                 |
| public + `@InternalKatachiApi`     | ライブラリの他モジュール（`:katachi-konsist`、Gradle プラグインが生成するコード、`tool/dokka` など）だけが使うもの | 他モジュールから使われていない（→ `internal` にできる）／利用者向けの語彙になっている（→ `@ExperimentalKatachiApi` へ）／`.internal` パッケージの外にある |
| public + `@ExperimentalKatachiApi` | 利用者に開くが、まだ形が変わりうるもの                                                                             | 実は他モジュール専用（→ `@InternalKatachiApi`）／長く形が変わっておらず安定させてよい（→ 注釈を外す）／`.internal` パッケージの中にある                   |

- 列挙はスクリプトで行う（判断はしない。材料を出すだけ）。

  ```shell
  python3 .claude/skills/prerelease/scripts/list-public-api.py --release-dir .local/release-v<版>
  ```

    - `.local/release-v<版>/visibility-check.md` に全件を書き、チェックリストの3区分の件数の欄を埋める。標準出力には件数の表だけ。
    - 拾い方: ソースを字句走査し、`public` の付いた宣言のうち囲む型もすべて `public` のもの（両モジュールは
      `explicitApi()`）。トップレベル宣言と public な型の public メンバ。`override` は数えない。型に付いた注釈はメンバにも効くものとして区分けする。
      API リファレンスは `.internal` パッケージを外しているので、列挙の元にしない。
    - 1件ごとに出るもの: 基準（直前のタグ）からの印（`[追加]` / `[変更]` / `[移動]` / `[区分変更]`）、`file://` の絶対パス:行、
      `.internal` パッケージかどうか、名前が出てくる回数（`自` 同じモジュールの main・KDoc の例 / `他` もう片方のライブラリ・
      Gradle プラグインの Java（文字列を含む）・`tool/` / `test` / `sample` / `docs` 日本語のドキュメント）、シグネチャ。
      末尾に「基準にあって今は無い」宣言（削除・改名・非公開化の候補）。
    - **回数は目安。**名前だけで数えるので、`name` や `path` のような一般的な名前のメンバは別物も数える。
      `@InternalKatachiApi` で `他 0` のものは `internal` にできる候補だが、本当に使われていないかは subagent に grep で確かめさせる。
- 区分ごとに1体ずつ、並列な subagent に任せる。
    - 渡すもの: その区分の一覧（`list-public-api.py --only public|internal-api|experimental`。基準から変わったものだけなら `--changed`）と、上の表。
    - 1件ごとに「判断（OK / 要検討）・理由」を書かせる。宣言の場所と使われている場所はスクリプトの出力をそのまま使わせる。
    - 印の付いたもの（直前のリリースから増えた・変わった公開 API）は必ず目を通させる（差分は
      `git diff <基準> -- katachi/src/main katachi-konsist/src/main`）。
    - **直させない。**判断が要るものは警告として報告させる。
- subagent の判断を `.local/release-v0.0.0/visibility-check.md` に足す（スクリプトが書いた一覧の、各項目の下に）。
  prerelease-check-list.md には要検討の件数と、要検討の件の警告だけを書く（件数はスクリプトが埋めている）。

## 4. リリースノート作成

- 本バージョンではいるユーザ向けの影響を git, PR の履歴などから調査し、列挙する。
    - 材料はスクリプトで出す。直前のタグからのコミットを、BREAKING CHANGE（件名の `!` と本文の `BREAKING CHANGE:`。本文の説明つき）・
      Conventional Commits の type ごと・scope ごとの件数に並べ、未コミットの変更の件数も出す。

      ```shell
      python3 .claude/skills/prerelease/scripts/release-note-material.py           # --files でコミットごとのファイルも
      ```

    - どれが利用者に効くか、どうまとめるかは判断する（`refactor!` でも利用者に見えないもの、`docs` でも利用者に効くものがある）。
      未コミットの変更があれば、リリースに入らないことを警告する。
- .local/release-v0.0.0/release-note.md (0.0.0 の部分は該当リリースバージョン) にリリースノートを作成する。
    - ユーザ影響を中心に作成する。
    - 重要でない変更は Also セクションに簡略的に列挙するに留める。

## 5. 実際の実装・挙動 と ドキュメント に不整合がないかチェック

- check-docs-against-impl skill を実行する。
    - 基準には直前のリリースのタグを渡す（その SKILL の `list-api-changes.py <基準>` が材料を出す）。
    - 結果は `.local/release-v0.0.0/docs-vs-impl.md` にまとめさせる。
- prerelease-check-list.md には、件数と priority 7 以上の警告だけを書く。

## 6. ドキュメントサイトと API リファレンスを Playwright で巡回する

ビルドが通ることと、ページが正しく見えることは別。実際のブラウザで全ページを開いて確かめる。

1. ビルドして配信する（リポジトリの直下で）。

   ```shell
   ./gradlew generateApiDocs      # docs/public/api-docs を作り直す（verifyApiDocs 込み）
   cd docs && pnpm run build      # リンク検査込み。落ちたら、その内容を警告にする
   cd docs && pnpm preview        # http://localhost:4321/katachi/ で配信（background で起動し、最後に止める）
   ```

2. 巡回するページを列挙する。

   ```shell
   python3 .claude/skills/prerelease/scripts/list-site-pages.py                 # 区分ごとの件数
   python3 .claude/skills/prerelease/scripts/list-site-pages.py --group ja      # en / ja / api-docs
   ```

3. 区分（en / ja / api-docs）ごとに1体ずつ、並列な subagent（model: sonnet）に Playwright MCP で巡回させる。
   渡すもの: その区分の URL の一覧と、下の「見ること」。スクリーンショットは `.local/release-v0.0.0/screenshots/<区分>/` に置かせる。
   **ページを直させない。**見つけたものは警告として報告させる。

   見ること（ページごと）:
   - 開けるか（404 やビルドの取り残しが無いか）、コンソールのエラー・警告
   - ページ内のリンク（サイト内のもの）が開けるか。API リファレンスへのリンクも含める
   - 見た目が崩れていないか: `<Tabs>`（ラベルの折り返し）、`<FileTree>`、`<CodeComparison>`、Mermaid、表、コードブロック、`<details>`。
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
- prerelease-check-list.md には、区分ごとのページ数と見つけた問題の件数、priority 7 以上の警告だけを書く。

## 7. 報告

- それぞれの実行結果を
- 上記のステップを実行してきた中で見つけた警告をサマライズし重要なものがあればユーザに警告する。
- 形式は以下。この形式から外れないように厳密に処理する。
  ```
  ✅ リリース前チェック完了

  - チェックリスト: file://{prerelease-check-list.md の絶対パス}
  - リリースノート: file://{.local/release-v0.0.0/release-note.md の絶対パス}
  
  ---
  
  ## リリースノート概要
  
  {file://{.local/release-v0.0.0/release-note.md をサマライズして 100 文字/文, 標準3文(最大でも5文) 以内にした内容}

  ---

  ## ⚠️ 重要な警告 {重要な警告がない場合はセクションごとトルツメ}

  ### {警告内容タイトル}

  {詳細}

  ---

  ## 軽微な警告

  - xx 件
  - 詳細: file://{prerelease-check-list.md の絶対パス}

  ---
  ```

    - 重要な警告がある場合は `✅ リリース前チェック完了` -> `⚠️ リリース前チェック完了` にする。
    - 重要な警告の詳細はダラダラ書きすぎない。
