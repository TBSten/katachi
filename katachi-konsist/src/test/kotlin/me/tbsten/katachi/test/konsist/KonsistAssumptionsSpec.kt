package me.tbsten.katachi.test.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.provider.KoBaseProvider
import com.lemonappdev.konsist.api.provider.KoLocationProvider
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File
import java.lang.reflect.Modifier
import me.tbsten.katachi.konsist.KonsistScope

/**
 * What katachi is allowed to assume about Konsist 0.17.3, measured rather than remembered.
 *
 * Konsist 0.17.3 is the last release of that line (2024-12), so these are not assumptions
 * about "Konsist" but about one frozen artifact. Every one of them is load bearing: the
 * evaluator written on top of this scope calls `scopeFromExternalDirectories` with absolute
 * paths, narrows the result with `slice`, compares counts to notice files it silently lost,
 * and reads `location` for line numbers. When one of these goes red, the evaluator is wrong
 * rather than the test.
 */
class KonsistAssumptionsSpec : FreeSpec({
    "(1) scopeFromExternalDirectories が絶対パスのディレクトリを受け取る" {
        fixtureProject(
            "src/main/kotlin/com/example/Service.kt" to SERVICE_KT,
            "src/main/kotlin/com/example/Repository.kt" to REPOSITORY_KT,
        ) { root ->
            val scope = Konsist.scopeFromExternalDirectories(listOf(root.fixturePath("src")))

            scope.files.map { File(it.path).name }.sorted() shouldBe
                listOf("Repository.kt", "Service.kt")
        }
    }

    "(2) slice と KoPathProvider.path" - {
        "path は絶対で、fixture の下を指す" {
            fixtureProject("src/main/kotlin/com/example/Service.kt" to SERVICE_KT) { root ->
                val scope = Konsist.scopeFromExternalDirectories(listOf(root.fixturePath("src")))

                scope.files.single().path shouldBe
                    root.fixturePath("src/main/kotlin/com/example/Service.kt")
            }
        }

        "slice が path の集合でファイルを絞り込める" {
            fixtureProject(
                "src/main/kotlin/com/example/Service.kt" to SERVICE_KT,
                "src/main/kotlin/com/example/Repository.kt" to REPOSITORY_KT,
            ) { root ->
                val wanted = hashSetOf(root.fixturePath("src/main/kotlin/com/example/Service.kt"))
                val scope = Konsist.scopeFromExternalDirectories(listOf(root.fixturePath("src")))

                // The evaluator spells the comparison exactly like this: Konsist hands back the
                // OS separator, so it is normalised before being looked up in the wanted set.
                val sliced = scope.slice { File(it.path).invariantSeparatorsPath in wanted }

                sliced.files.map { it.path } shouldBe wanted.toList()
            }
        }

        "宣言からも同じ絶対パスが読める" {
            fixtureProject("src/main/kotlin/com/example/Service.kt" to SERVICE_KT) { root ->
                val scope = Konsist.scopeFromExternalDirectories(listOf(root.fixturePath("src")))

                scope.classes().single().path shouldBe
                    root.fixturePath("src/main/kotlin/com/example/Service.kt")
            }
        }
    }

    "(3) 何も無いスコープが例外にならない" - {
        "ディレクトリ集合が空" {
            Konsist.scopeFromExternalDirectories(emptyList()).files.shouldBeEmpty()
        }

        "ディレクトリはあるが Kotlin ファイルが 1 つも無い" {
            fixtureProject("docs/notes.md" to "# notes\n") { root ->
                val scope = Konsist.scopeFromExternalDirectories(listOf(root.fixturePath("docs")))

                scope.files.shouldBeEmpty()
                scope.classes().shouldBeEmpty()
            }
        }
    }

    "(4) スコープに入るファイルの種類" - {
        "build/ の下の .kt も入る" {
            fixtureProject(
                "src/Main.kt" to MAIN_KT,
                "build/generated/Generated.kt" to GENERATED_KT,
            ) { root ->
                val scope = Konsist.scopeFromExternalDirectories(listOf(root.fixturePath(".")))

                scope.files.map { File(it.path).name } shouldContain "Generated.kt"
            }
        }

        ".md は入らない" {
            fixtureProject("src/Main.kt" to MAIN_KT, "src/notes.md" to "# notes\n") { root ->
                val scope = Konsist.scopeFromExternalDirectories(listOf(root.fixturePath("src")))

                scope.files.map { File(it.path).name } shouldBe listOf("Main.kt")
            }
        }

        // 設計（§9 層3 の 4 番）は「.kts がスコープに入る」と書いているが、**入らない**。
        // Konsist 0.17.3 の `File.isKotlinFile` は `name.endsWith(".kt")` ひとつで、
        // `build.gradle.kts` も `Task.kts` も `KotlinFileParser` に渡らない。
        // §7.3 の `konsistScopeOf` が `.kt` と `.kts` の両方を `wanted` に入れて件数を
        // 突き合わせると、.kts を覆った制約が必ず件数不一致になる。報告済み。
        ".kts は入らない（設計の想定と違う。ステップ7 の件数照合に効く）" {
            fixtureProject(
                "src/Main.kt" to MAIN_KT,
                "build.gradle.kts" to "plugins { }\n",
                "src/Task.kts" to "val task = 1\n",
            ) { root ->
                val scope = Konsist.scopeFromExternalDirectories(listOf(root.fixturePath(".")))

                val names = scope.files.map { File(it.path).name }
                names shouldBe listOf("Main.kt")
                names shouldNotContain "build.gradle.kts"
                names shouldNotContain "Task.kts"
            }
        }
    }

    "(5) projectPath は fixture 相対にならない" {
        fixtureProject("src/main/kotlin/com/example/Service.kt" to SERVICE_KT) { root ->
            val scope = Konsist.scopeFromExternalDirectories(listOf(root.fixturePath("src")))
            val file = scope.files.single()

            // Konsist resolves `projectPath` against a root it infers for itself -- the working
            // directory's project, which under Gradle is this repository, not the fixture. That
            // is why katachi reports `path` made relative by hand and never touches this.
            file.projectPath shouldNotBe "src/main/kotlin/com/example/Service.kt"
            file.projectPath shouldNotBe "/src/main/kotlin/com/example/Service.kt"
            file.projectPath.endsWith("/src/main/kotlin/com/example/Service.kt") shouldBe true
        }
    }

    "(6) KoLocationProvider.location が読める" - {
        "location は path:line:column の形で返る" {
            fixtureProject("src/main/kotlin/com/example/Service.kt" to SERVICE_KT) { root ->
                val scope = Konsist.scopeFromExternalDirectories(listOf(root.fixturePath("src")))
                val klass = scope.classes().single()

                val path = root.fixturePath("src/main/kotlin/com/example/Service.kt")
                klass.location shouldBe "$path:3:1"
            }
        }

        "スコープ内のどの宣言を読んでも投げない" {
            fixtureProject(
                "src/main/kotlin/com/example/Service.kt" to SERVICE_KT,
                "src/main/kotlin/com/example/Repository.kt" to REPOSITORY_KT,
            ) { root ->
                val scope = Konsist.scopeFromExternalDirectories(listOf(root.fixturePath("src")))

                // `location` is computed from PSI offsets and is documented as able to throw
                // when there is none, so the evaluator wraps every read. Reading every
                // declaration of a parsed scope is the case katachi actually hits, and none of
                // them throws -- but a file declaration is not a `KoLocationProvider` at all
                // (it carries `KoNameProvider` and `KoPathProvider` only). A rejection reported
                // against a whole file therefore has `line = null` by construction, which is
                // why the evaluator narrows with `as?` instead of assuming the interface.
                val byType = scope.declarations().associate { declaration ->
                    val locatable = declaration as? KoLocationProvider
                    val location = locatable?.let { runCatching { it.location }.getOrNull() }
                    declaration::class.simpleName.orEmpty() to when {
                        locatable == null -> "not locatable"
                        location == null -> "threw"
                        else -> "readable"
                    }
                }

                byType shouldBe mapOf(
                    "KoFileDeclarationCore" to "not locatable",
                    "KoPackageDeclarationCore" to "readable",
                    "KoClassDeclarationCore" to "readable",
                    "KoFunctionDeclarationCore" to "readable",
                )
            }
        }
    }

    "(7) api.verify の public 拡張はちょうど 12 本" {
        // The shadow written in the next step has to cover every one of these, so the count is
        // pinned here rather than counted by hand at review time. `$default` bridges are the
        // compiler's, not part of the surface.
        val verify = Class.forName(VERIFY_CLASS_NAME)
        val declared = verify.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) }
            .filterNot { it.name.endsWith("\$default") }

        declared.size shouldBe 12

        declared.map { "${it.name}(${it.parameterTypes.first().simpleName})" }.sorted() shouldBe
            listOf(
                "assertEmpty(List)",
                "assertEmpty(Sequence)",
                "assertFalse(KoBaseProvider)",
                "assertFalse(List)",
                "assertFalse(Sequence)",
                "assertNotEmpty(List)",
                "assertNotEmpty(Sequence)",
                "assertNotNull(KoBaseProvider)",
                "assertNull(KoBaseProvider)",
                "assertTrue(KoBaseProvider)",
                "assertTrue(List)",
                "assertTrue(Sequence)",
            )
    }

    "(7b) その 12 本すべてが KonsistScope の宣言メンバとして shadow されている" {
        // The containment is only as good as its coverage: one spelling left unshadowed is a
        // way back to `assertTrue`, and nothing about the call site would look different. So
        // the two surfaces are compared as sets rather than counted.
        //
        // A member extension of an interface takes its extension receiver as the first JVM
        // parameter, which is exactly what the top level extension does too -- so `(name,
        // first parameter)` is directly comparable between the two.
        val konsist = Class.forName(VERIFY_CLASS_NAME).declaredMethods
            .filter { Modifier.isPublic(it.modifiers) }
            .filterNot { it.name.endsWith("\$default") }
            .map { "${it.name}(${it.parameterTypes.first().simpleName})" }
            .toSet()

        val shadowed = KonsistScope::class.java.declaredMethods
            .filterNot { it.name.endsWith("\$default") }
            .filter { it.parameterTypes.isNotEmpty() }
            .map { "${it.name}(${it.parameterTypes.first().simpleName})" }
            .toSet()

        withClue("KonsistScope が shadow していない api.verify の綴り") {
            konsist.filterNot { it in shadowed }.shouldBeEmpty()
        }
        // Reads as an assertion about the size of the hole rather than about the size of the
        // shadow: a spelling katachi adds of its own (`must`) is fine, a Konsist one is not.
        konsist.size shouldBe 12
    }

    "(8) KDoc に書いた語彙がそのままコンパイルできる" {
        // Nothing runs these -- `KonsistScope` has no implementation until the next step. That
        // they compile is the assertion: `hasInternalModifier` and `hasPublicOrDefaultModifier`
        // are properties in 0.17.3 (javap: `getHasInternalModifier()`), so a `()` anywhere in
        // the documented examples would be an unresolved reference rather than a wrong answer.
        val fromKonsistScopeKDoc: KonsistScope.() -> Unit = {
            classes().must { it.hasInternalModifier }
            declarations().mustNot { it.toString().contains("TODO") }
        }

        val fromMustKDoc: KonsistScope.() -> Unit = {
            classes().withNameEndingWith("UseCase")
                .must { klass -> klass.hasFunction { it.name == "invoke" } }
            functions().must { it.hasPublicOrDefaultModifier }
        }

        val fromMustNotAndEmptyKDoc: KonsistScope.() -> Unit = {
            files.mustNot { file -> file.imports.any { it.name.startsWith("android.") } }
            classes().filter { it.hasPublicOrDefaultModifier }.mustBeEmpty()
        }

        // `declarations()` returns `List<KoBaseDeclaration>`, whose only supertype is
        // `KoBaseProvider`. This is why `must` is bounded there and not at `KoPathProvider`:
        // with the tighter bound, the most natural Konsist query of all would not compile and
        // a user would be pushed straight back to `assertTrue`.
        val bound: List<KoBaseProvider> = emptyList()
        bound.isEmpty() shouldBe true

        listOf(fromKonsistScopeKDoc, fromMustKDoc, fromMustNotAndEmptyKDoc).size shouldBe 3
    }
})

/** The single file `com.lemonappdev.konsist.api.verify` compiles to. */
private const val VERIFY_CLASS_NAME: String =
    "com.lemonappdev.konsist.api.verify.KoDeclarationAndProviderAssertKt"

private val SERVICE_KT: String = """
    package com.example

    internal class Service {
        fun run() = Unit
    }
""".trimIndent() + "\n"

private val REPOSITORY_KT: String = """
    package com.example

    class Repository
""".trimIndent() + "\n"

private val MAIN_KT: String = """
    package com.example

    class Main
""".trimIndent() + "\n"

private val GENERATED_KT: String = """
    package generated

    class Generated
""".trimIndent() + "\n"
