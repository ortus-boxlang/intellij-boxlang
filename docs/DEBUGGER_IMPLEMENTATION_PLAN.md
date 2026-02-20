# BoxLang Debugger Implementation Plan

This document outlines the steps to integrate a Debug Adapter Protocol (DAP) server into the IntelliJ BoxLang plugin.

---

## Current Progress

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | Run Configuration Foundation | ✅ Complete |
| 2 | Run Configuration Producer | ✅ Complete |
| 3 | DAP Client Infrastructure | ✅ Complete |
| 4 | Breakpoint Support | ✅ Complete |
| 5 | Debug Process Core | ✅ Complete |
| 6 | Execution Suspension & Stack Frames | ✅ Complete |
| 7 | Variables Display | ✅ Complete |
| 8 | Stepping Controls | ✅ Complete |
| 9 | Watch Expressions & Evaluation | ✅ Complete |
| 10 | Console Integration | Pending |

**Branch:** `debugger`

### Completed Commits
- `133489a` - Implement Phase 1: Run configuration foundation
- `5f0a482` - Add 'use current file' option to run configuration
- `5e52034` - Split file templates into BoxLang Class and BoxLang Script
- `e1e371e` - Implement Phase 2: Run configuration producer and gutter icons
- `6c22f58` - Show run gutter icon on main() method for .bx class files
- `c898c9d` - Implement Phase 3: DAP client infrastructure
- `4c2b357` - Implement Phase 4: Breakpoint support
- `b96c4f6` - Fix breakpoint icons to use standard red dots
- `b9664f8` - Implement Phase 5: Debug process core
- `4cf9d76` - Implement Phase 6-8: Stack frames, variables, stepping, session lifecycle fix

---

## Overview

The goal is to integrate an external DAP server (provided as a `.jar` file) into IntelliJ's debugging infrastructure. This follows a similar pattern to the existing LSP integration in `BoxLangLspClientService.java`.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        IntelliJ IDEA                            │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────┐  │
│  │ Run Config      │───▶│ Program Runner  │───▶│ Debug       │  │
│  │ (BoxLangDebug)  │    │ (DebugRunner)   │    │ Process     │  │
│  └─────────────────┘    └─────────────────┘    └──────┬──────┘  │
│                                                       │         │
│  ┌─────────────────┐    ┌─────────────────┐          │         │
│  │ Breakpoint      │    │ XDebugProcess   │◀─────────┘         │
│  │ Handler         │───▶│ (DAP Client)    │                    │
│  └─────────────────┘    └────────┬────────┘                    │
└──────────────────────────────────┼──────────────────────────────┘
                                   │ JSON-RPC/DAP
                                   ▼
                    ┌──────────────────────────────┐
                    │     BoxLang DAP Server       │
                    │        (External JAR)        │
                    └──────────────────────────────┘
