package me.tbsten.katachi.intellij.model

/**
 * The explanation of one `.kt` file's top-level declarations under a `ktFileContent { }`
 * constraint (design 1.7.2): which line of the allow list let each one in. Written by katachi's
 * own judgement, so it cannot disagree with the check.
 */
internal data class FileDeclarationsModel(
    /** Relative to the project root, `/` separated; the same spelling as a layout entry's `matchedFiles`. */
    val file: String,
    /**
     * Whether the top-level declarations were judged: `false` only when the file could not be
     * explained ([unexplainedReason]). A block with no declaration line still judges them, allowing none.
     */
    val declarationsChecked: Boolean,
    val declarations: List<DeclarationModel>,
    /** The required lines no declaration matched. */
    val missing: List<RuleRef>,
    /** Why the block could not be judged for this file; [declarations] is empty then. */
    val unexplainedReason: String?,
)

internal data class DeclarationModel(
    val kind: SourceDeclarationKind,
    val name: String,
    /** 1-based; the annotation's line when there is one. */
    val line: Int,
    val verdict: DeclarationVerdict,
    /** The lines that decided [verdict]; empty for [DeclarationVerdict.Unexpected]. */
    val rules: List<RuleRef>,
    /** For [DeclarationVerdict.Mismatched]: how the declaration differs. */
    val difference: String?,
)

/** One line of the allow list as it was evaluated for the file, and where it was written. */
internal data class RuleRef(val text: String, val declaredAt: DeclarationSiteRef?)

/** What kind of top-level declaration. A name this plugin does not know is [Unknown]. */
internal sealed interface SourceDeclarationKind : JsonNamed {
    data object Class : SourceDeclarationKind { override val jsonName: String get() = "class" }
    data object Interface : SourceDeclarationKind { override val jsonName: String get() = "interface" }
    data object Object : SourceDeclarationKind { override val jsonName: String get() = "object" }
    data object Fun : SourceDeclarationKind { override val jsonName: String get() = "fun" }
    data object Val : SourceDeclarationKind { override val jsonName: String get() = "val" }
    data object Var : SourceDeclarationKind { override val jsonName: String get() = "var" }
    data object TypeAlias : SourceDeclarationKind { override val jsonName: String get() = "typealias" }
    data class Unknown(override val jsonName: String) : SourceDeclarationKind

    companion object {
        val known: List<SourceDeclarationKind> = listOf(Class, Interface, Object, Fun, Val, Var, TypeAlias)
        fun of(name: String): SourceDeclarationKind = known.firstOrNull { it.jsonName == name } ?: Unknown(name)
    }
}

/** The judgement of one declaration. */
internal sealed interface DeclarationVerdict : JsonNamed {
    /** A line of the allow list matched. */
    data object Allowed : DeclarationVerdict { override val jsonName: String get() = "Allowed" }

    /** No line matched but `anythingElse()` did. */
    data object AllowedByAnythingElse : DeclarationVerdict { override val jsonName: String get() = "AllowedByAnythingElse" }

    /** A `not.` line matched. */
    data object Prohibited : DeclarationVerdict { override val jsonName: String get() = "Prohibited" }

    /** The name matched an allowed line but the shape differs. */
    data object Mismatched : DeclarationVerdict { override val jsonName: String get() = "Mismatched" }

    /** No line matched. */
    data object Unexpected : DeclarationVerdict { override val jsonName: String get() = "Unexpected" }
    data class Unknown(override val jsonName: String) : DeclarationVerdict

    /** Whether the declaration passes the check (the allowed verdicts); an unknown one is not assumed to. */
    val isAllowed: Boolean get() = this == Allowed || this == AllowedByAnythingElse

    companion object {
        // Lazy: isAllowed is a default method, so touching Allowed first initializes this interface (and an eager list
        // here) while Allowed is still being created, and the list would hold null.
        val known: List<DeclarationVerdict> by lazy { listOf(Allowed, AllowedByAnythingElse, Prohibited, Mismatched, Unexpected) }
        fun of(name: String): DeclarationVerdict = known.firstOrNull { it.jsonName == name } ?: Unknown(name)
    }
}
