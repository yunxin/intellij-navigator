# Build

Requires JDK 17+. Use PyCharm's bundled JDK:

```bash
export JAVA_HOME="/Applications/PyCharm CE.app/Contents/jbr/Contents/Home"
```

## Commands

- Build plugin: `./gradlew buildPlugin`
- Run tests: `./gradlew test`
- Build output: `build/distributions/pycharm-navigator-*.zip`

After building, copy the zip to `dist/`:

```bash
cp build/distributions/pycharm-navigator-1.0.0.zip dist/pycharm-navigator-1.0.0.zip
```
