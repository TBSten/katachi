package com.example.kmp.roles

import com.example.kmp.processor.owner
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*

/** The `build.gradle.kts` every module of this sample carries. */
fun DeclarationContainerScope.gradleModule() = "GradleModule" {
    title = "モジュールのビルドスクリプト"
    // Covers `architecture-test/build.gradle.kts` as well: that module is a module
    // like any other, and the price of giving katachi a home of its own is that its
    // build script shows up here.
    summary = "各モジュールの build.gradle.kts"
    documented = false
    owner = "platform"
    example("data/build.gradle.kts", ":data のビルドスクリプト")
    example("architecture-test/build.gradle.kts", ":architecture-test のビルドスクリプト")
    // One line per module, written the way `settings.gradle.kts` writes it. An empty
    // `.module { }` block is not an empty declaration: every module block says
    // `build/` is not checked and `build.gradle.kts` has to be there, which is the
    // whole of this role. The other roles say where that module's sources go.
    //
    // Only `:feature:*` is written with a wildcard, because that is the one place
    // where modules are expected to multiply. It stands for the feature modules that
    // exist, so a new one is picked up without this list being touched — and a
    // `feature/` directory that is not a module at all has nothing claiming it.
    layout {
        ":app:android".module { }
        ":architecture-test".module { }
        ":data".module { }
        ":feature:*".module { }
        ":navigation".module { }
        ":testing".module { }
        ":ui".module { }
    }
}
