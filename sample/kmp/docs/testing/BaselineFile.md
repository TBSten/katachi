[katachi-sample-kmp](../README.md) / [テスト支援](README.md)

# baseline（棚上げした違反の台帳）

katachi を入れた時点ですでにあった違反を記録し、テストを落とさずに棚上げしておく台帳

`ProjectArchitecture.kt` の `baseline()` が指すファイルです。ここに記録した
違反は `:architecture-test:test` を落とさず、「held back N violations」と件数だけが出ます。
記録に無い新しい違反は、これまでどおりテストを落とします。

このサンプルでは、baseline の見本として1件を意図的に残してあります。`:data` の
`androidMain` にある `user/UserAgent.android.kt` です。`androidMain` に置けるのは
`platform` package だけなので、`user` ディレクトリごと `[UnexpectedDirectory]` になります。

これは手で書きません。更新は次の2つで行います。

- `./gradlew :architecture-test:test -Dkatachi.baseline.update=true` — 今ある違反で丸ごと作り直す
- `./gradlew :architecture-test:test -Dkatachi.baseline.prune=true` — 直した違反の項目だけを消す

違反を直すと、その項目は「もう無い違反を棚上げしている」として `[StaleBaselineEntry]` で
テストを落とします。prune で項目を消すまで落ち続けるので、棚上げの件数は減る一方になります。
CI（環境変数 `CI=true`）では update も prune も拒否され、突き合わせだけを行います。

## Placement

| Module | Path | When to use |
|---|---|---|
|  | `katachi-baseline.json` |  |

## Examples

- `katachi-baseline.json` ... 棚上げした違反の一覧

## 置いてはいけないもの

- 恒久的に認めたいもの。それは台帳ではなく、定義の `layout { }` に役割として書きます
- 手で足した項目。次の update で書き戻されます
