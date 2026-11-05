package me.tbsten.katachi.intellij.data.baseline

import java.io.IOException
import java.nio.charset.CharacterCodingException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

// Plain disk work: call it off the EDT.
internal fun readBaselineFile(file: Path): BaselineReading {
    val text = try {
        Files.readString(file)
    } catch (_: NoSuchFileException) {
        return BaselineReading.Missing
    } catch (failure: CharacterCodingException) {
        return BaselineReading.Unreadable("not UTF-8 text: ${failure.message}")
    } catch (failure: IOException) {
        return BaselineReading.Unreadable("could not be read: ${failure.message}")
    }
    return parseBaselineText(text)
}
