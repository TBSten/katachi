package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.internal.MemoSlot
import java.util.concurrent.ConcurrentMap
import kotlin.reflect.KClass

/**
 * What one constraint is asked about: the files it covers, and where they came from.
 *
 * Built by the check, never by a caller — a subject that named files the walk never saw would
 * make a report point at paths that do not exist, so there is no public constructor.
 *
 * ## Example 1: read the covered files inside a constraint
 * ```kt
 * val noTodo = FileConstraint { subject ->
 *     subject.files.filter { File("${subject.projectRoot}/$it").readText().contains("TODO") }
 *         .map { FileConstraintFailure(it) }
 * }
 * ```
 */
@ExperimentalKatachiApi
public class FileConstraintSubject internal constructor(
    /**
     * The role whose layout the constraint was written in.
     *
     * ## Example 1: word a failure with the role it belongs to
     * ```kt
     * FileConstraint { subject ->
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
     * FileConstraint { subject ->
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
     * FileConstraint { subject ->
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
     * FileConstraint { subject ->
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
     * FileConstraint { subject ->
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
     * FileConstraint { subject ->
     *     subject.files.filterNot { it.endsWith(".kt") }.map { FileConstraintFailure(it) }
     * }
     * ```
     */
    public val files: List<String>,
    private val shared: ConcurrentMap<Any, MemoSlot>,
    /**
     * Every name a `capture("...")` of the layout this constraint covers gives a value to: the
     * names of a `":...:${capture("...")}".module { }` key first, then those of the path, in the
     * order they were written. Empty when the layout names nothing.
     *
     * A name here is one [captureValuesOf] can answer for -- for a file whose own layout
     * declaration names it. A role whose places are declared by more than one path may name
     * something on one path only.
     *
     * ## Example 1: refuse a rule that asks for a name the layout does not capture
     * ```kt
     * FileConstraint { subject ->
     *     if ("feature" !in subject.captureNames) {
     *         throw IllegalStateException("the layout of ${subject.role.qualifiedName} captures no feature")
     *     }
     *     emptyList()
     * }
     * ```
     */
    public val captureNames: Set<String> = emptySet(),
    private val captureValues: (String) -> Map<String, String> = { emptyMap() },
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
     * [create] runs at most once per key per run, also when `FileConstraintCheck(parallelism = n)`
     * evaluates constraints on several threads: the others asking for the same key wait for it.
     * One that throws stores nothing, and the next caller runs its own [create].
     *
     * ## Example 1: parse a set of directories once per run
     * ```kt
     * FileConstraint { subject ->
     *     val parsed = subject.memo(ParsedScopeKey(subject.paths), ParsedScope::class) {
     *         ParsedScope.of(subject.paths)
     *     }
     *     parsed.reject(subject.files)
     * }
     * ```
     */
    public fun <T : Any> memo(key: Any, type: KClass<T>, create: () -> T): T {
        val slot = shared[key] ?: MemoSlot().let { fresh -> shared.putIfAbsent(key, fresh) ?: fresh }
        val existing = slot.getOrCreate(create)
        // Checked before it is narrowed, so the failure is this library's own exception rather
        // than a bare ClassCastException: `Class.cast` below can no longer throw.
        val jvmType = type.java
        if (!jvmType.isInstance(existing)) {
            throw KatachiFileConstraintMemoTypeException(
                key = key.toString(),
                expected = type,
                actual = existing::class,
            )
        }
        return jvmType.cast(existing)
    }

    /**
     * What each `capture("...")` of the layout took from [file]'s own path, by name.
     *
     * Read off the layout declarations that match [file]: `"feature" / capture("feature") / ...`
     * gives `feature`, `"${capture("name")}Repository".ktFile()` gives `name`, and a module key
     * `":feature:${capture("feature")}"` gives the module's own name. A name whose declaration
     * does not match [file] is left out, so the map can be smaller than [captureNames] -- and
     * empty for a file no declaration names anything on.
     *
     * Meant for one of [files]. Asked about another path, it still answers with whatever the
     * layout's declarations say about that path rather than refusing.
     *
     * ## Example 1: hold every file to the feature its directory names
     * ```kt
     * FileConstraint { subject ->
     *     subject.files.filter { file ->
     *         val feature = subject.captureValuesOf(file)["feature"] ?: return@filter false
     *         !file.substringAfterLast('/').startsWith(feature.replaceFirstChar { it.uppercase() })
     *     }.map { FileConstraintFailure(it) }
     * }
     * ```
     */
    public fun captureValuesOf(file: String): Map<String, String> = captureValues(file)

    /** The role, the constraint (its name, or where it was declared) and the number of files. */
    override fun toString(): String =
        "FileConstraintSubject(${role.qualifiedName}, ${name ?: declaredAt}, files=${files.size})"
}
