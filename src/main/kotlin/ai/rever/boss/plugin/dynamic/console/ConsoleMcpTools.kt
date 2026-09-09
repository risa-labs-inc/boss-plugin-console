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
                "$MAX_DEFAULT_CHARS characters. Pass lines to widen, up to a reply ceiling of " +
                "$MAX_ABSOLUTE_CHARS characters. A trimmed reply ends with a note counting what " +
                "was left out.",
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
                "lines. Pass limit to widen, up to a reply ceiling of $MAX_ABSOLUTE_CHARS characters. " +
                "A trimmed reply ends with a note counting what was left out.",
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
     * Render the newest [limit] entries of [pool]. Every reply is bounded: [MAX_DEFAULT_CHARS]
     * when [capChars] (the caller left the count argument out), [MAX_ABSOLUTE_CHARS] otherwise, so
     * no argument value can ask for an unbounded result. A note reports only what the caller did
     * not ask to lose: with no count that is everything past the default, with an explicit count
     * only what the char ceiling itself cut. [param] names the argument that widens the reply.
     */
    private fun render(
        pool: List<LogEntryData>,
        limit: Int,
        capChars: Boolean,
        param: String,
        noun: String,
    ): String {
        val budget = if (capChars) MAX_DEFAULT_CHARS else MAX_ABSOLUTE_CHARS
        val requested = pool.takeLast(limit).map { format(it) }
        val kept = fitToCharBudget(requested, budget)
        var body = kept.joinToString("\n")
        if (body.length > budget) {
            val widen =
                if (capChars) "Pass $param for the full text."
                else "It hit the $MAX_ABSOLUTE_CHARS-character reply ceiling."
            body = body.takeChars(budget) + "... [line cut short. $widen]"
        }
        // An explicit count is a contract: honouring it exactly is not a loss worth reporting, so
        // measure against what was asked for. With no count, everything past the default is a loss
        // the caller never chose, so measure against the whole pool.
        val asked = if (capChars) pool.size else requested.size
        val omitted = asked - kept.size
        if (omitted <= 0) return body
        val plural = if (omitted == 1) noun else "${noun}s"
        val advice =
            if (capChars) "Pass $param to include ${if (omitted == 1) "it" else "them"}."
            else "The reply hit the $MAX_ABSOLUTE_CHARS-character ceiling."
        return "$body\n[$omitted older $plural omitted. $advice]"
    }

    /** Newest-first walk keeping the formatted lines that fit in [budget]; always keeps one. */
    private fun fitToCharBudget(lines: List<String>, budget: Int): List<String> {
        if (lines.isEmpty()) return lines
        val last = lines.size - 1
        var used = 0
        var start = last
        for (i in last downTo 0) {
            used += lines[i].length + 1
            if (used > budget && i < last) break
            start = i
        }
        return lines.subList(start, lines.size)
    }

    /** [String.take] that never splits a surrogate pair, which would leave a lone surrogate. */
    private fun String.takeChars(n: Int): String {
        if (length <= n) return this
        return substring(0, if (n > 0 && this[n - 1].isHighSurrogate()) n - 1 else n)
    }

    private fun format(e: LogEntryData): String {
        val src = e.source.name
        return "${e.formatTimestamp()} [$src] ${e.message}"
    }

    private companion object {
        const val DEFAULT_TAIL_LINES = 20
        const val DEFAULT_SEARCH_LIMIT = 10
        const val MAX_DEFAULT_CHARS = 1600

        /**
         * Hard ceiling on any reply, whatever the caller asks for. A console line measures ~44
         * tokens, so the old `coerceIn(1, 5000)` bound alone allowed a ~200k-token single result -
         * and the omission note points callers straight at raising the count. Set well above
         * [MAX_DEFAULT_CHARS] so an explicit count stays genuinely useful.
         */
        const val MAX_ABSOLUTE_CHARS = 40_000
        const val LINES_SCHEMA =
            """{"type":"object","properties":{"lines":{"type":"integer","description":"Number of trailing lines to return (max 5000). Omit for the $DEFAULT_TAIL_LINES most recent, trimmed to about $MAX_DEFAULT_CHARS characters. Pass a number to get exactly that many, subject only to a $MAX_ABSOLUTE_CHARS-character reply ceiling; if that ceiling cuts the reply short it says how many lines it dropped."},"plugin_id":{"type":"string","description":"Only lines attributed to this plugin (keyword heuristic)."}}}"""
        const val SEARCH_SCHEMA =
            """{"type":"object","properties":{"query":{"type":"string","description":"Substring to match."},"limit":{"type":"integer","description":"Maximum matches to return, taken from the most recent (max 5000). Omit for the $DEFAULT_SEARCH_LIMIT most recent, trimmed to about $MAX_DEFAULT_CHARS characters. Pass a number to get exactly that many, subject only to a $MAX_ABSOLUTE_CHARS-character reply ceiling; if that ceiling cuts the reply short it says how many matches it dropped."}},"required":["query"]}"""
    }
}
