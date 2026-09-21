package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gitTracked
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.fs.FileSelection
import me.tbsten.katachi.fs.FsPath
import me.tbsten.katachi.fs.KatachiFileSystem
import me.tbsten.katachi.fs.ProjectRoot

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

    "利用者が実装した FileSelection を files に渡すと、その集合が検査される" {
        val arch = architecture {
            files = HidesGeneratedFiles
            "app".group { "Root" { layout { "gradlew".file() } } }
        }

        arch.files shouldBe HidesGeneratedFiles
        arch.validate(repositoryOf { "gradlew"(); "generated.txt"() })
            .labels() shouldBe emptyList()
    }

    "同じツリーでも wholeTree() なら隠していたファイルが Unexpected になる" {
        architectureOf { "app".group { "Root" { layout { "gradlew".file() } } } }
            .validate(repositoryOf { "gradlew"(); "generated.txt"() })
            .labels() shouldBe listOf("[UnexpectedFile] generated.txt")
    }
})

/**
 * A [FileSelection] of the kind a project outside katachi writes: one generated file never
 * reaches the walk, so the check never learns that it was there.
 *
 * It is written against the published surface only — implement [FileSelection], wrap the
 * delegate — which is what makes it worth having in a spec: nothing here is a privilege of
 * katachi's own two selections.
 */
private object HidesGeneratedFiles : FileSelection {
    override fun fileSystemFor(
        delegate: KatachiFileSystem,
        projectRoot: ProjectRoot,
    ): KatachiFileSystem = object : KatachiFileSystem by delegate {
        override fun list(directory: FsPath): List<FsPath> =
            delegate.list(directory).filter { it.name != "generated.txt" }
    }
}
