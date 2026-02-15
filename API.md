# PyCharm Navigator Plugin - API Reference

TCP protocol for integrating with the PyCharm Navigator plugin.

## Connection

- **Protocol:** TCP
- **Port:** 8765
- **Format:** Newline-delimited JSON

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

Navigate to a class, function, or method by qualified name.

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

**Resolution algorithm:**
1. Split name by `.`
2. Use rightmost part for index lookup
3. Filter by qualifier chain
4. If `fileHint` provided and matches some results, filter to those (soft matching: if hint matches nothing, it's ignored)

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

## Response

All requests return a JSON response:

```json
{"status":"ok"}
{"status":"multiple","count":3}
{"status":"error","message":"Not found"}
```

| Status | Meaning |
|--------|---------|
| `ok` | Single match found, navigated directly |
| `multiple` | Multiple matches, selector popup shown |
| `error` | No matches or other error |

---

## Examples

### File navigation

```bash
echo '{"type":"file","path":"models.py","line":42}' | nc localhost 8765
```

### Symbol navigation

```bash
# Find class
echo '{"type":"symbol","name":"UserModel"}' | nc localhost 8765

# Find method in class
echo '{"type":"symbol","name":"UserModel.save"}' | nc localhost 8765

# Find class with file hint
echo '{"type":"symbol","name":"UserModel","fileHint":"models.py"}' | nc localhost 8765
```

### Text search

```bash
echo '{"type":"text","text":"def main():"}' | nc localhost 8765

# Text search with file hint
echo '{"type":"text","text":"def main():","fileHint":"app.py"}' | nc localhost 8765
```

---

## Client Example (Python)

```python
import socket
import json

def navigate(request: dict, host="localhost", port=8765) -> dict:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.connect((host, port))
        s.sendall((json.dumps(request) + "\n").encode())
        response = s.recv(1024).decode()
        return json.loads(response)

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
