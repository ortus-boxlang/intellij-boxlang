# BoxLang Debugger Implementation Plan

This document outlines the steps to integrate a Debug Adapter Protocol (DAP) server into the IntelliJ BoxLang plugin.

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

## Phase 1: Run Configuration Foundation

**Goal:** Create the basic run configuration infrastructure so users can configure and run BoxLang scripts.

### Tasks

#### 1.1 Add LSP4J Debug Dependency
Update `build.gradle.kts` to include the DAP library.

#### 1.2 Create Run Configuration Type
**File:** `src/main/java/com/ortussolutions/intellijboxlang/run/BoxLangConfigurationType.java`

```java
public class BoxLangConfigurationType implements ConfigurationType {
    // Define the run configuration type with icon, name, description
}
```

#### 1.3 Create Run Configuration
**File:** `src/main/java/com/ortussolutions/intellijboxlang/run/BoxLangRunConfiguration.java`

Stores configuration options:
- Script/class to run
- Working directory
- Program arguments
- Environment variables
- BoxLang runtime path

#### 1.4 Create Configuration Factory
**File:** `src/main/java/com/ortussolutions/intellijboxlang/run/BoxLangConfigurationFactory.java`

Factory for creating new run configurations.

#### 1.5 Create Run Configuration Editor
**File:** `src/main/java/com/ortussolutions/intellijboxlang/run/BoxLangRunConfigurationEditor.java`

UI form for editing run configuration settings.

#### 1.6 Create Run Profile State
**File:** `src/main/java/com/ortussolutions/intellijboxlang/run/BoxLangRunProfileState.java`

Defines how to execute the BoxLang script (command line, process builder).

#### 1.7 Update plugin.xml
Register the run configuration type.

### Phase 1 Testing Criteria

| Test | Expected Result |
|------|-----------------|
| Open Run/Debug Configurations dialog | "BoxLang" appears as a configuration type |
| Add new BoxLang configuration | Configuration editor UI appears |
| Configure script path | Path field accepts file selection |
| Configure working directory | Directory field works correctly |
| Configure program arguments | Arguments field saves/loads correctly |
| Save and reload configuration | All settings persist correctly |
| Click "Run" button | BoxLang script executes, output appears in Run tool window |
| Script with error | Error output displayed correctly |
| Script with console input | Input works correctly (if applicable) |

### Phase 1 Manual Testing Steps

1. Build and run the plugin in sandbox IDE
2. Create a new project or open existing BoxLang project
3. Go to Run → Edit Configurations
4. Click "+" and verify "BoxLang" appears in the list
5. Create a new BoxLang configuration
6. Set the script path to a simple BoxLang file (e.g., `println("Hello World")`)
7. Click "Run" and verify output appears
8. Test with a script that has an error and verify error output

---

## Phase 2: Run Configuration Producer

**Goal:** Enable right-click "Run" on BoxLang files.

### Tasks

#### 2.1 Create Run Configuration Producer
**File:** `src/main/java/com/ortussolutions/intellijboxlang/run/BoxLangRunConfigurationProducer.java`

Auto-generates run configurations from context (right-click on file).

#### 2.2 Create Run Line Marker Provider (Optional)
**File:** `src/main/java/com/ortussolutions/intellijboxlang/run/BoxLangRunLineMarkerProvider.java`

Adds gutter icons for runnable files/functions.

#### 2.3 Update plugin.xml
Register the run configuration producer.

### Phase 2 Testing Criteria

| Test | Expected Result |
|------|-----------------|
| Right-click on .bx file | "Run 'filename'" appears in context menu |
| Right-click Run | Creates and runs configuration automatically |
| Run same file again | Reuses existing configuration |
| Gutter icon on main file | Run icon appears in gutter (if implemented) |
| Click gutter icon | Runs the file |

### Phase 2 Manual Testing Steps

