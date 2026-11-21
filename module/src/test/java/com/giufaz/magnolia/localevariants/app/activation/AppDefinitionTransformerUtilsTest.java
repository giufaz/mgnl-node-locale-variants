package com.giufaz.magnolia.localevariants.app.activation;

import static com.giufaz.magnolia.localevariants.app.activation.AppDefinitionTransformerUtils.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AppDefinitionTransformerUtilsTest {

    // --- Normalized copy ---

    // [{name: a, ...}] → {a: {name: a, ...}}, in the same order, at every level
    @Test
    void test_normalizedCopy_namedCollectionsAsLists() {
        Map<String, Object> actionbar = map("sections", list(
                map("name", "item", "groups", list(
                        map("name", "addActions", "items", list(map("name", "addItem"), map("name", "editItem"))),
                        map("name", "editActions"))),
                map("name", "root")));

        Map<String, Object> copy = normalizedCopy(actionbar);

        assertEquals(map("sections", map(
                "item", map("name", "item", "groups", map(
                        "addActions", map("name", "addActions", "items", map(
                                "addItem", map("name", "addItem"), "editItem", map("name", "editItem"))),
                        "editActions", map("name", "editActions"))),
                "root", map("name", "root"))), copy);
        assertEquals(List.of("item", "root"), List.copyOf(sectionsOf(copy).keySet()), "order kept");
    }

    // Already maps: kept, and what is inside them is normalized too
    @Test
    void test_normalizedCopy_namedCollectionsAsMaps() {
        Map<String, Object> actionbar = map("sections", map("item", map("groups", list(map("name", "addActions")))));

        assertEquals(map("sections", map("item", map("groups", map("addActions", map("name", "addActions"))))),
                normalizedCopy(actionbar));
    }

    @Test
    void test_normalizedCopy_everyNamedCollection() {
        for (String key : List.of("subApps", "actions", "sections", "groups", "items", "rules", "contentViews")) {
            assertEquals(map(key, map("a", map("name", "a"))), normalizedCopy(map(key, list(map("name", "a")))), key);
        }
    }

    // Rule definitions have no name: the key is the position, the rules are all
    // kept in order
    @Test
    void test_normalizedCopy_itemsWithoutName() {
        Map<String, Object> availability = map("rules", list(
                map("$type", "jcrIsDeletedRule", "negate", true), map("name", "own", "class", "my.Rule"), map("class", "my.OtherRule")));

        Map<String, Object> copy = normalizedCopy(availability);

        assertEquals(map("rules", map(
                "_0", map("$type", "jcrIsDeletedRule", "negate", true),
                "own", map("name", "own", "class", "my.Rule"),
                "_2", map("class", "my.OtherRule"))), copy);
        assertEquals(List.of("_0", "own", "_2"), List.copyOf(rulesOf(copy).keySet()));
    }

    // A list, the values of a map, or "a,b": always a list
    @Test
    void test_normalizedCopy_stringCollections() {
        assertEquals(map("nodeTypes", list("a", "b")), normalizedCopy(map("nodeTypes", list("a", "b"))), "list");
        assertEquals(map("nodeTypes", list("a", "b")), normalizedCopy(map("nodeTypes", map("x", "a", "y", "b"))), "map");
        assertEquals(map("nodeTypes", list("a", "b")), normalizedCopy(map("nodeTypes", "a,b")), "comma-separated");
        assertEquals(map("allowedNodeTypes", list("a")), normalizedCopy(map("allowedNodeTypes", map("x", "a"))));
        assertEquals(map("nodeTypes", list()), normalizedCopy(map("nodeTypes", null)), "\"nodeTypes:\" with no value");
    }

    // Always a list that can be changed: the transformer adds the container type
    @Test
    void test_normalizedCopy_stringCollectionsAreMutable() {
        for (Object value : new Object[] { List.of("a"), Map.of("x", "a"), "a", list() }) {
            Map<String, Object> copy = normalizedCopy(map("nodeTypes", value));

            nodeTypesOf(copy).add("b");

            assertTrue(nodeTypesOf(copy).contains("b"), String.valueOf(value));
        }
    }

    // Other lists and values stay as they are
    @Test
    void test_normalizedCopy_otherValues() {
        Map<String, Object> app = map("label", "Promos", "columns", list(map("name", "title"), map("name", "status")),
                "permissions", map("roles", list("editor")), "enabled", true, "icon", null);

        assertEquals(app, normalizedCopy(app));
    }

    // The key tells the kind of collection, wherever it is; at the root there is
    // no key
    @Test
    void test_normalizedCopy_rootIsNeverACollection() {
        assertEquals(list(map("name", "a")), normalizedCopy(list(map("name", "a"))));
    }

    // Deep: changing the copy never changes the source, at any level (YAML
    // anchors make the source share instances)
    @Test
    @SuppressWarnings("unchecked")
    void test_normalizedCopy_isDeep() {
        Map<String, Object> shared = map("nodeTypes", list("lvd:promo"));
        Map<String, Object> app = map("subApps", map("browser", map(
                "actions", map("editItem", map("availability", shared), "export", map("availability", shared)),
                "columns", list(map("name", "title")))));
        // A snapshot that shares nothing with the source (a copy could share what it failed to copy)
        String before = app.toString();

        Map<String, Object> copy = normalizedCopy(app);
        Map<String, Object> browser = subAppsOf(copy).get("browser");
        nodeTypesOf(availabilityOf(actionsOf(browser).get("editItem"))).add("lv:localeVariants");
        actionsOf(browser).put("added", map());
        List<Map<String, Object>> columns = (List<Map<String, Object>>) browser.get("columns");
        columns.get(0).put("width", 100); // an item of a list that is not a collection of named items
        columns.add(map("name", "added"));

        assertEquals(before, app.toString(), "source unchanged");
        assertEquals(list("lvd:promo"), nodeTypesOf(availabilityOf(actionsOf(browser).get("export"))),
                "the copy doesn't share instances either");
    }

    // --- The parts of a normalized app definition ---

    // The definition's own part, so changing it changes the definition
    @Test
    void test_accessors_existingParts() {
        Map<String, Object> browser = map("actions", map("editItem", map("availability", map("rules", map(), "nodeTypes", list()))));

        Map<String, Object> availability = availabilityOf(actionsOf(browser).get("editItem"));
        rulesOf(availability).put("own", map("class", "my.Rule"));
        nodeTypesOf(availability).add("lvd:promo");

        assertEquals(map("actions", map("editItem", map("availability",
                map("rules", map("own", map("class", "my.Rule")), "nodeTypes", list("lvd:promo"))))), browser);
    }

    // A missing part is added empty
    @Test
    void test_accessors_missingParts() {
        Map<String, Object> app = map();
        subAppsOf(app);
        assertEquals(map("subApps", map()), app);

        Map<String, Object> subApp = map();
        actionsOf(subApp);
        actionbarOf(subApp);
        workbenchOf(subApp);
        assertEquals(map("actions", map(), "actionbar", map(), "workbench", map()), subApp);

        Map<String, Object> actionbar = map();
        sectionsOf(actionbar);
        assertEquals(map("sections", map()), actionbar);

        Map<String, Object> section = map();
        groupsOf(section);
        nodeTypesOf(availabilityOf(section));
        rulesOf(availabilityOf(section));
        assertEquals(map("groups", map(), "availability", map("nodeTypes", list(), "rules", map())), section);

        Map<String, Object> group = map();
        itemsOf(group);
        assertEquals(map("items", map()), group);

        Map<String, Object> workbench = map();
        contentViewsOf(workbench);
        assertEquals(map("contentViews", map()), workbench);

        Map<String, Object> datasource = map();
        allowedNodeTypesOf(datasource);
        assertEquals(map("allowedNodeTypes", list()), datasource);
    }

    // "actions:" with no value in YAML: like a missing part
    @Test
    void test_accessors_partsWithoutValue() {
        Map<String, Object> subApp = map("actions", null);

        actionsOf(subApp).put("editItem", map());

        assertEquals(map("actions", map("editItem", map())), subApp);
    }

    // A sub-app without a datasource uses the app's: it must stay without
    @Test
    void test_datasourceOf_missing() {
        Map<String, Object> subApp = map("workbench", map());

        Map<String, Object> datasource = datasourceOf(subApp);
        allowedNodeTypesOf(datasource).add("lv:localeVariants");

        assertEquals(map("workbench", map()), subApp);
    }

    @Test
    void test_datasourceOf_existing() {
        Map<String, Object> subApp = map("datasource", map("workspace", "promos"));

        datasourceOf(subApp).put("allowedNodeTypes", list("lvd:promo"));

        assertEquals(map("datasource", map("workspace", "promos", "allowedNodeTypes", list("lvd:promo"))), subApp);
    }

    // Mutable and ordered, like the YAML reader's output
    static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    static List<Object> list(Object... values) {
        return new ArrayList<>(List.of(values));
    }
}
