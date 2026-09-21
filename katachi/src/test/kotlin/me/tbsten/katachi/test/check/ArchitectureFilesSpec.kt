package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.check.FileSelection
import me.tbsten.katachi.dsl.architecture

class ArchitectureFilesSpec : FreeSpec({
    "files を書かなければ gitTracked() になる" {
        architecture { }.files shouldBe FileSelection.GitTracked
    }

    "files = wholeTree() を書けば WholeTree になる" {
        val arch = architecture {
            files = wholeTree()
            "domain".group { "UseCase" { } }
        }

        arch.files shouldBe FileSelection.WholeTree
        arch.allRoles.map { it.name } shouldBe listOf("UseCase")
    }

    "files = gitTracked() を明示しても既定と同じ値になる" {
        architecture { files = gitTracked() }.files shouldBe FileSelection.GitTracked
    }
})
