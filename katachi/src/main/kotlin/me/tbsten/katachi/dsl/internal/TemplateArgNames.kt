package me.tbsten.katachi.dsl.internal

/**
 * The `--arg` name that says which templates to run or describe. Spelled once, because
 * [me.tbsten.katachi.template.GenerateCodeFromTemplate.undeclaredArgNames] has to read it out of
 * the raw arguments before anything has decoded the processor's `Args`, and a capture of that
 * name is refused.
 */
internal const val TEMPLATE_ARG: String = "template"

/** The `--arg` name of `GenerateCodeFromTemplate.Args.onExisting`, which a capture may not take. */
internal const val ON_EXISTING_ARG: String = "onExisting"
