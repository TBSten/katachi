package me.tbsten.katachi.script.internal

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.asSequence

// `lint`: lists the Roles that seem to hold files of different kinds, and the other checks of the
// guide's step 3 (flat groups, files not split, documented = false candidates). Reads the definition
// as Gradle evaluated it (DefinitionModel), never the source text. Findings never fail it.

/** `lint`. */
internal fun ScriptContext.runLint(args: List<String>): Int {
    val parsed = parseArgs(commandSpec("lint"), args)
    parsed.positionals.firstOrNull()?.let { dieUsage(Messages.Lint.unknownOption(it)) }
    lintRoles(parsed.value("--module-dir"))
    return 0
}

/** A Role written in the definition directory, with the file it was declared in. */
private class LintRole(
    val role: DefinedRole,
    val path: Path,
    val line: Int,
    val documentedSomewhere: Boolean,
    /** Constraints written inside the Role's braces, read from the source (the JSON cannot tell them apart). */
    val inlineConstraints: List<InlineConstraint>,
) {
    val name: String get() = role.name

    /** Files the layout declares one by one, synthetic lines of a `module { }` block left out. */
    val files: List<String> = role.layout.filter { it.kind == "File" && !it.synthetic }.map { it.path }

    /** `anyFile()` and `ignore()` lines, each with the directory it covers. */
    val catchall: List<Pair<String, String>> = role.layout.filter { (it.kind == "AnyFile" || it.kind == "Ignore") && !it.synthetic }.map { it.kind to it.path }

    val places: List<String> = files + catchall.map { it.second }
    val hasConstraint: Boolean get() = role.constraints > 0
}

private class LintGroup(val group: DefinedGroup, val path: Path, val line: Int, val directRoles: Int, val hasGroup: Boolean) {
    val name: String get() = group.name
}

private class Decl(val kind: String, val name: String, val line: Int)

/** (pattern on the lower-cased file name, label, whether gradle() declares it). */
private fun ScriptContext.lintKinds(): List<Triple<Regex, String, Boolean>> = listOf(
    Triple("^settings\\.gradle(\\.kts)?$", "settings.gradle(.kts)", true),
    Triple("^build\\.gradle(\\.kts)?$", "build.gradle(.kts)", true),
    Triple("^gradle\\.properties$", "gradle.properties", true),
    Triple("^gradlew(\\.bat)?$", "gradlew", true),
    Triple("^gradle-wrapper\\.jar$", "gradle-wrapper.jar", true),
    Triple("^gradle-wrapper\\.properties$", "gradle-wrapper.properties", true),
    Triple("\\.versions\\.toml$", "version catalog", true),
    Triple("^local\\.properties$", "local.properties", false),
    Triple("^readme", "README", false),
    Triple("^(licen[cs]e|copying|notice)", "LICENSE", false),
    Triple("^contributing", "CONTRIBUTING", false),
    Triple("^(changelog|changes|release[-_]?notes)", "CHANGELOG", false),
    Triple("^code[-_]of[-_]conduct", "CODE_OF_CONDUCT", false),
    Triple("^security\\.md$", "SECURITY.md", false),
    Triple("^(claude|agents|gemini|copilot-instructions)\\.md$", pick(Messages.Lint.aiAgentInstructions), false),
    Triple("^\\.gitignore$", ".gitignore", false),
    Triple("^\\.gitattributes$", ".gitattributes", false),
    Triple("^\\.editorconfig$", ".editorconfig", false),
    Triple("^androidmanifest\\.xml$", "AndroidManifest.xml", false),
    Triple("^proguard-rules\\.pro$", "proguard-rules.pro", false),
    Triple("^renovate\\.json5?$", "renovate", false),
    Triple("^gemfile(\\.lock)?$", "Gemfile", false),
    Triple("^dangerfile", "Dangerfile", false),
    Triple("^(package(-lock)?\\.json|yarn\\.lock|pnpm-lock\\.yaml)$", "npm", false),
    Triple("^(detekt|lint)[^/]*\\.(yml|yaml|xml)$", pick(Messages.Lint.staticAnalysisConfig), false),
).map { (pattern, label, gradle) -> Triple(Regex(pattern), label, gradle) }

