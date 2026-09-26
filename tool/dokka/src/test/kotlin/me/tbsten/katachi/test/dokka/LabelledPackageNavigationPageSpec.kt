package me.tbsten.katachi.test.dokka

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.dokka.KatachiDokkaPlugin
import me.tbsten.katachi.dokka.navigation.LabelledPackageNavigationPage
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.renderers.html.NavigationPage
import org.jetbrains.dokka.pages.RendererSpecificResourcePage
import org.jetbrains.dokka.pages.RenderingStrategy
import org.jetbrains.dokka.plugability.DokkaPlugin
import org.jetbrains.dokka.plugability.DokkaPluginApiPreview
import org.jetbrains.dokka.plugability.PluginApiPreviewAcknowledgement
import org.jetbrains.dokka.transformers.pages.PageTransformer

/**
 * [LabelledPackageNavigationPage] copies the rendering of Dokka's `NavigationPage`. On a tree
 * without labels the two must write the same text, so an upgrade of Dokka that changes the
 * rendering, the `pageId` format or an icon's CSS class fails here instead of silently breaking
 * the `hierarchical-no-link` sidebar.
 */
class LabelledPackageNavigationPageSpec : FreeSpec({
    listOf(false, true).forEach { delayed ->
        "ラベルの無い木では、Dokka の NavigationPage と同じ文字列を書く（delayTemplateSubstitution = $delayed）" {
            val run = DokkaRunner.run(
                SOURCES,
                DokkaRunner.configuration(moduleName = "app", delayTemplateSubstitution = delayed),
                plugins = listOf(CopiedNavigationPlugin()),
            )
            val dokka = run.files.getValue("navigation.html")
            val copy = run.files.getValue("$COPY_NAME.html")

            // The tree has what the copy renders: Featured, nested packages, icons, strikethrough.
            dokka shouldContain "⭐️ Featured"
            dokka shouldContain "strikethrough"
            iconsOf(dokka) shouldContainAll listOf(
                "class-kt", "abstract-class-kt", "enum-class-kt", "annotation-class-kt", "interface-kt",
                "function", "exception-class", "object", "typealias-kt", "val", "var",
            )
            copy shouldBe dokka
        }
    }
})

private const val COPY_NAME = "navigation-copy"

/** The CSS classes of the icons in [html], without the shared `toc--icon`. */
private fun iconsOf(html: String): Set<String> =
    Regex("""class="toc--icon ([^"]+)"""").findAll(html).map { it.groupValues[1] }.toSet()

/**
 * After the sidebar is built, puts next to Dokka's `navigation.html` a `navigation-copy.html` that
 * [LabelledPackageNavigationPage] renders from the same tree.
 */
internal class CopiedNavigationPlugin : DokkaPlugin() {
    private val dokkaBase by lazy { plugin<DokkaBase>() }
    private val katachi by lazy { plugin<KatachiDokkaPlugin>() }

    internal val copiedNavigation by extending {
        dokkaBase.htmlPreprocessors providing { _ ->
            PageTransformer { input ->
                val navigation = input.children.filterIsInstance<NavigationPage>().single()
                val copy = LabelledPackageNavigationPage(navigation.root, navigation.moduleName, navigation.context)
                val strategy = copy.strategy as RenderingStrategy.Callback
                val page = RendererSpecificResourcePage(
                    COPY_NAME,
                    emptyList(),
                    RenderingStrategy.Callback { strategy.instructions(this, copy) },
                )
                input.modified(children = input.children + page)
            }
        } order { after(katachi.sidebarInstaller) }
    }

    @DokkaPluginApiPreview
    override fun pluginApiPreviewAcknowledgement(): PluginApiPreviewAcknowledgement = PluginApiPreviewAcknowledgement
}

/** One package with a declaration of each kind the sidebar gives its own icon, one of them featured. */
private val SOURCES: String = """
    |/src/main/kotlin/com/example/kinds/Kinds.kt
    |package com.example.kinds
    |
    |/**
    | * A class.
    | *
    | * @featured
    | */
    |public class Plain
    |
    |/** An abstract class. */
    |public abstract class Base
    |
    |/** An enum. */
    |public enum class Color { RED }
    |
    |/** An annotation. */
    |public annotation class Marker
    |
    |/** An interface. */
    |public interface Shape
    |
    |/** A function. */
    |public fun make(): Plain = Plain()
    |
    |/** An exception. */
    |public class Failure : Exception()
    |
    |/** An object. */
    |public object Single
    |
    |/** A type alias. */
    |public typealias Name = String
    |
    |/** A val. */
    |public val constant: Int = 1
    |
    |/** A var. */
    |public var mutable: Int = 1
    |
    |/** Deprecated. */
    |@Deprecated("Use Plain")
    |public class Old
    |
    |/src/main/kotlin/com/example/kinds/nested/Nested.kt
    |package com.example.kinds.nested
    |
    |/** In a sub-package. */
    |public class Inner
""".trimMargin()
