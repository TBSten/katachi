package me.tbsten.katachi.test.script

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.toPath
import kotlin.io.path.writeText
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
import me.tbsten.katachi.script.KatachiScript
import me.tbsten.katachi.script.internal.KATACHI_SCRIPT_FILE_ENV
import me.tbsten.katachi.script.internal.ProcessLauncher
import me.tbsten.katachi.script.internal.ScriptContext
import me.tbsten.katachi.script.internal.runScript
import me.tbsten.katachi.script.internal.scriptClasspath
import me.tbsten.katachi.script.internal.scriptDirectoryOf

/** `*.katachi.kts`: the script definition, `katachi <file>.katachi.kts` and `katachi internal script-classpath` (KATACHI-101). */
class KatachiScriptSpec : FreeSpec({
    "スクリプト定義" - {
        "前置きなしで、トップレベルの変数が katachi.scaffold の既定値になる" {
            val value = evalKatachiScript(
                """
                defaultPackage = "com.example.app"
                katachiVersion = "0.4.0"
                katachi.scaffold.`test-framework`(kotest).arguments
                """.trimIndent(),
            )
            value shouldBe listOf(
                "scaffold", "--package", "com.example.app", "--katachi", "0.4.0", "--test-framework", "kotest",
            )
        }

        "変数を変えると、そのあとに読んだ katachi から反映される" {
            val value = evalKatachiScript(
                """
                val before = katachi.doctor.arguments
                lang = ja
                moduleDir = "static/katachi"
                listOf(before, katachi.doctor.arguments, katachi.scaffold.arguments)
                """.trimIndent(),
            )
            value shouldBe listOf(
                listOf("doctor"),
                listOf("doctor", "--lang", "ja"),
                listOf("scaffold", "--module-dir", "static/katachi"),
            )
        }

        "args にはファイル名のあとの引数が入る" {
            evalKatachiScript("args.toList()", args = arrayOf("a", "b")) shouldBe listOf("a", "b")
        }

        "スクリプトが見えるのは katachi の jar と標準ライブラリだけで、読み込んだ側のクラスパス全体ではない" {
            val classpath = createJvmCompilationConfigurationFromTemplate<KatachiScript>()[
                ScriptCompilationConfiguration.dependencies
            ].orEmpty().filterIsInstance<JvmDependency>().flatMap { it.classpath }.map { it.toPath() }
            classpath shouldContainAll scriptClasspath()
            // Kotest is on this test's classpath; in the IDE it would be the IDE's own jars.
            val kotest = FreeSpec::class.java.protectionDomain.codeSource.location.toURI().toPath()
            classpath shouldNotContain kotest
        }

        "IDE と kotlin コマンドが定義を見つけるための目印が jar に入っている" {
            val marker = "META-INF/kotlin/script/templates/${KatachiScript::class.qualifiedName}.classname"
            KatachiScript::class.java.classLoader.getResource(marker).shouldNotBeNull()
        }
    }

    "既定の cwd" - {
        "katachi が渡したスクリプトの場所のディレクトリになる" {
            scriptDirectoryOf(mapOf(KATACHI_SCRIPT_FILE_ENV to "/work/proj/scripts/setup.katachi.kts")) shouldBe
                Path("/work/proj/scripts")
        }

        "katachi を通さずに動かしたときは null（今いるディレクトリ）" {
            scriptDirectoryOf(emptyMap()).shouldBeNull()
            scriptDirectoryOf(mapOf(KATACHI_SCRIPT_FILE_ENV to " ")).shouldBeNull()
        }
    }

    "katachi internal script-classpath" - {
        "katachi-script と katachi 本体の場所を 1 行に 1 つずつ出す" {
            val run = runCapturing(createTempDirectory("katachi-script-"), listOf("internal", "script-classpath"))
            run.status shouldBe 0
            val lines = run.out.lines().filter { it.isNotBlank() }
            lines shouldBe scriptClasspath().map { it.toString() }
            lines.forEach { Path(it).exists() shouldBe true }
            lines.size shouldBe 4
        }

        "知らない internal サブコマンドは止まる" {
            val run = runCapturing(createTempDirectory("katachi-script-"), listOf("internal", "nothing"))
            run.status shouldBe 1
            run.err shouldContain "nothing"
        }

        "--help には出さない" {
            val run = runCapturing(createTempDirectory("katachi-script-"), listOf("--help"))
            run.out.contains("script-classpath") shouldBe false
        }
    }

    "katachi <file>.katachi.kts" - {
        class Recording(private val status: Int = 0, private val fails: Boolean = false) : ProcessLauncher {
            val launches = mutableListOf<Triple<List<String>, Map<String, String>, Path>>()

            override fun launch(command: List<String>, extraEnv: Map<String, String>, cwd: Path): Int {
                if (fails) throw IOException("kotlin: not found")
                launches += Triple(command, extraEnv, cwd)
                return status
            }
        }

        fun runWith(launcher: ProcessLauncher, cwd: Path, args: List<String>): RecordedRun {
            val out = StringBuilder()
            val err = StringBuilder()
            val context = ScriptContext(cwd, emptyMap(), { out.appendLine(it) }, { err.appendLine(it) }, launch = launcher)
            return RecordedRun(runScript(args, context), out.toString(), err.toString())
        }

        "kotlin -cp <script-classpath> <絶対パス> <引数> を起動し、その終了コードを返す" {
            val project = createTempDirectory("katachi-script-")
            val script = project.resolve("setup.katachi.kts").also { it.writeText("println(1)") }
            val launcher = Recording(status = 3)

            val run = runWith(launcher, project, listOf("setup.katachi.kts", "a", "--help"))

            run.status shouldBe 3
            val (command, env, cwd) = launcher.launches.single()
            command shouldBe listOf(
                "kotlin", "-no-reflect", "-cp", scriptClasspath().joinToString(java.io.File.pathSeparator), script.toString(), "a", "--help",
            )
            env shouldBe mapOf(KATACHI_SCRIPT_FILE_ENV to script.toString())
            cwd shouldBe project
        }

        "ファイルが無ければ起動せずに止まる" {
            val launcher = Recording()
            val run = runWith(launcher, createTempDirectory("katachi-script-"), listOf("missing.katachi.kts"))
            run.status shouldBe 1
            run.err shouldContain "missing.katachi.kts"
            launcher.launches.size shouldBe 0
        }

        "kotlin が起動できなければ入れ方を出して止まる" {
            val project = createTempDirectory("katachi-script-")
            project.resolve("setup.katachi.kts").writeText("println(1)")
            val run = runWith(Recording(fails = true), project, listOf("setup.katachi.kts"))
            run.status shouldBe 1
            run.err shouldContain "brew install kotlin"
        }
    }
})

private fun evalKatachiScript(source: String, args: Array<String> = emptyArray()): Any? {
    val result = BasicJvmScriptingHost().eval(
        source.toScriptSource("test.katachi.kts"),
        createJvmCompilationConfigurationFromTemplate<KatachiScript>(),
        ScriptEvaluationConfiguration { constructorArgs(args) },
    )
    val errors = result.reports.filter { it.severity >= ScriptDiagnostic.Severity.ERROR }
    errors.map { it.render() }.shouldContainNothing()
    val evaluation = result.shouldBeInstanceOf<ResultWithDiagnostics.Success<*>>().value
    val returned = evaluation.shouldBeInstanceOf<kotlin.script.experimental.api.EvaluationResult>().returnValue
    return returned.shouldBeInstanceOf<ResultValue.Value>().value
}

private fun List<String>.shouldContainNothing() {
    if (isNotEmpty()) throw AssertionError(joinToString("\n"))
}
