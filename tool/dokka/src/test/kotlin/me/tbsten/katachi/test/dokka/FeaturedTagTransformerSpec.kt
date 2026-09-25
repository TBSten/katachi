package me.tbsten.katachi.test.dokka

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dokka.featured.FeaturedMarker
import me.tbsten.katachi.dokka.featured.featuredMarker
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.doc.Text
import org.jetbrains.dokka.model.properties.WithExtraProperties
import org.jetbrains.dokka.model.withDescendants

class FeaturedTagTransformerSpec : FreeSpec({
    val run by lazy { DokkaRunner.run(FeaturedSources.ALL_KINDS) }
    val marked by lazy { run.markedDeclarations() }

    "@featured を付けた宣言" - {
        "クラス・関数・プロパティ・typealias・enum entry・入れ子のメンバに印が付く" {
            marked.map { it.first.name } shouldContainExactlyInAnyOrder listOf(
                "architecture",
                "Architecture",
                "assert",
                "assert",
                "formatVersion",
                "Definition",
                "STRICT",
                "DeclarationScope",
                "name",
            )
        }

        "タグの無い宣言には印が付かない" {
            marked.map { it.first.name } shouldNotContain "Untagged"
            marked.map { it.first.name } shouldNotContain "describe"
            marked.map { it.first.name } shouldNotContain "LENIENT"
        }

        "internal の宣言はドキュメントから外れるので、印も残らない" {
            marked.map { it.first.name } shouldNotContain "Hidden"
        }
    }

    "タグの後ろの文" - {
        "書いてあれば summaryOverride に入る" {
            val assert = marked.first { it.first.name == "assert" }.second
            val text = assert.summaryOverride.values.single().withDescendants()
                .filterIsInstance<Text>().joinToString("") { it.body }
            text shouldBe "Runs every check and throws AssertionError on violations."
        }

        "無ければ summaryOverride は空" {
            marked.first { it.first.name == "Architecture" }.second
                .summaryOverride.shouldBeEmpty()
        }
    }
})

/** Every declaration of the run that carries a [FeaturedMarker], with its marker. */
private fun DokkaRun.markedDeclarations(): List<Pair<Documentable, FeaturedMarker>> {
    val root: Documentable = module
    return root.withDescendants()
        .mapNotNull { documentable ->
            (documentable as? WithExtraProperties<*>)?.extra?.featuredMarker?.let { documentable to it }
        }
        .toList()
}
