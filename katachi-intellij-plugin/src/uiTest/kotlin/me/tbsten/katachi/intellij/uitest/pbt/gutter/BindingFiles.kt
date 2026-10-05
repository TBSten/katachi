package me.tbsten.katachi.intellij.uitest.pbt.gutter

import me.tbsten.katachi.intellij.model.DeclarationModel
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DeclarationVerdict
import me.tbsten.katachi.intellij.model.RuleRef
import me.tbsten.katachi.intellij.model.SourceDeclarationKind
import me.tbsten.katachi.intellij.presentation.gutter.BoundExplanation
import me.tbsten.katachi.intellij.presentation.gutter.SourceDeclaration
import kotlin.random.Random

/** A file's top-level declarations in line order, as katachi saw them when it explained the file. */
internal data class BindingFile(val declarations: List<SourceDeclaration>) {
    /** katachi's explanation of each declaration, in the same order; the n-th one's rule is `#n`, so a test sees which bound. */
    fun explanations(): List<DeclarationModel> = declarations.mapIndexed { index, it ->
        DeclarationModel(it.kind, it.name, it.line, DeclarationVerdict.Allowed, listOf(RuleRef("#$index", DeclarationSiteRef("Role.kt", index + 1))), null)
    }

    fun tracked(): TrackedFile = TrackedFile(declarations.mapIndexed { index, it -> TrackedDeclaration(it, index) })
}

/** A declaration of an edited file, and which explained one it was ([origin]), `null` for one the edits made. */
internal data class TrackedDeclaration(val declaration: SourceDeclaration, val origin: Int?)

internal data class TrackedFile(val entries: List<TrackedDeclaration>)

/** The model a binding holds; a binding that says "not computed" fails the test. */
internal val BoundExplanation.model: DeclarationModel
    get() = (this as? BoundExplanation.Explained)?.declaration ?: throw AssertionError("not bound to an explanation: $this")

/** Random files: up to 12 declarations of every kind, overloaded functions among them when asked. */
internal object BindingFiles {
    private val names = listOf("HealthService", "HealthRepository", "load", "save", "DEFAULT", "Id", "Mapper", "toDomain")

    fun random(random: Random, overloads: Boolean): BindingFile {
        val count = random.nextInt(1, 13)
        var line = random.nextInt(1, 6)
        val used = mutableSetOf<Pair<SourceDeclarationKind, String>>()
        val declarations = mutableListOf<SourceDeclaration>()
        repeat(count) { index ->
            val kind = SourceDeclarationKind.known[random.nextInt(SourceDeclarationKind.known.size)]
            val base = names[random.nextInt(names.size)]
            val name = if (overloads && kind == SourceDeclarationKind.Fun && (kind to base) in used) base else uniqueName(base, index, kind, used)
            used += kind to name
            declarations += SourceDeclaration(kind, name, line)
            line += random.nextInt(1, 8)
        }
        return BindingFile(declarations)
    }

    private fun uniqueName(base: String, index: Int, kind: SourceDeclarationKind, used: Set<Pair<SourceDeclarationKind, String>>): String =
        if ((kind to base) in used) "$base$index" else base
}

/** One edit of a file after it was explained. */
internal data class BindingEdit(val name: String, val apply: (TrackedFile) -> TrackedFile)

/** The edits a user makes between two refreshes of the definition. */
internal object BindingEdits {
    private var counter = 0

    fun random(random: Random, file: TrackedFile): BindingEdit {
        val entries = file.entries
        val at = if (entries.isEmpty()) 0 else random.nextInt(entries.size)
        return when (if (entries.isEmpty()) 0 else random.nextInt(6)) {
            0 -> add(at, random)
            1 -> blankLines(at, random.nextInt(1, 15))
            2 -> BindingEdit("remove #$at") { it.copy(entries = it.entries.filterIndexed { index, _ -> index != at }) }
            3 -> change("rename #$at", at) { it.copy(name = it.name + "Renamed${counter++}") }
            4 -> change("change the kind of #$at", at) { declaration ->
                declaration.copy(kind = SourceDeclarationKind.known.first { it != declaration.kind })
            }
            else -> overload(at)
        }
    }

    private fun add(at: Int, random: Random): BindingEdit {
        val kind = SourceDeclarationKind.known[random.nextInt(SourceDeclarationKind.known.size)]
        val name = "Added${counter++}"
        return BindingEdit("add $kind $name at #$at") { file ->
            val line = file.entries.getOrNull(at)?.declaration?.line ?: ((file.entries.lastOrNull()?.declaration?.line ?: 0) + 2)
            val shifted = shiftFrom(file, at, 2)
            shifted.copy(entries = shifted.entries.toMutableList().apply { add(at.coerceAtMost(size), TrackedDeclaration(SourceDeclaration(kind, name, line), null)) })
        }
    }

    private fun blankLines(at: Int, count: Int) = BindingEdit("$count blank lines above #$at") { shiftFrom(it, at, count) }

    private fun change(name: String, at: Int, edit: (SourceDeclaration) -> SourceDeclaration) = BindingEdit(name) { file ->
        file.copy(entries = file.entries.mapIndexed { index, it -> if (index == at) TrackedDeclaration(edit(it.declaration), null) else it })
    }

    /** Another function of the same name right after #at (only when #at is a function; else a blank line). */
    private fun overload(at: Int) = BindingEdit("overload #$at") { file ->
        val target = file.entries[at].declaration
        if (target.kind != SourceDeclarationKind.Fun) return@BindingEdit shiftFrom(file, at, 1)
        val shifted = shiftFrom(file, at + 1, 2)
        shifted.copy(entries = shifted.entries.toMutableList().apply { add(at + 1, TrackedDeclaration(target.copy(line = target.line + 1), null)) })
    }

    private fun shiftFrom(file: TrackedFile, at: Int, lines: Int): TrackedFile =
        file.copy(entries = file.entries.mapIndexed { index, it -> if (index >= at) it.copy(declaration = it.declaration.copy(line = it.declaration.line + lines)) else it })
}
