package me.tbsten.katachi.test.architecture

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.processor.ProjectModel
import me.tbsten.katachi.processor.process

/**
 * Guards the one thing neither the layout check nor a `konsist { }` can say: that a wildcard
 * declaration still matches something.
 *
 * Deny by default only watches the other direction. A file no role declared is an
 * `[UnexpectedFile]`, and a declaration **without** a wildcard that matches nothing is a
 * `[MissingFile]` — but a declaration **with** one is optional by nature
 * (`LayoutEntry.required` is false for every pattern), because a place that fills up over time
 * has to be allowed to be empty today. That is the hole: rename a package, delete a layer, or
 * mistype a glob, and the definition goes on passing while describing a repository that no
 * longer exists.
 *
 * Konsist cannot close it either. `KonsistScope` deliberately shadows Konsist's
 * `assertNotEmpty`, on the grounds that katachi reports things that exist and are wrong.
 *
 * This replaces the third assertion of the spec that used to live at
 * `katachi/src/test/kotlin/me/tbsten/katachi/test/PackageDependencySpec.kt`, which asked that
 * no layer of `:katachi` had become empty. It is checked per declaration rather than per role,
 * for a reason worth writing down: `".module { }"` injects that module's `build.gradle.kts`
 * into every role that uses it, so "this role owns at least one file" is answered `true` by
 * the injected build script alone and would have caught nothing.
 */
@OptIn(ExperimentalKatachiApi::class, InternalKatachiApi::class)
class RoleCoverageSpec : FreeSpec({
    "役割" - {
        "ワイルドカードを含む宣言が、どれも1件以上の実ファイルに当たっている" {
            val unmatched = projectArchitecture.process { model ->
                model.declaredEntries
                    // Entries without a wildcard are `required`, and a missing one is already
                    // reported as `[MissingFile]` by the check itself. `Ignore` and plain
                    // `Directory` entries claim no file and have nothing to match.
                    .filter { it.kind == LayoutEntryKind.File || it.kind == LayoutEntryKind.AnyFile }
                    .filterNot { it.required }
                    .filterNot { model.matchesSomething(it) }
                    .map { "${it.role.qualifiedName}  ${it.path}" }
            }

            unmatched shouldBe emptyList()
        }
    }
})

/**
 * Whether any file the declaring role owns is actually matched by [entry].
 *
 * `filesOf` answers with every file that role's layout allowed, so the glob is re-applied here
 * to pin the answer to this one declaration. An `anyFile()` entry is a directory pattern
 * rather than a file one, so it is matched against each file's parent.
 */
@OptIn(ExperimentalKatachiApi::class, InternalKatachiApi::class)
private fun ProjectModel.matchesSomething(entry: LayoutEntry): Boolean {
    val owned = filesOf(entry.role)
    return when (entry.kind) {
        LayoutEntryKind.AnyFile ->
            owned.any { entry.glob.matches(it.substringBeforeLast('/', missingDelimiterValue = "")) }

        else -> owned.any { entry.glob.matches(it) }
    }
}
