package com.giufaz.magnolia.localevariants.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class LocaleVariantsAppConfigTest {

    @Test
    void test_defaults() {
        LocaleVariantsAppConfig config = new LocaleVariantsAppConfig();
        assertTrue(config.isEnabled(), "enabled by default");
        assertEquals(List.of(), config.getNodeTypes());
        assertEquals(List.of(), config.getVariantActions());
        assertEquals(List.of(), config.getContainerActions());
        assertFalse(config.isReplaceDefaultVariantActions(), "the app's actions are added to the defaults");
        assertFalse(config.isReplaceDefaultContainerActions(), "the app's actions are added to the defaults");
    }

    // "nodeTypes:" with no value in YAML
    @Test
    void test_setLists_null() {
        LocaleVariantsAppConfig config = new LocaleVariantsAppConfig();
        config.setNodeTypes(null);
        config.setVariantActions(null);
        config.setContainerActions(null);
        assertEquals(List.of(), config.getNodeTypes());
        assertEquals(List.of(), config.getVariantActions());
        assertEquals(List.of(), config.getContainerActions());
    }

    @Test
    void test_actionDefaults_null() {
        ActionDefaults defaults = new ActionDefaults();
        assertEquals(List.of(), defaults.getVariantActions());
        assertEquals(List.of(), defaults.getContainerActions());
        defaults.setVariantActions(null);
        defaults.setContainerActions(null);
        assertEquals(List.of(), defaults.getVariantActions());
        assertEquals(List.of(), defaults.getContainerActions());
    }
}
