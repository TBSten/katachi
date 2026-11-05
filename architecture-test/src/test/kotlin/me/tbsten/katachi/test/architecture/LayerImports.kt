package me.tbsten.katachi.test.architecture

private val LAYERS: List<List<String>> =
    listOf(
        listOf(""),
        listOf("util"),
        listOf("dsl"),
        listOf("processor", "check", "report"),
        listOf("docs"),
        listOf("template"),
        listOf("ide"),
        listOf("konsist", "konture"),
        listOf("script"),
    )

private fun layersAfter(layer: String): List<String> {
    val index = LAYERS.indexOfFirst { layer in it }
    require(index >= 0) { "`$layer` is not a layer in LAYERS" }
    return LAYERS.drop(index + 1).flatten()
}

private const val ROOT_PACKAGE: String = "me.tbsten.katachi"

fun laterLayerImportsOf(layer: String): List<String> =
    layersAfter(layer).map { "$ROOT_PACKAGE.$it.**" }
