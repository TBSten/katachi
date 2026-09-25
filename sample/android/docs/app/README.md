[katachi-sample-android](../README.md)

# エントリーポイントレイヤー

:app が持つもの。起動の入口と、Android のリソース

`:app` モジュールそのもの。このアプリで唯一、すべての feature を知ってよいモジュールで、
`MainActivity` の中でナビゲーショングラフを組み立てて `:feature:home` と
`:feature:settings` をつなぐ。

集めたのは、`:app` にしか置けない2つ。Android が起動時に触る型
（`MainActivity` / `MainApplication`）と、アプリとして必要な Kotlin 以外のファイル
（`AndroidManifest.xml` / `res/` / `proguard-rules.pro`）。どちらも
「アプリケーションであること」から来るもので、アプリの機能そのものではない。

画面の中身はここには無い。`:app` は依存の一番上にいて下向きにしか参照しないので、
`:app` を丸ごと差し替えても下のモジュールは壊れない。ここに Composable が増え始めたら、
それは feature モジュールへ引っ越すべきもの。

`:app` は package がモジュールパスから導けない2つのモジュールのうちの1つで、
`com/example/sample` を直接書く。アプリ本体なので、`:ui` → `com.example.sample.ui` の
ような対応を持たないため（もう1つは `:architecture-test`）。

| 役割 | 概要 |
|---|---|
| [エントリポイント](./Entrypoint.md) | :app に置く、Android がアプリを起動するときに触る型 |
| [Android リソース](./AndroidResource.md) | AndroidManifest.xml・res/・proguard-rules.pro |

## このグループの配置

```
:app
  src/main/
    kotlin/com/example/sample/
      MainActivity.kt     エントリポイント
      MainApplication.kt  エントリポイント
    AndroidManifest.xml   Android リソース
  proguard-rules.pro      Android リソース

app/src/main/res/         Android リソース
```
