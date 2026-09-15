# Remaining CFML feedback work

User scope: items 1, 2, 4, 5, and 6 from the feedback follow-up. Item 3 (engine/version preferences) is not part of this goal.

- [x] 1. Runtime setup: progress, actionable errors, explicit retry, runtime/module/server readiness.
- [x] 2. Audit syntax diagnostics against malformed tags, delimiters, expressions, and valid CFML. Verify existing LSP support; fill demonstrated gaps.
- [x] 4. Completion: project-defined functions, named-argument hints, member methods, local shadowing of built-ins.
- [x] 5. Migration guidance delivered with explicit verification limits: file associations, color settings, free IDEA compatibility; verify open/pinned files and history preservation before promising it.
- [x] 6. Diagnostic report: versions, paths, setup state, relevant startup errors/logs; preview and deliberate copy/export, no automatic sharing.

## Verification baseline

Prior to this goal: 295 tests passed; plugin packaging and task-file formatting passed. These are historical results and must be rerun for new changes. Live Windows/IDEA 2025.3 testing has not been performed.

## 2026-09-15 checkpoint

Implemented in this checkout:

- Runtime downloads now stage temporary files, check download length and runtime JAR identity, preserve existing files on failure/cancellation, and clean temporary files.
- Runtime resolution checks compatible valid local installs before contacting the version catalog. Minimum-version prefixes are handled; version-catalog requests have connection/read timeouts.
- Setup runs asynchronously with progress, failure details, cancellation handling, and explicit Retry. Runtime and LSP settings downloads use the same recovery path.
- LSP startup now publishes the server only after initialization, records readiness/failures, drains bounded stdout/stderr, cleans failed processes, and suppresses repeated automatic startup failures. Explicit retry is available. Invalid module overrides produce visible errors instead of silently falling back.
- Tools > BoxLang Tooling Status provides a local snapshot and editable diagnostic preview, explicit Copy Report, refresh, settings access, and server retry. Common credentials and home/project paths are redacted. No automatic sharing.
- `docs/cfml-migration.md` covers associations, colors/TODO settings, free IDEA compatibility, setup, and the limits of migration verification. Open/pinned files and history preservation remain unverified; no preservation promise is made.

Verification: 309 tests passed, zero failures/errors/skips; `buildPlugin` and task-file `spotlessCheck` passed. Tests include real local HTTP responses, interrupted/truncated/invalid runtime downloads, async setup failure/retry/cancellation, startup failure suppression and explicit retry, report redaction, and editable preview/copy behavior. Successful live startup of the plugin service and Windows/IDEA 2025.3 migration remain unverified.

### Diagnostic audit: demonstrated failure, fix pending

`scripts/audit-cfml-diagnostics.py` launches an isolated copy of a local bx-lsp module and sends real LSP initialize/didOpen/diagnostic requests for synthetic CFML. It does not load application code. The first attempt with the entire user modules directory failed on an incompatible bx-compat-cfml module; the probe now copies only bx-lsp into its temporary modules directory.

`workbench/cfml-lsp-diagnostics-baseline.json` records runtime/module identities and JAR hashes. Tested installed bx-lsp 1.9.0-snapshot with BoxLang 1.12.0-snapshot: 5 valid examples correctly had no errors, while all 6 malformed examples incorrectly had no errors (5/11 expectations met). Cases cover missing/mismatched tags, parentheses, braces, and missing/broken expressions.

Read-only source inspection of `/Users/elpete/Developer/github/ortus-boxlang/boxlang-lsp`, clean branch `codex/app-aware-mapping-resolution`, HEAD `ea37dd0`, found `workspace/FileParseResult.java` initializes `issues` empty and never copies parser issues, and generates diagnostics only when an AST root is present. This is the next concrete diagnostic fix to verify. No changes have been made in that repository yet. Its test runtime exists at `src/test/resources/libs/boxlang-1.9.0-snapshot.jar`.

Still required: fix and rerun the demonstrated diagnostic cases; project-defined function/member completion, named-argument hints, and local shadowing; finish migration preservation verification or retain explicit limitations; verify successful plugin-service readiness/recovery before claiming the entire runtime item complete. Update the migration guide's temporary completion/diagnostic limitations when those changes land.

## Diagnostic and completion checkpoint (2026-09-15)

Companion changes now exist in `/Users/elpete/Developer/github/ortus-boxlang/boxlang-lsp` (same previously clean branch, no commit/push/install):

