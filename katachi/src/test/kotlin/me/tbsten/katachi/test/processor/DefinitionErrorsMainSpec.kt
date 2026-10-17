package me.tbsten.katachi.test.processor

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import java.io.File
import kotlin.random.Random
import kotlin.reflect.KClass
import me.tbsten.katachi.dsl.KatachiDuplicateDeclarationException
import me.tbsten.katachi.dsl.KatachiInvalidTemplateSetException
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.processor.KatachiEntryPointNotFoundException
import me.tbsten.katachi.processor.internal.runMain
import me.tbsten.katachi.test.ide.rootlessProject
import me.tbsten.katachi.test.template.withTempProject

/**
 * `main()` with `--definition-errors`: the file the IDE plugin reads to put a definition's mistake
 * on the line that wrote it (K-2 to K-7 and K-9 of the IDE definition authoring plan).
 */
class DefinitionErrorsMainSpec : FreeSpec({
    fun Throwable.rootCause(): Throwable = generateSequence(this) { it.cause }.last()

    fun lineOf(json: String, key: String): String = json.lineSequence().first { it.trim().startsWith("\"$key\":") }.trim()

    "初期化の誤り" - {
        "architecture { } が投げると phase = entryPoint で、投げた例外自身が stderr の先頭に出る" {
            withTempProject { root ->
                val run = runDefinitionMain(DuplicateGroupEntryPoint::class, validProject(), root)

                run.status shouldBe 1
                run.thrown.shouldBeInstanceOf<KatachiDuplicateDeclarationException>()
                run.stderr shouldStartWith "Exception in thread \"main\" me.tbsten.katachi.dsl.KatachiDuplicateDeclarationException"
                run.stderr shouldNotContain "KatachiEntryPointNotFoundException"
                val json = run.errors.readText()
                lineOf(json, "phase") shouldBe "\"phase\": \"entryPoint\","
                lineOf(json, "processor") shouldBe "\"processor\": null,"
                lineOf(json, "exception") shouldBe "\"exception\": \"me.tbsten.katachi.dsl.KatachiDuplicateDeclarationException\","
                json shouldContain "\"declaredAt\": {\n        \"fileName\": \"DefinitionErrorsSupport.kt\",\n        \"line\": ${WrittenAt.duplicateGroup}\n      }"
                run.definition.exists() shouldBe false
            }
        }

        "templateSet がメンバの外でパラメータを読んでも初期化の誤りで、パラメータの宣言の行を指す" {
            withTempProject { root ->
                val run = runDefinitionMain(ReadOutsideMemberEntryPoint::class, validProject(), root)

                run.status shouldBe 1
                run.thrown.shouldBeInstanceOf<KatachiInvalidTemplateSetException>()
                val json = run.errors.readText()
                lineOf(json, "exception") shouldBe "\"exception\": \"me.tbsten.katachi.dsl.KatachiInvalidTemplateSetException\","
                json shouldContain "\"line\": ${WrittenAt.readOutsideMember}\n"
            }
        }

        "定義の誤りでない初期化の失敗は、今までどおり KatachiEntryPointNotFoundException で包み、errors は空" {
            withTempProject { root ->
                val run = runDefinitionMain(BoomEntryPoint::class, validProject(), root)

                run.status shouldBe 1
                val thrown = run.thrown.shouldBeInstanceOf<KatachiEntryPointNotFoundException>()
                thrown.rootCause().message shouldBe "boom"
                run.errors.readText() shouldContain "\"errors\": []"
            }
        }

        "存在しない class 名も KatachiEntryPointNotFoundException で、errors は空" {
            withTempProject { root ->
                val missing = File(root, "definition.json")
                val stderr = StringBuilder()
                val errors = File(root, "definition-errors.json")
                val thrown = shouldThrow<KatachiEntryPointNotFoundException> {
                    runMain(
                        arrayOf(
                            "--entry-point=me.tbsten.katachi.test.processor.NoSuchEntryPoint",
                            "--processor=internalDefinitionJson",
                            "--arg=output=${missing.path}",
                            "--definition-errors=${errors.path}",
                        ),
                        err = { stderr.appendLine(it) },
                        fileSystem = validProject(),
                        out = {},
                    )
                }

                thrown.message shouldContain "NoSuchEntryPoint"
                errors.readText() shouldContain "\"errors\": []"
            }
        }

        "--definition-errors が無ければ誤りのファイルは書かず、終了コードと stderr は同じ" {
            withTempProject { root ->
                val run = runDefinitionMain(DuplicateGroupEntryPoint::class, validProject(), root, withErrorsFile = false)

                run.status shouldBe 1
                run.errors.exists() shouldBe false
                run.thrown.shouldBeInstanceOf<KatachiDuplicateDeclarationException>()
                run.stderr shouldStartWith "Exception in thread \"main\" me.tbsten.katachi.dsl.KatachiDuplicateDeclarationException"
            }
        }
    }

    "平坦化の誤り" - {
        "名前の無い * の .template は processor の誤りで、definition.json は書き換えない" {
            withTempProject { root ->
                val previous = File(root, "definition.json").apply { writeText("previous\n") }
                previous.setLastModified(1_000_000_000_000L)

                val run = runDefinitionMain(WildcardTemplateEntryPoint::class, validProject(), root)

                run.status shouldBe 1
                previous.readText() shouldBe "previous\n"
                previous.lastModified() shouldBe 1_000_000_000_000L
                val json = run.errors.readText()
                lineOf(json, "phase") shouldBe "\"phase\": \"processor\","
                lineOf(json, "processor") shouldBe "\"processor\": \"internalDefinitionJson\","
                lineOf(json, "exception") shouldBe "\"exception\": \"me.tbsten.katachi.dsl.KatachiTemplateOnWildcardException\","
                json shouldContain "\"line\": ${WrittenAt.wildcardTemplate}\n"
            }
        }

        "templateSet が当たらないテンプレートを名指しすると processor の誤りで、その行を指す" {
            withTempProject { root ->
                val run = runDefinitionMain(UnknownMemberEntryPoint::class, validProject(), root)

                run.status shouldBe 1
                val json = run.errors.readText()
                lineOf(json, "exception") shouldBe "\"exception\": \"me.tbsten.katachi.dsl.KatachiInvalidTemplateSetMemberException\","
                json shouldContain "\"line\": ${WrittenAt.unknownMember}\n"
                run.definition.exists() shouldBe false
            }
        }

        "--definition-errors が無ければ誤りのファイルを書かない" {
            withTempProject { root ->
                val run = runDefinitionMain(WildcardTemplateEntryPoint::class, validProject(), root, withErrorsFile = false)

                run.status shouldBe 1
                run.errors.exists() shouldBe false
            }
        }
    }

    "誤りが無いとき" - {
        "errors は空で、definition.json が書かれる" {
            withTempProject { root ->
                val run = runDefinitionMain(ValidEntryPoint::class, validProject(), root)

                run.status shouldBe 0
                run.errors.readText() shouldContain "\"errors\": []"
                run.errors.readText() shouldStartWith "{\n  \"schemaVersion\": 1,\n"
                run.definition.readText() shouldStartWith "{\n  \"schemaVersion\": 1,\n"
            }
        }

        "環境の誤り(プロジェクトルートが無い)は WalkFailed のまま書かれ、errors は空" {
            withTempProject { root ->
                val run = runDefinitionMain(ValidEntryPoint::class, rootlessProject(), root)

                run.status shouldBe 0
                run.errors.readText() shouldContain "\"errors\": []"
                run.definition.readText() shouldContain "\"kind\": \"WalkFailed\""
            }
        }
    }

    "書き込みの失敗" - {
        "誤りのファイルを書けなくても本来の誤りは隠さず、警告を 1 行出す" {
            withTempProject { root ->
                File(root, "definition-errors.json").mkdirs()

                val run = runDefinitionMain(DuplicateGroupEntryPoint::class, validProject(), root)

                run.status shouldBe 1
                run.thrown.shouldBeInstanceOf<KatachiDuplicateDeclarationException>()
                run.stderr shouldContain "Exception in thread \"main\" me.tbsten.katachi.dsl.KatachiDuplicateDeclarationException"
                run.stderr.lines().count { it.startsWith("[WARN] Could not write ") } shouldBe 1
            }
        }
    }

    "操作の列" - {
        val variants: List<Variant> = listOf(
            Variant("正しい", ValidEntryPoint::class, ::validProject, errors = emptyList(), writesDefinition = true, walkFailed = false),
            Variant("初期化の誤り", DuplicateGroupEntryPoint::class, ::validProject, errors = listOf("entryPoint"), writesDefinition = false),
            Variant("平坦化の誤り", WildcardTemplateEntryPoint::class, ::validProject, errors = listOf("processor"), writesDefinition = false),
            Variant("templateSet の初期化の誤り", ReadOutsideMemberEntryPoint::class, ::validProject, errors = listOf("entryPoint"), writesDefinition = false),
            Variant("templateSet の平坦化の誤り", UnknownMemberEntryPoint::class, ::validProject, errors = listOf("processor"), writesDefinition = false),
            Variant("環境の誤り", ValidEntryPoint::class, ::rootlessProject, errors = emptyList(), writesDefinition = true, walkFailed = true),
        )

        "変種を 1〜8 個並べて順に走らせても、definition.json は最後に書いた実行のもの、誤りは最後の実行のもの" {
            val random = Random(20261017)
            repeat(40) { round ->
                val sequence = List(random.nextInt(1, 9)) { variants.random(random) }
                withTempProject { root ->
                    var lastDefinition: String? = null
                    var lastWalkFailed = false
                    sequence.forEachIndexed { step, variant ->
                        // An old time stamp, so that a run that writes is told from one that leaves the file alone.
                        File(root, "definition.json").takeIf { it.exists() }?.setLastModified(OLD_TIME)
                        val run = runDefinitionMain(variant.entryPoint, variant.project(), root)
                        val label = "round $round step $step ${variant.name} of ${sequence.map { it.name }}"

                        run.errors.readText().let { json ->
                            Regex("\"phase\": \"(\\w+)\"").findAll(json).map { it.groupValues[1] }.toList()
                                .also { phases -> (label to phases) shouldBe (label to variant.errors) }
                        }
                        if (variant.writesDefinition) {
                            lastDefinition = run.definition.readText()
                            lastWalkFailed = variant.walkFailed
                            (label to (run.definition.lastModified() > OLD_TIME)) shouldBe (label to true)
                        } else if (run.definition.exists()) {
                            (label to run.definition.lastModified()) shouldBe (label to OLD_TIME)
                        }
                        (label to run.definition.exists()) shouldBe (label to (lastDefinition != null))
                        if (lastDefinition != null) {
                            val now = run.definition.readText()
                            (label to now) shouldBe (label to lastDefinition)
                            (label to now.contains("\"kind\": \"WalkFailed\"")) shouldBe (label to lastWalkFailed)
                        }
                        (run.status == 0) shouldBe variant.errors.isEmpty()
                    }
                }
            }
        }
    }
})

private class Variant(
    val name: String,
    val entryPoint: KClass<*>,
    val project: () -> KatachiFileSystem,
    val errors: List<String>,
    val writesDefinition: Boolean,
    /** Whether the `definition.json` it writes is a `WalkFailed` one (the environment is wrong). */
    val walkFailed: Boolean = false,
)

private const val OLD_TIME: Long = 1_000_000_000_000L
