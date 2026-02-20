package com.ortussolutions.intellijboxlang.debug;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import org.eclipse.lsp4j.debug.SetBreakpointsResponse;
import org.eclipse.lsp4j.debug.SourceBreakpoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles breakpoint registration and synchronization with the DAP server.
 * Converts IntelliJ breakpoints to DAP breakpoints and sends them to the server.
 */
public class BoxLangBreakpointHandler extends XBreakpointHandler<XLineBreakpoint<BoxLangBreakpointProperties>> {
    private static final Logger LOG = Logger.getInstance(BoxLangBreakpointHandler.class);

    private final BoxLangDebugProcess debugProcess;
    
    // Track breakpoints by file path for efficient updates
    private final Map<String, Set<XLineBreakpoint<BoxLangBreakpointProperties>>> breakpointsByFile = new ConcurrentHashMap<>();
    
    // Track whether initial configuration is complete - before this, don't send breakpoints individually
    private volatile boolean configurationComplete = false;

    public BoxLangBreakpointHandler(@NotNull BoxLangDebugProcess debugProcess) {
        super(BoxLangLineBreakpointType.class);
        this.debugProcess = debugProcess;
    }

    @Override
    public void registerBreakpoint(@NotNull XLineBreakpoint<BoxLangBreakpointProperties> breakpoint) {
        XSourcePosition position = breakpoint.getSourcePosition();
        if (position == null) {
            LOG.warn("Cannot register breakpoint without source position");
            return;
        }

        VirtualFile file = position.getFile();
        String filePath = file.getPath();
        
        LOG.info("Registering breakpoint at " + filePath + ":" + (position.getLine() + 1));
        
        breakpointsByFile.computeIfAbsent(filePath, k -> ConcurrentHashMap.newKeySet()).add(breakpoint);
        
        // Only send breakpoints immediately if initial configuration is complete
        // During startup, breakpoints will be synced all at once via syncAllBreakpoints
        if (configurationComplete) {
            sendBreakpointsToServer(filePath);
        } else {
            LOG.debug("Deferring breakpoint sync until configuration is complete");
        }
    }

    @Override
    public void unregisterBreakpoint(@NotNull XLineBreakpoint<BoxLangBreakpointProperties> breakpoint, boolean temporary) {
        XSourcePosition position = breakpoint.getSourcePosition();
        if (position == null) {
            return;
        }

        VirtualFile file = position.getFile();
        String filePath = file.getPath();
        
        Set<XLineBreakpoint<BoxLangBreakpointProperties>> fileBreakpoints = breakpointsByFile.get(filePath);
        if (fileBreakpoints != null) {
            fileBreakpoints.remove(breakpoint);
            if (fileBreakpoints.isEmpty()) {
                breakpointsByFile.remove(filePath);
            }
        }
        
        sendBreakpointsToServer(filePath);
    }

    /**
     * Builds a DAP SourceBreakpoint from an IntelliJ XLineBreakpoint.
     * Reads condition, log expression from IntelliJ's built-in breakpoint UI fields
     * (XBreakpoint.getConditionExpression() and XBreakpoint.getLogExpressionObject()),
     * NOT from BoxLangBreakpointProperties which are never populated by the UI.
     * 
     * @return The SourceBreakpoint, or null if the breakpoint is disabled or has no position.
     */
    @Nullable
    private SourceBreakpoint buildSourceBreakpoint(@NotNull XLineBreakpoint<BoxLangBreakpointProperties> bp) {
        if (!bp.isEnabled()) {
            return null;
        }

        XSourcePosition pos = bp.getSourcePosition();
        if (pos == null) {
            return null;
        }

        SourceBreakpoint sourceBreakpoint = new SourceBreakpoint();
        // DAP uses 1-based line numbers, IntelliJ uses 0-based
        sourceBreakpoint.setLine(pos.getLine() + 1);

        // Read condition from IntelliJ's built-in breakpoint condition field.
        // This is what the user edits via the breakpoint popup / "More" dialog.
        XExpression conditionExpr = bp.getConditionExpression();
        if (conditionExpr != null) {
            String condition = conditionExpr.getExpression();
            if (condition != null && !condition.isBlank()) {
                sourceBreakpoint.setCondition(condition);
            }
        }

        // Read log expression from IntelliJ's built-in "Evaluate and log" field.
        // When the breakpoint's suspend policy is NONE, IntelliJ treats it as a
        // "log breakpoint" (tracepoint) — the log expression is what gets logged.
        XExpression logExpr = bp.getLogExpressionObject();
        if (logExpr != null) {
            String logExpression = logExpr.getExpression();
            if (logExpression != null && !logExpression.isBlank()) {
                sourceBreakpoint.setLogMessage(logExpression);
            }
        }

        return sourceBreakpoint;
    }

