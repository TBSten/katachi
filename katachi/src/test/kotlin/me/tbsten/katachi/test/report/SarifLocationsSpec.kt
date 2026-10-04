package me.tbsten.katachi.test.report

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.check.MissingFile
import me.tbsten.katachi.check.StaleBaselineEntry
import me.tbsten.katachi.check.UncheckedCheck
import me.tbsten.katachi.check.UnexpectedFile
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.report.SarifContext
import me.tbsten.katachi.report.toSarif

/** The `locations[0].physicalLocation` block of the single result in [sarif], as text. */
private fun primaryLocationOf(sarif: String): String =
    sarif.substringAfter("\"locations\": [").substringBefore("          ]")

/** The `relatedLocations` block of the single result in [sarif], or `""` when there is none. */
private fun relatedLocationsOf(sarif: String): String =
    if ("\"relatedLocations\"" in sarif) sarif.substringAfter("\"relatedLocations\": [").substringBefore("          ]") else ""

private fun notificationsOf(sarif: String): String =
    if ("toolExecutionNotifications" in sarif) sarif.substringAfter("\"toolExecutionNotifications\": [").substringBefore("          ]") else ""

private fun Violation.sarifWith(context: SarifContext): String = listOf(this).toSarif(context)

/**
 * The four steps a result's location goes through, and the notification each fall says:
 * the file itself, the line that declared it, the `architecture { }` call, a bare name.
 */
