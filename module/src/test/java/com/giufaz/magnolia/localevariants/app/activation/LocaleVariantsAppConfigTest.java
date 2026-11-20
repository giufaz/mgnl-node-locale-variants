package com.giufaz.magnolia.localevariants.app.activation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class LocaleVariantsAppConfigTest {

    @Test
    void test_defaults() {
        LocaleVariantsAppConfig config = new LocaleVariantsAppConfig();
        assertTrue(config.isEnabled(), "enabled by default");
        assertEquals(List.of(), config.getNodeTypes());
    }

    @Test
    void test_setNodeTypes_null() {
        LocaleVariantsAppConfig config = new LocaleVariantsAppConfig();
        config.setNodeTypes(null);
        assertEquals(List.of(), config.getNodeTypes());
    }

    // An app is decorated again only when its config changes: equality covers every field
    @Test
    void test_equals() {
        assertEquals(config(true, "lvd:promo"), config(true, "lvd:promo"));
        assertEquals(config(true, "lvd:promo").hashCode(), config(true, "lvd:promo").hashCode());
        assertNotEquals(config(true, "lvd:promo"), config(false, "lvd:promo"), "enabled");
        assertNotEquals(config(true, "lvd:promo"), config(true, "lvd:other"), "nodeTypes");
        assertNotEquals(config(true, "lvd:promo"), null);
    }

    private static LocaleVariantsAppConfig config(boolean enabled, String... nodeTypes) {
        LocaleVariantsAppConfig config = new LocaleVariantsAppConfig();
        config.setEnabled(enabled);
        config.setNodeTypes(List.of(nodeTypes));
        return config;
    }
}
