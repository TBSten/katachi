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

/**
 * The role of a stand-in implementation other modules' tests use.
 *
 * `:testing` follows the module path convention, so its package comes from `modulePackage`
 * rather than being written out the way `:architecture-test` has to write its own.
 */
fun DeclarationContainerScope.fake() = "Fake" {
    title = "Fake"
    summary = "A stand-in implementation kept in :testing, used by the tests of other modules"
    description = """
        A test implementation that satisfies the interfaces of `:data` with in-memory values
        only. `FakeUserRepository` implements `UserRepository` and `FakeSettingsRepository`
        implements `SettingsRepository`. Constructor arguments have defaults, so a test writes
        only the values that matter to it.

        The point of this role is that fakes sit in `main`, not in `src/test`. Code in
        `src/test` is visible only to its own module, so handing it to another module's tests
        means publishing it as production code. Users pull it in with
        `testImplementation(project(":testing"))`. `:testing` depends on `:data` with `api` so
        that the consumer receives the interfaces along with it.

        File names are `Fake*.kt`. Only stand-in implementations may live in `:testing`; if you
        want to add test helpers or custom assertions, add a role first. The tests themselves
        are a different role (test code) and live in `src/test`.

        Can be generated from a template. What you pass as `repository` is the name of the
        interface to implement as is (`UserRepository`), and `--arg template=testing.Fake --arg
        repository=UserRepository` produces `FakeUserRepository.kt`. Which domain package it
        goes into is decided by which `DataDomain` the start of the name matches.
    """.trimIndent()
    forbiddenContents = """
        Code called from production. Only the test compile path may depend on `:testing`; the
        `main` of `:app` and `:feature:*` must not refer to it.
    """.trimIndent()
    example("FakeUserRepository", "The in-memory implementation of UserRepository")
    example("FakeSettingsRepository", "The in-memory implementation of SettingsRepository")
    // Implements what the Repository template generates (`--arg name=` there is folded into
    // `repository` here), so run that one first: the fake of an interface that is not there
    // does not compile.
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg template=testing.Fake --arg repository=UserProfileRepository
    layout {
        ":testing".module {
            mainSourceSet / kotlin / modulePackage / "Fake${capture("repository")}".ktFile()
                .template {
                    val repository = captureValue("repository")
                    // The package a repository interface lives in is not part of its own name, so it
                    // is found the same way DataDomain.packageName itself is used elsewhere: by the
                    // domain name it starts with.
                    val domain = DataDomain.entries.firstOrNull { repository.startsWith(it.name) }
                    require(isPreview || domain != null) {
                        "--arg repository=$repository: must start with one of " +
                            DataDomain.entries.joinToString { it.name }
                    }
                    val packageName = "com.example.sample.data.${(domain ?: DataDomain.entries.first()).packageName}"

                    """
                        package com.example.sample.testing

                        import $packageName.$repository

                        /** In-memory [$repository] for tests of other modules. */
                        class Fake$repository(
                            private var value: String = "",
                        ) : $repository {
                            override fun load(): String = value
                        }
                    """.trimIndent() + "\n"
                }
        }
    }
}
