// (1), (2), (9) and (12) reach Konture's `internal` parser the same way `KontureParsing.kt`
// does, so that the assumptions are measured against the very members the backend calls.
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package me.tbsten.katachi.test.konture

import io.github.baole.konture.ClassesRuleBuilder
import io.github.baole.konture.FileDeclaration
import io.github.baole.konture.FilesRuleBuilder
import io.github.baole.konture.FunctionsRuleBuilder
import io.github.baole.konture.Konture
import io.github.baole.konture.LayeredArchitectureBuilder
import io.github.baole.konture.Module
import io.github.baole.konture.ModulesRuleBuilder
import io.github.baole.konture.ProjectGraph
import io.github.baole.konture.PropertiesRuleBuilder
import io.github.baole.konture.RuleDefinition
import io.github.baole.konture.RuleSet
import io.github.baole.konture.SlicesRuleBuilder
import io.github.baole.konture.SourceSet
import io.github.baole.konture.SourceSetId
import io.github.baole.konture.SourceSetKind
import io.github.baole.konture.SourceSetRole
import io.github.baole.konture.core.KontureLogger
import io.github.baole.konture.core.LogLevel
import io.github.baole.konture.core.model.Severity
import io.github.baole.konture.core.model.Subject
import io.github.baole.konture.core.model.ViolationReport
import io.github.baole.konture.impl.BaselineSerializer
import io.github.baole.konture.impl.KontureRuntimeStateProvider
import io.github.baole.konture.impl.PsiParser
import io.github.baole.konture.impl.cache.IncrementalAstCache
import io.github.baole.konture.impl.psi.MapSymbolLookup
import io.github.baole.konture.impl.report.ReportAccumulator
import io.github.baole.konture.scope
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipFile
import me.tbsten.katachi.konture.KontureConstraintScope
import me.tbsten.katachi.konture.KontureDefaultGraphShadows
import me.tbsten.katachi.konture.internal.audited

/**
 * What katachi is allowed to assume about Konture 1.0.1, measured rather than remembered.
 *
 * The backend parses each covered file with `PsiParser.parseFile` behind a `MapSymbolLookup`
 * of the covered set, assembles a `ProjectGraph` with public constructors, evaluates each rule
 * builder's `check()` with `failOnSeverity = null`, and reads the violations back by subject.
 * Every one of those steps rests on a fact about 1.0.1 pinned below; when Konture moves and one
 * of these goes red, the backend is what has to change, not the test.
 *
 * The numbers are the ones `.local/v0.3/konture/design.md` 5. uses.
 */
