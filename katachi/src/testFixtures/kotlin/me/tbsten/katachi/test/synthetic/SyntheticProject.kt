package me.tbsten.katachi.test.synthetic

import kotlin.math.roundToInt
import kotlin.random.Random
import me.tbsten.katachi.dsl.files.FsPath

/**
 * A generated project tree, and everything a spec or a benchmark needs to know about it.
 *
 * Built by [generateProject]. Every path is relative to [root] and `/` separated. The tree is
 * names only: [writeTo] writes it to disk when a check needs real contents (Konsist).
 *
 * ## Example 1: generate a project and check it in memory
 * ```kt
 * val project = generateProject(files = 1_000, roles = 5, modules = 10, seed = 42)
 * project.architecture().validate(project.inMemoryFileSystem())
 *     .map { "[${it.label}] ${it.path}" }
 *     .sorted() shouldBe project.expectedViolationLabels
 * ```
 */
class SyntheticProject internal constructor(
    /** The absolute directory the tree sits in. Also the working directory of its file systems. */
    val root: FsPath,
    /** Every file of the tree, sorted, including [ignoredFiles] and the files of [unknownDirectories]. */
    val files: List<String>,
    /** The roles, in declaration order. */
    val roles: List<SyntheticRole>,
    /** The Gradle modules below `modules/`, e.g. `:modules:m3`. The root project `:` is not in it. */
    val modules: List<String>,
    /** Directories no role declares. The walk reports each one once and does not descend. */
    val unknownDirectories: List<String>,
    /** Files below a module's `build/`, which [architecture] ignores. */
    val ignoredFiles: List<String>,
    /**
     * Files directly inside a module's `src/main/kotlin`: a directory every module role
     * passes through, where no role declares a file. The walk reports each one as an
     * `UnexpectedFile`, with the nearby locations computed over every module's role directories.
     */
    val unexpectedFiles: List<String> = emptyList(),
) {
    /**
     * What `validate()` answers for [architecture], as `[label] path`, sorted: one
     * `UnexpectedDirectory` per [unknownDirectories], one `UnexpectedFile` per
     * [unexpectedFiles], and nothing else.
     */
    val expectedViolationLabels: List<String> =
        (unknownDirectories.map { "[UnexpectedDirectory] $it" } + unexpectedFiles.map { "[UnexpectedFile] $it" }).sorted()

    override fun toString(): String =
        "SyntheticProject(files=${files.size}, roles=${roles.size}, modules=${modules.size})"
}

/**
 * One role of a [SyntheticProject].
 *
 * @property directory the directory the role's files sit in: below each module's
 *   `src/main/kotlin` for [SyntheticPlacement.Module], below `shared/` for
 *   [SyntheticPlacement.Fixed].
 * @property files the role's files, relative to the project root.
 */
data class SyntheticRole(
    val name: String,
    val placement: SyntheticPlacement,
    val directory: String,
    val files: List<String>,
)

/** How a [SyntheticRole] is declared. */
enum class SyntheticPlacement {
    /** `"modules" / "*" { mainSourceSet / kotlin / ... }`: one layout entry per module. */
    Module,

    /** A fixed path below the project root: one layout entry however many modules there are. */
    Fixed,
}

/**
 * Generates a project of exactly [files] files, [roles] roles and [modules] modules.
 *
 * The same arguments always give the same tree: everything random is drawn from [seed].
 *
 * - Roles alternate between [SyntheticPlacement.Module] (even indices) and
 *   [SyntheticPlacement.Fixed] (odd indices), so the flattened layout grows with
 *   `roles × modules`.
 * - [unknownDirectoryRatio] of the files go into directories no role declares, four to a
 *   directory, half of them at the project root and half inside a module.
 * - [ignoredFileRatio] of the files go below a module's `build/`.
 * - [unexpectedFiles] of the files go directly inside a module's `src/main/kotlin`, a
 *   directory the module roles pass through without declaring a file in.
 * - The fixed part — `settings.gradle.kts`, the root `build.gradle.kts` and one
 *   `build.gradle.kts` per module — counts towards [files] too.
 *
 * @throws IllegalArgumentException when [files] is too small to hold the fixed part, or a
 *   count or ratio is out of range.
 */
