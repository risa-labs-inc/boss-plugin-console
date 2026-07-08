package ai.rever.boss.plugin.dynamic.console

import ai.rever.boss.plugin.api.LoadedPluginInfo
import ai.rever.boss.plugin.api.LogDataProvider
import ai.rever.boss.plugin.api.LogEntryData
import ai.rever.boss.plugin.api.LogFilterData
import ai.rever.boss.plugin.api.PluginLogMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for Console tab.
 *
 * Delegates to LogDataProvider from the host application for log capture,
 * source filtering, and search (host-side), and adds a per-plugin filter on top
 * ([PluginLogMatcher] keyword attribution — shared with the Tool Sidecar via
 * the ConsoleLogsAPI). The plugin selection lives in [ConsolePluginFilter],
 * shared across panel instances and with the API.
 *
 * @param logDataProvider Provider from host app that has access to GlobalLogCapture
 * @param pluginFilter Plugin-level filter selection holder
 * @param loadedPlugins Supplier of currently loaded plugins for the dropdown
 */
class ConsoleViewModel(
    private val logDataProvider: LogDataProvider,
    private val pluginFilter: ConsolePluginFilter,
    private val loadedPlugins: () -> List<LoadedPluginInfo> = { emptyList() },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Host-filtered (source + search) logs, further narrowed to the selected plugin. */
    val logs: StateFlow<List<LogEntryData>> = combine(
        logDataProvider.logs,
        pluginFilter.pluginId,
        pluginFilter.displayName,
    ) { entries, pluginId, displayName ->
        if (pluginId == null) entries
        else PluginLogMatcher.filter(entries, pluginId, displayName)
    }.stateIn(scope, SharingStarted.Eagerly, logDataProvider.logs.value)

    val filter: StateFlow<LogFilterData> = logDataProvider.filter
    val searchQuery: StateFlow<String> = logDataProvider.searchQuery
    val autoScroll: StateFlow<Boolean> = logDataProvider.autoScroll

    /** Selected plugin filter (null = all plugins). */
    val selectedPluginId: StateFlow<String?> = pluginFilter.pluginId
    val selectedPluginName: StateFlow<String?> = pluginFilter.displayName

    /** Options for the plugin dropdown; refreshed on demand. */
    private val _pluginOptions = MutableStateFlow<List<LoadedPluginInfo>>(emptyList())
    val pluginOptions: StateFlow<List<LoadedPluginInfo>> = _pluginOptions.asStateFlow()

    /**
     * Set log filter.
     */
    fun setFilter(filter: LogFilterData) {
        logDataProvider.setFilter(filter)
    }

    /**
     * Set search query.
     */
    fun setSearchQuery(query: String) {
        logDataProvider.setSearchQuery(query)
    }

    /** Select which plugin's logs to show; null clears back to all plugins. */
    fun setPluginFilter(plugin: LoadedPluginInfo?) {
        pluginFilter.set(plugin?.pluginId, plugin?.displayName)
    }

    /** Re-query the loaded plugin list (called when the dropdown opens). */
    fun refreshPluginOptions() {
        _pluginOptions.value = loadedPlugins().sortedBy { it.displayName.lowercase() }
    }

    /**
     * Toggle auto-scroll.
     */
    fun toggleAutoScroll() {
        logDataProvider.toggleAutoScroll()
    }

    /**
     * Clear all logs.
     */
    fun clearLogs() {
        logDataProvider.clearLogs()
    }

    /**
     * Get all logs as text (for copy to clipboard). Respects the plugin filter
     * when one is active.
     */
    fun getAllLogsAsText(): String {
        return if (pluginFilter.pluginId.value == null) {
            logDataProvider.exportLogs()
        } else {
            logs.value.joinToString("\n") { "${it.formatTimestamp()} [${it.source.name}] ${it.message}" }
        }
    }

    /**
     * Clean up resources.
     * Note: We don't stop log capture here since the provider is managed by the host app.
     */
    fun dispose() {
        scope.cancel()
    }
}
