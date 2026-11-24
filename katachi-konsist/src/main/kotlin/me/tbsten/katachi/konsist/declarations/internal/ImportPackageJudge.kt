package me.tbsten.katachi.konsist.declarations.internal

/** How the check decided one import: the lines that allowed it, the line that prohibits it, or that none allows it. */
internal sealed interface ImportOutcome {
    class Allowed(val rules: List<ImportRule>) : ImportOutcome

    class Prohibited(val rule: ImportRule) : ImportOutcome

    data object Unexpected : ImportOutcome
}

internal class JudgedImport(val import: ImportFact, val outcome: ImportOutcome)

// The one judgement: the check keeps the findings of it, the explanation keeps all of it.
internal fun outcomesOfImports(rules: List<ImportRule>, imports: List<ImportFact>): List<JudgedImport> {
    val allowed = rules.filterNot { it.prohibited }
    return imports.map { import ->
        val prohibitedBy = rules.firstOrNull { it.prohibited && importMatches(it, import.path) }
        val allowedBy = allowed.filter { importMatches(it, import.path) }
        JudgedImport(
            import,
            when {
                prohibitedBy != null -> ImportOutcome.Prohibited(prohibitedBy)
                allowedBy.isNotEmpty() -> ImportOutcome.Allowed(allowedBy)
                else -> ImportOutcome.Unexpected
            },
        )
    }
}

internal fun judgeImports(rules: List<ImportRule>, imports: List<ImportFact>): List<KtFileFinding> {
    val allowed = rules.filterNot { it.prohibited }
    return outcomesOfImports(rules, imports).mapNotNull { judged ->
        when (val outcome = judged.outcome) {
            is ImportOutcome.Prohibited -> ProhibitedImport(judged.import, outcome.rule)
            ImportOutcome.Unexpected -> UnexpectedImport(judged.import, allowed)
            is ImportOutcome.Allowed -> null
        }
    }
}

private fun importMatches(rule: ImportRule, path: String): Boolean =
    rule.path.matches(path) && rule.except.none { it.matches(path) }

/** How the check decided the `package` line: the lines that allowed it, the line that prohibits it, or the finding of a package no line allows. */
internal sealed interface PackageOutcome {
    class Allowed(val rules: List<PackageRule>) : PackageOutcome

    class Prohibited(val finding: ProhibitedPackage) : PackageOutcome

    class Unexpected(val finding: UnexpectedPackage) : PackageOutcome
}

// The default package has no level: only a pattern of nothing but `**` levels matches it, allowed or prohibited; `*` does not.
internal fun outcomeOfPackage(rules: List<PackageRule>, facts: KtFileFacts): PackageOutcome {
    val packageName = facts.packageName
    val directoryPackage = directoryPackageOf(facts.path)
    if (rules.isEmpty()) {
        return packageName?.let { PackageOutcome.Unexpected(UnexpectedPackage(it, facts.packageLine, allowed = emptyList(), firstRule = null, directoryPackage)) }
            ?: PackageOutcome.Allowed(emptyList())
    }
    rules.firstOrNull { it.prohibited && packageMatches(it, packageName, facts.path, directoryPackage) }
        ?.let { return PackageOutcome.Prohibited(ProhibitedPackage(packageName, facts.packageLine, it)) }
    val allowed = rules.filterNot { it.prohibited }
    val allowedBy = allowed.filter { packageMatches(it, packageName, facts.path, directoryPackage) }
    if (allowedBy.isNotEmpty()) return PackageOutcome.Allowed(allowedBy)
    return PackageOutcome.Unexpected(UnexpectedPackage(packageName, facts.packageLine, allowed, rules.first(), directoryPackage))
}

internal fun judgePackage(rules: List<PackageRule>, facts: KtFileFacts): KtFileFinding? = when (val outcome = outcomeOfPackage(rules, facts)) {
    is PackageOutcome.Allowed -> null
    is PackageOutcome.Prohibited -> outcome.finding
    is PackageOutcome.Unexpected -> outcome.finding
}

private fun packageMatches(rule: PackageRule, packageName: String?, path: String, directoryPackage: String?): Boolean =
    when (val target = rule.target) {
        is PackageTarget.Pattern -> pathMatches(target.rule, packageName) && rule.except.none { pathMatches(it, packageName) }
        // A file without a package is in the default one only directly under the source root's `kotlin/`.
        PackageTarget.MatchingDirectory -> when {
            packageName == null -> directoryPackage == ""
            directoryPackage != null -> packageName == directoryPackage
            // No source root in the path: the file's path has to end with its package, as
            // Konsist's `hasMatchingPath` reads it.
            else -> path.endsWith("/" + packageName.replace('.', '/') + "/" + path.substringAfterLast('/')) ||
                path == packageName.replace('.', '/') + "/" + path.substringAfterLast('/')
        }
    }

private fun pathMatches(rule: NameRule, packageName: String?): Boolean =
    if (packageName == null) rule.written.split('.').all { it == ANY_LEVELS } else rule.matches(packageName)

private const val ANY_LEVELS: String = "**"

private val SOURCE_ROOT: Regex = Regex("""(?:^|/)src/[^/]+/(?:kotlin|java)/""")

internal fun directoryPackageOf(path: String): String? {
    val root = SOURCE_ROOT.findAll(path).lastOrNull() ?: return null
    val directories = path.substring(root.range.last + 1).substringBeforeLast('/', missingDelimiterValue = "")
    return directories.replace('/', '.')
}
