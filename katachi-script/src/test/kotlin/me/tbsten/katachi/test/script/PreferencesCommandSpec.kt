package me.tbsten.katachi.test.script

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** `preferences show / set`: how the definition is to be written, recorded in the check list (KATACHI-266). */
class PreferencesCommandSpec : FreeSpec({
    "show は記録が無ければ推奨を、catalog があれば検出したことを出す" {
        val fixture = PreferencesFixture()
        fixture.project.resolve("gradle").createDirectories().resolve("libs.versions.toml").writeText("[versions]\n")

        val run = fixture.run("agent", "preferences")

        run.status shouldBe 0
        run.out shouldContain "nestedGroups = yes（推奨）   # group を入れ子にするか (yes/no)"
        run.out shouldContain "moduleDir = architecture-test（推奨）"
        run.out shouldContain "testFramework = junit5（推奨）"
        run.out shouldContain "dependencyStyle = catalog（検出: gradle/libs.versions.toml）"
        run.out shouldContain "constraintNames = plain（推奨）"
    }

    "show は Gradle のファイルから kotest を見つける" {
        val fixture = PreferencesFixture()
        fixture.project.resolve("app").createDirectories().resolve("build.gradle.kts")
            .writeText("dependencies {\n    testImplementation(\"io.kotest:kotest-runner-junit5:6.0.0\")\n}\n")

        fixture.run("agent", "preferences", "show").out shouldContain "testFramework = kotest（検出: app/build.gradle.kts）"
    }

    "set は値を確かめてから記録し、show は記録済みと出す" {
        val fixture = PreferencesFixture()

        val run = fixture.run("agent", "preferences", "set", "nestedGroups=no", "constraintNames=numbered")

        run.status shouldBe 0
        run.out shouldContain "記録しました: nestedGroups=no constraintNames=numbered"
        fixture.preferences() shouldBe JsonObject(mapOf("nestedGroups" to JsonPrimitive("no"), "constraintNames" to JsonPrimitive("numbered")))
        fixture.run("agent", "preferences").out shouldContain "nestedGroups = no（記録済み）"
    }

    "set を省いて key=value だけでも記録する" {
        val fixture = PreferencesFixture()
        fixture.run("agent", "preferences", "oneRolePerFile=no").status shouldBe 0
        fixture.preferences()["oneRolePerFile"] shouldBe JsonPrimitive("no")
    }

    "moduleDir は meta.moduleDir に記録する" {
        val fixture = PreferencesFixture()

        fixture.run("agent", "preferences", "set", "moduleDir=static-analysis/katachi").status shouldBe 0

        fixture.checkList()["meta"]!!.jsonObject["moduleDir"] shouldBe JsonPrimitive("static-analysis/katachi")
        fixture.run("agent", "preferences").out shouldContain "moduleDir = static-analysis/katachi（記録済み）"
    }

    "どれか 1 つでも値が違えば、ほかの値も書かずに止まる" {
        val fixture = PreferencesFixture()

        val run = fixture.run("agent", "preferences", "set", "nestedGroups=no", "testFramework=spock")

        run.status shouldBe 1
        run.err shouldContain "testFramework の値が違います: spock（使える値: junit5/kotest/kotest-fun/kotest-string/kotest-should/kotest-describe/kotest-behavior/kotest-word/kotest-free/kotest-feature/kotest-expect/kotest-annotation）"
        fixture.preferences() shouldBe JsonObject(emptyMap())
    }

    "知らないキー・形の違う引数・相対パスでない moduleDir は止まる" {
        val fixture = PreferencesFixture()
        fixture.run("agent", "preferences", "set", "colour=red").err shouldContain "知らないキーです: colour"
        fixture.run("agent", "preferences", "set", "nestedGroups").err shouldContain "preferences set は key=value の形です: nestedGroups"
        fixture.run("agent", "preferences", "set", "moduleDir=../outside").err shouldContain "--module-dir は Gradle ルートからの相対パスです"
        fixture.run("agent", "preferences", "set").err shouldContain "preferences set: key=value を 1 つ以上指定してください"
        fixture.run("agent", "preferences", "list").err shouldContain "preferences のサブコマンドは show / set です（指定されたのは 'list'）"
    }
})

/** A project and a working directory whose check list records it, with no preferences yet. */
private class PreferencesFixture {
    val project: Path = createTempDirectory("katachi-project-")
    val workdir: Path = workdirWith("""{"meta": {"projectRoot": "$project"}, "preferences": {}, "steps": []}""")

    fun run(vararg args: String): RecordedRun = runCapturing(project, args.toList(), env = mapOf("KATACHI_WORKDIR" to workdir.toString()))

    fun checkList(): JsonObject = jsonBlockOf(workdir.resolve("check-list.html"), "checklist")

    fun preferences(): JsonObject = checkList()["preferences"]!!.jsonObject
}
