[katachi-sample-android](../README.md) / [エントリーポイントレイヤー](README.md)

# エントリポイント

:app に置く、Android がアプリを起動するときに触る型

Android が起動時に最初に触る型。`:app` に `MainActivity` と `MainApplication` が
1つずつ、名指しで置いてある。どちらかが消えると `[MissingFile]` で検査が落ちる。
起動できないアプリが検査を通ってしまわないようにするため。

`MainActivity` は `setContent { AppTheme { AppNavHost() } }` だけを書く。
`AppNavHost` は同じファイルの private な `@Composable` で、`:feature:*` が公開する
Route を並べてナビゲーショングラフを組み立てる。すべての feature を知ってよい
モジュールは `:app` だけで、その知識はこのファイルの中に閉じている。

`MainApplication` は `Application` を継承するだけ。DI コンテナの初期化のような
「起動時に1回だけ」の処理を足す場所として空けてある。

置いてはいけないもの: 画面の中身。`:app` は feature をつなぐだけで、
UI は `:ui` と `:feature:*` にある。ここに Composable が増え始めたら、
それは feature モジュールに引っ越すべきもの。

この役割の package は `modulePackage` を使わず `com/example/sample` と直接書く。
`:app` はアプリ本体で、`:ui` → `com.example.sample.ui` のような
モジュールパスとの対応を持たないため。

## 配置場所

| モジュール | パス | 使い分け |
|---|---|---|
| `:app` | `src/main/kotlin/com/example/sample/MainActivity.kt` |  |
| `:app` | `src/main/kotlin/com/example/sample/MainApplication.kt` |  |

## 例

- `MainActivity` ... 起動時に表示される Activity
- `MainApplication` ... Application の実装
