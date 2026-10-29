package me.tbsten.katachi.intellij.data

import me.tbsten.katachi.intellij.data.definition.DefinitionLookup
import me.tbsten.katachi.intellij.data.definition.DefinitionMatcher
import me.tbsten.katachi.intellij.model.ConstraintBackend
import me.tbsten.katachi.intellij.model.ConstraintRange
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DeclarationVerdict
import me.tbsten.katachi.intellij.model.DefinitionModel
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.LayoutEntryKind
import me.tbsten.katachi.intellij.model.MatchOutcome
import me.tbsten.katachi.intellij.model.SourceDeclarationKind
import me.tbsten.katachi.intellij.testing.DefinitionFixtures
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Pins the plugin to what katachi really writes for `definition.json` (design 1.x): the fixtures
 * `contract/definition/real-{jvm,android,kmp}-definition.json` are the unedited outputs of
 * `:architecture-test:katachiInternalDefinitionJson` of `sample/{jvm,android,kmp}` (only `projectRoot`
 * is replaced by `/__ROOT__`). The files they name are looked up in the sample's own directory next to this
 * module, so a sample that moves a file fails here until the fixtures are taken again.
 *
 * Taking them again: copy the sample, point its `includeBuild` and version catalog at this checkout, `git init` + `git add -A`,
 * run `./gradlew :architecture-test:katachiInternalDefinitionJson`, and copy
 * `architecture-test/build/katachi/internalDefinitionJson/definition.json` over the fixture.
 */
class RealDefinitionContractTest {
    private class Sample(
        val name: String,
        val roles: Int,
        val groups: Int,
        val constraints: Int,
        /** Constraints that wrote `fileDeclarations`, and the files they explain (a file once per constraint). */
        val explainedFiles: Int,
        val declarations: Int,
        val allowedByAnythingElse: Int,
        /** Tracked files no role holds (an `UnexpectedFile`, a file in an `UnexpectedDirectory`, or a `CaptureMismatch`) that the sample's `katachi-baseline.json` holds back on purpose, in `git ls-files` order. */
        val heldBack: List<String> = emptyList(),
    ) {
        val fixture: String get() = "real-$name-definition"
        val definition: DefinitionModel get() = DefinitionFixtures.definition(fixture)

        /** The checkout's copy of the sample the fixture was taken from. */
        val directory: Path get() = Paths.get("../sample/$name").toAbsolutePath().normalize()
    }

    private val samples = listOf(
        Sample(
            "jvm", roles = 30, groups = 8, constraints = 7, explainedFiles = 6, declarations = 6, allowedByAnythingElse = 0,
            heldBack = listOf(
                "src/main/kotlin/com/example/controller/health/LegacyStatusController.kt",
                "src/main/kotlin/com/example/service/LegacyHealthCheck.kt",
            ),
        ),
        Sample("android", roles = 37, groups = 8, constraints = 10, explainedFiles = 15, declarations = 28, allowedByAnythingElse = 0,
            heldBack = listOf(
                "data/src/main/kotlin/com/example/sample/data/legacy/LegacyUserCache.kt",
                "feature/home/src/main/kotlin/com/example/sample/feature/home/HomeFormatter.kt",
            ),
        ),
        Sample("kmp", roles = 44, groups = 8, constraints = 8, explainedFiles = 10, declarations = 14, allowedByAnythingElse = 3,
            heldBack = listOf("data/src/androidMain/kotlin/com/example/kmp/data/user/UserAgent.android.kt"),
        ),
    )

    private val ktFileContentConstraints = { definition: DefinitionModel ->
        definition.roles.flatMap { role -> role.constraints.filter { it.backend == ConstraintBackend.KtFileContent }.map { role to it } }
    }

    @Test
    fun `3 サンプルの definition json はプラグインが読め 件数と形が katachi の出力どおりになる`() {
        for (sample in samples) {
            val definition = sample.definition
            assertEquals(sample.name, sample.roles, definition.roles.size)
            assertEquals(sample.name, sample.groups, definition.groups.size)
            assertEquals(sample.name, sample.constraints, definition.roles.sumOf { it.constraints.size })
            assertEquals("${sample.name}: no problem the walk or the explanation reported", emptyList<Any>(), definition.problems)
            assertEquals(Paths.get("/__ROOT__"), definition.projectRoot)
            assertTrue(sample.name, definition.architecture.isGitTracked)
            assertNotNull(sample.name, definition.architecture.declaredAt)
            for (role in definition.roles) {
                assertEquals("${sample.name} ${role.qualifiedName}", role.layout.indices.toList(), role.layout.map { it.index })
                for (constraint in role.constraints) {
                    assertTrue("${sample.name} ${role.qualifiedName}", constraint.entryIndices.all { it in role.layout.indices })
                }
            }
        }
    }

