package com.example.sample

import com.example.sample.groups.appGroup
import com.example.sample.groups.buildGroup
import com.example.sample.groups.dataGroup
import com.example.sample.groups.featureGroup
import com.example.sample.groups.testingGroup
import com.example.sample.groups.toolGroup
import com.example.sample.groups.uiGroup
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.gradle.ModulePackage
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.capitalizedModuleNamePackage

/**
 * Where a module keeps its Kotlin sources, below its own source set.
 *
 * Declared once, here, and written as `modulePackage` inside every `module { }` block that
 * follows the convention: `:ui` means `com/example/sample/ui`, `:feature:home` means
 * `com/example/sample/feature/home`. It is a strategy rather than a string because one
 * `val` has to stand for a different directory in every module it is read in.
 *
 * Two modules of this sample do **not** follow it and write their package out as a plain
 * key instead: `:app`, whose sources sit directly in `com.example.sample` because it is the
 * application itself, and `:architecture-test`, which is not a layer of the app at all.
 * Bending the strategy into covering those two would hide, in a lambda, the very fact that
 * they are exceptions.
 *
 * Declared next to [projectArchitecture] rather than in one role file because every group
 * uses it and none of them owns it.
 */
val modulePackage: ModulePackage = capitalizedModuleNamePackage("com.example.sample")

/**
 * The architecture of this sample, written the way a user of katachi would write it.
 *
 * The definition is split one declaration per file: `roles/<Name>Role.kt` holds one role and
 * `groups/<Name>Group.kt` holds one group, which says what it is made of by calling the role
 * functions in order. Both are extensions on `DeclarationContainerScope` — the scope that
 * `architecture { }` and `"...".group { }` share — so a role can be moved into another group
 * without touching the role's own file. This file only calls the seven group functions, so it
 * stays short no matter how many roles the app grows.
 *
 * The file *names* carry the convention, so nothing has to be written twice: a role named
 * `"UiCore"` belongs in `roles/UiCoreRole.kt`, a group named `"build"` in
 * `groups/BuildGroup.kt`. A `build` *directory* would have been invisible to git — `.gitignore`
 * ignores `build/` at every level — but that entry matches directories only, so a file named
 * after the group is safe.
 *
 * None of those functions may be `inline`. An inlined frame reports the caller's file with a
 * line number past its end, and katachi captures the declaration site from the stack, so the
 * violation would point at a line nobody wrote. Written once here rather than repeated in
 * twenty-six files; [ProjectArchitectureSpec] is what actually holds the line.
 *
 * Every `layout { }` is written in terms of Gradle: a place is named by the module path it
 * belongs to (`":feature:*".module { }`), the source set inside it (`mainSourceSet`) and
 * [modulePackage], rather than by spelling the directories out. `:feature:*` is the one to
 * read first — it is matched against the modules that exist, and what the `*` captured is
 * read back as `wildcards[0]`, which is what ties a module's name to the names of the files
 * in it.
 *
 * Building this value reads nothing from disk — the `layout { }` blocks are deferred until
 * a check runs — so it is safe to hold in a top level `val`.
 *
 * [ProjectArchitectureTest] is the whole of what a user writes. [ProjectArchitectureSpec]
 * is katachi's own integration test on top of it, and asserts among other things that every
 * declaration's `declaredAt` points at the file it is actually written in — not at this one
 * — which is how the sample notices if capturing the declaration site ever breaks in a real
 * Android unit test run.
 */
val projectArchitecture: Architecture = architecture {
    featureGroup()
    uiGroup()
    dataGroup()
    appGroup()
    testingGroup()
    buildGroup()
    toolGroup()
}
