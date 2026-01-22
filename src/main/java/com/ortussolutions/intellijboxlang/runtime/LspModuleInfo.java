package com.ortussolutions.intellijboxlang.runtime;

import java.nio.file.Path;

public class LspModuleInfo {
    public String requestedVersion;
    public Path modulePath;
    public Path boxJsonPath;
    public boolean needsDownload;
}
