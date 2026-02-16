# IntelliJ Navigator Plugin

A plugin that enables external tools to navigate IntelliJ-based IDEs to specific files, lines, and symbols via TCP socket. Tested with PyCharm, compatible with all IntelliJ-based IDEs (WebStorm, GoLand, IntelliJ IDEA, etc.).

## Features

- Navigate to files by path (partial matching supported)
- Navigate to symbols (classes, functions, methods, variables, constants)
- Partial, case-insensitive, and camelCase symbol matching
- Search by code text when line numbers aren't available
- Selector popup for multiple matches
- Works with Claude Code and other tools

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

## License

MIT
