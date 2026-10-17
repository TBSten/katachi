package me.tbsten.katachi.test.template

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.string.shouldNotContain
import java.util.concurrent.Executors
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.processor.internal.RealArchitectureProcessContext
import me.tbsten.katachi.processor.KatachiUnknownProcessorArgException
import me.tbsten.katachi.processor.internal.checkNoUnknownArgs
import me.tbsten.katachi.processor.internal.process
import me.tbsten.katachi.template.GenerateCodeFromTemplate
import me.tbsten.katachi.template.KatachiAmbiguousTemplateException
import me.tbsten.katachi.template.KatachiDuplicateTemplateOutputException
import me.tbsten.katachi.template.KatachiExistingTemplateFileException
import me.tbsten.katachi.template.KatachiInvalidTemplateCaptureValueException
import me.tbsten.katachi.template.KatachiInvalidTemplateSetArgException
import me.tbsten.katachi.template.KatachiInvalidTemplateSetArgException.Problem
import me.tbsten.katachi.template.KatachiInvalidTemplateSpecifierException
import me.tbsten.katachi.template.KatachiTemplateSetParameterValueException
import me.tbsten.katachi.template.KatachiUnknownTemplateException
import me.tbsten.katachi.template.OnExisting
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.test.check.architectureOf
import me.tbsten.katachi.test.dsl.files.ForbiddenFileSystem

