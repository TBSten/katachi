package com.example.kmp

import io.kotest.core.spec.style.FreeSpec
import me.tbsten.katachi.check.assert

/**
 * The whole point of adopting katachi, in one call.
 *
 * Everything a user writes is here: a definition held in a top level `val`, and one test that
 * asserts it. There is no list of rules to keep in step with the definition — the definition
 * *is* the rule — and a failure prints every violation at once instead of one per run.
 *
 * Nothing in this file mentions a path, a file system or a project root: `assert()` finds the
 * project root by walking up from the working directory (see [ProjectRootSpec]) and reads the
 * tree from there.
 */
class ProjectLayoutSpec : FreeSpec({
    "リポジトリの中身が定義した layout どおりである" {
        projectArchitecture.assert()
    }
})
