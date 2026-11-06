package me.tbsten.katachi.dsl.internal

// What one module key of gradle() declared: one subtree per module the key stood for. A wildcard key may have
// stood for several modules, or for none, so this is not one directory and cannot be continued with `/`.
internal class LayoutModule(
    val declared: List<LayoutNode>,
) {
    // Existence only: a module's files are still checked the usual way once they exist.
    fun optional(): LayoutModule {
        declared.forEach { it.markFilesOptional() }
        return this
    }

    override fun toString(): String = "LayoutModule(${declared.map { it.pathFromDeclaration() }})"
}
