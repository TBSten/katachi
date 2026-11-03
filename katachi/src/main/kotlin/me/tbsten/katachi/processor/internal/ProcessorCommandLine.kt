package me.tbsten.katachi.processor.internal

import me.tbsten.katachi.processor.KatachiDuplicateEntryPointOptionException
import me.tbsten.katachi.processor.KatachiDuplicateProcessorArgException
import me.tbsten.katachi.processor.KatachiInvalidProcessorArgForOptionException
import me.tbsten.katachi.processor.KatachiInvalidProcessorArgOptionException
import me.tbsten.katachi.processor.KatachiMissingEntryPointOptionException
import me.tbsten.katachi.processor.KatachiMissingProcessorSelectionException
import me.tbsten.katachi.processor.KatachiUnknownProcessorOptionException

internal data class ProcessorCommandLine(
    val entryPointClassName: String,
    val processorKeys: List<String>,
    val args: Map<String, String>,
    val argsFor: Map<String, Map<String, String>> = emptyMap(),
    val definitionErrorsPath: String? = null,
)

// Every token is --name=value in one argv element, so values with spaces or = are never split by the shell.
internal fun parseProcessorCommandLine(argv: Array<String>): ProcessorCommandLine {
    var entryPointClassName: String? = null
    val processorKeys = LinkedHashSet<String>()
    val args = LinkedHashMap<String, String>()
    val argsFor = LinkedHashMap<String, LinkedHashMap<String, String>>()
    val argForTokens = LinkedHashMap<String, String>()
    var definitionErrorsPath: String? = null

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

            token.startsWith("--definition-errors=") -> {
                definitionErrorsPath = token.removePrefix("--definition-errors=").takeIf { it.isNotEmpty() }
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
        definitionErrorsPath = definitionErrorsPath,
    )
}
