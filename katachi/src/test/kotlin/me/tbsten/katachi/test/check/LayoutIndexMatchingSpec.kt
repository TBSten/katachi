package me.tbsten.katachi.test.check

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.check.internal.LayoutIndex
import me.tbsten.katachi.dsl.ArchitectureScope
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.internal.Glob
import me.tbsten.katachi.dsl.internal.flattenLayout
import me.tbsten.katachi.dsl.kotlin.ktFile
import kotlin.random.Random

/**
 * Pins what `LayoutIndex` answers — which roles a file belongs to, which declarations claim
 * it and in what order, and which directories are known or ignored — against the plain
 * reading of the entries: every pattern tried against the path, in declaration order.
 *
 * The index is free to skip patterns that cannot match, but never to change an answer, so
 * every question is asked of both on the same paths and the two have to agree exactly.
 */
private fun entriesOf(block: ArchitectureScope.() -> Unit): List<LayoutEntry> = architecture(block).flattenLayout()

/** The reading `LayoutIndex` has to agree with: every entry tried, in declaration order. */
private class LinearReference(private val entries: List<LayoutEntry>) {
    private val files = entries.filter { it.kind == LayoutEntryKind.File }
    private val open = entries.filter { it.kind == LayoutEntryKind.AnyFile }
    private val ignored = entries.filter { it.kind == LayoutEntryKind.Ignore }.map { it.glob }
    private val known = entries.flatMapTo(linkedSetOf<String>()) { entry ->
        val segments = entry.path.split('/')
        val levels = if (entry.kind == LayoutEntryKind.File) segments.size - 1 else segments.size
        (1..levels).map { segments.take(it).joinToString("/") }
    }.map { Glob.compile(it) }

    fun rolesOf(file: String): List<Role> = matchesOn(file, files, open).keys.toList()

    fun claimsOn(file: String): List<LayoutEntry> =
        matchesOn(file, files.filterNot { it.synthetic }, open.filterNot { it.synthetic }).values.toList()

    fun isKnown(directory: String): Boolean = known.any { it.matches(directory) }

    fun isIgnored(directory: String): Boolean = ignored.any { it.matches(directory) }

    private fun matchesOn(file: String, fileEntries: List<LayoutEntry>, openEntries: List<LayoutEntry>): Map<Role, LayoutEntry> {
        val matched = LinkedHashMap<Role, LayoutEntry>()
        for (entry in fileEntries) if (entry.glob.matches(file)) matched.putIfAbsent(entry.role, entry)
        val directory = file.substringBeforeLast('/', missingDelimiterValue = "")
        if (directory.isNotEmpty()) {
            for (entry in openEntries) if (entry.glob.matches(directory)) matched.putIfAbsent(entry.role, entry)
        }
        return matched
    }
}

/** A definition mixing literal paths, `*`, `**`, escapes, module keys and overlaps. */
private val mixedDefinition: ArchitectureScope.() -> Unit = {
    "ui".group {
        "Screen" {
            layout {
                "home" { "*Screen".ktFile(); "HomeScreen".ktFile() }
                "feature" / "*" / "ui" / "*".ktFile()
            }
        }
        "ViewModel" {
            layout {
                "home" { "*ViewModel".ktFile(); "*".ktFile() }
                "feature" / "**" / "*ViewModel".ktFile()
            }
        }
        "Open" {
            layout {
                "home" { anyFile() }
                "generated" / "**" { anyFile() }
                "**" / "assets" { anyFile() }
            }
        }
    }
    "build".group {
        "Gradle" {
            layout {
                "build.gradle.kts".file()
                "settings.gradle.kts".file()
                "**" / "build.gradle.kts".file()
                "gradle" / "libs.versions.toml".file()
                "build".ignore()
                "**" / "build".ignore()
                "tmp" / "*".ignore()
            }
        }
        "Literal" {
            layout {
                "weird" / "a\\*b.kt".file()
                "weird" / "x\\*y" { "*.kt".file() }
                "docs" / "**".file()
            }
        }
    }
    "modules".group {
        "Feature" { layout { ":feature:*".module { "src" / "main" / "kotlin" / "**" / "*.kt".file() } } }
        "App" { layout { ":app".module { "src" { anyFile() } } } }
    }
}

private val segmentPool = listOf(
    "home", "feature", "profile", "ui", "src", "main", "kotlin", "generated", "assets", "build",
    "gradle", "tmp", "weird", "docs", "app", "a*b.kt", "x*y", "HomeScreen.kt", "HomeViewModel.kt",
    "Other.kt", "build.gradle.kts", "settings.gradle.kts", "libs.versions.toml", "README.md",
)

/** Paths walked from [segmentPool], seeded so a failure names a path that reproduces. */
private fun randomPaths(count: Int, seed: Int): List<String> {
    val random = Random(seed)
    return List(count) {
        val depth = 1 + random.nextInt(7)
        List(depth) { segmentPool[random.nextInt(segmentPool.size)] }.joinToString("/")
    }
}

