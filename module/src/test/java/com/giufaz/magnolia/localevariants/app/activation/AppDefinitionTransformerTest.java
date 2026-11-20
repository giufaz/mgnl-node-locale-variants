package com.giufaz.magnolia.localevariants.app.activation;

import static com.giufaz.magnolia.localevariants.app.activation.AppDefinitionTransformer.ADD_ACTION;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.giufaz.magnolia.localevariants.app.create.AddLocaleVariantActionDefinition;

class AppDefinitionTransformerTest {

    private final AppDefinitionTransformer transformer = new AppDefinitionTransformer();

    // Content-types app shape: sections and groups as maps
    @Test
    void test_transform_contentTypesApp() {
        Map<String, Object> app = map("datasource", map("allowedNodeTypes", list("mgnl:folder", "lvd:promo")),
                "subApps", map(
                        "browser", map("workbench", map(), "actions", map("editItem", map()), "actionbar", map("sections", map(
                                "item", section("lvd:promo", map("addActions", map("items", list(map("name", "addItem"))))),
                                "folder", section("mgnl:folder", map("addActions", map("items", list())))))),
                        "detail", map("form", map())));

        Map<String, Object> result = transformer.transform(app, enabled("lvd:promo"));

        assertEquals(list("mgnl:folder", "lvd:promo", "lv:localeVariants"), get(result, "datasource", "allowedNodeTypes"));
        Map<?, ?> action = (Map<?, ?>) get(result, "subApps", "browser", "actions", ADD_ACTION);
        assertEquals(AddLocaleVariantActionDefinition.class.getName(), action.get("class"));
        assertEquals(list("lvd:promo"), get(result, "subApps", "browser", "actions", ADD_ACTION, "availability", "nodeTypes"));
        assertEquals(list(map("name", "addItem"), map("name", ADD_ACTION)),
                get(result, "subApps", "browser", "actionbar", "sections", "item", "groups", "addActions", "items"));
        assertEquals(list(), get(result, "subApps", "browser", "actionbar", "sections", "folder", "groups", "addActions", "items"),
                "folders: no action");
        assertEquals(map("form", map()), get(result, "subApps", "detail"), "detail sub-app untouched");
    }

    @Test
    void test_transform_sourceUnchanged() {
        Map<String, Object> app = map("datasource", map("allowedNodeTypes", list("lvd:promo")),
                "subApps", map("browser", map("workbench", map(), "actionbar", map("sections", map(
                        "item", section("lvd:promo", map("addActions", map("items", list()))))))));
        Map<String, Object> copy = AppDefinitionTransformer.deepCopy(app);

        transformer.transform(app, enabled("lvd:promo"));

        assertEquals(copy, app);
    }

    // Hand-written app shape: sections and groups as lists, allowedNodeTypes as a map
    @Test
    void test_transform_handWrittenMultiTypeApp() {
        List<Object> sections = list(listSection("social", "lvd:ctaSocial"), listSection("link", "lvd:ctaLink"),
                listSection("contact", "lvd:ctaContact"));
        Map<String, Object> app = map("datasource", map("allowedNodeTypes", map("folder", "mgnl:folder", "social", "lvd:ctaSocial")),
                "subApps", map("browser", map("workbench", map(), "actionbar", map("sections", sections))));

        Map<String, Object> result = transformer.transform(app, enabled("lvd:ctaSocial", "lvd:ctaLink"));

        assertEquals("lv:localeVariants", ((Map<?, ?>) get(result, "datasource", "allowedNodeTypes")).get("lv-localeVariants"),
                "map form: added with a derived key");
        List<?> resultSections = (List<?>) get(result, "subApps", "browser", "actionbar", "sections");
        assertTrue(hasAddAction(resultSections.get(0)), "social");
        assertTrue(hasAddAction(resultSections.get(1)), "link");
        assertFalse(hasAddAction(resultSections.get(2)), "contact: not enabled");
    }

    // The browser's own datasource gets the container type too
    @Test
    void test_transform_subAppDatasource() {
        Map<String, Object> app = map("subApps", map("browser", map("workbench", map(),
                "datasource", map("allowedNodeTypes", list("lvd:promo")), "actionbar", map())));

        Map<String, Object> result = transformer.transform(app, enabled("lvd:promo"));

        assertEquals(list("lvd:promo", "lv:localeVariants"), get(result, "subApps", "browser", "datasource", "allowedNodeTypes"));
    }

    // No allowedNodeTypes: every type is already shown
    @Test
    void test_transform_datasourceWithoutAllowedNodeTypes() {
        Map<String, Object> app = map("datasource", map("workspace", "promos"));

        Map<String, Object> result = transformer.transform(app, enabled("lvd:promo"));

        assertEquals(map("workspace", "promos"), result.get("datasource"));
    }

    // Empty: no restriction either, so the container type is not added (the tree
    // would show only the containers)
    @Test
    void test_transform_datasourceWithEmptyAllowedNodeTypes() {
        Map<String, Object> app = map("datasource", map("allowedNodeTypes", list()),
                "subApps", map("browser", map("workbench", map(), "datasource", map("allowedNodeTypes", map()),
                        "actionbar", map())));

        Map<String, Object> result = transformer.transform(app, enabled("lvd:promo"));

        assertEquals(list(), get(result, "datasource", "allowedNodeTypes"), "list form");
        assertEquals(map(), get(result, "subApps", "browser", "datasource", "allowedNodeTypes"), "map form");
    }

