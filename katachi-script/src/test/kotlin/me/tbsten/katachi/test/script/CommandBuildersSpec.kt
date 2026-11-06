package me.tbsten.katachi.test.script

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * The script builders (`katachi.scaffold.`package`(...)`) are generated from COMMAND_SPECS and checked in.
 * Rewrite them with `./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.
 */
class CommandBuildersSpec : FreeSpec({
    val dir = File("src/main/kotlin/me/tbsten/katachi/script")
    val updating = System.getProperty("katachi.golden.update").equals("true", ignoreCase = true)
    val generated = generateCommandBuilders()

    "生成したビルダーがチェックインされたものと一致する" {
        if (updating) {
            generated.forEach { (name, source) -> dir.resolve(name).writeText(source) }
        }
        val differing = generated.filter { (name, source) -> dir.resolve(name).takeIf { it.isFile }?.readText() != source }.keys
        withClue(
            "Out of date with COMMAND_SPECS: $differing. Run " +
                "`./gradlew :katachi-script:test --tests '*CommandBuildersSpec' -Dkatachi.golden.update=true`.",
        ) {
            differing.shouldBeEmpty()
        }
    }

    "spec から消えたサブコマンドの生成ファイルが残っていない" {
        if (updating) {
            dir.listFiles().orEmpty().filter { it.isFile && it.name !in generated && it.readText().startsWith(GENERATED_HEADER) }.forEach { it.delete() }
        }
        val stale = dir.listFiles().orEmpty().filter { it.isFile && it.name !in generated && it.readText().startsWith(GENERATED_HEADER) }
        stale.map { it.name } shouldBe emptyList()
    }
})
