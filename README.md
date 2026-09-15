# BoxLang IDE - IntelliJ Plugin

The official IntelliJ IDEA plugin for [BoxLang](https://boxlang.io), a modern dynamic JVM language. This plugin provides comprehensive language support including syntax highlighting, LSP integration, run configurations, and full debugging capabilities.

## Features

### Syntax Highlighting
- Full lexer-based syntax highlighting for BoxLang files (`.bx`, `.bxm`, `.bxs`)
- Customizable color schemes via **Settings > Editor > Color Scheme > BoxLang**
- Support for keywords, strings, numbers, comments, operators, and string interpolation

### CFML editing

See the [CFML migration guide](docs/cfml-migration.md) for file associations, colors, compatibility, and setup troubleshooting.

These features work locally in `.cfm` and `.cfc` files without the BoxLang runtime or LSP:

- Type `>` to close common CFML block tags, including `cfif`, `cfoutput`, `cfloop`, `cfscript`, `cffunction`, and `cfquery`. The caret stays between the tags. Existing matching closing tags are preserved, including nested tags of the same name.
- Self-closing tags, standalone tags such as `cfset`, custom tags, and optional-body/action tags such as `cftransaction` are not automatically closed.
- Built-in function completion includes 800 Adobe ColdFusion/Lucee functions, signatures, return types, parameter hints, and Quick Documentation. Completion inserts parentheses and reuses existing ones. Named-argument suggestions insert `=` and omit already supplied names; parameter hints follow the argument name when arguments are reordered.
- Suggestions appear in CFScript, CFML expression tags, and hash interpolation, with comments, literal strings, and plain template text excluded.
- With the updated language server running, completion includes visible project functions and indexed CFC methods. Local functions override built-in signatures and documentation. Known native receiver types (for example, an `array` parameter) receive CFDocs-backed member suggestions and parameter hints. Unknown/dynamic receiver types may have no member suggestions.
- Function availability is a combined catalog, not filtered by your server's engine/version. See [companion-module setup and verification](docs/cfml-verification.md).

Function metadata is bundled from [CFDocs](https://github.com/foundeo/cfdocs) under its MIT license; built-in completion works offline. Project and member completion requests use the local language server. The source revision and license are in `src/main/resources/cfml`. To refresh the catalog from a CFDocs checkout, run `python3 scripts/update-cfml-catalog.py /path/to/cfdocs`.

### Language Server Protocol (LSP)
- Semantic token highlighting
- Real-time diagnostics and error reporting
- Document symbols for structure view
- Automatic LSP module download and management

### Runtime setup and troubleshooting

Download notifications start a cancellable background task with byte progress when the server provides a download size. Runtime downloads are checked before replacing the cached JAR. Failed or cancelled downloads offer **Retry**; ordinary editing does not repeatedly prompt. A compatible cached runtime can start the language server while the version catalog is offline.

Use **Tools > BoxLang Tooling Status** (also available through Find Action) to inspect installation and server readiness separately:

- **Waiting**: use the download notification or open BoxLang Settings.
- **Downloading**: inspect the IDE background task for progress or cancellation.
- **Installed**: files are present; the language server may still be starting.
- **Ready**: the language server completed initialization.
- **Failed**: review the failure and startup output, correct the connection, Java, or module settings, then choose **Retry Language Server**.

The dialog includes a local diagnostic report with plugin/IDE/Java versions, configured paths, setup state, and bounded recent startup output. Review and edit it before choosing **Copy Report**. Common credentials and home/project paths are redacted; nothing is automatically uploaded. Custom JVM arguments, environment variables, and source files are excluded. The report shows the IDE log folder if additional logs are needed.

### Run Configurations
- Run BoxLang scripts directly from the IDE
- Run current file or specify script path
- Configurable working directory, program arguments, and environment variables
- JVM arguments support
- Gutter icons for quick run/debug access

### Debugger
- Full Debug Adapter Protocol (DAP) support
- Line breakpoints with conditional expressions
- Step over, step into, step out, and run to cursor
- Variable inspection with expandable nested values
- Watch expressions
- Stack frame navigation
- Expression evaluation in debug console
- Attach to running BoxLang processes
- Path mapping for remote debugging

### Project Settings
- Configurable BoxLang runtime version and path
- BoxLang home directory configuration
- Java home selection
- LSP version and heap size settings
- `.bvmrc` support (BoxLang Version Manager)

### File Templates
- Create new BoxLang Class, Script, or Template files from the New menu

## Requirements

- **IntelliJ IDEA** 2024.2 or later (Community or Ultimate)
- **Java 21** or later (for running BoxLang)

## Installation

### From JetBrains Marketplace

1. Open IntelliJ IDEA
2. Go to **Settings/Preferences > Plugins**
3. Select the **Marketplace** tab
4. Search for "BoxLang IDE"
5. Click **Install**
6. Restart the IDE when prompted

### Manual Installation

1. Download the latest release from the [Releases](https://github.com/ortus-boxlang/intellij-boxlang/releases) page
2. Open IntelliJ IDEA
3. Go to **Settings/Preferences > Plugins**
4. Click the gear icon and select **Install Plugin from Disk...**
5. Select the downloaded `.zip` file
6. Restart the IDE when prompted

## Configuration

### BoxLang Runtime

The plugin can automatically download and manage the BoxLang runtime. To configure:

1. Go to **Settings/Preferences > Languages & Frameworks > BoxLang**
2. Configure the following options:
   - **BoxLang Version**: Select or specify a BoxLang version
   - **BoxLang Home**: Directory for BoxLang configuration and modules
   - **Java Home**: Path to Java 21+ installation

### LSP Settings

- **LSP Version**: Version of the BoxLang LSP module to use
- **Max Heap Size**: Memory allocation for the LSP server (64-8192 MB)

## Usage

### Creating BoxLang Files

Right-click in the Project view and select **New > BoxLang File** to create a new:
- **BoxLang Class** (`.bx`)
- **BoxLang Script** (`.bxs`)
- **BoxLang Template** (`.bxm`)

### Running Scripts

1. Open a BoxLang file
2. Click the green play icon in the gutter, or
3. Right-click and select **Run**, or
4. Create a Run Configuration via **Run > Edit Configurations**

### Debugging

1. Set breakpoints by clicking in the gutter
2. Click the debug icon in the gutter, or
3. Right-click and select **Debug**
4. Use the Debug tool window to inspect variables, evaluate expressions, and control execution

### Attaching to a Running Process

1. Start your BoxLang application with debug flags
2. Create an **Attach to BoxLang** run configuration
3. Configure the host and port
4. Click **Debug** to attach

## File Extensions

| Extension | Description |
|-----------|-------------|
| `.bx` | BoxLang Class file |
| `.bxm` | BoxLang Template file |
| `.bxs` | BoxLang Script file |

## Building from Source

```bash
# Clone the repository
git clone https://github.com/ortus-boxlang/intellij-boxlang.git
cd intellij-boxlang

# Build the plugin
./gradlew buildPlugin

# Run the plugin in a sandboxed IDE
./gradlew runIde

# Run tests
./gradlew test
```

## Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details.

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests with `./gradlew test`
5. Submit a pull request

## Support

- **Documentation**: [boxlang.io](https://boxlang.io)
- **Issues**: [GitHub Issues](https://github.com/ortus-boxlang/intellij-boxlang/issues)
- **Community**: [BoxLang Community](https://community.ortussolutions.com/)

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## About Ortus Solutions

[Ortus Solutions](https://www.ortussolutions.com) is the company behind BoxLang and many other popular open-source projects including ColdBox, CommandBox, and TestBox.

---

Copyright Since 2023 by Ortus Solutions, Corp | [www.boxlang.io](https://boxlang.io) | [www.ortussolutions.com](https://www.ortussolutions.com)
