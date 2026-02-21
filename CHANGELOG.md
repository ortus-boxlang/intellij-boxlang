# Changelog

All notable changes to the BoxLang IDE IntelliJ Plugin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0] - 2025-02-20

### Added

#### Syntax Highlighting
- Full lexer-based syntax highlighting for BoxLang files
- Support for `.bx` (class), `.bxm` (template), and `.bxs` (script) file extensions
- Customizable color settings page with 16 configurable attributes
- Highlighting for keywords, identifiers, strings, numbers, comments, and operators
- String interpolation support with `#expression#` syntax
- BoxLang tag highlighting (`<bx:...>`)

#### Language Server Protocol (LSP)
- Complete LSP client implementation
- Semantic token support for enhanced highlighting
- Real-time diagnostics and error reporting
- Document symbols for structure view navigation
- Automatic LSP module download from ForgeBox
- Configurable LSP server JVM settings

#### Run Configurations
- BoxLang run configuration type
- Run current file or specified script path
- Configurable working directory
- Program arguments and environment variables support
- JVM arguments configuration
- Run line markers (gutter icons) for quick execution

#### Debugger
- Full Debug Adapter Protocol (DAP) implementation
- Line breakpoints with conditional expression support
- Step over, step into, step out operations
- Run to cursor functionality
- Variable inspection with expandable nested values
- Watch expressions
- Stack frame navigation
- Expression evaluation in debug console (REPL)
- Attach configuration for connecting to running BoxLang processes
- Path mapping for remote debugging scenarios

#### Project Settings
- Project-level BoxLang configuration
- BoxLang version/jar path configuration
- BoxLang home directory setting
- Java home configuration
- LSP version selection
- LSP max heap size configuration (64-8192 MB)
- Debugger jar path configuration
- `.bvmrc` (BoxLang Version Manager) support
- Optional download prompts

#### Runtime Management
- Automatic BoxLang runtime resolution
- Runtime download from Ortus S3
- Semantic version support
- Local cache management

#### File Templates
- New BoxLang Class template
- New BoxLang Script template
- New BoxLang Template template
- Accessible via New menu in Project view

#### Structure View
- Document outline via LSP document symbols
- Navigate code structure within files

### Technical Details
- Minimum IDE version: IntelliJ IDEA 2024.2 (build 242)
- Java 21 required for BoxLang runtime
- Dependencies: LSP4J 0.22.0, Semver4j 3.1.0

[Unreleased]: https://github.com/ortus-boxlang/intellij-boxlang/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/ortus-boxlang/intellij-boxlang/releases/tag/v1.0.0
