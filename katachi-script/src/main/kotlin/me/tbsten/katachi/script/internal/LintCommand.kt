package me.tbsten.katachi.script.internal

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.asSequence

// `lint`: lists the Roles that seem to hold files of different kinds, and the other checks of the
// guide's step 3 (flat groups, files not split, documented = false candidates). Never fails.
// A plain scan that skips strings and comments and counts braces, not a Kotlin parser, so false
// positives are possible. The kit's `lint_roles` (its embedded python, PYROLES), rule by rule.

/** `lint`. */
internal fun ScriptContext.runLint(): Int {
    lintRoles()
    return 0
}

private class Token(val type: String, val value: String?, val line: Int)

private val NO_TOKEN = Token("", null, 0)

private class LintRole(val name: String, val path: Path, val line: Int, val group: LintGroup?) {
    val files = mutableListOf<Triple<String, Int, String>>()
    val catchall = mutableListOf<Triple<String, String, Int>>()
    val places = mutableListOf<String>()
    var hasConstraint = false
    var documented = false

    /** Itself or any group around it writes documented (a group's false reaches the Roles inside). */
    fun documentedSomewhere(): Boolean {
        var g = group
        while (g != null) {
            if (g.documented) return true
            g = g.parent
        }
        return documented
    }
}

private class LintGroup(val name: String, val path: Path, val line: Int, val parent: LintGroup?) {
    var directRoles = 0
    var hasGroup = false
    var documented = false
}

private class Decl(val kind: String, val name: String)

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

private fun tokenize(src: String): List<Token> {
    val tokens = mutableListOf<Token>()
    var i = 0
    val n = src.length
    var line = 1
    fun newlines(from: Int, to: Int) = (from until to).count { src[it] == '\n' }
    while (i < n) {
        val c = src[i]
        when {
            c == '\n' -> {
                line++
                i++
            }

            c == ' ' || c == '\t' || c == '\r' -> i++

            src.startsWith("//", i) -> {
                val j = src.indexOf('\n', i)
                i = if (j < 0) n else j
            }

            src.startsWith("/*", i) -> {
                var j = src.indexOf("*/", i + 2)
                j = if (j < 0) n else j + 2
                line += newlines(i, j)
                i = j
            }

            src.startsWith("\"\"\"", i) -> {
                var j = src.indexOf("\"\"\"", i + 3)
                j = if (j < 0) n else j + 3
                while (j < n && src[j] == '"') j++
                line += newlines(i, j)
                tokens += Token("str", null, line)
                i = j
            }

            c == '"' -> {
                var j = i + 1
                val buf = StringBuilder()
                var depth = 0
                while (j < n) {
                    val d = src[j]
                    if (depth == 0 && d == '\\') {
                        buf.append(src, j, minOf(j + 2, n))
                        j += 2
                        continue
                    }
                    if (depth == 0 && d == '"') break
                    if (d == '\n') break
                    if (src.startsWith("\${", j)) {
                        depth++
                        buf.append("\${")
                        j += 2
                        continue
                    }
                    if (depth > 0 && d == '}') depth--
                    buf.append(d)
                    j++
                }
                tokens += Token("str", buf.toString(), line)
                i = j + 1
            }

            c == '\'' -> {
                val j = src.indexOf('\'', i + 1 + if (src.getOrNull(i + 1) == '\\') 1 else 0)
                i = if (j < 0) n else j + 1
            }

            c.isLetter() || c == '_' || c == '`' -> {
                var j = i + 1
                if (c == '`') {
                    // No closing `: up to the end (indexOf's -1 plus 1 would go back to 0 and loop forever).
                    j = src.indexOf('`', i + 1)
                    j = if (j < 0) n else j + 1
                } else {
                    while (j < n && (src[j].isLetterOrDigit() || src[j] == '_')) j++
                }
                tokens += Token("id", src.substring(i, j), line)
                i = j
            }

            else -> {
                tokens += Token("op", c.toString(), line)
                i++
            }
        }
    }
    return tokens
}

/** hooks such as gradle { buildScript { } } are `identifier {`, a block, never a Role. Keep it that way. */
private val CONSTRAINT_CALLS = setOf("fileConstraint", "konsist", "ktFileContent", "konture")

private class StackEntry(val kind: String, val name: String? = null, val group: LintGroup? = null)

