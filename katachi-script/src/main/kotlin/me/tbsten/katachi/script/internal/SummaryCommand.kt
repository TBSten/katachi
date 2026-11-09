package me.tbsten.katachi.script.internal

import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/** `summary`: the message to give back to the user, or, before the end, where to resume. */
internal fun ScriptContext.runSummary(): Int {
    val workdir = requireWorkdir()
    // Only read where the module goes; a missing definition does not stop it (it resumes sessions cut before scaffold).
    val module = resolveModuleDir()
    val checkListPath = workdir.resolve(CHECK_LIST_FILE)
    if (!checkListPath.isRegularFile()) die(Messages.Summary.notFoundRunInit(fileUri(checkListPath)))
    val cl = readTree(checkListPath, "checklist", Messages.Summary.cannotReadJsonCheck)
    // The report's questions too: once they never reached the user before the end.
    val reportPath = workdir.resolve(REPORT_FILE)
    val report = (if (reportPath.isRegularFile()) readJsonBlockOrNull(reportPath, "report")?.toTree().asMap() else null) ?: JMap()
    val definition = definitionDir(module)
    printSummary(cl, report, module, definition, definition.isDirectory(), Guide.bundled(lang).ids)
    return 0
}

private fun ScriptContext.printSummary(cl: JMap, report: JMap, module: ModuleDir, definition: Path, definitionExists: Boolean, toc: List<String>) {
    val templates = report.listOrEmpty("templates").maps()
    val meta = cl.mapOrEmpty("meta")
    val workdir = meta.str("workdir")?.takeIf { it.isNotEmpty() }
    val projectRoot = meta.str("projectRoot")?.takeIf { it.isNotEmpty() }?.let { resolve(it) } ?: cwd
    fun uri(path: String): String = fileUriOf(projectRoot.resolve(path))
    fun inWorkdir(name: String): String = if (workdir == null) pick(Messages.Summary.workingDirectory) + name else uri("$workdir/$name")
    val workdirLine = pick(Messages.Summary.workingDirectoryS).format(workdir?.let(::uri) ?: pick(Messages.Summary.workdirPlaceholder))

    val violations = cl.listOrEmpty("violations")
    val changed = cl.listOrEmpty("changedFiles")
    val items = cl.requiredItems()
    val done = items.filter { truthy(it["done"]) }
    val optionalItems = cl.optionalItems()
    val (reportAnswered, reportUnanswered) = answerCounts(report, Target.REPORT)
    val (clAnswered, clUnanswered) = answerCounts(cl, Target.CHECK_LIST)

    if (done.size < items.size) {
        say(Messages.Summary.couldNotSetUp)
        say("")
        say(Messages.Summary.unfinishedItems)
        for (item in items) if (!truthy(item["done"])) out("- ${pyStr(item["id"])} ${item["label"]?.let(::pyStr) ?: ""}")
        // So that the next session can go on without relying on what was said in this one.
        say("")
        say(Messages.Summary.resume)
        out(workdirLine)
        out(pick(Messages.Summary.doneS).format(done.joinToString(" ") { pyStr(it["id"]) }.ifEmpty { "-" }))
        nextSection(items, toc)?.let { section ->
            say(Messages.Summary.nextSectionGuide(section))
        }
        if (definitionExists) {
            say(Messages.Summary.definition(fileUriOf(definition)))
        } else {
            say(Messages.Summary.definitionNotYetScaffold(module.dir))
        }
        say(Messages.Summary.questionsReportAnsweredUnanswered(reportAnswered, reportUnanswered.size, clAnswered, clUnanswered.size))
        say("")
        say(Messages.Summary.checkList)
        out(inWorkdir(CHECK_LIST_FILE))
        return
    }

    val modulePath = module.gradlePath
    if (violations.isNotEmpty()) say(Messages.Summary.setUpKatachi) else say(Messages.Summary.setUpKatachiDone)
    say("")
    say(Messages.Summary.generatedCodeNeedsYour)
    say("")
    say(Messages.Summary.checkListLine(inWorkdir(CHECK_LIST_FILE)))
    say(Messages.Summary.codebaseReport(inWorkdir(REPORT_FILE)))
    if (violations.isNotEmpty()) {
        say(Messages.Summary.testResultArchitectureViolations(modulePath, violations.size))
    } else {
        say(Messages.Summary.testResultAllGreen(modulePath))
    }
    printQuestions(
        pick(Messages.Summary.questionsYouCheckList),
        pick(Messages.Summary.questionsUserCheckList),
        clAnswered,
        clUnanswered,
    )
    printQuestions(pick(Messages.Summary.inconsistenciesCodebaseReport), pick(Messages.Summary.questionsReport), reportAnswered, reportUnanswered)
    say(Messages.Summary.changedFiles(changed.size))
    out("- Next action:")
    say(Messages.Summary.reviewProjectarchitectureKtUnder(uri(module.dir)))
    // The optional steps not done yet, as what can be done next (their labels are past tense, so they get their own wording).
    val hints = mapOf(
        "6-1" to pick(Messages.Summary.runOneRealImplementation),
        "6-2" to pick(Messages.Summary.addTestCi(modulePath)),
        "6-3" to pick(Messages.Summary.youCanSetUp(modulePath)),
    )
    for (item in optionalItems) {
        if (truthy(item["done"])) continue
        val id = pyStr(item["id"])
        if (id == "6-4") {
            // No Role worth a template: recommend nothing.
            if (templates.isEmpty()) continue
            say(Messages.Summary.youCanSetUpTemplates(templates.size))
            for (template in templates) {
                val files = template.listOrEmpty("files").joinToString(", ") { pyStr(it) }.ifEmpty { pick(Messages.Summary.blank) }
                val params = template.listOrEmpty("params").joinToString(", ") { pyStr(it) }.ifEmpty { pick(Messages.Summary.noParams) }
                val role = template["role"]?.takeIf(::truthy)?.let(::pyStr) ?: pick(Messages.Summary.blank)
                say(Messages.Summary.parameters(role, files, params))
            }
            continue
        }
        out("    - ${hints[id] ?: item["label"]?.let(::pyStr) ?: id}")
    }

    // The resume block, in the finished form too: the guide's last sections send the next session here.
    val optionalTitles = mapOf(
        "6-1" to ("6-A" to pick(Messages.Summary.confirmInstallationWorks)),
        "6-2" to ("6-B" to pick(Messages.Summary.addCi)),
        "6-3" to ("6-C" to pick(Messages.Summary.setUpDocumentationGeneration)),
        "6-4" to ("6-D" to pick(Messages.Summary.setUpCodeGeneration)),
    )
    say("")
    say(Messages.Summary.resume)
    out(workdirLine)
    out(pick(Messages.Summary.doneS).format((items + optionalItems).filter { truthy(it["done"]) }.joinToString(" ") { pyStr(it["id"]) }))
    say(Messages.Summary.nextSectionPickOne)
    say(Messages.Summary.fixWhatReviewPointed)
    for (item in optionalItems) {
        val id = pyStr(item["id"])
        if (truthy(item["done"]) || id !in optionalTitles) continue
        if (id == "6-4" && templates.isEmpty()) continue
        val (section, title) = optionalTitles.getValue(id)
        out("    - $title: $AGENT_COMMAND guide $section")
    }
}

