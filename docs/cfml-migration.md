# Moving a CFML project to BoxLang IDE

## Compatibility and feature coverage

The plugin targets IntelliJ IDEA 2024.2 or later and builds/tests against Community Edition 2024.2.5. It does not require an Ultimate license. This is the validated SDK baseline; it is not proof of testing every later IntelliJ release or operating system.

The editor supports `.cfm` and `.cfc` files. Syntax colors, TODO patterns, common closing-tag insertion, built-in function completion, parameter hints, and Quick Documentation work without installing a BoxLang runtime. LSP features require a compatible BoxLang runtime and the `bx-lsp` module, even when your application runs on Adobe ColdFusion or Lucee. Installing this tooling does not configure your application server.

The local function catalog combines Adobe ColdFusion and Lucee functions. Suggestions are not filtered by your server version. The updated language server adds visible project functions, indexed CFC methods, and CFDocs-backed native members when the receiver type is known. Local functions take precedence over built-ins, including their named parameters and documentation. Dynamic receiver types may remain unresolved. These additions require the companion LSP build; the local catalog still provides built-in functions without it. CFML syntax diagnostics come from `bx-lsp`. The companion LSP change publishes parser errors and detects unparsed template text; older builds may omit these errors. See [verification and companion-module setup](cfml-verification.md) for the development build and tested cases.

## Switch file associations

1. In **Settings > Plugins**, disable the old JetBrains CFML plugin and enable **BoxLang IDE**, then restart IntelliJ when prompted.
2. Open both a `.cfm` and a `.cfc` file. Confirm they use the BoxLang file type/icon.
3. If a file is plain text or still uses another language, open **Settings > Editor > File Types**. Associate `*.cfm` and `*.cfc` with **BoxLang**, removing competing associations when IntelliJ asks. Check for any individual-file type override as well.
4. Test basic coloring and function completion before configuring LSP features. In a script region, type `arrayApp` and invoke completion; in a template, type `<cfoutput>` and check that the closing tag appears.

### Open tabs, pins, and history

Preservation across disabling the old plugin, enabling this one, and restarting IntelliJ has **not yet been verified** for open tabs, pinned files, recent files, or Local History. This guide makes no preservation guarantee. Record your open/pinned files and retain a copy of project settings before the switch if you need to compare or restore that state. Your source files do not need conversion to use the editor.

## Restore familiar colors

Old CFML color settings are not automatically imported. Open **Settings > Editor > Color Scheme > BoxLang** and adjust the categories you use: Keyword, String, Function name (declaration), Function call, Built-in function, and the comment categories. Keep the old color scheme available as a reference while mapping those colors.

TODO patterns use IntelliJ's **Settings > Editor > TODO** configuration. Check the pattern and its custom foreground/background/font settings there. For example, a `HIGH:` marker must match a configured pattern and appear inside a recognized comment. The plugin's automated tests cover custom TODO colors in CFML template comments, script line/block comments, and documentation comments. A specific scheme or snippet may still need investigation if it behaves differently.

There is no need to enable `idea.is.internal` or use the PSI Viewer for normal color configuration or setup troubleshooting.

## Configure language-server tooling

Open **Settings > Languages & Frameworks > BoxLang**. **Project Overrides** under that page can override the global Java home and LSP paths for one project.

Accept the runtime/module download notification, or download from the settings page. Follow progress through the IDE background task. Failed or cancelled downloads offer **Retry**. Runtime installation and a running language server are separate states.

Use **Tools > BoxLang Tooling Status** to see the current state. After correcting Java or module settings, choose **Retry Language Server**, then **Refresh**. **Ready** means initialization completed. **Waiting** means a required component still needs installation. A failed download does not require deleting the cached runtime to retry.

For a support report, review the editable preview, remove anything you do not want to share, and choose **Copy Report**. The report includes versions, setup paths and state, recent startup output, and the IDE log folder. It is not sent automatically. Include a small CFML example and your expected behavior when reporting an editor issue.

## Migration verification still needed

A live Windows/IntelliJ IDEA 2025.3 migration should check:

- Open and pinned `.cfm`/`.cfc` tabs before and after the plugin switch and restart.
- Recent files and Local History before and after the switch.
- File associations for existing projects and individual-file overrides.
- The user's existing color scheme and custom `HIGH:` TODO pattern.
- Runtime download, cancellation, retry, and successful LSP initialization.

These checks remain distinct from the Community 2024.2.5 automated test suite.
