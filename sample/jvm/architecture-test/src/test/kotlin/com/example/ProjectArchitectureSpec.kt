package com.example

import com.example.application.apiRoles
import com.example.application.dataRoles
import com.example.application.domainRoles
import com.example.gradle.gradleRoles
import com.example.testing.testingRoles
import com.example.tool.toolRoles
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.InternalKatachiApi
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.capitalizedModuleNamePackage
import me.tbsten.katachi.dsl.flattenLayout
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.kotlin.ktsFile

/**
 * Checks that [projectArchitecture] builds into the model we expect, and that the check it
 * drives actually looks at this project.
 *
 * katachi's own integration test, not part of adopting katachi: a project that uses katachi
 * writes `ProjectArchitectureTest` and nothing else. katachi's unit tests prove the DSL and
 * the traversal work in isolation; this proves they still work when the definition is
 * written by a user, in a user's build, against a katachi resolved through a composite
 * build.
 *
 * Since the definition was split into `ArchitectureScope` extension functions, it also
 * proves the harder half: declaration sites still point at the file the user wrote, even
 * though `ProjectArchitecture.kt` no longer contains a single `group` call.
 *
 * `validate()` is `@InternalKatachiApi` on purpose - a user asserts, and does not read the
 * violations - so this file opts in where a user would not have to.
 */
@OptIn(InternalKatachiApi::class)
class ProjectArchitectureSpec : FreeSpec({
    "宣言した group がすべてモデルに含まれる" {
        projectArchitecture.allGroups.map { it.qualifiedName }.toSet() shouldBe setOf(
            "api",
            "domain",
            "data",
            "app",
            "testing",
            "build",
            "tool",
        )
    }

    "宣言した役割がすべて group のパス付きでモデルに含まれる" {
        projectArchitecture.allRoles.map { it.qualifiedName }.toSet() shouldBe setOf(
            "api/Controller",
            "api/KtorPlugin",
            "domain/Service",
            "domain/Model",
            "data/Repository",
            "app/Entrypoint",
            "app/ServerConfig",
            "testing/Test",
            "testing/ArchitectureDefinition",
            "build/Gradle",
            "tool/Git",
        )
    }

    "役割の宣言位置として、それを書いた拡張関数のファイルの行番号が取れる" {
        // Guards the stack-trace based capture in a real user build: if the frame filter
        // ever starts skipping user code, this reports the test runner's file instead.
        val controller = projectArchitecture.allRoles.single { it.qualifiedName == "api/Controller" }
        controller.declaredAt.fileName shouldBe "ApiRoles.kt"
        (controller.declaredAt.lineNumber > 0) shouldBe true
    }

    "group の宣言位置も、それを書いた拡張関数のファイルである" {
        val api = projectArchitecture.allGroups.single { it.qualifiedName == "api" }
        api.declaredAt.fileName shouldBe "ApiRoles.kt"
    }

    "宣言位置が ProjectArchitecture.kt ではなく、複数のファイルにまたがる" {
        // The point of splitting the definition: `ProjectArchitecture.kt` only calls the
        // extension functions, so nothing may be attributed to it. If katachi captured the
        // frame one level out, every declaration would collapse onto this one file.
        val files = (projectArchitecture.allGroups.map { it.declaredAt } + projectArchitecture.allRoles.map { it.declaredAt })
            .map { it.fileName }
            .distinct()
            .sorted()

        files shouldContainExactly listOf(
            "ApiRoles.kt",
            "AppRoles.kt",
            "DataRoles.kt",
            "DomainRoles.kt",
            "GradleRoles.kt",
            "TestingRoles.kt",
            "ToolRoles.kt",
        )
    }

    "group ごとに、期待したファイルで宣言されている" {
        projectArchitecture.allGroups.forEach { group ->
            group.declaredAt.fileName shouldBe DECLARING_FILES.getValue(group.qualifiedName)
        }
    }

    "役割は、それが属する group と同じファイルで宣言されている" {
        projectArchitecture.allRoles.forEach { role ->
            val groupName = role.qualifiedName.substringBeforeLast('/')
            role.declaredAt.fileName shouldBe DECLARING_FILES.getValue(groupName)
        }
    }

    "捕捉した行番号の行に、その宣言が実際に書かれている" {
        // The tests above only prove the file name and that the line is positive. This one
        // reads the source back, so a one-frame shift lands on `architecture {` or on the
        // `apiRoles()` call in ProjectArchitecture.kt and fails. No line number is
        // hard-coded, so editing the definition does not break it.
        projectArchitecture.allGroups.forEach { group ->
            val source = sourceLinesOf(group.declaredAt.fileName)
            source[group.declaredAt.lineNumber - 1] shouldContain "\"${group.name}\""
        }
        projectArchitecture.allRoles.forEach { role ->
            val source = sourceLinesOf(role.declaredAt.fileName)
            source[role.declaredAt.lineNumber - 1] shouldContain "\"${role.name}\""
        }
    }

    "documented = false を付けた group だけが documented = false になる" {
        projectArchitecture.allGroups
            .filterNot { it.documented }
            .map { it.qualifiedName } shouldBe listOf("build", "tool")
    }

    "documented を省略した役割はすべて documented = true になる" {
        projectArchitecture.allRoles.filterNot { it.documented } shouldBe emptyList()
    }

    "title を省略しなかった役割は指定した表示名を持つ" {
        val model = projectArchitecture.allRoles.single { it.qualifiedName == "domain/Model" }
        model.title shouldBe "モデル"
        model.examples.map { it.name } shouldBe listOf("Health")
    }

    "すべての役割が layout を1つ持つ" {
        projectArchitecture.allRoles.filter { it.layouts.size != 1 } shouldBe emptyList()
    }

    "役割を1つ欠いた定義では、その役割が覆っていたファイルが Unexpected になる" {
        // "assert() が通る" alone cannot tell a working check from one that walks nothing:
        // an empty traversal passes just as happily. Dropping `app/Entrypoint` leaves
        // `Application.kt` in a directory other roles still claim, so the file itself has to
        // be reached and matched for this to fail - which is the part being proven here.
        architectureWithoutEntrypointRole.validate().map { "[${it.label}] ${it.path}" } shouldBe listOf(
            "[UnexpectedFile] src/main/kotlin/com/example/Application.kt",
        )
    }

    "`.module { }` と sourceSet が、手で書いたディレクトリ宣言と同じエントリに展開される" {
        // The whole claim of step 3: the sugar is a shorthand and not a second way of
        // saying something slightly different. Written against `:architecture-test` rather
        // than the root project so that the module directory itself is part of the answer.
        val sugared = architecture {
            "sugar".group {
                "Sugared" {
                    layout {
                        ":architecture-test".module {
                            mainSourceSet / kotlin / "*".ktFile()
                        }
                    }
                }
            }
        }
        val handWritten = architecture {
            "sugar".group {
                "HandWritten" {
                    layout {
                        "architecture-test" {
                            "build".ignore()
                            "build.gradle".ktsFile()
                            "src/main" / "kotlin" / "*".ktFile()
                        }
                    }
                }
            }
        }

        // The role name is all that differs, so the entries are compared without it.
        sugared.flattenLayout().map { shapeOf(it) } shouldBe handWritten.flattenLayout().map { shapeOf(it) }
    }

    "modulePackage はモジュールごとに解決され、base package を変えると宣言先が変わる" {
        // Guards against the sugar going through without ever being read: if the package
        // levels came from anywhere but `modulePackage`, both of these would land on the
        // same path and the check would not be following the definition at all.
        fun pathsUnder(base: String): List<String> = architecture {
            "sugar".group {
                "Packaged" {
                    layout {
                        ":".module {
                            mainSourceSet / kotlin / capitalizedModuleNamePackage(base) / "Application".ktFile()
                        }
                    }
                }
            }
        }.flattenLayout().map { it.path }

        pathsUnder("com.example") shouldContain "src/main/kotlin/com/example/Application.kt"
        pathsUnder("com.other.app") shouldContain "src/main/kotlin/com/other/app/Application.kt"
    }

    "正しい定義では違反が1件も出ない" {
        // The same run ProjectArchitectureTest makes, read as a list rather than as a thrown
        // error, so a failure here names the violations instead of only the message.
        projectArchitecture.validate() shouldBe emptyList()
    }
})