private val handPickedPaths = listOf(
    "build.gradle.kts", "settings.gradle.kts", "README.md",
    "home/HomeScreen.kt", "home/HomeViewModel.kt", "home/Other.kt", "home/README.md", "home",
    "feature/profile/ui/ProfileScreen.kt", "feature/profile/ProfileViewModel.kt", "feature/ProfileViewModel.kt",
    "feature/profile/deep/er/ProfileViewModel.kt", "feature/profile/build.gradle.kts",
    "feature/profile/src/main/kotlin/Foo.kt", "feature/profile/src/main/kotlin/a/b/Foo.kt",
    "feature/profile/src", "feature/profile/src/main",
    "generated/Out.kt", "generated/x/y/Out.kt", "generated",
    "assets/logo.png", "app/src/assets/logo.png", "a/b/c/assets/logo.png",
    "app/build.gradle.kts", "app/src/Foo.kt", "app/src/main/Foo.kt", "app/build", "app/build/x.txt",
    "build", "build/out.txt", "x/y/build", "tmp/cache", "tmp", "tmp/cache/deeper",
    "weird/a*b.kt", "weird/aXb.kt", "weird/a\\*b.kt", "weird/x*y/Foo.kt", "weird/xZy/Foo.kt", "weird/x*y",
    "docs", "docs/index.md", "docs/a/b/c.md", "gradle/libs.versions.toml",
)

private fun directoriesOf(paths: List<String>): Set<String> = paths.flatMapTo(linkedSetOf()) { path ->
    val segments = path.split('/')
    (1..segments.size).map { segments.take(it).joinToString("/") }
}

private fun LayoutIndex.shouldAgreeWith(reference: LinearReference, paths: List<String>) {
    for (path in paths) {
        withClue(path) {
            rolesOf(path) shouldBe reference.rolesOf(path)
            claimsOn(path).map { it.identity() } shouldBe reference.claimsOn(path).map { it.identity() }
        }
    }
    for (directory in directoriesOf(paths)) {
        withClue(directory) {
            isKnown(directory) shouldBe reference.isKnown(directory)
            isIgnored(directory) shouldBe reference.isIgnored(directory)
        }
    }
}

private fun LayoutEntry.identity(): Int = System.identityHashCode(this)

class LayoutIndexMatchingSpec : FreeSpec({
    "手で選んだパスで、線形に全パターンを当てた答えと一致する" {
        val entries = entriesOf(mixedDefinition)
        LayoutIndex(entries).shouldAgreeWith(LinearReference(entries), handPickedPaths)
    }

    "ランダムなパスでも、線形に全パターンを当てた答えと一致する" {
        val entries = entriesOf(mixedDefinition)
        LayoutIndex(entries).shouldAgreeWith(LinearReference(entries), randomPaths(count = 5_000, seed = 20260927))
    }

    "答えそのもの" - {
        val entries = entriesOf(mixedDefinition)
        val index = LayoutIndex(entries)

        "重なる役割は宣言順に全部返る" {
            index.rolesOf("home/HomeScreen.kt").map { it.name } shouldBe listOf("Screen", "ViewModel", "Open")
            index.rolesOf("home/HomeViewModel.kt").map { it.name } shouldBe listOf("ViewModel", "Open")
        }

        "claimsOn は名指しした役割が先、anyFile だけの役割が後" {
            index.claimsOn("home/HomeScreen.kt").map { it.role.name to it.path } shouldBe listOf(
                "Screen" to "home/*Screen.kt",
                "ViewModel" to "home/*.kt",
                "Open" to "home",
            )
        }

        "末尾の ** は0段にも当たる" {
            index.rolesOf("docs").map { it.name } shouldBe listOf("Literal")
            index.rolesOf("generated/Out.kt").map { it.name } shouldBe listOf("Open")
        }

        "先頭の ** はどの深さにも当たる" {
            index.rolesOf("a/b/c/assets/logo.png").map { it.name } shouldBe listOf("Open")
            index.isIgnored("x/y/build") shouldBe true
        }

        "エスケープした * は文字どおりにだけ当たる" {
            index.rolesOf("weird/a*b.kt").map { it.name } shouldBe listOf("Literal")
            index.rolesOf("weird/aXb.kt").shouldBeEmpty()
            index.rolesOf("weird/x*y/Foo.kt").map { it.name } shouldBe listOf("Literal")
            index.rolesOf("weird/xZy/Foo.kt").shouldBeEmpty()
        }

        "ワイルドカードの module キーを展開したパスに当たる" {
            index.rolesOf("feature/profile/src/main/kotlin/a/Foo.kt").map { it.name } shouldBe listOf("Feature")
            index.isKnown("feature/profile/src/main") shouldBe true
        }

        "どの役割も宣言していないファイルは空" {
            index.rolesOf("README.md").shouldBeEmpty()
            index.rolesOf("feature/profile/README.md").shouldBeEmpty()
        }
    }

    "先頭に ** の無い定義では、宣言の外のディレクトリは known にならない" {
        val index = LayoutIndex(
            entriesOf {
                "modules".group {
                    "Feature" { layout { ":feature:*".module { "src" / "main" / "*.kt".file() } } }
                }
            },
        )
        index.isKnown("feature") shouldBe true
        index.isKnown("feature/profile/src/main") shouldBe true
        index.isKnown("feature/profile/other") shouldBe false
        index.isKnown("other") shouldBe false
    }

    "役割が1つも無い定義では何にも当たらない" {
        val index = LayoutIndex(emptyList())
        index.rolesOf("a/b.kt").shouldBeEmpty()
        index.isKnown("a") shouldBe false
        index.isIgnored("a") shouldBe false
    }
})
