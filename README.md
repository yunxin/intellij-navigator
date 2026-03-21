# IntelliJ Navigator Plugin

IDE navigation plugins for [AgentTerm](https://github.com/yunxin/agent-term). Enables AgentTerm (or any TCP client) to navigate IntelliJ-based IDEs to specific files, lines, and symbols.

Tested with PyCharm, compatible with all IntelliJ-based IDEs (WebStorm, GoLand, IntelliJ IDEA, etc.).

## Plugin Roles

Two plugins work together to handle navigation in JetBrains Remote Development:

| Plugin | Port | Install on | Role |
|--------|------|------------|------|
| **intellij-navigator** (backend) | 8765 | **Host** IDE | Resolves file paths and symbols, opens or activates files, moves the caret when requested |
| **intellij-navigator-frontend** | 8766 | **Client** IDE | Scrolls the editor viewport to the caret position |

In a local (non-remote) setup, install both plugins in the same IDE.

## Features

- Navigate to files by path (partial matching supported)
- Activate a file while preserving IntelliJ's remembered caret and scroll state
- Navigate to symbols (classes, functions, methods, variables, constants)
- Partial, case-insensitive, and camelCase symbol matching
- Search by code text when line numbers aren't available
- Selector popup for multiple matches

## Download

Pre-built plugin zips are available on the [AgentTerm releases page](https://github.com/yunxin/agent-term/releases).

## Documentation

- **[Installation Guide](INSTALL.md)** - Setup and configuration for users
- **[API Reference](API.md)** - Protocol specification for developers

## Quick Start

1. Build the plugin: `./gradlew buildPlugin` (output in `build/distributions/`)
2. Open a project in your IDE
3. Test:
   ```bash
   echo '{"type":"file","path":"your_file.py","line":1}' | nc localhost 8765
   ```

To switch to a file without forcing a new location, use:

```bash
echo '{"type":"file","path":"your_file.py"}' | nc localhost 8765
```

## License

MIT
