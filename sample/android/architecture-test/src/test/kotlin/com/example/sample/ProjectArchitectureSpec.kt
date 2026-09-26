package com.example.sample

import com.example.sample.groups.appGroup
import com.example.sample.groups.dataGroup
import com.example.sample.groups.featureGroup
import com.example.sample.groups.gradleGroup
import com.example.sample.groups.testingGroup
import com.example.sample.groups.uiGroup
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.Documented
import me.tbsten.katachi.dsl.Examples
import me.tbsten.katachi.dsl.Title
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.pascalCase

/**
 * Checks that [projectArchitecture] builds into the model we meant to declare, and that the
 * check it drives really looks at this repository.
 *
 * **katachi's own integration test. A project adopting katachi does not write this** — it
 * writes [ProjectArchitectureTest] and nothing else. This one is run from a real Gradle test
 * task of a sample project rather than from katachi's own JVM tests, and what it guards is
 * that nothing in the surrounding toolchain quietly breaks the DSL — above all the
 * declaration site capture, which reads the JVM stack trace.
 *
 * Since the definition is split one declaration per file, the declaration site tests below
 * are also the proof that the split is free: a role written in `roles/ScreenRole.kt` must
 * report `ScreenRole.kt`, not the `ProjectArchitecture.kt` that called into it — which no
 * longer contains a single `group` or role call of its own.
 *
 * The last test is the one that keeps the rest honest: `assert()` passing proves nothing on
 * its own, because a traversal that reached no file at all would also pass. Removing one
 * group from an otherwise identical definition has to turn exactly that group's files into
 * violations.
 */
