package me.tbsten.katachi.test.architecture.groups

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.test.architecture.roles.buildLogic
import me.tbsten.katachi.test.architecture.roles.gradleModule
import me.tbsten.katachi.test.architecture.roles.gradleRoot

/**
 * The roles of the build definition.
 *
 * Build files are checked like everything else and are noise in the generated documentation,
 * so the whole group opts out with `documented = false`.
 *
 * What the build *writes* needs no role: `build/` and `.kotlin/` are in `.gitignore` and the
 * default `files = gitTracked()` never offers them. Every `.module { }` still injects
 * `"build".ignore()` of its own, so the reason stays readable under `files = wholeTree()` too.
 *
 * ## Why no `konsist { }` anywhere in this group
 *
 * Konsist 0.17.3 parses a file only when its name ends in `.kt`. Every file here is a `.kts`
 * script or a properties file, so a constraint would cover files and find none it can read —
 * which katachi reports as `KatachiKonsistNoKotlinFilesException` rather than passing quietly.
 */
fun DeclarationContainerScope.buildGroup() = "build".group {
    documented = false
    title = "ビルド"

    gradleRoot()
    gradleModule()
    buildLogic()
}
