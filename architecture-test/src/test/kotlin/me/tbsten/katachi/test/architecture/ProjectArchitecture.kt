package me.tbsten.katachi.test.architecture

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.test.architecture.devsupport.devSupportGroup
import me.tbsten.katachi.test.architecture.docs.docsGroup
import me.tbsten.katachi.test.architecture.library.libraryGroup

val projectArchitecture: Architecture = architecture {
    libraryGroup()
    docsGroup()
    devSupportGroup()
}
