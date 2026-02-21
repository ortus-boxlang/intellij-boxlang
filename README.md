# BoxLang IDE - IntelliJ Plugin

The official IntelliJ IDEA plugin for [BoxLang](https://boxlang.io), a modern dynamic JVM language. This plugin provides comprehensive language support including syntax highlighting, LSP integration, run configurations, and full debugging capabilities.

## Features

### Syntax Highlighting
- Full lexer-based syntax highlighting for BoxLang files (`.bx`, `.bxm`, `.bxs`)
- Customizable color schemes via **Settings > Editor > Color Scheme > BoxLang**
- Support for keywords, strings, numbers, comments, operators, and string interpolation

### Language Server Protocol (LSP)
- Semantic token highlighting
- Real-time diagnostics and error reporting
- Document symbols for structure view
- Automatic LSP module download and management

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
