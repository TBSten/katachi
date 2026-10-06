package me.tbsten.katachi.test.konsist.declarations

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import me.tbsten.katachi.dsl.KatachiGlobSyntaxException
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentPatternException
import me.tbsten.katachi.konsist.declarations.KtFileContentPatternKind
import me.tbsten.katachi.konsist.declarations.KtFileContentScope
import me.tbsten.katachi.konsist.declarations.internal.canonicalType

/**
 * The patterns of a line: what is refused, and with which [KtFileContentPatternKind]; how a type is
 * put in canonical form; how a pattern matches.
 */

private fun patternErrorOf(block: KtFileContentScope.() -> Unit): KatachiKtFileContentPatternException =
    shouldThrow<KatachiKtFileContentPatternException> { rulesOf(block = block) }

class KtFileContentPatternSpec : FreeSpec({
    "読めないパターン" - {
        "名前の閉じない [ {・空のクラス・空のパターンは Name" {
            patternErrorOf { `class`("Foo[") }.what shouldBe KtFileContentPatternKind.Name
            patternErrorOf { `fun`("{on,{do}}") }.what shouldBe KtFileContentPatternKind.Name
            patternErrorOf { `val`("") }.what shouldBe KtFileContentPatternKind.Name
            patternErrorOf { `class`("H") { `fun`("[]") } }.what shouldBe KtFileContentPatternKind.Name
        }

        "import と package の空の区切り・区切りをまたぐ {..}・空のパターン" {
            patternErrorOf { `import`("a..b") }.what shouldBe KtFileContentPatternKind.Import
            patternErrorOf { `import`("a.{b.c}") }.what shouldBe KtFileContentPatternKind.Import
            patternErrorOf { not.`import`("") }.what shouldBe KtFileContentPatternKind.Import
            patternErrorOf { `package`("com..example") }.what shouldBe KtFileContentPatternKind.Package
            patternErrorOf { not.`package`("com.**x") }.what shouldBe KtFileContentPatternKind.Package
        }

        "型・親の型・アノテーション・except" {
            patternErrorOf { `val`("x", "Map<{K, V>") }.what shouldBe KtFileContentPatternKind.Type
            patternErrorOf { `fun`("x")("[z-a]") }.what shouldBe KtFileContentPatternKind.Type
            patternErrorOf { `fun`("x").returns("") }.what shouldBe KtFileContentPatternKind.Type
            patternErrorOf { `class`("A").superclass("Base{T") }.what shouldBe KtFileContentPatternKind.Superclass
            patternErrorOf { annotated("Pre[view").`fun`("x") }.what shouldBe KtFileContentPatternKind.Annotation
            patternErrorOf { annotated("androidx..Composable").`fun`("x") }.what shouldBe KtFileContentPatternKind.Annotation
            patternErrorOf { not.`class`("*").except("a[b") }.what shouldBe KtFileContentPatternKind.Except
            patternErrorOf { not.`import`("a.**").except("a..b") }.what shouldBe KtFileContentPatternKind.Except
        }

        "文面は書いたパターン・宣言元・glob の理由・直し方の順" {
            val error = patternErrorOf { `import`("a..b") }

            error.pattern shouldBe "a..b"
            error.cause.shouldBeInstanceOf<KatachiGlobSyntaxException>()
            error.declaredAt.fileName shouldBe "KtFileContentPatternSpec.kt"
            val lines = error.message.orEmpty().lines()
            lines.first() shouldBe "The import pattern \"a..b\" in ktFileContent { } at ${error.declaredAt} cannot be read."
            lines[1] shouldBe error.cause?.message?.lines()?.first()
            lines.last() shouldBe "Escape a literal *, ?, [, ], {, }, , or \\ with \\."
            error.message.orEmpty().filter { it.code > 127 } shouldBe ""
        }
    }

    "新しい構文" - {
        "名前の ? [..] {..} は読める" {
            val name = rulesOf { `fun`("on{Click,Tap}") }.firstDeclaration.name
            name.matches("onClick") shouldBe true
            name.matches("onTap") shouldBe true
            name.matches("onDrag") shouldBe false
            rulesOf { `class`("Foo?") }.firstDeclaration.name.matches("FooA") shouldBe true
            rulesOf { `class`("[A-Z]*") }.firstDeclaration.name.matches("Home") shouldBe true
            rulesOf { `class`("[A-Z]*") }.firstDeclaration.name.matches("home") shouldBe false
        }

        "import の {..} は区切りの中で選べる" {
            val path = rulesOf { `import`("a.{b,c}") }.imports.single().path
            path.matches("a.b") shouldBe true
            path.matches("a.c") shouldBe true
            path.matches("a.d") shouldBe false
        }
    }

    "型" - {
        "型の ? は nullable の印で、エラーにならず文字として当たる" {
            val rules = rulesOf {
                `val`("name", "String?")
                `fun`("find")("List<String?>").returns("Int?")
                `class`("A").superclass("Repository<User?>")
            }
            val declarations = rules.declarations?.lines.orEmpty()

            declarations[0].propertyType?.canonical?.matches("String?") shouldBe true
            declarations[0].propertyType?.canonical?.matches("String") shouldBe false
            declarations[1].parameters?.single()?.canonical?.matches("List<String?>") shouldBe true
            declarations[1].returnType?.canonical?.matches("Int?") shouldBe true
            declarations[2].superclasses.single().pattern.matches("Repository<User?>") shouldBe true
        }

        "型はパッケージを外し空白を詰めて比べ、* は何にでも当たる" {
            canonicalType("kotlinx.coroutines.flow.StateFlow<com.example.HomeUiState>") shouldBe "StateFlow<HomeUiState>"
            canonicalType("Map.Entry<K, V>") shouldBe "Map.Entry<K,V>"
            canonicalType("(String) -> Unit") shouldBe "(String)->Unit"
            canonicalType("kotlin.collections.List<*>") shouldBe "List<*>"

            val type = rulesOf { `val`("uiState", "kotlinx.coroutines.flow.StateFlow< * >") }.firstDeclaration.propertyType
            type?.written shouldBe "kotlinx.coroutines.flow.StateFlow< * >"
            type?.canonical?.pattern shouldBe "StateFlow<*>"
            type?.canonical?.matches("StateFlow<HomeUiState>") shouldBe true
            type?.canonical?.matches("StateFlow<*>") shouldBe true
            type?.canonical?.matches("StateFlow") shouldBe false
        }

        "親の型は型引数を書かなければ名前で比べる" {
            val superclasses = rulesOf {
                `class`("A").superclass("Repository", "Repository<User>", "com.example.Base")
            }.firstDeclaration.superclasses

            superclasses.map { it.byName } shouldBe listOf(true, false, true)
            superclasses.map { it.pattern.pattern } shouldBe listOf("Repository", "Repository<User>", "Base")
        }
    }

    "名前と区切り" - {
        "名前の * は空を含めて何にでも当たり、import の * は 1 区切り、** はまたぐ" {
            val rules = rulesOf {
                `import`("kotlinx.coroutines.*")
                `import`("kotlinx.**")
                `class`("*Impl")
            }

            rules.imports[0].path.matches("kotlinx.coroutines.launch") shouldBe true
            rules.imports[0].path.matches("kotlinx.coroutines.flow.Flow") shouldBe false
            rules.imports[1].path.matches("kotlinx.coroutines.flow.Flow") shouldBe true
            rules.firstDeclaration.name.matches("Foo.BarImpl") shouldBe true
            rules.firstDeclaration.name.matches("Impl") shouldBe true
        }

        "アノテーションは . を含めば完全修飾名、含まなければ単純名で比べる" {
            val annotations = rulesOf {
                annotated("Composable", "androidx.compose.runtime.*").`fun`("x")
            }.firstDeclaration.shapes.single().modifiers.annotations

            annotations.map { it.qualified } shouldBe listOf(false, true)
            annotations[1].pattern.matches("androidx.compose.runtime.Composable") shouldBe true
            annotations[1].pattern.matches("androidx.compose.ui.Modifier") shouldBe false
        }

        "大文字と小文字を区別する" {
            rulesOf { `class`("Home") }.firstDeclaration.name.matches("home") shouldBe false
        }
    }
})