class KontureAssumptionsSpec : FreeSpec({
    "(1) PsiParser.parseFile は .kt を FileDeclaration にし、filePath は渡した File.absolutePath" {
        fixtureProject("src/PublicThing.kt" to PUBLIC_THING_KT) { root ->
            // A non-canonical spelling, so that `absolutePath` and `canonicalPath` differ.
            val file = File(root, "src/../src/PublicThing.kt")

            val parsed = PsiParser.parseFile(file, null).shouldBeInstanceOf<FileDeclaration>()

            parsed.filePath shouldBe file.absolutePath
            parsed.filePath shouldNotBe file.canonicalPath
            parsed.packageName shouldBe "com.example"
            parsed.classes.map { it.name } shouldBe listOf("PublicThing")
            parsed.classes.single().sourceLine shouldBe 3
        }
    }

    "(2) MapSymbolLookup を渡すと、別ファイルの同じパッケージのクラスが FQ 名に解決される" {
        fixtureProject(
            "src/UsesSibling.kt" to USES_SIBLING_KT,
            "src/PublicThing.kt" to PUBLIC_THING_KT,
        ) { root ->
            val files = listOf(File(root, "src/UsesSibling.kt"), File(root, "src/PublicThing.kt"))
            val lookup = MapSymbolLookup(PsiParser.getDeclaredClassFqNames(files), PsiParser.getDeclaredTypeAliases(files))

            val parsed = PsiParser.parseFile(files.first(), lookup).shouldBeInstanceOf<FileDeclaration>()

            // `PublicThing` is written bare in `UsesSibling`; only the lookup knows it is `com.example.PublicThing`.
            parsed.usages.map { it.targetFqName } shouldContain "com.example.PublicThing"
        }
    }

    "(3) public constructor で組んだ graph を FilesRuleBuilder(graph).check(graph) が読む" {
        fixtureProject(
            "src/PublicThing.kt" to PUBLIC_THING_KT,
            "src/Helper.kt" to HELPER_KT,
        ) { root ->
            val graph = graphOf(root, "src/PublicThing.kt", "src/Helper.kt")

            val report = audit { FilesRuleBuilder(graph).apply { allowEmpty(); should() nameEndsWith "Thing.kt" }.check(graph) }

            // The explicit `main / PRODUCTION` membership is what `SourceSets.production()` selects.
            report.violations.map { it.subject.name } shouldBe listOf("Helper.kt")
        }
    }

    "(4) failOnSeverity = null なら check() は投げず、全件が violations に入る。ClassSubject の line は 1 始まり" - {
        "audit では全件が report に入り、line は宣言行" {
            fixtureProject(
                "src/PublicThing.kt" to PUBLIC_THING_KT,
                "src/InternalThing.kt" to INTERNAL_THING_KT,
            ) { root ->
                val graph = graphOf(root, "src/PublicThing.kt", "src/InternalThing.kt")

                val report = audit { ClassesRuleBuilder(graph).apply { allowEmpty(); should().bePrivate() }.check(graph) }

                report.violations.size shouldBe 2
                val publicThing = report.violations.single { it.subject.name == "com.example.PublicThing" }
                val subject = publicThing.subject.shouldBeInstanceOf<Subject.ClassSubject>()
                subject.simpleName shouldBe "PublicThing"
                subject.location?.line shouldBe 3
                File(subject.location?.filePath.orEmpty()).isAbsolute shouldBe true
                publicThing.sourceLocation shouldBe subject.location
                publicThing.isSuppressed shouldBe false
            }
        }

        "同じ規則を failOnSeverity = ERROR で走らせると AssertionError" {
            fixtureProject("src/PublicThing.kt" to PUBLIC_THING_KT) { root ->
                val graph = graphOf(root, "src/PublicThing.kt")
                withFailOnSeverity(Severity.ERROR) {
                    silently {
                        shouldThrow<AssertionError> {
                            ClassesRuleBuilder(graph).apply { allowEmpty(); should().bePrivate() }.check(graph)
                        }
                    }
                }
            }
        }
    }

    "(5) that() 0 件は allowEmpty() 無しで AssertionError、有りで空の report。should() 無しは AssertionError" {
        fixtureProject("src/PublicThing.kt" to PUBLIC_THING_KT) { root ->
            val graph = graphOf(root, "src/PublicThing.kt")

            val emptySelection = audit {
                shouldThrow<AssertionError> {
                    ClassesRuleBuilder(graph).apply { that() inPackage "com.nowhere.."; should().bePublic() }.check(graph)
                }
            }
            emptySelection.message.orEmpty() shouldContain "allowEmpty()"

            val allowed = audit {
                ClassesRuleBuilder(graph).apply { allowEmpty(); that() inPackage "com.nowhere.."; should().bePublic() }.check(graph)
            }
            allowed.violations.shouldBeEmpty()

            val noAssertion = audit {
                shouldThrow<AssertionError> { FilesRuleBuilder(graph).apply { allowEmpty() }.check(graph) }
            }
            noAssertion.message.orEmpty() shouldContain "should()"
        }
    }

    "(6) files の Violation は CustomSubject(name = ファイル名) で line = 1、sourceLocation == subject.location" {
        fixtureProject("src/Helper.kt" to HELPER_KT) { root ->
            val graph = graphOf(root, "src/Helper.kt")

            val report = audit { FilesRuleBuilder(graph).apply { allowEmpty(); should() nameEndsWith "Thing.kt" }.check(graph) }

            val violation = report.violations.single()
            val subject = violation.subject.shouldBeInstanceOf<Subject.CustomSubject>()
            subject.name shouldBe "Helper.kt"
            subject.location?.line shouldBe 1
            violation.sourceLocation shouldBe subject.location
            violation.ruleId shouldBe "files.rule"
        }
    }

    "(7) functions / properties の subject の名前は pkg[.Class].name の形" {
        fixtureProject("src/PublicThing.kt" to PUBLIC_THING_KT, "src/TopLevel.kt" to TOP_LEVEL_KT) { root ->
            val graph = graphOf(root, "src/PublicThing.kt", "src/TopLevel.kt")

            val functions = audit { FunctionsRuleBuilder(graph).apply { allowEmpty(); should().bePrivate() }.check(graph) }
            functions.violations.map { it.subject.name }.sorted() shouldBe
                listOf("com.example.PublicThing.run", "com.example.topLevel")
            functions.violations.single { it.subject.name == "com.example.PublicThing.run" }
                .subject.shouldBeInstanceOf<Subject.FunctionSubject>().location?.line shouldBe 4

            val properties = audit { PropertiesRuleBuilder(graph).apply { allowEmpty(); should().bePrivate() }.check(graph) }
            properties.violations.map { it.subject.name }.sorted() shouldBe
                listOf("com.example.PublicThing.count", "com.example.topLevelValue")
            properties.violations.single { it.subject.name == "com.example.PublicThing.count" }
                .subject.shouldBeInstanceOf<Subject.CustomSubject>().location?.line shouldBe 5
        }
    }

    "(8) Konture.projectGraph は layout_v2.json 無しで IllegalArgumentException。parseFile を呼んでも既定の graph は set されない" {
        fixtureProject("src/PublicThing.kt" to PUBLIC_THING_KT) { root ->
            PsiParser.parseFile(File(root, "src/PublicThing.kt"), null)

            val missing = silently { shouldThrow<IllegalArgumentException> { Konture.projectGraph } }

            missing.message.orEmpty() shouldContain "layout_v2.json"
        }
    }

    "(9) IncrementalAstCache: 同じ内容・同じ lookup の 2 回目は parseHits が 1 増える" {
        fixtureProject("src/PublicThing.kt" to PUBLIC_THING_KT) { root ->
            val file = File(root, "src/PublicThing.kt")
            val lookup = MapSymbolLookup(PsiParser.getDeclaredClassFqNames(listOf(file)), PsiParser.getDeclaredTypeAliases(listOf(file)))
            Konture.incremental shouldBe true

            PsiParser.parseFile(file, lookup)
            val hitsBefore = IncrementalAstCache.parseHits
            PsiParser.parseFile(file, lookup)

            IncrementalAstCache.parseHits shouldBe hitsBefore + 1
        }
    }

    "(10) KontureLogger.logger と Konture.failOnSeverity は ThreadLocal で、別スレッドに漏れない" {
        val mine: (LogLevel, String, Throwable?) -> Unit = { _, _, _ -> }
        val seenLogger = AtomicReference<((LogLevel, String, Throwable?) -> Unit)?>(null)
        val seenSeverity = AtomicReference<Severity?>(Severity.WARNING)

        val setter = Thread {
            KontureLogger.logger = mine
            Konture.failOnSeverity = null
            val observer = Thread {
                seenLogger.set(KontureLogger.logger)
                seenSeverity.set(Konture.failOnSeverity)
            }
            observer.start()
            observer.join()
        }
        setter.start()
        setter.join()

        seenLogger.get() shouldNotBe mine
        seenSeverity.get() shouldBe Severity.ERROR
        // And this thread's own logger is still Konture's default, not `mine`.
        KontureLogger.logger shouldNotBe mine
    }

    "(11) 既定の graph への入口は、KontureDefaultGraphShadows の @Deprecated(ERROR) メンバと名前と引数の型で 1 対 1" {
        // Konture's entry points to the default graph, read out of every file facade class
        // (`*Kt`) of the `io.github.baole.konture` package in Konture's jar: the `Konture.*`
        // extensions that return a rule builder, a `RuleSet` or `Unit` (`assertNoCycles`, the
        // block forms that run and assert at once, `layered`, `Konture.architecture`,
        // `Konture.architectureRules`), and the receiver-less functions that take a DSL block and
        // return `Unit`, a `RuleDefinition` or a `RuleSet` (`architecture`, `rule`,
        // `architectureRules`). The `scope` family is deliberately not shadowed -- it fails loudly
        // at run time anyway (8) -- so it is excluded here by return type. A receiver shows up in
        // bytecode as the first parameter: `KontureFileScope.assertTrue(...)` and
        // `List<FileDeclarationContext>.assertFilesTrue(...)` take their receiver first and are not
        // entry points, while the receiver-less ones take a rule id or name (`String`), a
        // `ProjectGraph` or the block itself first.
        val entryReturnTypes = setOf(
            FilesRuleBuilder::class.java,
            ClassesRuleBuilder::class.java,
            FunctionsRuleBuilder::class.java,
            PropertiesRuleBuilder::class.java,
            ModulesRuleBuilder::class.java,
            SlicesRuleBuilder::class.java,
            LayeredArchitectureBuilder::class.java,
            RuleSet::class.java,
            RuleDefinition::class.java,
            Void.TYPE,
        )
        val receiverlessReturnTypes = setOf(RuleSet::class.java, RuleDefinition::class.java, Void.TYPE)
        val facades = kontureFacadeClasses()
        withClue("Konture の jar から facade が読めている") {
            facades.map { it.simpleName } shouldContain "ExtensionsKt"
        }
        val konture = facades.flatMap { facade ->
            facade.declaredMethods
                .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic && !it.name.contains('$') }
                .filter { method ->
                    val first = method.parameterTypes.firstOrNull()
                    val onKonture = first == Konture::class.java
                    val onNothing = first == Function1::class.java ||
                        first == String::class.java ||
                        first == ProjectGraph::class.java
                    val takesBlock = method.parameterTypes.any { it == Function1::class.java }
                    (onKonture && method.returnType in entryReturnTypes) ||
                        (onNothing && takesBlock && method.returnType in receiverlessReturnTypes)
                }
        }.map { it.signature() }.toSet()

        // A member extension of an interface takes its extension receiver as the first JVM
        // parameter, which is exactly what the top level extension does too -- so `(name,
        // parameter types)` is directly comparable between the two. `Nothing` compiles to
        // `java.lang.Void`, which is what tells a shadow from a real member.
        val shadowed = KontureDefaultGraphShadows::class.java.declaredMethods
            // The compiler's `access$...$jd` bridges for default bodies are not declarations.
            .filterNot { it.isSynthetic || it.name.contains('$') }
            .filter { it.returnType == Void::class.java }
            .map { it.signature() }
            .toSet()

        withClue("KontureDefaultGraphShadows が shadow していない Konture の入口") {
            konture.filterNot { it in shadowed }.shouldBeEmpty()
        }
        withClue("Konture に無い綴りを shadow している") {
            shadowed.filterNot { it in konture }.shouldBeEmpty()
        }
        konture.size shouldBe 37
        // And the scope a block runs against inherits every one of them, so they win resolution there.
        KontureConstraintScope::class.java.interfaces.toList() shouldContain KontureDefaultGraphShadows::class.java
    }

    "(12) getDeclaredClassFqNames は .kt だけを読む" {
        fixtureProject(
            "src/PublicThing.kt" to PUBLIC_THING_KT,
            "src/Task.kts" to "class FromScript\n",
        ) { root ->
            val names = PsiParser.getDeclaredClassFqNames(listOf(File(root, "src/PublicThing.kt"), File(root, "src/Task.kts")))

            names shouldContainExactly setOf("com.example.PublicThing")
        }
    }

    "(13) @Suppress(\"konture:classes.rule\") を付けたクラスの Violation は isSuppressed = true" {
        fixtureProject("src/Suppressed.kt" to SUPPRESSED_CLASS_KT) { root ->
            val graph = graphOf(root, "src/Suppressed.kt")

            val report = audit { ClassesRuleBuilder(graph).apply { allowEmpty(); should().bePrivate() }.check(graph) }

            val violation = report.violations.single()
            violation.subject.name shouldBe "com.example.Suppressed"
            violation.isSuppressed shouldBe true
        }
    }

    "(14) KDoc に書いた語彙がそのままコンパイルできる" {
        // Nothing runs these. That they compile is the assertion: a renamed or deprecated
        // Konture spelling in a documented example would be an unresolved reference, or under
        // `-Werror` a deprecation, rather than a wrong answer.
        val fromScopeKDoc: KontureConstraintScope.() -> Unit = {
            classes { should() notDependOnPackages "androidx.compose.." }
            files { should() nameEndsWith "UseCase.kt" }
            classes { that() nameEndsWith "UseCase"; should().bePublic() }
            functions { that() haveName "invoke"; should().bePublic() }
            properties { should().beVal() }
            classes { should().beInternal() }
        }
        val fromKontureKDoc: KontureConstraintScope.() -> Unit = {
            classes { should() notDependOnPackages "com.google.devtools.ksp.." }
        }

        listOf(fromScopeKDoc, fromKontureKDoc).size shouldBe 2
    }

    "(15) *RuleBuilder.check() は呼び出したスレッドで完結し、Konture.parallel を読まない" {
        // `audited { }` switches thread local state; that only works if the predicates run on the
        // thread that called `check()`. Konture reads `parallel` in `KontureContext.verifyAll` alone.
        fixtureProject("src/PublicThing.kt" to PUBLIC_THING_KT) { root ->
            val graph = graphOf(root, "src/PublicThing.kt")
            val seen = mutableSetOf<Thread>()
            val parallelBefore = Konture.parallel
            Konture.parallel = true
            try {
                audit {
                    ClassesRuleBuilder(graph).apply {
                        allowEmpty()
                        should() satisfy { seen += Thread.currentThread(); true }
                    }.check(graph)
                }
            } finally {
                Konture.parallel = parallelBefore
            }
            seen shouldBe setOf(Thread.currentThread())
        }
    }

    "(16) Konture の baseline で抑えた違反も ViolationReport.violations には残る" {
        // katachi promises that only its own baseline applies. That holds because Konture builds
        // the report it returns before matching the violations against its baseline; the baseline
        // decides what is thrown and logged, not what is reported. Pinned by writing a baseline that
        // matches the violation -- in Konture's own format, from Konture's own normalised record of
        // it -- and reading the same rule back under it.
        fixtureProject("src/PublicThing.kt" to PUBLIC_THING_KT) { root ->
            val graph = graphOf(root, "src/PublicThing.kt")
            val rule = { ClassesRuleBuilder(graph).apply { allowEmpty(); should().bePrivate() }.check(graph) }
            audit(rule).violations.size shouldBe 1

            val baseline = File(root, "konture-baseline.json")
            val evaluated = KontureRuntimeStateProvider.currentState.baselineManager.evaluatedViolations.toList()
            evaluated.any { it.message.contains("com.example.PublicThing") } shouldBe true
            silently { BaselineSerializer.writeViolationsToFile(baseline, evaluated) }
            System.setProperty(Konture.PROPERTY_BASELINE_PATH, baseline.absolutePath)
            try {
                // The baseline is in effect: with failOnSeverity = ERROR the same rule no longer throws.
                withFailOnSeverity(Severity.ERROR) { silently(rule) }
                // And in audit mode the report still carries the violation the baseline suppressed.
                audit(rule).violations.map { it.subject.name } shouldBe listOf("com.example.PublicThing")
            } finally {
                System.clearProperty(Konture.PROPERTY_BASELINE_PATH)
            }
        }
    }

    "(17) Konture は既定で check() ごとに HTML レポートを書き、katachi の audited { } はそれを止める。cache と baseline は既定で書かれない" {
        // Konture's default `reportPath` ends in `.html`, and that alone enables its HTML writer
        // on every `check()` -- audit mode (failOnSeverity = null) does not stop it. `audited { }`
        // therefore also points `reportPath` away from a report suffix; this is what proves that
        // both halves are true for 1.0.1. The report lands under `user.dir`, the module directory
        // under Gradle, so it is deleted first and looked for afterwards.
        val userDir = File(System.getProperty("user.dir"))
        val reportDir = File(userDir, "build/reports/konture")
        fixtureProject("src/PublicThing.kt" to PUBLIC_THING_KT) { root ->
            val graph = graphOf(root, "src/PublicThing.kt")
            val rule = { ClassesRuleBuilder(graph).apply { allowEmpty(); should().bePrivate() }.check(graph) }

            reportDir.deleteRecursively()
            audit(rule)
            File(reportDir, "konture-report.html").isFile shouldBe true

            reportDir.deleteRecursively()
            audited(rule)
            reportDir.exists() shouldBe false

            kontureRun("src/PublicThing.kt" to PUBLIC_THING_KT) { classes { should().beInternal() } }
            reportDir.exists() shouldBe false
        }

        Konture.cacheEnabled shouldBe false
        File(userDir, ".konture").exists() shouldBe false
        File(userDir, "konture-baseline.json").exists() shouldBe false
    }

    "(18) parse cache のキーは canonical path で、別の綴りで 2 回目に頼むと 1 回目の綴りの filePath が返る" {
        fixtureProject("src/Spelled.kt" to PUBLIC_THING_KT) { root ->
            val first = PsiParser.parseFile(File(root, "src/../src/Spelled.kt"), null).shouldBeInstanceOf<FileDeclaration>()
            val second = PsiParser.parseFile(File(root, "src/Spelled.kt"), null).shouldBeInstanceOf<FileDeclaration>()

            first.filePath shouldBe File(root, "src/../src/Spelled.kt").absolutePath
            second.filePath shouldBe first.filePath
            second.filePath shouldNotBe File(root, "src/Spelled.kt").absolutePath
        }
    }

    "(19) should() 無しの AssertionError は *RuleBuilder.check が投げ、satisfy { } の中の AssertionError はそうでない" {
        // What `KontureFileConstraint` reads off the first stack frame to tell a refused rule from
        // a predicate that asserted.
        fixtureProject("src/PublicThing.kt" to PUBLIC_THING_KT) { root ->
            val graph = graphOf(root, "src/PublicThing.kt")

            val refused = audit {
                shouldThrow<AssertionError> { ClassesRuleBuilder(graph).apply { allowEmpty() }.check(graph) }
            }
            refused.stackTrace.first().className shouldBe "io.github.baole.konture.ClassesRuleBuilder"
            refused.stackTrace.first().methodName shouldBe "check"

            val fromPredicate = audit {
                shouldThrow<AssertionError> {
                    ClassesRuleBuilder(graph).apply {
                        allowEmpty()
                        should() satisfy { throw AssertionError("mine") }
                    }.check(graph)
                }
            }
            fromPredicate.stackTrace.first().className shouldNotStartWith "io.github.baole.konture."
        }
    }

    "(20) 既定の graph に触った例外のスタックには Konture.getProjectGraph があり、利用者の require には無い" {
        // What `KontureFileConstraint` reads off the stack to tell Konture reaching for its default
        // graph from an IllegalArgumentException of the reader's own.
        val fromDefaultGraph = silently { shouldThrow<IllegalArgumentException> { Konture.scope } }
        fromDefaultGraph.stackTrace.any {
            it.className == "io.github.baole.konture.Konture" && it.methodName == "getProjectGraph"
        } shouldBe true

        val mine = shouldThrow<IllegalArgumentException> { require(false) { "mine" } }
        mine.stackTrace.none { it.className == "io.github.baole.konture.Konture" } shouldBe true
    }

    "(21) check() のたびに ReportAccumulator と evaluatedViolations が増え、parse cache は増えない" {
        // Konture's JVM wide bookkeeping, which `audited { }` leaves alone: one RuleEvaluation per
        // check(), one entry per distinct normalised violation, and nothing parsed again for a
        // graph already built. The design (2.5) accepts the growth; this is its measure. The
        // violation is keyed by its message and the test location Konture finds on the stack,
        // which here is this file's `audit` helper for every spec, so the class name has to be one
        // no other spec rejects for the entry to be new.
        fixtureProject("src/OnlyHere.kt" to ONLY_HERE_KT) { root ->
            val graph = graphOf(root, "src/OnlyHere.kt")
            val evaluationsBefore = ReportAccumulator.getAllEvaluations().size
            val evaluatedBefore = KontureRuntimeStateProvider.currentState.baselineManager.evaluatedViolations.size
            val parsedBefore = IncrementalAstCache.size

            repeat(3) {
                audit { ClassesRuleBuilder(graph).apply { allowEmpty(); should().bePrivate() }.check(graph) }
            }

            ReportAccumulator.getAllEvaluations().size shouldBe evaluationsBefore + 3
            KontureRuntimeStateProvider.currentState.baselineManager.evaluatedViolations.size shouldBe evaluatedBefore + 1
            IncrementalAstCache.size shouldBe parsedBefore
        }
    }
})

