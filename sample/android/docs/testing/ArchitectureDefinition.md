[katachi-sample-android](../README.md) / [テスト](README.md)

# アーキテクチャ定義

katachi の DSL で書かれた役割の定義。どのレイヤーにも属さない

katachi の DSL で書かれた、この定義そのもの。`:architecture-test` という、
アプリのどのレイヤーにも属さない素の `kotlin("jvm")` モジュールに置く。
Android のモジュールではないのは、katachi が JVM のライブラリで、
プロジェクトの種別によらず同じ形にできるから。

1宣言1ファイルで、ファイル名がその種類を表す。`ProjectArchitecture.kt` が入口で
group の関数を呼ぶだけ、`groups/<Name>Group.kt` が group を1つ、
`roles/<Name>Role.kt` が役割を1つ。`groups/` と `roles/` には `*Group.kt` `*Role.kt` しか
置けないので、共有のヘルパーが3つ目の種類として紛れ込むと `[UnexpectedFile]` になる。
唯一の例外が `DocumentSections.kt` で、全 group・全役割が `by` で使う節の定義だけを
1ファイルにまとめてあるので、`ProjectArchitecture.kt` と同じく名指しで許してある。

拡張関数を `inline` にしてはいけない。katachi は宣言位置をスタックトレースから取るので、
inline すると呼び出し元ファイルの、誰も書いていない行を指すようになる。
`ProjectArchitectureSpec` がその行を実際に読み戻して見張っている。

`:architecture-test` は `:app` と並ぶ、package がモジュールパスから導けない
モジュール。そのまま当てると `com/example/sample/architectureTest` になってしまうので、
この役割とテストコード役割はどちらも `com/example/sample` を直接書く。
アプリの一部ではないものに、アプリの package 規則を当てる意味がない。

テストと同じモジュールを共有しているが、両者は別のことを書いている。
定義は「どんな形をしているか」、テストは「どう振る舞うか」。区別はファイルの場所と名前でつく。

## Placement

| Module | Path | When to use |
|---|---|---|
| `:architecture-test` | `src/test/kotlin/com/example/sample/ProjectArchitecture.kt` |  |
| `:architecture-test` | `src/test/kotlin/com/example/sample/DocumentSections.kt` |  |
| `:architecture-test` | `src/test/kotlin/com/example/sample/groups/*Group.kt` |  |
| `:architecture-test` | `src/test/kotlin/com/example/sample/roles/*Role.kt` |  |

## Examples

- `ProjectArchitecture.kt` ... 定義の入口
- `roles/ScreenRole.kt` ... Screen の役割の宣言
