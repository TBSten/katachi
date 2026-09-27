# 3. 公開範囲（可視性）のチェック

[SKILL.md](../SKILL.md) の手順 3 の詳細。

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
      `@InternalKatachiApi` で `他 0` のものは `internal` にできる候補だが、本当に使われていないかは subagent に grep
      で確かめさせる。
- 区分ごとに1体ずつ、並列な subagent に任せる。
    - 渡すもの: その区分の一覧（`list-public-api.py --only public|internal-api|experimental`。基準から変わったものだけなら
      `--changed`）と、上の表。区分ごとの一覧をファイルに書くなら `.local/release-v<版>/tmp/visibility-<区分>.md` に置く。
    - 1件ごとに「判断（OK / 要検討）・理由」を書かせる。宣言の場所と使われている場所はスクリプトの出力をそのまま使わせる。
      subagent に下書きのファイル（`part-*.md` など）を書かせるなら `tmp/` の下に置かせる。
    - 印の付いたもの（直前のリリースから増えた・変わった公開 API）は必ず目を通させる（差分は
      `git diff <基準> -- katachi/src/main katachi-konsist/src/main`）。
    - **直させない。**判断が要るものは警告として報告させる。
- subagent の判断を `.local/release-v0.0.0/visibility-check.md` に足す（スクリプトが書いた一覧の、各項目の下に）。
  prerelease-check-list.md には要検討の件数と、要検討の件の警告だけを書く（件数はスクリプトが埋めている）。
