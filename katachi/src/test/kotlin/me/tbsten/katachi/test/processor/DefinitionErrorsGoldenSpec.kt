package me.tbsten.katachi.test.processor

import io.kotest.core.spec.style.FreeSpec
import java.io.File
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.KatachiTemplateOnWildcardException
import me.tbsten.katachi.processor.internal.DefinitionErrorEntry
import me.tbsten.katachi.processor.internal.DefinitionErrorPhase
import me.tbsten.katachi.processor.internal.writeDefinitionErrors
import me.tbsten.katachi.test.ide.checkDefinitionGolden
import me.tbsten.katachi.test.ide.maskVolatile
import me.tbsten.katachi.test.template.withTempProject

/** The text of `definition-errors.json` (K-10 of the IDE definition authoring plan): key order, line breaks, `null`. */
class DefinitionErrorsGoldenSpec : FreeSpec({
    "書式" - {
        "キーの順・複数行の message・null の declaredAt・空の errors" {
            withTempProject { root ->
                val output = File(root, "definition-errors.json")
                val located = KatachiTemplateOnWildcardException(
                    role = "data.Repository",
                    path = "repository/*Repository.kt",
                    declaredAt = DeclarationSite("ScreenRole.kt", 4),
                )
                val unlocated = MultilineException("first line\nsecond \"quoted\" line with a \\ backslash").apply {
                    stackTrace = arrayOf(StackTraceElement("me.tbsten.katachi.dsl.Group", "group", "Group.kt", 10))
                }

                writeDefinitionErrors(
                    path = output.path,
                    entries = listOf(
                        DefinitionErrorEntry(DefinitionErrorPhase.Processor, "internalDefinitionJson", located),
                        DefinitionErrorEntry(DefinitionErrorPhase.EntryPoint, null, ExceptionInInitializerError(unlocated)),
                    ),
                    warn = {},
                )
                checkDefinitionGolden("definition-errors.json", maskVolatile(output.readText()))

                writeDefinitionErrors(path = output.path, entries = emptyList(), warn = {})
                checkDefinitionGolden("definition-errors-empty.json", maskVolatile(output.readText()))
            }
        }
    }
})

@OptIn(ExperimentalKatachiApi::class)
internal class MultilineException(message: String) : KatachiDeclarationException(message)
