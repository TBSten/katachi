package com.example.kmp

import io.kotest.core.spec.style.FreeSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.Documented
import me.tbsten.katachi.dsl.Examples
import me.tbsten.katachi.dsl.Summary
import me.tbsten.katachi.dsl.Title
import me.tbsten.katachi.dsl.pascalCase

/**
 * Checks the definition under `src/test/kotlin/com/example/kmp` against the model katachi
 * builds from it: names, titles, summaries and declaration sites.
 *
 * Whether the repository matches that definition is a different question, asked by
 * [ProjectLayoutSpec] with one `assert()`.
 *
 * Titles, summaries and examples are metadata on the declaration, read back as `role[Title]`.
 * That read is `@ExperimentalKatachiApi` — the shape still moves — while writing it in the
 * definition (`title = "..."`) needs no opt-in at all.
 */
@OptIn(ExperimentalKatachiApi::class)
class ProjectArchitectureSpec : FreeSpec({
    "宣言した group がすべてモデルに含まれる" {
        projectArchitecture.allGroups.map { it.qualifiedName }.toSet() shouldBe
            setOf("feature", "ui", "data", "testing", "app", "build", "tool")
    }

    "宣言した役割がすべてモデルに含まれる" {
        projectArchitecture.allRoles.map { it.qualifiedName }.toSet() shouldBe
            setOf(
                "feature/Screen",
                "feature/ViewModel",
                "feature/Route",
                "ui/Component",
                "ui/Theme",
                "ui/UiCore",
                "ui/Preview",
                "ui/PreviewRoot",
                "ui/Navigation",
                "data/Repository",
                "data/PlatformImplementation",
                "testing/Fake",
                "testing/Test",
                "testing/ArchitectureDefinition",
                "app/Entrypoint",
                "app/AndroidResource",
                "app/XcodeProject",
                "build/GradleModule",
                "build/GradleRoot",
                "tool/Git",
            )
    }

    "group は宣言した順に並ぶ" {
        // The order is the order `ProjectArchitecture.kt` calls the group functions in,
        // which is the only thing that decides it now that every declaration lives in a
        // file of its own.
        projectArchitecture.groups.map { it.name } shouldContainExactly
            listOf("feature", "ui", "data", "testing", "app", "build", "tool")
    }

    "宣言元のファイル名と行番号が取れる" {
        // The real point of this test: the declaration site is read off the stack trace, so
        // it is the kind of thing that breaks only outside katachi's own test setup.
        val screen = projectArchitecture.allRoles.single { it.qualifiedName == "feature/Screen" }
        screen.declaredAt.fileName shouldBe "ScreenRole.kt"
        (screen.declaredAt.lineNumber > 0) shouldBe true

        // Groups and layouts carry one too.
        projectArchitecture.allGroups.single { it.name == "data" }
            .declaredAt.fileName shouldBe "DataGroup.kt"
        screen.layouts.single().declaredAt.fileName shouldBe "ScreenRole.kt"
    }

    "宣言位置は、その宣言の名前から決まるファイルを指す" {
        // The definition is split one declaration per file and `ProjectArchitecture.kt` only
        // calls the group functions. Every declaration therefore has to point at the file it
        // is written in, never at the file that called the function -- which is what would
        // happen if the extension functions were `inline`, or if katachi's frame filter
        // stopped one level too early.
        //
        // The naming rule is checked rather than listed, so adding a role does not mean
        // editing a table here: a group named `"debug-menu"` belongs in `DebugMenuGroup.kt`,
        // and `pascalCase` is the same conversion katachi applies to a captured wildcard.
        projectArchitecture.allGroups.forEach { group ->
            group.declaredAt.fileName shouldBe "${group.name.pascalCase}Group.kt"
        }
        projectArchitecture.allRoles.forEach { role ->
            role.declaredAt.fileName shouldBe "${role.name.pascalCase}Role.kt"
        }

        // Said once more from the other side: if the frame were captured one level out,
        // every declaration would collapse onto the file that called into it.
        val fileNames = (
            projectArchitecture.allGroups.map { it.declaredAt.fileName } +
                projectArchitecture.allRoles.map { it.declaredAt.fileName }
            ).distinct()

        fileNames shouldNotContain "ProjectArchitecture.kt"
    }

    "捕捉した行番号の行に、その宣言が実際に書かれている" {
        // The test above only proves which file each declaration claims. This one reads the
        // source back, so a one-frame shift -- landing on the `uiGroup()` call in
        // ProjectArchitecture.kt, or on the `component()` call inside `UiGroup.kt` -- fails
        // here. It is also what catches an `inline` slipping onto a group or role function,
        // since the remapped line number no longer holds the name. No line number is
        // hard-coded, so editing the definition does not break it.
        val sources = architectureDefinitionSources()

        projectArchitecture.allGroups.forEach { group ->
            val lines = requireNotNull(sources[group.declaredAt.fileName]) {
                "${group.name} の宣言元 ${group.declaredAt.fileName} が見つからない"
            }
            lines[group.declaredAt.lineNumber - 1] shouldContain "\"${group.name}\""
        }
        projectArchitecture.allRoles.forEach { role ->
            val lines = requireNotNull(sources[role.declaredAt.fileName]) {
                "${role.qualifiedName} の宣言元 ${role.declaredAt.fileName} が見つからない"
            }
            lines[role.declaredAt.lineNumber - 1] shouldContain "\"${role.name}\""
        }
    }

    "ビルドとツールの group と役割は documented = false" {
        listOf("build", "tool").forAll { groupName ->
            projectArchitecture.allGroups.single { it.name == groupName }[Documented] shouldBe false
            projectArchitecture.allRoles
                .filter { it.groupPath == listOf(groupName) }
                .forAll { it[Documented] shouldBe false }
        }
    }

    "documented を省略した group と役割には Documented が入らない" {
        // 省略を true と読むのは読む側の仕事。宣言には「書かなかった」だけが残る。
        projectArchitecture.allGroups.single { it.name == "ui" }[Documented] shouldBe null
        projectArchitecture.allRoles
            .single { it.qualifiedName == "data/Repository" }[Documented] shouldBe null
    }

    "title を書いた役割・group は表示名がそれになる" {
        projectArchitecture.allRoles.single { it.qualifiedName == "feature/Screen" }[Title] shouldBe
            "画面"
        // The group is named `ui` but titled `UI`, so this fails if the declared title is
        // dropped in favour of the default.
        projectArchitecture.allGroups.single { it.name == "ui" }[Title] shouldBe "UI"
    }

    "title を省略した役割は Title を持たず、役割名がそのまま表示名になる" {
        // `tool/Git` is the only declaration in this sample without a `title`.
        val git = projectArchitecture.allRoles.single { it.qualifiedName == "tool/Git" }
        git[Title] shouldBe null
        (git[Title] ?: git.name) shouldBe "Git"
    }

    "ui の共通レイヤーの役割は :ui モジュールの package を指している" {
        // `:ui:component` / `:ui:theme` / `:ui:core` は1つの `:ui` モジュールに統合された。
        // summary が旧モジュールパスのまま残ると、生成されるドキュメントが実態と食い違う。
        val roles = projectArchitecture.allRoles.associateBy { it.qualifiedName }
        listOf("ui/Component", "ui/Theme", "ui/UiCore", "ui/PreviewRoot").forAll { qualifiedName ->
            val summary = requireNotNull(roles[qualifiedName]?.get(Summary)) { "$qualifiedName に summary がない" }
            summary shouldContain ":ui モジュールの"
            summary shouldNotContain ":ui:"
        }
    }

    "Preview と PreviewRoot は別の役割" {
        // Two roles whose names are prefixes of each other, which is exactly where a mix-up
        // would go unnoticed. `Preview` is the `@Preview` function itself and lives beside
        // the composable it renders; `PreviewRoot` is the single wrapper in `:ui`.
        val roles = projectArchitecture.allRoles.associateBy { it.qualifiedName }
        val preview = requireNotNull(roles["ui/Preview"]) { "ui/Preview がない" }
        val previewRoot = requireNotNull(roles["ui/PreviewRoot"]) { "ui/PreviewRoot がない" }

        preview[Summary].orEmpty() shouldContain "Preview.kt"
        preview[Summary].orEmpty() shouldContain "PreviewRoot で包む"
        previewRoot[Summary].orEmpty() shouldContain "preview package"
    }

    "data の役割は :data モジュールの package を指している" {
        // `:data` was flat until the `user` / `platform` split. A summary that does not name
        // its package would send a reader of the generated docs to the wrong directory.
        val roles = projectArchitecture.allRoles.associateBy { it.qualifiedName }
        val packageOfRole = mapOf(
            "data/Repository" to "user",
            "data/PlatformImplementation" to "platform",
        )
        packageOfRole.forAll { (qualifiedName, packageName) ->
            val summary = requireNotNull(roles[qualifiedName]?.get(Summary)) { "$qualifiedName に summary がない" }
            summary shouldContain ":data モジュールの $packageName package"
        }
    }

    "data に settings package の役割は無い" {
        // sample/android has a SettingsRepository and this sample does not. Naming a
        // `settings` role here would document a package that no file lives in.
        projectArchitecture.allRoles
            .filter { it.groupPath == listOf("data") }
            .map { it.name } shouldContainExactly listOf("Repository", "PlatformImplementation")
        projectArchitecture.allRoles.forAll { role ->
            role[Summary].orEmpty() shouldNotContain "settings package"
        }
    }

    "example は呼んだ順にすべて保持される" {
        projectArchitecture.allRoles.single { it.qualifiedName == "data/Repository" }[Examples]
            .orEmpty().map { it.name } shouldContainExactly
            listOf("UserRepository", "UserRepositoryImpl")
    }

    "KMP 特有の役割が宣言されている" {
        // These three are what makes this sample different from sample/android.
        val roles = projectArchitecture.allRoles.associateBy { it.qualifiedName }
        roles["data/PlatformImplementation"] shouldNotBe null
        roles["app/XcodeProject"] shouldNotBe null
        roles["testing/Test"]?.get(Summary)?.contains("commonTest") shouldBe true
    }

    "katachi の定義を置くモジュール自身にも役割がある" {
        // The cost of moving the definition into `:architecture-test`: that module is part
        // of the repository, so it needs a role too. Its sources are the definition itself,
        // and its build script falls under the module build script role.
        val roles = projectArchitecture.allRoles.associateBy { it.qualifiedName }
        val definition = requireNotNull(roles["testing/ArchitectureDefinition"]) {
            "testing/ArchitectureDefinition がない"
        }
        definition[Summary].orEmpty() shouldContain ":architecture-test"
        val moduleScriptExamples =
            requireNotNull(roles["build/GradleModule"]?.get(Examples)) { "build/GradleModule がない" }
        moduleScriptExamples.any { it.name == "architecture-test/build.gradle.kts" } shouldBe true
    }

    "すべての役割が layout を1つ以上持つ" {
        projectArchitecture.allRoles.forAll { it.layouts.isNotEmpty() shouldBe true }
    }
})

/**
 * The definition's source files, keyed by file name, so a captured line number can be
 * compared against what is actually written there.
 *
 * A test task's working directory is its module directory -- here `architecture-test`, one
 * level below the sample root -- but that is a default a build file can change, so the
 * source root is looked up by walking up from wherever the tests run. File names are unique
 * across the tree, which is what makes a flat map enough.
 */
private fun architectureDefinitionSources(): Map<String, List<String>> {
    val relativePath = "src/test/kotlin/com/example/kmp"
    // `getProperty` is a platform type; the JVM always defines `user.dir`.
    val workingDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
    val sourceRoot = generateSequence(workingDir) { it.parentFile }
        .map { File(it, relativePath) }
        .firstOrNull { it.isDirectory }
    return requireNotNull(sourceRoot) { "$relativePath が $workingDir とその親に見つからない" }
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .associate { it.name to it.readLines() }
}