1. Open a `.bx` file in the editor
2. Right-click in the editor or on the file in Project view
3. Verify "Run 'filename.bx'" appears in the context menu
4. Click it and verify the script runs
5. Check Run Configurations dialog - a new configuration should exist

---

## Phase 3: DAP Client Infrastructure

**Goal:** Establish communication with the DAP server.

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

### Phase 3 Testing Criteria

| Test | Expected Result |
|------|-----------------|
| Unit test: Start DAP service | Service starts without error |
| Unit test: Connect to DAP server | Connection established successfully |
| Unit test: Send initialize request | Receives valid initialize response |
| Unit test: Send initialized notification | No error |
| Unit test: Disconnect | Clean disconnection |
| Unit test: Stop service | Process terminated cleanly |
| Integration test: Full handshake | Complete DAP initialization sequence works |

### Phase 3 Manual Testing Steps

1. Write a simple test that starts the DAP service
2. Verify the DAP server JAR process starts
3. Verify the initialize/initialized handshake completes
4. Check logs for any connection errors
5. Verify clean shutdown when test ends

---

## Phase 4: Breakpoint Support

**Goal:** Allow users to set breakpoints in BoxLang files.

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

### Phase 4 Testing Criteria

| Test | Expected Result |
|------|-----------------|
| Click in gutter of .bx file | Red breakpoint dot appears |
| Click breakpoint again | Breakpoint is removed |
| View Breakpoints dialog | BoxLang breakpoints listed |
| Disable breakpoint | Breakpoint shown as disabled (hollow dot) |
| Enable breakpoint | Breakpoint shown as enabled (solid dot) |
| Breakpoint persists after restart | Breakpoints saved and restored |
| Cannot set breakpoint on blank line | No breakpoint set (or warning) |

### Phase 4 Manual Testing Steps

1. Open a BoxLang file in the editor
2. Click in the gutter next to a line of code
3. Verify a red breakpoint dot appears
4. Go to Run → View Breakpoints
5. Verify your breakpoint is listed
6. Restart the IDE and verify breakpoints persist

---

## Phase 5: Debug Process Core

**Goal:** Create the debug process that manages a debug session.

### Tasks

#### 5.1 Create XDebugProcess Implementation
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangDebugProcess.java`

The core debug process that bridges IntelliJ's debug UI with the DAP server:

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
    
    // Step over, step into, step out, resume, stop methods
}
```

#### 5.2 Create Breakpoint Handler
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangBreakpointHandler.java`

Syncs breakpoints with DAP server:

```java
public class BoxLangBreakpointHandler 
    extends XBreakpointHandler<XLineBreakpoint<XBreakpointProperties>> {
    
    @Override
    public void registerBreakpoint(@NotNull XLineBreakpoint<XBreakpointProperties> breakpoint) {
        // Send setBreakpoints request to DAP server
    }
    
    @Override
    public void unregisterBreakpoint(@NotNull XLineBreakpoint<XBreakpointProperties> breakpoint, boolean temporary) {
        // Update breakpoints on DAP server
    }
}
```

#### 5.3 Create Debugger Editors Provider
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangDebuggerEditorsProvider.java`

```java
public class BoxLangDebuggerEditorsProvider extends XDebuggerEditorsProviderBase {
    @Override
    public @NotNull FileType getFileType() {
        return BoxLangFileType.INSTANCE;
    }
}
```

#### 5.4 Create Debug Program Runner
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangDebugRunner.java`

```java
public class BoxLangDebugRunner extends GenericProgramRunner<RunnerSettings> {
    @Override
    public String getRunnerId() {
        return "BoxLangDebugRunner";
    }
    
    @Override
    public boolean canRun(String executorId, RunProfile profile) {
        return DefaultDebugExecutor.EXECUTOR_ID.equals(executorId) 
            && profile instanceof BoxLangRunConfiguration;
    }
    
