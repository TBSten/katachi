package me.tbsten.katachi.test.konsist.declarations

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.random.Random

/**
 * The judging over random allow lists (plan P1, properties 1 to 6; property 7 is
 * `KtFileContentPerFilePropertySpec`): a source that follows its allow list passes, and each single
 * change to it -- a declaration, an import or the package added, removed or changed -- is reported
 * exactly once, or not at all where `allow(anythingElse)` or a line allows it. A kind no line is
 * written for allows nothing (deny by default), so a change there is reported too.
 *
 * A case is an allow list and a source made together (`KtFileContentArbs*SpecSupport.kt`). What a
 * change should give is said from how the case was made and from tables written by hand, not by
 * judging it a second way. The trials are few by default; `-Pkatachi.pbt.scale=N` runs N times as
 * many and `-Pkatachi.pbt.seed=N` another seed (`KtFileContentPbtSpecSupport.kt`).
 */
class KtFileContentPropertySpec : FreeSpec({
    "沿うソースは違反 0 件" {
        checkCases("conforming", seedOffset = 0) { case, _ -> judgedCase(case).shouldBeEmpty() }
    }

    "列挙していない宣言を 1 つ足すと、その 1 件だけ（行を書いていない一覧でも）。anythingElse が許す一覧や、anythingElse が許したクラスのメンバなら 0 件" {
        checkCases("extra declaration", seedOffset = 1) { case, random ->
            val list = random.pick(case.lists())
            val kind = random.pick(if (list.path.isEmpty()) GenKind.entries else GenKind.entries - GenKind.TypeAlias)
            val extra = extraDeclaration(kind, random.nextInt(100))
            val rules = list.rules
            val expected = if (rules == null || rules.anythingElse.any { it.allowsPlain }) emptyList() else listOf(list.qualified(extra.name))
            val changed = case.updateList(list.path) { random.inserting(it, extra) }
            withClue(clueOf("added ${list.qualified(extra.name)}", changed)) {
                judgedCase(changed).map(::keyOf) shouldBe expected
            }
        }
    }

    "必須の宣言を 1 つ消すと、足りないが 1 件" {
        checkCases("missing declaration", seedOffset = 2, generate = { genCase(GenOptions(declarations = true)) }) { case, random ->
            val (list, index) = random.pickOrNull(requiredInstancesOf(case)) ?: return@checkCases
            val removed = list.declarations[index]
            val changed = case.updateList(list.path) { it.filterIndexed { i, _ -> i != index } }
            withClue(clueOf("removed ${list.qualified(removed.name)}", changed)) {
                val found = judgedCase(changed)
                found.map(::keyOf) shouldBe listOf(list.qualified(removed.name))
                found.single() shouldContain " | Missing: "
            }
        }
    }

    "* を含む required の行に当たる宣言を全部消すと、足りないが 1 件。1 つでも残れば 0 件" {
        checkCases("missing pattern", seedOffset = 12, generate = { genCase(GenOptions(declarations = true)) }) { case, random ->
            val (list, lineIndex) = random.pickOrNull(requiredPatternLinesOf(case)) ?: return@checkCases
            val line = list.rules?.lines?.get(lineIndex) ?: return@checkCases
            val made = list.declarations.indices.filter { list.declarations[it].origin == lineIndex }
            val keepOne = made.size > 1 && random.chance(50)
            val removed = if (keepOne) made.drop(1).toSet() else made.toSet()
            val changed = case.updateList(list.path) { it.filterIndexed { i, _ -> i !in removed } }
            val key = list.qualified(line.name.fillIn(case.fileName))
            withClue(clueOf("removed ${removed.size} of the ${made.size} declarations of the required line ${line.name}", changed)) {
                val found = judgedCase(changed)
                if (keepOne) {
                    found.shouldBeEmpty()
                } else {
                    found.map(::keyOf) shouldBe listOf(key)
                    found.single() shouldContain " | Missing: "
                }
            }
        }
    }

    "宣言の 1 か所（修飾子・アノテーション・親の型・型・引数・戻り値・種類）を変えると 1 件だけで、違うところを名指しする" {
        checkCases("one change", seedOffset = 3, generate = { genCase(GenOptions(declarations = true)) }) { case, random ->
            val (list, index) = random.pickOrNull(requiredInstancesOf(case)) ?: return@checkCases
            val declaration = list.declarations[index]
            val line = list.lineOf(declaration) ?: return@checkCases
            val placement = if (list.path.isEmpty()) GenPlacement.File else GenPlacement.ClassBody
            val change = random.pickOrNull(aspectChangesOf(line, declaration, case.fileName, placement)) ?: return@checkCases
            val changed = case.replaceDeclaration(list.path, index, change.changed)
            val key = list.qualified(declaration.name)
            withClue(clueOf("changed $key so that it differs in: ${change.differs}", changed)) {
                val found = judgedCase(changed)
                found.map(::keyOf) shouldBe listOf(key)
                val mismatch = found.single().endsWith(" | Differs in: ${change.differs}")
                // Allowed some other way (allow(anythingElse), a line named *), the line is missing instead.
                val missingWithFound = list.rules?.allowsOtherwise == true && found.single().contains(" | Found: ")
                withClue("found: ${found.single()}") { (mismatch || missingWithFound) shouldBe true }
            }
        }
    }

    "禁止の行に当たる宣言を足すと 1 件。許可の行にも当たっても違反" {
        checkCases("prohibited declaration", seedOffset = 4, generate = { genCase(GenOptions(prohibitedDeclaration = true)) }) { case, random ->
            val list = random.pickOrNull(case.lists().filter { list -> list.rules?.lines?.any { it.prohibited } == true }) ?: return@checkCases
            val rules = list.rules ?: return@checkCases
            val line = random.pick(rules.lines.filter { it.prohibited })
            val kind = line.kinds.first()
            val name = if (line.name.startsWith("*")) nameOf(kind, "One") + line.name.removePrefix("*") else line.name.removeSuffix("*") + "One"
            val keywords = line.alternatives.first().keywords
            // In the file, a line named * that allows it in full is put in too, now and then.
            val alsoAllowed = list.path.isEmpty() && random.chance(50)
            val allowing = GenLine(listOf(kind), listOf(GenModifiers(keywords)), name = "*")
            val withRules = if (alsoAllowed) case.copy(declarationRules = GenList(rules.entries + allowing)) else case
            val changed = withRules.updateList(list.path) { random.inserting(it, plainDeclaration(kind, name, keywords, emptyList(), container = null)) }
            withClue(clueOf("added ${list.qualified(name)}" + if (alsoAllowed) " and a line named * that allows it" else "", changed)) {
                val found = judgedCase(changed)
                found.map(::keyOf) shouldBe listOf(list.qualified(name))
                found.single() shouldContain " | Prohibited by: not "
            }
        }
    }

    "禁止の行の except に当たる宣言は禁止されず、許可の行に当たれば通る" {
        checkCases("excepted declaration", seedOffset = 11, generate = { genCase(GenOptions(prohibitedDeclaration = true)) }) { case, random ->
            val lists = case.lists().filter { list -> list.rules?.lines?.any { it.prohibited && it.except.isNotEmpty() } == true }
            val list = random.pickOrNull(lists) ?: return@checkCases
            val rules = list.rules ?: return@checkCases
            val line = random.pick(rules.lines.filter { it.prohibited && it.except.isNotEmpty() })
            val kind = line.kinds.first()
            val keywords = line.alternatives.first().keywords
            val name = line.except.first()
            // In the file, a line named * allows it: nothing is wrong. In a class, it is at most not allowed, never prohibited.
            val allowed = list.path.isEmpty()
            val allowing = GenLine(listOf(kind), listOf(GenModifiers(keywords)), name = "*")
            val withRules = if (allowed) case.copy(declarationRules = GenList(rules.entries + allowing)) else case
            val changed = withRules.updateList(list.path) { random.inserting(it, plainDeclaration(kind, name, keywords, emptyList(), container = null)) }
            withClue(clueOf("added ${list.qualified(name)}, which ${line.name} gives back" + if (allowed) ", and a line named * that allows it" else "", changed)) {
                val found = judgedCase(changed)
                if (allowed) found.shouldBeEmpty() else found.map(::keyOf).filter { it != list.qualified(name) }.shouldBeEmpty()
                found.none { " | Prohibited by: " in it } shouldBe true
            }
        }
    }

    "禁止の import を 1 つ足すと 1 件。許可の行にも当たっても違反" {
        checkCases("prohibited import", seedOffset = 5, generate = { genCase(GenOptions(prohibitedImport = true)) }) { case, random ->
            val line = case.importLines.firstOrNull { it.prohibited } ?: return@checkCases
            val path = random.pick(IMPORT_TABLE.keys.filter { importLineMatches(line.pattern, line.except, it) })
            val alsoAllowed = random.chance(50) && case.importLines.none { it.pattern == "**" }
            val withRules = if (alsoAllowed) case.copy(importLines = case.importLines + GenImportLine("**")) else case
            val changed = withRules.copy(imports = random.inserting(withRules.imports, GenImport(path)))
            withClue(clueOf("imported $path" + if (alsoAllowed) " and allowed **" else "", changed)) {
                val found = judgedCase(changed)
                found.map(::keyOf) shouldBe listOf("import $path")
                found.single() shouldContain "Prohibited by: not import ${line.pattern}"
            }
        }
    }

    "except で禁止から外した import は、許可の行に当たるときだけ通る" {
        checkCases("excepted import", seedOffset = 6, generate = { genCase(GenOptions(exceptImport = true)) }) { case, random ->
            val line = case.importLines.firstOrNull { it.prohibited && it.except.isNotEmpty() } ?: return@checkCases
            val path = IMPORT_TABLE.keys.first { line.pattern in IMPORT_TABLE.getValue(it) && !importLineMatches(line.pattern, line.except, it) }
            if (case.imports.any { it.path == path }) return@checkCases
            val changed = case.copy(imports = random.inserting(case.imports, GenImport(path)))
            val expected = if (importAllowed(case.importLines, path)) emptyList() else listOf("import $path")
            withClue(clueOf("imported $path", changed)) { judgedCase(changed).map(::keyOf) shouldBe expected }
        }
    }

    "どの許可の行にも当たらない import を足すと 1 件。import の行を書いていなくても 1 件" {
        checkCases("unlisted import", seedOffset = 7) { case, random ->
            val path = random.pick(listOf("org.other.Thing", "com.exampleplus.Thing", "kotlin.time.Duration"))
            if (case.imports.any { it.path == path }) return@checkCases
            val changed = case.copy(imports = random.inserting(case.imports, GenImport(path)))
            val expected = if (importAllowed(case.importLines, path)) emptyList() else listOf("import $path")
            withClue(clueOf("imported $path", changed)) { judgedCase(changed).map(::keyOf) shouldBe expected }
        }
    }

    "* の無い import の行に当たる import を消しても 0 件。import の行は許すだけ" {
        val generate: Random.() -> GenCase = { generateSequence { genCase() }.first { case -> case.imports.any { it.path == EXACT_IMPORT } } }
        checkCases("removed import", seedOffset = 8, generate = generate) { case, _ ->
            if (case.imports.none { it.path == EXACT_IMPORT }) return@checkCases
            val changed = case.copy(imports = case.imports.filterNot { it.path == EXACT_IMPORT })
            withClue(clueOf("removed import $EXACT_IMPORT", changed)) { judgedCase(changed).shouldBeEmpty() }
        }
    }

    "package を変えると、許可の行に当たらないか禁止の行に当たれば 1 件。消すと 1 件" {
        val generate: Random.() -> GenCase = { generateSequence { genCase() }.first { it.packageLines.isNotEmpty() } }
        checkCases("package", seedOffset = 9, generate = generate) { case, random ->
            if (case.packageLines.isEmpty()) return@checkCases
            val packageName = random.pickOrNull(PACKAGE_TABLE.keys.filter { it != case.packageName }.takeIf { random.chance(80) }.orEmpty())
            val expected = when {
                packageName == null -> listOf("package (none)")
                packageAllowed(case.packageLines, packageName) -> emptyList()
                else -> listOf("package $packageName")
            }
            val changed = case.copy(packageName = packageName)
            withClue(clueOf("changed the package to ${packageName ?: "none"}", changed)) { judgedCase(changed).map(::keyOf) shouldBe expected }
        }
    }

    "宣言・メンバ・import の順を入れ替え、空行やコメントを入れても、行番号のほかは同じ結果" {
        checkCases("order and noise", seedOffset = 10) { case, random ->
            val changed = random.changeSome(case)
            val plain = judgedCase(changed).map(::withoutLineNumbers).sorted()
            val reordered = changed.shuffled(random)
            val noisy = sourceOf(reordered, noise = Random(random.nextLong()))
            withClue("the source before:\n${sourceOf(changed)}\nreordered, with comments:\n$noisy") {
                judged(noisy, reordered.path) { writeCase(reordered) }.map(::withoutLineNumbers).sorted() shouldBe plain
            }
        }
    }
})

