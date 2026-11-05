package me.tbsten.katachi.intellij.ide.mcp

import com.intellij.mcpserver.impl.util.asTools
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import me.tbsten.katachi.intellij.ide.gutter.GoldenFile
import java.io.File

/**
 * The names, descriptions and schemas an AI agent reads from the katachi tools. They are a contract with every agent
 * prompt that names them, so a change has to show up as a golden diff.
 */
internal class KatachiMcpSchemaTest : BasePlatformTestCase() {
    fun `test tool の名前と input output schema が golden と一致する`() {
        val text = KatachiMcpToolset().asTools().sortedBy { it.descriptor.name }.joinToString("\n") { tool ->
            val descriptor = tool.descriptor
            """
            |== ${descriptor.name}
            |${descriptor.description}
            |-- input
            |${descriptor.inputSchema.prettyPrint()}
            |-- output
            |${descriptor.outputSchema?.prettyPrint()}
            |
            """.trimMargin()
        }
        assertEquals(KatachiMcpToolsetTest.KATACHI_TOOL_NAMES, KatachiMcpToolset().asTools().map { it.descriptor.name }.sorted())
        GoldenFile.verify(File("src/test/resources/contract/mcp/katachi-mcp-tools.txt"), text, "build/mcp-golden-actual/katachi-mcp-tools.txt")
    }
}
