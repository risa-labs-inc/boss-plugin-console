package ai.rever.boss.plugin.dynamic.console

import ai.rever.boss.plugin.api.LogEntryData
import ai.rever.boss.plugin.api.LogFilterData
import ai.rever.boss.plugin.api.LogSourceData
import ai.rever.boss.plugin.scrollbar.getPanelScrollbarConfig
import ai.rever.boss.plugin.scrollbar.verticalScrollWithScrollbar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.awt.datatransfer.StringSelection

/**
 * Create a ClipEntry from text for clipboard operations.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun createTextClipEntry(text: String): ClipEntry {
    return ClipEntry(StringSelection(text))
}

/**
 * Get the display color for a log source.
 */
private fun getLogColor(source: LogSourceData): Color {
    return when (source) {
        LogSourceData.STDOUT -> Color(0xFFCCCCCC) // Light gray
        LogSourceData.STDERR -> Color(0xFFFF6B6B) // Red
    }
}

/**
 * Console view for displaying captured logs.
 *
 * @param viewModel The console view model managing log state
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ConsoleView(viewModel: ConsoleViewModel) {
    val logs by viewModel.logs.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val autoScroll by viewModel.autoScroll.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current

    val scrollState = rememberScrollState()

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logs.size, autoScroll) {
        if (autoScroll && logs.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E)) // Dark background
    ) {
        // Toolbar
        ConsoleToolbar(
            filter = filter,
            searchQuery = searchQuery,
            autoScroll = autoScroll,
            onFilterChange = { viewModel.setFilter(it) },
            onSearchQueryChange = { viewModel.setSearchQuery(it) },
            onToggleAutoScroll = { viewModel.toggleAutoScroll() },
            onClear = { viewModel.clearLogs() },
            onCopyAll = {
                coroutineScope.launch {
                    clipboard.setClipEntry(createTextClipEntry(viewModel.getAllLogsAsText()))
                }
            },
            onJumpToBottom = {
                coroutineScope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
            }
        )

        Divider(color = Color(0xFF333333))

        // Log display
        if (logs.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No logs captured yet",
                    color = Color(0xFF888888),
                    fontSize = 14.sp
                )
            }
        } else {
            // Log list with multi-line selection support.
            // Uses a non-lazy Column + verticalScroll (not LazyColumn) so that text
            // selection stays smooth — selection over a LazyColumn is jumpy because
            // off-screen rows aren't composed.
            val density = LocalDensity.current
            var viewportHeightPx by remember { mutableStateOf(0) }
            var pointerY by remember { mutableStateOf(Float.NaN) }
            var dragging by remember { mutableStateOf(false) }

            // Continuous edge auto-scroll during a selection drag (BossTerm-style):
            // while the primary button is held and the last-known pointer Y is past
            // the top/bottom edge, keep scrolling every frame — even with no movement.
            LaunchedEffect(dragging) {
                if (!dragging) return@LaunchedEffect
                val edgePx = with(density) { 28.dp.toPx() }
                val maxStepPx = with(density) { 16.dp.toPx() }
                while (dragging && isActive) {
                    val h = viewportHeightPx
                    val y = pointerY
                    if (h > 0 && !y.isNaN()) {
                        val delta = when {
                            y < edgePx -> -((edgePx - y) / edgePx).coerceIn(0f, 1f) * maxStepPx
                            y > h - edgePx -> ((y - (h - edgePx)) / edgePx).coerceIn(0f, 1f) * maxStepPx
                            else -> 0f
                        }
                        if (delta != 0f) scrollState.scrollBy(delta)
                    }
                    withFrameNanos { }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { viewportHeightPx = it.height }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                // Observe in the Initial pass without consuming, so the
                                // SelectionContainer still gets the events for selecting.
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                dragging = event.changes.any { it.pressed }
                                event.changes.firstOrNull()?.let { pointerY = it.position.y }
                            }
                        }
                    }
            ) {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScrollWithScrollbar(
                                scrollState = scrollState,
                                scrollbarConfig = getPanelScrollbarConfig()
                            )
                            .padding(8.dp)
                    ) {
                        logs.forEach { entry ->
                            LogEntryRow(entry)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Toolbar with controls.
 */
@Composable
private fun ConsoleToolbar(
    filter: LogFilterData,
    searchQuery: String,
    autoScroll: Boolean,
    onFilterChange: (LogFilterData) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleAutoScroll: () -> Unit,
    onClear: () -> Unit,
    onCopyAll: () -> Unit,
    onJumpToBottom: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2B2B2B))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: Filter and search
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Filter dropdown (Fluck-style matching search box)
            var filterMenuExpanded by remember { mutableStateOf(false) }
            Box {
                // Custom styled dropdown button
                Row(
                    modifier = Modifier
                        .height(28.dp)
                        .clickable { filterMenuExpanded = true }
                        .background(
                            Color(0xFF1E1F22),
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            1.dp,
                            Color(0xFF555555),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = filter.name,
                        style = MaterialTheme.typography.body2,
                        color = Color.White
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        "Filter",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = filterMenuExpanded,
                    onDismissRequest = { filterMenuExpanded = false }
                ) {
                    LogFilterData.values().forEach { f ->
                        DropdownMenuItem(onClick = {
                            onFilterChange(f)
                            filterMenuExpanded = false
                        }) {
                            Text(f.name)
                        }
                    }
                }
            }

            // Search box (Fluck-style)
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .width(200.dp)
                    .height(28.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.body2.copy(
                    color = Color.White
                ),
                cursorBrush = SolidColor(MaterialTheme.colors.primary),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Color(0xFF1E1F22), // Dark surface
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                1.dp,
                                Color(0xFF555555),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "Search...",
                                    style = MaterialTheme.typography.body2,
                                    color = Color(0xFF888888)
                                )
                            }
                            innerTextField()
                        }
                    }
                }
            )
        }

        // Right side: Actions
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Jump to bottom
            IconButton(
                onClick = onJumpToBottom,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.VerticalAlignBottom,
                    "Scroll to bottom",
                    tint = Color(0xFFCCCCCC),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Auto-scroll toggle
            IconButton(
                onClick = onToggleAutoScroll,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (autoScroll) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (autoScroll) "Auto-scroll enabled" else "Auto-scroll disabled",
                    tint = if (autoScroll) Color(0xFF4A9EFF) else Color(0xFF888888),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Copy all
            IconButton(
                onClick = onCopyAll,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    "Copy all logs",
                    tint = Color(0xFFCCCCCC),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Clear
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    "Clear logs",
                    tint = Color(0xFFFF6B6B),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Single log entry row.
 */
@Composable
private fun LogEntryRow(entry: LogEntryData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Timestamp
        Text(
            text = entry.formatTimestamp(),
            color = Color(0xFF888888),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )

        // Source badge
        Text(
            text = entry.source.name,
            color = when (entry.source) {
                LogSourceData.STDOUT -> Color(0xFF4A9EFF)
                LogSourceData.STDERR -> Color(0xFFFF6B6B)
            },
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )

        // Message (no fontFamily to support emojis)
        Text(
            text = entry.message,
            color = getLogColor(entry.source),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
