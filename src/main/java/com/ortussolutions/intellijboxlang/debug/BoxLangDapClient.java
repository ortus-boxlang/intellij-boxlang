package com.ortussolutions.intellijboxlang.debug;

import org.eclipse.lsp4j.debug.BreakpointEventArguments;
import org.eclipse.lsp4j.debug.CapabilitiesEventArguments;
import org.eclipse.lsp4j.debug.ContinuedEventArguments;
import org.eclipse.lsp4j.debug.ExitedEventArguments;
import org.eclipse.lsp4j.debug.OutputEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.TerminatedEventArguments;
import org.eclipse.lsp4j.debug.ThreadEventArguments;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;

/**
 * Receives DAP events from the debug adapter and forwards relevant events to {@link BoxLangDapService}.
 */
public class BoxLangDapClient implements IDebugProtocolClient {
    private final BoxLangDapService service;

    public BoxLangDapClient(BoxLangDapService service) {
        this.service = service;
    }

    @Override
    public void initialized() {
        service.handleInitialized();
    }

    @Override
    public void stopped(StoppedEventArguments args) {
        service.handleStopped(args);
    }

    @Override
    public void continued(ContinuedEventArguments args) {
        service.handleContinued(args);
    }

    @Override
    public void exited(ExitedEventArguments args) {
        service.handleExited(args);
    }

    @Override
    public void terminated(TerminatedEventArguments args) {
        service.handleTerminated(args);
    }

    @Override
    public void thread(ThreadEventArguments args) {
        service.handleThread(args);
    }

    @Override
    public void output(OutputEventArguments args) {
        service.handleOutput(args);
    }

    @Override
    public void breakpoint(BreakpointEventArguments args) {
        service.handleBreakpoint(args);
    }

    @Override
    public void capabilities(CapabilitiesEventArguments args) {
        service.handleCapabilities(args);
    }
}
