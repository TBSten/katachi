package me.tbsten.katachi.test.report

import java.io.File
import me.tbsten.katachi.check.AmbiguousLayout
import me.tbsten.katachi.check.LayoutClaim
import me.tbsten.katachi.check.MissingDescription
import me.tbsten.katachi.check.MissingFile
import me.tbsten.katachi.check.NearbyLocation
import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.StaleBaselineEntry
import me.tbsten.katachi.check.UncheckedCheck
import me.tbsten.katachi.check.UncheckedDirectory
import me.tbsten.katachi.check.UncheckedDirectoryReason
import me.tbsten.katachi.check.UncheckedFile
import me.tbsten.katachi.check.UncheckedFileConstraint
import me.tbsten.katachi.check.UncheckedFileConstraintReason
import me.tbsten.katachi.check.UnexpectedDirectory
import me.tbsten.katachi.check.UnexpectedFile
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.ViolationDetail
import me.tbsten.katachi.check.ViolationKind
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.report.SarifContext
import me.tbsten.katachi.report.internal.SarifEnvironment
import me.tbsten.katachi.report.internal.SarifRuns
import me.tbsten.katachi.test.check.architectureOf

/** The project root every SARIF spec places its fake repository at: one level below the repository root. */
internal val SARIF_PROJECT_ROOT: FsPath = FsPath.of("/w/repo/android")

/** What `git rev-parse --show-prefix` answers for [SARIF_PROJECT_ROOT]. */
internal const val SARIF_PREFIX: String = "android/"

/** The test source root the DSL files of the fake repository sit in. */
internal val SARIF_SOURCE_ROOT: FsPath = SARIF_PROJECT_ROOT / "architecture-test/src/test/kotlin"

/** Where the fake `architecture { }` was written. */
internal val ARCHITECTURE_SITE: DeclarationSite = DeclarationSite("ProjectArchitecture.kt", 12)

/**
 * A tree that exists only in memory, with text for the files that have some, so a SARIF spec
 * never touches the disk. Directories are whatever sits above a file.
 */
internal class FakeSarifTree(vararg files: Pair<String, String>) {
    val files: MutableMap<FsPath, String> = files.associateTo(mutableMapOf()) { (path, text) -> FsPath.of(path) to text }

    /** Every write, in order, so a spec can say what was written where -- and how often. */
    val writes: MutableList<Pair<FsPath, String>> = mutableListOf()

    /** Paths a write must fail at, to stand in for a disk that refuses. */
    val unwritable: MutableSet<FsPath> = mutableSetOf()

    fun isFile(path: FsPath): Boolean = path in files

    fun isDirectory(path: FsPath): Boolean = files.keys.any { it != path && it.startsWith(path) }

    fun readText(path: FsPath): String? = files[path]

    fun filesUnder(directory: FsPath): List<FsPath> =
        files.keys.filter { it != directory && it.startsWith(directory) }.sortedBy { it.value }

    fun directoriesUnder(directory: FsPath): List<FsPath> =
        files.keys.asSequence()
            .filter { it != directory && it.startsWith(directory) }
            .mapNotNull { file -> file.segments.getOrNull(directory.segments.size)?.let { directory / it } }
            .filter { isDirectory(it) }
            .distinct()
            .sortedBy { it.value }
            .toList()

    fun writeText(path: FsPath, text: String) {
        if (path in unwritable) throw java.io.IOException("Permission denied: ${path.value}")
        writes += path to text
        files[path] = text
    }

    /** A context over this tree, with the version pinned so the golden does not move with every release. */
    fun context(
        sourceRoots: List<FsPath> = listOf(SARIF_SOURCE_ROOT),
        projectRoot: FsPath = SARIF_PROJECT_ROOT,
        repositoryPrefix: String = SARIF_PREFIX,
        architectureDeclaredAt: DeclarationSite? = ARCHITECTURE_SITE,
    ): SarifContext = SarifContext(
        projectRoot = projectRoot,
        sourceRoots = sourceRoots,
        repositoryPrefix = repositoryPrefix,
        architectureDeclaredAt = architectureDeclaredAt,
        toolVersion = "0.0.0-test",
        isFile = ::isFile,
        readText = ::readText,
        filesUnder = ::filesUnder,
    )

