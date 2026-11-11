package me.tbsten.katachi.test.architecture.tool

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.konsist
import me.tbsten.katachi.test.architecture.KDOC_TAG_ORDER_RULE
import me.tbsten.katachi.test.architecture.keepsBlockTagsLast
import me.tbsten.katachi.test.architecture.publicDeclarationsOf

private const val DOKKA_MAIN_PACKAGE: String = "me/tbsten/katachi/dokka"

fun DeclarationContainerScope.dokkaPlugin() = "DokkaPlugin".role {
    title = "Dokka プラグイン"
    summary = "API リファレンスに Featured（ページの節とサイドバー）・サイドバーの package の階層表示と、AI エージェント向けの llms.txt・ページごとの Markdown を足す、このリポジトリ専用の Dokka プラグイン"
    description = """
        ライブラリの層ではなく、公開もしないツールなので、層のルールは持たず、`KDOC_TAG_ORDER_RULE` だけを当てます。
        サブパッケージは `**` を使わず 1 つずつ挙げているので、増やすときはここに足します。
    """.trimIndent()
    example("KatachiDokkaPlugin.kt", "拡張の登録だけを持つプラグイン本体")
    example("FeaturedTagTransformer.kt", "@featured の付いた宣言に印を付ける")
    example("ModuleFragmentStrategy.kt", "モジュールごとの run が残した断片を、束ねる run で回収する")
    example("LlmsModuleInstaller.kt", "モジュールの出力に llms.txt と llms-full.txt を足す")
    layout {
        "tool/dokka" {
            publicDeclarationsKeepTagsLast()
            mainSourceSet / kotlin / DOKKA_MAIN_PACKAGE / "*".ktFile()
            mainSourceSet / kotlin / DOKKA_MAIN_PACKAGE / "featured" / "*".ktFile()
            mainSourceSet / kotlin / DOKKA_MAIN_PACKAGE / "llms" / "*".ktFile()
            mainSourceSet / kotlin / DOKKA_MAIN_PACKAGE / "navigation" / "*".ktFile()
            // Implementation parts shared across features (collected here rather than under
            // whichever feature happened to introduce them first): "internal" is this module's
            // own domain split, not katachi's `.internal`-for-internal-declarations convention
            // (see `publicDeclarationsKeepTagsLast` — that convention does not apply here).
            mainSourceSet / kotlin / DOKKA_MAIN_PACKAGE / "internal" / "featured" / "*".ktFile()
            mainSourceSet / kotlin / DOKKA_MAIN_PACKAGE / "internal" / "fragment" / "*".ktFile()
            mainSourceSet / kotlin / DOKKA_MAIN_PACKAGE / "internal" / "link" / "*".ktFile()
            mainSourceSet / kotlin / DOKKA_MAIN_PACKAGE / "internal" / "markdown" / "*".ktFile()
            mainSourceSet / kotlin / DOKKA_MAIN_PACKAGE / "internal" / "page" / "*".ktFile()
            mainSourceSet / kotlin / DOKKA_MAIN_PACKAGE / "internal" / "summary" / "*".ktFile()
            // How Dokka finds the plugin: `ServiceLoader` over the plugin classpath.
            mainSourceSet / "resources" / "META-INF/services" / "org.jetbrains.dokka.plugability.DokkaPlugin".file()
        }
    }
}

// Written here rather than in a shared file: `konsist { }` captures the first frame outside
// katachi as its declaration site, so a shared wrapper would make every role that declares it
// report this same line.
private fun LayoutScope.publicDeclarationsKeepTagsLast() =
    KDOC_TAG_ORDER_RULE.konsist {
        files.flatMap(::publicDeclarationsOf).must(::keepsBlockTagsLast)
    }
