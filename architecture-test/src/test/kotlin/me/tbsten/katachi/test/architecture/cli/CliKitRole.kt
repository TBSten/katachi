package me.tbsten.katachi.test.architecture.cli

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.cliKit() = "CliKit".role {
    title = "katachi コマンドの配布物"
    summary = "Homebrew・Scoop が展開するもの。入口の Kotlin スクリプトと、それを kotlin で起動する sh・cmd"
    description = """
        `./gradlew :katachi-script:cliTar`（macOS・Linux）と `:katachi-script:cliZip`（Windows、Scoop）が固めて Release に添付します。
        formula と Scoop の manifest は `TBSten/homebrew-tap` にあり、Release のたびに publish.yml が URL と sha256 を書き換えます。
    """.trimIndent()
    example("katachi.main.kts", "@file:DependsOn で katachi-script を引いて main(args) を呼ぶ")
    example("bin/katachi", "KATACHI_HOME を決めて kotlin katachi.main.kts を exec する")
    example("bin/katachi.cmd", "Windows 用。kotlin.bat を呼ぶ（CRLF のまま保つ）")
    layout {
        "katachi-script" {
            // `${katachiVersion}` inside is a template `cliDist` expands, so the checked-in file is not runnable as is.
            "cli" / "katachi.main.kts".file()
            "cli" / "bin" / "katachi".file()
            "cli" / "bin" / "katachi.cmd".file()
        }
    }
}