    /**
     * An environment over this tree whose two system properties are given here. [prefixCalls]
     * counts how often git would have been asked, which is how a spec says it never was.
     */
    fun environment(
        sarif: String? = null,
        sourceRoots: List<String>? = null,
        prefix: String? = SARIF_PREFIX,
        standardError: MutableList<String> = mutableListOf(),
        runs: SarifRuns = SarifRuns(),
        prefixCalls: MutableList<FsPath> = mutableListOf(),
    ): SarifEnvironment = SarifEnvironment(
        systemProperty = { key ->
            when (key) {
                "katachi.report.sarif" -> sarif
                "katachi.report.sourceRoots" -> sourceRoots?.joinToString(File.pathSeparator)
                else -> null
            }
        },
        standardError = { standardError += it },
        isFile = ::isFile,
        isDirectory = ::isDirectory,
        directoriesUnder = ::directoriesUnder,
        filesUnder = ::filesUnder,
        readText = ::readText,
        writeText = ::writeText,
        repositoryPrefix = { root ->
            prefixCalls += root
            prefix
        },
        runs = runs,
    )
}

/** The files of the fake repository the golden is built over, relative to [SARIF_PROJECT_ROOT]. */
internal val SARIF_REPOSITORY_FILES: List<String> = listOf(
    "app/src/main/kotlin/com/example/Helper.kt",
    "app/src/main/kotlin/com/example/Broken.kt",
    "core/domain/src/main/kotlin/com/example/domain/GetUserUseCase.kt",
    "core/domain/src/main/kotlin/com/example/domain/Shared.kt",
    "architecture-test/src/test/kotlin/com/example/roles/UseCaseRole.kt",
    "architecture-test/src/test/kotlin/com/example/roles/RepositoryRole.kt",
    "architecture-test/src/test/kotlin/com/example/ProjectArchitecture.kt",
)

/** The baseline file of the fake repository, with one stale entry on `legacy/Old.kt`. */
internal const val SARIF_BASELINE_TEXT: String = """{
  "version": 1,
  "checks": {
    "me.tbsten.katachi.check.LayoutCheck": [
      {"rule": "UnexpectedFile", "path": "legacy/Old.kt", "count": 2},
      {"rule": "UnexpectedFile", "path": "notes.md"}
    ]
  }
}
"""

/** The fake repository every golden is built over. */
internal fun sarifRepository(): FakeSarifTree = FakeSarifTree(
    *SARIF_REPOSITORY_FILES.map { (SARIF_PROJECT_ROOT / it).value to "" }.toTypedArray(),
    (SARIF_PROJECT_ROOT / "katachi-baseline.json").value to SARIF_BASELINE_TEXT,
)

/** Two roles the fake violations are about. */
internal class SarifRoles {
    private val architecture = architectureOf {
        "domain".group {
            "UseCase" { layout { "core/domain/src/main/kotlin/com/example/domain" { "*UseCase".ktFile() } } }
            "Repository" { layout { "core/data" { anyFile() } } }
        }
    }
    val useCase: Role = architecture.allRoles.single { it.name == "UseCase" }
    val repository: Role = architecture.allRoles.single { it.name == "Repository" }
}

/** A violation from a check outside katachi, with and without details. */
internal class ForeignSarifViolation(
    override val path: String,
    override val label: String,
    override val details: List<ViolationDetail> = emptyList(),
    override val kind: ViolationKind = ViolationKind.FileConstraint,
    override val severity: Severity = Severity.Error,
) : Violation

/**
 * One violation of every type katachi writes, plus two from outside: the list the golden holds.
 * In the order `assertWith` would hand them over -- by kind, `Failed` last.
 */
