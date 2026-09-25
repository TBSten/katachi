[katachi-sample-kmp](../README.md) / [データ](README.md)

# プラットフォーム実装

:data モジュールの platform package。commonMain の expect 宣言と、androidMain / iosMain の actual 実装が同じ package に揃う

このサンプルが KMP であるからこそ存在する役割です。`commonMain` に `expect` を書き、
`androidMain` と `iosMain` に `actual` を1つずつ置きます。3つのファイルは同じ package
（`com.example.kmp.data.platform`）に居なければコンパイルが通らないので、package まで
含めてこの役割の形です。

layout の3行は source set 名だけが違います。`"<名前>".sourceSet` は `src/<名前>` を
指すだけのもので、KMP の source set はまさにそれです。ファイル名の `.android` / `.ios`
は Kotlin の要求ではなく慣習ですが、ここでパターンに書いた以上はこのプロジェクトの規則に
なります。

置いてよいもの:

- common からは書けないプラットフォーム API の、薄い入口
- その入口を common 側に見せるための `expect` 宣言

置いてはいけないもの:

- common で書けるもの。`expect`/`actual` は1つ増えるごとに実装を2つ書くことになり、
  共通で済むものを持ち込むほど割に合わなくなります
- UI に関わるもの。画面まわりのプラットフォーム差は、このサンプルでは1つもありません

注意点として、iOS の `actual` は CI でコンパイルされません。CI が走らせるのは
`:architecture-test:test` と `:app:android:testDebugUnitTest` だけで、Kotlin/Native の
ビルドには触らないからです（理由は `app/ios/README.md` にあります）。しかもこの役割の
layout はどの行もワイルドカードなので、`iosMain` 側を丸ごと書き忘れても katachi は
`[MissingFile]` を出しません。expect と actual が揃っているかを見られるのは
コンパイラだけで、そのコンパイラが CI で動かない、という穴がここにあります。

## 配置場所

| モジュール | パス | 使い分け |
|---|---|---|
| `:data` | `src/commonMain/kotlin/**/platform/*.kt` |  |
| `:data` | `src/androidMain/kotlin/**/platform/*.android.kt` |  |
| `:data` | `src/iosMain/kotlin/**/platform/*.ios.kt` |  |

## 例

- `PlatformInfo.kt` ... commonMain の expect 宣言
- `PlatformInfo.android.kt` ... Android 向けの actual
- `PlatformInfo.ios.kt` ... iOS 向けの actual