    @Override
    protected RunContentDescriptor doExecute(RunProfileState state, ExecutionEnvironment env) {
        // Start DAP, create XDebugSession
    }
}
```

#### 5.5 Update plugin.xml
Register the debug runner.

### Phase 5 Testing Criteria

| Test | Expected Result |
|------|-----------------|
| Click "Debug" on BoxLang config | Debug session starts |
| Debug tool window opens | Shows Debugger tab with controls |
| Console output | Debug console shows program output |
| Stop button | Terminates debug session cleanly |
| Debug session ends | UI returns to normal state |
| Breakpoints sent to DAP | DAP server receives setBreakpoints request |

### Phase 5 Manual Testing Steps

1. Create a BoxLang run configuration
2. Set a breakpoint in the script
3. Click the Debug button (bug icon)
4. Verify the Debug tool window opens
5. Verify the script starts executing
6. Click Stop and verify clean termination

---

## Phase 6: Execution Suspension & Stack Frames

**Goal:** When a breakpoint is hit, show the suspended state with call stack.

### Tasks

#### 6.1 Create Suspend Context
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangSuspendContext.java`

```java
public class BoxLangSuspendContext extends XSuspendContext {
    @Override
    public XExecutionStack getActiveExecutionStack() {
        return activeStack;
    }
    
    @Override
    public XExecutionStack[] getExecutionStacks() {
        return stacks;
    }
}
```

#### 6.2 Create Execution Stack
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangExecutionStack.java`

```java
public class BoxLangExecutionStack extends XExecutionStack {
    @Override
    public XStackFrame getTopFrame() {
        return topFrame;
    }
    
    @Override
    public void computeStackFrames(int firstFrameIndex, XStackFrameContainer container) {
        // Fetch stack frames from DAP server
    }
}
```

#### 6.3 Create Stack Frame
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangStackFrame.java`

```java
public class BoxLangStackFrame extends XStackFrame {
    @Override
    public XSourcePosition getSourcePosition() {
        return sourcePosition;
    }
    
    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
        // Fetch variables from DAP server scopes/variables requests
    }
}
```

#### 6.4 Handle DAP stopped Event
Update `BoxLangDapClient` to handle `stopped` events and create suspend context.

### Phase 6 Testing Criteria

| Test | Expected Result |
|------|-----------------|
| Hit breakpoint | Execution pauses at breakpoint line |
| Editor highlight | Current line highlighted in yellow |
| Frames panel | Shows call stack with function names |
| Click stack frame | Editor navigates to that frame's location |
| Multiple threads | Each thread shown in Frames dropdown (if applicable) |
| Source position correct | Line numbers match actual code |

### Phase 6 Manual Testing Steps

1. Create a BoxLang script with a function call:
   ```boxlang
   function foo() {
       println("in foo");  // Set breakpoint here
   }
   foo();
   ```
2. Set a breakpoint inside the function
3. Debug the script
4. Verify execution stops at the breakpoint
5. Verify the Frames panel shows the call stack
6. Click on different stack frames and verify navigation

---

## Phase 7: Variables Display

**Goal:** Show variables and their values when suspended.

### Tasks

#### 7.1 Create Debug Value
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangValue.java`

Base class for debug values.

#### 7.2 Create Named Value
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangNamedValue.java`

Represents a variable with name, type, and value.

```java
public class BoxLangNamedValue extends XNamedValue {
    @Override
    public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
        node.setPresentation(icon, type, value, hasChildren);
    }
    
    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
        // For complex types, fetch child properties
    }
}
```

#### 7.3 Update Stack Frame
Update `BoxLangStackFrame.computeChildren()` to fetch and display variables.

### Phase 7 Testing Criteria

| Test | Expected Result |
|------|-----------------|
| Variables panel | Shows local variables |
| Variable types | Type shown for each variable |
| Variable values | Current value displayed |
| Expand object | Shows object properties |
| Expand array | Shows array elements |
| Modify value (if supported) | Value changes in running program |
| Hover over variable in editor | Shows value tooltip |

