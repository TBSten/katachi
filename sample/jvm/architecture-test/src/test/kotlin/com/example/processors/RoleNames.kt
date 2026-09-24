package com.example.processors

// A processor this sample writes itself, registered in `architecture-test/build.gradle.kts`
// under the key "roleNames". Whether it is written as an `object` (like this one) or as a
// plain class with a no-argument constructor makes no difference to `katachi { processors {
// register(...) } }` -- both are instantiated the same way by `runKatachiProcessor`.
//
// Its `Args` type is what pulls `alias(libs.plugins.kotlinPluginSerialization)` into this
// module's `plugins { }`: a processor with no arguments at all -- see sample/android and
// sample/kmp, which register only ArchitectureProcessorNoArg processors -- needs no
// serialization compiler plugin.

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessor

@OptIn(ExperimentalKatachiApi::class)
object RoleNames : ArchitectureProcessor<RoleNames.Args, List<String>> {
    override val argsSerializer: KSerializer<Args> = Args.serializer()

    override fun process(context: ArchitectureProcessContext<Args>): List<String> {
        context.log("Listing roles with prefix=${context.args.prefix}")
        return context.roles.map { it.qualifiedName }.filter { it.startsWith(context.args.prefix) }
    }

    @Serializable
    data class Args(val prefix: String = "")
}
