package com.ortussolutions.intellijboxlang.debug;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValueChildrenList;
import org.eclipse.lsp4j.debug.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a single stack frame in the BoxLang debugger.
 * Maps DAP StackFrame data to IntelliJ's XStackFrame interface.
 * 
 * This provides:
 * - Source position (file + line) so IntelliJ highlights the current line
 * - Frame display name in the Frames panel
 * - Variable computation (Phase 7 will add real variables)
 */
public class BoxLangStackFrame extends XStackFrame {
    private static final Logger LOG = Logger.getInstance(BoxLangStackFrame.class);

    private final BoxLangDebugProcess debugProcess;
    private final StackFrame dapFrame;
    private final XSourcePosition sourcePosition;

    public BoxLangStackFrame(@NotNull BoxLangDebugProcess debugProcess,
                              @NotNull StackFrame dapFrame) {
        this.debugProcess = debugProcess;
        this.dapFrame = dapFrame;
        this.sourcePosition = computeSourcePosition(dapFrame);
    }

    /**
     * Returns the DAP frame ID, used for scopes/variables requests.
     */
    public int getFrameId() {
        return dapFrame.getId();
    }

    @Override
    public @Nullable XSourcePosition getSourcePosition() {
        return sourcePosition;
    }

    @Override
    public @Nullable XDebuggerEvaluator getEvaluator() {
        return new BoxLangEvaluator(debugProcess, dapFrame.getId());
    }

    @Override
    public void customizePresentation(@NotNull ColoredTextContainer component) {
        String name = dapFrame.getName();
        if (name == null || name.isEmpty()) {
            name = "<unknown>";
        }

        // Display: "functionName() at file.bxs:line"
        component.append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES);

        Source source = dapFrame.getSource();
        if (source != null && source.getName() != null) {
            component.append("  ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
            component.append(source.getName() + ":" + dapFrame.getLine(),
                    SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
    }

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
        // Fetch scopes for this frame, then fetch variables for each scope
        BoxLangDapService dapService = debugProcess.getDapService();
        if (dapService == null || !dapService.isConnected()) {
            node.addChildren(XValueChildrenList.EMPTY, true);
            return;
        }

        dapService.scopes(dapFrame.getId())
            .thenAccept(scopesResponse -> {
                if (scopesResponse == null || scopesResponse.getScopes() == null ||
                        scopesResponse.getScopes().length == 0) {
                    node.addChildren(XValueChildrenList.EMPTY, true);
                    return;
                }

                Scope[] scopes = scopesResponse.getScopes();
                // Fetch variables for all scopes and add them as children
                fetchScopeVariables(node, dapService, scopes, 0);
            })
            .exceptionally(ex -> {
                LOG.warn("Failed to fetch scopes for frame " + dapFrame.getId(), ex);
                node.addChildren(XValueChildrenList.EMPTY, true);
                return null;
            });
    }

    /**
     * Recursively fetches variables for each scope and adds them to the node.
     * Scopes are processed sequentially so they appear in order in the UI.
     */
    private void fetchScopeVariables(@NotNull XCompositeNode node,
                                      @NotNull BoxLangDapService dapService,
                                      @NotNull Scope[] scopes,
                                      int index) {
        if (index >= scopes.length) {
            // All scopes processed - we're done
            node.addChildren(XValueChildrenList.EMPTY, true);
            return;
        }

        Scope scope = scopes[index];
        boolean isLastScope = (index == scopes.length - 1);

        dapService.variables(scope.getVariablesReference())
            .thenAccept(variablesResponse -> {
                XValueChildrenList children = new XValueChildrenList();

                if (variablesResponse != null && variablesResponse.getVariables() != null) {
                    for (Variable variable : variablesResponse.getVariables()) {
                        children.add(new BoxLangNamedValue(debugProcess, variable));
                    }
                }

                // Add this scope's variables; isLast=true only for the final scope
                node.addChildren(children, isLastScope);

                if (!isLastScope) {
                    // Fetch next scope
                    fetchScopeVariables(node, dapService, scopes, index + 1);
                }
            })
            .exceptionally(ex -> {
                LOG.warn("Failed to fetch variables for scope: " + scope.getName(), ex);
                // Still try to process remaining scopes
                if (isLastScope) {
                    node.addChildren(XValueChildrenList.EMPTY, true);
                } else {
                    fetchScopeVariables(node, dapService, scopes, index + 1);
                }
                return null;
            });
    }

    /**
     * Computes the IntelliJ source position from a DAP StackFrame.
     * Maps the DAP source path + line number to an IntelliJ XSourcePosition.
     */
    private @Nullable XSourcePosition computeSourcePosition(@NotNull StackFrame frame) {
        Source source = frame.getSource();
        if (source == null || source.getPath() == null) {
            LOG.debug("No source path in stack frame: " + frame.getName());
            return null;
        }

        String path = source.getPath();
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
        if (file == null) {
            LOG.debug("Could not find file for source path: " + path);
            return null;
        }

        // DAP uses 1-based lines, IntelliJ uses 0-based
        int line = frame.getLine() - 1;
        if (line < 0) {
            line = 0;
        }

        return XDebuggerUtil.getInstance().createPosition(file, line);
    }
}
