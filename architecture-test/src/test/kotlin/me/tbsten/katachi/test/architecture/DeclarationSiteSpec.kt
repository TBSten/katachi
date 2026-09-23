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
 */
@OptIn(ExperimentalKatachiApi::class)
class DeclarationSiteSpec : FreeSpec({
    "group ごとに、その group の名前から決まるファイルで宣言されている" {
        // The naming rule is the whole convention, so it is checked rather than listed: a
        // group named `"debug-menu"` belongs in `DebugMenuGroup.kt`, and `pascalCase` is the
        // same conversion katachi applies to a captured wildcard.
        projectArchitecture.allGroups.forEach { group ->
            group.declaredAt.fileName shouldBe "${group.name.pascalCase}Group.kt"
        }
    }

    "役割ごとに、その役割の名前から決まるファイルで宣言されている" {
        projectArchitecture.allRoles.forEach { role ->
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
        projectArchitecture.allGroups.forEach { group ->
            val source = sourceLinesOf(group.declaredAt.fileName)
            source[group.declaredAt.lineNumber - 1] shouldContain "\"${group.name}\""
        }
        projectArchitecture.allRoles.forEach { role ->
            val source = sourceLinesOf(role.declaredAt.fileName)
            source[role.declaredAt.lineNumber - 1] shouldContain "\"${role.name}\""
        }
    }
})

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
