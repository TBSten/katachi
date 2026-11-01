package com.example.testing

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

fun DeclarationContainerScope.processorDefinition() = "ProcessorDefinition".role {
    title = "Processor definition"
    summary = "A custom processor that reads the definition, registered in `architecture-test/build.gradle.kts`"
    description = """
        The code behind a `katachi<Key>` task that reads the definition and produces something
        from it. One processor per file in `processors/`, registered in the `katachi { processors { } }`
        block of `architecture-test/build.gradle.kts`.
    """.trimIndent()
    forbiddenContents = """
        - A group or a role. Those go in `<Name>Group.kt` and `<Name>Role.kt`
    """.trimIndent()
    example("processors/RoleNames.kt", "A processor that lists role names by a prefix")
    layout {
        "architecture-test" / testSourceSet / kotlin / "com/example" / "processors" / "*".ktFile()
    }
}
