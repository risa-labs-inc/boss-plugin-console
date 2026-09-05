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
                "Pass plugin_id to only return lines attributed to that plugin. " +
                "Defaults to the $DEFAULT_TAIL_LINES most recent lines, trimmed to about " +
                "$MAX_DEFAULT_CHARS characters. Pass lines to widen; an explicit value is returned " +
                "in full. A trimmed reply ends with a note counting what was left out.",
            inputSchema = LINES_SCHEMA,
            handler = McpToolHandler { args ->
                val requested = args.int("lines")
                val n = (requested ?: DEFAULT_TAIL_LINES).coerceIn(1, 5000)
                val all = logDataProvider.logs.value
                val pluginId = args.string("plugin_id")
                val pool = if (pluginId.isNullOrBlank()) all
                else PluginLogMatcher.filter(
                    all,
                    pluginId,
                    loadedPlugins().firstOrNull { it.pluginId == pluginId }?.displayName,
                )
                if (pool.isEmpty()) McpToolResult("(no console output captured)")
                else McpToolResult(render(pool, n, requested == null, "lines", "line"))
            },
        ),
        McpToolDefinition(
            name = "console_search",
            description = "Search captured console output for lines containing a substring (case-insensitive). " +
                "Defaults to the $DEFAULT_SEARCH_LIMIT most recent matches, trimmed to about " +
                "$MAX_DEFAULT_CHARS characters, because a common term otherwise matches thousands of " +
                "lines. Pass limit to widen; an explicit value is returned in full. A trimmed reply " +
                "ends with a note counting what was left out.",
            inputSchema = SEARCH_SCHEMA,
            handler = McpToolHandler { args ->
                val query = args.string("query")
                    ?: return@McpToolHandler McpToolResult("Missing required argument: query", isError = true)
                val requested = args.int("limit")
                val limit = (requested ?: DEFAULT_SEARCH_LIMIT).coerceIn(1, 5000)
                val matches = logDataProvider.logs.value
                    .filter { it.message.contains(query, ignoreCase = true) }
                if (matches.isEmpty()) McpToolResult("No console lines match \"$query\".")
                else McpToolResult(render(matches, limit, requested == null, "limit", "matching line"))
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

    /**
     * Render the newest [limit] entries of [pool]. When [capChars] (the caller left the count
     * argument out) the result is additionally trimmed to [MAX_DEFAULT_CHARS] so one pathological
     * line cannot blow the budget on its own. Anything dropped is reported in a trailing note,
     * naming [param] as the argument that widens the reply.
     */
    private fun render(
        pool: List<LogEntryData>,
        limit: Int,
        capChars: Boolean,
        param: String,
        noun: String,
    ): String {
        val kept = pool.takeLast(limit).let { if (capChars) fitToCharBudget(it) else it }
        var body = kept.joinToString("\n") { format(it) }
        if (capChars && body.length > MAX_DEFAULT_CHARS) {
            body = body.take(MAX_DEFAULT_CHARS) + "... [line cut short. Raise $param for the full text.]"
        }
        val omitted = pool.size - kept.size
        if (omitted <= 0) return body
        val plural = if (omitted == 1) noun else "${noun}s"
        return "$body\n[$omitted older $plural omitted. Raise $param to include them.]"
    }

    /** Newest-first walk keeping the entries that fit in [MAX_DEFAULT_CHARS]; always keeps one. */
    private fun fitToCharBudget(entries: List<LogEntryData>): List<LogEntryData> {
        if (entries.isEmpty()) return entries
        val last = entries.size - 1
        var used = 0
        var start = last
        for (i in last downTo 0) {
            used += format(entries[i]).length + 1
            if (used > MAX_DEFAULT_CHARS && i < last) break
            start = i
        }
        return entries.subList(start, entries.size)
    }

    private fun format(e: LogEntryData): String {
        val src = e.source.name
        return "${e.formatTimestamp()} [$src] ${e.message}"
    }

    private companion object {
        const val DEFAULT_TAIL_LINES = 20
        const val DEFAULT_SEARCH_LIMIT = 10
        const val MAX_DEFAULT_CHARS = 1600
        val LINES_SCHEMA =
            """{"type":"object","properties":{"lines":{"type":"integer","description":"Number of trailing lines to return (max 5000). Omit for the $DEFAULT_TAIL_LINES most recent, trimmed to about $MAX_DEFAULT_CHARS characters. Pass a number to get exactly that many, untrimmed; a shortened reply says how many older lines were omitted."},"plugin_id":{"type":"string","description":"Only lines attributed to this plugin (keyword heuristic)."}}}"""
        val SEARCH_SCHEMA =
            """{"type":"object","properties":{"query":{"type":"string","description":"Substring to match."},"limit":{"type":"integer","description":"Maximum matches to return, taken from the most recent (max 5000). Omit for the $DEFAULT_SEARCH_LIMIT most recent, trimmed to about $MAX_DEFAULT_CHARS characters. Pass a number to get exactly that many, untrimmed; a shortened reply says how many older matches were omitted."}},"required":["query"]}"""
    }
}