/** A public class whose name no other spec of this module rejects, so that (21) records a new violation. */
private val ONLY_HERE_KT: String = """
    package com.example

    class OnlyInAssumptionTwentyOne
""".trimIndent() + "\n"

/** Where Konture's jar is, found from any class in it. */
private val KONTURE_PACKAGE_DIRECTORY: String = "io/github/baole/konture/"

/**
 * Every file facade class (`*Kt`) directly in the `io.github.baole.konture` package, read from
 * Konture's jar so that an entry point added in a new file is noticed.
 */
private fun kontureFacadeClasses(): List<Class<*>> {
    val jar = File(Konture::class.java.protectionDomain.codeSource.location.toURI())
    return ZipFile(jar).use { zip ->
        zip.entries().asSequence()
            .map { it.name }
            .filter {
                it.startsWith(KONTURE_PACKAGE_DIRECTORY) &&
                    it.endsWith("Kt.class") &&
                    '/' !in it.removePrefix(KONTURE_PACKAGE_DIRECTORY)
            }
            .map { Class.forName(it.removeSuffix(".class").replace('/', '.')) }
            .toList()
    }
}

/** `name(ParamType, ParamType)`, the shape both sides of (11) are compared in. */
private fun java.lang.reflect.Method.signature(): String =
    "$name(${parameterTypes.joinToString(", ") { it.simpleName }})"

