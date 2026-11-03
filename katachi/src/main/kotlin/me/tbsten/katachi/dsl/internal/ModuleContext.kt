package me.tbsten.katachi.dsl.internal

internal class ModuleContext(
    val modulePath: String,
    val wildcards: List<String>,
    // An entry is null when only some of the key's `*`s were named.
    val captureNames: List<String?>? = null,
) {
    // Only `*`s are named and a trailing `**` is the last wildcard, so names line up with the first values.
    val captures: Map<String, String>
        get() = captureNames.orEmpty().zip(wildcards)
            .mapNotNull { (name, value) -> name?.let { it to value } }
            .toMap()
}
