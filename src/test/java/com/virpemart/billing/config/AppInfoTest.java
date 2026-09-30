package com.virpemart.billing.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/** Checks that Maven filled in app.properties correctly during the build. */
class AppInfoTest {

    @Test
    void nameComesFromPom() {
        assertEquals("Virpe Mart Billing", AppInfo.name());
    }

    @Test
    void versionIsFilledInByTheBuild() {
        String version = AppInfo.version();
        assertNotNull(version);
        assertFalse(version.isBlank(), "version should not be blank");
        assertFalse(version.contains("${"), "version placeholder was not replaced: " + version);
    }
}
