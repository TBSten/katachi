package com.example.sample.testing

import com.example.sample.modulePackage
import me.tbsten.katachi.dsl.ArchitectureScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * Roles that exist for testing: the shared fakes in `:testing`, the tests themselves, and
 * the architecture definition.
 *
 * The definition lives in `:architecture-test`, a module that belongs to no layer of the
 * application. It is still code someone has to maintain, so it gets a role of its own
 * rather than hiding inside `Test`: `ArchitectureDefinition` describes the shape, `Test`
 * asserts behaviour. The two share one directory and are told apart by the file name, which
 * is why the definition is `ProjectArchitecture.kt` plus the `*Roles.kt` of the four
 * packages, and everything `*Spec.kt` or `*Test.kt` is a test.
 *
 * `:architecture-test` is also the second module whose package does not follow its module
 * path — it would come out as `com/example/sample/architectureTest` — so the two roles below
 * write `com/example/sample` out as a key. `:testing` does follow it and uses
 * `modulePackage`, which is what makes the difference visible side by side.
 *
 * Deliberately not `inline`. katachi reads the declaration site off the stack trace, and an
 * inlined frame reports a line number remapped past the end of the caller's file.
 */
fun ArchitectureScope.testingRoles() {
    "testing".group {
        title = "テスト"

        "Fake" {
            title = "フェイク"
            summary = ":testing に置く、他モジュールのテストから使う偽の実装"
            example("FakeUserRepository", "UserRepository のメモリ実装")
            layout {
                ":testing".module {
                    mainSourceSet / kotlin / modulePackage / "Fake*".ktFile()
                }
            }
        }

        "Test" {
            title = "テストコード"
            summary = "各モジュールの src/test/kotlin に置くテストそのもの"
            example("ProjectArchitectureSpec", "この定義そのものを検証するテスト")
            layout {
                // `:architecture-test` is the only module of this sample with tests. The
                // app modules are checked through it, so nothing else has a `src/test`.
                ":architecture-test".module {
                    testSourceSet / kotlin / "com/example/sample" {
                        "*Spec".ktFile()
                        "*Test".ktFile()
                    }
                }
            }
        }

        "ArchitectureDefinition" {
            title = "アーキテクチャ定義"
            summary = "katachi の DSL で書かれた役割の定義。どのレイヤーにも属さない"
            example("ProjectArchitecture.kt", "定義の入口。各 package の拡張関数を呼ぶ")
            example("UiRoles.kt", "UI レイヤーの役割を宣言する拡張関数")
            layout {
                ":architecture-test".module {
                    testSourceSet / kotlin / "com/example/sample" {
                        "ProjectArchitecture".ktFile()
                        // One package per kind of concern, each holding `*Roles.kt` and
                        // nothing else. Writing the four out means adding a fifth package is
                        // a decision this definition records, not something that slips in.
                        "application" { "*Roles".ktFile() }
                        "testing" { "*Roles".ktFile() }
                        "gradle" { "*Roles".ktFile() }
                        "tool" { "*Roles".ktFile() }
                    }
                }
            }
        }
    }
}
