[katachi-sample-android](../README.md) / [各画面の構成](README.md)

# 画面のテスト

:feature:<name> の src/test に置く、ViewModel を :testing のフェイクで動かすテスト

feature モジュールの単体テスト。`:feature:home` なら `src/test` の同じ package に
`HomeViewModelTest.kt` を置き、ファイル名はモジュール名（`Home`）で始めて `Test` で終える。

ViewModel の Repository はコンストラクタ引数なので、テストでは `:testing` の
`FakeUserRepository` のようなフェイクを渡して、流れてくる `UiState` を確かめる。
Compose にも Android の実機にも触らないので、JVM の上だけで走る。
feature モジュールの `build.gradle.kts` が `testImplementation(project(":testing"))` を
持つのはこのため。

テンプレートから、その feature の ViewModel をフェイクで組み立てるテストを生成できる。
`--arg feature=Home --arg name=ViewModel` で `HomeViewModelTest.kt` になる。

## Placement

| Module | Path | When to use |
|---|---|---|
| `:feature:home` | `src/test/kotlin/**/Home*Test.kt` | `:feature:home` の分。ファイル名は `Home` で始める |
| `:feature:settings` | `src/test/kotlin/**/Settings*Test.kt` | `:feature:settings` の分。ファイル名は `Settings` で始める |

## Examples

- `HomeViewModelTest` ... HomeViewModel をフェイクで動かすテスト
- `SettingsViewModelTest` ... SettingsViewModel をフェイクで動かすテスト

## 置いてはいけないもの

- 本物の Repository（`*RepositoryImpl`）。差し替えは `:testing` のフェイクで行う
- 画面の描画のテスト。Compose のテストが要るなら役割を分ける