### Phase 7 Manual Testing Steps

1. Create a script with variables:
   ```boxlang
   var name = "John";
   var age = 30;
   var person = { name: name, age: age };
   println(person);  // Set breakpoint here
   ```
2. Set breakpoint and debug
3. When stopped, check Variables panel
4. Verify `name`, `age`, and `person` are shown with correct values
5. Expand `person` to see nested properties

---

## Phase 8: Stepping Controls

**Goal:** Implement step over, step into, step out, and resume.

### Tasks

#### 8.1 Implement Step Over
```java
@Override
public void startStepOver(@Nullable XSuspendContext suspendContext) {
    dapService.getServer().next(new NextArguments());
}
```

#### 8.2 Implement Step Into
```java
@Override
public void startStepInto(@Nullable XSuspendContext suspendContext) {
    dapService.getServer().stepIn(new StepInArguments());
}
```

#### 8.3 Implement Step Out
```java
@Override
public void startStepOut(@Nullable XSuspendContext suspendContext) {
    dapService.getServer().stepOut(new StepOutArguments());
}
```

#### 8.4 Implement Resume
```java
@Override
public void resume(@Nullable XSuspendContext suspendContext) {
    dapService.getServer().continue_(new ContinueArguments());
}
```

#### 8.5 Handle DAP continued Event
Update `BoxLangDapClient` to handle `continued` events.

### Phase 8 Testing Criteria

| Test | Expected Result |
|------|-----------------|
| Step Over (F8) | Moves to next line, skips function internals |
| Step Into (F7) | Enters function call |
| Step Out (Shift+F8) | Exits current function, stops at caller |
| Resume (F9) | Continues until next breakpoint or end |
| Step from last line | Program completes normally |
| Step into built-in | Handles gracefully (skips or enters) |

### Phase 8 Manual Testing Steps

1. Create a script with function calls:
   ```boxlang
   function add(a, b) {
       var result = a + b;
       return result;
   }
   
   var x = 1;      // Breakpoint here
   var y = 2;
   var sum = add(x, y);
   println(sum);
   ```
2. Debug and stop at first breakpoint
3. Press F8 (Step Over) - should move to next line
4. Continue stepping until `add(x, y)` call
5. Press F7 (Step Into) - should enter `add` function
6. Press Shift+F8 (Step Out) - should return to caller
7. Press F9 (Resume) - should continue to end

---

## Phase 9: Watch Expressions & Evaluation

**Goal:** Allow users to evaluate expressions during debugging.

### Tasks

#### 9.1 Implement Expression Evaluation
Update `BoxLangDebugProcess` to support expression evaluation:

```java
@Override
public void evaluateExpression(@NotNull String expression, 
                                @NotNull XDebuggerEvaluator.XEvaluationCallback callback) {
    EvaluateArguments args = new EvaluateArguments();
    args.setExpression(expression);
    args.setFrameId(currentFrameId);
    
    dapService.getServer().evaluate(args).thenAccept(response -> {
        callback.evaluated(new BoxLangValue(response.getResult()));
    });
}
```

#### 9.2 Create Evaluator
**File:** `src/main/java/com/ortussolutions/intellijboxlang/debug/BoxLangEvaluator.java`

```java
public class BoxLangEvaluator extends XDebuggerEvaluator {
    @Override
    public void evaluate(@NotNull String expression,
                         @NotNull XEvaluationCallback callback,
                         @Nullable XSourcePosition expressionPosition) {
        // Evaluate expression via DAP
    }
}
```

#### 9.3 Update Editors Provider
Ensure proper document creation for evaluate dialog.

### Phase 9 Testing Criteria

| Test | Expected Result |
|------|-----------------|
| Add watch expression | Expression appears in Watches panel |
| Watch shows value | Current value displayed when stopped |
| Watch updates on step | Value refreshes after each step |
| Evaluate expression (Alt+F8) | Evaluate dialog opens |
| Evaluate simple expression | Result shown correctly |
| Evaluate complex expression | Object/array result expandable |
| Invalid expression | Error message shown |
| Remove watch | Expression removed from panel |

