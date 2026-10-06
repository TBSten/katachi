package me.tbsten.katachi.test.dsl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.ints.shouldBeInRange
import java.io.File
import kotlin.random.Random
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import me.tbsten.katachi.dsl.KatachiGlobSyntaxException
import me.tbsten.katachi.dsl.internal.Glob
import me.tbsten.katachi.template.internal.encodeToJson

/*
 * The glob cases the IDE plugin's port of `Glob` is checked against
 * (`katachi-intellij-plugin/src/test/resources/contract/glob/glob-cases.json`, read by its
 * `KatachiGlobConformanceTest`). Each row is a pattern and a path with katachi's own answer, or a
 * pattern katachi refuses to compile. A change to `Glob`'s grammar changes the rows, which fails
 * this spec until the fixture is regenerated -- and then fails the plugin until its port agrees.
 */

private const val UPDATE_ENV: String = "KATACHI_UPDATE_IDE_PLUGIN_FIXTURES"

/** Relative to `katachi/`, the working directory of `:katachi:test`. */
private const val FIXTURE: String = "../katachi-intellij-plugin/src/test/resources/contract/glob/glob-cases.json"

private const val UPDATE_COMMAND: String =
    "$UPDATE_ENV=true ./gradlew :katachi:test --tests '*GlobConformanceFixtureSpec*' --rerun"

/**
 * One row: `error` with no path for a pattern that does not compile, a path and its answer otherwise.
 * A row that compiles also carries the pattern's [literalPrefix] and [hasWildcard], which the port's
 * prefix index buckets candidates by: a wrong prefix drops candidates without any `matches` row failing.
 * A row that matches also carries what each capturing wildcard took, so the capture values are pinned
 * and not only the yes or no.
 */
@Serializable
private class GlobCaseJson(
    val pattern: String,
    val path: String?,
    val matches: Boolean?,
    val error: Boolean,
    val literalPrefix: List<String>?,
    val hasWildcard: Boolean?,
    /** What each capturing wildcard matched (`matchGroups`), when [matches] is true. */
    val groups: List<String>?,
)

