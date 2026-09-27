# katachi-sample-android documentation

Compose で書かれた Android アプリを、katachi で形から説明したもの。このページ以下はすべて `katachiDocs` が生成したもので、手では書かない。

## Document map

### [各画面の構成](./feature/README.md)

画面1つぶんのモジュール。:feature:<name> ごとに Screen / ViewModel / Route を1つずつ置き、画面の部品とテストを足していく

- [Screen](./feature/Screen.md)
- [ViewModel](./feature/ViewModel.md)
- [Route](./feature/Route.md)
- [画面の部品](./feature/FeatureComponent.md)
- [画面のテスト](./feature/FeatureTest.md)

### [UI (共通レイヤー)](./ui/README.md)

feature をまたいで共有する UI。:ui の4つの package と :navigation

- [共通コンポーネント](./ui/Component.md)
- [テーマ](./ui/Theme.md)
- [UI 基盤](./ui/UiCore.md)
- [プレビュー](./ui/Preview.md)
- [プレビューの土台](./ui/PreviewRoot.md)
- [画面遷移](./ui/Navigation.md)

### [データレイヤー](./data/README.md)

:data が持つもの。データの取得と保存

- [リポジトリ](./data/Repository.md)

### [エントリーポイントレイヤー](./app/README.md)

:app が持つもの。起動の入口と、Android のリソース

- [エントリポイント](./app/Entrypoint.md)
- [Android リソース](./app/AndroidResource.md)

### [テスト](./testing/README.md)

アプリを確かめるためにあるもの。共有のフェイク、テスト、アーキテクチャ定義、生成ドキュメント、レイアウトのスナップショット、baseline

- [フェイク](./testing/Fake.md)
- [テストコード](./testing/Test.md)
- [アーキテクチャ定義](./testing/ArchitectureDefinition.md)
- [生成ドキュメント](./testing/GeneratedDocumentation.md)
- [レイアウトのスナップショット](./testing/LayoutSnapshot.md)
- [baseline（棚上げした違反の台帳）](./testing/BaselineFile.md)

## 各画面の構成

画面1つぶんのモジュール。:feature:<name> ごとに Screen / ViewModel / Route を1つずつ置き、画面の部品とテストを足していく

- [Screen](./feature/Screen.md) ... 1つの画面の UI 実装となる @Composable。:feature:<name> ごとに <Name>Screen.kt を置く
- [ViewModel](./feature/ViewModel.md) ... 画面の状態を StateFlow で公開し、イベントを受け取る androidx.lifecycle.ViewModel
- [Route](./feature/Route.md) ... 画面への遷移先。feature の外に公開する唯一の入口
- [画面の部品](./feature/FeatureComponent.md) ... 1つの画面でしか使わない @Composable。:feature:<name> の component package に <Name>*.kt で置く
- [画面のテスト](./feature/FeatureTest.md) ... :feature:<name> の src/test に置く、ViewModel を :testing のフェイクで動かすテスト

## UI (共通レイヤー)

feature をまたいで共有する UI。:ui の4つの package と :navigation

- [共通コンポーネント](./ui/Component.md) ... :ui モジュールの component package に置く、feature をまたいで使う部品
- [テーマ](./ui/Theme.md) ... :ui モジュールの theme package に置く、色・タイポグラフィ・形
- [UI 基盤](./ui/UiCore.md) ... :ui モジュールの core package に置く、UI 層の土台になる型
- [プレビュー](./ui/Preview.md) ... @Preview を付けた private @Composable。対象の Composable と同じファイルに置き、中身は PreviewRoot で包む
- [プレビューの土台](./ui/PreviewRoot.md) ... :ui モジュールの preview package に置く、すべての @Preview が中身を包む土台。テーマと背景を 1 箇所で決め、darkTheme を受け取って明暗を出し分ける
- [画面遷移](./ui/Navigation.md) ... :navigation に置く、画面間の移動

## データレイヤー

:data が持つもの。データの取得と保存

- [リポジトリ](./data/Repository.md) ... データの取得と保存。インターフェースと実装を :data の、扱う対象ごとの package （user / settings）に並べて置く

## エントリーポイントレイヤー

:app が持つもの。起動の入口と、Android のリソース

- [エントリポイント](./app/Entrypoint.md) ... :app に置く、Android がアプリを起動するときに触る型
- [Android リソース](./app/AndroidResource.md) ... AndroidManifest.xml・res/・proguard-rules.pro

## テスト

アプリを確かめるためにあるもの。共有のフェイク、テスト、アーキテクチャ定義、生成ドキュメント、レイアウトのスナップショット、baseline

- [フェイク](./testing/Fake.md) ... :testing に置く、他モジュールのテストから使う偽の実装
- [テストコード](./testing/Test.md) ... :architecture-test の src/test/kotlin に置く、定義を検査するテスト
- [アーキテクチャ定義](./testing/ArchitectureDefinition.md) ... katachi の DSL で書かれた役割の定義。どのレイヤーにも属さない
- [生成ドキュメント](./testing/GeneratedDocumentation.md) ... この定義から書き出され、リポジトリにコミットされる Markdown
- [レイアウトのスナップショット](./testing/LayoutSnapshot.md) ... この定義を平坦化して全行書き出した記録。定義の変化を人が差分でレビューするためにある
- [baseline（棚上げした違反の台帳）](./testing/BaselineFile.md) ... katachi を入れた時点ですでにあった違反を記録し、テストを落とさずに棚上げしておく台帳
