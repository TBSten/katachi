[katachi-sample-kmp](../README.md) / [テスト支援](README.md)

# テストコード

各モジュールのテスト。KMP モジュールは commonTest、純 Android / 純 JVM モジュールは src/test

アプリ自身のテストです。置き場所はモジュールの種類で決まります。Android モジュールは
`src/test`、KMP モジュールなら `commonTest`。どちらも「そのモジュールのテスト」で
あることは変わらないので、katachi の上では1つの役割にしています。

いま layout に書いてあるのは `:app:android` の `testSourceSet` だけです。実際に
テストを持つモジュールがそこしかないからで、`commonTest` を持つモジュールが現れたら
そのとき1行足します。無いディレクトリを先に宣言すると、このサンプルが持っていない形を
持っていると言うことになります。

テストが `:app:android` に集まっているのは KMP ゆえの事情です。他のモジュールは
Android と iOS だけを持つ KMP で JVM ターゲットがないので、Compose を使わない部分
（`Navigator`、`UiState`、`FakeUserRepository`）を回せる JVM のテストがここしか
ありません。

画面の `@Composable` はテストしていません。Compose のテストランタイムが要る話になり、
このサンプルが見せたい範囲の外です。

## Placement

| Module | Path | When to use |
|---|---|---|
| `:app:android` | `src/test/kotlin/com/example/kmp/app/*Spec.kt` |  |

## Examples

- `SampleModulesSpec` ... :app:android のユニットテスト

## 置いてはいけないもの

- アーキテクチャ定義。`:architecture-test` は ArchitectureDefinition の役割です
- テストダブル。`Fake*` は `:testing` の commonMain にあります
