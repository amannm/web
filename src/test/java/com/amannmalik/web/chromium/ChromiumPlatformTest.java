package com.amannmalik.web.chromium.test;

import org.junit.jupiter.api.Test;

import com.amannmalik.web.chromium.ChromiumPlatform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ChromiumPlatformTest {

    @Test
    void selectsArmBuildOnAppleSilicon() {
        var platform = ChromiumPlatform.from("Mac OS X", "aarch64");
        assertEquals(ChromiumPlatform.MAC_ARM, platform);
    }

    @Test
    void fallsBackToIntelOnMacWhenArchitectureUnknown() {
        var platform = ChromiumPlatform.from("Darwin", "x86_64");
        assertEquals(ChromiumPlatform.MAC_INTEL, platform);
    }

    @Test
    void selectsLinuxSnapshot() {
        var platform = ChromiumPlatform.from("Linux", "amd64");
        assertEquals(ChromiumPlatform.LINUX_X64, platform);
    }

    @Test
    void rejectsUnknownPlatform() {
        assertThrows(IllegalArgumentException.class, () -> ChromiumPlatform.from("Solaris", "sparc"));
    }
}
