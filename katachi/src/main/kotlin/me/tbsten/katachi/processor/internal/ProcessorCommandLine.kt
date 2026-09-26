package me.tbsten.katachi.processor.internal

import me.tbsten.katachi.processor.KatachiDuplicateEntryPointOptionException
import me.tbsten.katachi.processor.KatachiDuplicateProcessorArgException
import me.tbsten.katachi.processor.KatachiInvalidProcessorArgForOptionException
import me.tbsten.katachi.processor.KatachiInvalidProcessorArgOptionException
import me.tbsten.katachi.processor.KatachiMissingEntryPointOptionException
import me.tbsten.katachi.processor.KatachiMissingProcessorSelectionException
import me.tbsten.katachi.processor.KatachiUnknownProcessorOptionException

/**
 * `main()`'s command line, already parsed.
 *
 * @property entryPointClassName the fully qualified name of the generated
 *   [KatachiEntryPoint] to load.
 * @property processorKeys the `--processor` keys to run, in order, with duplicates removed.
 * @property args every `--arg key=value`, as a flat map. Every selected processor is given them.
 * @property argsFor every `--arg-for=<processor key>:<name>=<value>`, by processor key. Only that
 *   processor is given them, and an [args] entry of the same name wins over them.
 */
internal data class ProcessorCommandLine(
    val entryPointClassName: String,
    val processorKeys: List<String>,
    val args: Map<String, String>,
    val argsFor: Map<String, Map<String, String>> = emptyMap(),
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
 * - `--arg-for=<processor key>:<key>=<value>`, any number of times. Split on the first `:`, then
 *   on the first `=` after it. The processor key has to be one of the `--processor` keys.
 *
 * @throws KatachiDuplicateEntryPointOptionException when `--entry-point` is given twice.
 * @throws KatachiMissingEntryPointOptionException when it is not given at all.
 * @throws KatachiMissingProcessorSelectionException when no `--processor` key remains.
 * @throws KatachiInvalidProcessorArgOptionException when an `--arg` is not `key=value`.
 * @throws KatachiDuplicateProcessorArgException when the same `--arg` key, or the same
 *   processor key and name of `--arg-for`, is given twice.
 * @throws KatachiInvalidProcessorArgForOptionException when an `--arg-for` is not
 *   `<processor key>:<key>=<value>`, or names a processor no `--processor` selected.
 * @throws KatachiUnknownProcessorOptionException for any other token.
 */
internal fun parseProcessorCommandLine(argv: Array<String>): ProcessorCommandLine {
    var entryPointClassName: String? = null
    val processorKeys = LinkedHashSet<String>()
    val args = LinkedHashMap<String, String>()
    val argsFor = LinkedHashMap<String, LinkedHashMap<String, String>>()
    val argForTokens = LinkedHashMap<String, String>()

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

            token.startsWith("--arg-for=") -> {
                val raw = token.removePrefix("--arg-for=")
                val keyEnd = raw.indexOf(':')
                val nameEnd = if (keyEnd <= 0) -1 else raw.indexOf('=', startIndex = keyEnd + 1)
                if (nameEnd <= keyEnd + 1) {
                    throw KatachiInvalidProcessorArgForOptionException(token, processorKey = null, selectedKeys = emptyList())
                }
                val processorKey = raw.substring(0, keyEnd)
                val name = raw.substring(keyEnd + 1, nameEnd)
                val forProcessor = argsFor.getOrPut(processorKey) { LinkedHashMap() }
                if (name in forProcessor) {
                    throw KatachiDuplicateProcessorArgException(key = "$processorKey:$name", argument = token)
                }
                forProcessor[name] = raw.substring(nameEnd + 1)
                argForTokens.putIfAbsent(processorKey, token)
            }

            else -> throw KatachiUnknownProcessorOptionException(token)
        }
    }

    val resolvedEntryPointClassName = entryPointClassName ?: throw KatachiMissingEntryPointOptionException()
    if (processorKeys.isEmpty()) throw KatachiMissingProcessorSelectionException()
    // After the loop rather than inside it: `--arg-for` may come before the `--processor` that
    // selects its processor.
    for ((processorKey, token) in argForTokens) {
        if (processorKey !in processorKeys) {
            throw KatachiInvalidProcessorArgForOptionException(token, processorKey, processorKeys.toList())
        }
    }

    return ProcessorCommandLine(
        entryPointClassName = resolvedEntryPointClassName,
        processorKeys = processorKeys.toList(),
        args = args,
        argsFor = argsFor,
    )
}