/** A flattened entry without the role that declared it, for comparing two definitions. */
@OptIn(InternalKatachiApi::class)
private fun shapeOf(entry: LayoutEntry): String =
    "${entry.path}\t${entry.kind}\t${if (entry.required) "required" else "optional"}"

/**
 * [projectArchitecture] with `app/Entrypoint` removed, and nothing else changed.
 *
 * Deliberately broken, and deliberately kept out of `ProjectArchitecture.kt`: a reader
 * looking for the definition to copy should never meet it. The rest of the `app` group is
 * repeated here rather than reused, because `appRoles()` declares both roles at once and a
 * group name may only be declared once.
 */
private val architectureWithoutEntrypointRole: Architecture = architecture {
    apiRoles()
    domainRoles()
    dataRoles()
    "app".group {
        "ServerConfig" {
            layout {
                ":".module {
                    mainSourceSet {
                        "resources" {
                            "application.conf".file()
                            "logback.xml".file()
                        }
                    }
                }
            }
        }
    }
    testingRoles()
    gradleRoles()
    toolRoles()
}

/** Which file each group - and therefore each of its roles - is declared in. */
private val DECLARING_FILES: Map<String, String> = mapOf(
    "api" to "ApiRoles.kt",
    "domain" to "DomainRoles.kt",
    "data" to "DataRoles.kt",
    "app" to "AppRoles.kt",
    "testing" to "TestingRoles.kt",
    "build" to "GradleRoles.kt",
    "tool" to "ToolRoles.kt",
)

/** Cached so that reading a source file once per declaration does not hit the disk again. */
private val sourceLineCache = mutableMapOf<String, List<String>>()

/**
 * The lines of one file of the architecture definition, so a captured line number can be
 * compared against what is actually written there.
 *
 * A test task's working directory is its module directory - here
 * `sample/jvm/architecture-test` - but that is a default a build file can change, so the
 * source root is looked up by walking up from wherever the tests run, and the file itself
 * by name, so moving a role into another package needs no change here.
 */
private fun sourceLinesOf(fileName: String): List<String> = sourceLineCache.getOrPut(fileName) {
    // `getProperty` is a platform type; the JVM always defines `user.dir`.
    val workingDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
    val sourceRoot = generateSequence(workingDir) { it.parentFile }
        .map { File(it, "src/test/kotlin") }
        .firstOrNull { it.isDirectory }
    requireNotNull(sourceRoot) { "src/test/kotlin が $workingDir とその親に見つからない" }

    val source = sourceRoot.walkTopDown().firstOrNull { it.isFile && it.name == fileName }
    requireNotNull(source) { "$fileName が $sourceRoot 以下に見つからない" }.readLines()
}
