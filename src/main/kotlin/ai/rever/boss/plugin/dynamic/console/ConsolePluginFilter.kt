package ai.rever.boss.plugin.dynamic.console

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plugin-level holder for the console's per-plugin filter selection, shared by
 * every Console panel instance and the [ConsoleLogsApiImpl] so external callers
 * (e.g. the Tool Sidecar's "Open in Console") and the panel dropdown stay in
 * sync. Null id = show all plugins.
 */
class ConsolePluginFilter {

    private val _pluginId = MutableStateFlow<String?>(null)
    val pluginId: StateFlow<String?> = _pluginId.asStateFlow()

    private val _displayName = MutableStateFlow<String?>(null)
    val displayName: StateFlow<String?> = _displayName.asStateFlow()

    fun set(pluginId: String?, displayName: String?) {
        _pluginId.value = pluginId
        _displayName.value = displayName
    }
}
