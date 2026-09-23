package com.llqqrr.aircrate.mixin;

import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirCrateMixinPluginTest {
    @Test
    void resourceProbeDoesNotCallLoadClass() {
        ClassLoader loader = new ClassLoader(null) {
            @Override
            public URL getResource(String name) {
                return name.equals("example/Present.class")
                        ? AirCrateMixinPluginTest.class.getResource("AirCrateMixinPluginTest.class")
                        : null;
            }

            @Override
            protected Class<?> loadClass(String name, boolean resolve) {
                throw new AssertionError("resource probe must not load " + name);
            }
        };

        assertTrue(ClassResourceProbe.exists(loader, "example.Present"));
        assertFalse(ClassResourceProbe.exists(loader, "example.Missing"));
    }
}
