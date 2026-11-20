package me.tbsten.katachi.test.processor

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.ByteArrayInputStream
import java.io.InputStream
import me.tbsten.katachi.processor.KatachiEntryPointNotFoundException
import me.tbsten.katachi.processor.KatachiProcessorJvmTooOldException
import me.tbsten.katachi.processor.internal.KatachiEntryPoint
import me.tbsten.katachi.processor.internal.loadEntryPoint

/**
 * The entry point read by a processor JVM older than the Java the tests were compiled for: what a `katachi<Key>`
 * task started on the JVM running Gradle 8 hit while the plugin did not start it on the module's toolchain.
 */
class ProcessorJvmTooOldSpec : FreeSpec({
    val runsOn = Runtime.version().feature()
    val newer = runsOn + 1
    val entryPoint = ValidEntryPoint::class.java.name

    "新しい Java 向けの entry point は、コンパイルされた Java と動いている Java を言う" {
        val loader = ClassFilesLoader(mapOf(entryPoint to classFileOf(entryPoint, major = newer + 44)))

        val thrown = shouldThrow<KatachiProcessorJvmTooOldException> { loadEntryPoint(entryPoint, loader) }

        thrown.className shouldBe entryPoint
        thrown.classFileJavaVersion shouldBe newer
        thrown.runtimeJavaVersion shouldBe runsOn
        thrown.cause.shouldBeInstanceOf<UnsupportedClassVersionError>()
        thrown.message shouldBe """
            Could not load $entryPoint: it was compiled for Java $newer, but the processor runs on Java $runsOn.
            A JVM cannot load a class compiled for a newer Java than its own, so the definition could not be read.
            Run the processor on Java $newer or newer. katachi's Gradle plugin starts the katachi<Key> tasks on the module's Java toolchain, the one the tests are compiled with: use the plugin of the same version as this library, and do not point the task's javaLauncher or executable at an older Java.
        """.trimIndent()
    }

    "拒まれたのが entry point の読み込みに要る別の class なら、その class を名指す" {
        val dependency = KatachiEntryPoint::class.java.name
        val loader = ClassFilesLoader(
            mapOf(
                entryPoint to classFileOf(entryPoint),
                dependency to classFileOf(dependency, major = newer + 44),
            ),
        )

        val thrown = shouldThrow<KatachiProcessorJvmTooOldException> { loadEntryPoint(entryPoint, loader) }

        thrown.className shouldBe dependency
        thrown.classFileJavaVersion shouldBe newer
    }

    "JVM の文面が版を言わなくても、class ファイルから版を読んで同じ例外にする" {
        val loader = ClassFilesLoader(
            mapOf(entryPoint to classFileOf(entryPoint, major = newer + 44)),
            refusedWithoutVersion = setOf(entryPoint),
        )

        val thrown = shouldThrow<KatachiProcessorJvmTooOldException> { loadEntryPoint(entryPoint, loader) }

        thrown.className shouldBe entryPoint
        thrown.classFileJavaVersion shouldBe newer
        thrown.runtimeJavaVersion shouldBe runsOn
    }

    "版が古くないのに拒まれた（preview 機能）なら、今までどおり KatachiEntryPointNotFoundException" {
        val loader = ClassFilesLoader(mapOf(entryPoint to classFileOf(entryPoint, major = runsOn + 44, minor = 0xFFFF)))

        val thrown = shouldThrow<KatachiEntryPointNotFoundException> { loadEntryPoint(entryPoint, loader) }

        thrown.cause.shouldBeInstanceOf<UnsupportedClassVersionError>()
    }
})

/** [className]'s class file as the test class path has it, with its version set to [major].[minor] when [major] is given. */
private fun classFileOf(className: String, major: Int? = null, minor: Int = 0): ByteArray {
    val bytes = ProcessorJvmTooOldSpec::class.java.classLoader
        .getResourceAsStream(className.replace('.', '/') + ".class")!!
        .use { it.readBytes() }
    if (major != null) {
        // u4 magic, then u2 minor_version and u2 major_version, big-endian.
        bytes[4] = (minor shr 8).toByte()
        bytes[5] = minor.toByte()
        bytes[6] = (major shr 8).toByte()
        bytes[7] = major.toByte()
    }
    return bytes
}

/**
 * Defines the classes of [classFiles] itself, before asking its parent, and serves their bytes as resources. A class
 * of [refusedWithoutVersion] is refused the way a JVM other than HotSpot may refuse it, with a message naming no
 * version.
 */
private class ClassFilesLoader(
    private val classFiles: Map<String, ByteArray>,
    private val refusedWithoutVersion: Set<String> = emptySet(),
) : ClassLoader(ClassFilesLoader::class.java.classLoader) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        val bytes = classFiles[name] ?: return super.loadClass(name, resolve)
        if (name in refusedWithoutVersion) throw UnsupportedClassVersionError("JVMCFRE003 bad major version; class=$name, offset=6")
        synchronized(getClassLoadingLock(name)) {
            findLoadedClass(name)?.let { return it }
            return defineClass(name, bytes, 0, bytes.size)
        }
    }

    override fun getResourceAsStream(name: String): InputStream? =
        classFiles[name.removeSuffix(".class").replace('/', '.')]?.let(::ByteArrayInputStream)
            ?: super.getResourceAsStream(name)
}
