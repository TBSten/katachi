package com.example

import com.example.groups.coreGroup
import com.example.groups.gradleGroup
import com.example.groups.testingGroup
import com.example.groups.toolGroup
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.ModulePackage
import me.tbsten.katachi.dsl.gradle.capitalizedModuleNamePackage

/**
 * Where a module keeps its sources, below its own source set.
 *
 * `modulePackage` is not a katachi symbol: it is a `val` the project declares once and writes
 * into every `layout { }`, and it stands for a different directory in each module it is read
 * in. Here the application is the root project, so `":"` alone derives nothing and the base
 * package is the whole answer — `com/example`.
 *
 * `:architecture-test` deliberately does not use it. Its sources are in `com.example` as well,
 * while this strategy would derive `com/example/architectureTest` from the module name, so the
 * roles covering that module write their package out instead.
 */
val modulePackage: ModulePackage = capitalizedModuleNamePackage("com.example")

/**
 * The architecture of this sample, described with katachi.
 *
 * This is the fourth sample, and the only one whose subject is **how a user writes a processor**.
 * The application it describes -- three files under `src/main/kotlin` -- is as small as it can
 * be while still giving the roles something to cover; the three processors in
 * `processors/` are what a reader came for, and the `testing/Processor` role is what declares
 * them.
 *
 * The definition is split one declaration per file: `roles/<Name>Role.kt` holds one role and
 * `groups/<Name>Group.kt` holds one group, which says what it is made of by calling the role
 * functions in order. None of those functions may be `inline`: katachi captures the declaration
 * site from the stack, and an inlined frame reports the caller's file with a line number past
 * its end.
 *
 * katachi denies by default, so this is an allow list: every file in the project has to be
 * covered by some role, and anything else fails `ProjectArchitectureTest`.
 */
val projectArchitecture: Architecture = architecture {
    title = "自作プロセッサのサンプル"
    description = "katachi の定義を読む processor を、利用者が自分で書く方法だけを見せるサンプル。" +
        "このページ以下はすべて `katachiDocs` が生成したもので、手では書かない。"

    coreGroup()
    testingGroup()
    gradleGroup()
    toolGroup()
}
