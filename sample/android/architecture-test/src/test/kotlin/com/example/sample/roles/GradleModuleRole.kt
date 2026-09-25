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
    description = """
        各モジュールの `build.gradle.kts`。`":app".module { }` のように空のブロックが並ぶ。
        `.module { }` 自体が「そのディレクトリがある」「`build/` は見ない」
        「`build.gradle.kts` がある」の3つを意味するので、中身を書かなくてもモジュールの宣言になる。

        ここは `settings.gradle.kts` の `include(...)` を写した場所で、それ以上のことは書かない。
        モジュールの中に何が入るかは、同じモジュールを名指しする他の役割が言う。
        両方をここに書くと、`:ui` の中身が「UI の話」ではなく「ビルドの話」に見えてしまう。

        feature だけは `":feature:*"` の1行。feature モジュールは誰にも断らずに増えるもので、
        `include(":feature:profile")` を足したときにこの定義を直さずに済むようにしてある。

        `documented = false`。ビルド設定はどのプロジェクトにもある同じ形で、このアプリが
        何であるかを何も語らないので、生成されるドキュメントには出さない。検査はする。
    """.trimIndent()
    example("app/build.gradle.kts", ":app のビルドスクリプト")
    example("feature/home/build.gradle.kts", ":feature:home のビルドスクリプト")
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
