package me.tbsten.katachi.script.internal

/**
 * The guide and the two templates, shipped inside this artifact so that `init` and `guide` need no
 * network. The guide is the katachi skill's setup steps joined into one text, in English only
 * (KATACHI-318); the templates come from `docs/public/install/<lang>/` as they are.
 */
internal object Bundle {
    private const val ROOT = "META-INF/katachi-script/install"

    /** The setup steps of the katachi skill (`skills/katachi/references/setup`), one language for all. */
    const val GUIDE: String = "guide.md"

    const val CHECK_LIST_TEMPLATE: String = "install-check-list.html"

    const val REPORT_TEMPLATE: String = "project-code-base-report-template.html"

    /** Where a bundled file says it came from, in the MANIFEST and in messages. */
    fun origin(lang: Lang, name: String): String = when (name) {
        GUIDE -> "katachi-script ${ScriptVersion.current()}:skills/katachi/references/setup"
        else -> "katachi-script ${ScriptVersion.current()}:install/${lang.code}/$name"
    }

    fun text(lang: Lang, name: String): String {
        val path = if (name == GUIDE) "$ROOT/$name" else "$ROOT/${lang.code}/$name"
        val stream = Bundle::class.java.classLoader.getResourceAsStream(path)
            ?: die(
                "katachi に同梱されているはずの $path がありません。katachi を入れ直してください（brew reinstall tbsten/tap/katachi）。",
                "$path, which ships with katachi, is missing. Reinstall katachi (brew reinstall tbsten/tap/katachi).",
            )
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
