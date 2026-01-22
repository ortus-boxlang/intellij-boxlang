package com.ortussolutions.intellijboxlang.runtime;

import com.intellij.openapi.progress.ProgressIndicator;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BoxLangDownloadService {
    private static final int BUFFER_SIZE = 8192;

    private BoxLangDownloadService() {
    }

    public static void downloadTo(URL url, Path target, ProgressIndicator indicator) throws IOException {
        Files.createDirectories(target.getParent());

        try (InputStream input = url.openStream();
             OutputStream output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
                total += read;
                if (indicator != null) {
                    indicator.setText2(String.format("Downloaded %,d bytes", total));
                }
            }
        }
    }
}
