package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.internal.MemoSlot
import java.util.concurrent.ConcurrentMap
import kotlin.reflect.KClass

/**
 * What one constraint is asked about: the files it covers, and where they came from.
 *
 * Built by the check, never by a caller, so it only ever names files that exist.
 *
 * ## Example 1: read the covered files inside a constraint
 * ```kt
 * val noTodo = FileConstraint { subject ->
 *     println("${subject.role.qualifiedName} ${subject.name ?: subject.declaredAt} under ${subject.paths}")
 *     subject.files.filter { File("${subject.projectRoot}/$it").readText().contains("TODO") }
 *         .map { FileConstraintFailure(it) }
 * }
 * ```
 *
 * ## Example 2: hold every file to the feature its directory names
 * ```kt
 * FileConstraint { subject ->
 *     check("feature" in subject.captureNames) { "the layout captures no feature" }
 *     subject.files.filter { file ->
 *         val feature = subject.captureValuesOf(file)["feature"] ?: return@filter false
 *         !file.substringAfterLast('/').startsWith(feature.replaceFirstChar { it.uppercase() })
 *     }.map { FileConstraintFailure(it) }
 * }
 * ```
 */
@ExperimentalKatachiApi
public class FileConstraintSubject internal constructor(
    /** The role whose layout the constraint was written in. */
    public val role: Role,
    /** What the constraint was called, or `null` when it was declared without a name. */
    public val name: String?,
    /** Where the constraint was written, so that a backend's own error can point back at it. */
    public val declaredAt: DeclarationSite,
    /**
     * The resolved directories this constraint is anchored at, outermost first, project
     * relative.
     */
    public val paths: List<String>,
    /**
     * The project root, absolute and `/` separated, for a backend that has to open a file.
     *
     * Everything else katachi hands out is project relative.
     */
    public val projectRoot: String,
    /**
     * Files that exist and that this constraint covers, project relative, in walk order.
     *
     * Empty is a normal answer: a place that fills up over time (one directory per feature) holds no files
     * until it does, and a constraint over nothing reports nothing.
     */
    public val files: List<String>,
    private val shared: ConcurrentMap<Any, MemoSlot>,
    /**
     * Every name a `capture("...")` of the covered layout gives a value to, in the order written.
     *
     * Empty when the layout names nothing. [captureValuesOf] answers for these names, for a file
     * whose own layout declaration names them; a role declared by more than one path may name
     * something on one path only. A derived capture (`capture("x").pascalCase`) is not listed.
     */
    public val captureNames: Set<String> = emptySet(),
    private val captureValues: (String) -> Map<String, String> = { emptyMap() },
) {
    /**
     * Scratch space shared by every constraint of one run, and thrown away with it.
     *
     * "One run" is one `assert(...)` call, whatever checks it was handed. Use it for something
     * expensive that does not depend on which constraint is asking (a parsed scope, an index),
     * and key it on a value **unique to your backend**: two backends using the same key for
     * different types throw [KatachiFileConstraintMemoTypeException].
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
     *
     * @throws KatachiFileConstraintMemoTypeException when a value of another type is already stored
     *   under [key].
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
     * gives `feature`, and `"${capture("name")}Repository".ktFile()` gives `name`. A name whose
     * declaration does not match [file] is left out, so the map can be smaller than
     * [captureNames][FileConstraintSubject.captureNames]. A name written on two levels of one path gives one value.
     *
     * Meant for one of [files][FileConstraintSubject.files]. Asked about another path, it still answers with whatever the
     * layout's declarations say about that path rather than refusing.
     */
    public fun captureValuesOf(file: String): Map<String, String> = captureValues(file)

    /** The role, the constraint (its name, or where it was declared) and the number of files. */
    override fun toString(): String =
        "FileConstraintSubject(${role.qualifiedName}, ${name ?: declaredAt}, files=${files.size})"
}
