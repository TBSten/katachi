package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi
import kotlin.reflect.KClass

/**
 * What a `constraint { }` block asks of the files the layout around it matched.
 *
 * It is handed the files that **exist**, already narrowed to the block the constraint was
 * written in, and answers with the ones it rejects. Whatever it does in between — parsing
 * Kotlin, reading bytes, matching names — is none of katachi's business: katachi walks the
 * tree once, hands the result over, and turns what comes back into report blocks.
 *
 * A constraint that finds nothing to say returns an empty list. There is no way to say
 * "nothing was actually checked", which is why a backend that can tell (`katachi-konsist`
 * counts its own expectations) raises instead of returning empty.
 *
 * ## Example 1: reject every file whose name does not end in `UseCase.kt`
 * ```kt
 * val namedUseCase = FileSetConstraint { subject ->
 *     subject.files.filterNot { it.endsWith("UseCase.kt") }.map { ConstraintFailure(it) }
 * }
 * ```
 */
@ExperimentalKatachiApi
public fun interface FileSetConstraint {
    /**
     * Answers with the files of [subject] this constraint rejects, empty when it rejects none.
     *
     * Every [ConstraintFailure.file] has to be one of [ConstraintSubject.files]: answering
     * about a file that was never asked about is reported rather than dropped, because a
     * backend that has silently stopped seeing the right files has no other way to find out.
     *
     * ## Example 1: evaluate a constraint over the files of one subject
     * ```kt
     * val rejected = namedUseCase.evaluate(subject).map { it.file }
     * rejected shouldBe listOf("core/domain/useCase/Helper.kt")
     * ```
     */
    public fun evaluate(subject: ConstraintSubject): List<ConstraintFailure>
}

/**
 * One file a [FileSetConstraint] rejected, and optionally where inside it.
 *
 * A `data class` so that a backend's own tests can compare what it produced with
 * `shouldBe listOf(ConstraintFailure("core/domain/useCase/Helper.kt"))`, and so that the same
 * declaration rejected twice in one block counts once.
 *
 * ## Example 1: reject a whole file, without naming anything inside it
 * ```kt
 * ConstraintFailure("core/domain/useCase/Helper.kt")
 * ```
 *
 * ## Example 2: reject one declaration inside a file
 * ```kt
 * ConstraintFailure("core/domain/useCase/Helper.kt", declaration = "Helper", line = 12)
 * ```
 */
@ExperimentalKatachiApi
public data class ConstraintFailure(
    /**
     * The rejected file, project relative and spelled exactly as in [ConstraintSubject.files].
     *
     * ## Example 1: read back which file was rejected
     * ```kt
     * ConstraintFailure("core/domain/useCase/Helper.kt").file shouldBe
     *     "core/domain/useCase/Helper.kt"
     * ```
     */
    public val file: String,
    /**
     * What inside the file was rejected — a class or function name — or `null` for a backend
     * that works one whole file at a time.
     *
     * ## Example 1: name the class a rule rejected
     * ```kt
     * ConstraintFailure("core/domain/useCase/Helper.kt", declaration = "Helper").declaration
     *     shouldBe "Helper"
     * ```
     */
    public val declaration: String? = null,
    /**
     * Line the rejected declaration starts at, 1-based, or `null` when the backend cannot say.
     *
     * ## Example 1: point a report at the line a rule rejected
     * ```kt
     * ConstraintFailure("core/domain/useCase/Helper.kt", line = 12).line shouldBe 12
     * ```
     */
    public val line: Int? = null,
)

/**
 * What one constraint is asked about: the files it covers, and where they came from.
 *
 * Built by the check, never by a caller — a subject that named files the walk never saw would
 * make a report point at paths that do not exist, so there is no public constructor.
 *
 * ## Example 1: read the covered files inside a constraint
 * ```kt
 * val noTodo = FileSetConstraint { subject ->
 *     subject.files.filter { File("${subject.projectRoot}/$it").readText().contains("TODO") }
 *         .map { ConstraintFailure(it) }
 * }
 * ```
 */
