package me.tbsten.katachi.test.konsist.declarations

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentPatternException
import me.tbsten.katachi.konsist.declarations.KtFileContentPatternKind

/**
 * The `How to fix:` of a failure, where it says more than the general shape: a kind written with
 * `not.` lines only, a missing line whose name a declaration of another shape has, and the code of
 * a name the glob would read as a pattern. Also the patterns refused before they mislead.
 */

class KtFileContentFixesSpec : FreeSpec({
    "not. の行しか無い種類は、残りを許す行を最初の直し方に出す" - {
        "import" {
            fixLinesOf("package com.example\n\nimport kotlinx.coroutines.flow.Flow\n\nclass Sample\n") {
                `package`("**")
                not.`import`("android.**")
            }.first() shouldBe "import kotlinx.coroutines.flow.Flow: Only not. lines are written for imports, so every other one is " +
                "rejected too. To allow the rest, add at $SPEC_SITE: / `import`(\"**\")"
        }

        "宣言" {
            fixLinesOf("package com.example\n\nclass Sample\n") {
                `package`("**")
                not.allow(`class`, "*Impl")
            }.first() shouldBe
                "Sample: Only not. lines are written for declarations, so every other one is rejected too. " +
                "To allow the rest, add at $SPEC_SITE: / allow(anythingElse)"
        }

        "許可の行が 1 つでもあれば出さない" {
            fixLinesOf("package com.example\n\nimport a.B\n\nclass Sample\n") {
                `package`("**")
                `import`("kotlin.**")
                not.`import`("android.**")
                required(`class`, fileName)
            }.none { "Only not. lines" in it } shouldBe true
        }
    }

    "名前の当たる宣言が別の形で許されていたら、その宣言の形を直す直し方を出す" {
        var line: DeclarationSite? = null
        fixLinesOf("package com.example\n\ninterface SampleAction\n") {
            `package`("**")
            line = DeclarationSite.capture(); required(sealed, `interface`, "SampleAction")
            allow(anythingElse)
        } shouldBe listOf(
            "SampleAction: Change the declaration at line 3 to: sealed interface SampleAction",
            "SampleAction: Or change the line at $line if the declaration is right",
            "SampleAction: If some files do not need it, write allow(...) in place of required(...) at $line",
        )
    }

    "glob の文字を含む名前は、エスケープしたパターンとして出し、貼ればその名前だけに当たる" {
        val source = "package com.example\n\nclass Sample\n\nfun `is it {ok}?`() {}\nfun `a*b`() {}\n"
        val fixes = fixLinesOf(source) {
            `package`("**")
            required(`class`, fileName)
        }
        fixes.filter { "allow(`fun`" in it }.map { it.substringAfterLast(" / ") } shouldBe listOf(
            "allow(`fun`, \"is it \\\\{ok\\\\}\\\\?\")",
            "allow(`fun`, \"a\\\\*b\")",
        )
        // Pasted back, the code compiles and allows exactly those two.
        judged(source) {
            `package`("**")
            required(`class`, fileName)
            allow(`fun`, "is it \\{ok\\}\\?")
            allow(`fun`, "a\\*b")
        }.shouldBeEmpty()
        judged("package com.example\n\nclass Sample\n\nfun axxb() {}\n") {
            `package`("**")
            required(`class`, fileName)
            allow(`fun`, "a\\*b")
        }.size shouldBe 1
    }

    "型のパターンに package と * を書くと、何にでも当たる代わりに例外" {
        val error = shouldThrow<KatachiKtFileContentPatternException> { rulesOf { required(`val`, "x").type("com.example.*") } }
        error.what shouldBe KtFileContentPatternKind.Type
        error.message shouldContain "Types are compared without their package"
        shouldThrow<KatachiKtFileContentPatternException> { rulesOf { required(`class`, fileName).superclass("com.example.*") } }
            .what shouldBe KtFileContentPatternKind.Superclass
        // A type argument's wildcard is not a package's.
        rulesOf { required(`val`, "x").type("kotlinx.coroutines.flow.StateFlow<*>") }
    }
})
