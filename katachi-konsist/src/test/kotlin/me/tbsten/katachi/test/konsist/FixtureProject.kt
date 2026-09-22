package me.tbsten.katachi.test.konsist

import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * A throwaway project on the real file system, for a spec that has to hand Konsist something
 * to parse.
 *
 * NOTE: このパッケージを `me.tbsten.katachi.konsist.test` にしてはいけない。
 * captureDeclarationSite() は `me.tbsten.katachi.` を読み飛ばし、`me.tbsten.katachi.test.`
 * だけを例外にしている（DeclarationSite.kt）。前者に置くとスペック自身のフレームが飛ばされ、
 * 宣言位置が kotest 内部を指す。
 *
 * Konsist parses from disk and katachi finds a project root by walking up from a working
 * directory, so neither can be given a fake. The fixtures therefore live under `.local/tmp/`,
 * which is git ignored, and each one is deleted in a `finally` whether the spec passed or not.
 */

/** Where every fixture is written, resolved once per JVM. */
private val FIXTURE_ROOT: File by lazy {
    File(repositoryRoot(), ".local/tmp/katachi-konsist-fixtures")
}

/** Numbers the fixtures of one run so that two specs never share a directory. */
private val fixtureNumber = AtomicInteger(0)

/**
 * The top of this repository, found by walking up from the JVM's working directory.
 *
 * Under Gradle that working directory is `katachi-konsist/`, but a spec run from an IDE may
 * start anywhere, so the two files that only the repository root holds are the marker.
 */
private fun repositoryRoot(): File {
    var directory: File? = File("").absoluteFile
    while (directory != null) {
        val isRoot = File(directory, "settings.gradle.kts").isFile &&
            File(directory, "gradlew").isFile &&
            File(directory, "katachi-konsist").isDirectory
        if (isRoot) return directory
        directory = directory.parentFile
    }
    return File("").absoluteFile
}

/**
 * Writes [files] into a fresh directory, runs [use] against it, and deletes it again.
 *
 * The directory gets a `gradlew` of its own so that `findProjectRoot` stops there instead of
 * climbing out into this repository: a fixture has to be its own project, or the paths in a
 * report would be relative to katachi rather than to the fixture.
 *
 * @param files project relative path to contents. Parent directories are created.
 * @param use what to do with the fixture root, which is absolute.
 */
internal fun <T> fixtureProject(vararg files: Pair<String, String>, use: (File) -> T): T {
    val root = File(FIXTURE_ROOT, fixtureNumber.incrementAndGet().toString())
    root.deleteRecursively()
    root.mkdirs()
    // The project root marker. Contents do not matter: katachi looks for the name.
    File(root, "gradlew").writeText("#!/bin/sh\n")
    for ((path, contents) in files) {
        val file = File(root, path)
        file.parentFile?.mkdirs()
        file.writeText(contents)
    }
    return try {
        use(root)
    } finally {
        root.deleteRecursively()
    }
}

/** The absolute, `/` separated path of [relative] inside this fixture root. */
internal fun File.fixturePath(relative: String): String =
    File(this, relative).absolutePath.replace(File.separatorChar, '/')