/** Patterns and paths written by hand, one group per rule of the grammar. */
private val HAND_WRITTEN: List<Pair<String, List<String>>> = listOf(
    "build.gradle.kts" to listOf("build.gradle.kts", "build_gradle_kts", "app/build.gradle.kts", "build.gradle.kt"),
    "*.kt" to listOf("Foo.kt", "a/Foo.kt", ".kt", "Foo.kts", "x.kt"),
    "*UseCase.kt" to listOf("GetUserUseCase.kt", "UseCase.kt", "useCase/GetUserUseCase.kt", "GetUserUseCase.kts"),
    "Pre*Post" to listOf("PreXPost", "PrePost", "PreX/Post", "PreXYZPost"),
    "*.*" to listOf("a.b", "ab", ".b", "a.", "a.b.c"),
    "src/*/Foo.kt" to listOf("src/main/Foo.kt", "src/Foo.kt", "src/a/b/Foo.kt", "src/main/Foo.kts"),
    "src/*/*" to listOf("src/a/b", "src/a", "src/a/b/c"),
    "**" to listOf("a", "a/b/c.kt", "build.gradle.kts"),
    "src/**" to listOf("src", "src/a", "src/a/b/c", "srcx", "other/a"),
    "src/**/Foo.kt" to listOf("src/Foo.kt", "src/a/Foo.kt", "src/a/b/c/Foo.kt", "Foo.kt", "src/a/Bar.kt"),
    "**/build.gradle.kts" to listOf("build.gradle.kts", "core/data/build.gradle.kts", "core/data/build.gradle.kt"),
    "**/useCase/*UseCase.kt" to listOf("useCase/GetUseCase.kt", "a/b/useCase/GetUseCase.kt", "a/useCase/x/GetUseCase.kt"),
    "a/**/b/**/c" to listOf("a/b/c", "a/x/b/y/c", "a/b/x/y/c", "a/c", "a/x/y/c"),
    "core/*/src/main/kotlin/**/useCase" to listOf(
        "core/domain/src/main/kotlin/useCase",
        "core/domain/src/main/kotlin/com/example/useCase",
        "core/src/main/kotlin/useCase",
    ),
    "\\*.kt" to listOf("*.kt", "Foo.kt"),
    "a\\,b.kt" to listOf("a,b.kt", "ab.kt"),
    "a,b.kt" to listOf("a,b.kt", "a.kt"),
    "\\{x\\}" to listOf("{x}", "x"),
    "\\[x\\]" to listOf("[x]", "x"),
    "\\?" to listOf("?", "a"),
    "a\\\\b" to listOf("a\\b", "ab"),
    "a.b" to listOf("a.b", "axb"),
    "a+b(c)" to listOf("a+b(c)", "aab(c)"),
    "\$x^" to listOf("\$x^", "x"),
    "ドメイン/*.kt" to listOf("ドメイン/ユースケース.kt", "domain/ユースケース.kt"),
    ":feature:*" to listOf(":feature:home", ":feature"),
    "/src/*" to listOf("/src/a", "src/a"),
    "Src/*.kt" to listOf("src/A.kt", "Src/A.kt"),
    // Cases the IDE plugin's first, hand-written conformance file held (deleted once the generated one had them).
    "src/**/Foo.kt" to listOf("src/a/b/Foo.kt"),
    "**/Foo.kt" to listOf("Foo.kt", "a/b/Foo.kt", "a/b/Bar.kt"),
    "a/**/b/**/c" to listOf("a/x/b/y/z/c", "a/x/c"),
    "**/assets" to listOf("a/b/assets", "assets"),
    "a\\*b.kt" to listOf("a*b.kt", "aXb.kt"),
    "x\\\\y" to listOf("x\\y"),
    "a\\,b" to listOf("a,b"),
    "a\\{b\\}" to listOf("a{b}"),
    "a\\?b" to listOf("a?b"),
    "a\\[b\\]" to listOf("a[b]"),
    "a.b+c(d)^\$|e" to listOf("a.b+c(d)^\$|e"),
    "a.b" to listOf("aXb"),
    "日本語/*.kt" to listOf("日本語/あ.kt"),
    "Foo*Bar*.kt" to listOf("FooXBarY.kt", "FooBar.kt"),
    "core/*/src/**/useCase/*UseCase.kt" to listOf(
        "core/domain/src/main/kotlin/com/example/useCase/GetUserUseCase.kt",
        "core/domain/src/useCase/GetUserUseCase.kt",
        "core/src/useCase/GetUserUseCase.kt",
    ),
    // TODO: `a/**/**` does not match `a` although `a/**` does (the second `**` inherits the first one's
    //  separator). Nobody needs two `**` in a row; the row pins what Glob answers so the plugin's port follows it.
    "a/**/**" to listOf("a", "a/b", "a/b/c"),
    "**/**" to listOf("a"),
    // `*` may match nothing inside a level; a whole level never does.
    "app_icon*.xml" to listOf("app_icon.xml", "app_icon_dark.xml", "app_icon/x.xml", "app_ico.xml"),
    "DropShadow*" to listOf("DropShadow", "DropShadow.kt", "dropShadow"),
    "feature/*" to listOf("feature/home", "feature", "feature/home/impl"),
    // `?` is one character other than the separator and is never a value.
    "Foo?.kt" to listOf("FooA.kt", "Foo.kt", "FooAB.kt", "Foo/.kt"),
    "*?.kt" to listOf("AB.kt", "A.kt", ".kt"),
    // `[...]`: lists, ranges, negation, a `-` at either end, and members that are only literal.
    "[abc].kt" to listOf("a.kt", "d.kt", "ab.kt"),
    "[a-c]x" to listOf("bx", "dx", "Bx"),
    "[!a]x" to listOf("bx", "ax", "/x"),
    "[^a]x" to listOf("bx", "ax"),
    "a[!x]b" to listOf("a/b", "aYb"),
    "[-a].kt" to listOf("-.kt", "a.kt", "b.kt"),
    "[a-].kt" to listOf("-.kt", "a.kt", "b.kt"),
    "[*?{].kt" to listOf("*.kt", "?.kt", "{.kt", "a.kt"),
    "[\\]a].kt" to listOf("].kt", "a.kt"),
    "[a!^]x" to listOf("!x", "^x", "bx"),
    // `{...}`: alternatives, an empty one, wildcards inside (not values), and a `,` outside.
    "*.{kt,kts}" to listOf("Foo.kt", "build.kts", "Foo.java", "Foo.kt/x"),
    "build.gradle{,.kts}" to listOf("build.gradle", "build.gradle.kts", "build.gradle.x"),
    "*{Screen,*Route}" to listOf("HomeScreen", "HomeFooRoute", "Home"),
    "{a?,[xy]z}.kt" to listOf("ab.kt", "xz.kt", "az.kt", "bz.kt"),
    "src/{main,test}/kotlin/*.kt" to listOf("src/main/kotlin/A.kt", "src/test/kotlin/A.kt", "src/debug/kotlin/A.kt"),
    "{*}.kt" to listOf("a.kt", "a/b.kt"),
    // The same syntax on a module path.
    ":feature:{home,settings}" to listOf(":feature:home", ":feature:other"),
    ":core:[a-d]*" to listOf(":core:data", ":core:ui"),
)

/** Patterns katachi refuses to compile. */
private val REFUSED: List<String> = listOf(
    "", "a//b", "/", "a/", "//", "*/", "a]", "a}",
    "a**b", "**a", "a**", "src/***", "a\\", "\\a.kt", "a\\-b", "a\\/b",
    "a[bc", "a{b,c", "a[", "a{", "[]", "[!]", "[^]", "[z-a]", "[a/b]", "{}", "{a,{b}}", "{**}", "{a/b,c}",
    "{a,b", "a/{x", "{a[b}", "[a{]b}",
)

/** Literal levels the generated patterns and paths are made of. */
private val LITERALS: List<String> = listOf("src", "main", "kotlin", "core", "data", "Foo.kt", "x.kt", "useCase", "a", "b")

