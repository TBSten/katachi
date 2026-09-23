package com.example.sample.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*

/**
 * The role of a module's build script.
 *
 * This is where the module paths are listed and nothing else is: `".module { }"` is defined
 * as a directory plus `"build".ignore()` plus `"build.gradle".ktsFile()`, so an empty block is
 * already the whole of what a Gradle module is from this role's point of view. The other roles
 * of the definition name the same modules again to say what is *inside* them.
 */
fun DeclarationContainerScope.gradleModule() = "GradleModule" {
    title = "モジュールのビルドスクリプト"
    summary = "各モジュールの build.gradle.kts"
    documented = false
    layout {
        // One line per `include(...)` in settings.gradle.kts, in the same order —
        // except the features, which are one `":feature:*"` because a feature module
        // is added without asking anyone.
        ":app".module { }
        ":architecture-test".module { }
        ":feature:*".module { }
        ":data".module { }
        ":ui".module { }
        ":navigation".module { }
        ":testing".module { }
    }
}