/** [block] with `Konture.failOnSeverity` set to [severity] on this thread, and put back afterwards. */
private fun <T> withFailOnSeverity(severity: Severity?, block: () -> T): T {
    val before = Konture.failOnSeverity
    Konture.failOnSeverity = severity
    return try {
        block()
    } finally {
        Konture.failOnSeverity = before
    }
}

/** The one source set the backend stamps every file with, spelled as `KontureGraphs.kt` spells it. */
private val MAIN: SourceSetId = SourceSetId(":", "main", SourceSetKind.JVM, SourceSetRole.PRODUCTION)

/** A graph of exactly [relativePaths] under [root], built the way the backend builds it. */
private fun graphOf(root: File, vararg relativePaths: String): ProjectGraph {
    val files = relativePaths.map { File(root, it) }
    val lookup = MapSymbolLookup(PsiParser.getDeclaredClassFqNames(files), PsiParser.getDeclaredTypeAliases(files))
    val parsed = files.map { file ->
        val declaration = PsiParser.parseFile(file, lookup).shouldBeInstanceOf<FileDeclaration>()
        declaration.copy(sourceSets = listOf(MAIN), usages = declaration.usages.map { it.copy(sourceSets = listOf(MAIN)) })
    }
    val module = Module(
        buildId = ":",
        path = ":",
        projectDir = root.absolutePath,
        appliedPlugins = emptyList(),
        sourceSets = listOf(SourceSet(name = "main", kind = "KOTLIN_JVM", production = true, srcDirs = emptyList())),
        dependencies = emptyList(),
        files = parsed,
    )
    return ProjectGraph(mapOf(":" to listOf(module)))
}

