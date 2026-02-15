# Installation Guide

## Requirements

- PyCharm (Community or Professional)
- Version 2024.1 or later

## Installation

1. Download `dist/pycharm-navigator-1.0.0.zip` from this repository
2. In PyCharm: **Settings** → **Plugins** → **⚙️** (gear icon) → **Install Plugin from Disk...**
3. Select the downloaded zip file
4. Restart PyCharm

## Verify Installation

1. Open a project in PyCharm
2. Run this command in your terminal:
   ```bash
   echo '{"type":"file","path":"any_file.py","line":1}' | nc localhost 8765
   ```
3. PyCharm should jump to the file

## Platform Setup

### macOS

Works out of the box with `nc` (netcat).

### Linux

```bash
# Install netcat if needed
sudo apt-get install netcat-openbsd  # Debian/Ubuntu
sudo yum install nc                   # RHEL/CentOS
```

### WSL (Windows Subsystem for Linux)

When connecting from WSL to PyCharm on Windows:

1. **Find Windows host IP:**
   ```bash
   cat /etc/resolv.conf | grep nameserver | awk '{print $2}'
   ```

2. **Connect using that IP:**
   ```bash
   WINDOWS_HOST=$(cat /etc/resolv.conf | grep nameserver | awk '{print $2}')
   echo '{"type":"file","path":"test.py","line":1}' | nc $WINDOWS_HOST 8765
   ```

3. **Configure Windows Firewall:**
   - Open Windows Defender Firewall
   - Click "Advanced settings"
   - Inbound Rules → New Rule
   - Port → TCP → 8765 → Allow the connection

### Windows (Native)

```powershell
# Install netcat via Chocolatey
choco install netcat

# Use same syntax as Linux
echo '{"type":"file","path":"test.py","line":1}' | nc localhost 8765
```

Or use PowerShell directly:
```powershell
$client = New-Object System.Net.Sockets.TcpClient("localhost", 8765)
$stream = $client.GetStream()
$writer = New-Object System.IO.StreamWriter($stream)
$reader = New-Object System.IO.StreamReader($stream)
$writer.WriteLine('{"type":"file","path":"test.py","line":1}')
$writer.Flush()
$reader.ReadLine()
$client.Close()
```

## Troubleshooting

### Connection refused

1. Ensure PyCharm is running with a project open (server starts when project opens)
2. Check plugin is installed: **Settings** → **Plugins** → search "PyCharm Navigator"
3. Check PyCharm logs: **Help** → **Show Log in Finder/Explorer**

### Port already in use

```bash
# Check what's using port 8765
lsof -i :8765              # macOS/Linux
netstat -ano | findstr :8765   # Windows
```

### Plugin not loading

Check the IDE log for errors:
- macOS: `~/Library/Logs/JetBrains/PyCharm*/idea.log`
- Linux: `~/.cache/JetBrains/PyCharm*/log/idea.log`
- Windows: `%APPDATA%\JetBrains\PyCharm*\log\idea.log`

## Building from Source

See [CLAUDE.md](CLAUDE.md) for build instructions.
