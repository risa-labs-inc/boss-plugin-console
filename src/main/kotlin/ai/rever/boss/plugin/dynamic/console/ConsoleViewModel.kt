package ai.rever.boss.plugin.dynamic.console

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for Console tab.
 *
 * Manages log capture, filtering, and search functionality.
 * Uses GlobalLogCapture singleton to access logs from app startup.
 */
class ConsoleViewModel {
    // Log capture system (global singleton started in main.kt)
    private val logCapture = GlobalLogCapture.getLogCapture()

    // All logs (filtered by current filter)
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    // Current filter
    private val _filter = MutableStateFlow(LogFilter.ALL)
    val filter: StateFlow<LogFilter> = _filter.asStateFlow()

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Auto-scroll enabled
    private val _autoScroll = MutableStateFlow(true)
    val autoScroll: StateFlow<Boolean> = _autoScroll.asStateFlow()

    init {
        // Listen for new log entries
        // (Log capture already started globally in main.kt)
        logCapture.addListener { entry ->
            updateLogs()
        }

        // Initial load (will load all logs from app startup)
        updateLogs()
    }

    /**
     * Update filtered logs based on current filter and search.
     */
    private fun updateLogs() {
        val allLogs = logCapture.getLogs()

        // Apply filter
        val filtered = when (_filter.value) {
            LogFilter.ALL -> allLogs
            LogFilter.STDOUT -> allLogs.filter { it.source == LogSource.STDOUT }
            LogFilter.STDERR -> allLogs.filter { it.source == LogSource.STDERR }
        }

        // Apply search
        val searched = if (_searchQuery.value.isNotEmpty()) {
            filtered.filter {
                it.message.contains(_searchQuery.value, ignoreCase = true)
            }
        } else {
            filtered
        }

        _logs.value = searched
    }

    /**
     * Set log filter.
     */
    fun setFilter(filter: LogFilter) {
        _filter.value = filter
        updateLogs()
    }

    /**
     * Set search query.
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        updateLogs()
    }

    /**
     * Toggle auto-scroll.
     */
    fun toggleAutoScroll() {
        _autoScroll.value = !_autoScroll.value
    }

    /**
     * Clear all logs.
     */
    fun clearLogs() {
        logCapture.clear()
        updateLogs()
    }

    /**
     * Get all logs as text (for copy to clipboard).
     */
    fun getAllLogsAsText(): String {
        return _logs.value.joinToString("\n") { entry ->
            "[${entry.formatTimestamp()}] [${entry.source}] ${entry.message}"
        }
    }

    /**
     * Clean up resources.
     */
    fun dispose() {
        logCapture.stop()
    }
}

/**
 * Log filter options.
 */
enum class LogFilter {
    /**
     * Show all logs (stdout + stderr)
     */
    ALL,

    /**
     * Show only stdout logs
     */
    STDOUT,

    /**
     * Show only stderr logs
     */
    STDERR
}
