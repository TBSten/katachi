package me.tbsten.katachi.test.dokka

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dokka.llms.markdown.SignatureText
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.properties.PropertyContainer
import org.jetbrains.dokka.pages.ContentBreakLine
import org.jetbrains.dokka.pages.ContentDRILink
import org.jetbrains.dokka.pages.ContentGroup
import org.jetbrains.dokka.pages.ContentKind
import org.jetbrains.dokka.pages.ContentNode
import org.jetbrains.dokka.pages.ContentText
import org.jetbrains.dokka.pages.DCI
import org.jetbrains.dokka.pages.Kind

class SignatureTextSpec : FreeSpec({
    "最初の Symbol の文字をつなぎ、改行は改行のまま、連続する空白は1つにする" {
        val signature = group(
            ContentKind.Symbol,
            text("@Deprecated"),
            ContentBreakLine(emptySet()),
            text("fun  "),
            ContentDRILink(listOf(text("add")), DRI("shop", "Cart"), dci(ContentKind.Main), emptySet()),
            text("(item: Item)"),
        )
        val other = group(ContentKind.Symbol, text("fun other()"))

        SignatureText.of(listOf(group(ContentKind.Main, signature, other))) shouldBe "@Deprecated\nfun add(item: Item)"
    }

    "1行にすると改行も空白になる" {
        SignatureText.oneLine("@Deprecated\nfun add()") shouldBe "@Deprecated fun add()"
    }

    "Symbol が無ければ null" {
        SignatureText.of(listOf(group(ContentKind.Main, text("no signature")))).shouldBeNull()
    }
})

private fun dci(kind: Kind) = DCI(setOf(DRI.topLevel), kind)

private fun text(value: String) = ContentText(value, dci(ContentKind.Main), emptySet())

private fun group(kind: Kind, vararg children: ContentNode) =
    ContentGroup(children.toList(), dci(kind), emptySet(), emptySet(), PropertyContainer.empty())
