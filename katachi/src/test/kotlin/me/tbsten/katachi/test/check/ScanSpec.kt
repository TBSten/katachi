package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.files.internal.GitTrackedFileSystem
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.processor.internal.process
import me.tbsten.katachi.test.dsl.files.fakeFileSystem

class ScanSpec : FreeSpec({
    "ルート直下" - {
        "宣言したファイルは違反にならない" {
            layoutArchitecture { ".gitignore".file() }
                .validate(repositoryOf { ".gitignore"() })
                .labels() shouldBe emptyList()
        }

        "宣言のないファイルは Unexpected になる" {
            layoutArchitecture { ".gitignore".file() }
                .validate(repositoryOf { ".gitignore"(); "notes.md"() })
                .labels() shouldBe listOf("[UnexpectedFile] notes.md")
        }

        "宣言のないディレクトリは配下のファイル数によらず1件だけ報告される" {
            layoutArchitecture { ".gitignore".file() }
                .validate(
                    repositoryOf {
                        ".gitignore"()
                        "tmp-experiment" {
                            "a.kt"()
                            "b.kt"()
                            "deep" { "c.kt"() }
                        }
                    },
                )
                .labels() shouldBe listOf("[UnexpectedDirectory] tmp-experiment")
        }
    }

    "宣言済みディレクトリの中" - {
        "空のディレクトリブロックの配下にあるファイルは Unexpected になる" {
            layoutArchitecture { "di" { } }
                .validate(repositoryOf { "di" { "Module.kt"() } })
                .labels() shouldBe listOf("[UnexpectedFile] di/Module.kt")
        }

        "未知のディレクトリはルート直下でなくても打ち切られる" {
            layoutArchitecture { "src" { "App.kt".file() } }
                .validate(
                    repositoryOf {
                        "src" {
                            "App.kt"()
                            "generated" { "a.kt"(); "b.kt"() }
                        }
                    },
                )
                .labels() shouldBe listOf("[UnexpectedDirectory] src/generated")
        }

        "ワイルドカードにマッチするファイルは許可され同じディレクトリの別名は Unexpected になる" {
            layoutArchitecture { "useCase" / "*UseCase".ktFile() }
                .validate(
                    repositoryOf {
                        "useCase" {
                            "GetUserUseCase.kt"()
                            "TokenRefresher.kt"()
                        }
                    },
                )
                .labels() shouldBe listOf("[UnexpectedFile] useCase/TokenRefresher.kt")
        }
    }

    "anyFile" - {
        "直下の任意のファイルを許可する" {
            layoutArchitecture { "generated" { anyFile() } }
                .validate(repositoryOf { "generated" { "a.kt"(); "b.txt"() } })
                .labels() shouldBe emptyList()
        }

        "サブディレクトリは許可しない" {
            layoutArchitecture { "generated" { anyFile() } }
                .validate(repositoryOf { "generated" { "a.kt"(); "nested" { "b.kt"() } } })
                .labels() shouldBe listOf("[UnexpectedDirectory] generated/nested")
        }
    }

    "ignore" - {
        "配下は何階層下でも違反にならない" {
            layoutArchitecture { "build".ignore() }
                .validate(
                    repositoryOf {
                        "build" {
                            "output.jar"()
                            "classes" { "kotlin" { "main" { "App.class"() } } }
                        }
                    },
                )
                .labels() shouldBe emptyList()
        }

        "ブロック内の ignore でも同じ結果になる" {
            layoutArchitecture { "build" { ignore() } }
                .validate(repositoryOf { "build" { "nested" { "output.jar"() } } })
                .labels() shouldBe emptyList()
        }
    }

    "Missing" - {
        "ワイルドカードを含まない宣言に実体が無いと1件報告される" {
            layoutArchitecture { "gradle" / "libs.versions.toml".file() }
                .validate(repositoryOf { })
                .labels() shouldBe listOf("[MissingFile] gradle/libs.versions.toml")
        }

        "実体があれば報告されない" {
            layoutArchitecture { "gradle" / "libs.versions.toml".file() }
                .validate(repositoryOf { "gradle" { "libs.versions.toml"() } })
                .labels() shouldBe emptyList()
        }

        "ワイルドカードを含む宣言はマッチが0件でも報告されない" {
            layoutArchitecture { "repository" / "*Repository".ktFile() }
                .validate(repositoryOf { "repository" { } })
                .labels() shouldBe emptyList()
        }

        "optional を付けた宣言は報告されない" {
            layoutArchitecture { "CHANGELOG.md".file().optional() }
                .validate(repositoryOf { })
                .labels() shouldBe emptyList()
        }

        "ディレクトリは実体が無くても報告されない" {
            layoutArchitecture { "docs" { anyFile() } }
                .validate(repositoryOf { })
                .labels() shouldBe emptyList()
        }
    }

    "複数の役割" - {
        "1つのファイルに2つの役割がマッチしてもエラーにはならず、重なりの Warning だけが出る" {
            // The two declarations overlap on `core/GetUser.kt` without being the same text
            // (`core/*.kt` vs `core/GetUser.kt`). The walk allows the file — a file is fine
            // as long as *some* role allows it — and reports the overlap itself as the
            // `AmbiguousLayout` warning no comparison of the pattern text could have found.
            val violations = architectureOf {
                "domain".group {
                    "UseCase".role { layout { "core" / "*.kt".file() } }
                    "Api".role { layout { "core" / "GetUser.kt".file() } }
                }
            }.validate(repositoryOf { "core" { "GetUser.kt"() } })

            violations.labels() shouldBe listOf("[AmbiguousLayout] core/GetUser.kt")
            violations.map { it.severity } shouldBe listOf(Severity.Warning)
        }

        "役割ごとの宣言は合わせて1つの allow list になる" {
            architectureOf {
                "domain".group {
                    "UseCase".role { layout { "core" / "*UseCase".ktFile() } }
                    "Repository".role { layout { "core" / "*Repository".ktFile() } }
                }
            }
                .validate(
                    repositoryOf {
                        "core" {
                            "GetUserUseCase.kt"()
                            "UserRepository.kt"()
                            "TokenRefresher.kt"()
                        }
                    },
                )
                .labels() shouldBe listOf("[UnexpectedFile] core/TokenRefresher.kt")
        }
    }

    "記法の等価性" - {
        "スラッシュ連結と入れ子ブロックで検査結果が同じになる" {
            val tree = repositoryOf { "src" { "main" { "App.kt"(); "notes.txt"() } } }
            val chained = layoutArchitecture { "src" / "main" / "App".ktFile() }
            val nested = layoutArchitecture { "src" { "main" { "App".ktFile() } } }

            chained.validate(tree).labels() shouldBe listOf("[UnexpectedFile] src/main/notes.txt")
            chained.validate(tree).labels() shouldBe nested.validate(tree).labels()
        }
    }

    "git のディレクトリ" - {
        ".git はどの階層にあっても files の指定によらず違反にならない" {
            // `repositoryOf` already puts `.git/HEAD` at the root; a nested one stands for a
            // submodule checked out as a directory.
            layoutArchitecture {
                ".gitignore".file()
                "module" { }
            }
                .validate(
                    repositoryOf {
                        ".gitignore"()
                        "module" { ".git" { "HEAD"() } }
                    },
                )
                .labels() shouldBe emptyList()
        }

        "worktree やサブモジュールの .git ファイルも違反にならない" {
            // In a worktree and in a submodule `.git` is a file that points at the real
            // repository. `gradlew` is the root marker here, so the walk reaches the file.
            val tree = fakeFileSystem(workingDirectory = "/repo") {
                "/repo" {
                    ".git"()
                    "gradlew"()
                    "module" { ".git"() }
                }
            }

            layoutArchitecture {
                "gradlew".file()
                "module" { }
            }.validate(tree).labels() shouldBe emptyList()
        }

        ".gradle と .idea は宣言が無ければほかのディレクトリと同じく Unexpected になる" {
            layoutArchitecture {
                ".gitignore".file()
                "module" { }
            }
                .validate(
                    repositoryOf {
                        ".gitignore"()
                        ".gradle" { "cache.bin"() }
                        ".idea" { "workspace.xml"() }
                        "module" { ".gradle" { "cache.bin"() } }
                    },
                )
                .labels() shouldBe listOf(
                    "[UnexpectedDirectory] .gradle",
                    "[UnexpectedDirectory] .idea",
                    "[UnexpectedDirectory] module/.gradle",
                )
        }

        ".idea を ignore すれば中は見ない" {
            layoutArchitecture {
                ".gitignore".file()
                ".idea".ignore()
            }
                .validate(
                    repositoryOf {
                        ".gitignore"()
                        ".idea" { "workspace.xml"(); "codeStyles" { "Project.xml"() } }
                    },
                )
                .labels() shouldBe emptyList()
        }

        ".idea の中に宣言したファイルは役割に入る" {
            val definition = layoutArchitecture { ".idea" / "codeStyles" / "Project.xml".file() }
            val tree = repositoryOf { ".idea" { "codeStyles" { "Project.xml"() } } }

            definition.validate(tree).labels() shouldBe emptyList()
            definition.process(tree) { context -> context.filesOf(context.roles.single()) } shouldBe
                listOf(".idea/codeStyles/Project.xml")
        }

        "gradle wrapper は役割を書かない限り Unexpected になる" {
            layoutArchitecture { "gradle" / "libs.versions.toml".file() }
                .validate(
                    repositoryOf {
                        "gradle" {
                            "libs.versions.toml"()
                            "wrapper" { "gradle-wrapper.properties"() }
                        }
                    },
                )
                .labels() shouldBe listOf("[UnexpectedDirectory] gradle/wrapper")
        }
    }

    "検査対象のファイル集合" - {
        val tree = fakeFileSystem(workingDirectory = "/repo") {
            "/repo" {
                "gradlew"()
                "local.properties"()
                "build" { "output.jar"() }
            }
        }
        val definition = layoutArchitecture { "gradlew".file() }

        "git が見せないファイルは宣言が無くても違反にならない" {
            val tracked = GitTrackedFileSystem(tree, FsPath.of("/repo"), listOf("gradlew"))

            definition.validate(tracked).labels() shouldBe emptyList()
        }

        "ファイルツリーをそのまま走査すると同じファイルが Unexpected になる" {
            definition.validate(tree).labels() shouldBe listOf(
                "[UnexpectedDirectory] build",
                "[UnexpectedFile] local.properties",
            )
        }

        "ignore 対象の中で追跡しているファイル" - {
            // `.idea/*` with `!.idea/codeStyles/`: git reports the tracked `Project.xml` and
            // nothing else below `.idea`, while `workspace.xml` sits on disk next to it.
            val ideTree = fakeFileSystem(workingDirectory = "/repo") {
                "/repo" {
                    "gradlew"()
                    ".gradle" { "cache.bin"() }
                    ".idea" {
                        "workspace.xml"()
                        "codeStyles" { "Project.xml"() }
                    }
                }
            }
            val tracked = GitTrackedFileSystem(
                ideTree,
                FsPath.of("/repo"),
                listOf("gradlew", ".idea/codeStyles/Project.xml"),
            )

            "宣言どおりに見える" {
                val declared = layoutArchitecture {
                    "gradlew".file()
                    ".idea" / "codeStyles" / "Project.xml".file()
                }

                declared.validate(tracked).labels() shouldBe emptyList()
                declared.process(tracked) { context -> context.filesOf(context.roles.single()) } shouldBe
                    listOf(".idea/codeStyles/Project.xml", "gradlew")
            }

            "ワイルドカードで宣言しても追跡しているファイルだけが役割に入る" {
                // A wildcard never becomes a required file, so `labels()` alone would stay
                // empty even if the walk skipped `.idea` altogether. The files the role
                // holds are what proves the walk went in.
                val declared = layoutArchitecture {
                    "gradlew".file()
                    ".idea" / "codeStyles" / "*.xml".file()
                }

                declared.validate(tracked).labels() shouldBe emptyList()
                declared.process(tracked) { context -> context.filesOf(context.roles.single()) } shouldBe
                    listOf(".idea/codeStyles/Project.xml", "gradlew")
            }

            "宣言が無ければ .idea が Unexpected になる" {
                layoutArchitecture { "gradlew".file() }
                    .validate(tracked)
                    .labels() shouldBe listOf("[UnexpectedDirectory] .idea")
            }

            "追跡していないファイルは宣言があっても Missing になる" {
                layoutArchitecture {
                    "gradlew".file()
                    ".idea" / "codeStyles" / "Project.xml".file()
                    ".idea" / "workspace.xml".file()
                }
                    .validate(tracked)
                    .labels() shouldBe listOf("[MissingFile] .idea/workspace.xml")
            }

            "git が何も報告しない .gradle は宣言が無くても見えない" {
                layoutArchitecture {
                    "gradlew".file()
                    ".idea" / "codeStyles" / "Project.xml".file()
                }
                    .validate(tracked)
                    .labels() shouldBe emptyList()
            }
        }
    }

    "走査の起点" - {
        "作業ディレクトリがサブモジュールでもリポジトリルートから走査する" {
            val tree = repositoryOf(workingDirectory = "/repo/app/src") {
                ".gitignore"()
                "app" { "src" { "App.kt"() } }
            }

            layoutArchitecture {
                ".gitignore".file()
                "app" / "src" / "App".ktFile()
            }.validate(tree).labels() shouldBe emptyList()
        }
    }
})