/** Konture's audit mode for one block, by hand: what `audited { }` does, spelled out so (3)-(7) measure Konture alone. */
private fun <T> audit(block: () -> T): T {
    val severity = Konture.failOnSeverity
    Konture.failOnSeverity = null
    return try {
        silently(block)
    } finally {
        Konture.failOnSeverity = severity
    }
}

/** [block] with Konture's logger swallowed, so a measured violation does not print to stdout. */
private fun <T> silently(block: () -> T): T {
    val logger = KontureLogger.logger
    KontureLogger.logger = { _, _, _ -> }
    return try {
        block()
    } finally {
        KontureLogger.logger = logger
    }
}

/** Refers to `PublicThing` by its simple name, without an import: resolvable only through a symbol lookup. */
private val USES_SIBLING_KT: String = """
    package com.example

    class UsesSibling {
        fun thing(): PublicThing = PublicThing()
    }
""".trimIndent() + "\n"

/** A top level function on line 3 and a top level property on line 5. */
private val TOP_LEVEL_KT: String = """
    package com.example

    fun topLevel() = Unit

    val topLevelValue: Int = 0
""".trimIndent() + "\n"

private val SUPPRESSED_CLASS_KT: String = """
    package com.example

    @Suppress("konture:classes.rule")
    class Suppressed
""".trimIndent() + "\n"
