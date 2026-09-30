package com.virpemart.billing.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Application name and version.
 *
 * <p>The values come from {@code app.properties}, which Maven fills in at build time from
 * {@code pom.xml}. This keeps the version in exactly one place.
 */
public final class AppInfo {

    private static final String RESOURCE = "/app.properties";
    private static final Properties PROPERTIES = load();

    private AppInfo() {
    }

    /** Human-readable application name, for example "Virpe Mart Billing". */
    public static String name() {
        return PROPERTIES.getProperty("app.name");
    }

    /** Application version from pom.xml, for example "0.1.0". */
    public static String version() {
        return PROPERTIES.getProperty("app.version");
    }

    private static Properties load() {
        try (InputStream in = AppInfo.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource: " + RESOURCE);
            }
            Properties properties = new Properties();
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + RESOURCE, e);
        }
    }
}
