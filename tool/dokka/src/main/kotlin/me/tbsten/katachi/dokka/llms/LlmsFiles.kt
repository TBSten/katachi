package me.tbsten.katachi.dokka.llms

import me.tbsten.katachi.dokka.KatachiDokkaConfiguration

/** The llms.txt index of a module, or of the aggregated output: see https://llmstxt.org/. */
internal const val LLMS_FILE: String = "llms.txt"

/** The whole API reference of a module in one Markdown file, for tools that read everything. */
internal const val LLMS_FULL_FILE: String = "llms-full.txt"

/** The llms files this configuration asks each module for, in the order they are listed. */
internal val KatachiDokkaConfiguration.llmsFiles: List<String>
    get() = listOfNotNull(LLMS_FILE.takeIf { llms }, LLMS_FULL_FILE.takeIf { llmsFull })
