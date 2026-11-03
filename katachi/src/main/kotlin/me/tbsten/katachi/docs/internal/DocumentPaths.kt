package me.tbsten.katachi.docs.internal

import me.tbsten.katachi.dsl.Group
import me.tbsten.katachi.dsl.Role

// qualifiedName is '.'-separated; build paths from the path segments.
internal fun Group.directoryPath(): String = path.joinToString("/")

// qualifiedName is '.'-separated; build paths from the path segments.
internal fun Role.pagePath(): String = (groupPath + name).joinToString("/")