private fun scan(path: Path, roles: MutableList<LintRole>, groups: MutableList<LintGroup>, decls: MutableMap<Path, MutableList<Decl>>) {
    val src = String(Files.readAllBytes(path), Charsets.UTF_8)
    val tokens = tokenize(src)
    val stack = mutableListOf<StackEntry>()
    var role: LintRole? = null
    for ((k, tok) in tokens.withIndex()) {
        val prev = tokens.getOrNull(k - 1) ?: NO_TOKEN
        val prev2 = tokens.getOrNull(k - 2) ?: NO_TOKEN
        val prev3 = tokens.getOrNull(k - 3) ?: NO_TOKEN
        val next = tokens.getOrNull(k + 1) ?: NO_TOKEN
        val kinds = stack.map { it.kind }
        val inLayout = "layout" in kinds
        val group = stack.lastOrNull { it.kind == "group" }?.group
        if (tok.type == "op" && tok.value == "{") {
            // A Role is "Name".role { }; the old "Name" { } (now a compile error) outside layout { } is read as one too.
            val roleCall = prev.type == "id" && prev.value == "role" && prev2.value == "." && prev3.type == "str" && prev3.value != null
            if (roleCall || (prev.type == "str" && prev.value != null)) {
                val nameTok = if (roleCall) prev3 else prev
                val name = nameTok.value!!
                when {
                    inLayout -> stack += StackEntry("dir", name)
                    "role" in kinds || "other" in kinds || Regex("\\s").containsMatchIn(name) -> stack += StackEntry("other")
                    else -> {
                        val r = LintRole(name, path, nameTok.line, group)
                        role = r
                        roles += r
                        decls.getOrPut(path) { mutableListOf() } += Decl("Role", name)
                        if (group != null) group.directRoles++
                        stack += StackEntry("role", name)
                    }
                }
            } else if (prev.type == "id" && prev.value == "group" && prev2.value == ".") {
                val g = LintGroup(if (prev3.type == "str") prev3.value ?: "None" else "?", path, prev3.line, group)
                groups += g
                decls.getOrPut(path) { mutableListOf() } += Decl("group", g.name)
                if (group != null) group.hasGroup = true
                stack += StackEntry("group", group = g)
            } else if (prev.type == "id" && prev.value == "module" && prev2.value == "." && inLayout) {
                stack += StackEntry("dir", prev3.value?.takeIf { it.isNotEmpty() } ?: "?")
            } else if (prev.type == "id" && prev.value == "layout" && "role" in kinds) {
                stack += StackEntry("layout")
            } else if (prev.type == "id" && (prev.value == "konsist" || prev.value == "template")) {
                stack += StackEntry("other")
            } else {
                stack += StackEntry("block")
            }
            continue
        }
        if (tok.type == "op" && tok.value == "}") {
            if (stack.isNotEmpty() && stack.removeAt(stack.size - 1).kind == "role") role = null
            continue
        }
        if (!inLayout && tok.type == "id") {
            // Outside layout: note constraint calls and documented = ... on the Role / group.
            if (role != null && tok.value in CONSTRAINT_CALLS && (next.value == "(" || next.value == "{")) {
                role.hasConstraint = true
            } else if (tok.value == "documented" && next.value == "=") {
                if (role != null) role.documented = true else if (group != null) group.documented = true
            }
            continue
        }
        if (!(inLayout && role != null)) continue
        val dirs = stack.filter { it.kind == "dir" && !it.name.isNullOrEmpty() }.joinToString("/") { it.name!! }
        // The chain of / in `mainSourceSet / kotlin / "x".ktFile()` counts as a place too.
        val chain = mutableListOf<String>()
        var b = k - 1
        while (b >= 0 && (tokens[b].value == "/" || tokens[b].value == "." || tokens[b].type == "str" || tokens[b].type == "id")) {
            val t = tokens[b]
            if ((t.type == "str" || t.type == "id") && !t.value.isNullOrEmpty()) chain += t.value
            if ((t.type == "str" || t.type == "id") && b > 0 && tokens[b - 1].value != "/" && tokens[b - 1].value != ".") break
            b--
        }
        val where = dirs + "/" + chain.reversed().joinToString("/")
        if (tok.type == "id" && tok.value in setOf("file", "ktFile", "ktsFile") && prev.value == "." && next.value == "(") {
            if (prev2.type == "str" && prev2.value != null) {
                val suffix = when (tok.value) {
                    "ktFile" -> ".kt"
                    "ktsFile" -> ".kts"
                    else -> ""
                }
                role.files += Triple(prev2.value + suffix, tok.line, where)
                role.places += where
            }
        } else if (tok.type == "id" && (tok.value == "anyFile" || tok.value == "ignore") && next.value == "(") {
            var target = dirs
            if (tok.value == "ignore" && prev.value == "." && prev2.type == "str" && !prev2.value.isNullOrEmpty()) {
                target = (if (dirs.isNotEmpty()) "$dirs/" else "") + prev2.value
            }
            role.catchall += Triple(tok.value!!, target.ifEmpty { "." }, tok.line)
            role.places += "$where/$target"
        }
    }
}

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

