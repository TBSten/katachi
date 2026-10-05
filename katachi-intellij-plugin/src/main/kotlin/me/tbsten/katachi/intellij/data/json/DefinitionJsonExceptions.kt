package me.tbsten.katachi.intellij.data.json

import java.nio.file.Path

/**
 * `definition.json` could not be read. The loader turns these into the error screen; they never
 * reach the user as a stack trace.
 */
internal sealed class KatachiDefinitionJsonException(message: String) : IllegalStateException(message) {
    abstract val file: Path
}

/** The file is empty, cut off or not JSON at all. katachi replaces the file in one move, so this is an interrupted write or a hand edit. */
internal class KatachiMalformedDefinitionJsonException(
    override val file: Path,
    /** What the parser tripped over, with the offset. */
    val problem: String,
) : KatachiDefinitionJsonException(
    message = """
        Could not read ${file.toUri()} as JSON: $problem.
        katachi writes this file in one piece, so it was probably cut off or edited by hand.
        Reload the definition to have katachiInternalDefinitionJson write it again.
    """.trimIndent(),
)

/** The JSON is well formed but a required key is missing or has another type: the plugin and the project's katachi disagree on the format. */
internal class KatachiIncompatibleDefinitionJsonException(
    override val file: Path,
    /** Where in the document, such as `roles[0].layout[2].kind`. */
    val location: String,
    /** The JSON type the plugin expected, such as `string or null`. */
    val expected: String,
    /** The JSON type found, or `null` when the key is missing. */
    val actual: String?,
) : KatachiDefinitionJsonException(
    message = """
        ${if (actual == null) "Missing key $location" else "The key $location is a $actual"} in ${file.toUri()}; expected $expected.
        This plugin and the project's katachi disagree on the format of the definition.
        Use a katachi version and a plugin version released together.
    """.trimIndent(),
)

/** The `schemaVersion` is not one this plugin reads. A newer katachi changed the meaning of a key. */
internal class KatachiUnsupportedDefinitionSchemaException(
    override val file: Path,
    /** The version the file declares. */
    val found: Int,
    /** The versions this plugin reads. */
    val supported: List<Int>,
) : KatachiDefinitionJsonException(
    message = """
        ${file.toUri()} has schemaVersion $found; this plugin reads ${supported.joinToString(" and ")}.
        katachi and the katachi IDE plugin are of versions that do not match.
        Use a katachi version and a plugin version released together.
    """.trimIndent(),
)
