[Ktor サンプルアプリ](../README.md) / [ドメイン](README.md)

# モデル

ドメインで扱う値。API の入出力としてもそのまま使う

アプリが扱う値そのものです。`Health` は `status` と `version` を持つ `@Serializable` な
data class で、Repository が作り、Service が受け渡し、Controller がそのまま
`GET /health` の応答本文にします。

ドメインのモデルと API のペイロードを分けていないのは、このサンプルが意図して選んだ形です。
両者がずれ始めたら API 層に DTO の役割を新しく立てて戻せるように、いまは1つにしてあります。

置いてよいのは data class・enum・値オブジェクトと、その値に閉じた計算です。
置いてはいけないもの:

- 取得や保存。I/O はリポジトリの役割です
- Ktor への依存。モデルが知ってよいのは `kotlinx.serialization` までです

名前は「何を表す値か」そのもので、接尾辞は付けません（`HealthModel` ではなく `Health`）。
対象は `model` パッケージ直下の `.kt` だけで、その下にディレクトリを掘っても
この役割には入りません。

## 配置場所

| モジュール | パス | 使い分け |
|---|---|---|
| `:` | `src/main/kotlin/**/model/*.kt` |  |

## 例

- `Health` ... 稼働状態とバージョン