@OptIn(InternalKatachiApi::class, ExperimentalKatachiApi::class)
class ProjectArchitectureSpec : FreeSpec({
    "宣言した group がすべて宣言順にモデルに含まれる" {
        projectArchitecture.allGroups.map { it.qualifiedName } shouldContainExactly
            listOf("feature", "ui", "data", "app", "testing", "Gradle", "Gradle/GradleWrapper", "tool")
    }

    "宣言した役割が group ごと正しくモデルに含まれる" {
        projectArchitecture.allRoles.map { it.qualifiedName }.toSet() shouldBe setOf(
            "feature/Screen",
            "feature/ViewModel",
            "feature/Route",
            "feature/FeatureComponent",
            "feature/FeatureTest",
            "ui/Component",
            "ui/Theme",
            "ui/UiCore",
            "ui/Preview",
            "ui/PreviewRoot",
            "ui/Navigation",
            "data/Repository",
            "app/Entrypoint",
            "app/AndroidResource",
            "testing/Fake",
            "testing/Test",
            "testing/ArchitectureDefinition",
            "testing/GeneratedDocumentation",
            "testing/LayoutSnapshot",
            "Gradle/SettingsScript",
            "Gradle/BuildScript",
            "Gradle/GradleProperties",
            "Gradle/VersionCatalog",
            "Gradle/GradleWrapper/LauncherScript",
            "Gradle/GradleWrapper/WrapperJar",
            "Gradle/GradleWrapper/WrapperProperties",
            "tool/Git",
            "tool/Documentation",
        )
    }

    "役割の宣言位置として、その役割を書いたファイルの行番号が取れる" {
        val screen = projectArchitecture.allRoles.single { it.qualifiedName == "feature/Screen" }
        screen.declaredAt.fileName shouldBe "ScreenRole.kt"
        // The exact line churns on every edit; that it is a real line is the point.
        (screen.declaredAt.lineNumber > 0) shouldBe true
    }

    "group ごとに、その group の名前から決まるファイルで宣言されている" {
        // The naming rule is the whole convention, so it is checked rather than listed: a
        // group named `"debug-menu"` belongs in `DebugMenuGroup.kt`, and `pascalCase` is the
        // same conversion katachi applies to a captured wildcard.
        //
        // `Gradle` and everything nested under it are left out: they are declared by
        // katachi's own `gradle()`, not by this convention — see the dedicated test below.
        projectArchitecture.allGroups.filterNot { it.qualifiedName.isGradleGroupSubtree() }.forEach { group ->
            group.declaredAt.fileName shouldBe "${group.name.pascalCase}Group.kt"
        }
    }

    "役割ごとに、その役割の名前から決まるファイルで宣言されている" {
        projectArchitecture.allRoles.filterNot { it.qualifiedName.isGradleGroupSubtree() }.forEach { role ->
            role.declaredAt.fileName shouldBe "${role.name.pascalCase}Role.kt"
        }
    }

    "Gradle とその配下は、gradle() を呼んだ1箇所にまとめて宣言されている" {
        // katachi はスタックトレースを自分のフレームの外まで辿って宣言位置を捕まえるので、
        // `gradle()` の中で書かれた `"Gradle".group { }` やその中の `name { }` はそれぞれの
        // 行ではなく、katachi の外で最初に見つかるフレーム — `groups/GradleGroup.kt` の
        // `gradle()` 呼び出し — に集約される。「1宣言1ファイル」規約の例外ではなく、
        // 宣言位置が実際に1箇所しかないことの表れ。
        val gradleGroup = projectArchitecture.allGroups.single { it.qualifiedName == "Gradle" }
        gradleGroup.declaredAt.fileName shouldBe "GradleGroup.kt"

        val sites = (
            projectArchitecture.allGroups.filter { it.qualifiedName.isGradleGroupSubtree() }
                .map { it.declaredAt } +
                projectArchitecture.allRoles.filter { it.qualifiedName.isGradleGroupSubtree() }
                    .map { it.declaredAt }
            ).distinct()
        sites shouldBe listOf(gradleGroup.declaredAt)
    }

    "宣言位置が ProjectArchitecture.kt ではなく、宣言を書いたファイルを指す" {
        // The point of the split: neither `architecture { }` nor the extension functions are
        // `inline`, so the captured frame is the declaration's own and never the `uiGroup()`
        // call in ProjectArchitecture.kt. If katachi captured the frame one level out, every
        // declaration would collapse onto that one file.
        val fileNames = (
            projectArchitecture.allGroups.map { it.declaredAt.fileName } +
                projectArchitecture.allRoles.map { it.declaredAt.fileName }
            ).distinct()

        fileNames shouldNotContain "ProjectArchitecture.kt"
    }

    "layout の宣言位置も、その役割を書いたファイルになる" {
        val screen = projectArchitecture.allRoles.single { it.qualifiedName == "feature/Screen" }
        screen.layouts.single().declaredAt.fileName shouldBe "ScreenRole.kt"
    }

    "捕捉した行番号の行に、その宣言が実際に書かれている" {
        // The tests above only prove the file name and that the line is positive. This one
        // reads the source back, so a one-frame shift — landing on the `uiGroup()` call in
        // ProjectArchitecture.kt, or on the `component()` call inside `UiGroup.kt` — fails
        // here. It is also what catches an `inline` slipping onto a group or role function,
        // since the remapped line number no longer holds the name. No line number is
        // hard-coded, so editing the declaration files does not break it.
        val sources = declarationSourceLines()

        projectArchitecture.allGroups.filterNot { it.qualifiedName.isGradleGroupSubtree() }.forEach { group ->
            val source = sources.getValue(group.declaredAt.fileName)
            source[group.declaredAt.lineNumber - 1] shouldContain "\"${group.name}\""
        }
        projectArchitecture.allRoles.filterNot { it.qualifiedName.isGradleGroupSubtree() }.forEach { role ->
            val source = sources.getValue(role.declaredAt.fileName)
            source[role.declaredAt.lineNumber - 1] shouldContain "\"${role.name}\""
        }
    }

    "Gradle group と tool group だけが documented = false になっている" {
        // 書かなかった宣言には Documented が入らない。省略を true と読むのはここ（読む側）。
        val documentedByGroup =
            projectArchitecture.allGroups.associate { it.qualifiedName to (it[Documented] ?: true) }
        documentedByGroup shouldBe mapOf(
            "feature" to true,
            "ui" to true,
            "data" to true,
            "app" to true,
            "testing" to true,
            "Gradle" to false,
            "Gradle/GradleWrapper" to false,
            "tool" to false,
        )
    }

    "Gradle group と tool group の役割だけが documented = false になっている" {
        val undocumentedGroupPaths = listOf(listOf("Gradle"), listOf("Gradle", "GradleWrapper"), listOf("tool"))
        val (undocumentedRoles, otherRoles) =
            projectArchitecture.allRoles.partition { it.groupPath in undocumentedGroupPaths }
        undocumentedRoles.map { it[Documented] ?: true }.toSet() shouldBe setOf(false)
        otherRoles.map { it[Documented] ?: true }.toSet() shouldBe setOf(true)
    }

    "title を省略した役割は Title を持たず、役割名がそのまま表示名になる" {
        val git = projectArchitecture.allRoles.single { it.qualifiedName == "tool/Git" }
        git[Title] shouldBe null
        (git[Title] ?: git.name) shouldBe "Git"
    }

    "title を書いた役割はその表示名になる" {
        val screen = projectArchitecture.allRoles.single { it.qualifiedName == "feature/Screen" }
        screen[Title] shouldBe "Screen"
    }

    "1つの役割が複数の置き場所を layout として持てる" {
        val repository = projectArchitecture.allRoles.single { it.qualifiedName == "data/Repository" }
        repository.layouts.size shouldBe 2
    }

    "example は呼んだ順に保持される" {
        val repository = projectArchitecture.allRoles.single { it.qualifiedName == "data/Repository" }
        repository[Examples].orEmpty().map { it.name } shouldContainExactly
            listOf("UserRepository", "UserRepositoryImpl")
    }

    "役割を1つの group ぶん欠いた定義では、その group が覆っていたファイルだけが違反になる" {
        // The counterpart of ProjectArchitectureTest: that one proves the definition
        // accepts the repository, this one proves the traversal actually reached it. An
        // empty result here would mean the check walked nothing and passed for free.
        //
        // `tool` is the group to drop because it owns exactly two files, both at the root,
        // so the expectation can be written out in full rather than as a count. The order
        // is katachi's: violations are grouped by kind, and within a kind the traversal
        // order survives — the root listed by name, where `.gitignore` precedes `README.md`.
        val violations = architectureWithoutToolRoles.validate()

        violations.map { "[${it.label}] ${it.path}" } shouldContainExactly listOf(
            "[UnexpectedFile] .gitignore",
            "[UnexpectedFile] README.md",
        )
    }
})

