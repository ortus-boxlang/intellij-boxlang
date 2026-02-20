package com.ortussolutions.intellijboxlang.debug;

import com.intellij.execution.console.BaseConsoleExecuteActionHandler;
import com.intellij.execution.console.ConsoleExecuteAction;
import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import com.ortussolutions.intellijboxlang.BoxLangLanguage;
import org.jetbrains.annotations.NotNull;

/**
 * Debug console for BoxLang that provides:
 * <ul>
 *   <li>Program output (stdout/stderr) display via the attached ProcessHandler</li>
 *   <li>A REPL input field for evaluating BoxLang expressions during debugging</li>
 * </ul>
 * <p>
 * The console uses IntelliJ's {@link LanguageConsoleImpl} which provides a split view:
 * the upper area shows output history and the lower area is an editor with BoxLang
 * syntax highlighting where users can type expressions to evaluate.
 * <p>
 * Expression evaluation is sent to the bx-debugger via DAP {@code evaluate} requests
 * with the {@code "repl"} context, scoped to the current top stack frame.
 */
public class BoxLangDebugConsole {
    private final LanguageConsoleView consoleView;
    private final BoxLangDebugProcess debugProcess;

    public BoxLangDebugConsole(@NotNull Project project,
                                @NotNull BoxLangDebugProcess debugProcess) {
        this.debugProcess = debugProcess;
        this.consoleView = new LanguageConsoleImpl(project, "BoxLang Debug Console",
                BoxLangLanguage.INSTANCE);

        // Register the execute action so pressing Enter in the input field triggers evaluation.
        // We must explicitly register the shortcut on the console editor's component —
        // just constructing the action is not enough to bind Enter to it.
        ConsoleExecuteAction executeAction = new ConsoleExecuteAction(
                consoleView, new BoxLangConsoleExecuteHandler());
        executeAction.registerCustomShortcutSet(
                executeAction.getShortcutSet(),
                consoleView.getConsoleEditor().getComponent());
    }

    /**
     * Attaches the ProcessHandler to the console so DAP output events are displayed.
     */
    public void attachToProcess(@NotNull ProcessHandler processHandler) {
        consoleView.attachToProcess(processHandler);
    }

    /**
     * Returns the underlying console view for use as an ExecutionConsole.
     */
    public LanguageConsoleView getConsoleView() {
        return consoleView;
    }

    /**
     * Handles Enter key in the console input field.
     * Sends the typed expression to the DAP server for evaluation and displays the result.
     */
    private class BoxLangConsoleExecuteHandler extends BaseConsoleExecuteActionHandler {

        BoxLangConsoleExecuteHandler() {
            super(true); // preserveMarkup = true
        }

        @Override
        protected void execute(@NotNull String text, @NotNull LanguageConsoleView console) {
            BoxLangDapService dapService = debugProcess.getDapService();
            if (!dapService.isConnected()) {
                console.print("Error: Debug session is not connected\n",
                        ConsoleViewContentType.ERROR_OUTPUT);
                return;
            }

            int frameId = debugProcess.getActiveFrameId();
            if (frameId < 0) {
                console.print("Error: No active stack frame (execution must be paused)\n",
                        ConsoleViewContentType.ERROR_OUTPUT);
                return;
            }

            dapService.evaluate(text, frameId, "repl")
                .thenAccept(response -> {
                    if (response == null) {
                        console.print("No response from debugger\n",
                                ConsoleViewContentType.ERROR_OUTPUT);
                        return;
                    }

                    String result = response.getResult();
                    String type = response.getType();

                    StringBuilder output = new StringBuilder();
                    if (type != null && !type.isEmpty()) {
                        output.append("(").append(type).append(") ");
                    }
                    output.append(result != null ? result : "null");
                    output.append("\n");

                    console.print(output.toString(), ConsoleViewContentType.NORMAL_OUTPUT);
                })
                .exceptionally(ex -> {
                    String message = ex.getMessage();
                    if (ex.getCause() != null) {
                        message = ex.getCause().getMessage();
                    }
                    console.print("Error: " + (message != null ? message : "Evaluation failed") + "\n",
                            ConsoleViewContentType.ERROR_OUTPUT);
                    return null;
                });
        }
    }
}
