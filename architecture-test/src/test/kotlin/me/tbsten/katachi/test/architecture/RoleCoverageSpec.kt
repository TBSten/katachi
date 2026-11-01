package me.tbsten.katachi.test.architecture

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.process

@OptIn(ExperimentalKatachiApi::class, InternalKatachiApi::class)
class RoleCoverageSpec : FreeSpec({
    "役割" - {
        "ワイルドカードを含む宣言が、どれも1件以上の実ファイルに当たっている" {
            val unmatched = projectArchitecture.process { context ->
                context.declaredEntries
                    // Entries without a wildcard are `required`, and a missing one is already
                    // reported as `[MissingFile]` by the check itself. `Ignore` and plain
                    // `Directory` entries claim no file and have nothing to match.
                    .filter { it.kind == LayoutEntryKind.File || it.kind == LayoutEntryKind.AnyFile }
                    .filterNot { it.required }
                    // `gradle()`'s own roles declare both the Kotlin and the Groovy spelling of
                    // `settings.gradle(.kts)` and `build.gradle(.kts)`, each optional so either
                    // may be the one a build actually uses, and `gradle/gradle-daemon-jvm.properties`
                    // for the builds that pin a daemon JVM. This repository writes every script
                    // in Kotlin and pins none, so those are optional-and-unmatched by design, not a
                    // hole in this project's coverage — `gradleOwnDeclarations` is the same set
                    // `DeclarationSiteSpec` carves out, for a different reason: there, because
                    // the declaration site is katachi's; here, because the declaration itself is.
                    .filterNot { it.role.qualifiedName in gradleOwnDeclarations }
                    .filterNot { context.matchesSomething(it) }
                    .map { "${it.role.qualifiedName}  ${it.path}" }
            }

            unmatched shouldBe emptyList()
        }
    }
})

@OptIn(ExperimentalKatachiApi::class, InternalKatachiApi::class)
private fun ArchitectureProcessContext<Unit>.matchesSomething(entry: LayoutEntry): Boolean {
    val owned = filesOf(entry.role)
    return when (entry.kind) {
        LayoutEntryKind.AnyFile ->
            owned.any { entry.glob.matches(it.substringBeforeLast('/', missingDelimiterValue = "")) }

        else -> owned.any { entry.glob.matches(it) }
    }
}
