# Build

Requires JDK 17+. Use PyCharm's bundled JDK:

```bash
export JAVA_HOME="/Applications/PyCharm CE.app/Contents/jbr/Contents/Home"
```

## Commands

- Build plugin: `./gradlew buildPlugin`
- Run tests: `./gradlew test`
- Build output: `build/distributions/intellij-navigator-*.zip`

After building, copy the zip to the public repo and update the GitHub release:

```bash
cp build/distributions/intellij-navigator-1.0.0.zip ../agent-term-public/intellij-navigator-1.0.0.zip
cd ../agent-term-public && gh release upload v0.1.0 intellij-navigator-1.0.0.zip --clobber
```

## E2E Testing

Launch sandbox IDE with the plugin:

```bash
./gradlew runIde
```

Wait for the IDE to open and a project to load, then send TCP requests with `printf` + `nc`:

```bash
printf '{"type":"symbol","name":"MyClass"}\n' | nc -w 2 localhost 8765
```

Use `printf` (not `echo`) to avoid shell quoting issues with JSON.

### Sanity check categories

- **Exact lookup**: class, function by name
- **Constants/variables**: module-level assignments, namedtuples
- **Partial matching**: prefix (`Warm` → `Warmup`), case-insensitive (`warmup` → `Warmup`), camelCase (`UV` → `URLValue`)
- **Qualified**: `Class.method`, `module.Class`
- **self/cls stripping**: `self.method` → `method`
- **Soft qualifier fallback**: `WrongClass.method` still finds `method`
- **Negative**: nonexistent symbols return `{"status":"error"}`
