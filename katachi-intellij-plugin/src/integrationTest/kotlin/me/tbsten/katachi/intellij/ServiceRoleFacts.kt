package me.tbsten.katachi.intellij

import java.nio.file.Path
import kotlin.io.path.readLines

/**
 * What the smokes expect of sample/jvm's `ServiceRole.kt`, read from the copy's file instead of written down: the line of the
 * declaration that allows `class HealthService`, and the name of the `ktFileContent { }` constraint. A line number
 * written into a test goes stale at the next edit of the sample (it did, twice); this follows the file.
 */
internal class ServiceRoleFacts private constructor(lines: List<String>) {
    /**
     * 1-based line of `required(public, `class`, fileName)`: what the gutter names as the allowing line, and where "Go to the rule"
     * of a rejected file lands.
     */
    val allowedLine: Int = lines.indexOfFirst { REQUIRED.containsMatchIn(it) }.also {
        check(it >= 0) { "ServiceRole.kt no longer has a `required(…, `class`, fileName)` line; update ServiceRoleFacts" }
    } + 1

    /** What the gutter and the underline call the declaration, as `<modifiers> class HealthService`. */
    val allowedDeclaration: String = "public class HealthService"

    private val constraintIndex: Int = lines.indexOfFirst { CONSTRAINT.containsMatchIn(it) }.also {
        check(it >= 0) { "ServiceRole.kt no longer has a `\"<name>\".ktFileContent {` constraint; update ServiceRoleFacts" }
    }

    /** The constraint's name, as written in the definition. */
    val constraintName: String = CONSTRAINT.find(lines[constraintIndex])!!.groupValues[1]

    companion object {
        private val REQUIRED = Regex("""required\(\s*public\s*,\s*`class`\s*,\s*fileName\s*\)""")
        private val CONSTRAINT = Regex("""^\s*"([^"]+)"\.ktFileContent\s*\{""")

        /** [project] is the copy of sample/jvm. */
        fun of(project: Path): ServiceRoleFacts =
            ServiceRoleFacts(project.resolve("architecture-test/src/test/kotlin/com/example/domain/ServiceRole.kt").readLines())
    }
}
