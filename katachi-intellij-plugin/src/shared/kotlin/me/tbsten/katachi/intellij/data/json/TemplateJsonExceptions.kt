package me.tbsten.katachi.intellij.data.json

import java.nio.file.Path

internal sealed class KatachiTemplateJsonException(message: String) : IllegalStateException(message) {
    abstract val file: Path
}

internal class KatachiMalformedTemplateJsonException(
    override val file: Path,
    val problem: String,
) : KatachiTemplateJsonException(
    message = """
        Could not read ${file.toUri()} as JSON: $problem.
        katachi writes this file in one piece, so it was probably cut off or edited by hand.
        Reload the templates to have katachiInternalTemplatesJson write it again.
    """.trimIndent(),
)

internal class KatachiIncompatibleTemplateJsonException(
    override val file: Path,
    val location: String,
    val expected: String,
    val actual: String?,
) : KatachiTemplateJsonException(
    message = """
        ${if (actual == null) "Missing key $location" else "The key $location is a $actual"} in ${file.toUri()}; expected $expected.
        This plugin and the project's katachi disagree on the format of the template list.
        Use a katachi version and a plugin version released together.
    """.trimIndent(),
)
