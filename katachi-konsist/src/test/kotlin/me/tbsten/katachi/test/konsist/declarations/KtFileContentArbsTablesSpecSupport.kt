package me.tbsten.katachi.test.konsist.declarations

/*
 * What the judging properties write, with what each piece matches written down by hand: the
 * expected results come from these tables, not from katachi's own matching.
 */

/** Import paths the cases write, each with every import pattern of the cases that matches it. */
internal val IMPORT_TABLE: Map<String, Set<String>> = mapOf(
    "kotlin.collections.List" to setOf("kotlin.**", "**"),
    "kotlin.time.Duration" to setOf("kotlin.**", "**"),
    "kotlinx.coroutines.launch" to setOf("kotlinx.coroutines.*", "**"),
    "kotlinx.coroutines.*" to setOf("kotlinx.coroutines.*", "**"),
    "kotlinx.coroutines.flow.Flow" to setOf("kotlinx.coroutines.flow.*", "**"),
    "kotlinx.coroutines.flow.StateFlow" to setOf("kotlinx.coroutines.flow.*", "**"),
    "com.example.domain.GetUser" to setOf("com.example.domain.**", "com.example.**", "**"),
    "com.example.domain.model.User" to setOf("com.example.domain.**", "com.example.**", "**"),
    "com.example.App" to setOf("com.example.App", "com.example.**", "**"),
    "com.example.annotations.Keep" to setOf("com.example.annotations.*", "com.example.**", "**"),
    "com.example.annotations.*" to setOf("com.example.annotations.*", "com.example.**", "**"),
    "com.example.data.UserRepository" to setOf("com.example.data.**", "com.example.**", "**"),
    "com.example.data.model.UserEntity" to setOf("com.example.data.**", "com.example.data.model.**", "com.example.**", "**"),
    "android.content.Context" to setOf("android.**", "**"),
    "org.other.Thing" to setOf("**"),
    "com.exampleplus.Thing" to setOf("**"),
)

/** The import patterns an allowed import line may write. */
internal val ALLOWED_IMPORT_PATTERNS: List<String> = listOf(
    "kotlin.**",
    "kotlinx.coroutines.*",
    "kotlinx.coroutines.flow.*",
    "com.example.domain.**",
    "com.example.annotations.*",
    "com.example.**",
    "**",
)

/** The `not.` import lines a case may write: a pattern, and what its `.except(...)` may give back. */
internal val PROHIBITED_IMPORTS: List<Pair<String, List<String>>> = listOf(
    "com.example.data.**" to listOf("com.example.data.model.**"),
    "android.**" to emptyList(),
)

/** The import an import line names in full, with no `*`: it only allows, like any import line. */
internal const val EXACT_IMPORT: String = "com.example.App"

/** Whether an import line of [pattern] (and [except]) matches [path], read from [IMPORT_TABLE]. */
internal fun importLineMatches(pattern: String, except: List<String>, path: String): Boolean {
    val matched = IMPORT_TABLE[path] ?: error("no table entry for the import $path")
    return pattern in matched && except.none { it in matched }
}

/** Packages a case is written in, each with every package pattern of the cases that matches it. */
internal val PACKAGE_TABLE: Map<String, Set<String>> = mapOf(
    "com.example.feature.home" to setOf("com.example.**", "com.example.feature.*", "com.example.feature.home"),
    "com.example.feature.settings" to setOf("com.example.**", "com.example.feature.*"),
    "com.example.feature.home.ui" to setOf("com.example.**"),
    "com.example.legacy.old" to setOf("com.example.**", "com.example.legacy.**"),
    "org.other" to emptySet(),
)

/** The packages a case that follows its rules may be in: none the `not.` package line names. */
internal val CONFORMING_PACKAGES: List<String> = PACKAGE_TABLE.keys.filterNot { it.startsWith("com.example.legacy") }

/** The package patterns an allowed package line may write. */
internal val ALLOWED_PACKAGE_PATTERNS: List<String> = listOf("com.example.**", "com.example.feature.*", "com.example.feature.home")

/** The `not.` package line a case may write. */
internal const val PROHIBITED_PACKAGE: String = "com.example.legacy.**"

/**
 * A type a line may ask for ([pattern]), types written in a way it matches ([matching]), and a
 * type it does not match ([other]). `{F}` is the file's name.
 */
