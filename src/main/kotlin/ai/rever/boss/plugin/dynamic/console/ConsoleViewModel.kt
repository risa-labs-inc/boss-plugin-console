package ai.rever.boss.plugin.dynamic.console

import ai.rever.boss.plugin.api.LogDataProvider
import ai.rever.boss.plugin.api.LogEntryData
import ai.rever.boss.plugin.api.LogFilterData
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel for Console tab.
 *
 * Delegates to LogDataProvider from the host application for log capture,
 * filtering, and search functionality. This avoids classloader isolation
 * issues that occur when dynamic plugins try to access GlobalLogCapture directly.
 *
 * @param logDataProvider Provider from host app that has access to GlobalLogCapture
 */
class ConsoleViewModel(
    private val logDataProvider: LogDataProvider
) {
    // Delegate to provider's StateFlows
    val logs: StateFlow<List<LogEntryData>> = logDataProvider.logs
    val filter: StateFlow<LogFilterData> = logDataProvider.filter
    val searchQuery: StateFlow<String> = logDataProvider.searchQuery
    val autoScroll: StateFlow<Boolean> = logDataProvider.autoScroll

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
     * Get all logs as text (for copy to clipboard).
     */
    fun getAllLogsAsText(): String {
        return logDataProvider.exportLogs()
    }

    /**
     * Clean up resources.
     * Note: We don't stop log capture here since the provider is managed by the host app.
     */
    fun dispose() {
        // No-op - provider lifecycle is managed by host application
    }
}