### Phase 9 Manual Testing Steps

1. Debug a script with variables
2. In the Watches panel, click "+" to add a watch
3. Enter an expression like `x + y`
4. Verify the result is shown
5. Step to a new line and verify watch updates
6. Press Alt+F8 to open Evaluate dialog
7. Enter an expression and verify result

---

## Phase 10: Console Integration

**Goal:** Proper console output and input during debugging.

### Tasks

#### 10.1 Handle DAP output Events
Update `BoxLangDapClient` to handle `output` events and display in console.

#### 10.2 Create Debug Console View
Ensure proper console integration with the Debug tool window.

#### 10.3 Handle Console Input (if supported)
Route user input from console to DAP server.

### Phase 10 Testing Criteria

| Test | Expected Result |
|------|-----------------|
| println output | Appears in Debug Console |
| Error output | Shown in red/error styling |
| Console clear | Can clear console output |
| Copy from console | Text can be selected and copied |
| Console input (if supported) | Can type input for running program |

### Phase 10 Manual Testing Steps

1. Debug a script with output:
   ```boxlang
   println("Hello");
   println("World");
   ```
2. Verify both lines appear in Debug Console
3. Test with error output if applicable

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
│   ├── BoxLangRunProfileState.java      # Run profile state
│   ├── BoxLangRunner.java               # Run program runner
│   ├── BoxLangRunConfigurationProducer.java # Auto-detection
│   └── BoxLangRunLineMarkerProvider.java # Gutter run icons
└── ... (existing files)
```

---

## Test File Structure

```
src/test/java/com/ortussolutions/intellijboxlang/
├── debug/
│   ├── BoxLangDapServiceTest.java       # DAP service unit tests
│   ├── BoxLangDapClientTest.java        # DAP client tests
│   ├── BoxLangDebugProcessTest.java     # Debug process tests
│   ├── BoxLangBreakpointHandlerTest.java # Breakpoint tests
│   └── BoxLangDebugIntegrationTest.java # Integration tests
├── run/
│   ├── BoxLangRunConfigurationTest.java # Run config tests
│   └── BoxLangRunProfileStateTest.java  # Execution tests
└── ... (existing tests)
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

## Phase Summary

| Phase | Description | Key Deliverables |
|-------|-------------|------------------|
| 1 | Run Configuration Foundation | Run configs work, scripts execute |
| 2 | Run Configuration Producer | Right-click to run files |
| 3 | DAP Client Infrastructure | DAP connection established |
| 4 | Breakpoint Support | Breakpoints can be set in UI |
| 5 | Debug Process Core | Debug sessions start/stop |
| 6 | Execution Suspension & Stack Frames | Call stack displayed on breakpoint |
| 7 | Variables Display | Variables shown when suspended |
| 8 | Stepping Controls | Step over/into/out/resume work |
| 9 | Watch Expressions & Evaluation | Expression evaluation works |
| 10 | Console Integration | Console output during debug |

---

## References

- [IntelliJ Platform SDK - Run Configurations](https://plugins.jetbrains.com/docs/intellij/run-configurations.html)
- [IntelliJ Platform SDK - Execution](https://plugins.jetbrains.com/docs/intellij/execution.html)
- [IntelliJ Platform SDK - Debugger](https://plugins.jetbrains.com/docs/intellij/debug.html)
- [Debug Adapter Protocol Specification](https://microsoft.github.io/debug-adapter-protocol/)
- [LSP4J Debug Package](https://github.com/eclipse-lsp4j/lsp4j/tree/main/org.eclipse.lsp4j.debug)
- [Existing LSP Client Implementation](../src/main/java/com/ortussolutions/intellijboxlang/lsp/BoxLangLspClientService.java)
