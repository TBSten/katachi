# app/ios

このディレクトリは **Gradle モジュールではありません**。Xcode プロジェクトの置き場所で、
`settings.gradle.kts` からは意図的に見えないようにしてあります。

それがこのディレクトリを置いている理由です。実際の KMP リポジトリには Gradle が管理するディレクトリと
管理しないディレクトリが混ざっていて、katachi はその両方を書けなければなりません。このディレクトリを
受け持つのは役割 `app/XcodeProject`
（[`architecture-test/src/test/kotlin/com/example/kmp/roles/XcodeProjectRole.kt`](../../architecture-test/src/test/kotlin/com/example/kmp/roles/XcodeProjectRole.kt)）で、
`"app/ios" { ignore() }` と宣言して中の検査を止めています。中身を決めるのは katachi ではなく Xcode だからです。

## コミットしているもの・していないもの

コミットしているのは、いくつかの Swift ファイルと `Info.plist`、つまり iOS アプリの**形**だけです。

`iosApp.xcodeproj/` はコミットしていません。手書きの `project.pbxproj` は Xcode が開けない壊れ方をするうえ、
このサンプルは iOS 向けにビルドしないので、置いても死荷重になります。実際にアプリを動かしたいときは
Xcode で作ってください。

```
app/ios/
  README.md
  iosApp/
    iosApp.xcodeproj/        # コミットしていない。Xcode で作る
    iosApp/
      iosAppApp.swift
      ContentView.swift
      Info.plist
```

## CI で iOS をビルドしない理由

katachi は JVM のライブラリなので、katachi のテストは iOS の上では走りません。定義を素の `kotlin("jvm")`
モジュールである `:architecture-test` に置いているのはそのためで、ほかのモジュールには定義を置ける
JVM ターゲットがありません。KMP モジュールには Kotlin の iOS ターゲット（`iosArm64()` /
`iosSimulatorArm64()`）を宣言してあり、モジュールの構成は現実的な形にしてあります。ただし Linux の
CI ランナーではコンパイルできず、まっさらな macOS ランナーでもまず Kotlin/Native の配布物を
ダウンロードすることになります。UI のモジュールは Compose Multiplatform を使っているので、iOS 向けに
コンパイルすると Compose の Kotlin/Native klib までコンパイルすることになり、さらに遅くなります。

そのため CI（ルートの `./gradlew checkSampleKmp`）が回すのは、`:architecture-test:test`（katachi の検査）、
`:app:android:testDebugUnitTest`（サンプル自身のユニットテスト）、`:architecture-test:katachiLayout` と
`:architecture-test:katachiDocs --arg mode=check`（layout のスナップショットと生成ドキュメントが最新か）、
それにテンプレートからの生成と baseline の確認です。どれも iOS のタスクには届きません。`check` のような
lifecycle タスクは iOS のタスクまで引き込むので使っていません。

## 共有コードをつなぐなら（まだしていない）

`:data` のようなモジュールに `binaries.framework { baseName = "Shared" }` を足し、Xcode の Run Script
phase から `./gradlew :data:embedAndSignAppleFrameworkForXcode` を呼びます。このサンプルは iOS 向けに
ビルドしないので、設定はしていません。