/** Trials per judging property unless `-Pkatachi.pbt.scale` says otherwise: each parses one or two files. */
private const val DEFAULT_TRIALS: Int = 300

/** One trial: a case, and the seed of the choices a property makes on it (what to add, remove or change). */
private data class Trial(val case: GenCase, val choices: Long)

private fun checkCases(
    property: String,
    seedOffset: Long,
    generate: Random.() -> GenCase = { genCase() },
    check: (GenCase, Random) -> Unit,
) = checkTrials(
    property = property,
    trials = pbtTrials(DEFAULT_TRIALS),
    seed = PBT_SEED + seedOffset,
    generate = { random -> Trial(random.generate(), random.nextLong()) },
    shrinks = { trial -> trial.case.shrinks().map { Trial(it, trial.choices) } },
    describe = { trial -> "choices: ${trial.choices}\n" + describeCase(trial.case) },
    check = { trial -> check(trial.case, Random(trial.choices)) },
)

private fun clueOf(what: String, changed: GenCase): String = "$what\nthe source judged:\n${sourceOf(changed)}"

/**
 * The declarations, with their lists, that a required line named in full was made for: the one
 * declaration of that name. A required line with `*` may have several, see [requiredPatternLinesOf].
 */
private fun requiredInstancesOf(case: GenCase): List<Pair<ListRef, Int>> = case.lists().flatMap { list ->
    list.declarations.indices.filter { index ->
        list.lineOf(list.declarations[index])?.let { it.isRequired && '*' !in it.name } == true
    }.map { list to it }
}

