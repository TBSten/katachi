[katachi-sample-kmp](../README.md) / [テスト支援](README.md)

# アーキテクチャ定義

:architecture-test モジュールの src/test。katachi の DSL で書いたこのプロジェクトの定義。どのレイヤーにも属さないので専用モジュールに置く

katachi でこのプロジェクトの形を書いたコードです。いま読んでいるファイルもこの役割に
属します。テストコードとは分けてあります。定義の仕事はプロジェクトを説明することで、
それを実際のディレクトリと突き合わせるテスト（`ProjectLayoutSpec`）は1行しかありません。

専用の `:architecture-test` モジュールに置いているのは KMP だからです。katachi は JVM の
ライブラリで、このビルドの他のモジュールは Android と iOS だけを持つ KMP なので、
定義を置ける `commonTest` がどこにもありません。1つだけ `kotlin("jvm")` のモジュールを
用意する、というのが katachi の推奨する形でもあります。

宣言1つにつきファイル1つ、という規則で並べます。役割 `"UiCore"` は
`roles/UiCoreRole.kt`、group `"build"` は `groups/BuildGroup.kt`。
`ProjectArchitectureSpec` はその規則自体を検査していて、宣言位置をソースから読み戻すので、
group / role の関数に `inline` が付くと落ちます。

layout はわざと緩く、package の1段を `*` で受けています。`roles` に新しいファイルを
足しても定義を触らずに通る、という側です。厳しく package 名まで書く形は
sample/android の方にあり、両方あることで選べることが見えます。

## Placement

| Module | Path | When to use |
|---|---|---|
| `:architecture-test` | `src/test/kotlin/com/example/kmp/*.kt` |  |
| `:architecture-test` | `src/test/kotlin/com/example/kmp/*/*.kt` |  |

## Examples

- `ProjectArchitecture.kt` ... 定義の入口
- `roles/ComponentRole.kt` ... 役割1つの宣言

## 置いてよいもの

- 定義（`groups` / `roles` package）と、その入口の `ProjectArchitecture.kt`
- 定義を検査する `*Spec.kt`
- `processor` package の自作プロセッサ（`owner` のような独自メタデータを読むもの）

## 置いてはいけないもの

- アプリのコード。このモジュールはアプリのどのレイヤーにも属しません
