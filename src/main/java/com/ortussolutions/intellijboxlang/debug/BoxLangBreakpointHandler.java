package com.ortussolutions.intellijboxlang.debug;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import org.eclipse.lsp4j.debug.SetBreakpointsResponse;
import org.eclipse.lsp4j.debug.SourceBreakpoint;
import org.jetbrains.annotations.NotNull;

import java.util.*;
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
        
        breakpointsByFile.computeIfAbsent(filePath, k -> ConcurrentHashMap.newKeySet()).add(breakpoint);
        
        sendBreakpointsToServer(filePath);
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
     * Sends all breakpoints for a given file to the DAP server.
     */
    private void sendBreakpointsToServer(@NotNull String filePath) {
        BoxLangDapService dapService = debugProcess.getDapService();
        if (dapService == null || !dapService.isConnected()) {
            LOG.debug("DAP service not connected, deferring breakpoint sync");
            return;
        }

        Set<XLineBreakpoint<BoxLangBreakpointProperties>> fileBreakpoints = breakpointsByFile.get(filePath);
        List<SourceBreakpoint> dapBreakpoints = new ArrayList<>();

        if (fileBreakpoints != null) {
            for (XLineBreakpoint<BoxLangBreakpointProperties> bp : fileBreakpoints) {
                if (!bp.isEnabled()) {
                    continue;
                }
                
                XSourcePosition pos = bp.getSourcePosition();
                if (pos == null) {
                    continue;
                }

                SourceBreakpoint sourceBreakpoint = new SourceBreakpoint();
                // DAP uses 1-based line numbers, IntelliJ uses 0-based
                sourceBreakpoint.setLine(pos.getLine() + 1);
                
                // Handle conditional breakpoints
                BoxLangBreakpointProperties props = bp.getProperties();
                if (props != null) {
                    String condition = props.getCondition();
                    if (condition != null && !condition.isBlank()) {
                        sourceBreakpoint.setCondition(condition);
                    }
                    
                    int hitCount = props.getHitCount();
                    if (hitCount > 0) {
                        sourceBreakpoint.setHitCondition(String.valueOf(hitCount));
                    }
                    
                    String logExpression = props.getLogExpression();
                    if (logExpression != null && !logExpression.isBlank()) {
                        sourceBreakpoint.setLogMessage(logExpression);
                    }
                }
                
                dapBreakpoints.add(sourceBreakpoint);
            }
        }

        LOG.debug("Sending " + dapBreakpoints.size() + " breakpoints for file: " + filePath);
        
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
            return;
        }

        org.eclipse.lsp4j.debug.Breakpoint[] verifiedBreakpoints = response.getBreakpoints();
        Set<XLineBreakpoint<BoxLangBreakpointProperties>> fileBreakpoints = breakpointsByFile.get(filePath);
        
        if (fileBreakpoints == null) {
            return;
        }

        // Match verified breakpoints back to IntelliJ breakpoints by line number
        for (org.eclipse.lsp4j.debug.Breakpoint verified : verifiedBreakpoints) {
            if (verified.getLine() == null) {
                continue;
            }
            
            int verifiedLine = verified.getLine() - 1; // Convert back to 0-based
            
            for (XLineBreakpoint<BoxLangBreakpointProperties> bp : fileBreakpoints) {
                XSourcePosition pos = bp.getSourcePosition();
                if (pos != null && pos.getLine() == verifiedLine) {
                    if (Boolean.TRUE.equals(verified.isVerified())) {
                        debugProcess.getSession().setBreakpointVerified(bp);
                    } else {
                        String message = verified.getMessage();
                        if (message == null) {
                            message = "Breakpoint could not be verified";
                        }
                        debugProcess.getSession().setBreakpointInvalid(bp, message);
                    }
                    break;
                }
            }
        }
    }

    /**
     * Re-sends all breakpoints to the server.
     * Called after the debug session is fully initialized.
     */
    public void syncAllBreakpoints() {
        for (String filePath : breakpointsByFile.keySet()) {
            sendBreakpointsToServer(filePath);
        }
    }

    /**
     * Clears all tracked breakpoints.
     */
    public void clear() {
        breakpointsByFile.clear();
    }
}
