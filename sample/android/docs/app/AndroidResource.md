[katachi-sample-android](../README.md) / [エントリーポイントレイヤー](README.md)

# Android リソース

AndroidManifest.xml・res/・proguard-rules.pro

`:app` が持つ、Kotlin ではないファイル。`AndroidManifest.xml`、`res/`、
`proguard-rules.pro` の3つで、どれも形を決めているのは Android のビルドシステムであって
このプロジェクトではない。

`AndroidManifest.xml` と `proguard-rules.pro` は名指し。どちらもアプリに1つしか無いので、
2つ目が現れたら違反にする。

`res/` は `ignore()` してある。中の構成（`values/` `drawable-*/` など）は
Android のリソースシステムが決めた規則で、AGP がすでに検証している。
katachi 側でもう一度書き下すと、同じ規則の写しが2つできて、片方が必ず古くなる。
いま入っているのは `values/strings.xml` と `values/themes.xml`。

リソースを持てるモジュールは `:app` だけではない（`:ui` も Android ライブラリ）が、
このサンプルでは `:app` 以外に `res/` が無いので、この役割は `:app` だけを見ている。
他のモジュールにリソースを置くなら、そのとき役割を広げる。

## Placement

| Module | Path | When to use |
|---|---|---|
| `:app` | `proguard-rules.pro` |  |
| `:app` | `src/main/AndroidManifest.xml` |  |
|  | `app/src/main/res` |  |

## Examples

- `AndroidManifest.xml` ... アプリの構成
- `res/values/strings.xml` ... 文字列リソース
