package me.tbsten.katachi.processor

/**
 * `main()`'s command line, already parsed.
 *
 * @property entryPointClassName the fully qualified name of the generated
 *   [KatachiEntryPoint] to load.
 * @property processorKeys the `--processor` keys to run, in order, with duplicates removed.
 * @property args every `--arg key=value`, as a flat map.
 */
internal data class ProcessorCommandLine(
    val entryPointClassName: String,
    val processorKeys: List<String>,
    val args: Map<String, String>,
)

/**
 * Parses [argv] into a [ProcessorCommandLine].
 *
 * The Gradle plugin builds this command line for a normal run, so this parser's real job is
 * defensive: it exists so that `main()` can be called directly -- by a test, or by hand -- with
 * an error that names the bad token rather than an `ArrayIndexOutOfBoundsException`. Every token
 * this reads is `--name=value`, a single `argv` element, so a value that itself contains spaces
 * or `=` never gets split by the shell before this function sees it.
 *
 * Accepted tokens:
 * - `--entry-point=<FQCN>`, exactly once.
 * - `--processor=<key>[,<key>...]`, any number of times. Comma-split, empty pieces dropped,
 *   duplicates removed keeping the first occurrence's position. At least one key has to remain
 *   once every `--processor` token is combined.
 * - `--arg=<key>=<value>`, any number of times. Split on the *first* `=` only, so a value that
 *   itself contains `=` (`--arg=invokeImpl=TODO()`) is kept whole.
 *
 * @throws KatachiDuplicateEntryPointOptionException when `--entry-point` is given twice.
 * @throws KatachiMissingEntryPointOptionException when it is not given at all.
 * @throws KatachiMissingProcessorSelectionException when no `--processor` key remains.
 * @throws KatachiInvalidProcessorArgOptionException when an `--arg` is not `key=value`.
 * @throws KatachiDuplicateProcessorArgException when the same `--arg` key is given twice.
 * @throws KatachiUnknownProcessorOptionException for any other token.
 */
internal fun parseProcessorCommandLine(argv: Array<String>): ProcessorCommandLine {
    var entryPointClassName: String? = null
    val processorKeys = LinkedHashSet<String>()
    val args = LinkedHashMap<String, String>()

    for (token in argv) {
        when {
            token.startsWith("--entry-point=") -> {
                if (entryPointClassName != null) throw KatachiDuplicateEntryPointOptionException(token)
                entryPointClassName = token.removePrefix("--entry-point=")
            }

            token.startsWith("--processor=") -> {
                token.removePrefix("--processor=")
                    .split(',')
                    .filter { it.isNotEmpty() }
                    .forEach(processorKeys::add)
            }

            token.startsWith("--arg=") -> {
                val raw = token.removePrefix("--arg=")
                val separatorIndex = raw.indexOf('=')
                if (separatorIndex <= 0) throw KatachiInvalidProcessorArgOptionException(token)
                val key = raw.substring(0, separatorIndex)
                val value = raw.substring(separatorIndex + 1)
                if (key in args) throw KatachiDuplicateProcessorArgException(key = key, argument = token)
                args[key] = value
            }

            else -> throw KatachiUnknownProcessorOptionException(token)
        }
    }

    val resolvedEntryPointClassName = entryPointClassName ?: throw KatachiMissingEntryPointOptionException()
    if (processorKeys.isEmpty()) throw KatachiMissingProcessorSelectionException()

    return ProcessorCommandLine(
        entryPointClassName = resolvedEntryPointClassName,
        processorKeys = processorKeys.toList(),
        args = args,
    )
}
