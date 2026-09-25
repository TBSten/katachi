[katachi-sample-kmp](../README.md) / [フィーチャー](README.md)

# ViewModel

画面の状態を持つ androidx.lifecycle.ViewModel。Repository から取得した値を UiState に変換し、StateFlow で公開する

画面の状態を持つ場所です。`:feature:<name>` の `commonMain` に `<Name>ViewModel.kt` を
1ファイル。Screen と同じくファイル名がモジュール名に縛られるので、1つの feature モジュールに
ViewModel が2つ並ぶことはありません。

`androidx.lifecycle.ViewModel` を継承していますが、これは Compose Multiplatform 版
（`org.jetbrains.androidx.lifecycle`）の実体なので `commonMain` に書けて iOS でも動きます。
パッケージ名が `androidx` で始まるからといって Android 専用ではない、というのが
KMP で引っかかりやすいところです。

置いてよいもの:

- `private val mutableState = MutableStateFlow(...)` と、それを `asStateFlow()` で
  公開する `val state: StateFlow<UiState<...>>`
- 画面から呼ばれる操作（`reload()` など）と、`viewModelScope` を使った読み込み
- 画面に出す形にまとめた data class（`SettingsUi` のように、同じファイル内で構わない）

置いてはいけないもの:

- `@Composable`。描くのは Screen の仕事です
- `android.*` の import と `Context`。これが要る処理は `:data` の PlatformImplementation
  （expect/actual）に降ろします。ここに書くと `commonMain` がコンパイルできません
- 他の feature の ViewModel への依存

Repository は引数で受け取るだけで、自分では作りません。作るのは Route の役目です。

## 配置場所

| モジュール | パス | 使い分け |
|---|---|---|
| `:feature:*` | `src/commonMain/kotlin/**/<name>ViewModel.kt` |  |

## 例

- `HomeViewModel` ... ホーム画面の状態
- `SettingsViewModel` ... 設定画面の状態