```

## Dependencies

Add to `build.gradle.kts`:

```kotlin
dependencies {
    // Existing LSP4J dependency already includes DAP support
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.debug:0.22.0")
}
```

> Note: LSP4J includes `org.eclipse.lsp4j.debug` package with full DAP protocol support.

---

## Phase 1: Run Configuration Foundation ✅

**Goal:** Create the basic run configuration infrastructure so users can configure and run BoxLang scripts.

**Status:** Complete

**Files Created:**
- `src/main/java/com/ortussolutions/intellijboxlang/run/BoxLangConfigurationType.java`
- `src/main/java/com/ortussolutions/intellijboxlang/run/BoxLangConfigurationFactory.java`
- `src/main/java/com/ortussolutions/intellijboxlang/run/BoxLangRunConfiguration.java`
- `src/main/java/com/ortussolutions/intellijboxlang/run/BoxLangRunConfigurationOptions.java`
- `src/main/java/com/ortussolutions/intellijboxlang/run/BoxLangRunConfigurationEditor.java`
- `src/main/java/com/ortussolutions/intellijboxlang/run/BoxLangRunProfileState.java`
- `src/test/java/com/ortussolutions/intellijboxlang/run/BoxLangRunConfigurationTest.java`

**Features:**
- Run configurations appear in Run/Debug Configurations dialog
- "Use current file" option (default) runs the currently open file
- Script path, working directory, program arguments, JVM args configurable
- Validation for script path when not using current file
- Smart Java executable resolution (settings → JAVA_HOME → IntelliJ JDK → PATH)

### Testing Criteria

| Test | Result |
|------|--------|
| Open Run/Debug Configurations dialog | ✅ "BoxLang" appears as a configuration type |
| Add new BoxLang configuration | ✅ Configuration editor UI appears |
| Configure script path | ✅ Path field accepts file selection |
| Configure working directory | ✅ Directory field works correctly |
| Configure program arguments | ✅ Arguments field saves/loads correctly |
| Save and reload configuration | ✅ All settings persist correctly |
| Click "Run" button | ✅ BoxLang script executes, output appears in Run tool window |

---

## Phase 2: Run Configuration Producer ✅

**Goal:** Enable right-click "Run" on BoxLang files and gutter run icons.

**Status:** Complete

**Files Created:**
- `src/main/java/com/ortussolutions/intellijboxlang/run/BoxLangRunConfigurationProducer.java`
- `src/main/java/com/ortussolutions/intellijboxlang/run/BoxLangRunLineMarkerProvider.java`
- `src/test/java/com/ortussolutions/intellijboxlang/run/BoxLangRunConfigurationProducerTest.java`

**plugin.xml additions:**
```xml
<runConfigurationProducer implementation="com.ortussolutions.intellijboxlang.run.BoxLangRunConfigurationProducer"/>
<runLineMarkerContributor language="BoxLang" implementationClass="com.ortussolutions.intellijboxlang.run.BoxLangRunLineMarkerProvider"/>
```

### Testing Criteria

| Test | Result |
|------|--------|
| Right-click on .bx file shows "Run 'filename'" in context menu | ✅ |
| Right-click Run creates and runs configuration automatically | ✅ |
| Run same file again reuses existing configuration | ✅ |
| Gutter icon appears on first line of BoxLang files | ✅ |
| Click gutter icon runs the file | ✅ |

---

## Phase 3: DAP Client Infrastructure

**Goal:** Establish communication with the DAP server.

**Status:** ✅ Complete

**Files Created:**
- `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangDapService.java`
- `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangDapClient.java`
- `src/test/java/com/ortussolutions/intellijboxlang/debug/BoxLangDapServiceTest.java`

**Features:**
- DAP server process lifecycle management
- JSON-RPC communication via stdin/stdout
- DapEventListener interface for event callbacks
- Full DAP protocol support (initialize, launch, setBreakpoints, stepping, etc.)

### Testing Criteria

| Test | Result |
|------|--------|
| Unit test: Start DAP service | ✅ Service starts without error |
| Unit test: Connect to DAP server | ✅ Connection established |
| Unit test: Send initialize request | ✅ Receives valid response |
| Unit test: Disconnect | ✅ Clean disconnection |
| Unit test: Stop service | ✅ Process terminated cleanly |

---

## Phase 4: Breakpoint Support

**Goal:** Allow users to set breakpoints in BoxLang files.

**Status:** ✅ Complete

**Files Created:**
- `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangLineBreakpointType.java`
- `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangBreakpointProperties.java`

**plugin.xml additions:**
```xml
<xdebugger.breakpointType implementation="com.ortussolutions.intellijboxlang.debug.BoxLangLineBreakpointType"/>
```

**Features:**
- Line breakpoints in .bx, .bxs, .bxm files
- Breakpoint properties for conditional breakpoints, hit counts, and log expressions
- Breakpoints persist across IDE restarts

### Testing Criteria

| Test | Expected Result |
|------|-----------------|
| Click in gutter of .bx file | Red breakpoint dot appears |
| Click breakpoint again | Breakpoint is removed |
| View Breakpoints dialog | BoxLang breakpoints listed |
| Disable breakpoint | Breakpoint shown as disabled |
| Enable breakpoint | Breakpoint shown as enabled |
| Breakpoint persists after restart | Breakpoints saved and restored |

---

## Phase 5: Debug Process Core

**Goal:** Create the debug process that manages a debug session.

**Status:** ✅ Complete

**Files Created:**
- `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangDebugProcess.java`
- `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangBreakpointHandler.java`
- `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangDebuggerEditorsProvider.java`
- `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangDebugRunner.java`
- `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangSuspendContext.java`

**plugin.xml additions:**
```xml
<programRunner implementation="com.ortussolutions.intellijboxlang.debug.BoxLangDebugRunner"/>
```

**Features:**
- Debug runner that starts the DAP server via bx-debugger module
- Debug process managing DAP connection and debug operations
- Breakpoint handler syncing IntelliJ breakpoints to DAP server
- Basic suspend context (stack frames in Phase 6)
- Stepping operations (step over, step into, step out, resume, pause)

### Testing Criteria

| Test | Expected Result |
|------|-----------------|
| Click "Debug" on BoxLang config | Debug session starts |
| Debug tool window opens | Shows Debugger tab with controls |
| Console output | Debug console shows program output |
| Stop button | Terminates debug session cleanly |

---

## Phase 6: Execution Suspension & Stack Frames

**Goal:** When a breakpoint is hit, show the suspended state with call stack.

**Status:** ✅ Complete

### Tasks

#### 6.1 Create Suspend Context
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangSuspendContext.java`