/** (answered, unanswered with their questions) for one file. */
private fun answerCounts(doc: JMap, target: Target): Pair<Int, List<Pair<String, JMap>>> {
    val questions = doc.listOrEmpty("questions")
    val answers = doc["answers"].asMap() ?: JMap()
    val ids = questions.mapIndexed { n, q -> questionId(target, q.asMap() ?: JMap(), n) }
    val answered = ids.count { truthy(answers[it]) }
    val unanswered = ids.zip(questions).filter { (id, _) -> !truthy(answers[id]) }.map { (id, q) -> id to (q.asMap() ?: JMap()) }
    return answered to unanswered
}

/** Answered ones as a count, unanswered ones listed with id, question and recommendation. */
private fun ScriptContext.printQuestions(label: String, where: String, answered: Int, unanswered: List<Pair<String, JMap>>) {
    if (answered == 0 && unanswered.isEmpty()) return
    say(Messages.Summary.answeredUnanswered(label, answered, unanswered.size, where))
    for ((id, q) in unanswered) {
        out("    - $id ${questionLine(q)}")
    }
}

/** `<question> → 推奨: <recommendation>`, as summary and answers show an unanswered question. */
internal fun ScriptContext.questionLine(q: JMap): String {
    val text = q["question"]?.takeIf(::truthy)?.let(::pyStr) ?: pick(Messages.Summary.blank)
    val recommendation = q["recommendation"]?.takeIf(::truthy)?.let(::pyStr)
    return if (recommendation == null) text else text + pick(Messages.Summary.recommended(recommendation))
}

internal fun Path.readTextOrNull(): String? = if (isRegularFile()) readText() else null
