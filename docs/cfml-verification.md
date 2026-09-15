# CFML feedback verification

## Companion LSP fix

Syntax diagnostics and project/member completion require the companion changes in `boxlang-lsp` as well as this plugin. The old tested LSP snapshot discarded parser issues; some incomplete templates also returned partial syntax trees without an error. The updated `FileParseResult` publishes parser issues independently of the syntax tree and reports unparsed CFML template content. Corrected files clear the previous diagnostics.

For local testing, build the companion repository with `./gradlew test shadowJar`. Its unpacked module is `build/module`, and its installable ZIP is under `build/distributions`. Point **BoxLang > Project Overrides > LSP module path** at that unpacked module directory, then use **Tools > BoxLang Tooling Status > Retry Language Server**. This avoids changing the global module installation. These source changes and artifacts have not been published as a new release.

## Repeatable protocol audit

Run `scripts/audit-cfml-diagnostics.py` with `--runtime-jar`, `--modules-directory`, and `--output`. The modules directory must contain a `bx-lsp` child. The script copies only that module into a temporary directory and sends real LSP requests for synthetic files. It does not load your application or other installed modules.

Results in `workbench/cfml-lsp-diagnostics-fixed.json` cover 18 cases using BoxLang 1.12.0-snapshot and the rebuilt bx-lsp 1.9.0-snapshot. Runtime and module JAR hashes are recorded. Cases include valid tag/script components, template expressions, empty/whitespace files, comments, mixed text/tags, unmatched tags, unclosed tags after text, unmatched delimiters, and broken expressions. All 18 expectations passed.

The companion LSP suite ran 472 tests: 471 passed and one existing test was skipped. Its new CFML tests also verify that correcting a file clears old errors.

## Plugin integration test

The opt-in `BoxLangLspLiveTest` accepts two Gradle properties:

- `-PboxlangLiveRuntimeJar=/path/to/boxlang-VERSION.jar`
- `-PboxlangLiveLspModule=/path/to/boxlang-lsp/build/module`

Run it with `./gradlew test --tests '*BoxLangLspLiveTest'` and those properties. It uses an isolated module/home, initializes a real language server through the plugin service, requests a CFML diagnostic, checks editor error highlighting and correction clearing, exercises local-function shadowing, project named arguments and typed-array member completion through IntelliJ completion, and retries the server. Without the properties, this opt-in test returns before its integration assertions; the ordinary CI suite does not validate a live LSP. This test does not install the plugin into your normal IDE profile.

Windows/IDEA 2025.3 migration, pinned/open tabs, recent files, and Local History still need live migration verification. The migration guide retains those limits.

## Completion coverage and boundaries

The companion completion tests cover nearest visible function declarations, local functions shadowing built-ins, sibling nested functions staying out of scope, private CFC methods staying hidden from callers, typed native members, and completion against unsaved text before diagnostic debouncing. A temporary completion-only parse recovers a half-typed call without changing the real document or its diagnostics.

The plugin consumes structured callable metadata from the updated LSP. Its local CFDocs catalog filters native member suggestions and supplies CFML signatures, argument names and source links. Completion remains available for built-ins when the server is unavailable. Member suggestions depend on type inference; dynamically assigned receivers and arbitrary incomplete syntax are not fully resolved. Engine/version filtering is outside this change.

Windows/IDEA 2025.3 plugin-switch verification remains manual, including open tabs, pins, recent files and Local History. The migration guide makes no preservation guarantee.

## Final local verification

The plugin suite passed 319 tests with the live runtime/module properties enabled (zero failures, errors or skips). Plugin packaging and task-file formatting checks passed. The rebuilt companion module passed the 18-case protocol diagnostic audit. These results verify local development artifacts; no release or normal-profile installation was performed.