#### 6.2 Create Execution Stack
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangExecutionStack.java`

#### 6.3 Create Stack Frame
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangStackFrame.java`

#### 6.4 Handle DAP stopped Event
Update `BoxLangDapClient` to handle `stopped` events and create suspend context.

#### 6.5 Debug Session Lifecycle Fix
Created `BoxLangDapProcessHandler` to keep the debug session alive until DAP `terminated`/`exited` events, fixing premature session termination for fast scripts.

**Key Discovery:** `positionReached()` must be called on the EDT via `invokeLater()` for IntelliJ to properly activate debug toolbar buttons.

### Files Created/Modified:
- `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangSuspendContext.java`
- `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangExecutionStack.java`
- `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangStackFrame.java`
- `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangDapProcessHandler.java`

### Testing Criteria

| Test | Expected Result |
|------|-----------------|
| Hit breakpoint | ✅ Execution pauses at breakpoint line |
| Editor highlight | ✅ Current line highlighted in yellow |
| Frames panel | ✅ Shows call stack with function names |
| Click stack frame | ✅ Editor navigates to that frame's location |

---

## Phase 7: Variables Display

**Goal:** Show variables and their values when suspended.

**Status:** ✅ Complete

### Tasks

#### 7.1 Create Named Value
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangNamedValue.java`

Displays variables with name, type, value and supports expandable children for objects/arrays/structs via recursive DAP `variables` requests.

#### 7.2 Update Stack Frame
`BoxLangStackFrame.computeChildren()` fetches scopes via DAP `scopes` request, then fetches variables for each scope via DAP `variables` request.

### Testing Criteria

| Test | Expected Result |
|------|-----------------|
| Variables panel | ✅ Shows local variables |
| Variable types | ✅ Type shown for each variable |
| Variable values | ✅ Current value displayed |
| Expand object | ✅ Shows object properties |
| Expand array | ✅ Shows array elements |

---

## Phase 8: Stepping Controls

**Goal:** Implement step over, step into, step out, and resume.

**Status:** ✅ Complete

### Tasks

- ✅ Implement Step Over (`next` DAP request)
- ✅ Implement Step Into (`stepIn` DAP request)
- ✅ Implement Step Out (`stepOut` DAP request)
- ✅ Implement Resume (`continue` DAP request)
- ✅ Handle DAP `continued` event

All stepping operations are wired in `BoxLangDebugProcess` and send the appropriate DAP requests via `BoxLangDapService`.

### Testing Criteria

| Test | Expected Result |
|------|-----------------|
| Step Over (F8) | ✅ Moves to next line, skips function internals |
| Step Into (F7) | ✅ Enters function call |
| Step Out (Shift+F8) | ✅ Exits current function, stops at caller |
| Resume (F9) | ✅ Continues until next breakpoint or end |

---

## Phase 9: Watch Expressions & Evaluation

**Goal:** Allow users to evaluate expressions during debugging.

**Status:** ✅ Complete

### Tasks

#### 9.1 Create Evaluator
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangEvaluator.java`

Sends DAP `evaluate` requests with the expression and current frame ID. Results are wrapped in `EvaluateResultValue` (inner XValue class) which supports expandable children for complex results.

#### 9.2 Wire Evaluator into Stack Frame
`BoxLangStackFrame.getEvaluator()` returns a `BoxLangEvaluator` bound to the current frame, enabling watch expressions and the Evaluate Expression dialog.