    @Test
    fun `実 katachi の値はプラグインが知らない種類として残らない`() {
        for (sample in samples) {
            val definition = sample.definition
            for (role in definition.roles) {
                for (entry in role.layout) {
                    assertTrue("${sample.name} ${role.qualifiedName}[${entry.index}] ${entry.kind}", entry.kind !is LayoutEntryKind.Unknown)
                }
                for (constraint in role.constraints) {
                    assertTrue("${sample.name} ${constraint.backend}", constraint.backend !is ConstraintBackend.Unknown)
                    assertTrue("${sample.name} ${constraint.range}", constraint.range !is ConstraintRange.Unknown)
                    for (file in constraint.fileDeclarations.orEmpty()) {
                        assertEquals("${sample.name} ${file.file}", null, file.unexplainedReason)
                        for (declaration in file.declarations) {
                            assertTrue("${sample.name} ${declaration.kind}", declaration.kind !is SourceDeclarationKind.Unknown)
                            assertTrue("${sample.name} ${declaration.verdict}", declaration.verdict !is DeclarationVerdict.Unknown)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `matchedFiles は空でなく打ち切られず サンプルのディレクトリに実在する`() {
        for (sample in samples) {
            val files = sample.definition.roles.flatMap { role -> role.layout.flatMap { entry -> entry.matchedFiles.map { role to entry to it } } }
            assertTrue("${sample.name} has matched files", files.size > 50)
            assertTrue("${sample.name}: nothing cut", sample.definition.roles.all { role -> role.layout.none { it.truncated } })
            val missing = files.filter { (_, file) -> !Files.isRegularFile(sample.directory.resolve(file)) }
            assertEquals("${sample.name}: matchedFiles that are not in ${sample.directory}", emptyList<String>(), missing.map { (_, file) -> file })
        }
    }

    @Test
    fun `fileDeclarations は ktFileContent の制約だけが持ち 説明したファイルは実在し その役割の matchedFiles に入る`() {
        for (sample in samples) {
            val definition = sample.definition
            val explained = ktFileContentConstraints(definition).flatMap { (role, constraint) -> constraint.fileDeclarations.orEmpty().map { role to it } }
            assertEquals("${sample.name}: files explained", sample.explainedFiles, explained.size)
            assertEquals("${sample.name}: declarations", sample.declarations, explained.sumOf { (_, file) -> file.declarations.size })
            assertEquals(
                "${sample.name}: AllowedByAnythingElse",
                sample.allowedByAnythingElse,
                explained.sumOf { (_, file) -> file.declarations.count { it.verdict == DeclarationVerdict.AllowedByAnythingElse } },
            )
            assertTrue(
                "${sample.name}: every real declaration is allowed (the samples pass their own check)",
                explained.all { (_, file) -> file.declarations.all { it.verdict.isAllowed } && file.missing.isEmpty() },
            )
            // The other backends do not explain.
            val others = definition.roles.flatMap { it.constraints }.filter { it.backend != ConstraintBackend.KtFileContent }
            assertTrue("${sample.name}: only ktFileContent writes fileDeclarations", others.all { it.fileDeclarations.isNullOrEmpty() })
            for ((role, file) in explained) {
                assertTrue("${sample.name}: ${file.file} exists", Files.isRegularFile(sample.directory.resolve(file.file)))
                val held = role.layout.any { file.file in it.matchedFiles }
                assertTrue("${sample.name}: ${file.file} is in a matchedFiles of ${role.qualifiedName}", held)
                for (declaration in file.declarations) {
                    assertTrue("${sample.name}: ${file.file} ${declaration.name} line", declaration.line >= 1)
                    assertTrue("${sample.name}: ${declaration.name} names the line that allowed it", declaration.rules.isNotEmpty())
                    assertTrue("${sample.name}: ${declaration.name}", declaration.rules.all { it.declaredAt != null })
                }
            }
        }
    }

    @Test
    fun `sample-jvm の HealthService は ServiceRole の 82 行目の class HealthService で Allowed になり 照合を通しても同じ答えが出る`() {
        val sample = samples.first { it.name == "jvm" }
        val definition = sample.definition
        val lookup = DefinitionLookup(listOf(definitionSnapshot(definition)))
        val health = definition.projectRoot!!.resolve("src/main/kotlin/com/example/service/HealthService.kt")

        val answer = lookup.fileDeclarationsOf(health, Freshness.Fresh).single()
        assertEquals("domain.Service", answer.role.qualifiedName)
        assertEquals(ConstraintBackend.KtFileContent, answer.constraint.backend)
        val declaration = answer.declarations.declarations.single()
        assertEquals(SourceDeclarationKind.Class, declaration.kind)
        assertEquals("HealthService", declaration.name)
        assertEquals(DeclarationVerdict.Allowed, declaration.verdict)
        assertEquals(listOf("internal.optional() class HealthService"), declaration.rules.map { it.text })
        assertEquals(listOf(DeclarationSiteRef("ServiceRole.kt", 82)), declaration.rules.map { it.declaredAt })
        assertEquals(true, answer.declarations.declarationsChecked)

        // The Konsist constraint of the same role covers the file too, and does not explain.
        val roles = lookup.forPath(health, Freshness.Fresh) ?: throw AssertionError("no definition above $health")
        assertEquals(listOf("domain.Service"), roles.matches.map { it.role.qualifiedName })
        assertEquals(setOf(ConstraintBackend.KtFileContent, ConstraintBackend.Konsist), roles.constraints.map { it.constraint.backend }.toSet())
        assertEquals(MatchOutcome.Matched, roles.outcome)
    }

    @Test
    fun `fileDeclarations を持つファイルを照合すると説明を書いた役割に当たり どのファイルも判定済みになる`() {
        for (sample in samples) {
            val definition = sample.definition
            val lookup = DefinitionLookup(listOf(definitionSnapshot(definition)))
            val root = definition.projectRoot!!
            for ((role, constraint) in ktFileContentConstraints(definition)) {
                for (file in constraint.fileDeclarations.orEmpty()) {
                    val matched = lookup.forPath(root.resolve(file.file), Freshness.Fresh)
                    assertTrue("${sample.name}: ${file.file} is held by ${role.qualifiedName}", matched?.matches.orEmpty().any { it.role.qualifiedName == role.qualifiedName })
                    val explained = lookup.fileDeclarationsOf(root.resolve(file.file), Freshness.Fresh)
                    assertTrue("${sample.name}: ${file.file}", explained.any { it.role.qualifiedName == role.qualifiedName && it.declarations == file })
                }
            }
        }
        // ktFileContent is deny by default: every explained file is judged, so none is left unchecked.
        for (sample in samples) {
            val unchecked = ktFileContentConstraints(sample.definition)
                .flatMap { (_, constraint) -> constraint.fileDeclarations.orEmpty() }
                .filterNot { it.declarationsChecked }
            assertEquals("${sample.name}: files left unchecked", emptyList<String>(), unchecked.map { it.file })
        }
    }

    @Test
    fun `実 katachi が書いた layout で git の追跡ファイルは baseline が抑えたものを除きすべて照合が役割か ignore に振り分ける`() {
        for (sample in samples) {
            val tracked = gitTrackedFilesOf(sample.directory)
            assumeTrue("git ls-files is not available in ${sample.directory}", tracked != null)
            val matcher = DefinitionMatcher(sample.definition)
            val unexpected = tracked!!.mapNotNull { file ->
                val match = matcher.matchFile(file)
                if (match.outcome == MatchOutcome.Matched || match.outcome is MatchOutcome.Ignored) null else file
            }
            assertEquals("${sample.name}: tracked files the plugin's matcher does not accept, against the ones the baseline holds back", sample.heldBack, unexpected)
            assertEquals("${sample.name}: every glob compiles", emptyList<Any>(), matcher.skippedGlobs)
        }
    }

    @Test
    fun `matchedFiles の全件が照合でも同じ役割に当たる`() {
        for (sample in samples) {
            val definition = sample.definition
            val matcher = DefinitionMatcher(definition)
            for (role in definition.roles) {
                for (entry in role.layout.filter { it.kind == LayoutEntryKind.File || it.kind == LayoutEntryKind.AnyFile }) {
                    for (file in entry.matchedFiles) {
                        val roles = matcher.matchFile(file).matches.map { it.role.qualifiedName }
                        assertTrue("${sample.name}: $file (${role.qualifiedName}[${entry.index}]) matched $roles", role.qualifiedName in roles)
                    }
                }
            }
        }
    }

    /** `git ls-files` of [directory] (relative to it), or `null` when git or the repository is not there. */
    private fun gitTrackedFilesOf(directory: Path): List<String>? = try {
        val process = ProcessBuilder("git", "ls-files", "-z").directory(directory.toFile()).redirectErrorStream(false).start()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        if (process.waitFor() == 0) output.split('\u0000').filter { it.isNotEmpty() } else null
    } catch (_: java.io.IOException) {
        null
    }
}
