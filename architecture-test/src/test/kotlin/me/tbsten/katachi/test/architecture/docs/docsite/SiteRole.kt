package me.tbsten.katachi.test.architecture.docs.docsite

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.site() = "Site".role {
    title = "ドキュメントサイト"
    summary = "Astro (Starlight) のサイト。中の構成は Astro の規約が決めるので katachi は言わない"
    example("astro.config.mjs", "Starlight とテーマの配線")
    example("package.json", "サイトの依存とスクリプト")
    layout {
        "docs" {
            // Toolchain config files land here and come and go.
            anyFile()
            // Astro owns what is inside this, down to the file names of the routes. `public` belongs to `Dokka`.
            "src".ignore()
            // `pnpm patch` writes the patched dependencies here, named by pnpm (`<name>@<version>.patch`).
            "patches" { "*.patch".file() }
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
