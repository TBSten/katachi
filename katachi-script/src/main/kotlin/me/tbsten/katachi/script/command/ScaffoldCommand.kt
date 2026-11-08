// Generated from COMMAND_SPECS (internal/CommandSpecs.kt) by CommandBuildersSpec. Do not edit by hand:
// change the spec and run `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.

package me.tbsten.katachi.script.command

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.script.Katachi
import me.tbsten.katachi.script.internal.CommandLine
import me.tbsten.katachi.script.internal.commandText
import me.tbsten.katachi.script.internal.requireOption
import me.tbsten.katachi.script.internal.run
import me.tbsten.katachi.script.option.DependencyStyle
import me.tbsten.katachi.script.option.KotestStyle
import me.tbsten.katachi.script.option.TestFramework

/**
 * The `katachi scaffold` command: creates the test module and adds it to the build.
 *
 * Building one is writing `katachi scaffold ...` in a shell: the methods are named after the
 * options of `katachi scaffold --help` and add the same flag. Each returns a new
 * command; calling one twice keeps the last value.
 * `--package` is required and is checked by [execute], not while the command is built.
 *
 * ## Example 1: build the command, print it and run it
 *
 * ```kt
 * val command = Katachi().scaffold.`package`("me.tbsten.katachi.sample").`test-framework`(kotest)
 * println(command) // katachi scaffold --package me.tbsten.katachi.sample --test-framework kotest
 * command.execute()
 * ```
 *
 * The `execute()` above runs the same thing as this line in a shell:
 *
 * ```sh
 * katachi scaffold --package me.tbsten.katachi.sample --test-framework kotest
 * ```
 *
 * @see Katachi
 */
public class ScaffoldCommand internal constructor(
    private val katachi: Katachi,
    private val line: CommandLine,
    private val declaredAt: DeclarationSite,
) : KatachiCommand {
    private fun next(line: CommandLine) = ScaffoldCommand(katachi, line, declaredAt)

    /**
     * Same as `katachi scaffold --package <name>` on the command line: the package of the app. Required.
     *
     * @param name Such as `com.example.app`.
     */
    public fun `package`(name: String): ScaffoldCommand = next(line.with("--package", name))

    /**
     * Same as `katachi scaffold --katachi <version>` on the command line: the katachi version to depend on. Without it, the version `init` recorded.
     *
     * @param version Such as `0.4.0`.
     */
    public fun katachi(version: String): ScaffoldCommand = next(line.with("--katachi", version))

    /**
     * Same as `katachi scaffold --kotlin <version>` on the command line: the Kotlin version. Without it, the one found in the project.
     *
     * @param version Such as `2.2.21`.
     */
    public fun kotlin(version: String): ScaffoldCommand = next(line.with("--kotlin", version))

    /**
     * Same as `katachi scaffold --no-konsist` on the command line: lays the module out without katachi-konsist, so `ktFileContent { }` is not available.
     *
     * @param enabled False takes the option off again.
     */
    public fun `no-konsist`(enabled: Boolean = true): ScaffoldCommand = next(line.withFlag("--no-konsist", enabled))

    /**
     * Same as `katachi scaffold --module-dir <path>` on the command line: where the test module goes, relative to the Gradle root.
     *
     * @param path Such as `static-analysis/katachi`.
     */
    public fun `module-dir`(path: String): ScaffoldCommand = next(line.with("--module-dir", path))

    /**
     * Same as `katachi scaffold --test-framework <junit5|kotest>` on the command line: the framework the generated tests use.
     *
     * @param framework [junit5] or [kotest].
     */
    public fun `test-framework`(framework: TestFramework): ScaffoldCommand =
        next(line.with("--test-framework", framework.value))

    /**
     * Same as `katachi scaffold --kotest-style <fun-spec|string-spec|should-spec|describe-spec|behavior-spec|word-spec|free-spec|feature-spec|expect-spec|annotation-spec>` on the command line: the spec style of the generated Kotest test. Without `--test-framework`, it picks kotest.
     *
     * @param style [KotestStyle.FunSpec], [KotestStyle.StringSpec], [KotestStyle.ShouldSpec], [KotestStyle.DescribeSpec], [KotestStyle.BehaviorSpec], [KotestStyle.WordSpec], [KotestStyle.FreeSpec], [KotestStyle.FeatureSpec], [KotestStyle.ExpectSpec] or [KotestStyle.AnnotationSpec].
     */
    public fun `kotest-style`(style: KotestStyle): ScaffoldCommand = next(line.with("--kotest-style", style.value))

    /**
     * Same as `katachi scaffold --deps <catalog|literal>` on the command line: how the dependencies are written.
     *
     * @param style [catalog] or [literal].
     */
    public fun deps(style: DependencyStyle): ScaffoldCommand = next(line.with("--deps", style.value))

    /**
     * Same as `katachi scaffold --force` on the command line: overwrites an existing test module.
     *
     * @param enabled False takes the option off again.
     */
    public fun force(enabled: Boolean = true): ScaffoldCommand = next(line.withFlag("--force", enabled))

    /**
     * Same as `katachi scaffold --dry-run` on the command line: prints what would be written, and writes nothing.
     *
     * @param enabled False takes the option off again.
     */
    public fun `dry-run`(enabled: Boolean = true): ScaffoldCommand = next(line.withFlag("--dry-run", enabled))

    /**
     * Appends [arguments] as they are, for an option this builder has no method for yet:
     * `arg("--new-option", "v")` is `katachi scaffold ... --new-option v` on the command line.
     *
     * @param arguments Such as `"--new-option", "value"`.
     */
    public fun arg(vararg arguments: String): ScaffoldCommand = next(line.plus(arguments.toList()))

    override val arguments: List<String> get() = line.arguments

    override fun execute(): Int {
        requireOption(line, "--package", declaredAt)
        return katachi.run(arguments)
    }

    /** The command line as typed in a shell: `katachi scaffold --package me.tbsten.katachi.sample --test-framework kotest`. Values a shell would split are quoted. */
    override fun toString(): String = commandText(arguments)
}
