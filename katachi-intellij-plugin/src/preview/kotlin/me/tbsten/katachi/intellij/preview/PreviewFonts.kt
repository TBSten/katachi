@file:OptIn(ExperimentalComposeUiApi::class) // ImageComposeScene.semanticsOwners

package me.tbsten.katachi.intellij.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.platform.FontLoadResult
import org.jetbrains.jewel.foundation.DisabledAppearanceValues
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.createDefaultTextStyle
import org.jetbrains.jewel.intui.standalone.theme.dark
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.light
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Typeface
import org.jetbrains.skia.paragraph.FontCollection
import org.jetbrains.skia.paragraph.TypefaceFontProvider
import java.util.Collections
import java.util.IdentityHashMap

/**
 * The fonts the headless preview draws with, all bundled, so that a PNG has the same pixels on every
 * machine: Inter (from standalone Jewel) for Latin, and a subset of Noto Sans JP
 * (src/preview/resources/fonts/noto-sans-jp, built by scripts/preview-font/build_font.sh) for
 * Japanese.
 *
 * Without this, Compose draws a character Inter lacks with the OS's own font (Skia asks the OS before
 * the fonts an app registered), and the height of that line differs between macOS versions: the
 * preview's cut-off gate failed on CI while it passed locally.
 *
 * How: Compose hands Skia more than one family name only for a generic family such as
 * [FontFamily.SansSerif] (".AppleSystemUIFont", "Helvetica Neue", "Helvetica" on macOS), and Skia
 * looks those names up in the font collection's dynamic font manager before the OS. So
 * [PreviewTheme] draws the text in [FontFamily.SansSerif], and [install] registers Inter under its
 * first name and Noto Sans JP under the others. [FontFamily.Monospace] keeps the OS font for Latin
 * (changing it would change every English PNG) and gets Noto Sans JP for Japanese the same way.
 * TODO: Latin in FontFamily.Monospace still depends on the OS (SF Mono on macOS); bundle a monospace
 *  font (JetBrains Mono is in standalone Jewel) under Monospace's first name to remove that too.
 *
 * Example:
 * ```
 * ImageComposeScene(width, height, Density(1f)) {
 *     PreviewTheme(isDark = false) { KatachiToolWindowContent(ui, onIntent = {}) }
 * }
 * ```
 * Run `verifyPreview -Pkatachi.preview.noSystemFonts=true` to draw with the OS fonts switched off;
 * only text in [FontFamily.Monospace] may then differ from the golden.
 */
internal object PreviewFonts {
    private val interFiles = listOf("Thin", "ExtraLight", "Light", "Regular", "Medium", "SemiBold", "Bold", "ExtraBold", "Black")
        .flatMap { weight -> listOf("Inter-$weight", if (weight == "Regular") "Inter-Italic" else "Inter-${weight}Italic") }
        .map { "/fonts/inter/$it.ttf" }
    private val japaneseFiles = listOf("Regular", "SemiBold", "Bold").map { "/fonts/noto-sans-jp/NotoSansJP-$it.ttf" }

    private val inter: List<Typeface> by lazy { interFiles.map(::typefaceOf) }
    private val japanese: List<Typeface> by lazy { japaneseFiles.map(::typefaceOf) }

    /**
     * The platform's names of [FontFamily.SansSerif] and [FontFamily.Monospace], read through a
     * resolver of their own: resolving them through a scene's resolver would make its font
     * collection remember the OS fonts for them before [install] runs.
     */
    private val sansSerifNames: List<String> by lazy { namesOf(FontFamily.SansSerif) }
    private val monospaceNames: List<String> by lazy { namesOf(FontFamily.Monospace) }

    private val installed: MutableSet<FontCollection> = Collections.newSetFromMap(IdentityHashMap())

    /** True when the OS fonts are switched off (`-Pkatachi.preview.noSystemFonts=true`), to check that nothing needs them. */
    private val noSystemFonts: Boolean get() = System.getProperty("katachi.preview.noSystemFonts") == "true"

    /** Whether the bundled fonts have a glyph for [codePoint]: Inter, or the Noto Sans JP subset. */
    fun covers(codePoint: Int): Boolean =
        listOf(inter[interFiles.indexOf("/fonts/inter/Inter-Regular.ttf")], japanese.first()).any { it.getUTF32Glyph(codePoint) != 0.toShort() }