/** buildSrc and the builds settings includes: gradle() does not cover them. */
private fun lintIncludedBuilds(projectRoot: Path): Set<String> {
    val found = linkedSetOf("buildSrc")
    for (name in listOf("settings.gradle.kts", "settings.gradle")) {
        val file = projectRoot.resolve(name)
        if (!file.isRegularFile()) continue
        val src = String(Files.readAllBytes(file), Charsets.UTF_8)
        for (m in Regex("\\bincludeBuild\\s*\\(?\\s*[\"']([^\"']+)[\"']").findAll(src)) {
            val d = Path.of(m.groupValues[1]).normalize().toString().replace('\\', '/').ifEmpty { "." }
            if (d != "." && !d.startsWith("..")) found += d
        }
    }
    return found
}

/** The Role check, after `check 3-2` / `check 3-3` and as `lint`. Stops only when there is no definition to read or Gradle cannot evaluate it. */
internal fun ScriptContext.lintRoles(moduleDirArg: String?) {
    val module = resolveModuleDir(moduleDirArg)
    val definitionDir = requireDefinitionDir(module)
    val checkList = findWorkdir()?.resolve(CHECK_LIST_FILE)
    val data = checkList?.let { readJsonBlockOrNull(it, "checklist") }?.toTree().asMap() ?: JMap()
    val projectRoot = projectRoot()
    val prefs = data.mapOrEmpty("preferences")
    val root = projectRoot.resolve(module.dir).resolve("src/test/kotlin")
    val included = lintIncludedBuilds(projectRoot)
    val kindsTable = lintKinds()

    fun inIncludedBuild(where: String): Boolean {
        val w = where.replace('\\', '/').replace(':', '/').trimStart('/')
        return included.any { w == it || w.startsWith("$it/") }
    }

    fun kindOf(name: String): Pair<String?, Boolean> {
        val base = name.substringAfterLast('/').lowercase()
        return kindsTable.firstOrNull { it.first.containsMatchIn(base) }?.let { it.second to it.third } ?: (null to false)
    }

    fun uri(path: Path) = fileUriOf(projectRoot.resolve(path).toAbsolutePath())

    val model = evaluateDefinition(projectRoot, module)

    // The JSON keeps the file name only; a Role whose file is not in the definition directory is not the user's to fix here.
    val sources = LinkedHashMap<String, Path>()
    Files.walk(definitionDir).use { stream ->
        stream.asSequence().filter { it.isRegularFile() && it.name.endsWith(".kt") }
            .sortedWith(compareBy({ it.parent.toString() }, { it.name }))
            .forEach { sources.putIfAbsent(it.name, it) }
    }
    val groupsByPath = model.groups.associateBy { it.path }
    fun documentedSomewhere(role: DefinedRole): Boolean =
        role.documented != null || (1..role.groupPath.size).any { groupsByPath[role.groupPath.take(it)]?.documented != null }

    // Parsed once per file, and only for the files a Role is declared in.
    val roleCallsByFile = HashMap<Path, List<RoleCall>>()
    fun inlineConstraintsOf(role: DefinedRole, path: Path, line: Int): List<InlineConstraint> {
        val calls = roleCallsByFile.getOrPut(path) { parseRoleCalls(path.name, String(Files.readAllBytes(path), Charsets.UTF_8)) }
        // The JSON's line is the one the Role call was on; fall back to a name that is the only one in the file.
        val call = calls.firstOrNull { it.line == line } ?: calls.filter { it.name == role.name }.singleOrNull()
        return call?.inlineConstraints.orEmpty()
    }
    val roles = model.roles.filterNot { it.declaredByGradle }.mapNotNull { role ->
        val site = role.declaredAt ?: return@mapNotNull null
        val path = sources[site.fileName] ?: return@mapNotNull null
        LintRole(role, path, site.line, documentedSomewhere(role), inlineConstraintsOf(role, path, site.line))
    }.sortedWith(compareBy({ it.path.toString() }, { it.line }))
    val groups = model.groups.filterNot { it.declaredByGradle }.mapNotNull { group ->
        val site = group.declaredAt ?: return@mapNotNull null
        val path = sources[site.fileName] ?: return@mapNotNull null
        val directRoles = model.roles.count { it.groupPath == group.path }
        val hasGroup = model.groups.any { it.path.size == group.path.size + 1 && it.path.take(group.path.size) == group.path }
        LintGroup(group, path, site.line, directRoles, hasGroup)
    }.sortedWith(compareBy({ it.path.toString() }, { it.line }))
    val decls = (roles.map { it.path to Decl("Role", it.name, it.line) } + groups.map { it.path to Decl("group", it.name, it.line) })
        .groupBy({ it.first }, { it.second })

    val warnings = mutableListOf<Pair<LintRole, List<String>>>()
    val gradleHand = mutableListOf<Pair<LintRole, String>>()
    val gradleConstrained = LinkedHashSet<Triple<LintRole, String, String>>()
    val testSegment = Regex("^(test|testSourceSet|androidTest|androidUnitTest|androidInstrumentedTest|[a-z][A-Za-z]*Test|testFixtures)$")
    val mainSegment = Regex("^(main|mainSourceSet|[a-z][A-Za-z]*Main)$")
    for (r in roles) {
        val reasons = mutableListOf<String>()
        val kinds = LinkedHashMap<String, MutableList<String>>()
        for (name in r.files) {
            val (label, gradle) = kindOf(name)
            if (label == null) continue
            kinds.getOrPut(label) { mutableListOf() } += name.substringAfterLast('/')
            // gradle() does not cover buildSrc and included builds, so it is not recommended under them.
            if (gradle && !inIncludedBuild(name)) {
                if (!r.hasConstraint) gradleHand += r to name else gradleConstrained += Triple(r, name, label)
            }
        }
        if (kinds.size >= 2) {
            reasons += pick(Messages.Lint.filesDifferentKindsShare) +
                kinds.entries.joinToString(" / ") { (label, names) -> pick(Messages.Lint.kindFiles(label, names.toSortedSet().joinToString(", "))) }
        }
        val dots = r.catchall.map { it.second.substringAfterLast('/') }.filter { it.startsWith(".") && it.length > 1 }.toSortedSet()
        if (dots.size >= 2) {
            reasons += pick(Messages.Lint.groupsPerToolConfig) + dots.joinToString(", ")
        }
        val segments = r.places.map { it.replace('\\', '/').split("/").toSet() }
        val hasTest = segments.any { s -> s.any { testSegment.matches(it) } }
        val hasMain = segments.any { s -> s.any { mainSegment.matches(it) } && s.none { testSegment.matches(it) } }
        if (hasTest && hasMain) reasons += pick(Messages.Lint.declaresMainTestFiles)
        if (reasons.isNotEmpty()) warnings += r to reasons
    }

    val nestedGroups = prefs["nestedGroups"] != "no"
    val oneRolePerFile = prefs["oneRolePerFile"] != "no"
    val flatGroups = if (nestedGroups) groups.filter { it.directRoles >= 8 && !it.hasGroup } else emptyList()
    val unsplitFiles = if (oneRolePerFile) {
        decls.entries.sortedBy { it.key.toString() }.map { (path, ds) -> path to ds.sortedBy { it.line } }.filter { it.second.size >= 2 }
    } else {
        emptyList()
    }
    // Role and group names are UpperCamelCase, like katachi's own Gradle / Git groups (KATACHI-362).
    val lowercaseNames = (roles.map { Triple("Role", it.name, it.path to it.line) } + groups.map { Triple("group", it.name, it.path to it.line) })
        .filter { it.second.firstOrNull()?.isLowerCase() == true }
        .sortedWith(compareBy({ it.third.first.toString() }, { it.third.second }))
    val documentedCandidates = mutableListOf<Pair<LintRole, String>>()
    for (r in roles) {
        if (r.documentedSomewhere) continue
        if (r.files.isNotEmpty()) {
            val labels = r.files.map { kindOf(it) }
            if (labels.all { (label, gradle) -> label != null && !gradle }) documentedCandidates += r to labels.mapNotNull { it.first }.toSortedSet().joinToString(", ")
        } else if (r.catchall.isNotEmpty() && r.catchall.all { it.first == "Ignore" }) {
            documentedCandidates += r to "ignore()"
        }
    }
    val hookOf = mapOf(
        "settings.gradle(.kts)" to "settingsScript { }",
        "build.gradle(.kts)" to "buildScript { }",
        "gradle.properties" to "gradleProperties { }",
        "gradlew" to "launcherScript { }",
        "gradle-wrapper.jar" to "wrapperJar { }",
        "gradle-wrapper.properties" to "wrapperProperties { }",
        "version catalog" to "versionCatalog { }",
    )

    val inlineConstraintRoles = roles.filter { it.inlineConstraints.isNotEmpty() }
    if (warnings.isEmpty() && inlineConstraintRoles.isEmpty() && gradleHand.isEmpty() && gradleConstrained.isEmpty() && flatGroups.isEmpty() && lowercaseNames.isEmpty() && unsplitFiles.isEmpty() && documentedCandidates.isEmpty()) {
        say(Messages.Lint.roleCheckNoRole(roles.size))
        return
    }
    say("")
    say(Messages.Lint.roleCheckNeverFails)
    say(Messages.Lint.definition(uri(root)))
    if (warnings.isNotEmpty()) {
        say("")
        say(Messages.Lint.rolesSeemHoldFiles(warnings.size, roles.size))
        for ((r, reasons) in warnings) {
            say("")
            say("  ${uri(r.path)}:${r.line} Role \"${r.name}\"")
            for (x in reasons) say("    - $x")
        }
        say("")
        say(Messages.Lint.roleNameRoleStands)
    }
    if (gradleHand.isNotEmpty()) {
        val names = gradleHand.map { it.first.name }.toSortedSet().joinToString(", ")
        say("")
        say(Messages.Lint.gradleFilesDeclaredHand(names))
        say(Messages.Lint.gradleDeclaresWrapperSettings)
    }
    if (gradleConstrained.isNotEmpty()) {
        say("")
        say(Messages.Lint.gradleFilesInConstrainedRoles)
        for ((r, name, label) in gradleConstrained.sortedWith(compareBy({ it.first.path.toString() }, { it.first.line }))) {
            val hook = hookOf[label] ?: "buildScript { }"
            say(Messages.Lint.gradleRoleHook(uri(r.path), r.line, r.name, name.substringAfterLast('/'), hook))
        }
        say(Messages.Lint.replacePlacementSingleLine)
    }
    if (inlineConstraintRoles.isNotEmpty()) {
        say("")
        say(Messages.Lint.inlineConstraintsInRoles(inlineConstraintRoles.size))
        for (r in inlineConstraintRoles) {
            val what = r.inlineConstraints.joinToString(", ") { pick(Messages.Lint.constraintAtLine(it.call, it.line)) }
            say(Messages.Lint.roleWhat(uri(r.path), r.line, r.name, what))
        }
        say(Messages.Lint.splitConstraintsIntoFunctions)
    }
    if (flatGroups.isNotEmpty()) {
        say("")
        say(Messages.Lint.flatGroupsMoreRoles(flatGroups.size))
        for (g in flatGroups) say(Messages.Lint.groupRoles(uri(g.path), g.line, g.name, g.directRoles))
        say(Messages.Lint.nestGroupsOrderModule)
    }
    if (lowercaseNames.isNotEmpty()) {
        say("")
        say(Messages.Lint.lowercaseNames(lowercaseNames.size))
        for ((kind, name, at) in lowercaseNames) {
            say(Messages.Lint.lowercaseName(uri(at.first), at.second, kind, name, name.replaceFirstChar { it.uppercaseChar() }))
        }
        say(Messages.Lint.nameInUpperCamelCase)
    }
    if (unsplitFiles.isNotEmpty()) {
        say("")
        say(Messages.Lint.filesNotSplitMore(unsplitFiles.size))
        for ((path, ds) in unsplitFiles) say("  ${uri(path)}: ${ds.joinToString(", ") { "${it.kind} \"${it.name}\"" }}")
        say(Messages.Lint.writeOneRolePer)
    }
    if (documentedCandidates.isNotEmpty()) {
        say("")
        say(Messages.Lint.candidatesDocumentedFalseReview(documentedCandidates.size))
        for ((r, what) in documentedCandidates) say(Messages.Lint.roleWhat(uri(r.path), r.line, r.name, what))
        say(Messages.Lint.setDocumentedFalseGenerated)
    }
    say("==================================================================")
}