internal fun everyKindOfViolation(roles: SarifRoles = SarifRoles()): List<Violation> {
    val useCaseSite = DeclarationSite("UseCaseRole.kt", 16)
    val repositorySite = DeclarationSite("RepositoryRole.kt", 9)
    return listOf(
        UnexpectedFile(
            path = "app/src/main/kotlin/com/example/Helper.kt",
            nearby = listOf(NearbyLocation(roles.useCase, "core/domain/src/main/kotlin/com/example/domain")),
        ),
        UnexpectedDirectory(path = "legacy"),
        MissingFile(
            path = "core/domain/src/main/kotlin/com/example/domain/GetOrderUseCase.kt",
            role = roles.useCase,
            declaredAt = useCaseSite,
        ),
        UnsatisfiedFileConstraint(
            path = "core/domain/src/main/kotlin/com/example/domain/GetUserUseCase.kt",
            declaration = "GetUserUseCase",
            line = 7,
            role = roles.useCase,
            constraintName = "外から使えること",
            layoutPath = "core/domain",
            declaredAt = useCaseSite,
        ),
        ForeignSarifViolation(
            path = "app/src/main/kotlin/com/example/Broken.kt",
            label = "TodoRule",
            details = listOf(ViolationDetail("Rule", "TODO"), ViolationDetail("Line", "12")),
        ),
        ForeignSarifViolation(path = "app/src/main/kotlin/com/example/Broken.kt", label = "BareRule", severity = Severity.Warning),
        StaleBaselineEntry(
            path = "legacy/Old.kt",
            check = "me.tbsten.katachi.check.LayoutCheck",
            rule = "UnexpectedFile",
            allowed = 2,
            found = 0,
            baselinePath = "katachi-baseline.json",
        ),
        AmbiguousLayout(
            path = "core/domain/src/main/kotlin/com/example/domain/Shared.kt",
            claims = listOf(LayoutClaim(roles.useCase, useCaseSite), LayoutClaim(roles.repository, repositorySite)),
            overlappingFiles = listOf("core/domain/src/main/kotlin/com/example/domain/Shared.kt"),
        ),
        MissingDescription(
            path = "core/data",
            role = roles.repository,
            otherPlaces = listOf("feature/cart/data"),
            declaredAt = repositorySite,
        ),
        UncheckedFile(path = "app/src/main/kotlin/com/example/Broken.kt", cause = IllegalStateException("unreadable")),
        UncheckedDirectory(
            path = "build",
            reason = UncheckedDirectoryReason.NotWalked,
            cause = java.io.IOException("Permission denied"),
        ),
        UncheckedCheck(check = "com.example.TodoCheck", cause = IllegalStateException("broken check")),
        UncheckedFileConstraint(
            path = "core/data",
            reason = UncheckedFileConstraintReason.NotEvaluated,
            role = roles.repository,
            constraintName = null,
            layoutPath = null,
            declaredAt = repositorySite,
            cause = null,
        ),
    )
}

/** The golden directory, found from wherever the test happens to be run. */
internal fun sarifGoldenDirectory(): File {
    val fromModule = File("src/test/resources/sarif/golden")
    return if (fromModule.isDirectory) fromModule else File("katachi/src/test/resources/sarif/golden")
}

/** Whether this run was asked to rewrite the goldens: `-Dkatachi.golden.update=true`. */
internal fun updatingGoldens(): Boolean = System.getProperty("katachi.golden.update").equals("true", ignoreCase = true)

/** The text of the golden [name], or `null` when it has not been written yet. */
internal fun goldenText(name: String): String? =
    sarifGoldenDirectory().resolve(name).takeIf { it.isFile }?.readText(Charsets.UTF_8)

/** Compares [actual] with the golden [name], or rewrites the golden when asked to. */
internal fun checkGolden(name: String, actual: String) {
    val file = sarifGoldenDirectory().resolve(name)
    if (updatingGoldens()) {
        file.parentFile.mkdirs()
        file.writeText(actual, Charsets.UTF_8)
        return
    }
    val expected = goldenText(name)
        ?: throw AssertionError("No golden at ${file.path}. Run `./gradlew :katachi:test -Dkatachi.golden.update=true` once to write it.")
    if (expected != actual) {
        throw AssertionError(
            "SARIF differs from golden ${file.path}. Re-run with -Dkatachi.golden.update=true if the change is intended.\n" +
                "--- expected\n$expected\n--- actual\n$actual",
        )
    }
}
