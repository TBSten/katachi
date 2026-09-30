@file:OptIn(ExperimentalKatachiApi::class)

package com.example.sample.roles

import com.example.sample.forbiddenContents
import com.example.sample.groups.DataDomain
import com.example.sample.modulePackage
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template

/** The role of the production implementation of a repository interface. */
fun DeclarationContainerScope.repositoryImplementation() = "RepositoryImplementation" {
    title = "Repository implementation"
    summary = "The implementation of a repository interface, in :data, next to the interface"
    description = """
        The implementation of a repository, kept in `:data` in the same package as its interface
        (`user/UserRepositoryImpl.kt`). Callers never name it: the interface is what a
        ViewModel receives, and wiring the implementation in is the only place it appears.

        Only the `User*RepositoryImpl.kt` part can be generated from a template
        (`UserRepositoryImpl.kt` itself is handwritten in each domain from the start). Each id
        shares its interface template's `name` capture, so passing `name` once binds both when
        they are generated together
        (`--arg template=data.RepositoryInterface.user,data.RepositoryImplementation.user`).
    """.trimIndent()
    forbiddenContents = """
        - The interface (`*Repository.kt`). It is the RepositoryInterface role
        - Types for screens. `:data` does not know `:ui`
    """.trimIndent()
    example("UserRepositoryImpl", "The implementation of UserRepository")
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg template=data.RepositoryImplementation.user --arg name=Profile
    layout {
        ":data".module {
            mainSourceSet / kotlin / modulePackage {
                DataDomain.entries.forEach { domain ->
                    domain.packageName {
                        "${domain.name}RepositoryImpl".ktFile()
                        "${domain.name}${capture("name")}RepositoryImpl".ktFile()
                            .template(id = domain.templateId) {
                                repositoryImplContent(domain, captureValue("name"))
                            }
                    }
                }
            }
        }
    }
}

/** The implementation [repositoryImplementation]'s `.template { }` writes, for one [DataDomain]. */
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
