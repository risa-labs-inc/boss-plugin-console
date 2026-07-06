package ai.rever.boss.plugin.dynamic.console

import ai.rever.boss.plugin.api.ConsoleLogsAPI
import ai.rever.boss.plugin.api.LoadedPluginInfo
import ai.rever.boss.plugin.api.LogDataProvider
import ai.rever.boss.plugin.api.LogEntryData
import ai.rever.boss.plugin.api.PluginLogMatcher
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The Console plugin's implementation of [ConsoleLogsAPI], registered via
 * `context.registerPluginAPI` so other plugins (e.g. the Tool Sidecar) reuse
 * the console's per-plugin log attribution instead of duplicating it.
 *
 * @param scope the plugin-level scope ([ai.rever.boss.plugin.api.PluginContext.pluginScope]);
 *   flows created here live until the plugin unloads.
 */
internal class ConsoleLogsApiImpl(
    private val provider: LogDataProvider,
    private val scope: CoroutineScope,
    private val filter: ConsolePluginFilter,
    private val loadedPlugins: () -> List<LoadedPluginInfo>,
) : ConsoleLogsAPI {

    private val flows = ConcurrentHashMap<String, StateFlow<List<LogEntryData>>>()

    override fun logsForPlugin(pluginId: String, displayName: String?): StateFlow<List<LogEntryData>> =
        flows.getOrPut(pluginId) {
            val keywords = PluginLogMatcher.keywordsFor(pluginId, displayName ?: resolveName(pluginId))
            val select = { entries: List<LogEntryData> ->
                entries.filter { PluginLogMatcher.matches(it.message, keywords) }
            }
            provider.logs
                .map(select)
                // Seed synchronously so a first .value read (e.g. an MCP probe right
                // after the flow is created) sees current history, not emptyList.
                .stateIn(scope, SharingStarted.Eagerly, select(provider.logs.value))
        }

    override val pluginFilter: StateFlow<String?> get() = filter.pluginId

    override fun setPluginFilter(pluginId: String?) {
        filter.set(pluginId, pluginId?.let(::resolveName))
    }

    private fun resolveName(pluginId: String): String? =
        loadedPlugins().firstOrNull { it.pluginId == pluginId }?.displayName
}
