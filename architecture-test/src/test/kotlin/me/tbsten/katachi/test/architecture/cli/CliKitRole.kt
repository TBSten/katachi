package me.tbsten.katachi.test.architecture.cli

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.cliKit() = "CliKit".role {
    title = "katachi コマンドの配布物"
    summary = "Homebrew の formula が展開するもの。入口の Kotlin スクリプトと、それを kotlin で起動する sh"
    description = """
        `./gradlew :katachi-script:cliTar` が tar にし、Release に添付します。
        formula は `TBSten/homebrew-tap` にあり、Release のたびに publish.yml が URL と sha256 を書き換えます。
    """.trimIndent()
    example("katachi.main.kts", "@file:DependsOn で katachi-script を引いて main(args) を呼ぶ")
    example("bin/katachi", "KATACHI_HOME を決めて kotlin katachi.main.kts を exec する")
    layout {
        "katachi-script" {
            // `${katachiVersion}` inside is a template `cliDist` expands, so the checked-in file is not runnable as is.
            "cli" / "katachi.main.kts".file()
            "cli" / "bin" / "katachi".file()
        }
    }
}
