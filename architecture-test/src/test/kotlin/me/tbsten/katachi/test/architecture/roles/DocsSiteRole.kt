package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The role of the Astro (Starlight) site under `docs/`.
 *
 * ## Why the site is declared loosely
 *
 * Directly under `docs/` the role says `anyFile()` rather than naming `package.json`,
 * `astro.config.mjs` and the rest. That is a deliberate loosening: people and other agents
 * edit this directory continuously, and a per-file declaration would turn one added `.npmrc`
 * into a failing build in a module that has nothing to do with the docs.
 *
 * What stays tight is the set of top-level directories. A new one appearing under `docs/` is
 * reported, which is exactly the change worth hearing about — a new tool's config file is not.
 */
fun DeclarationContainerScope.docsSite() = "DocsSite" {
    title = "ドキュメントサイト"
    summary = "Astro (Starlight) のサイト。中の構成は Astro の規約が決めるので katachi は言わない"
    example("astro.config.mjs", "Starlight とテーマの配線")
    example("package.json", "サイトの依存とスクリプト")
    layout {
        "docs" {
            // Toolchain config files land here and come and go; see the KDoc above.
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
