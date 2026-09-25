package me.tbsten.katachi.test.architecture.groups

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.test.architecture.roles.agentRule
import me.tbsten.katachi.test.architecture.roles.ci
import me.tbsten.katachi.test.architecture.roles.dokkaPlugin
import me.tbsten.katachi.test.architecture.roles.git

/**
 * The roles of the tooling around the project that is not the build itself.
 *
 * Like the `Gradle` group, these are checked but kept out of the generated documentation.
 *
 * `:tool:dokka` belongs here too: a Dokka plugin used only by this repository's own API
 * reference, never published and never depended on by the library.
 *
 * ## `.idea/` deliberately has no role
 *
 * `.idea/dictionaries/project.xml` is tracked by git, so `files = gitTracked()` offers it —
 * and yet declaring it would *create* a violation rather than remove one. `Scan.visitDirectory`
 * tests `FOREIGN_DIRECTORY_NAMES` (`.git`, `.gradle`, `.idea`) before it consults the layout
 * at all and returns, so the walk never reaches anything below `.idea/`. A declared file the
 * walk never visits is reported as `[MissingFile]`.
 *
 * Not declaring it is therefore the right answer here, and the gap is katachi's, not this
 * definition's: a project with tracked `.idea/` files has no way to declare them today.
 * Filed as a TODO against katachi rather than papered over.
 */
fun DeclarationContainerScope.toolGroup() = "tool".group {
    documented = false
    title = "ツール設定"

    ci()
    agentRule()
    git()
    dokkaPlugin()
}
