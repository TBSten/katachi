package me.tbsten.katachi.test.architecture

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.pascalCase

/**
 * Guards the naming rule the split of this definition rests on, and the stack capture that
 * makes it observable.
 *
 * [projectArchitecture] no longer holds a single `group` or role call of its own, so every
 * declaration site has to come out of a `groups/` or `roles/` file. Nothing else in this module
 * reads `declaredAt`: `ProjectArchitectureSpec` asserts the layout and `RoleCoverageSpec` the
 * wildcards, and both stay green if an `inline` slips onto a declaration function or if katachi
 * starts capturing one frame further out. The three samples have carried this test since they
 * were split; the largest of the four definitions had no equivalent until now.
 *
 * Reading metadata off a declaration is `@ExperimentalKatachiApi` because the shape is still
 * moving. Writing it in the definition needs no opt-in.
 *
 * ## The `Gradle` group is a second exception, narrower than the whole subtree
 *
 * `gradle()` declares its own group, the nested `GradleWrapper` group with its three roles, and
 * four more roles (`SettingsScript`, `BuildScript`, `GradleProperties`, `VersionCatalog`) from
 * inside katachi's own `me.tbsten.katachi.dsl.gradle.GradleGroup.kt`, so their captured frame is
 * not that file but the first one outside katachi -- the `gradle { }` call in this project's own
 * `groups/GradleGroup.kt`. Every one of those collapses onto that single line, which is
 * exactly what `Gradle は gradle() を呼んだ1箇所にまとめて宣言されている` below checks.
 *
 * `Gradle.BuildLogic` is declared inside the same `gradle { }` block but is not one of
 * those: `buildLogic()` is a plain call to a function written in `roles/BuildLogicRole.kt`, and
 * the role itself -- `"BuildLogic" { }` -- is literally written there, so its own frame is
 * outside katachi already and the walk never reaches `groups/GradleGroup.kt` at all. It is
 * checked by the ordinary per-file rule below, same as any other role.
 */
@OptIn(ExperimentalKatachiApi::class)
class DeclarationSiteSpec : FreeSpec({
    "group ごとに、その group の名前から決まるファイルで宣言されている" {
        // The naming rule is the whole convention, so it is checked rather than listed: a
        // group named `"debug-menu"` belongs in `DebugMenuGroup.kt`, and `pascalCase` is the
        // same conversion katachi applies to a captured wildcard.
        //
        // `gradle()`'s own group and roles are declared inside katachi, not here -- see the
        // class KDoc -- so they are excluded and checked separately below.
        projectArchitecture.allGroups.filterNot { it.qualifiedName in gradleOwnDeclarations }.forEach { group ->
            group.declaredAt.fileName shouldBe "${group.name.pascalCase}Group.kt"
        }
    }

    "役割ごとに、その役割の名前から決まるファイルで宣言されている" {
        projectArchitecture.allRoles.filterNot { it.qualifiedName in gradleOwnDeclarations }.forEach { role ->
            role.declaredAt.fileName shouldBe "${role.name.pascalCase}Role.kt"
        }
    }

    "宣言位置が ProjectArchitecture.kt ではなく、宣言を書いたファイルを指す" {
        // `ProjectArchitecture.kt` only calls the group functions, so nothing may be
        // attributed to it. If katachi captured the frame one level out, every declaration
        // would collapse onto this one file.
        val files = (projectArchitecture.allGroups.map { it.declaredAt } + projectArchitecture.allRoles.map { it.declaredAt })
            .map { it.fileName }
            .distinct()

        files shouldNotContain "ProjectArchitecture.kt"
    }

    "捕捉した行番号の行に、その宣言が実際に書かれている" {
        // The tests above only prove the file name. This one reads the source back, so a
        // one-frame shift lands on `architecture {` or on a `libraryGroup()` call and fails.
        // It is also what catches an `inline` slipping onto a group or role function, since
        // the remapped line number no longer holds the name. No line number is hard-coded, so
        // editing the definition does not break it.
        projectArchitecture.allGroups.filterNot { it.qualifiedName in gradleOwnDeclarations }.forEach { group ->
            val source = sourceLinesOf(group.declaredAt.fileName)
            source[group.declaredAt.lineNumber - 1] shouldContain "\"${group.name}\""
        }
        projectArchitecture.allRoles.filterNot { it.qualifiedName in gradleOwnDeclarations }.forEach { role ->
            val source = sourceLinesOf(role.declaredAt.fileName)
            source[role.declaredAt.lineNumber - 1] shouldContain "\"${role.name}\""
        }
    }

    "Gradle は gradle() を呼んだ1箇所にまとめて宣言されている" {
        val gradleGroup = projectArchitecture.allGroups.single { it.qualifiedName == "Gradle" }
        gradleGroup.declaredAt.fileName shouldBe "GradleGroup.kt"

        val sites = (
            projectArchitecture.allGroups.filter { it.qualifiedName in gradleOwnDeclarations }
                .map { it.declaredAt } +
                projectArchitecture.allRoles.filter { it.qualifiedName in gradleOwnDeclarations }
                    .map { it.declaredAt }
            ).distinct()
        sites shouldBe listOf(gradleGroup.declaredAt)

        val source = sourceLinesOf(gradleGroup.declaredAt.fileName)
        source[gradleGroup.declaredAt.lineNumber - 1] shouldContain "gradle {"
    }

    "Gradle.BuildLogic は gradle() の内側から buildLogic() で足されるが、宣言位置は自分のファイル" {
        // block() に渡した buildLogic() 自体はこの project の groups/GradleGroup.kt から
        // 呼ばれるが、"BuildLogic" { } という呼び出しそのものは roles/BuildLogicRole.kt に
        // 書かれているので、宣言位置はそちらに残る。gradle() が内部で持つ役割と違って、
        // katachi のフレームを一切経由しない。
        val buildLogic = projectArchitecture.allRoles.single { it.qualifiedName == "Gradle.BuildLogic" }
        buildLogic.declaredAt.fileName shouldBe "BuildLogicRole.kt"
    }
})

