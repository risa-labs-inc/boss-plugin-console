package ai.rever.boss.plugin.dynamic.console

/**
 * Global singleton for log capture.
 *
 * This starts capturing logs from app startup (initialized in main.kt)
 * so that the Console panel can show ALL logs, not just logs after the panel opens.
 *
 * Usage:
 * - main.kt: Call GlobalLogCapture.start() at app startup
 * - ConsoleViewModel: Use GlobalLogCapture.getLogCapture() instead of creating new instance
 */
object GlobalLogCapture {
    private val logCapture = DesktopLogCapture()

    /**
     * Start capturing logs globally from app startup.
     * Should be called once in main.kt.
     */
    fun start() {
        logCapture.start()
    }

    /**
     * Get the global log capture instance.
     * Used by ConsoleViewModel to access captured logs.
     */
    fun getLogCapture(): DesktopLogCapture = logCapture
}
