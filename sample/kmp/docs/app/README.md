[katachi-sample-kmp](../README.md)

# アプリ

Gradle がビルドする Android アプリと、Xcode がビルドする iOS アプリ

実際に配布される2つのアプリです。どちらも「アプリ」なのに、書き方がまるで違うので
1つの group にまとめました。

`:app:android` は Gradle モジュールなのでモジュールパスで書けます。`app/ios` は
`settings.gradle.kts` が意図的に include していないので、モジュールパスが存在せず、
ただのディレクトリキーとして宣言して `ignore()` で検査を止めます。現実の KMP
リポジトリは Gradle が管理するディレクトリと管理しないディレクトリが混ざるので、
その両方を書けることを見せるのがこの group です。

`:app:android` にはもう1つ特徴があります。このサンプル唯一の入れ子のモジュールパスで、
かつ唯一 package がモジュールパスに従わないモジュール（`com.example.kmp.app`、
`com.example.kmp.app.android` ではない）です。だから Entrypoint と AndroidResource は
`modulePackage` を使わず package を直書きしています。規則に従わないものを規則で
書こうとして曲げるより、違うと書く方が読み手に親切です。

置いてよいもの:

- 起動点と、アプリ全体の組み立て（`AppRoot`）
- Android のビルドが要求するリソース

置いてはいけないもの:

- 画面そのもの。画面は feature モジュールにあり、ここは Route を呼ぶだけです
- 共有したいロジック。ここに書いたものは iOS から見えません

| 役割 | 概要 |
|---|---|
| [エントリポイント](./Entrypoint.md) | Android アプリの起動点。ComponentActivity と、そこから setContent で呼ぶアプリ全体の @Composable |
| [Android リソース](./AndroidResource.md) | AndroidManifest.xml と res/ 以下のリソース XML。:app:android だけが持つ |
| [Xcode プロジェクト](./XcodeProject.md) | app/ios 以下。Gradle の管理外で、検査もしない |

## このグループの配置

```
:app:android
  src/main/
    kotlin/com/example/kmp/app/*.kt  エントリポイント
    AndroidManifest.xml              Android リソース
    res/*/*.xml                      Android リソース

app/ios/                             Xcode プロジェクト
```
