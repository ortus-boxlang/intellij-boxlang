# BoxLang Debugger Implementation Plan

This document outlines the steps to integrate a Debug Adapter Protocol (DAP) server into the IntelliJ BoxLang plugin.

---

## Current Progress

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | Run Configuration Foundation | ✅ Complete |
| 2 | Run Configuration Producer | ✅ Complete |
| 3 | DAP Client Infrastructure | Pending |
| 4 | Breakpoint Support | Pending |
| 5 | Debug Process Core | Pending |
| 6 | Execution Suspension & Stack Frames | Pending |
| 7 | Variables Display | Pending |
| 8 | Stepping Controls | Pending |
| 9 | Watch Expressions & Evaluation | Pending |
| 10 | Console Integration | Pending |

**Branch:** `debugger`

### Completed Commits
- `133489a` - Implement Phase 1: Run configuration foundation
- `5f0a482` - Add 'use current file' option to run configuration
- `5e52034` - Split file templates into BoxLang Class and BoxLang Script
- `e1e371e` - Implement Phase 2: Run configuration producer and gutter icons

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

**Status:** Pending

### Tasks

#### 3.1 Create DAP Client Service
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangDapService.java`

Responsibilities:
- Start the DAP server JAR as an external process
- Establish JSON-RPC communication (stdin/stdout or TCP socket)
- Manage DAP server lifecycle
- Provide DAP client interface to other components

Pattern to follow: `BoxLangLspClientService.java`

```java
@Service(Service.Level.PROJECT)
public final class BoxLangDapService implements Disposable {
    private IDebugProtocolServer debugServer;
    private Future<Void> listenerFuture;
    private Process serverProcess;
    
    // Methods: start(), stop(), getServer(), isConnected()
}
```

#### 3.2 Create DAP Client Implementation
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangDapClient.java`

Implements `IDebugProtocolClient` interface to handle callbacks from DAP server:
- `output()` - Console output
- `stopped()` - Execution stopped (breakpoint, step, etc.)
- `continued()` - Execution continued
- `thread()` - Thread started/exited
- `terminated()` - Debug session ended
- `breakpoint()` - Breakpoint status changed

### Testing Criteria

| Test | Expected Result |
|------|-----------------|
| Unit test: Start DAP service | Service starts without error |
| Unit test: Connect to DAP server | Connection established successfully |
| Unit test: Send initialize request | Receives valid initialize response |
| Unit test: Send initialized notification | No error |
| Unit test: Disconnect | Clean disconnection |
| Unit test: Stop service | Process terminated cleanly |
| Integration test: Full handshake | Complete DAP initialization sequence works |

---

## Phase 4: Breakpoint Support

**Goal:** Allow users to set breakpoints in BoxLang files.

**Status:** Pending

### Tasks

#### 4.1 Create Line Breakpoint Type
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangLineBreakpointType.java`

```java
public class BoxLangLineBreakpointType extends XLineBreakpointType<XBreakpointProperties> {
    public BoxLangLineBreakpointType() {
        super("boxlang-line", "BoxLang Line Breakpoints");
    }
    
    @Override
    public boolean canPutAt(@NotNull VirtualFile file, int line, @NotNull Project project) {
        return file.getFileType() instanceof BoxLangFileType;
    }
}
```

#### 4.2 Create Breakpoint Properties (Optional)
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangBreakpointProperties.java`

For conditional breakpoints, hit counts, etc.

#### 4.3 Update plugin.xml
Register the breakpoint type.

### Testing Criteria

| Test | Expected Result |
|------|-----------------|
| Click in gutter of .bx file | Red breakpoint dot appears |
| Click breakpoint again | Breakpoint is removed |
| View Breakpoints dialog | BoxLang breakpoints listed |
| Disable breakpoint | Breakpoint shown as disabled (hollow dot) |
| Enable breakpoint | Breakpoint shown as enabled (solid dot) |
| Breakpoint persists after restart | Breakpoints saved and restored |

---

