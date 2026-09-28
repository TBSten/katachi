package me.tbsten.katachi.intellij.uitest.generate

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary

/** One thing the user or the IDE does around the generation of two files (0 and 1). */
internal sealed interface FlowOp {
    /** The user presses [Generate] for the file. */
    data class Start(val file: Int) : FlowOp

    /** The re-read of the templates finishes (it was waiting for Gradle's JSON task). */
    data class ReleaseReload(val file: Int) : FlowOp

    /** `katachiTemplate` finishes. */
    data class FinishGradle(val file: Int, val succeeds: Boolean) : FlowOp

    /** The project closes while the generation is running. */
    data class Cancel(val file: Int) : FlowOp

    /** Something else writes the file on disk; an editor that shows it unchanged follows. */
    data class ExternalWrite(val file: Int, val text: String) : FlowOp

    /** The user types into the open editor and does not save. */
    data class TypeUnsaved(val file: Int, val text: String) : FlowOp

    data class Save(val file: Int) : FlowOp

    data class Delete(val file: Int) : FlowOp

    /** The definition changed: what the next re-read of the templates answers. */
    data class SetCatalog(val mode: CatalogMode) : FlowOp

    /** An IDE call ("mkdirs", "provisional", "saveDocument") starts or stops throwing. */
    data class ToggleIdeFailure(val effect: String) : FlowOp
}

/** Texts of a file: none has content but the ones the generator marks; [isContent] is the rule. */
internal val FLOW_TEXTS: List<String> = listOf("", " \n", "// note\n", "package a.b\n", "class A\n", "import x.Y\n", "// x\nclass A\n")

internal val IDE_FAILURES: List<String> = listOf("mkdirs", "provisional", "saveDocument")

/** Mostly the flow itself and the user's edits, now and then the IDE-wide changes. */
internal fun flowOpArb(): Arb<FlowOp> = arbitrary { rs ->
    val r = rs.random
    val file = r.nextInt(FILE_COUNT)
    when (r.nextInt(100)) {
        in 0..21 -> FlowOp.Start(file)
        in 22..35 -> FlowOp.ReleaseReload(file)
        in 36..49 -> FlowOp.FinishGradle(file, r.nextInt(10) < 6)
        in 50..54 -> FlowOp.Cancel(file)
        in 55..62 -> FlowOp.ExternalWrite(file, FLOW_TEXTS[r.nextInt(FLOW_TEXTS.size)])
        in 63..71 -> FlowOp.TypeUnsaved(file, FLOW_TEXTS[r.nextInt(FLOW_TEXTS.size)])
        in 72..77 -> FlowOp.Save(file)
        in 78..84 -> FlowOp.Delete(file)
        in 85..93 -> FlowOp.SetCatalog(CatalogMode.entries[if (r.nextInt(3) == 0) r.nextInt(3) else 0])
        else -> FlowOp.ToggleIdeFailure(IDE_FAILURES[r.nextInt(IDE_FAILURES.size)])
    }
}
