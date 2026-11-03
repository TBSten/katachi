package me.tbsten.katachi.dsl.internal

// Declare skips every member's block; Run and Preview run both, with the set's parameter values bound.
internal sealed interface TemplateSetMode {
    data object Declare : TemplateSetMode

    class Run(val values: Map<String, String>) : TemplateSetMode

    class Preview(val values: Map<String, String>) : TemplateSetMode
}