@ExperimentalKatachiApi
public class ConstraintSubject internal constructor(
    /**
     * The role whose layout the constraint was written in.
     *
     * ## Example 1: word a failure with the role it belongs to
     * ```kt
     * FileSetConstraint { subject ->
     *     println("checking ${subject.role.qualifiedName}")
     *     emptyList()
     * }
     * ```
     */
    public val role: Role,
    /**
     * What the constraint was called, or `null` when it was declared without a name.
     *
     * ## Example 1: fall back to the declaration site when there is no name
     * ```kt
     * FileSetConstraint { subject ->
     *     println(subject.name ?: subject.declaredAt.toString())
     *     emptyList()
     * }
     * ```
     */
    public val name: String?,
    /**
     * Where the constraint was written, so that a backend's own error can point back at it.
     *
     * ## Example 1: point a backend's own error at the line that declared the constraint
     * ```kt
     * FileSetConstraint { subject ->
     *     throw IllegalStateException("cannot run, declared at ${subject.declaredAt}")
     * }
     * ```
     */
    public val declaredAt: DeclarationSite,
    /**
     * The resolved directories this constraint is anchored at, outermost first, project
     * relative.
     *
     * ## Example 1: name the directories a constraint covers
     * ```kt
     * FileSetConstraint { subject ->
     *     println("under ${subject.paths.joinToString(", ")}")
     *     emptyList()
     * }
     * ```
     */
    public val paths: List<String>,
    /**
     * The project root, absolute and `/` separated, for a backend that has to open a file.
     *
     * Everything else katachi hands out is project relative; this is the one value that is
     * not, because a parser cannot be pointed at a relative path.
     *
     * ## Example 1: turn a covered file into an absolute path
     * ```kt
     * FileSetConstraint { subject ->
     *     val absolute = subject.files.map { "${subject.projectRoot}/$it" }
     *     emptyList()
     * }
     * ```
     */
    public val projectRoot: String,
    /**
     * Files that exist and that this constraint covers, project relative, in walk order.
     *
     * Empty is a normal answer: a place that fills up over time (`":feature:*"`) holds no
     * files until it does, and a constraint over nothing reports nothing.
     *
     * ## Example 1: reject every covered file that is not a Kotlin file
     * ```kt
     * FileSetConstraint { subject ->
     *     subject.files.filterNot { it.endsWith(".kt") }.map { ConstraintFailure(it) }
     * }
     * ```
     */
    public val files: List<String>,
    private val shared: MutableMap<Any, Any>,
) {
    /**
     * Scratch space shared by every constraint of one run, and thrown away with it.
     *
     * The map belongs to the model one walk of the project built, so "one run" means one
     * walk whatever mix of checks was handed to `assert(...)`. Use it for something expensive
     * that does not depend on which constraint is asking — a parsed scope, an index — and key
     * it on a value **unique to the backend that holds it**: two backends using the same key
     * for different types is reported as a bug rather than silently handing one the other's
     * value.
     *
     * ## Example 1: parse a set of directories once per run
     * ```kt
     * FileSetConstraint { subject ->
     *     val parsed = subject.memo(ParsedScopeKey(subject.paths), ParsedScope::class) {
     *         ParsedScope.of(subject.paths)
     *     }
     *     parsed.reject(subject.files)
     * }
     * ```
     */
    public fun <T : Any> memo(key: Any, type: KClass<T>, create: () -> T): T {
        val existing = shared[key] ?: return create().also { shared[key] = it }
        // Checked before it is narrowed, so the failure is this library's own exception rather
        // than a bare ClassCastException: `Class.cast` below can no longer throw.
        val jvmType = type.java
        if (!jvmType.isInstance(existing)) {
            throw KatachiConstraintMemoTypeException(
                key = key.toString(),
                expected = type,
                actual = existing::class,
            )
        }
        return jvmType.cast(existing)
    }

    override fun toString(): String =
        "ConstraintSubject(${role.qualifiedName}, ${name ?: declaredAt}, files=${files.size})"
}
