package me.tbsten.katachi.docs.internal

import me.tbsten.katachi.dsl.Group
import me.tbsten.katachi.dsl.Role

/**
 * The `/` separated path of the directory this group's page sits in, relative to the
 * documentation root.
 *
 * [Group.qualifiedName] is `.` separated for reading and comparing, not for paths, so a link or
 * an output path is built from [Group.path] directly rather than from it.
 */
internal fun Group.directoryPath(): String = path.joinToString("/")

/**
 * The `/` separated path of this role's page, without [PAGE_EXTENSION], relative to the
 * documentation root.
 *
 * [Role.qualifiedName] is `.` separated for reading and comparing, not for paths, so a link or
 * an output path is built from [Role.groupPath] and [Role.name] directly rather than from it.
 */
internal fun Role.pagePath(): String = (groupPath + name).joinToString("/")