/** The required lines with `*` in their name, with their lists and their index among the list's lines. */
private fun requiredPatternLinesOf(case: GenCase): List<Pair<ListRef, Int>> = case.lists().flatMap { list ->
    list.rules?.lines.orEmpty().withIndex().filter { (_, line) -> line.isRequired && '*' in line.name }.map { list to it.index }
}

/** Whether the package lines allow [packageName] in a file whose directory names another package. */
private fun packageAllowed(lines: List<GenPackageLine>, packageName: String): Boolean {
    val matching = PACKAGE_TABLE.getValue(packageName)
    if (lines.any { it.prohibited && it.pattern in matching }) return false
    return lines.any { !it.prohibited && it.pattern != null && it.pattern in matching }
}

/** [case] with up to three of the changes the properties above make, so that the judging has something to say. */
private fun Random.changeSome(case: GenCase): GenCase {
    var changed = case
    repeat(nextInt(0, 4)) {
        changed = when (nextInt(3)) {
            0 -> {
                val list = pick(changed.lists())
                val kind = pick(if (list.path.isEmpty()) GenKind.entries else GenKind.entries - GenKind.TypeAlias)
                changed.updateList(list.path) { inserting(it, extraDeclaration(kind, nextInt(1000))) }
            }
            1 -> pickOrNull(requiredInstancesOf(changed))?.let { (list, index) ->
                changed.updateList(list.path) { it.filterIndexed { i, _ -> i != index } }
            } ?: changed
            else -> pickOrNull(requiredInstancesOf(changed))?.let { (list, index) ->
                val declaration = list.declarations[index]
                val line = list.lineOf(declaration)
                val placement = if (list.path.isEmpty()) GenPlacement.File else GenPlacement.ClassBody
                line?.let { pickOrNull(aspectChangesOf(it, declaration, changed.fileName, placement)) }
                    ?.let { changed.replaceDeclaration(list.path, index, it.changed) }
            } ?: changed
        }
    }
    return changed
}