internal class TypeSample(val pattern: String, val matching: List<String>, val other: String) {
    override fun toString(): String = "TypeSample($pattern)"
}

internal val TYPE_SAMPLES: List<TypeSample> = listOf(
    TypeSample("Int", listOf("Int", "kotlin.Int"), "Long"),
    TypeSample("String?", listOf("String?", "kotlin.String?"), "String"),
    TypeSample("List<String>", listOf("List<String>", "kotlin.collections.List<kotlin.String>", "List< String >"), "List<Int>"),
    TypeSample("StateFlow<*>", listOf("StateFlow<Int>", "kotlinx.coroutines.flow.StateFlow<com.example.HomeUiState>"), "Flow<Int>"),
    TypeSample(
        "StateFlow<{F}UiState>",
        listOf("StateFlow<{F}UiState>", "kotlinx.coroutines.flow.StateFlow<com.example.{F}UiState>"),
        "StateFlow<OtherUiState>",
    ),
    TypeSample("Map<String, Int>", listOf("Map<String, Int>", "Map<String,Int>", "Map< String , Int >"), "Map<String, Long>"),
    TypeSample("(String) -> Unit", listOf("(String) -> Unit", "(kotlin.String)->Unit"), "(Int) -> Unit"),
    TypeSample("*Id", listOf("UserId", "com.example.OrderId"), "Name"),
)

/** Return types: [TYPE_SAMPLES], and `Unit`, which a block body that writes no return type returns. */
internal val UNIT_SAMPLE: TypeSample = TypeSample("Unit", listOf("Unit", "kotlin.Unit"), "Int")

internal val RETURN_SAMPLES: List<TypeSample> = TYPE_SAMPLES + UNIT_SAMPLE

/** The sample a line's type [pattern] was taken from. */
internal fun typeSampleOf(pattern: String): TypeSample =
    RETURN_SAMPLES.firstOrNull { it.pattern == pattern } ?: error("no type sample for $pattern")

/**
 * A supertype a line may ask for, how a class or an object ([classSpellings]) and an interface
 * ([interfaceSpellings]) may write one it matches, and one it does not match ([other]).
 */
internal class SupertypeSample(
    val pattern: String,
    val classSpellings: List<String>,
    val interfaceSpellings: List<String>,
    val other: String,
) {
    override fun toString(): String = "SupertypeSample($pattern)"
}

internal val SUPERTYPE_SAMPLES: List<SupertypeSample> = listOf(
    SupertypeSample("Base", listOf("Base()", "com.example.Base()", "Base<Int>()"), listOf("Base", "Base<Int>"), "Other"),
    SupertypeSample(
        "Repository<User>",
        listOf("Repository<User>", "com.example.Repository<com.example.User>"),
        listOf("Repository<User>", "Repository< User >"),
        "Repository<Admin>",
    ),
    SupertypeSample("Repository<*>", listOf("Repository<Admin>"), listOf("Repository<Admin>"), "Store<Admin>"),
    SupertypeSample("ViewModel", listOf("ViewModel()", "androidx.lifecycle.ViewModel()"), listOf("ViewModel"), "Other"),
)

/** The supertype a `Default{F}` class line asks for: the interface named after the file. */
internal val FILE_SUPERTYPE: SupertypeSample = SupertypeSample("{F}", listOf("{F}"), listOf("{F}"), "Other")

internal fun supertypeSampleOf(pattern: String): SupertypeSample =
    (SUPERTYPE_SAMPLES + FILE_SUPERTYPE).firstOrNull { it.pattern == pattern } ?: error("no supertype sample for $pattern")

/**
 * The names `annotated(...)` may write, each with how a source may write an annotation it
 * matches and the import that spelling needs (or `null`).
 */
internal val ANNOTATION_SPELLINGS: Map<String, List<Pair<String, String?>>> = mapOf(
    "Keep" to listOf("Keep" to null, "com.example.annotations.Keep" to null),
    "com.example.annotations.Keep" to listOf(
        "com.example.annotations.Keep" to null,
        "Keep" to "com.example.annotations.Keep",
        "Keep" to "com.example.annotations.*",
    ),
    "Mark*" to listOf("Marker" to null, "Marker(\"reason\")" to null),
)

/** The visibility keywords. */
internal val VISIBILITIES: Set<String> = setOf("public", "internal", "protected", "private")
