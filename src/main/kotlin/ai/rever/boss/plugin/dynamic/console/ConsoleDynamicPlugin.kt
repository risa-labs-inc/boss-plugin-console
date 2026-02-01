package ai.rever.boss.plugin.dynamic.console

import ai.rever.boss.plugin.api.DynamicPlugin
import ai.rever.boss.plugin.api.PluginContext

/**
 * Console dynamic plugin - Loaded from external JAR.
 *
 * Displays captured stdout/stderr logs in a side panel.
 * This is the dynamic plugin version that implements DynamicPlugin interface.
 */
class ConsoleDynamicPlugin : DynamicPlugin {
    override val pluginId: String = "ai.rever.boss.plugin.dynamic.console"
    override val displayName: String = "Console (Dynamic)"
    override val version: String = "1.0.0"
    override val description: String = "Displays captured stdout/stderr logs in a side panel"
    override val author: String = "Rever AI"
    override val url: String = "https://github.com/ReverAI/boss-plugin-console"

    override fun register(context: PluginContext) {
        context.panelRegistry.registerPanel(ConsoleInfo) { ctx, panelInfo ->
            ConsoleComponent(ctx, panelInfo)
        }
    }
}
