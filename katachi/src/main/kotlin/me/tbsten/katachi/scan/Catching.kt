package me.tbsten.katachi.scan

/**
 * Whether the check has to end instead of turning this into a violation.
 *
 * `runCatching` swallows every [Throwable], which is more than the walk is entitled to decide
 * about. The four below are the ones where carrying on either cannot work or destroys
 * something, and they are listed here rather than at each call site so that every unit of the
 * walk treats a failure the same way.
 *
 * A [me.tbsten.katachi.KatachiInternalException] is deliberately not among them. It is a
 * katachi bug, but turning one path into an `[UncheckedFile]` and checking the rest hands the
 * reader more than failing the whole run would.
 */
internal val Throwable.isFatal: Boolean
    get() = when (this) {
        // The JVM cannot carry on. Catching one only loses the cause: the next path runs into
        // the same wall with less information attached.
        is VirtualMachineError -> true
        // The classpath is broken, so every path fails the same way and the report fills up
        // with copies of one problem instead of the violations it was asked for.
        is LinkageError -> true
        // The caller asked for this to stop. Swallowing that is ignoring it.
        is InterruptedException -> true
        // An assertion, which is a result rather than a failure of the walk: a finished check
        // (`KatachiArchitectureAssertionError`) is one, and so is an assertion the caller's own
        // test harness threw from inside the file system it handed in. Reporting either as an
        // `[UncheckedFile]` would bury the answer the caller is waiting for. Named by the whole
        // family rather than by the check's own class, because the check is written on top of
        // this walk and naming it here would point the dependency back up.
        is AssertionError -> true
        else -> false
    }

/**
 * Runs [block] and keeps whatever it threw, unless [isFatal] says the run has to end.
 *
 * Written with `is` rather than `as`: nothing is cast here, the type is only asked about.
 *
 * It sits in `scan` rather than next to the walk that first needed it because the same rule
 * has to hold one layer up: `validate` runs each check handed to it under this, so that a
 * third-party check that throws becomes one `[UncheckedCheck]` block instead of the end of
 * the run. "Here it failed, so the neighbour still answers" is one rule, and one rule is one
 * place.
 */
internal inline fun <T> catching(block: () -> T): Result<T> =
    runCatching(block).onFailure { if (it.isFatal) throw it }
