package me.tbsten.katachi.test.architecture.docs

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.docsSite() = "DocsSite".role {
    title = "ドキュメントサイト"
    summary = "Astro (Starlight) のサイト。中の構成は Astro の規約が決めるので katachi は言わない"
    example("astro.config.mjs", "Starlight とテーマの配線")
    example("package.json", "サイトの依存とスクリプト")
    layout {
        "docs" {
            // Toolchain config files land here and come and go.
            anyFile()
            // Astro owns what is inside these, down to the file names of the routes.
            "src".ignore()
            "public".ignore()
            // Listed in `docs/.gitignore`, so the default `files = gitTracked()` never
            // offers them at all. Written out anyway, the way every sample writes
            // `"build".ignore()`: a reader who switches to `files = wholeTree()` has to
            // be able to see why they are not checked.
            "node_modules".ignore()
            "dist".ignore()
            ".astro".ignore()
        }
    }
}
