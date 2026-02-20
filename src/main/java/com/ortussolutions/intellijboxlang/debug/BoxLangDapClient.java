package com.ortussolutions.intellijboxlang.debug;

import com.intellij.openapi.diagnostic.Logger;
import org.eclipse.lsp4j.debug.*;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;

/**
 * DAP client implementation that receives events and callbacks from the DAP server.
 * This handles events like stopped (breakpoint hit), output (console), terminated, etc.
 */
public class BoxLangDapClient implements IDebugProtocolClient {
    private static final Logger LOG = Logger.getInstance(BoxLangDapClient.class);
    
    private final BoxLangDapService service;

    public BoxLangDapClient(BoxLangDapService service) {
        this.service = service;
    }

    @Override
    public void initialized() {
        LOG.info("DAP: initialized event received - server is ready for configuration");
        service.handleInitialized();
    }

    @Override
    public void stopped(StoppedEventArguments args) {
        LOG.info("DAP: stopped event - reason: " + args.getReason() + ", threadId: " + args.getThreadId());
        service.handleStopped(args);
    }

    @Override
    public void continued(ContinuedEventArguments args) {
        LOG.debug("DAP: continued event - threadId: " + args.getThreadId());
        service.handleContinued(args);
    }

    @Override
    public void exited(ExitedEventArguments args) {
        LOG.info("DAP: exited event - exitCode: " + args.getExitCode());
        service.handleExited(args);
    }

    @Override
    public void terminated(TerminatedEventArguments args) {
        LOG.info("DAP: terminated event");
        service.handleTerminated(args);
    }

    @Override
    public void thread(ThreadEventArguments args) {
        LOG.debug("DAP: thread event - reason: " + args.getReason() + ", threadId: " + args.getThreadId());
        service.handleThread(args);
    }

    @Override
    public void output(OutputEventArguments args) {
        LOG.debug("DAP: output event - category: " + args.getCategory());
        service.handleOutput(args);
    }

    @Override
    public void breakpoint(BreakpointEventArguments args) {
        LOG.debug("DAP: breakpoint event - reason: " + args.getReason());
        service.handleBreakpoint(args);
    }

    @Override
    public void module(ModuleEventArguments args) {
        LOG.debug("DAP: module event - reason: " + args.getReason());
    }

    @Override
    public void loadedSource(LoadedSourceEventArguments args) {
        LOG.debug("DAP: loadedSource event");
    }

    @Override
    public void process(ProcessEventArguments args) {
        LOG.info("DAP: process event - name: " + args.getName());
    }

    @Override
    public void capabilities(CapabilitiesEventArguments args) {
        LOG.debug("DAP: capabilities event");
        service.handleCapabilities(args);
    }

    @Override
    public void progressStart(ProgressStartEventArguments args) {
        LOG.debug("DAP: progressStart event");
    }

    @Override
    public void progressUpdate(ProgressUpdateEventArguments args) {
        LOG.debug("DAP: progressUpdate event");
    }

    @Override
    public void progressEnd(ProgressEndEventArguments args) {
        LOG.debug("DAP: progressEnd event");
    }

    @Override
    public void invalidated(InvalidatedEventArguments args) {
        LOG.debug("DAP: invalidated event");
    }

    @Override
    public void memory(MemoryEventArguments args) {
        LOG.debug("DAP: memory event");
    }
}
