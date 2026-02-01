package ai.rever.boss.plugin.dynamic.console

import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle.Callbacks

/**
 * Console panel component.
 *
 * Shows real-time application logs captured from System.out and System.err.
 */
class ConsoleComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    private val viewModel = ConsoleViewModel()

    init {
        // Dispose view model when panel closes
        lifecycle.subscribe(
            callbacks = object : Callbacks {
                override fun onDestroy() {
                    viewModel.dispose()
                }
            }
        )
    }

    @Composable
    override fun Content() {
        ConsoleView(viewModel)
    }
}
