# PyCharm Navigator Plugin

A plugin that enables external tools to navigate PyCharm to specific files, lines, and symbols via TCP socket.

## Features

- Navigate to files by path (partial matching supported)
- Navigate to symbols (classes, functions, methods)
- Search by code text when line numbers aren't available
- Selector popup for multiple matches
- Works with Claude Code and other tools

## Documentation

- **[Installation Guide](INSTALL.md)** - Setup and configuration for users
- **[API Reference](API.md)** - Protocol specification for developers

## Quick Start

1. Install plugin from `dist/pycharm-navigator-1.0.0.zip`
2. Open a project in PyCharm
3. Test:
   ```bash
   echo '{"type":"file","path":"your_file.py","line":1}' | nc localhost 8765
   ```

## License

MIT
