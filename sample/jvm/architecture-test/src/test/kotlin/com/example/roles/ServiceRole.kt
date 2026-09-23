package com.example.roles

import com.example.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.konsist

/** The role holding one application-specific behaviour, built out of repositories. */
fun DeclarationContainerScope.service() = "Service" {
    title = "サービス"
    summary = "アプリ固有の振る舞いを1つ持ち、Repository を組み合わせて実現する"
    example("HealthService", "サーバの稼働状態を取得する")
    layout {
        ":".module {
            mustBePublic()
            mainSourceSet / kotlin / modulePackage / "service" / "*Service".ktFile()
        }
    }
}

// Kept in this file rather than in a shared one: the declaration site is the first frame
// outside katachi, so a violation keeps naming the role that owns the rule.
//
// Adoption step 4 of katachi's own README (`konsist-integration`): the one line that shows a
// backend-written constraint next to katachi's own layout vocabulary. Also stands as the
// regression test for `LayoutNode.synthetic` — a `":".module { }` block injects `build` and
// `build.gradle.kts`, and this constraint would wrongly cover `build.gradle.kts` if that
// exclusion ever broke.
private fun LayoutScope.mustBePublic() =
    "public であること".konsist {
        classes().must { it.hasPublicOrDefaultModifier }
    }
