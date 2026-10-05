package me.tbsten.katachi.intellij.data.sarif

import java.nio.file.Path

/** A `katachi.sarif` that could not be read. The service keeps the earlier answer and shows this as the reason. */
internal sealed class KatachiSarifReadException(message: String) : IllegalStateException(message) {
    abstract val file: Path
}

/** The file is empty, cut off or not JSON. `assert()` writes it in place, so a read that races the write sees this; the service reads again. */
internal class KatachiMalformedSarifException(
    override val file: Path,
    val problem: String,
) : KatachiSarifReadException(
    message = """
        Could not read ${file.toUri()} as JSON: $problem.
        katachi may still be writing it. Run the check again if this stays.
    """.trimIndent(),
)

/** `version` is not `2.1.0`, so the keys read here may mean something else. */
internal class KatachiUnsupportedSarifVersionException(
    override val file: Path,
    /** The `version` the file declares, or `null` when it declares none. */
    val found: String?,
) : KatachiSarifReadException(
    message = """
        ${file.toUri()} declares SARIF version ${found ?: "(none)"}; this plugin reads 2.1.0.
        It was probably not written by katachi's report { sarif = true }.
    """.trimIndent(),
)

/** The JSON is SARIF 2.1.0 but lacks a key every result needs (`runs`). */
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
