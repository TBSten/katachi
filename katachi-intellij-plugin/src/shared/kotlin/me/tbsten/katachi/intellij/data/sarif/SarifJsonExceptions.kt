package me.tbsten.katachi.intellij.data.sarif

import java.nio.file.Path

internal sealed class KatachiSarifReadException(message: String) : IllegalStateException(message) {
    abstract val file: Path
}

internal class KatachiMalformedSarifException(
    override val file: Path,
    val problem: String,
) : KatachiSarifReadException(
    message = """
        Could not read ${file.toUri()} as JSON: $problem.
        katachi may still be writing it. Run the check again if this stays.
    """.trimIndent(),
)

internal class KatachiUnsupportedSarifVersionException(
    override val file: Path,
    val found: String?,
) : KatachiSarifReadException(
    message = """
        ${file.toUri()} declares SARIF version ${found ?: "(none)"}; this plugin reads 2.1.0.
        It was probably not written by katachi's report { sarif = true }.
    """.trimIndent(),
)

internal class KatachiIncompatibleSarifException(
    override val file: Path,
    val location: String,
    val expected: String,
) : KatachiSarifReadException(
    message = """
        Missing or mistyped $location in ${file.toUri()}; expected $expected.
        It was probably not written by katachi's report { sarif = true }.
    """.trimIndent(),
)
