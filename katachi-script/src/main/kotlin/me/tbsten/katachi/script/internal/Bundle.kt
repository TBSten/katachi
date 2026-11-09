package me.tbsten.katachi.script.internal

/**
 * The guide and the two templates, shipped inside this artifact so that `init` and `guide` need no
 * network. `bundleInstallKit` copies them from `docs/public/install/<lang>/` as they are.
 */
internal object Bundle {
    private const val ROOT = "META-INF/katachi-script/install"

    const val GUIDE: String = "index.md"

    const val CHECK_LIST_TEMPLATE: String = "install-check-list.html"

    const val REPORT_TEMPLATE: String = "project-code-base-report-template.html"

    /** Where a bundled file says it came from, in the MANIFEST and in messages. */
    fun origin(lang: Lang, name: String): String = "katachi-script ${ScriptVersion.current()}:install/${lang.code}/$name"

    fun text(lang: Lang, name: String): String {
        val path = "$ROOT/${lang.code}/$name"
        val stream = Bundle::class.java.classLoader.getResourceAsStream(path)
            ?: die(Messages.Bundle.whichShipsKatachiMissing(path))
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
