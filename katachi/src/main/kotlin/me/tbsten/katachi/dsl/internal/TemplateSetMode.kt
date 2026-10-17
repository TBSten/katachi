package me.tbsten.katachi.dsl.internal

/**
 * When a template set's block is replayed, which decides what the block may do.
 *
 * Declare runs the outer block and skips every member's block. Run and Preview run both, with
 * the set's parameter values bound; Preview differs only in what `isPreview` reads as.
 */
internal sealed interface TemplateSetMode {
    data object Declare : TemplateSetMode

    class Run(val values: Map<String, String>) : TemplateSetMode

    class Preview(val values: Map<String, String>) : TemplateSetMode
}
