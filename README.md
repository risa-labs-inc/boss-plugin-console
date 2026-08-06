# BOSS Console

Captured stdout and stderr from BOSS, in a left sidebar panel.

Wraps the host's `LogDataProvider` in a scrolling, selectable log view with source filtering,
search, and per-plugin attribution, so you can watch what the app and its plugins are printing
without leaving the window.

## What it does

- **Live output** from BOSS in the left bottom panel slot, updating as it is captured.
- **Filter by source** (ALL / STDOUT / STDERR) and type to search the buffer.
- **Narrow to one plugin** with the "All plugins" dropdown. Attribution is a keyword heuristic
  (`PluginLogMatcher`), not an exact channel, so treat it as a strong hint rather than a
  guarantee.
- **Lock or unlock auto-scroll**, and jump back to the newest line.
- **Copy** the filtered buffer to the clipboard, or **clear** it.

Text selection stays smooth because the view is a `Column` with `verticalScroll` rather than a
`LazyColumn`. That is a deliberate trade of virtualization for selectability.

## MCP tools

| Tool | Purpose |
|---|---|
| `console_tail` | Last N captured lines, optionally filtered to one plugin |
| `console_search` | Case-insensitive substring search over captured output |
| `console_clear` | Empty the capture buffer |

## For other plugins

Console registers `ConsoleLogsAPI`, so another plugin can reuse its per-plugin log attribution
and drive "Open in Console" without reimplementing either. The Tool Sidecar uses this.

## Requirements

- BOSS >= 9.2.35, boss-plugin-api >= 1.0.59
- `context.logDataProvider` must be present. Without it `register()` throws rather than putting
  up an empty panel.
- No external binaries.

## Build

```bash
./gradlew buildPluginJar
cp build/libs/boss-plugin-console-*.jar ~/.boss/plugins/
```

Then reload from Toolbox. For a dev-mode host use `~/.boss_debug/plugins/` instead.

See [AGENTS.md](AGENTS.md) for architecture and conventions.
