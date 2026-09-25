[katachi-sample-kmp](../README.md)

# データ

:data モジュール。データの取得口と、プラットフォームで実装が変わる部分

`:data` 1モジュールを package で割った層です。`user` にリポジトリ、`platform` に
expect/actual の組が入ります。`:ui` と同じく、モジュールではなく package で分けた形です。

2つの役割を同じ group に置いているのは、どちらも「アプリの外から値を持ってくる口」
だからです。`UserRepository` はデータの取得口、`platformName()` は実行環境という
外側の情報の取得口で、呼ぶ側（ViewModel）から見ればどちらも同じ `:data` への1本の依存に
見えます。

KMP のプラットフォーム差をこの group に閉じ込めているのが、このサンプルの設計です。
`androidMain` / `iosMain` を持つのは `:data` だけで、UI 側（`:ui` と feature）には
`expect`/`actual` が1つもありません。プラットフォーム固有の処理が要るという話になったら、
画面ではなくここに降ろしてください。

置いてはいけないもの:

- UI の型。`UiState` は `:ui` の core package にあり、`:data` はそれを知りません
- テスト用の偽実装。`FakeUserRepository` は `:testing` にあります
- `@Composable`。`:data` のビルドスクリプトに Compose のプラグインは入っていません

| 役割 | 概要 |
|---|---|
| [リポジトリ](./Repository.md) | :data モジュールの user package。データの取得口で、インターフェースと実装の2つの置き方を持つ |
| [プラットフォーム実装](./PlatformImplementation.md) | :data モジュールの platform package。commonMain の expect 宣言と、androidMain / iosMain の actual 実装が同じ package に揃う |

## このグループの配置

```
:data
  src/
    commonMain/kotlin/**/
      user/
        *Repository.kt                           リポジトリ
        *RepositoryImpl.kt                       リポジトリ
      platform/*.kt                              プラットフォーム実装
    androidMain/kotlin/**/platform/*.android.kt  プラットフォーム実装
    iosMain/kotlin/**/platform/*.ios.kt          プラットフォーム実装
```
