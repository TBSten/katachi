package me.tbsten.katachi.check

import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.fs.KatachiFileSystem
import me.tbsten.katachi.fs.RealFileSystem
import me.tbsten.katachi.processor.process
import me.tbsten.katachi.scan.Violation

/**
 * Runs the check against the real file system and returns every violation.
 *
 * This is [LayoutCheck] run through `process`, kept as a name of its own because it is the
 * shape the check has always had here and nothing about it needs to change: a caller who wants
 * the violations of the layout says so, without having to know that the check became one
 * processor among others underneath.
 *
 * Nothing is thrown, including when the definition allows nothing at all: `assert()` is what
 * turns this into a test failure, and a report or a baseline can sit on it instead.
 *
 * @throws me.tbsten.katachi.fs.KatachiProjectRootNotFoundException when no directory above the
 *   working directory carries a Gradle, Maven or git marker.
 * @throws me.tbsten.katachi.fs.KatachiGitUnavailableException when `files = gitTracked()` and
 *   the project root is a git repository, but git cannot be run.
 */
@InternalKatachiApi
public fun Architecture.validate(): List<Violation> = validate(RealFileSystem())

/**
 * [validate] against [fileSystem], which katachi's own specs use to hand it a tree that only
 * exists in memory.
 *
 * The walk is reached through [LayoutCheck] and the model it is handed, which is also why the
 * project root is resolved when this is called rather than when the model is built: the check
 * asks for the violations straight away, and asking is what starts the walk. See
 * `scanProject` for what that walk does and does not catch.
 */
@InternalKatachiApi
public fun Architecture.validate(fileSystem: KatachiFileSystem): List<Violation> =
    process(LayoutCheck(), fileSystem)
