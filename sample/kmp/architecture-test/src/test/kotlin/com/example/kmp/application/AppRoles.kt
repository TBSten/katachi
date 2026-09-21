package com.example.kmp.application

import me.tbsten.katachi.dsl.ArchitectureScope

/**
 * The two applications: the Android one Gradle builds, and the iOS one Xcode builds.
 *
 * `:app:android` is the one nested module path of this sample, and it is also the one module
 * whose package does not follow it: the sources sit in `com.example.kmp.app`, not in
 * `com.example.kmp.app.android`. So the package is written out here instead of coming from
 * `modulePackage` — a module that does not follow the rule should say so rather than bend it.
 */
fun ArchitectureScope.appRoles() {
    "app".group {
        title = "アプリ"

        "Entrypoint" {
            title = "エントリポイント"
            summary = "Android アプリの起動点。ComponentActivity と、そこから setContent で呼ぶアプリ全体の @Composable"
            example("MainActivity", "起動時に表示される Activity")
            example("AppRoot", "テーマとナビゲーションを組み立てる Composable")
            // `mainSourceSet`, not `"commonMain".sourceSet`: `:app:android` is the Android
            // application module, and its code lives in `src/main` like any Android module's.
            layout {
                ":app:android".module {
                    mainSourceSet / kotlin / "com/example/kmp/app" / "*".ktFile()
                }
            }
        }
        "AndroidResource" {
            title = "Android リソース"
            summary = "AndroidManifest.xml、res/、proguard-rules.pro"
            example("AndroidManifest.xml", "アプリとモジュールのマニフェスト")
            example("res/values/strings.xml", "文字列リソース")
            // `res/*/` is the resource qualifier directory (`values`, `drawable`,
            // `mipmap-hdpi`, ...). Android decides those names, so the layout names the
            // level rather than each directory.
            layout {
                ":app:android".module {
                    mainSourceSet / "AndroidManifest.xml".file()
                    mainSourceSet / "res" / "*" / "*.xml".file()
                }
            }
        }
        // `app/ios` is not a Gradle module, so it is not written as one: there is no module
        // path that resolves to it and no `build.gradle.kts` to require. It stays a plain
        // directory key, which is the whole point of having it in this sample. Xcode owns
        // what is inside it, so the role declares the directory and stops the check there. It
        // is still a declared directory with a role and a summary around it, which is the
        // only way katachi lets anything go unchecked.
        "XcodeProject" {
            title = "Xcode プロジェクト"
            summary = "app/ios 以下。Gradle の管理外で、検査もしない"
            example("iosAppApp.swift", "SwiftUI のエントリポイント")
            layout {
                "app/ios" { ignore() }
            }
        }
    }
}
