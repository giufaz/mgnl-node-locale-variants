package com.giufaz.magnolia.localevariants.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ResolvedLocaleVariantsAppConfigTest {

    // The app's own actions are added to the defaults: defaults first, no duplicates
    @Test
    void test_of_addsToDefaults() {
        LocaleVariantsAppConfig config = config("lvd:promo");
        config.setVariantActions(List.of("editPromo", "export"));
        config.setContainerActions(List.of("exportPromo"));

        ResolvedLocaleVariantsAppConfig app = ResolvedLocaleVariantsAppConfig.of("promos", config,
                defaults(List.of("editItem", "export"), List.of("activate", "export")));

        assertEquals("promos", app.name());
        assertEquals(List.of("lvd:promo"), app.nodeTypes());
        assertEquals(List.of("editItem", "export", "editPromo"), app.variantActions());
        assertEquals(List.of("activate", "export", "exportPromo"), app.containerActions());
    }

    // Each list is replaced on its own
    @Test
    void test_of_replacesDefaults() {
        LocaleVariantsAppConfig config = config("lvd:promo");
        config.setVariantActions(List.of("editPromo"));
        config.setReplaceDefaultVariantActions(true);
        config.setContainerActions(List.of("exportPromo"));

        ResolvedLocaleVariantsAppConfig app = ResolvedLocaleVariantsAppConfig.of("promos", config,
                defaults(List.of("editItem"), List.of("activate")));

        assertEquals(List.of("editPromo"), app.variantActions(), "replaced");
        assertEquals(List.of("activate", "exportPromo"), app.containerActions(), "added");
    }

    // Replacing with an empty list: nothing is allowed
    @Test
    void test_of_replacesDefaultsWithNothing() {
        LocaleVariantsAppConfig config = config("lvd:cta");
        config.setReplaceDefaultContainerActions(true);

        ResolvedLocaleVariantsAppConfig app = ResolvedLocaleVariantsAppConfig.of("ctas", config,
                defaults(List.of("editItem"), List.of("activate")));

        assertEquals(List.of("editItem"), app.variantActions());
        assertEquals(List.of(), app.containerActions());
    }

    // From the defaults: on unless the config switches it off
    @Test
    void test_of_recursiveRestore() {
        ActionDefaults defaults = new ActionDefaults();
        assertTrue(ResolvedLocaleVariantsAppConfig.of("promos", config("lvd:promo"), defaults).recursiveRestore());

        defaults.setRecursiveRestore(false);
        assertFalse(ResolvedLocaleVariantsAppConfig.of("promos", config("lvd:promo"), defaults).recursiveRestore());
    }

    @Test
    void test_allows() {
        ResolvedLocaleVariantsAppConfig app = new ResolvedLocaleVariantsAppConfig("promos", List.of("lvd:promo"),
                List.of("editItem"), List.of("activate"), true);

        assertTrue(app.allowsOnVariant("editItem"));
        assertFalse(app.allowsOnVariant("activate"), "allowed on containers only");
        assertTrue(app.allowsOnContainer("activate"));
        assertFalse(app.allowsOnContainer("editItem"), "allowed on variants only");
    }

    // A later change of the module config beans doesn't reach an applied app
    @Test
    void test_immutable() {
        List<String> nodeTypes = new ArrayList<>(List.of("lvd:promo"));
        List<String> variantActions = new ArrayList<>(List.of("editItem"));
        List<String> containerActions = new ArrayList<>(List.of("activate"));
        ResolvedLocaleVariantsAppConfig app = new ResolvedLocaleVariantsAppConfig("promos", nodeTypes, variantActions,
                containerActions, true);

        nodeTypes.add("lvd:other");
        variantActions.add("duplicate");
        containerActions.add("duplicate");

        assertEquals(List.of("lvd:promo"), app.nodeTypes());
        assertEquals(List.of("editItem"), app.variantActions());
        assertEquals(List.of("activate"), app.containerActions());
        assertThrows(UnsupportedOperationException.class, () -> app.variantActions().add("duplicate"));
    }

    // An app is decorated again only when its resolved config changes: equality
    // covers every part
    @Test
    void test_equals() {
        assertEquals(app("promos", "lvd:promo", "editItem", "activate"), app("promos", "lvd:promo", "editItem", "activate"));
        assertNotEquals(app("promos", "lvd:promo", "editItem", "activate"), app("other", "lvd:promo", "editItem", "activate"));
        assertNotEquals(app("promos", "lvd:promo", "editItem", "activate"), app("promos", "lvd:other", "editItem", "activate"));
        assertNotEquals(app("promos", "lvd:promo", "editItem", "activate"), app("promos", "lvd:promo", "export", "activate"));
        assertNotEquals(app("promos", "lvd:promo", "editItem", "activate"), app("promos", "lvd:promo", "editItem", "export"));
    }

    private static ResolvedLocaleVariantsAppConfig app(String name, String nodeType, String variantAction,
            String containerAction) {
        return new ResolvedLocaleVariantsAppConfig(name, List.of(nodeType), List.of(variantAction), List.of(containerAction), true);
    }

    private static LocaleVariantsAppConfig config(String... nodeTypes) {
        LocaleVariantsAppConfig config = new LocaleVariantsAppConfig();
        config.setNodeTypes(List.of(nodeTypes));
        return config;
    }

    private static ActionDefaults defaults(List<String> variantActions, List<String> containerActions) {
        ActionDefaults defaults = new ActionDefaults();
        defaults.setVariantActions(variantActions);
        defaults.setContainerActions(containerActions);
        return defaults;
    }
}
