# Kotlin コード規約

## 可視性

ライブラリのモジュール内の可視性は以下のように管理している。都度これに従って最小限度の visibility を丁寧に検討する。

| 可視性                          | 説明                                                                                                                                                                                        |
|---------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| private                         | そのファイル・宣言内で private なもの。                                                                                                                                                     |
| internal                        | そのモジュール内でのみ有効なもの。他モジュールから使われていないものは、`@InternalKatachiApi` を付けて public にせずこちらにする。トップレベル宣言は `.internal` パッケージに置く（下記）。 |
| public * InternalKatachiApi     | ライブラリの他モジュール（katachi-konsist、Gradle プラグインの生成コード、tool/dokka、リポジトリ内の architecture-test・サンプル）専用。ユーザには触って欲しくない。                        |
| public * ExperimentalKatachiApi | ユーザに開いているが、形がまだ変わる API。ユーザが自前で katachi を拡張するためのもの（自前の `.module { }` を書くための `expandModulePath` など）もここ。                                  |
| public                          | ユーザが触ることのできる public な API。 **explicitApi を指定しているため、省略してはいけない。**                                                                                           |

### 内部実装は `.internal` パッケージに置く

トップレベルの `internal` 宣言と `@InternalKatachiApi` の宣言は、そのパッケージの `internal` サブパッケージに置く。

| 元のパッケージ                                                        | 置き場所                             |
|-----------------------------------------------------------------------|--------------------------------------|
| `me.tbsten.katachi`（ルート）                                         | `me.tbsten.katachi.internal`         |
| `me.tbsten.katachi.<層>`（`dsl.gradle` のような下位パッケージも同じ） | `me.tbsten.katachi.<層>.internal`    |
| `me.tbsten.katachi.konsist`（`:katachi-konsist`）                     | `me.tbsten.katachi.konsist.internal` |

- `.internal` パッケージには `internal` / `@InternalKatachiApi` / `private` のトップレベル宣言だけを置く。利用者に見せる
  public（`@ExperimentalKatachiApi` を含む）は置かない。
- 対象は **トップレベル宣言だけ**。public な型のメンバ（`internal constructor`、`internal val` など）は型と一緒に元のパッケージに残る。
- `private` はファイルの中に閉じているので、使う側と同じファイルに置く。public な側と `.internal` の側の両方から使う
  `private` は `internal` にして `.internal` に移す。
- `@PublishedApi internal` も `.internal` に置く。inline 関数から参照されるので ABI の一部になる点に注意する。
- **例外**: public な sealed interface の直接の実装（`ArchitectureScopeImpl`、`LayoutScopeImpl` など）は、Kotlin の規則で
  sealed と同じパッケージにしか置けないので、元のパッケージに `internal` のまま置く。
- 層は `.internal` を含めて元のパッケージと同じとして扱う（`dsl.internal` は `dsl` 層）。
- architecture-test の各層の役割が `INTERNAL_PACKAGE_RULE` で検査している。
- `.internal` パッケージは Dokka の API リファレンスに出さない（
  `buildSrc/src/main/kotlin/katachi-kotlin-library.gradle.kts` の `perPackageOption`）。

## コメント

コメントは全て英語で記載してください。

### public 宣言

- public (InternalKatachiApi も含む) な宣言には必ず KDoc を以下の形式で記載してください。

`````kt
/**
 * <簡単な説明。150 文字以内。>
 * 
 * <より詳細な説明。オプション>
 *     
 * ## <説明用トピックのタイトル。50文字以内。オプション>
 *     
 * <説明用トピックの説明>
 *     
 * ## Example 1: <例1のタイトル。型・トップレベル関数には1つ以上。メンバは下の条件を満たすときだけ>
 *     
 * ```kt
 * architecture {
 *   "..." {
 *     // Role 内
 *     mainSourceSet / kotlin / modulePackage / "useCase" / "*UseCase".ktFile()
 * 
 *     konsist { 
 *       "外から使えること".konsist {
 *         classes().must { it.hasPublicOrDefaultModifier }
 *       }
 *     }
 *   }
 * }
 * ```
 * 
 * @param <オプション>
 * @receiver <オプション>
 * @throws <オプション>
 * @return <オプション>
 * @see <オプションだが、関連するクラスが1つでもあるならそれをリンクすることを強く推奨>
 */
context(scope: ConstraintScope)
public fun String.konsist(block: KonsistScope.() -> Unit) {
    /* ... */
}
`````

#### `## Example` を書く単位

**型（interface / class / object）とトップレベル関数には1つ以上。メンバには原則書かない。**

型の例はたいてい **メンバの使われ方も一緒に見せている**。そこにメンバごとの例を足すと、
ほぼ同じコードが宣言の数だけ並ぶ。実際に、メンバ9個の interface で
コメントが全体の 86% を占める状態になった。

メンバに `## Example` を書くのは、 **型の例からは読み取れないとき**だけ:

- 呼び出しの形が型の例と違う（別のオーバーロード、別のレシーバ）
- 契約が自明でない（「これを呼ぶと走査が起きる」のような、シグネチャに出ない性質）

#### 「なぜ」は1箇所にだけ書く

設計の経緯・却下した代替案・歴史的な理由は、 **型の KDoc に1回**書く。
メンバごとに繰り返さない。 **2段落を超えるなら `docs/internal/` か `.local/design-draft/` の
設計メモに置き、KDoc からはそこを指す。**

KDoc は「これをどう使うか」を読む場所で、「なぜこの設計にしたか」を読む場所ではない。

#### 例外: `override`

- `override` な宣言には `## Example` を求めない。ドキュメントは override 元から引き継がれるし、
  `equals` / `hashCode` / `toString` に例を書いても Kotlin の説明にしかならない。

### public 以外

- private, internal の宣言についてはむしろコメントをしっかり書きすぎないようにする。
- 本当に背景情報が必要なコードにのみ追加して、無尽蔵にコメントを増やしすぎない。
- **見るのは修飾子ではなく到達可能性。** `internal class ...Impl` のメンバに `public` が付いているのは
  interface の実装として修飾子を狭められないからであって、外から触れるからではない。
  外側の型が internal / private なら、その中の `public` メンバもここで言う「public 以外」に当たる。

### KDoc 以外のコメント

- インラインで expression などに入れるコメントには「何をしているか」ではなく「なぜこうしているか」を書く。

## `as` の禁止

`as` 演算子は利用しないこと。想定している型でなかった場合には `as? throw ...` のように専用のエラーを用意して
適切なエラーメッセージが表示されるようにすることを心がける。

## 複数行文字列

複数行文字列は ```"""...""".trimIndent()``` を使用する。`"...\n" + "...\n" + ...` のように `\n`
と文字列連結を使う記述方法は可読性を著しく低下させるため使用しない。