    /**
     * Collects enabled DAP breakpoints for a file.
     */
    @NotNull
    private List<SourceBreakpoint> collectDapBreakpoints(@Nullable Set<XLineBreakpoint<BoxLangBreakpointProperties>> fileBreakpoints) {
        List<SourceBreakpoint> dapBreakpoints = new ArrayList<>();
        if (fileBreakpoints != null) {
            for (XLineBreakpoint<BoxLangBreakpointProperties> bp : fileBreakpoints) {
                SourceBreakpoint sbp = buildSourceBreakpoint(bp);
                if (sbp != null) {
                    dapBreakpoints.add(sbp);
                }
            }
        }
        return dapBreakpoints;
    }

    /**
     * Collects enabled DAP SourceBreakpoints for a given file path.
     * Used by BoxLangDebugProcess for run-to-cursor to preserve existing breakpoints
     * when adding a temporary one (since DAP setBreakpoints replaces all breakpoints for a file).
     */
    @NotNull
    public List<SourceBreakpoint> collectDapBreakpointsForFile(@NotNull String filePath) {
        return collectDapBreakpoints(breakpointsByFile.get(filePath));
    }

    /**
     * Logs the breakpoints about to be sent.
     */
    private void logBreakpoints(@NotNull String filePath, @NotNull List<SourceBreakpoint> dapBreakpoints) {
        LOG.info("Sending " + dapBreakpoints.size() + " breakpoints for file: " + filePath);
        for (SourceBreakpoint sbp : dapBreakpoints) {
            StringBuilder info = new StringBuilder("  Line " + sbp.getLine());
            if (sbp.getCondition() != null) {
                info.append(" [condition: ").append(sbp.getCondition()).append("]");
            }
            if (sbp.getLogMessage() != null) {
                info.append(" [log: ").append(sbp.getLogMessage()).append("]");
            }
            LOG.info(info.toString());
        }
    }

    /**
     * Sends all breakpoints for a given file to the DAP server.
     */
    private void sendBreakpointsToServer(@NotNull String filePath) {
        BoxLangDapService dapService = debugProcess.getDapService();
        if (dapService == null || !dapService.isConfigurationReady()) {
            LOG.debug("DAP service not ready for configuration, deferring breakpoint sync for: " + filePath);
            return;
        }

        List<SourceBreakpoint> dapBreakpoints = collectDapBreakpoints(breakpointsByFile.get(filePath));
        logBreakpoints(filePath, dapBreakpoints);
        
        dapService.setBreakpoints(filePath, dapBreakpoints)
            .thenAccept(response -> handleSetBreakpointsResponse(filePath, response))
            .exceptionally(ex -> {
                LOG.warn("Failed to set breakpoints for " + filePath, ex);
                return null;
            });
    }