    // Pages shape: no section names a node type ("crud" for every page), so the
    // untyped sections get the action, not the ones of deleted items
    @Test
    void test_transform_appWithoutTypedSections() {
        Map<String, Object> notDeleted = map("rules", map("notDeleted", map("$type", "jcrIsDeletedRule", "negate", true)));
        Map<String, Object> deleted = map("rules", map("isDeleted", map("$type", "jcrIsDeletedRule")));
        List<Object> sections = list(
                map("name", "crud", "availability", notDeleted, "groups", list(map("name", "add-delete", "items", list(map("name", "add"))))),
                map("name", "pageDelete", "availability", deleted, "groups", list(map("name", "add-delete", "items", list(map("name", "add"))))));
        Map<String, Object> app = map("subApps", map("browser", map("workbench", map(), "actionbar", map("sections", sections))));

        Map<String, Object> result = transformer.transform(app, enabled("mgnl:page"));

        List<?> resultSections = (List<?>) get(result, "subApps", "browser", "actionbar", "sections");
        assertTrue(hasAddAction(resultSections.get(0)), "crud");
        assertFalse(hasAddAction(resultSections.get(1)), "pageDelete: deleted items");
    }

    // Content-types shape: the item section names the type, so the untyped root
    // section stays without the action
    @Test
    void test_transform_untypedRootSection() {
        Map<String, Object> app = map("subApps", map("browser", map("workbench", map(), "actionbar", map("sections", map(
                "item", section("lvd:promo", map("addActions", map("items", list(map("name", "addItem"))))),
                "root", map("availability", map("root", true), "groups", map("addActions", map("items", list(map("name", "addItem"))))))))));

        Map<String, Object> result = transformer.transform(app, enabled("lvd:promo"));

        assertEquals(list(map("name", "addItem")),
                get(result, "subApps", "browser", "actionbar", "sections", "root", "groups", "addActions", "items"));
    }

    // Pages' page editor: an action bar but no workbench, so not a browser
    @Test
    void test_transform_actionbarWithoutWorkbench() {
        Map<String, Object> detail = map("actions", map("editProperties", map()), "actionbar", map("sections", list(
                map("name", "pageActions", "groups", list(map("name", "g", "items", list(map("name", "editProperties"))))))));
        Map<String, Object> app = map("subApps", map("detail", detail));

        Map<String, Object> result = transformer.transform(app, enabled("mgnl:page"));

        assertEquals(detail, get(result, "subApps", "detail"));
    }

    // An app that already defines addLocaleVariant keeps its own definition
    @Test
    void test_transform_actionAlreadyDefined() {
        Map<String, Object> own = map("class", "my.OwnActionDefinition");
        Map<String, Object> app = map("subApps", map("browser", map("workbench", map(), "actions", map(ADD_ACTION, own),
                "actionbar", map("sections", map("item", section("lvd:promo",
                        map("addActions", map("items", list(map("name", ADD_ACTION))))))))));

        Map<String, Object> result = transformer.transform(app, enabled("lvd:promo"));

        assertEquals(own, get(result, "subApps", "browser", "actions", ADD_ACTION));
        assertEquals(list(map("name", ADD_ACTION)),
                get(result, "subApps", "browser", "actionbar", "sections", "item", "groups", "addActions", "items"), "not twice");
    }

    // Actions as a list: the app's own actions are kept, ours is appended once.
    // Defensive only: Magnolia itself leaves a list-form "actions" null (a map
    // property), so such an app doesn't start anyway
    @Test
    void test_transform_actionsAsList() {
        Map<String, Object> app = map("subApps", map("browser", map("workbench", map(),
                "actions", list(map("name", "editItem", "class", "my.EditActionDefinition")),
                "actionbar", map("sections", list(listSection("item", "lvd:promo"))))));

        Map<String, Object> result = transformer.transform(app, enabled("lvd:promo"));
        Map<String, Object> again = transformer.transform(result, enabled("lvd:promo"));

        List<?> actions = (List<?>) get(result, "subApps", "browser", "actions");
        assertEquals(2, actions.size(), "the app's action and ours");
        assertEquals(map("name", "editItem", "class", "my.EditActionDefinition"), actions.get(0));
        assertEquals(AddLocaleVariantActionDefinition.class.getName(), ((Map<?, ?>) actions.get(1)).get("class"));
        assertEquals(ADD_ACTION, ((Map<?, ?>) actions.get(1)).get("name"));
        assertEquals(2, ((List<?>) get(again, "subApps", "browser", "actions")).size(), "not added twice");
    }

    private static LocaleVariantsAppConfig enabled(String... nodeTypes) {
        LocaleVariantsAppConfig app = new LocaleVariantsAppConfig();
        app.setNodeTypes(List.of(nodeTypes));
        return app;
    }

    private static boolean hasAddAction(Object section) {
        Map<?, ?> group = (Map<?, ?>) ((List<?>) ((Map<?, ?>) section).get("groups")).get(0);
        return AppDefinitionTransformer.namedMaps(group.get("items")).containsKey(ADD_ACTION);
    }

    private static Map<String, Object> section(String nodeType, Map<String, Object> groups) {
        return map("availability", map("nodeTypes", map(nodeType.replace(':', '-'), nodeType)), "groups", groups);
    }

    private static Map<String, Object> listSection(String name, String nodeType) {
        return map("name", name, "availability", map("nodeTypes", list(nodeType)),
                "groups", list(map("name", "editActions", "items", list(map("name", "edit")))));
    }

    // Follows map keys, or names in lists
    private static Object get(Map<String, Object> root, String... path) {
        Object node = root;
        for (String key : path) {
            node = node instanceof Map<?, ?> map ? map.get(key) : AppDefinitionTransformer.namedMaps(node).get(key);
        }
        return node;
    }

    // Mutable and ordered, like the YAML reader's output
    private static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static List<Object> list(Object... values) {
        return new ArrayList<>(List.of(values));
    }
}