/**
 * [projectArchitecture] with `toolGroup()` left out, used by the last test above.
 *
 * Deliberately broken, and deliberately kept out of `ProjectArchitecture.kt`: that file is
 * what a user reads as the worked example of a definition, and a definition that is meant to
 * fail has no place in it.
 */
private val architectureWithoutToolRoles: Architecture = architecture {
    featureGroup()
    uiGroup()
    dataGroup()
    appGroup()
    testingGroup()
    gradleGroup()
    // toolGroup() — omitted on purpose. `.gitignore` and `README.md` lose their role.
}

/** `"Gradle"` itself or anything nested under it -- see `groups/GradleGroup.kt`. */
private fun String.isGradleGroupSubtree(): Boolean = this == "Gradle" || startsWith("Gradle/")

/**
 * Every Kotlin source of this test source set, keyed by file name, so a captured line
 * number can be compared against what is actually written there.
 *
 * The declarations sit one per file under `groups/` and `roles/`, so the lookup is by file
 * name rather than by a fixed path: that is all a [me.tbsten.katachi.dsl.DeclarationSite]
 * carries, and it keeps the test from having to know which package a role was moved into.
 *
 * A test task's working directory is its module directory, but that is a default a build
 * file can change, so the source set is looked up by walking up from wherever the tests run.
 */
private fun declarationSourceLines(): Map<String, List<String>> {
    val relativePath = "src/test/kotlin/com/example/sample"
    // `getProperty` is a platform type, and AGP compiles unit tests in strict mode; the JVM
    // always defines `user.dir`.
    val workingDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
    val sourceRoot = generateSequence(workingDir) { it.parentFile }
        .map { File(it, relativePath) }
        .firstOrNull { it.isDirectory }
    requireNotNull(sourceRoot) { "$relativePath が $workingDir とその親に見つからない" }

    return sourceRoot.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .associate { it.name to it.readLines() }
}