    /**
     * Handles the response from the DAP server after setting breakpoints.
     * Updates breakpoint verification status.
     */
    private void handleSetBreakpointsResponse(@NotNull String filePath, SetBreakpointsResponse response) {
        if (response == null || response.getBreakpoints() == null) {
            LOG.warn("SetBreakpoints response was null for: " + filePath);
            return;
        }

        org.eclipse.lsp4j.debug.Breakpoint[] verifiedBreakpoints = response.getBreakpoints();
        LOG.info("SetBreakpoints response for " + filePath + ": " + verifiedBreakpoints.length + " breakpoints");
        
        Set<XLineBreakpoint<BoxLangBreakpointProperties>> fileBreakpoints = breakpointsByFile.get(filePath);
        
        if (fileBreakpoints == null) {
            return;
        }

        // Match verified breakpoints back to IntelliJ breakpoints by line number
        for (org.eclipse.lsp4j.debug.Breakpoint verified : verifiedBreakpoints) {
            LOG.info("  Breakpoint line " + verified.getLine() + ": verified=" + verified.isVerified() + 
                (verified.getMessage() != null ? ", message=" + verified.getMessage() : ""));
            
            if (verified.getLine() == null) {
                continue;
            }
            
            int verifiedLine = verified.getLine() - 1; // Convert back to 0-based
            
            for (XLineBreakpoint<BoxLangBreakpointProperties> bp : fileBreakpoints) {
                XSourcePosition pos = bp.getSourcePosition();
                if (pos != null && pos.getLine() == verifiedLine) {
                    if (Boolean.TRUE.equals(verified.isVerified())) {
                        debugProcess.getSession().setBreakpointVerified(bp);
                    } else if (configurationComplete) {
                        // Only mark as invalid AFTER configuration is complete.
                        // Before launch, the VM doesn't exist yet so the server will report
                        // verified=false for all breakpoints. That's expected — breakpoints
                        // will be verified later when their classes load in the target VM.
                        String message = verified.getMessage();
                        if (message == null) {
                            message = "Breakpoint could not be verified";
                        }
                        debugProcess.getSession().setBreakpointInvalid(bp, message);
                    } else {
                        LOG.info("  Breakpoint at line " + verified.getLine() + 
                            " not yet verified (pre-launch) - will be verified when class loads");
                    }
                    break;
                }
            }
        }
    }

    /**
     * Re-sends all breakpoints to the server.
     * Called after the debug session is fully initialized.
     * @return A CompletableFuture that completes when all breakpoints have been synced.
     */
    public CompletableFuture<Void> syncAllBreakpoints() {
        LOG.info("Syncing all breakpoints to DAP server (" + breakpointsByFile.size() + " files)");
        BoxLangDapService dapService = debugProcess.getDapService();
        LOG.info("  dapService=" + (dapService != null ? "present" : "null") + 
                 ", isConnected=" + (dapService != null && dapService.isConnected()) +
                 ", isConfigurationReady=" + (dapService != null && dapService.isConfigurationReady()));
        
        if (breakpointsByFile.isEmpty()) {
            LOG.info("No breakpoints to sync");
            return CompletableFuture.completedFuture(null);
        }
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (String filePath : breakpointsByFile.keySet()) {
            Set<XLineBreakpoint<BoxLangBreakpointProperties>> bps = breakpointsByFile.get(filePath);
            LOG.info("  File: " + filePath + " - " + (bps != null ? bps.size() : 0) + " breakpoints");
            CompletableFuture<Void> future = sendBreakpointsToServerAsync(filePath);
            if (future != null) {
                futures.add(future);
            }
        }
        
        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
    
    /**
     * Sends all breakpoints for a given file to the DAP server asynchronously.
     * @return A CompletableFuture that completes when the breakpoints are sent, or null if not sent.
     */
    private CompletableFuture<Void> sendBreakpointsToServerAsync(@NotNull String filePath) {
        BoxLangDapService dapService = debugProcess.getDapService();
        if (dapService == null || !dapService.isConfigurationReady()) {
            LOG.debug("DAP service not ready for configuration, cannot sync breakpoints for: " + filePath);
            return null;
        }

        List<SourceBreakpoint> dapBreakpoints = collectDapBreakpoints(breakpointsByFile.get(filePath));
        logBreakpoints(filePath, dapBreakpoints);
        
        return dapService.setBreakpoints(filePath, dapBreakpoints)
            .thenAccept(response -> handleSetBreakpointsResponse(filePath, response))
            .exceptionally(ex -> {
                LOG.warn("Failed to set breakpoints for " + filePath, ex);
                return null;
            });
    }

    /**
     * Clears all tracked breakpoints.
     */
    public void clear() {
        breakpointsByFile.clear();
    }
    
    /**
     * Marks configuration as complete. After this, breakpoints will be sent immediately
     * when registered/unregistered instead of being deferred.
     */
    public void markConfigurationComplete() {
        LOG.info("Marking configuration complete - future breakpoint changes will be sent immediately");
        configurationComplete = true;
    }
}
