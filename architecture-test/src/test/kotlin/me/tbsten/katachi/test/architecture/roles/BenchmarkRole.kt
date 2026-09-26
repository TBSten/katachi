package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.gradle.sourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * The role of `:benchmark`, the JMH benchmarks of katachi's own performance.
 *
 * Never published and not part of `check`: it is run on demand or by a scheduled job, so it
 * carries none of the library's layer rules and sits in the undocumented `tool` group.
 */
fun DeclarationContainerScope.benchmark() = "Benchmark" {
    title = "性能ベンチマーク"
    summary = "JMH で walk・ロール照合・layout 評価・Konsist 解析・初回の assert() を測る、公開しないモジュール。実プロジェクトでの計測と gradle-profiler のシナリオも置く"
    example("WalkBench.kt", "ファイル数 × ロール数 × モジュール数を振って、メモリ上の木で検査全体を測る")
    example("BenchmarkSupport.kt", "ベンチマークが共有する定義の組み立てと、測る前の確かめ")
    layout {
        ":benchmark".module {
            description = "JMH のベンチマークと、実プロジェクトでの計測。どちらも check には入らない"
            "jmh".sourceSet / kotlin / "me/tbsten/katachi/benchmark" / "*Bench".ktFile()
            "jmh".sourceSet / kotlin / "me/tbsten/katachi/benchmark" / "BenchmarkSupport".ktFile()
            // Measures validate() on a real project cloned by the nightly workflow: the runner,
            // and one coarse definition per project it knows.
            "realProject".sourceSet / kotlin / "me/tbsten/katachi/benchmark/realproject" {
                "RealProjectMain".ktFile()
                "*Architecture".ktFile()
            }
            // gradle-profiler's scenarios for the Gradle side, run against sample/jvm.
            "gradle-profiler" / "*.scenarios".file()
        }
    }
}