/** Generating a template set: its members together, each with only the values it was passed. */
class TemplateSetGenerationSpec : FreeSpec({
    val set = "feature.NewFeature"

    "メンバと値" - {
        "1 回の生成で全メンバのパスと中身が、書いた順に出る" {
            val arch = templateSetArchitecture {
                val name by stringParameter()
                template("feature.FeatureScreen") { arg("name", name) }
                template("feature.FeatureViewModel") { arg("name", name) }
            }

            arch.generated(set, mapOf("name" to "Home")).entries.map { it.key to it.value } shouldContainExactly listOf(
                "feature/HomeScreen.kt" to "screen Home",
                "feature/HomeViewModel.kt" to "vm Home state=true size=1",
            )
        }

        "メンバごとに違う値を渡すと別のパスになる" {
            val arch = templateSetArchitecture {
                val name by stringParameter()
                template("feature.FeatureScreen") { arg("name", name) }
                template("feature.FeatureViewModel") { arg("name", "${name}Main") }
            }

            arch.generatedPaths(set, mapOf("name" to "Home")) shouldContainExactly
                listOf("feature/HomeMainViewModel.kt", "feature/HomeScreen.kt")
        }

        "Boolean と Int と enum の arg は --arg と同じ綴りで届く" {
            val arch = templateSetArchitecture {
                template("feature.FeatureViewModel") {
                    arg("name", "Home")
                    arg("withState", false)
                    arg("size", 3)
                }
                template("feature.Branchy") {
                    arg("name", "Home")
                    arg("mode", BranchMode.Extra)
                    arg("extra", "x")
                }
            }

            arch.generated(set) shouldContainExactly mapOf(
                "feature/HomeViewModel.kt" to "vm Home state=false size=3",
                "feature/HomeBranchy.kt" to "branchy Home Extra x",
            )
        }

        "メンバのブロックで組のパラメータの値を式に組み立てて渡せる" {
            val arch = templateSetArchitecture {
                val name by stringParameter()
                val withState by booleanParameter(default = false)
                template("feature.FeatureViewModel") {
                    arg("name", name)
                    arg("withState", withState)
                }
            }

            arch.generated(set, mapOf("name" to "Home", "withState" to "true")) shouldContainExactly
                mapOf("feature/HomeViewModel.kt" to "vm Home state=true size=1")
        }

        "arg を書かなかった同名の値はメンバに届かない" {
            val arch = templateSetArchitecture {
                val name by stringParameter()
                // name を渡さない: 組に同名のパラメータがあっても FeatureScreen には届かない。
                template("feature.FeatureScreen")
                template("feature.FeatureViewModel") { arg("name", name) }
            }

            val thrown = shouldThrow<KatachiInvalidTemplateSetArgException> {
                arch.generated(set, mapOf("name" to "Home"))
            }
            thrown.problem shouldBe Problem.Missing
            thrown.member shouldBe "feature.FeatureScreen"
            thrown.names shouldContainExactly listOf("name")
        }
    }

    "メンバの値の誤り" - {
        "必須のパラメータに arg が無いと Missing で、文面が arg の書き足しと template の行を案内する" {
            val arch = templateSetArchitecture {
                template("feature.FeatureTest") { arg("name", "Home") }
            }

            val thrown = shouldThrow<KatachiInvalidTemplateSetArgException> { arch.generated(set) }

            thrown.problem shouldBe Problem.Missing
            thrown.names shouldContainExactly listOf("suffix")
            thrown.message!! shouldContain """arg("suffix", ...)"""
            thrown.message!! shouldContain """template("feature.FeatureTest") at ${thrown.declaredAt}"""
            thrown.message!! shouldNotContain "--arg suffix"
            thrown.declaredAt.fileName shouldBe "TemplateSetGenerationSpec.kt"
            thrown.templateSetDeclaredAt.fileName shouldBe "TemplateSetSpecSupport.kt"
        }

        "知らない arg の名前は UnknownName で、受け付ける名前が並ぶ" {
            val arch = templateSetArchitecture {
                template("feature.FeatureViewModel") {
                    arg("name", "Home")
                    arg("typo", "x")
                }
            }

            val thrown = shouldThrow<KatachiInvalidTemplateSetArgException> { arch.generated(set) }

            thrown.problem shouldBe Problem.UnknownName
            thrown.names shouldContainExactly listOf("typo")
            thrown.accepted shouldContainExactly listOf("name", "size", "withState")
            thrown.message!! shouldContain "Accepted names: name, size, withState."
        }

        "メンバの別の分岐のパラメータ名は arg に書ける" {
            val arch = templateSetArchitecture {
                // extra は mode == Extra の分岐でだけ宣言される。arg に書いても、その分岐に入らなければ使われないだけ。
                template("feature.Branchy") {
                    arg("name", "Home")
                    arg("extra", "x")
                }
            }

            arch.generated(set) shouldContainExactly mapOf("feature/HomeBranchy.kt" to "branchy Home Plain")
        }

        "メンバが読めない値は InvalidValue で、元の例外を cause に持つ" {
            val arch = templateSetArchitecture {
                template("feature.FeatureViewModel") {
                    arg("name", "Home")
                    arg("withState", "maybe")
                }
            }

            val thrown = shouldThrow<KatachiInvalidTemplateSetArgException> { arch.generated(set) }

            thrown.problem shouldBe Problem.InvalidValue
            thrown.names shouldContainExactly listOf("withState")
            thrown.cause!!.message!! shouldContain "withState"
        }
    }

    "組の値" - {
        "値が無い・読めないは両方まとめて KatachiTemplateSetParameterValueException" {
            val arch = templateSetArchitecture {
                val name by stringParameter()
                val withState by booleanParameter(default = true)
                template("feature.FeatureViewModel") { arg("name", name); arg("withState", withState) }
            }

            val missing = shouldThrow<KatachiTemplateSetParameterValueException> { arch.generated(set, mapOf("withState" to "maybe")) }

            missing.missing shouldContainExactly listOf("name")
            missing.invalid shouldBe mapOf("withState" to "maybe")
            missing.accepted["withState"] shouldBe "true or false"
            missing.message!! shouldContain "--arg name=<value>"
        }

        "メンバが読まない組のパラメータでも、既定値が無ければ必須" {
            val arch = templateSetArchitecture {
                val unused by stringParameter()
                template("feature.FeatureScreen") { arg("name", "Home") }
            }

            shouldThrow<KatachiTemplateSetParameterValueException> { arch.generated(set) }.missing shouldContainExactly listOf("unused")
        }
    }

    "指定の読み方" - {
        val arch = templateSetArchitecture {
            template("feature.FeatureScreen") { arg("name", "Home") }
        }

        "group を省いた組の名前でも当たる" {
            arch.generatedPaths("NewFeature") shouldContainExactly listOf("feature/HomeScreen.kt")
        }

        "組と他の指定を混ぜる・組を 2 つ並べるは TemplateSetNotAlone" {
            val twoSets = templateSetArchitecture { template("feature.FeatureScreen") { arg("name", "A") } }
            shouldThrow<KatachiInvalidTemplateSpecifierException> {
                twoSets.generated(listOf(set, "feature.FeatureScreen"))
            }.problem shouldBe KatachiInvalidTemplateSpecifierException.Problem.TemplateSetNotAlone
            shouldThrow<KatachiInvalidTemplateSpecifierException> {
                twoSets.generated(listOf(set, "NewFeature"))
            }.problem shouldBe KatachiInvalidTemplateSpecifierException.Problem.TemplateSetNotAlone
        }

        "テンプレートとしても読める名前は KatachiAmbiguousTemplateException で、候補に組とテンプレートが並ぶ" {
            val clash = architectureOf {
                "FeatureScreen".role { layout { "a" / "A.kt".file().template { "// a" } } }
                "feature".group {
                    "x".role { layout { "x" / "X.kt".file().template { "// x" } } }
                    "FeatureScreen".templateSet { template("x") }
                }
            }

            val thrown = shouldThrow<KatachiAmbiguousTemplateException> { clash.generated("FeatureScreen") }

            thrown.candidates shouldContainExactly listOf("FeatureScreen", "feature.FeatureScreen")
        }

        "どれにも当たらない指定では、宣言された組が並ぶ" {
            val thrown = shouldThrow<KatachiUnknownTemplateException> { arch.generated("Nope") }

            thrown.declaredTemplateSets shouldContainExactly listOf(set)
            thrown.message!! shouldContain "Declared template sets:"
        }
    }

    "書き込み" - {
        val arch = templateSetArchitecture {
            template("feature.FeatureScreen") { arg("name", "Home") }
            template("feature.FeatureViewModel") { arg("name", "Home") }
        }
        val args = { onExisting: OnExisting ->
            GenerateCodeFromTemplate.Args(template = listOf(set), onExisting = onExisting)
        }

        "2 つのメンバが同じパスを作ると KatachiDuplicateTemplateOutputException" {
            // 同じ Role を 2 回書くのは定義の評価で止まるので、パスが重なる別の Role を 2 つ用意する。
            val overlap = architectureOf {
                "a".group { "A".role { layout { "shared" / "File.kt".file().template(id = "a") { "// a" } } } }
                "b".group { "B".role { layout { "shared" / "File.kt".file().template(id = "b") { "// b" } } } }
                "Both".templateSet { template("a.A.a"); template("b.B.b") }
            }

            val thrown = shouldThrow<KatachiDuplicateTemplateOutputException> { overlap.generated("Both") }

            thrown.path shouldBe "shared/File.kt"
            thrown.templates shouldContainExactly listOf("a.A.a", "b.B.b")
        }

        "メンバの 1 つが既存ファイルに当たると、fail では何も書かず、skip では何も書かず成功、overwrite では全部書く" {
            withTempProject { dir ->
                dir.resolve("feature").mkdirs()
                dir.resolve("feature/HomeViewModel.kt").writeText("// already here")

                shouldThrow<KatachiExistingTemplateFileException> {
                    arch.process(GenerateCodeFromTemplate, args(OnExisting.Fail), RealFileSystem(dir)).getOrThrow()
                }
                dir.relativeFilePaths() shouldContainExactly listOf("feature/HomeViewModel.kt", "gradlew")

                arch.process(GenerateCodeFromTemplate, args(OnExisting.Skip), RealFileSystem(dir)).getOrThrow()
                dir.relativeFilePaths() shouldContainExactly listOf("feature/HomeViewModel.kt", "gradlew")
                dir.resolve("feature/HomeViewModel.kt").readText() shouldBe "// already here"

                arch.process(GenerateCodeFromTemplate, args(OnExisting.Overwrite), RealFileSystem(dir)).getOrThrow()
                dir.relativeFilePaths() shouldContainExactly listOf("feature/HomeScreen.kt", "feature/HomeViewModel.kt", "gradlew")
                dir.resolve("feature/HomeViewModel.kt").readText() shouldBe "vm Home state=true size=1"
            }
        }
    }

    "undeclaredArgNames は組のパラメータの名前だけを答える" {
        val arch = templateSetArchitecture {
            val name by stringParameter()
            val withState by booleanParameter(default = true)
            template("feature.FeatureViewModel") { arg("name", name); arg("withState", withState) }
        }
        val context = RealArchitectureProcessContext(
            architecture = arch,
            args = Unit,
            fileSystem = ForbiddenFileSystem,
            rawArgs = mapOf("template" to set, "name" to "Home"),
        )

        // メンバの capture (name) と組の name が同名でも、答えは組の宣言どおり。メンバの size は含まれない。
        val answered = GenerateCodeFromTemplate.undeclaredArgNames(context)
        answered shouldBe setOf("name", "withState")
        ("size" in answered) shouldBe false
    }

    "メンバのパラメータ (size) を --arg で直接渡すと、processor の未知の引数として拒まれる" {
        val arch = templateSetArchitecture {
            val name by stringParameter()
            template("feature.FeatureViewModel") { arg("name", name) }
        }
        val context = RealArchitectureProcessContext(
            architecture = arch,
            args = Unit,
            fileSystem = ForbiddenFileSystem,
            rawArgs = mapOf("template" to set, "name" to "Home", "size" to "3"),
        )

        val thrown = shouldThrow<KatachiUnknownProcessorArgException> {
            checkNoUnknownArgs(listOf("template" to GenerateCodeFromTemplate), context, context.rawArgs)
        }

        thrown.unknown shouldBe setOf("size")
        ("name" in thrown.known) shouldBe true
        ("size" in thrown.known) shouldBe false
    }

    "メンバの capture の値が 1 階層として不正なら、組の例外ではなく capture の例外がそのまま出る" {
        val arch = templateSetArchitecture {
            val name by stringParameter()
            template("feature.FeatureScreen") { arg("name", name) }
        }

        val thrown = shouldThrow<KatachiInvalidTemplateCaptureValueException> {
            arch.generated(set, mapOf("name" to "a/b"))
        }

        thrown::class shouldBe KatachiInvalidTemplateCaptureValueException::class
    }

    "メンバの Boolean が読めない値なら、渡した値と受け付ける綴りと直す場所が文面に出る" {
        val arch = templateSetArchitecture {
            val name by stringParameter()
            template("feature.FeatureViewModel") { arg("name", name); arg("withState", "maybe") }
        }

        val thrown = shouldThrow<KatachiInvalidTemplateSetArgException> {
            arch.generated(set, mapOf("name" to "Home"))
        }

        thrown.problem shouldBe Problem.InvalidValue
        thrown.values shouldBe mapOf("withState" to "maybe")
        thrown.message!! shouldContain "withState=\"maybe\""
        thrown.message!! shouldContain "withState takes true or false"
        thrown.message!! shouldContain "Change arg(\"withState\", ...) in template(\"feature.FeatureViewModel\") at"
    }

    "読めない値と不足が同時なら、InvalidValue の文面に不足の分の Add arg も出る" {
        val both = templateSetArchitecture {
            // 読めない値 (withState) と不足 (capture の name) を同時に起こす。
            template("feature.FeatureViewModel") { arg("withState", "maybe") }
        }

        val thrown = shouldThrow<KatachiInvalidTemplateSetArgException> { both.generated(set) }

        thrown.problem shouldBe Problem.InvalidValue
        thrown.message!! shouldContain "Add arg(\"name\", ...) to template(\"feature.FeatureViewModel\")"
    }

    "生成のログは組の指定と対象の URI を書く" {
        val arch = templateSetArchitecture {
            template("feature.FeatureScreen") { arg("name", "Home") }
            template("feature.FeatureViewModel") { arg("name", "Home") }
        }
        withTempProject { dir ->
            val logs = mutableListOf<String>()
            val context = RealArchitectureProcessContext(
                architecture = arch,
                args = GenerateCodeFromTemplate.Args(template = listOf(set)),
                fileSystem = RealFileSystem(dir),
                onLog = { logs += it },
            )

            GenerateCodeFromTemplate.process(context).getOrThrow()

            logs.first() shouldStartWith "Generating 2 files of template set feature.NewFeature under file://"
        }
    }

    "同じ Architecture で複数のスレッドが違う値で同時に生成しても、それぞれの値の結果になる" {
        val arch = templateSetArchitecture {
            val name by stringParameter()
            template("feature.FeatureScreen") { arg("name", name) }
        }
        val pool = Executors.newFixedThreadPool(8)
        try {
            val results = (1..64).map { index ->
                index to pool.submit<Map<String, String>> { arch.generated(set, mapOf("name" to "N$index")) }
            }
            results.forEach { (index, future) ->
                future.get() shouldContainExactly mapOf("feature/N${index}Screen.kt" to "screen N$index")
            }
        } finally {
            pool.shutdownNow()
        }
    }
})
