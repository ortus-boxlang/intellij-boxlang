package com.ortussolutions.intellijboxlang.debug;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePlace;
import org.eclipse.lsp4j.debug.EvaluateResponse;
import org.eclipse.lsp4j.debug.Variable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Evaluates BoxLang expressions during debugging.
 * Used by the Watch panel, Evaluate Expression dialog (Alt+F8),
 * and hover evaluation in the editor.
 * 
 * Sends DAP `evaluate` requests to the bx-debugger server, which evaluates
 * the expression in the context of the current stack frame.
 */
public class BoxLangEvaluator extends XDebuggerEvaluator {
    private static final Logger LOG = Logger.getInstance(BoxLangEvaluator.class);

    private final BoxLangDebugProcess debugProcess;
    private final int frameId;

    public BoxLangEvaluator(@NotNull BoxLangDebugProcess debugProcess, int frameId) {
        this.debugProcess = debugProcess;
        this.frameId = frameId;
    }

    @Override
    public void evaluate(@NotNull String expression,
                          @NotNull XEvaluationCallback callback,
                          @Nullable XSourcePosition expressionPosition) {
        LOG.debug("Evaluating expression: " + expression + " in frame " + frameId);

        BoxLangDapService dapService = debugProcess.getDapService();
        if (dapService == null || !dapService.isConnected()) {
            callback.errorOccurred("Debug session is not connected");
            return;
        }

        // Determine evaluation context based on where it's being evaluated
        // "watch" for watch panel, "repl" for console, "hover" for editor hover
        String context = "watch";

        dapService.evaluate(expression, frameId, context)
            .thenAccept(response -> {
                if (response == null) {
                    callback.errorOccurred("No response from debugger");
                    return;
                }

                // Create an XValue from the evaluate response
                callback.evaluated(new EvaluateResultValue(debugProcess, expression, response));
            })
            .exceptionally(ex -> {
                String message = ex.getMessage();
                if (ex.getCause() != null) {
                    message = ex.getCause().getMessage();
                }
                LOG.debug("Evaluation failed for '" + expression + "': " + message);
                callback.errorOccurred(message != null ? message : "Evaluation failed");
                return null;
            });
    }

    /**
     * XValue wrapper for DAP evaluate response.
     * Displays the result and supports expanding if it has child variables.
     */
    private static class EvaluateResultValue extends XValue {
        private final BoxLangDebugProcess debugProcess;
        private final String expression;
        private final EvaluateResponse response;

        EvaluateResultValue(@NotNull BoxLangDebugProcess debugProcess,
                             @NotNull String expression,
                             @NotNull EvaluateResponse response) {
            this.debugProcess = debugProcess;
            this.expression = expression;
            this.response = response;
        }

        @Override
        public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
            String value = response.getResult() != null ? response.getResult() : "";
            String type = response.getType();
            boolean hasChildren = response.getVariablesReference() > 0;

            node.setPresentation(null, type, value, hasChildren);
        }

        @Override
        public void computeChildren(@NotNull XCompositeNode node) {
            int ref = response.getVariablesReference();
            if (ref <= 0) {
                node.addChildren(XValueChildrenList.EMPTY, true);
                return;
            }

            BoxLangDapService dapService = debugProcess.getDapService();
            if (dapService == null || !dapService.isConnected()) {
                node.addChildren(XValueChildrenList.EMPTY, true);
                return;
            }

            dapService.variables(ref)
                .thenAccept(variablesResponse -> {
                    XValueChildrenList children = new XValueChildrenList();

                    if (variablesResponse != null && variablesResponse.getVariables() != null) {
                        for (Variable childVar : variablesResponse.getVariables()) {
                            children.add(new BoxLangNamedValue(debugProcess, childVar));
                        }
                    }

                    node.addChildren(children, true);
                })
                .exceptionally(ex -> {
                    LOG.warn("Failed to fetch children for evaluated expression: " + expression, ex);
                    node.addChildren(XValueChildrenList.EMPTY, true);
                    return null;
                });
        }
    }
}
