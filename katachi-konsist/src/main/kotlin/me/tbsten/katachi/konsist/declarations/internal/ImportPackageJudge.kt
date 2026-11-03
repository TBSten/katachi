package me.tbsten.katachi.konsist.declarations.internal

internal fun judgeImports(rules: List<ImportRule>, imports: List<ImportFact>): List<KtFileFinding> {
    val allowed = rules.filterNot { it.prohibited }
    return imports.mapNotNull { import ->
        val prohibitedBy = rules.firstOrNull { it.prohibited && importMatches(it, import.path) }
        when {
            prohibitedBy != null -> ProhibitedImport(import, prohibitedBy)
            allowed.any { importMatches(it, import.path) } -> null
            else -> UnexpectedImport(import, allowed)
        }
    }
}

private fun importMatches(rule: ImportRule, path: String): Boolean =
    rule.path.matches(path) && rule.except.none { it.matches(path) }

// The default package has no level: only a pattern of nothing but `**` levels matches it, allowed or prohibited; `*` does not.
internal fun judgePackage(rules: List<PackageRule>, facts: KtFileFacts): KtFileFinding? {
    val packageName = facts.packageName
    val directoryPackage = directoryPackageOf(facts.path)
    if (rules.isEmpty()) {
        return packageName?.let { UnexpectedPackage(it, facts.packageLine, allowed = emptyList(), firstRule = null, directoryPackage) }
    }
    rules.firstOrNull { it.prohibited && packageMatches(it, packageName, facts.path, directoryPackage) }
        ?.let { return ProhibitedPackage(packageName, facts.packageLine, it) }
    val allowed = rules.filterNot { it.prohibited }
    if (allowed.any { packageMatches(it, packageName, facts.path, directoryPackage) }) return null
    return UnexpectedPackage(packageName, facts.packageLine, allowed, rules.first(), directoryPackage)
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