/** The Role check, after `check 3-2` / `check 3-3` and as `lint`. Stops only when there is no definition to read. */
internal fun ScriptContext.lintRoles() {
    val module = resolveModuleDir()
    requireDefinitionDir(module)
    val checkList = findWorkdir()?.resolve(CHECK_LIST_FILE)
    val data = checkList?.let { readJsonBlockOrNull(it, "checklist") }?.toTree().asMap() ?: JMap()
    val projectRoot = data.mapOrEmpty("meta").str("projectRoot")?.takeIf { it.isNotEmpty() }?.let(::resolve) ?: cwd
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

    val roles = mutableListOf<LintRole>()
    val groups = mutableListOf<LintGroup>()
    val decls = LinkedHashMap<Path, MutableList<Decl>>()
    if (root.isDirectory()) {
        val files = Files.walk(root).use { stream -> stream.asSequence().filter { it.isRegularFile() && it.name.endsWith(".kt") }.toList() }
        for (file in files.sortedWith(compareBy({ it.parent.toString() }, { it.name }))) scan(file, roles, groups, decls)
    }

    val warnings = mutableListOf<Pair<LintRole, List<String>>>()
    val gradleHand = mutableListOf<Pair<LintRole, String>>()
    val gradleConstrained = LinkedHashSet<Triple<LintRole, String, String>>()
    val testSegment = Regex("^(test|testSourceSet|androidTest|androidUnitTest|androidInstrumentedTest|[a-z][A-Za-z]*Test|testFixtures)$")
    val mainSegment = Regex("^(main|mainSourceSet|[a-z][A-Za-z]*Main)$")
    for (r in roles) {
        val reasons = mutableListOf<String>()
        val kinds = LinkedHashMap<String, MutableList<String>>()
        for ((name, _, where) in r.files) {
            val (label, gradle) = kindOf(name)
            if (label == null) continue
            kinds.getOrPut(label) { mutableListOf() } += name.substringAfterLast('/')
            // gradle() does not cover buildSrc and included builds, so it is not recommended under them.
            if (gradle && !inIncludedBuild(where)) {
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
    val unsplitFiles = if (oneRolePerFile) decls.entries.sortedBy { it.key.toString() }.filter { it.value.size >= 2 } else emptyList()
    val documentedCandidates = mutableListOf<Pair<LintRole, String>>()
    for (r in roles) {
        if (r.documentedSomewhere()) continue
        if (r.files.isNotEmpty()) {
            val labels = r.files.map { kindOf(it.first) }
            if (labels.all { (label, gradle) -> label != null && !gradle }) documentedCandidates += r to labels.mapNotNull { it.first }.toSortedSet().joinToString(", ")
        } else if (r.catchall.isNotEmpty() && r.catchall.all { it.first == "ignore" }) {
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

    if (warnings.isEmpty() && gradleHand.isEmpty() && gradleConstrained.isEmpty() && flatGroups.isEmpty() && unsplitFiles.isEmpty() && documentedCandidates.isEmpty()) {
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
    if (flatGroups.isNotEmpty()) {
        say("")
        say(Messages.Lint.flatGroupsMoreRoles(flatGroups.size))
        for (g in flatGroups) say(Messages.Lint.groupRoles(uri(g.path), g.line, g.name, g.directRoles))
        say(Messages.Lint.nestGroupsOrderModule)
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