/**
 * The groups and roles `gradle()` declares from inside itself -- everything
 * whose declaration site collapses onto the single `gradle { }` call in `groups/GradleGroup.kt`
 * of this project. `Gradle.BuildLogic` is deliberately not in this set; see the class KDoc.
 *
 * `internal` rather than `private`: `RoleCoverageSpec` reads it too, to carve the same roles
 * out of its own check for a different reason -- see the comment there.
 */
internal val gradleOwnDeclarations = setOf(
    "Gradle",
    "Gradle.GradleWrapper",
    "Gradle.GradleWrapper.LauncherScript",
    "Gradle.GradleWrapper.WrapperJar",
    "Gradle.GradleWrapper.WrapperProperties",
    "Gradle.SettingsScript",
    "Gradle.BuildScript",
    "Gradle.GradleProperties",
    "Gradle.VersionCatalog",
    "Gradle.DaemonJvmProperties",
)

/** Cached so that reading a source file once per declaration does not hit the disk again. */
private val sourceLineCache = mutableMapOf<String, List<String>>()

/**
 * The lines of one file of the architecture definition, so a captured line number can be
 * compared against what is actually written there.
 *
 * The file is looked up by name under the source root rather than by path, so moving a
 * declaration between `groups/` and `roles/` needs no change here.
 */
private fun sourceLinesOf(fileName: String): List<String> = sourceLineCache.getOrPut(fileName) {
    // `getProperty` is a platform type; the JVM always defines `user.dir`.
    val workingDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
    val sourceRoot = generateSequence(workingDir) { it.parentFile }
        .map { File(it, "src/test/kotlin") }
        .firstOrNull { it.isDirectory }
    requireNotNull(sourceRoot) { "src/test/kotlin が $workingDir とその親に見つからない" }

    val source = sourceRoot.walkTopDown().firstOrNull { it.isFile && it.name == fileName }
    requireNotNull(source) { "$fileName が $sourceRoot 以下に見つからない" }.readLines()
}
