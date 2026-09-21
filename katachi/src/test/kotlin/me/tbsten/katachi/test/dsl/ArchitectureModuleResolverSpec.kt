package me.tbsten.katachi.test.dsl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.ModulePath
import me.tbsten.katachi.dsl.ModuleResolver
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.conventionalModuleResolver

class ArchitectureModuleResolverSpec : FreeSpec({
    "moduleResolver を書かなければ規約ベースになる" {
        architecture { }.moduleResolver shouldBe ModuleResolver.Conventional
    }

    "conventionalModuleResolver() を明示しても既定と同じ値になる" {
        architecture { moduleResolver = conventionalModuleResolver() }
            .moduleResolver shouldBe ModuleResolver.Conventional
    }

    "moduleResolver を差し替えると architecture がそれを持つ" {
        val architecture = architecture {
            moduleResolver = ModuleResolver { module ->
                if (module.value == ":app") "apps/android" else module.segments.joinToString("/")
            }
            "domain".group { "UseCase" { } }
        }

        architecture.moduleResolver.directoryOf(ModulePath.of(":app")) shouldBe "apps/android"
        architecture.moduleResolver.directoryOf(ModulePath.of(":core:data")) shouldBe "core/data"
        architecture.allRoles.map { it.name } shouldBe listOf("UseCase")
    }
})
