@file:OptIn(ExperimentalKatachiApi::class)

package com.example.sample.data

import com.example.sample.data.DataDomain
import com.example.sample.forbiddenContents
import com.example.sample.testing.fake
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.konsist.declarations.ktFileContent

fun DeclarationContainerScope.repositoryImplementation() = "RepositoryImplementation".role {
    title = "Repository implementation"
    summary = "The implementation of a repository interface, in :data, next to the interface"
    description = """
        The implementation of a repository, kept in `:data` in the same package as its interface
        (`user/UserRepositoryImpl.kt`). Callers never name it: the interface is what a
        ViewModel receives, and wiring the implementation in is the only place it appears.

        Only the `User*RepositoryImpl.kt` part can be generated from a template
        (`UserRepositoryImpl.kt` itself is handwritten in each domain from the start). A user
        repository's implementation is usually generated with its interface and fake as the
        template set `Data.NewRepository` (`--arg template=Data.NewRepository --arg name=Cache`),
        which passes the one `name` to each of them.
    """.trimIndent()
    forbiddenContents = """
        - The interface (`*Repository.kt`). It is the RepositoryInterface role
        - Types for screens. `:data` does not know `:ui`
    """.trimIndent()
    example("UserRepositoryImpl", "The implementation of UserRepository")
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg template=Data.RepositoryImplementation.user --arg name=Profile
    layout {
        "data" {
            mainSourceSet / kotlin / "com/example/sample/data" {
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
    // The class named after the file implements the interface of the same name without `Impl`,
    // and holds overrides and private state only. The package is the domain's directory the
    // layout put the file in (data/user/ -> com.example.sample.data.user).
    ktFileContent {
        `package`(matchingDirectory)

        `import`("kotlin.**")
        `import`("kotlinx.**")
        `import`("com.example.sample.data.**")

        required(`class`, fileName).superclass(fileName.removeSuffix("Impl")) { // UserRepositoryImpl : UserRepository
            allow(override, `fun`, "*")
            allow(private, anythingElse)
        }
    }
}

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