#### 9.3 Editors Provider
`BoxLangDebuggerEditorsProvider` provides BoxLang file type context for expression editing in watch windows and evaluate dialogs, returned via `BoxLangDebugProcess.getEditorsProvider()`.

### Testing Criteria

| Test | Expected Result |
|------|-----------------|
| Add watch expression | Expression appears in Watches panel |
| Watch shows value | Current value displayed when stopped |
| Evaluate expression (Alt+F8) | Evaluate dialog opens |
| Evaluate simple expression | Result shown correctly |

---

## Phase 10: Console Integration

**Goal:** Proper console output and input during debugging.

**Status:** Pending

### Tasks

- Handle DAP `output` events
- Create Debug Console View
- Handle Console Input (if supported)

### Testing Criteria

| Test | Expected Result |
|------|-----------------|
| println output | Appears in Debug Console |
| Error output | Shown in red/error styling |
| Console clear | Can clear console output |

---

## File Structure Summary

```
src/main/java/com/ortussolutions/intellijboxlang/
├── debug/
│   ├── BoxLangDapService.java           # DAP client service
│   ├── BoxLangDapClient.java            # DAP client callbacks
│   ├── BoxLangDapProcessHandler.java    # Custom ProcessHandler for DAP lifecycle
│   ├── BoxLangDebugRunner.java          # Debug program runner
│   ├── BoxLangDebugProcess.java         # XDebugProcess implementation
│   ├── BoxLangSuspendContext.java       # Suspend context
│   ├── BoxLangExecutionStack.java       # Call stack
│   ├── BoxLangStackFrame.java           # Stack frame with evaluator
│   ├── BoxLangNamedValue.java           # Named debug values (expandable)
│   ├── BoxLangEvaluator.java            # Expression evaluator (DAP evaluate)
│   ├── BoxLangLineBreakpointType.java   # Breakpoint type
│   ├── BoxLangBreakpointHandler.java    # Breakpoint handler
│   ├── BoxLangBreakpointProperties.java # Breakpoint properties
│   └── BoxLangDebuggerEditorsProvider.java # Expression editors
├── run/
│   ├── BoxLangConfigurationType.java    # Run config type
│   ├── BoxLangConfigurationFactory.java # Config factory
│   ├── BoxLangRunConfiguration.java     # Run configuration
│   ├── BoxLangRunConfigurationEditor.java # Config editor UI
│   ├── BoxLangRunConfigurationOptions.java # Config options
│   ├── BoxLangRunProfileState.java      # Run profile state
│   ├── BoxLangRunConfigurationProducer.java # Auto-detection
│   └── BoxLangRunLineMarkerProvider.java # Gutter run icons
└── ... (existing files)
```

---

## DAP Protocol Flow

### Session Initialization
```
Client                          Server
  │                               │
  │──── initialize ──────────────▶│
  │◀─── initialize response ──────│
  │──── initialized ─────────────▶│
  │                               │
  │──── setBreakpoints ──────────▶│
  │◀─── setBreakpoints response ──│
  │                               │
  │──── configurationDone ───────▶│
  │◀─── configurationDone resp ───│
  │                               │
  │──── launch/attach ───────────▶│
  │◀─── launch/attach response ───│
```

### Breakpoint Hit
```
Client                          Server
  │                               │
  │◀─── stopped event ────────────│
  │──── threads ─────────────────▶│
  │◀─── threads response ─────────│
  │──── stackTrace ──────────────▶│
  │◀─── stackTrace response ──────│
  │──── scopes ──────────────────▶│
  │◀─── scopes response ──────────│
  │──── variables ───────────────▶│
  │◀─── variables response ───────│
```

---

## References

- [IntelliJ Platform SDK - Run Configurations](https://plugins.jetbrains.com/docs/intellij/run-configurations.html)
- [IntelliJ Platform SDK - Execution](https://plugins.jetbrains.com/docs/intellij/execution.html)
- [IntelliJ Platform SDK - Debugger](https://plugins.jetbrains.com/docs/intellij/debug.html)
- [Debug Adapter Protocol Specification](https://microsoft.github.io/debug-adapter-protocol/)
- [LSP4J Debug Package](https://github.com/eclipse-lsp4j/lsp4j/tree/main/org.eclipse.lsp4j.debug)
- [Existing LSP Client Implementation](../src/main/java/com/ortussolutions/intellijboxlang/lsp/BoxLangLspClientService.java)