class SarifLocationsSpec : FreeSpec({
    val tree = sarifRepository()
    val roles = SarifRoles()
    val useCaseSite = DeclarationSite("UseCaseRole.kt", 16)
    val missing = MissingFile("core/domain/src/main/kotlin/com/example/domain/GetOrderUseCase.kt", roles.useCase, useCaseSite)
    val uncheckedCheck = UncheckedCheck("com.example.TodoCheck", IllegalStateException("broken"))

    "まず path が実在するファイル" - {
        "リポジトリ相対の uri に %SRCROOT% が付き、region は無い" {
            val sarif = UnexpectedFile("app/src/main/kotlin/com/example/Helper.kt", emptyList()).sarifWith(tree.context())
            primaryLocationOf(sarif) shouldContain "\"uri\": \"android/app/src/main/kotlin/com/example/Helper.kt\""
            primaryLocationOf(sarif) shouldContain "\"uriBaseId\": \"%SRCROOT%\""
            primaryLocationOf(sarif) shouldNotContain "region"
            sarif shouldNotContain "toolExecutionNotifications"
        }

        "UnsatisfiedFileConstraint.line が region.startLine になり、宣言元が relatedLocations に付く" {
            val violation = UnsatisfiedFileConstraint(
                path = "core/domain/src/main/kotlin/com/example/domain/GetUserUseCase.kt",
                declaration = "GetUserUseCase",
                line = 7,
                role = roles.useCase,
                constraintName = null,
                layoutPath = null,
                declaredAt = useCaseSite,
            )
            val sarif = violation.sarifWith(tree.context())
            primaryLocationOf(sarif) shouldContain "\"startLine\": 7"
            relatedLocationsOf(sarif) shouldContain "\"uri\": \"android/architecture-test/src/test/kotlin/com/example/roles/UseCaseRole.kt\""
            relatedLocationsOf(sarif) shouldContain "\"startLine\": 16"
            relatedLocationsOf(sarif) shouldContain "\"text\": \"Declared here\""
        }

        "規則の行（ruleDeclaredAt）があれば、relatedLocations と properties の宣言元はテキストの報告と同じその行" {
            val violation = UnsatisfiedFileConstraint(
                path = "core/domain/src/main/kotlin/com/example/domain/GetUserUseCase.kt",
                declaration = "GetUserUseCaseImpl",
                line = 9,
                role = roles.useCase,
                constraintName = "ktFileContent",
                layoutPath = null,
                declaredAt = useCaseSite,
                ruleDeclaredAt = DeclarationSite("UseCaseRole.kt", 18),
            )
            val sarif = violation.sarifWith(tree.context())
            relatedLocationsOf(sarif) shouldContain "\"startLine\": 18"
            relatedLocationsOf(sarif) shouldNotContain "\"startLine\": 16"
            sarif shouldContain "\"declaredAt\": \"UseCaseRole.kt:18\""
            sarif shouldContain "Declared at: UseCaseRole.kt:18"
        }

        "空白と # と ? と % を含むパスは percent-encoding され、非 ASCII はそのまま" {
            val odd = FakeSarifTree((SARIF_PROJECT_ROOT / "My App/src/main/kotlin/com/example/Foo#1 100%.kt").value to "")
            val sarif = UnexpectedFile("My App/src/main/kotlin/com/example/Foo#1 100%.kt", emptyList()).sarifWith(odd.context())
            primaryLocationOf(sarif) shouldContain "\"uri\": \"android/My%20App/src/main/kotlin/com/example/Foo%231%20100%25.kt\""
            val japanese = FakeSarifTree((SARIF_PROJECT_ROOT / "app/src/main/kotlin/com/example/注文.kt").value to "")
            val raw = UnexpectedFile("app/src/main/kotlin/com/example/注文.kt", emptyList()).sarifWith(japanese.context())
            primaryLocationOf(raw) shouldContain "\"uri\": \"android/app/src/main/kotlin/com/example/注文.kt\""
        }

        "UnsatisfiedFileConstraint.line が 0 以下なら region を省く" {
            val violation = UnsatisfiedFileConstraint(
                path = "core/domain/src/main/kotlin/com/example/domain/GetUserUseCase.kt",
                declaration = "GetUserUseCase",
                line = 0,
                role = roles.useCase,
                constraintName = null,
                layoutPath = null,
                declaredAt = useCaseSite,
            )
            primaryLocationOf(violation.sarifWith(tree.context())) shouldNotContain "region"
        }

        "git の接頭辞が無ければプロジェクトルート相対のまま、%SRCROOT% はプロジェクトルート" {
            val sarif = UnexpectedFile("app/src/main/kotlin/com/example/Helper.kt", emptyList()).sarifWith(tree.context(repositoryPrefix = ""))
            primaryLocationOf(sarif) shouldContain "\"uri\": \"app/src/main/kotlin/com/example/Helper.kt\""
            sarif shouldContain "\"uri\": \"file:///w/repo/android/\""
        }

        "git の接頭辞がプロジェクトルートの末尾と合わなければ無かったことにする" {
            val sarif = UnexpectedFile("app/src/main/kotlin/com/example/Helper.kt", emptyList()).sarifWith(tree.context(repositoryPrefix = "ios/"))
            primaryLocationOf(sarif) shouldContain "\"uri\": \"app/src/main/kotlin/com/example/Helper.kt\""
            sarif shouldContain "\"uri\": \"file:///w/repo/android/\""
        }

        "Windows 風のプロジェクトルートでも uri は / 区切りで、%SRCROOT% は file:///C:/ になる" {
            val windows = FakeSarifTree("C:/w/repo/android/app/src/main/kotlin/com/example/Helper.kt" to "")
            val context = windows.context(projectRoot = FsPath.of("""C:\w\repo\android"""), sourceRoots = emptyList(), architectureDeclaredAt = null)
            val sarif = UnexpectedFile("app/src/main/kotlin/com/example/Helper.kt", emptyList()).sarifWith(context)
            primaryLocationOf(sarif) shouldContain "\"uri\": \"android/app/src/main/kotlin/com/example/Helper.kt\""
            sarif shouldContain "\"uri\": \"file:///C:/w/repo/\""
        }
    }

    "次に declaredAt を source roots から解決" - {
        "MissingFile は宣言した行に置かれ、期待したパスが relatedLocations に入る" {
            val sarif = missing.sarifWith(tree.context())
            primaryLocationOf(sarif) shouldContain "\"uri\": \"android/architecture-test/src/test/kotlin/com/example/roles/UseCaseRole.kt\""
            primaryLocationOf(sarif) shouldContain "\"startLine\": 16"
            relatedLocationsOf(sarif) shouldContain "\"uri\": \"android/core/domain/src/main/kotlin/com/example/domain/GetOrderUseCase.kt\""
            relatedLocationsOf(sarif) shouldContain "\"text\": \"The declared file, which does not exist\""
            relatedLocationsOf(sarif) shouldNotContain "region"
            sarif shouldNotContain "toolExecutionNotifications"
        }

        "解決したファイルがプロジェクトルートの外なら絶対 URI で uriBaseId を付けない" {
            val outside = FakeSarifTree("/elsewhere/dsl/UseCaseRole.kt" to "")
            val sarif = missing.sarifWith(outside.context(sourceRoots = listOf(FsPath.of("/elsewhere"))))
            primaryLocationOf(sarif) shouldContain "\"uri\": \"file:///elsewhere/dsl/UseCaseRole.kt\""
            primaryLocationOf(sarif) shouldNotContain "uriBaseId"
        }

        "同名のファイルが 2 つあると解決せず、architecture { } に落ちて理由を書く" {
            val twice = FakeSarifTree(
                (SARIF_SOURCE_ROOT / "a/UseCaseRole.kt").value to "",
                (SARIF_SOURCE_ROOT / "b/UseCaseRole.kt").value to "",
                (SARIF_SOURCE_ROOT / "ProjectArchitecture.kt").value to "",
            )
            val sarif = missing.sarifWith(twice.context())
            primaryLocationOf(sarif) shouldContain "\"uri\": \"android/architecture-test/src/test/kotlin/ProjectArchitecture.kt\""
            primaryLocationOf(sarif) shouldContain "\"startLine\": 12"
            notificationsOf(sarif) shouldContain "\"level\": \"warning\""
            notificationsOf(sarif) shouldContain
                "Declaration site UseCaseRole.kt:16 matches 2 files (architecture-test/src/test/kotlin/a/UseCaseRole.kt, " +
                "architecture-test/src/test/kotlin/b/UseCaseRole.kt), so it was not resolved. The violation was placed at the architecture definition instead."
        }

        "見つからなければ architecture { } に落ちて、探した source roots を書く" {
            val elsewhere = FakeSarifTree((SARIF_SOURCE_ROOT / "ProjectArchitecture.kt").value to "")
            val sarif = missing.sarifWith(elsewhere.context())
            primaryLocationOf(sarif) shouldContain "ProjectArchitecture.kt\""
            notificationsOf(sarif) shouldContain
                "Declaration site UseCaseRole.kt:16 was not found under any source root (architecture-test/src/test/kotlin). " +
                "The violation was placed at the architecture definition instead."
        }

        "<unknown> の宣言元は探さずに落ち、言うことが無いので notification も出ない" {
            val sarif = MissingFile("x/Y.kt", roles.useCase, DeclarationSite.Unknown).sarifWith(tree.context())
            primaryLocationOf(sarif) shouldContain "ProjectArchitecture.kt\""
            sarif shouldNotContain "toolExecutionNotifications"
        }
    }

    "それも無ければ architecture { } の受け皿" - {
        "UncheckedCheck の path は . なので architecture { } の行に置かれる" {
            val sarif = uncheckedCheck.sarifWith(tree.context())
            primaryLocationOf(sarif) shouldContain "\"uri\": \"android/architecture-test/src/test/kotlin/com/example/ProjectArchitecture.kt\""
            primaryLocationOf(sarif) shouldContain "\"startLine\": 12"
            sarif shouldNotContain "toolExecutionNotifications"
        }
    }

    "最後にファイル名だけ" - {
        "source roots が無いと宣言元のファイル名だけになり、理由を 1 件書く" {
            val sarif = missing.sarifWith(tree.context(sourceRoots = emptyList()))
            primaryLocationOf(sarif) shouldContain "\"uri\": \"UseCaseRole.kt\""
            primaryLocationOf(sarif) shouldNotContain "region"
            notificationsOf(sarif) shouldContain
                "No source root was given with -Dkatachi.report.sourceRoots and none was found under src/*/kotlin or src/*/java " +
                "of the project root, so declaration sites (FileName.kt:NN) could not be resolved to files. " +
                "Violations declared in the DSL were placed at the file name alone, which GitHub cannot show."
        }

        "同じ理由は 1 回にまとめる" {
            val sarif = listOf(missing, missing, uncheckedCheck).toSarif(tree.context(sourceRoots = emptyList()))
            Regex("No source root was given").findAll(sarif).count() shouldBe 1
        }

        "architecture { } も無ければ、宣言元の無い違反は path をそのまま置く" {
            val sarif = uncheckedCheck.sarifWith(tree.context(sourceRoots = emptyList(), architectureDeclaredAt = null))
            primaryLocationOf(sarif) shouldContain "\"uri\": \".\""
            sarif shouldNotContain "toolExecutionNotifications"
        }

        "architecture { } が解決できないときは、その理由の末尾が「ファイル名だけ」になる" {
            val noArchitectureFile = FakeSarifTree((SARIF_SOURCE_ROOT / "com/example/roles/UseCaseRole.kt").value to "")
            val sarif = uncheckedCheck.sarifWith(noArchitectureFile.context())
            primaryLocationOf(sarif) shouldContain "\"uri\": \"ProjectArchitecture.kt\""
            notificationsOf(sarif) shouldContain
                "Declaration site ProjectArchitecture.kt:12 was not found under any source root (architecture-test/src/test/kotlin). " +
                "The violation was placed at the file name alone, which GitHub cannot show."
        }
    }

    "StaleBaselineEntry" - {
        val stale = StaleBaselineEntry("legacy/Old.kt", "me.tbsten.katachi.check.LayoutCheck", "UnexpectedFile", 2, 0, "katachi-baseline.json")

        "baseline ファイルの該当行に置かれる" {
            val sarif = stale.sarifWith(tree.context())
            primaryLocationOf(sarif) shouldContain "\"uri\": \"android/katachi-baseline.json\""
            primaryLocationOf(sarif) shouldContain "\"startLine\": 5"
        }

        "行が見つからなければ region を省く" {
            val rewritten = FakeSarifTree((SARIF_PROJECT_ROOT / "katachi-baseline.json").value to "{\"version\": 1, \"checks\": {}}\n")
            val sarif = stale.sarifWith(rewritten.context())
            primaryLocationOf(sarif) shouldContain "\"uri\": \"android/katachi-baseline.json\""
            primaryLocationOf(sarif) shouldNotContain "region"
        }

        "baseline ファイルが無ければ architecture { } に落ちる" {
            val gone = FakeSarifTree((SARIF_SOURCE_ROOT / "ProjectArchitecture.kt").value to "")
            val sarif = stale.sarifWith(gone.context())
            primaryLocationOf(sarif) shouldContain "ProjectArchitecture.kt\""
        }
    }
})