    /**
     * Points the font collection behind [resolver] at the bundled fonts. Must run before the scene
     * lays out any text, as Skia caches what a family name resolves to.
     */
    fun install(resolver: FontFamily.Resolver) {
        val collection = fontCollectionOf(resolver)
        synchronized(installed) { if (!installed.add(collection)) return }
        check(sansSerifNames.size >= 2) {
            "FontFamily.SansSerif resolves to $sansSerifNames on this platform; the preview needs a second name to put Noto Sans JP under."
        }
        val provider = TypefaceFontProvider()
        inter.forEach { provider.registerTypeface(it, sansSerifNames.first()) }
        (sansSerifNames.drop(1) + monospaceNames.drop(1)).distinct().forEach { name ->
            japanese.forEach { provider.registerTypeface(it, name) }
        }
        collection.setDynamicFontManager(provider)
        if (noSystemFonts) collection.setEnableFallback(false)

        val resolved = collection.findTypefaces(sansSerifNames.toTypedArray(), FontStyle.NORMAL).map { it?.familyName }
        check(resolved.size >= 2 && resolved[0] == "Inter" && resolved[1] == "Noto Sans JP") {
            "FontFamily.SansSerif ($sansSerifNames) resolves to $resolved, not to Inter then Noto Sans JP; the preview would draw with the OS fonts."
        }
    }

    private fun namesOf(family: FontFamily): List<String> {
        val resolved = createFontFamilyResolver().resolve(family).value
        val result = resolved as? FontLoadResult ?: error("$family resolved to $resolved, not a FontLoadResult; Compose changed, update PreviewFonts.namesOf")
        return result.aliases
    }

    private fun typefaceOf(resource: String): Typeface {
        val bytes = checkNotNull(PreviewFonts::class.java.getResourceAsStream(resource)) {
            "The preview font $resource is not on the classpath (scripts/preview-font/build_font.sh builds the Japanese ones)."
        }.use { it.readAllBytes() }
        return checkNotNull(FontMgr.default.makeFromData(Data.makeFromBytes(bytes))) { "Skia could not read the preview font $resource" }
    }

    /**
     * The font collection Compose lays text out with. Compose keeps it internal (the resolver's
     * platform font loader), so it is read by reflection; a Compose upgrade that moves it fails here.
     */
    private fun fontCollectionOf(resolver: FontFamily.Resolver): FontCollection {
        val loaderField = resolver.javaClass.declaredFields.firstOrNull { it.name == "platformFontLoader" }
            ?: error("${resolver.javaClass.name} has no platformFontLoader field; Compose changed, update PreviewFonts.fontCollectionOf")
        loaderField.isAccessible = true
        val loader = loaderField.get(resolver)
        val getter = loader.javaClass.methods.firstOrNull { it.name == "getFontCollection" && it.parameterCount == 0 }
            ?: error("${loader.javaClass.name} has no fontCollection; Compose changed, update PreviewFonts.fontCollectionOf")
        val collection = getter.invoke(loader)
        return collection as? FontCollection ?: error("${loader.javaClass.name}.fontCollection is $collection; Compose changed, update PreviewFonts.fontCollectionOf")
    }
}

/** [IntUiTheme] drawing its text with the bundled fonts of [PreviewFonts]. */
@Composable
internal fun PreviewTheme(isDark: Boolean, content: @Composable () -> Unit) {
    val resolver = LocalFontFamilyResolver.current
    remember(resolver) { PreviewFonts.install(resolver) }
    val theme = remember(isDark) {
        // Inter's line height and size stay; only the family changes, to the one PreviewFonts fills.
        val text = JewelTheme.createDefaultTextStyle().copy(fontFamily = FontFamily.SansSerif)
        if (isDark) {
            JewelTheme.darkThemeDefinition(defaultTextStyle = text, disabledAppearanceValues = DisabledAppearanceValues.dark())
        } else {
            JewelTheme.lightThemeDefinition(defaultTextStyle = text, disabledAppearanceValues = DisabledAppearanceValues.light())
        }
    }
    IntUiTheme(theme = theme, styling = ComponentStyling, content = content)
}

/** Every text [scene] laid out, with its font family, for [PreviewChecks.fontProblems]. */
internal fun drawnTextsOf(scene: ImageComposeScene): List<PreviewChecks.DrawnText> {
    val texts = mutableListOf<PreviewChecks.DrawnText>()

    fun walk(node: SemanticsNode) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        for (layout in layouts) {
            val input = layout.layoutInput
            val families = listOf(input.style.fontFamily) + input.text.spanStyles.map { it.item.fontFamily }
            for (family in families.distinct()) {
                // No family is the default one, which Compose resolves as FontFamily.SansSerif.
                val bundled = family == null || family == FontFamily.Default || family == FontFamily.SansSerif || family == FontFamily.Monospace
                texts += PreviewChecks.DrawnText(input.text.text, family.toString(), bundled)
            }
        }
        node.children.forEach(::walk)
    }
    scene.semanticsOwners.forEach { walk(it.unmergedRootSemanticsNode) }
    return texts
}
