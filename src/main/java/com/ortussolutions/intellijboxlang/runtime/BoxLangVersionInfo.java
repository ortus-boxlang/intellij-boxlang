package com.ortussolutions.intellijboxlang.runtime;

import com.vdurmont.semver4j.Semver;
import java.time.Instant;

public record BoxLangVersionInfo(String name, String downloadUrl, Instant modifiedAt, Semver version) {
}
