package me.tbsten.katachi.intellij.data.json

import java.nio.file.Path

/**
 * `templateDescription.json` could not be read. The loader turns these into the error screen; they
 * never reach the user as a stack trace.
 */
internal sealed class KatachiTemplateJsonException(message: String) : IllegalStateException(message) {
    abstract val file: Path
}

/**
 * The file is empty, cut off or not JSON at all (E-37). katachi replaces the file atomically, so
 * this is an interrupted write or a hand edit rather than a race.
 */
internal class KatachiMalformedTemplateJsonException(
    override val file: Path,
    /** What the parser tripped over, with the offset. */
    val problem: String,
) : KatachiTemplateJsonException(
    message = """
        Could not read ${file.toUri()} as JSON: $problem.
        katachi writes this file in one piece, so it was probably cut off or edited by hand.
        Reload the templates to have katachiInternalTemplatesJson write it again.
    """.trimIndent(),
)

/**
 * The JSON is well formed but a required key is missing or has another type: this plugin and the
 * project's katachi disagree on the format (E-35).
 */
internal class KatachiIncompatibleTemplateJsonException(
    override val file: Path,
    /** Where in the document, such as `details[0].parameters[2].isRequired`. */
    val location: String,
    /** The JSON type the plugin expected, such as `string or null`. */
    val expected: String,
    /** The JSON type found, or `null` when the key is missing. */
    val actual: String?,
) : KatachiTemplateJsonException(
    message = """
        ${if (actual == null) "Missing key $location" else "The key $location is a $actual"} in ${file.toUri()}; expected $expected.
        This plugin and the project's katachi disagree on the format of the template list.
        Use a katachi version and a plugin version released together.
    """.trimIndent(),
)
