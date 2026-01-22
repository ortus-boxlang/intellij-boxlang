package com.ortussolutions.intellijboxlang.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;

public final class ForgeBoxLspResolver {
    private static final String ENDPOINT = "https://forgebox.io/api/v1/entry/bx-lsp";
    private ForgeBoxLspResolver() {
    }

    public static ForgeBoxLspDescriptor resolve(String requestedVersion) throws IOException {
        String payload = fetchPayload();
        String normalized = normalizeVersion(requestedVersion);
        JsonObject data = parseData(payload);
        if (normalized == null) {
            return resolveLatest(data);
        }
        ForgeBoxLspDescriptor matched = resolveVersion(data, normalized);
        if (matched != null) {
            matched.version = normalized;
            return matched;
        }
        throw new IOException("Unable to resolve bx-lsp download for version: " + normalized);
    }

    private static String normalizeVersion(String requestedVersion) {
        if (requestedVersion == null || requestedVersion.isBlank()) {
            return null;
        }
        String normalized = requestedVersion.trim();
        int atIndex = normalized.indexOf('@');
        if (atIndex >= 0) {
            normalized = normalized.substring(atIndex + 1);
        }
        return normalized.isBlank() ? null : normalized;
    }

    private static String fetchPayload() throws IOException {
        try (InputStream input = new URL(ENDPOINT).openStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ForgeBoxLspDescriptor resolveLatest(JsonObject data) throws IOException {
        JsonObject latest = getObject(data, "latestVersion");
        String downloadUrl = getString(latest, "downloadURL");
        if (downloadUrl == null) {
            throw new IOException("Unable to locate latest bx-lsp download URL.");
        }
        ForgeBoxLspDescriptor descriptor = new ForgeBoxLspDescriptor();
        descriptor.downloadUrl = downloadUrl;
        descriptor.version = getString(latest, "version");
        return descriptor;
    }

    private static ForgeBoxLspDescriptor resolveVersion(JsonObject data, String version) {
        if (!data.has("versions") || !data.get("versions").isJsonArray()) {
            return null;
        }
        for (JsonElement element : data.getAsJsonArray("versions")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            String entryVersion = getString(entry, "version");
            if (version.equals(entryVersion)) {
                ForgeBoxLspDescriptor descriptor = new ForgeBoxLspDescriptor();
                descriptor.version = entryVersion;
                descriptor.downloadUrl = getString(entry, "downloadURL");
                return descriptor;
            }
        }
        return null;
    }

    private static JsonObject parseData(String payload) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(payload).getAsJsonObject();
            JsonObject data = getObject(root, "data");
            if (data == null) {
                throw new IOException("ForgeBox response missing data payload.");
            }
            return data;
        } catch (Exception e) {
            throw new IOException("Unable to parse ForgeBox response.", e);
        }
    }

    private static JsonObject getObject(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonObject()) {
            return null;
        }
        return parent.getAsJsonObject(key);
    }

    private static String getString(JsonObject parent, String key) {
        if (parent == null || !parent.has(key)) {
            return null;
        }
        JsonElement value = parent.get(key);
        return value.isJsonPrimitive() ? value.getAsString() : null;
    }
}