/** Levels of a generated pattern that hold an escape or a non-ASCII letter, with the path text each matches. */
private val SPECIAL_LEVELS: Map<String, String> = mapOf(
    "a\\*b" to "a*b",
    "x\\,y" to "x,y",
    "ドメイン" to "ドメイン",
    "Foo?.kt" to "FooA.kt",
    "[a-c]x" to "bx",
    "[!a]x" to "bx",
    "{a,b*}" to "bZ",
    "x.{kt,kts}" to "x.kts",
)

/**
 * One random pattern: up to four levels, each a literal, `*`, a `*` with a suffix or prefix, `**`, or
 * one of [SPECIAL_LEVELS] (which now include `?`, `[...]` and `{...}`).
 */
private fun Random.pattern(): String {
    val levels = (1..nextInt(1, 5)).map {
        when (nextInt(8)) {
            0 -> "*"
            1 -> "*" + LITERALS.random(this).takeLast(3)
            2 -> LITERALS.random(this).take(2) + "*"
            3 -> "**"
            4 -> SPECIAL_LEVELS.keys.random(this)
            else -> LITERALS.random(this)
        }
    }
    // `**/**` is legal but says nothing a single `**` does not.
    return levels.fold(mutableListOf<String>()) { kept, level ->
        if (!(level == "**" && kept.lastOrNull() == "**")) kept += level
        kept
    }.joinToString("/")
}

/**
 * A path [pattern] matches by construction: each wildcard filled in (a `*` among other letters may
 * match nothing), `**` with zero to two levels.
 */
private fun Random.pathMatching(pattern: String): String = pattern.split('/').flatMap { level ->
    when {
        level in SPECIAL_LEVELS -> listOf(SPECIAL_LEVELS.getValue(level))
        level == "**" -> List(nextInt(3)) { LITERALS.random(this) }
        level == "*" -> listOf(listOf("X", "Home", "a.b").random(this))
        '*' in level -> listOf(level.replace("*", listOf("X", "Home", "a.b", "").random(this)))
        else -> listOf(level)
    }
}.joinToString("/").ifEmpty { "a" }

/** [path] changed a little: a level dropped, added, or renamed. Matching or not is katachi's to say. */
private fun Random.mutated(path: String): String {
    val levels = path.split('/').toMutableList()
    when (nextInt(3)) {
        0 -> if (levels.size > 1) levels.removeAt(nextInt(levels.size)) else levels += "extra"
        1 -> levels.add(nextInt(levels.size + 1), LITERALS.random(this))
        else -> levels[nextInt(levels.size)] = LITERALS.random(this)
    }
    return levels.joinToString("/")
}

/** Every row, the hand-written ones first, then the generated ones from a fixed seed. */
private fun globCases(): List<GlobCaseJson> {
    val rows = mutableListOf<GlobCaseJson>()
    fun answer(pattern: String, paths: List<String>) {
        val glob = Glob.compile(pattern)
        paths.distinct().forEach { path ->
            rows += GlobCaseJson(
                pattern, path, glob.matches(path), error = false, glob.literalPrefix, glob.hasWildcard,
                groups = glob.matchGroups(path),
            )
        }
    }
    HAND_WRITTEN.forEach { (pattern, paths) -> answer(pattern, paths) }
    REFUSED.forEach { pattern ->
        val refused = runCatching { Glob.compile(pattern) }.exceptionOrNull() is KatachiGlobSyntaxException
        if (!refused) throw AssertionError("`$pattern` was expected not to compile")
        rows += GlobCaseJson(pattern, path = null, matches = null, error = true, literalPrefix = null, hasWildcard = null, groups = null)
    }
    val random = Random(20261005)
    val generated = LinkedHashSet<String>()
    while (generated.size < 30) generated += random.pattern()
    generated.forEach { pattern ->
        val matching = random.pathMatching(pattern)
        answer(pattern, listOf(matching, random.mutated(matching), random.mutated(matching), random.pathMatching(pattern)))
    }
    return rows
}

class GlobConformanceFixtureSpec : FreeSpec({
    "glob-cases.json がいまの Glob の答えと一字一句同じ" {
        val cases = globCases()
        cases.size shouldBeInRange 250..400
        val json = encodeToJson(ListSerializer(GlobCaseJson.serializer()), cases)
        val fixture = File(FIXTURE)
        val checkedIn = if (fixture.isFile) fixture.readText() else null
        if (System.getenv(UPDATE_ENV) == "true") {
            fixture.parentFile.mkdirs()
            fixture.writeText(json)
        } else if (checkedIn != json) {
            val state = if (checkedIn == null) "is missing" else "is out of date"
            throw AssertionError(
                "The IDE plugin's fixture ${fixture.absoluteFile.normalize()} $state: it no longer matches what Glob answers.\n" +
                    "If the change is intended, update it from the repository root with\n" +
                    "  $UPDATE_COMMAND\n" +
                    "and run the IDE plugin's KatachiGlobConformanceTest against it.",
            )
        }
    }
})
