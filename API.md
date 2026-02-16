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
  2. frontend (8766): {"action":"scroll","file":"/project/foo.py","line":42}  →  scrolls editor to caret
  ```

- **`"multiple"`** — multiple matches, selector popup shown in IDE. No scroll request needed.
  The user selects from the popup, which navigates and scrolls automatically.

- **`"error"`** — no matches found. No further action.

The two-step flow for `"ok"` is required for remote development (WSL/Gateway) where
the backend runs headlessly and cannot scroll the editor. The frontend plugin runs on
the thin client where scroll APIs work. For local development, both plugins run in the
same IDE instance.

## Request Types

### 1. File Navigation

Navigate to a file, optionally at a specific line.

```json
{"type":"file","path":"foo/bar.py","line":42}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | string | yes | Must be `"file"` |
| `path` | string | yes | File path (partial matching supported) |
| `line` | integer | no | Line number (1-indexed) |

**Path matching:** Uses suffix-based matching. `"foo/bar.py"` matches `/project/src/foo/bar.py`.

---

### 2. Symbol Navigation

Navigate to a class, function, method, variable, or constant by name.

```json
{"type":"symbol","name":"MyClass.method"}
{"type":"symbol","name":"MyClass","fileHint":"models.py"}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | string | yes | Must be `"symbol"` |
| `name` | string | yes | Qualified symbol name |
| `fileHint` | string | no | File path hint (partial matching supported) |

**Name formats:**
- `"MyClass"` - finds class
- `"my_function"` - finds function
- `"MyClass.method"` - finds method in MyClass
- `"MY_CONSTANT"` - finds module-level constant/variable
- `"Warm"` - partial prefix match → finds `Warmup`
- `"warmup"` - case-insensitive → finds `Warmup`
- `"UV"` - camelCase → finds `URLValue`
- `"self.method"` - strips `self`/`cls` prefix automatically

**Resolution algorithm:**
1. Strip leading `self`/`cls` prefix
2. Split name by `.`, rightmost part is the symbol name, rest are qualifiers
3. Exact lookup via all symbol contributors (classes, functions, variables, constants)
4. If no exact match, fallback to partial matching (prefix, camelCase, case-insensitive)
5. Soft qualifier filtering — each qualifier narrows results only if it produces matches (e.g., `WrongClass.save` still finds `save`)
6. If `fileHint` provided and matches some results, filter to those (soft: if hint matches nothing, it's ignored)

---

### 3. Text Search

Navigate by searching file contents for a code snippet.

```json
{"type":"text","text":"def process():"}
{"type":"text","text":"def main():","fileHint":"app.py"}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | string | yes | Must be `"text"` |
| `text` | string | yes | Text to search for (trimmed before search) |
| `fileHint` | string | no | File path hint (partial matching supported) |

**Use case:** When you have a code snippet but no line number.

**Performance:** Searches all `.py` files. Fast for small/medium projects, slower for large projects.

**File hint:** If `fileHint` is provided and matches some results, only those are returned (soft matching: if hint matches nothing, it's ignored).

---

### 4. Scroll to Caret (Frontend, port 8766)

Scroll the active editor to the current caret position. The `file` and `line`
fields from the backend response must be forwarded here so the frontend can
wait for the editor state to match before scrolling (handles async Rd protocol
propagation on remote dev).

```json
{"action":"scroll","file":"/project/foo.py","line":42}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `action` | string | yes | Must be `"scroll"` |
| `file` | string | yes | File path from backend response |
| `line` | integer | yes | Line number from backend response (1-indexed) |

The frontend polls until the editor has the expected file open at the expected
caret line (up to 3s), then scrolls. Path matching is suffix-based to handle
different path prefixes across local/WSL/thin-client.

Send this after a backend response with `status: "ok"`. Not needed for
`"multiple"` (user selects from popup, which scrolls automatically) or `"error"`.

---

## Response

### Backend responses (port 8765)

```json
{"status":"ok","message":"caret=42:0","file":"/project/foo.py","line":42}
{"status":"multiple","count":3}
{"status":"error","message":"Not found"}
```

| Field | Type | Description |
|-------|------|-------------|
| `status` | string | `"ok"`, `"multiple"`, or `"error"` |
| `count` | integer | Number of matches (only when `status` is `"multiple"`) |
| `message` | string | Diagnostic info or error description |
| `file` | string | Absolute file path (only when `status` is `"ok"`) |
| `line` | integer | 1-indexed line number (only when `status` is `"ok"`) |

### Frontend responses (port 8766)

```json
{"status":"ok","message":"scrolled to 42:0"}
{"status":"error","message":"no-editor"}
```

| Status | Meaning |
|--------|---------|
| `ok` | Editor scrolled to caret position |
| `error` | No active editor or other error |

---

## Examples

### File navigation (two-step)

```bash
# Step 1: open file and move caret (backend)
printf '{"type":"file","path":"models.py","line":42}\n' | nc -w 2 localhost 8765
# → {"status":"ok","message":"caret=42:0","file":"/project/models.py","line":42}

# Step 2: scroll to caret (frontend) — forward file/line from backend response
printf '{"action":"scroll","file":"/project/models.py","line":42}\n' | nc -w 2 localhost 8766
```

### Symbol navigation

```bash
# Find class
printf '{"type":"symbol","name":"UserModel"}\n' | nc -w 2 localhost 8765
# → {"status":"ok","file":"/project/models.py","line":10,...}
printf '{"action":"scroll","file":"/project/models.py","line":10}\n' | nc -w 2 localhost 8766

# Find method in class
printf '{"type":"symbol","name":"UserModel.save"}\n' | nc -w 2 localhost 8765

# Find class with file hint
printf '{"type":"symbol","name":"UserModel","fileHint":"models.py"}\n' | nc -w 2 localhost 8765

# Find constant/variable
printf '{"type":"symbol","name":"NUM_WORKERS"}\n' | nc -w 2 localhost 8765

# Partial match (prefix, case-insensitive, camelCase)
printf '{"type":"symbol","name":"Warm"}\n' | nc -w 2 localhost 8765
```

### Text search

```bash
printf '{"type":"text","text":"def main():"}\n' | nc -w 2 localhost 8765
# → {"status":"ok","file":"/project/app.py","line":5,...}
printf '{"action":"scroll","file":"/project/app.py","line":5}\n' | nc -w 2 localhost 8766

# Text search with file hint
printf '{"type":"text","text":"def main():","fileHint":"app.py"}\n' | nc -w 2 localhost 8765
```

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
        send({"action": "scroll", "file": result["file"], "line": result["line"]}, host, FRONTEND_PORT)
    return result

# Examples
navigate({"type": "file", "path": "foo.py", "line": 10})
navigate({"type": "symbol", "name": "MyClass.method"})
navigate({"type": "symbol", "name": "MyClass", "fileHint": "models.py"})
navigate({"type": "text", "text": "def main():"})
navigate({"type": "text", "text": "def main():", "fileHint": "app.py"})
```

---

## Path Matching

Paths are matched from the end (suffix matching):

| Request | Candidate | Match |
|---------|-----------|-------|
| `bar.py` | `/project/src/foo/bar.py` | ✓ |
| `foo/bar.py` | `/project/src/foo/bar.py` | ✓ |
| `src/bar.py` | `/project/src/foo/bar.py` | ✗ |
