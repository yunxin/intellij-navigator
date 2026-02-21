# IntelliJ Navigator Plugin - API Reference

TCP protocol for integrating with the IntelliJ Navigator plugin.

## Connection

Two TCP servers work together:

| Server | Port | Purpose |
|--------|------|---------|
| **Backend** | 8765 | Resolve files/symbols, open file, move caret |
| **Frontend** | 8766 | Scroll editor to caret position |

- **Protocol:** TCP
- **Format:** Newline-delimited JSON

### Navigation flow

Send a request to the backend. Check the response `status`:

- **`"ok"`** — single match, file opened, caret moved. Response includes `file` and `line`.
  Forward them in a scroll request to the frontend:
  ```
  1. backend (8765):  {"type":"file","path":"foo.py","line":42}  →  {"status":"ok","file":"/project/foo.py","line":42}
  2. frontend (8766): {"action":"scroll","file":"/project/foo.py","line":42,"column":0}  →  {"status":"ok"}
  ```

- **`"multiple"`** — multiple matches, selector popup shown in IDE. No scroll request needed.
  The user selects from the popup, which navigates and scrolls automatically.

- **`"error"`** — no matches found. No further action.

The two-step flow for `"ok"` is required for remote development (WSL/Gateway) where
the backend runs headlessly and cannot scroll the editor. The frontend plugin runs on
the thin client where scroll APIs work. For local development, both plugins run in the
same IDE instance.

## Request Types

### File — open a file, optionally at a line

```json
{"type":"file","path":"foo/bar.py","line":42}
{"type":"file","path":"foo/bar.py","matchText":"def process():"}
```

- **line** (optional): 1-indexed line number.
- **matchText** (optional): expected trimmed line content.
  - With **line + matchText**: validates the line; spirals ±200 if mismatch (`"text_moved"`).
  - With **matchText only** (no line): searches the file top-to-bottom for the first match.

Data class: `FileRequest` in `src/main/kotlin/com/claudecode/navigator/model/NavigationRequest.kt`

### Symbol — navigate to a class, function, method, or variable by name

```json
{"type":"symbol","name":"MyClass.method","fileHint":"models.py"}
```

Data class: `SymbolRequest` in `src/main/kotlin/com/claudecode/navigator/model/NavigationRequest.kt`

### Text — search file contents for a code snippet

```json
{"type":"text","text":"def process():","fileHint":"app.py"}
```

Data class: `TextRequest` in `src/main/kotlin/com/claudecode/navigator/model/NavigationRequest.kt`

### Scroll — scroll the frontend editor to the caret (port 8766)

Send after a backend `"ok"` response. Forward the `file` and `line` from that response.

```json
{"action":"scroll","file":"/project/foo.py","line":42,"column":0}
```

Data class: `ScrollRequest` in `frontend-plugin/src/main/kotlin/com/claudecode/navigator/frontend/ScrollServer.kt`

## Responses

### Backend (port 8765)

```json
{"status":"ok","message":"caret=42:0","file":"/project/foo.py","line":42}
{"status":"multiple","count":3}
{"status":"error","message":"Not found"}
```

Data class: `NavigationResponse` in `src/main/kotlin/com/claudecode/navigator/model/NavigationRequest.kt`

### Frontend (port 8766)

```json
{"status":"ok"}
{"status":"file_too_short"}
{"status":"no_editor"}
{"status":"error","message":"..."}
```

Data class: `ScrollResponse` in `frontend-plugin/src/main/kotlin/com/claudecode/navigator/frontend/ScrollServer.kt`

---

## Client Example (Python)

```python
import socket
import json

BACKEND_PORT = 8765
FRONTEND_PORT = 8766

def send(request: dict, host="localhost", port=BACKEND_PORT) -> dict:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.connect((host, port))
        s.sendall((json.dumps(request) + "\n").encode())
        response = s.recv(1024).decode()
        return json.loads(response)

def navigate(request: dict, host="localhost") -> dict:
    """Send navigation request to backend, then scroll via frontend."""
    result = send(request, host, BACKEND_PORT)
    if result.get("status") == "ok":
        send({"action": "scroll", "file": result["file"], "line": result["line"], "column": 0}, host, FRONTEND_PORT)
    return result

# Examples
navigate({"type": "file", "path": "foo.py", "line": 10})
navigate({"type": "symbol", "name": "MyClass.method"})
navigate({"type": "symbol", "name": "MyClass", "fileHint": "models.py"})
navigate({"type": "text", "text": "def main():"})
navigate({"type": "text", "text": "def main():", "fileHint": "app.py"})
```
