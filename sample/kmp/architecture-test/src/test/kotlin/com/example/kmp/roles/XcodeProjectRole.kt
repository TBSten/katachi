package com.example.kmp.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The iOS application, which Xcode builds and Gradle knows nothing about.
 *
 * `app/ios` is not a Gradle module, so it is not written as one: there is no module path that
 * resolves to it and no `build.gradle.kts` to require. It stays a plain directory key, which
 * is the whole point of having it in this sample. Xcode owns what is inside it, so the role
 * declares the directory and stops the check there. It is still a declared directory with a
 * role and a summary around it, which is the only way katachi lets anything go unchecked.
 */
fun DeclarationContainerScope.xcodeProject() = "XcodeProject" {
    title = "Xcode プロジェクト"
    summary = "app/ios 以下。Gradle の管理外で、検査もしない"
    example("iosAppApp.swift", "SwiftUI のエントリポイント")
    layout {
        "app/ios" { ignore() }
    }
}
