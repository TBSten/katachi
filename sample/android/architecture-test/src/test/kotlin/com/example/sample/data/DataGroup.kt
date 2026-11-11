@file:OptIn(ExperimentalKatachiApi::class)

package com.example.sample.data

import com.example.sample.data.repositoryImplementation
import com.example.sample.data.repositoryInterface
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.dataGroup() = "Data".group {
    title = "Data layer"
    summary = "What :data holds: fetching and storing data"
    description = """
        The `:data` module. For now it has two roles, RepositoryInterface and
        RepositoryImplementation, with the interface and its implementation side by side in one
        package per subject (`user` / `settings`). Each package is one entry of `DataDomain`; to
        add a subject, add one line there.

        It is the lowest layer of this app and depends on no other module. It touches neither
        Android nor Compose, so it can be read without pulling in `:ui` or `:feature:*`. A
        ViewModel receives the interface here through its constructor, and tests swap it for a
        fake from `:testing`.

        It is a group even though it is small because this is where things will grow. Data sources
        or DTOs go here when they need to be split out. For now, any file in `:data` other than
        a repository interface or implementation is a violation, so there is no "put it down first
        and think later".
    """.trimIndent()

    repositoryInterface()
    repositoryImplementation()

    // A repository's interface, its implementation and its fake, as one set: the members come
    // from two groups (`Data` and `Testing`). Each member gets its own value by an expression;
    // the fake is told the interface it stands in for, `User<name>Repository`.
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg template=Data.NewRepository --arg name=Profile
    "NewRepository".templateSet {
        title = "New repository"
        summary = "A user repository's interface, its implementation and its fake"
        val name by stringParameter()

        template("Data.RepositoryInterface.user") {
            arg("name", name)
        }
        template("Data.RepositoryImplementation.user") {
            arg("name", name)
        }
        template("Testing.Fake") {
            arg("repository", "User${name}Repository")
        }
    }
}

enum class DataDomain {
    User,
    Settings,
    ;

    val packageName: String get() = name.lowercase()

    val templateId: String get() = name.replaceFirstChar(Char::lowercaseChar)
}
