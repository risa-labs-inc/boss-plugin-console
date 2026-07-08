package ai.rever.boss.plugin.dynamic.console

import ai.rever.boss.plugin.api.DynamicPlugin
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.api.PluginLoaderDelegate

/**
 * Console dynamic plugin - Loaded from external JAR.
 *
 * Displays captured stdout/stderr logs in a side panel.
 * This is the dynamic plugin version that implements DynamicPlugin interface.
 *
 * Uses LogDataProvider from host application to access logs, avoiding
 * classloader isolation issues that occur with GlobalLogCapture.
 */
class ConsoleDynamicPlugin : DynamicPlugin {
    override val pluginId: String = "ai.rever.boss.plugin.dynamic.console"
    override val displayName: String = "Console (Dynamic)"
    override val version: String = "1.2.0"
    override val description: String = "Displays captured stdout/stderr logs in a side panel"
    override val author: String = "Rever AI"
    override val url: String = "https://github.com/ReverAI/boss-plugin-console"

    override fun register(context: PluginContext) {
        // Get logDataProvider from context (provided by host application)
        val provider = context.logDataProvider
            ?: throw IllegalStateException("LogDataProvider not available in context. Console plugin requires log data access.")

        val pluginFilter = ConsolePluginFilter()
        val loadedPlugins = {
            context.getPluginAPI(PluginLoaderDelegate::class.java)?.getLoadedPlugins() ?: emptyList()
        }

        context.panelRegistry.registerPanel(ConsoleInfo) { ctx, panelInfo ->
            ConsoleComponent(
                ctx = ctx,
                panelInfo = panelInfo,
                logDataProvider = provider,
                pluginFilter = pluginFilter,
                loadedPlugins = loadedPlugins,
            )
        }
        // Shared per-plugin log access for other plugins (e.g. Tool Sidecar);
        // auto-unregistered when this plugin unloads.
        context.registerPluginAPI(
            ConsoleLogsApiImpl(provider, context.pluginScope, pluginFilter, loadedPlugins)
        )
        // Contribute console_tail/search/clear MCP tools; auto-removed on disable/unload.
        context.registerMcpToolProvider(ConsoleMcpToolProvider(pluginId, provider, loadedPlugins))
    }
}