## Phase 5: Debug Process Core

**Goal:** Create the debug process that manages a debug session.

**Status:** Pending

### Tasks

#### 5.1 Create XDebugProcess Implementation
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangDebugProcess.java`

```java
public class BoxLangDebugProcess extends XDebugProcess {
    private final BoxLangDapService dapService;
    
    @Override
    public XBreakpointHandler<?>[] getBreakpointHandlers() {
        return new XBreakpointHandler[]{ lineBreakpointHandler };
    }
    
    @Override
    public XDebuggerEditorsProvider getEditorsProvider() {
        return new BoxLangDebuggerEditorsProvider();
    }
}
```

#### 5.2 Create Breakpoint Handler
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangBreakpointHandler.java`

#### 5.3 Create Debugger Editors Provider
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangDebuggerEditorsProvider.java`

#### 5.4 Create Debug Program Runner
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangDebugRunner.java`

#### 5.5 Update plugin.xml
Register the debug runner.

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

**Status:** Pending

### Tasks

#### 6.1 Create Suspend Context
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangSuspendContext.java`

#### 6.2 Create Execution Stack
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangExecutionStack.java`

#### 6.3 Create Stack Frame
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangStackFrame.java`

#### 6.4 Handle DAP stopped Event
Update `BoxLangDapClient` to handle `stopped` events and create suspend context.

### Testing Criteria

| Test | Expected Result |
|------|-----------------|
| Hit breakpoint | Execution pauses at breakpoint line |
| Editor highlight | Current line highlighted in yellow |
| Frames panel | Shows call stack with function names |
| Click stack frame | Editor navigates to that frame's location |

---

## Phase 7: Variables Display

**Goal:** Show variables and their values when suspended.

**Status:** Pending

### Tasks

#### 7.1 Create Debug Value
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangValue.java`

#### 7.2 Create Named Value
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangNamedValue.java`

#### 7.3 Update Stack Frame
Update `BoxLangStackFrame.computeChildren()` to fetch and display variables.

### Testing Criteria

| Test | Expected Result |
|------|-----------------|
| Variables panel | Shows local variables |
| Variable types | Type shown for each variable |
| Variable values | Current value displayed |
| Expand object | Shows object properties |
| Expand array | Shows array elements |

---

## Phase 8: Stepping Controls

**Goal:** Implement step over, step into, step out, and resume.

**Status:** Pending

### Tasks

- Implement Step Over (`next` DAP request)
- Implement Step Into (`stepIn` DAP request)
- Implement Step Out (`stepOut` DAP request)
- Implement Resume (`continue` DAP request)
- Handle DAP `continued` event

### Testing Criteria

| Test | Expected Result |
|------|-----------------|
| Step Over (F8) | Moves to next line, skips function internals |
| Step Into (F7) | Enters function call |
| Step Out (Shift+F8) | Exits current function, stops at caller |
| Resume (F9) | Continues until next breakpoint or end |

---

## Phase 9: Watch Expressions & Evaluation

**Goal:** Allow users to evaluate expressions during debugging.

**Status:** Pending

### Tasks

#### 9.1 Create Evaluator
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangEvaluator.java`

#### 9.2 Implement Expression Evaluation
Use DAP `evaluate` request.

#### 9.3 Update Editors Provider
Ensure proper document creation for evaluate dialog.

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
│   ├── BoxLangDebugRunner.java          # Debug program runner
│   ├── BoxLangDebugProcess.java         # XDebugProcess implementation
│   ├── BoxLangSuspendContext.java       # Suspend context
│   ├── BoxLangExecutionStack.java       # Call stack
│   ├── BoxLangStackFrame.java           # Stack frame
│   ├── BoxLangValue.java                # Debug values
│   ├── BoxLangNamedValue.java           # Named debug values
│   ├── BoxLangEvaluator.java            # Expression evaluator
│   ├── BoxLangLineBreakpointType.java   # Breakpoint type
│   ├── BoxLangBreakpointHandler.java    # Breakpoint handler
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
