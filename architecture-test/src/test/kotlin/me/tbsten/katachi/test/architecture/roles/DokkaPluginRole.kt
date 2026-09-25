package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.test.architecture.mainPackage

/**
 * The role of `:tool:dokka`, the Dokka plugin behind this repository's own API reference.
 *
 * It is tooling rather than a layer of the library: nothing depends on it but the Dokka runs of
 * `generateApiDocs`, and it is never published. So it carries none of the layer rules of the
 * `library` group, and it sits in the undocumented `tool` group.
 *
 * Each subpackage is listed rather than matched with `**`, so a new one has to be declared here
 * on purpose.
 */
fun DeclarationContainerScope.dokkaPlugin() = "DokkaPlugin" {
    title = "Dokka プラグイン"
    summary = "API リファレンスに Featured（ページの節とサイドバー）と、AI エージェント向けの llms.txt・ページごとの Markdown を足す、このリポジトリ専用の Dokka プラグイン"
    example("KatachiDokkaPlugin.kt", "拡張の登録だけを持つプラグイン本体")
    example("FeaturedTagTransformer.kt", "@featured の付いた宣言に印を付ける")
    example("ModuleFragmentStrategy.kt", "モジュールごとの run が残した断片を、束ねる run で回収する")
    example("LlmsModuleInstaller.kt", "モジュールの出力に llms.txt と llms-full.txt を足す")
    layout {
        ":tool:dokka".module {
            mainSourceSet / kotlin / mainPackage / "*".ktFile()
            mainSourceSet / kotlin / mainPackage / "featured" / "*".ktFile()
            mainSourceSet / kotlin / mainPackage / "fragment" / "*".ktFile()
            mainSourceSet / kotlin / mainPackage / "llms" / "*".ktFile()
            mainSourceSet / kotlin / mainPackage / "llms" / "markdown" / "*".ktFile()
            mainSourceSet / kotlin / mainPackage / "llms" / "page" / "*".ktFile()
            mainSourceSet / kotlin / mainPackage / "llms" / "summary" / "*".ktFile()
            // How Dokka finds the plugin: `ServiceLoader` over the plugin classpath.
            mainSourceSet / "resources" / "META-INF/services" / "org.jetbrains.dokka.plugability.DokkaPlugin".file()
        }
    }
}
