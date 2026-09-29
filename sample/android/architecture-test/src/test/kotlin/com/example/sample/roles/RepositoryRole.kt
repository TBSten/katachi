package com.example.sample.roles

import com.example.sample.forbiddenContents
import com.example.sample.groups.DataDomain
import com.example.sample.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template

/** The role that fetches and stores data, declared as an interface and its implementation. */
fun DeclarationContainerScope.repository() = "Repository" {
    title = "Repository"
    summary = "Fetching and storing data. Interfaces and implementations sit side by side in :data, in one package per subject (user / settings)"
    description = """
        The only role of `:data`, taking on fetching and storing data. Packages are split per
        subject (`user` / `settings`), and the interface (`*Repository.kt`) and the
        implementation (`*RepositoryImpl.kt`) sit side by side in each. File names start with
        the package name (`User*Repository.kt` for `user`). Callers depend only on the
        interface, and the `Impl` name is never written in a ViewModel's arguments.

        This role has two `layout { }` blocks. The place is the same package and only the file
        name pattern differs, so the `description` of each `layout` explains which is the
        interface and which is the implementation. It is also a real example of one role having
        several places.

        `:data` depends on no other module and is the lowest layer of this app. It touches
        neither Android nor Compose, so the interfaces read as plain Kotlin. A ViewModel
        receives the interface through its constructor, and tests swap it for `Fake*` from
        `:testing`.

        Only the `User*Repository.kt` / `User*RepositoryImpl.kt` part can be generated from a
        template (`UserRepository.kt` / `UserRepositoryImpl.kt` themselves are handwritten in each
        domain from the start and are not generation targets). The id is split per domain, and
        `--arg template=data.Repository.user,data.Repository.userImpl --arg name=Cache` creates
        both at once.
    """.trimIndent()
    forbiddenContents = """
        - Types for screens. Repacking into `UiState` is the ViewModel's job, and `:data` does not know `:ui`
        - Files other than `*Repository.kt` and `*RepositoryImpl.kt`. If you want to split out
          DTOs or data sources, add a role first
        - Packages that span subjects. If a `user` type gets mixed into `settings`, split the packages again
    """.trimIndent()
    example("UserRepository", "The interface for fetching and storing the user")
    example("UserRepositoryImpl", "The implementation of UserRepository")
    // Two layouts: a role may live in more than one place. Here the interface
    // (`*Repository.kt`) and the implementation (`*RepositoryImpl.kt`). Both sit in
    // the same package, so `description` is what tells the two apart — which is the
    // question it exists to answer.
    //
    // One package per DataDomain, and a file name has to start with that domain's name: a
    // directory `capture(...)` has no value the layout could read back to write that requirement
    // once, so it is written out per domain instead (see DataDomain's KDoc). `capture(...)`
    // would still be enough to just choose which package a generated file goes in.
    // Written twice because katachi's `*` matches one character or more, so
    // `User*Repository` alone would not accept `UserRepository` itself.
    //
    // The domain's own file (`UserRepository.kt`, no capture) is written by hand and carries no
    // template. The capturing pattern's `.template` is attached inside the DataDomain loop, with
    // an id made from the loop variable (`user` / `userImpl`, `settings` / `settingsImpl`): one
    // source line run once per domain is still one template per domain, as long as each run
    // passes its own id.
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg template=data.Repository.user --arg name=Profile
    layout {
        ":data".module {
            mainSourceSet / kotlin / modulePackage {
                description = "The interface. The type callers depend on"
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
    layout {
        ":data".module {
            mainSourceSet / kotlin / modulePackage {
                description = "The implementation. Sits in the same package as the interface"
                // Each id shares its interface template's `name` capture: passing it once binds
                // both, when the two are generated together.
                DataDomain.entries.forEach { domain ->
                    domain.packageName {
                        "${domain.name}RepositoryImpl".ktFile()
                        "${domain.name}${capture("name")}RepositoryImpl".ktFile()
                            .template(id = "${domain.templateId}Impl") {
                                repositoryImplContent(domain, captureValue("name"))
                            }
                    }
                }
            }
        }
    }
}

/** The id a [DataDomain]'s interface template answers to: `user` for [DataDomain.User]. */
private val DataDomain.templateId: String get() = name.replaceFirstChar(Char::lowercaseChar)

/** The interface [repository]'s `.template { }` writes, for one [DataDomain]. */
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

/** The implementation [repository]'s `.template { }` writes, for one [DataDomain]. */
private fun repositoryImplContent(domain: DataDomain, name: String): String {
    val repository = "${domain.name}${name}Repository"
    val packageName = "com.example.sample.data.${domain.packageName}"
    return """
        package $packageName

        /** Production implementation of [$repository]. */
        class ${repository}Impl : $repository {
            override fun load(): String = ""
        }
    """.trimIndent() + "\n"
}
