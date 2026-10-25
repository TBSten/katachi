package me.tbsten.katachi.konsist.declarations.internal

/*
 * Judging a file's imports and its package. Like the declarations, each is prohibited first,
 * allowed next, and wrong otherwise; neither has an `allow(..., anythingElse)` or a mismatch -- an import
 * is its path, and a file has one package. With no line written, nothing is allowed.
 */

/** The name a file without a package declaration is matched with: the default package's, which is empty. */
private const val DEFAULT_PACKAGE: String = ""

/**
 * Judges [imports] against [rules]: one finding per wrong import, in line order. With no import
 * line written, every import is wrong. An import line only allows, so no import is ever missing.
 */
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

/**
 * Judges the package of [facts] against [rules]. With no package line written, a declared package
 * is wrong and a file declaring none is fine: it declares nothing to deny.
 */
internal fun judgePackage(rules: List<PackageRule>, facts: KtFileFacts): KtFileFinding? {
    val packageName = facts.packageName
    if (rules.isEmpty()) {
        return packageName?.let { UnexpectedPackage(it, facts.packageLine, allowed = emptyList(), firstRule = null, directoryPackage = null) }
    }
    val directoryPackage = directoryPackageOf(facts.path)
    if (packageName != null) {
        rules.firstOrNull { it.prohibited && packageMatches(it, packageName, facts.path, directoryPackage) }
            ?.let { return ProhibitedPackage(packageName, facts.packageLine, it) }
    }
    val allowed = rules.filterNot { it.prohibited }
    val isAllowed = allowed.any { rule ->
        if (packageName != null) {
            packageMatches(rule, packageName, facts.path, directoryPackage)
        } else {
            // A file without a package is in the default one, named "": a pattern matching that
            // (`**`) allows it, and so does `matchingDirectory` for a file directly under `kotlin/`.
            when (val target = rule.target) {
                is PackageTarget.Pattern -> target.rule.matches(DEFAULT_PACKAGE) && rule.except.none { it.matches(DEFAULT_PACKAGE) }
                PackageTarget.MatchingDirectory -> directoryPackage == DEFAULT_PACKAGE
            }
        }
    }
    if (isAllowed) return null
    return UnexpectedPackage(packageName, facts.packageLine, allowed, rules.first(), directoryPackage)
}

private fun packageMatches(rule: PackageRule, packageName: String, path: String, directoryPackage: String?): Boolean =
    when (val target = rule.target) {
        is PackageTarget.Pattern -> target.rule.matches(packageName) && rule.except.none { it.matches(packageName) }
        PackageTarget.MatchingDirectory -> if (directoryPackage != null) {
            packageName == directoryPackage
        } else {
            // No source root in the path: the file's path has to end with its package, as
            // Konsist's `hasMatchingPath` reads it.
            path.endsWith("/" + packageName.replace('.', '/') + "/" + path.substringAfterLast('/')) ||
                path == packageName.replace('.', '/') + "/" + path.substringAfterLast('/')
        }
    }

/** The last `src/<source set>/kotlin/` or `src/<source set>/java/` in a path. */
private val SOURCE_ROOT: Regex = Regex("""(?:^|/)src/[^/]+/(?:kotlin|java)/""")

/**
 * The directories of [path] after its last source root (`src/main/kotlin/`, `src/main/java/` and
 * the like) and before its file name, joined with `.`: `""` for a file directly under the root,
 * `null` when the path has no source root.
 */
internal fun directoryPackageOf(path: String): String? {
    val root = SOURCE_ROOT.findAll(path).lastOrNull() ?: return null
    val directories = path.substring(root.range.last + 1).substringBeforeLast('/', missingDelimiterValue = "")
    return directories.replace('/', '.')
}
