# Kotlin コード規約

## 可視性

ライブラリのモジュール内の可視性は以下のように管理している。都度これに従って最小限度の visibility を丁寧に検討する。

| 可視性                      | 説明                                                                                              |
|-----------------------------|---------------------------------------------------------------------------------------------------|
| private                     | そのファイル・宣言内で private なもの。                                                           |
| internal                    | そのモジュール内でのみ有効なもの。                                                                |
| public * InternalKatachiApi | ライブラリの他モジュールで利用するために public にしているが、ユーザには触って欲しくない。        |
| public                      | ユーザが触ることのできる public な API。 **explicitApi を指定しているため、省略してはいけない。** |

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
 * ## Example 1: <例1のタイトル, public 宣言では1つ以上>
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
