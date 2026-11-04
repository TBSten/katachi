package me.tbsten.katachi.intellij.data.json

import java.nio.file.Path

internal sealed class KatachiDefinitionJsonException(message: String) : IllegalStateException(message) {
    abstract val file: Path
}

internal class KatachiMalformedDefinitionJsonException(
    override val file: Path,
    val problem: String,
) : KatachiDefinitionJsonException(
    message = """
        Could not read ${file.toUri()} as JSON: $problem.
        katachi writes this file in one piece, so it was probably cut off or edited by hand.
        Reload the definition to have katachiInternalDefinitionJson write it again.
    """.trimIndent(),
)

internal class KatachiIncompatibleDefinitionJsonException(
    override val file: Path,
    val location: String,
    val expected: String,
    val actual: String?,
) : KatachiDefinitionJsonException(
    message = """
        ${if (actual == null) "Missing key $location" else "The key $location is a $actual"} in ${file.toUri()}; expected $expected.
        This plugin and the project's katachi disagree on the format of the definition.
        Use a katachi version and a plugin version released together.
    """.trimIndent(),
)

internal class KatachiUnsupportedDefinitionSchemaException(
    override val file: Path,
    val found: Int,
    val supported: List<Int>,
) : KatachiDefinitionJsonException(
    message = """
        ${file.toUri()} has schemaVersion $found; this plugin reads ${supported.joinToString(" and ")}.
        katachi and the katachi IDE plugin are of versions that do not match.
        Use a katachi version and a plugin version released together.
    """.trimIndent(),
)
