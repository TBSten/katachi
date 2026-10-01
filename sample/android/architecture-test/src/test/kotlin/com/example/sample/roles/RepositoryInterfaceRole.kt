@file:OptIn(ExperimentalKatachiApi::class)

package com.example.sample.roles

import com.example.sample.forbiddenContents
import com.example.sample.groups.DataDomain
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template

/** The role of the interface callers depend on to fetch and store data. */
fun DeclarationContainerScope.repositoryInterface() = "RepositoryInterface" {
    title = "Repository interface"
    summary = "The interface for fetching and storing data, in :data, in one package per subject (user / settings)"
    description = """
        The interface of a repository, kept in `:data`. Packages are split per subject
        (`user` / `settings`), and the file name starts with the package name
        (`User*Repository.kt` for `user`). Callers depend only on this interface, and the
        implementation's name is never written in a ViewModel's arguments.

        `:data` depends on no other module and is the lowest layer of this app. It touches
        neither Android nor Compose, so the interfaces read as plain Kotlin. A ViewModel
        receives the interface through its constructor, and tests swap it for `Fake*` from
        `:testing`.

        Only the `User*Repository.kt` part can be generated from a template
        (`UserRepository.kt` itself is handwritten in each domain from the start and is not a
        generation target). The id is split per domain, and
        `--arg template=data.RepositoryInterface.user,data.RepositoryImplementation.user
        --arg name=Cache` creates the interface and its implementation at once.
    """.trimIndent()
    forbiddenContents = """
        - Types for screens. Repacking into `UiState` is the ViewModel's job, and `:data` does not know `:ui`
        - The implementation (`*RepositoryImpl.kt`). It is the RepositoryImplementation role
        - Packages that span subjects. If a `user` type gets mixed into `settings`, split the packages again
    """.trimIndent()
    example("UserRepository", "The interface for fetching and storing the user")
    // One package per DataDomain, and a file name has to start with that domain's name: a
    // directory `capture(...)` has no value the layout could read back to write that requirement
    // once, so it is written out per domain instead (see DataDomain's KDoc). `capture(...)`
    // would still be enough to just choose which package a generated file goes in.
    // Written twice because katachi's `*` matches one character or more, so
    // `User*Repository` alone would not accept `UserRepository` itself.
    //
    // The domain's own file (`UserRepository.kt`, no capture) is written by hand and carries no
    // template. The capturing pattern's `.template` is attached inside the DataDomain loop, with
    // an id made from the loop variable (`user` / `settings`): one source line run once per
    // domain is still one template per domain, as long as each run passes its own id.
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg template=data.RepositoryInterface.user --arg name=Profile
    layout {
        "data" {
            mainSourceSet / kotlin / "com/example/sample/data" {
                DataDomain.entries.forEach { domain ->
                    domain.packageName {
                        "${domain.name}Repository".ktFile()
                        "${domain.name}${capture("name")}Repository".ktFile()
                            .template(id = domain.templateId) { repositoryInterfaceContent(domain, captureValue("name")) }
                    }
                }
            }
        }
    }
}

/** The interface [repositoryInterface]'s `.template { }` writes, for one [DataDomain]. */
private fun repositoryInterfaceContent(domain: DataDomain, name: String): String {
    val repository = "${domain.name}${name}Repository"
    val packageName = "com.example.sample.data.${domain.packageName}"
    return """
        package $packageName

        /** Reads ${name.lowercase()} data. */
        interface $repository {
            // TODO: replace with what this repository actually reads and writes.
            fun load(): String
        }
    """.trimIndent() + "\n"
}

