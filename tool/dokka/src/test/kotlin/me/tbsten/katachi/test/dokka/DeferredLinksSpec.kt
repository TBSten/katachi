package me.tbsten.katachi.test.dokka

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dokka.internal.link.DeferredLinks
import me.tbsten.katachi.dokka.internal.link.LinkedPage
import org.jetbrains.dokka.links.Callable
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.TypeConstructor

class DeferredLinksSpec : FreeSpec({
    val known = DRI(
        packageName = "alpha.api",
        classNames = "Entry",
        callable = Callable("open", params = listOf(TypeConstructor("kotlin.String", emptyList()))),
    )
    val other = DRI(packageName = "nowhere", classNames = "Missing")

    "モジュールの run が残した DRI は、束ねる run で同じ DRI として読み戻される" {
        var seen: DRI? = null
        DeferredLinks.rewrite(DeferredLinks.driLink("Entry.open", known), pathLink = { it }) { dri, _ ->
            seen = dri
            "../alpha/x.html"
        }.text shouldBe "[Entry.open](../alpha/x.html)"
        // Jackson makes a new PointingToDeclaration, so the DRIs are equal in what they say, not by ==.
        seen.toString() shouldBe known.toString()
    }

    "解決できない DRI はラベルだけになり、その DRI が返される" {
        val result = DeferredLinks.rewrite("See ${DeferredLinks.driLink("Missing", other)}.", pathLink = { it }) { _, _ -> null }
        result.text shouldBe "See Missing."
        result.unresolved shouldContainExactly listOf(other.toString())
    }

    "Markdown 版を指す DRI は、その印とともに読み戻される" {
        var seen: LinkedPage? = null
        DeferredLinks.rewrite(DeferredLinks.driLink("Entry", known, LinkedPage.Markdown), pathLink = { it }) { _, page ->
            seen = page
            "../alpha/x.html.md"
        }.text shouldBe "[Entry](../alpha/x.html.md)"
        seen shouldBe LinkedPage.Markdown
    }

    "自分のモジュールのパスは pathLink の答えに置き換わり、置換文字列の特殊文字もそのまま出る" {
        val text = "- [Cart](${DeferredLinks.path("shop/-cart/index.html")})"
        DeferredLinks.rewrite(text, pathLink = { "https://example.com/\$1/$it" }) { _, _ -> null }.text shouldBe
            "- [Cart](https://example.com/\$1/shop/-cart/index.html)"
    }
})