fun generateProject(
    files: Int,
    roles: Int,
    modules: Int,
    seed: Long = 0L,
    unknownDirectoryRatio: Double = 0.02,
    ignoredFileRatio: Double = 0.05,
    unexpectedFiles: Int = 0,
    root: String = "/repo",
): SyntheticProject {
    require(roles >= 1) { "roles must be at least 1, was $roles" }
    require(modules >= 1) { "modules must be at least 1, was $modules" }
    require(unknownDirectoryRatio in 0.0..1.0) { "unknownDirectoryRatio must be in 0..1, was $unknownDirectoryRatio" }
    require(ignoredFileRatio in 0.0..1.0) { "ignoredFileRatio must be in 0..1, was $ignoredFileRatio" }
    require(unexpectedFiles >= 0) { "unexpectedFiles must be 0 or more, was $unexpectedFiles" }
    val random = Random(seed)
    val moduleNames = List(modules) { "m$it" }
    val skeleton = listOf("build.gradle.kts", "settings.gradle.kts") +
        moduleNames.map { "modules/$it/build.gradle.kts" }
    val ignoredCount = (files * ignoredFileRatio).roundToInt()
    val unknownCount = (files * unknownDirectoryRatio).roundToInt()
    val roleFileCount = files - skeleton.size - ignoredCount - unknownCount - unexpectedFiles
    require(roleFileCount >= 0) {
        "files=$files cannot hold the ${skeleton.size} build files of $modules modules " +
            "plus $ignoredCount ignored, $unknownCount unknown and $unexpectedFiles unexpected files"
    }

    val ignored = List(ignoredCount) { index ->
        "modules/${moduleNames[random.nextInt(modules)]}/build/tmp/Generated$index.class"
    }
    val unexpected = List(unexpectedFiles) { index ->
        "modules/${moduleNames[random.nextInt(modules)]}/src/main/kotlin/Unexpected$index.kt"
    }
    val unknownDirectories = List((unknownCount + FILES_PER_UNKNOWN_DIRECTORY - 1) / FILES_PER_UNKNOWN_DIRECTORY) { index ->
        if (random.nextBoolean()) "stray$index" else "modules/${moduleNames[random.nextInt(modules)]}/stray$index"
    }
    val unknown = List(unknownCount) { index ->
        "${unknownDirectories[index / FILES_PER_UNKNOWN_DIRECTORY]}/Stray$index.kt"
    }

    val roleFiles = List(roles) { mutableListOf<String>() }
    repeat(roleFileCount) { index ->
        // Round robin rather than random, so every role gets its share at any size.
        val role = index % roles
        val name = "File${index}Role$role.kt"
        roleFiles[role] += if (placementOf(role) == SyntheticPlacement.Module) {
            "modules/${moduleNames[random.nextInt(modules)]}/src/main/kotlin/role$role/$name"
        } else {
            "shared/role$role/$name"
        }
    }
    val syntheticRoles = List(roles) { index ->
        SyntheticRole(
            name = "Role$index",
            placement = placementOf(index),
            directory = "role$index",
            files = roleFiles[index].sorted(),
        )
    }

    return SyntheticProject(
        root = FsPath.of(root),
        files = (skeleton + ignored + unknown + unexpected + roleFiles.flatten()).sorted(),
        roles = syntheticRoles,
        modules = moduleNames.map { ":modules:$it" },
        unknownDirectories = unknownDirectories.distinct().sorted(),
        ignoredFiles = ignored.sorted(),
        unexpectedFiles = unexpected.sorted(),
    )
}

private const val FILES_PER_UNKNOWN_DIRECTORY = 4

private fun placementOf(roleIndex: Int): SyntheticPlacement =
    if (roleIndex % 2 == 0) SyntheticPlacement.Module else SyntheticPlacement.Fixed
