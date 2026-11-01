package me.tbsten.katachi.test.architecture

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.pascalCase

@OptIn(ExperimentalKatachiApi::class)
class DeclarationSiteSpec : FreeSpec({
    "group ごとに、その group の名前から決まるファイルで宣言されている" {
        // The naming rule is the whole convention, so it is checked rather than listed: a
        // group named `"debug-menu"` belongs in `DebugMenuGroup.kt`, and `pascalCase` is the
        // same conversion katachi applies to a captured wildcard.
        //
        // `gradle()`'s own group and roles are declared inside katachi, not here, so they
        // are excluded and checked separately below.
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
        // block() に渡した buildLogic() 自体はこの project の gradle/GradleGroup.kt から
        // 呼ばれるが、"BuildLogic".role { } という呼び出しそのものは gradle/BuildLogicRole.kt に
        // 書かれているので、宣言位置はそちらに残る。gradle() が内部で持つ役割と違って、
        // katachi のフレームを一切経由しない。
        val buildLogic = projectArchitecture.allRoles.single { it.qualifiedName == "Gradle.BuildLogic" }
        buildLogic.declaredAt.fileName shouldBe "BuildLogicRole.kt"
    }
})

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

private val sourceLineCache = mutableMapOf<String, List<String>>()

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