- `FileParseResult.fullyParse()` copies runtime parser issues, resets stale derived state, and generates diagnostics even without an AST.
- A CFML template coverage guard reports meaningful unparsed suffixes when the runtime parser silently returns a partial template. Comments/whitespace are excluded using parser comment data.
- `CfmlSyntaxDiagnosticsTest`: 18 valid/malformed cases plus correction/reparse clearing. Companion full suite: 465 tests, zero failures/errors, one existing skipped test. Targeted Spotless check passed. Module built at `build/module`; ZIP at `build/distributions/bx-lsp-1.9.0-snapshot.zip`.
- Real protocol audit using the rebuilt module and BoxLang 1.12.0-snapshot: **18/18 cases passed**, recorded with artifact hashes in `workbench/cfml-lsp-diagnostics-fixed.json`. Intermediate 10/11 result retained separately.

Plugin changes:

- `BoxLangLspAnnotator` no longer discards zero-width parser errors (missing tokens/EOF); clamps diagnostic columns to the reported line.
- `BoxLangLspClientService.updateDiagnostics()` requests editor rehighlight only when diagnostics change, including clearing old errors.
- Named-argument completion inserts/reuses `=`, omits already supplied names, and parameter hints resolve reordered/case-insensitive names. Six new focused editor tests passed. Project-defined functions, member methods, and local shadowing are still pending.
- Opt-in `BoxLangLspLiveTest` with `-PboxlangLiveRuntimeJar` and `-PboxlangLiveLspModule` verifies real plugin-service startup, diagnostics, editor rendering, and restart. Standalone test passed before adding correction/clearing verification. It uses a physical VFS file and isolated module/home and temporarily adds the runtime to the test cache. Tests must allow the canonical temporary path (`toRealPath`) in VfsRootAccess.
- Full-suite isolation fix: CFML editor/TODO tests mask the annotator extension; they now clear `LanguageAnnotators` caches after masking and after teardown. Otherwise the later live test sees a cached empty annotator list.
- LSP document updates are debounced. The new live correction check waits for the editor's error highlighting to clear; immediately pulling diagnostics after didChange can still return the previous version.
- `docs/cfml-verification.md` documents companion module setup, the audit, and the opt-in integration test. Neither artifact has been published or installed into the normal IDE/user module directory.

Current full verification is in progress (includes live properties, package, task-file formatting). Log: `/tmp/intellij-feedback-full-tests.log`. Do not claim this run passed until its terminal result and XML totals are inspected.

Final verification for this checkpoint: **319 plugin tests passed**, zero failures/errors/skips, with the real-runtime integration enabled. The live test verified startup, actual diagnostic rendering, clearing after a correction, and explicit restart. `buildPlugin`, task-file `spotlessCheck`, and diff whitespace checks passed. Companion suite and 18/18 protocol audit passed as recorded above. The current verification process is complete.

## Final implementation checkpoint (2026-09-15)

Completed the selected implementation scope (items 1, 2, 4, 5, 6). Migration preservation is explicitly unverified; no guarantee is made.

- Plugin completion now consumes structured signatures from the companion LSP for visible project functions and indexed CFC methods. Local functions take precedence over built-ins, including parameter names and documentation.
- LSP completion filters sibling nested functions and prefers the nearest visible declaration. The index now records actual function access modifiers, preventing private CFC methods from appearing for external callers.
- Typed native receivers receive registered member suggestions, filtered through CFDocs in the plugin, with CFML signatures and source links. This is conservative: unresolved/dynamic receiver types and arbitrary incomplete syntax remain limitations.
- Completion reads current unsaved content before diagnostic debouncing and can recover a half-typed call using a temporary parse. It does not replace the user's source or publish repaired diagnostics.
- Live IntelliJ completion verified local `len` shadowing, project named arguments, and array member completion. Requests are cancellable and never wait on the UI thread.
- README, migration guide and verification guide describe the implemented coverage, companion-module requirement, and Windows/IDEA 2025.3 migration checks still needed.

Final checks: 319 plugin tests passed, zero failures/errors/skips, including the real-runtime integration. `buildPlugin` and task-file Spotless checks passed. Companion LSP: 472 tests total, zero failures/errors, one existing skip (471 passed); module packaging and targeted Spotless passed. The rebuilt module passed all 18 protocol diagnostic cases; the audit JSON contains its current hashes. Both worktrees passed `git diff --check`.

No commit, push, release, or normal-profile installation was performed. Both worktrees contain the local changes. Engine/version preferences (item 3) remain outside scope. Live Windows migration, tabs/pins/recent-files/Local History preservation, and broad dynamic receiver inference remain follow-up work.
