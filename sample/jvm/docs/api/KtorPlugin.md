[Ktor サンプルアプリ](../README.md) / [API](README.md)

# Ktor プラグイン設定

Ktor の Application に対する横断的な設定を1つ行う

特定のエンドポイントではなく、Application 全体に一度だけ効く設定を置く場所です。
1ファイルにつき `Application` の拡張関数 `configureXxx()` を1つ書き、
エントリポイントの `Application.module()` がそれを順に呼びます。

ファイル名に接尾辞はありません。`*Controller` のような手がかりが無いので、
「これはプラグイン設定だ」と言っているのは `plugin` パッケージそのものです。
ファイル名は install する Ktor の機能の名前に合わせます。

## Placement

| Module | Path | When to use |
|---|---|---|
| `:` | `src/main/kotlin/**/plugin/*.kt` |  |

## Examples

- `Routing` ... routing ツリーの配線
- `Serialization` ... JSON の入出力設定

## 置いてよいもの

置いてよいのは Ktor プラグインの `install(...)` と、その設定ブロックです。
`Serialization.kt` は `ContentNegotiation` に JSON（`prettyPrint` と `ignoreUnknownKeys` を
有効にしたもの）を入れ、`Routing.kt` は `routing { }` を開いて各 Controller の `register` を
呼びます。どの Controller が繋がっているかを1ファイルで見渡せるのが狙いです。

## 置いてはいけないもの

- エンドポイントのハンドラ本体。`get`/`post` の中身はコントローラの役割です
- ドメインの判断やデータ取得。Service・Repository をここから直接呼びません
- `main()` と `Application.module()`。起動と組み立てはエントリポイントの役割です
