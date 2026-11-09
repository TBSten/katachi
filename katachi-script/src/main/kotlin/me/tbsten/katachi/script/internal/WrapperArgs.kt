package me.tbsten.katachi.script.internal

/**
 * The mark `bin/katachi` puts in front of an argument `kotlin` would otherwise take for its own JVM.
 *
 * `kotlin` takes every `-D…` and `-J…`, wherever it sits on the command line, and reads every `@…` as an argfile, so
 * `katachi agent test -- -Dkatachi.baseline.update=true` would lose its `-D…` and
 * `katachi agent add … --allowed "@Serializable なルート"` would fail. `bin/katachi` writes
 * this same string (keep the two in sync) in front of those and of any argument already starting with it.
 */
internal const val WRAPPER_ARG_ESCAPE: String = "katachi-escaped:"

/** Undoes what `bin/katachi` did to [args] so the command sees them as the user typed them. */
internal fun unescapeWrapperArgs(args: List<String>): List<String> = args.map { it.removePrefix(WRAPPER_ARG_ESCAPE) }
