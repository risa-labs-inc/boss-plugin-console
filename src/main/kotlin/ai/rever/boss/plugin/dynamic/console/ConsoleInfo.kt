package ai.rever.boss.plugin.dynamic.console

import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.Panel.Companion.left
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PanelInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info

/**
 * Console panel info.
 *
 * Displays captured stdout/stderr logs in a side panel.
 */
object ConsoleInfo : PanelInfo {
    override val id = PanelId("dynamic-console", 16) // After Git Log (15)
    override val displayName = "Console (Dynamic)"
    override val icon = Icons.Outlined.Info
    override val defaultSlotPosition = left.bottom
}
