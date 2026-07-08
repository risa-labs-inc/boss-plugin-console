package ai.rever.boss.plugin.dynamic.console

import ai.rever.boss.plugin.api.LoadedPluginInfo
import ai.rever.boss.plugin.api.LogDataProvider
import ai.rever.boss.plugin.api.LogEntryData
import ai.rever.boss.plugin.api.McpToolDefinition
import ai.rever.boss.plugin.api.PluginLogMatcher
import ai.rever.boss.plugin.api.McpToolHandler
import ai.rever.boss.plugin.api.McpToolProvider
import ai.rever.boss.plugin.api.McpToolResult

/**
 * MCP tools contributed by the Console plugin: read and search the captured
 * stdout/stderr log stream. Registered in [ConsoleDynamicPlugin.register];
 * removed automatically on disable/unload.
 */
internal class ConsoleMcpToolProvider(
    override val providerId: String,
    private val logDataProvider: LogDataProvider,
    private val loadedPlugins: () -> List<LoadedPluginInfo> = { emptyList() },
) : McpToolProvider {

    override fun tools(): List<McpToolDefinition> = listOf(
        McpToolDefinition(
            name = "console_tail",
            description = "Return the most recent captured console log lines (stdout/stderr). " +
                "Pass plugin_id to only return lines attributed to that plugin.",
            inputSchema = LINES_SCHEMA,
            handler = McpToolHandler { args ->
                val n = (args.int("lines") ?: 100).coerceIn(1, 5000)
                val all = logDataProvider.logs.value
                val pluginId = args.string("plugin_id")
                val lines = if (pluginId.isNullOrBlank()) all.takeLast(n)
                else PluginLogMatcher.filter(
                    all,
                    pluginId,
                    loadedPlugins().firstOrNull { it.pluginId == pluginId }?.displayName,
                ).takeLast(n)
                if (lines.isEmpty()) McpToolResult("(no console output captured)")
                else McpToolResult(lines.joinToString("\n") { format(it) })
            },
        ),
        McpToolDefinition(
            name = "console_search",
            description = "Search captured console output for lines containing a substring (case-insensitive).",
            inputSchema = SEARCH_SCHEMA,
            handler = McpToolHandler { args ->
                val query = args.string("query")
                    ?: return@McpToolHandler McpToolResult("Missing required argument: query", isError = true)
                val limit = (args.int("limit") ?: 200).coerceIn(1, 5000)
                val matches = logDataProvider.logs.value
                    .filter { it.message.contains(query, ignoreCase = true) }
                    .takeLast(limit)
                if (matches.isEmpty()) McpToolResult("No console lines match \"$query\".")
                else McpToolResult(matches.joinToString("\n") { format(it) })
            },
        ),
        McpToolDefinition(
            name = "console_clear",
            description = "Clear the captured console output buffer.",
            readOnly = false,
            handler = McpToolHandler {
                logDataProvider.clearLogs()
                McpToolResult("Console cleared.")
            },
        ),
    )

    private fun format(e: LogEntryData): String {
        val src = e.source.name
        return "${e.formatTimestamp()} [$src] ${e.message}"
    }

    private companion object {
        const val LINES_SCHEMA =
            """{"type":"object","properties":{"lines":{"type":"integer","description":"Number of trailing lines (default 100)."},"plugin_id":{"type":"string","description":"Only lines attributed to this plugin (keyword heuristic)."}}}"""
        const val SEARCH_SCHEMA =
            """{"type":"object","properties":{"query":{"type":"string","description":"Substring to match."},"limit":{"type":"integer","description":"Max matches (default 200)."}},"required":["query"]}"""
    }
}
