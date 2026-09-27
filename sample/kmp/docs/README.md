# katachi-sample-kmp documentation

## Document map

### [フィーチャー](./feature/README.md)

1画面につき1モジュール。:feature:<name> が Screen / ViewModel / Route を必ず持つ

- [画面](./feature/Screen.md)
- [ViewModel](./feature/ViewModel.md)
- [ルート](./feature/Route.md)

### [UI](./ui/README.md)

:ui と :navigation。どの画面にも属さない共有の UI と、遷移先の定義

- [共通コンポーネント](./ui/Component.md)
- [テーマ](./ui/Theme.md)
- [UI 基盤](./ui/UiCore.md)
- [プレビュー](./ui/Preview.md)
- [プレビューの土台](./ui/PreviewRoot.md)
- [ナビゲーション](./ui/Navigation.md)

### [データ](./data/README.md)

:data モジュール。データの取得口と、プラットフォームで実装が変わる部分

- [リポジトリ](./data/Repository.md)
- [プラットフォーム実装](./data/PlatformImplementation.md)

### [テスト支援](./testing/README.md)

テストダブル、テストコード本体、katachi のアーキテクチャ定義、そこから書き出されるドキュメントとスナップショット、棚上げした違反の台帳

- [フェイク](./testing/Fake.md)
- [テストコード](./testing/Test.md)
- [アーキテクチャ定義](./testing/ArchitectureDefinition.md)
- [生成ドキュメント](./testing/GeneratedDocumentation.md)
- [レイアウトのスナップショット](./testing/LayoutSnapshot.md)
- [baseline（棚上げした違反の台帳）](./testing/Baseline.md)

### [アプリ](./app/README.md)

Gradle がビルドする Android アプリと、Xcode がビルドする iOS アプリ

- [エントリポイント](./app/Entrypoint.md)
- [Android リソース](./app/AndroidResource.md)
- [Xcode プロジェクト](./app/XcodeProject.md)

## フィーチャー

1画面につき1モジュール。:feature:<name> が Screen / ViewModel / Route を必ず持つ

- [画面](./feature/Screen.md) ... 1つの画面の @Composable。ViewModel の StateFlow を購読し、Component を組み合わせて描く
- [ViewModel](./feature/ViewModel.md) ... 画面の状態を持つ androidx.lifecycle.ViewModel。Repository から取得した値を UiState に変換し、StateFlow で公開する
- [ルート](./feature/Route.md) ... 画面を navigation の Destination に結びつけ、ViewModel の生成も引き受ける

## UI

:ui と :navigation。どの画面にも属さない共有の UI と、遷移先の定義

- [共通コンポーネント](./ui/Component.md) ... :ui モジュールの component package。複数の画面から使われる @Composable 部品
- [テーマ](./ui/Theme.md) ... :ui モジュールの theme package。MaterialTheme の設定と、色・余白のデザイントークン
- [UI 基盤](./ui/UiCore.md) ... :ui モジュールの core package。画面に依存しない UI の土台。UiState など
- [プレビュー](./ui/Preview.md) ... @Preview を付けた private @Composable。対象の Composable と同じ package の <対象>Preview.kt に置き、中身は PreviewRoot で包む
- [プレビューの土台](./ui/PreviewRoot.md) ... :ui モジュールの preview package。@Preview の中身を AppTheme と Surface で包む
- [ナビゲーション](./ui/Navigation.md) ... 遷移先の定義と、現在地を持つ Navigator

## データ

:data モジュール。データの取得口と、プラットフォームで実装が変わる部分

- [リポジトリ](./data/Repository.md) ... :data モジュールの user package。データの取得口で、インターフェースと実装の2つの置き方を持つ
- [プラットフォーム実装](./data/PlatformImplementation.md) ... :data モジュールの platform package。commonMain の expect 宣言と、androidMain / iosMain の actual 実装が同じ package に揃う

## テスト支援

テストダブル、テストコード本体、katachi のアーキテクチャ定義、そこから書き出されるドキュメントとスナップショット、棚上げした違反の台帳

- [フェイク](./testing/Fake.md) ... :testing の commonMain に置く偽の実装。他モジュールのテストから使う
- [テストコード](./testing/Test.md) ... 各モジュールのテスト。KMP モジュールは commonTest、純 Android / 純 JVM モジュールは src/test
- [アーキテクチャ定義](./testing/ArchitectureDefinition.md) ... :architecture-test モジュールの src/test。katachi の DSL で書いたこのプロジェクトの定義。どのレイヤーにも属さないので専用モジュールに置く
- [生成ドキュメント](./testing/GeneratedDocumentation.md) ... この定義から書き出され、リポジトリにコミットされる Markdown
- [レイアウトのスナップショット](./testing/LayoutSnapshot.md) ... この定義を平坦化して全行書き出した記録。定義の変化を人が差分でレビューするためにある
- [baseline（棚上げした違反の台帳）](./testing/Baseline.md) ... katachi を入れた時点ですでにあった違反を記録し、テストを落とさずに棚上げしておく台帳

## アプリ

Gradle がビルドする Android アプリと、Xcode がビルドする iOS アプリ

- [エントリポイント](./app/Entrypoint.md) ... Android アプリの起動点。ComponentActivity と、そこから setContent で呼ぶアプリ全体の @Composable
- [Android リソース](./app/AndroidResource.md) ... AndroidManifest.xml と res/ 以下のリソース XML。:app:android だけが持つ
- [Xcode プロジェクト](./app/XcodeProject.md) ... app/ios 以下。Gradle の管理外で、検査もしない
