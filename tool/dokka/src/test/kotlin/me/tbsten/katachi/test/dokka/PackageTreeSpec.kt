package me.tbsten.katachi.test.dokka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import me.tbsten.katachi.dokka.featured.FeaturedDeclarationKind
import me.tbsten.katachi.dokka.featured.FeaturedEntry
import me.tbsten.katachi.dokka.featured.FeaturedNavigation
import me.tbsten.katachi.dokka.navigation.KatachiDokkaDuplicatePackageNodeException
import me.tbsten.katachi.dokka.navigation.KatachiDokkaPackageNavigationException
import me.tbsten.katachi.dokka.navigation.PackageNavigation
import me.tbsten.katachi.dokka.navigation.isLabelOnly
import org.jetbrains.dokka.base.renderers.html.NavigationNode
import org.jetbrains.dokka.links.DRI

class PackageTreeSpec : FreeSpec({
    "実在しない中間のノードで子が1つのものは、子と1つのノードに畳む" {
        val root = PackageNavigation.HierarchicalModuleLink.nest(
            module(pkg("com.example.data"), pkg("com.example.data.local"), pkg("com.example.domain.model")),
        )
        root.outline() shouldBe listOf(
            "com.example",
            "  data",
            "    local",
            "  domain.model",
        )
        val domainModel = root.children.single().children[1]
        domainModel.dri.packageName shouldBe "com.example.domain.model"
    }

    "実在する package は、子の package が1つでも畳まない" {
        val root = PackageNavigation.HierarchicalModuleLink.nest(module(pkg("com.example.core"), pkg("com.example.core.util")))
        root.outline() shouldBe listOf("com.example.core", "  util")
        root.children.single().dri.packageName shouldBe "com.example.core"
    }

    "実在する package の子は、sub-package が先で自分の宣言が後ろ" {
        val core = pkg("sample", children = listOf(declaration("sample", "Zeta")))
        val root = PackageNavigation.HierarchicalModuleLink.nest(module(core, pkg("sample.dsl")))
        root.outline() shouldBe listOf("sample", "  dsl", "  Zeta")
    }

    "ルート package の [root] は階層に入れず、元の位置に残す" {
        val rootPackage = NavigationNode("[root]", DRI(packageName = ""), emptySet(), icon = null, children = emptyList())
        val root = PackageNavigation.HierarchicalModuleLink.nest(module(rootPackage, pkg("a.b"), pkg("a.c")))
        root.children.map { it.name } shouldContainExactly listOf("[root]", "a")
    }

    "package 以外のノードは、最初の package より前のものは前に、後ろのものは後ろに残る" {
        val before = declaration("x", "Before")
        val after = declaration("x", "After")
        val root = PackageNavigation.HierarchicalModuleLink.nest(module(before, pkg("a.b"), after, pkg("a.c")))
        root.children.map { it.name } shouldContainExactly listOf("Before", "a", "After")
    }

    "All Types のノード（.alltypes）は package として扱わず、元の位置に残す" {
        val allTypes = NavigationNode("All Types", DRI(packageName = ".alltypes"), emptySet(), icon = null, children = emptyList())
        val root = PackageNavigation.HierarchicalModuleLink.nest(module(pkg("a.b"), pkg("a.c"), allTypes))
        root.children.map { it.name } shouldContainExactly listOf("a", "All Types")
        root.children.last() shouldBeSameInstanceAs allTypes
    }

    "同じ package のノードが2つあれば、どちらかを黙って捨てずに弾く" {
        shouldThrow<KatachiDokkaDuplicatePackageNodeException> {
            PackageNavigation.HierarchicalModuleLink.nest(module(pkg("a.b"), pkg("a.b")))
        }.packageName shouldBe "a.b"
    }

    "Featured を足してから階層にしても、Featured はモジュールの先頭に残る" {
        val entry = FeaturedEntry("Entry", FeaturedDeclarationKind.Class, "a.b", DRI("a.b", "Entry"), emptySet(), summary = null)
        val withFeatured = FeaturedNavigation.withFeatured(module(pkg("a.b"), pkg("a.c")), "⭐️ Featured", listOf(entry))
        val root = PackageNavigation.HierarchicalModuleLink.nest(withFeatured)
        root.children.map { it.name } shouldContainExactly listOf("⭐️ Featured", "a")
    }

    "仮想ノードのリンク先" - {
        val module = module(pkg("com.example.a"), pkg("com.example.b"))

        "hierarchical-module-link ではモジュールのページ" {
            val virtual = PackageNavigation.HierarchicalModuleLink.nest(module).children.single()
            virtual.name shouldBe "com.example"
            virtual.dri shouldBe module.dri
            virtual.isLabelOnly shouldBe false
        }

        "hierarchical-no-link ではリンクしないラベルの印が付く" {
            val virtual = PackageNavigation.HierarchicalNoLink.nest(module).children.single()
            virtual.isLabelOnly shouldBe true
            virtual.dri.packageName shouldBe "com.example"
            virtual.children.map { it.isLabelOnly } shouldContainExactly listOf(false, false)
        }

        "flat では何も変えない" {
            PackageNavigation.Flat.nest(module) shouldBeSameInstanceAs module
        }
    }

    "package が無ければ元のノードをそのまま返す" {
        val module = module(declaration("x", "Only"))
        PackageNavigation.HierarchicalModuleLink.nest(module) shouldBeSameInstanceAs module
    }

    "設定の値からモードを選び、知らない値は選べる値を添えて弾く" {
        PackageNavigation.of("hierarchical-module-link") shouldBe PackageNavigation.HierarchicalModuleLink
        PackageNavigation.of("hierarchical-no-link") shouldBe PackageNavigation.HierarchicalNoLink
        PackageNavigation.of("flat") shouldBe PackageNavigation.Flat
        shouldThrow<KatachiDokkaPackageNavigationException> { PackageNavigation.of("tree") }.message shouldContain
            "\"hierarchical-module-link\", \"hierarchical-no-link\", \"flat\""
    }
})

private fun module(vararg children: NavigationNode): NavigationNode =
    NavigationNode("module", DRI(packageName = null), emptySet(), icon = null, children = children.toList())

private fun pkg(name: String, children: List<NavigationNode> = emptyList()): NavigationNode =
    NavigationNode(name, DRI(packageName = name), emptySet(), icon = null, children = children)

private fun declaration(packageName: String, name: String): NavigationNode =
    NavigationNode(name, DRI(packageName, name), emptySet(), icon = null, children = emptyList())

/** The names of the tree below [this], indented two spaces per level. */
private fun NavigationNode.outline(indent: String = ""): List<String> =
    children.flatMap { listOf(indent + it.name) + it.outline("$indent  ") }
