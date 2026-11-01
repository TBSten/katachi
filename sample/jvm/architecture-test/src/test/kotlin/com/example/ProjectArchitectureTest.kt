package com.example

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.assert
import org.junit.jupiter.api.Test

@OptIn(ExperimentalKatachiApi::class)
class ProjectArchitectureTest {
    @Test
    fun `the project structure matches the definition`() {
        projectArchitecture.assert(FileConstraintCheck())
    }
}
