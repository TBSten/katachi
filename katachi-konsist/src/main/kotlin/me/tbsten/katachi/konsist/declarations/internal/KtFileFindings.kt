package me.tbsten.katachi.konsist.declarations.internal

internal sealed interface KtFileFinding

internal class DeclarationListContext(
    val rules: DeclarationListRules,
    val containers: List<DeclarationFact>,
    val satisfied: Set<DeclarationRule>,
) {
    val container: DeclarationFact? get() = containers.lastOrNull()

    fun qualified(name: String): String = (containers.map { it.name } + name).joinToString(".")

    fun isMissing(rule: DeclarationRule): Boolean = rule.required && rule !in satisfied

    override fun toString(): String = "DeclarationListContext(${containers.map { it.name }})"
}

internal class UnexpectedDeclaration(
    val fact: DeclarationFact,
    val list: DeclarationListContext,
    val unclaimed: List<DeclarationRule>,
) : KtFileFinding

internal class ProhibitedDeclaration(
    val fact: DeclarationFact,
    val rule: DeclarationRule,
    val list: DeclarationListContext,
) : KtFileFinding

internal class MismatchedDeclaration(
    val fact: DeclarationFact,
    val rule: DeclarationRule,
    val list: DeclarationListContext,
    val differences: List<String>,
) : KtFileFinding

internal class MissingDeclaration(
    val rule: DeclarationRule,
    val list: DeclarationListContext,
    val found: DeclarationFact?,
) : KtFileFinding

internal class UnexpectedImport(
    val import: ImportFact,
    val allowed: List<ImportRule>,
) : KtFileFinding

internal class ProhibitedImport(val import: ImportFact, val rule: ImportRule) : KtFileFinding

internal class UnexpectedPackage(
    val packageName: String?,
    val line: Int?,
    val allowed: List<PackageRule>,
    val firstRule: PackageRule?,
    val directoryPackage: String?,
) : KtFileFinding

internal class ProhibitedPackage(val packageName: String?, val line: Int?, val rule: PackageRule) : KtFileFinding
