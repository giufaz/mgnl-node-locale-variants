package com.giufaz.magnolia.localevariants.app.activation;

import static com.giufaz.magnolia.localevariants.app.activation.AppDefinitionTransformer.ADD_ACTION;
import static com.giufaz.magnolia.localevariants.app.activation.AppDefinitionTransformer.CONTAINER_GROUP;
import static com.giufaz.magnolia.localevariants.app.activation.AppDefinitionTransformer.CONTAINER_SECTION;
import static com.giufaz.magnolia.localevariants.app.activation.AppDefinitionTransformerUtils.normalizedCopy;
import static com.giufaz.magnolia.localevariants.app.activation.AppDefinitionTransformerUtilsTest.list;
import static com.giufaz.magnolia.localevariants.app.activation.AppDefinitionTransformerUtilsTest.map;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.giufaz.magnolia.localevariants.app.create.AddLocaleVariantActionDefinition;
import com.giufaz.magnolia.localevariants.app.guard.LocaleVariantDropConstraintDefinition;
import com.giufaz.magnolia.localevariants.app.guard.LocaleVariantRuleDefinition;
import com.giufaz.magnolia.localevariants.config.ResolvedLocaleVariantsAppConfig;

class AppDefinitionTransformerTest {

    private static final String RULE = LocaleVariantRuleDefinition.class.getName();
    private static final Map<String, Object> NOT_ON_VARIANTS = map("class", RULE, "target", "variant", "negate", true);
    private static final Map<String, Object> NOT_ON_CONTAINERS = map("class", RULE, "target", "container", "negate", true);

    private final AppDefinitionTransformer transformer = new AppDefinitionTransformer();

    // --- Variants and containers show in the tree ---

    @Test
    void test_transform_allowedNodeTypes() {
        Map<String, Object> app = map("datasource", map("allowedNodeTypes", list("mgnl:folder", "lvd:promo")),
                "subApps", map("browser", browser("datasource", map("allowedNodeTypes", map("promo", "lvd:promo")))));

        Map<String, Object> result = transformer.transform(app, app("lvd:promo"));

        assertEquals(list("mgnl:folder", "lvd:promo", "lv:localeVariants"), get(result, "datasource", "allowedNodeTypes"), "app");
        assertEquals(list("lvd:promo", "lv:localeVariants"), get(result, "subApps", "browser", "datasource", "allowedNodeTypes"),
                "the browser's own datasource, written as a map");
    }

    @Test
    void test_transform_allowedNodeTypesWithContainerType() {
        Map<String, Object> app = map("datasource", map("allowedNodeTypes", list("lv:localeVariants", "lvd:promo")));

        Map<String, Object> result = transformer.transform(app, app("lvd:promo"));

        assertEquals(list("lv:localeVariants", "lvd:promo"), get(result, "datasource", "allowedNodeTypes"), "not twice");
    }

    // Missing or empty: no restriction, every type is already shown. Adding the
    // container type would make the tree show only the containers
    @Test
    void test_transform_noAllowedNodeTypes() {
        Map<String, Object> app = map("datasource", map("workspace", "promos"),
                "subApps", map("browser", browser("datasource", map("allowedNodeTypes", list()))));

        Map<String, Object> result = transformer.transform(app, app("lvd:promo"));

        assertFalse(result.toString().contains("lv:localeVariants"), "neither missing nor empty: " + result);
        assertEquals("promos", get(result, "datasource", "workspace"));
    }

    // A browser without a datasource uses the app's: it must stay without
    @Test
    void test_transform_browserWithoutDatasource() {
        Map<String, Object> app = map("subApps", map("browser", browser()));

        Map<String, Object> result = transformer.transform(app, app("lvd:promo"));

        assertNull(get(result, "subApps", "browser", "datasource"));
        assertNull(result.get("datasource"), "nor the app");
    }

    // --- The addLocaleVariant action ---

    @Test
    void test_transform_addActionDefinition() {
        Map<String, Object> app = map("subApps", map("browser", browser()));

        Map<String, Object> result = transformer.transform(app, app("lvd:ctaSocial", "lvd:ctaLink"));

        Map<?, ?> action = (Map<?, ?>) get(result, "subApps", "browser", "actions", ADD_ACTION);
        assertEquals(AddLocaleVariantActionDefinition.class.getName(), action.get("class"));
        assertEquals(List.of("class", "availability"), List.copyOf(action.keySet()),
                "label, icon and dialog are in the definition class");
        assertEquals(true, get(result, "subApps", "browser", "actions", ADD_ACTION, "availability", "writePermissionRequired"));
        assertEquals(list("lvd:ctaSocial", "lvd:ctaLink"),
                get(result, "subApps", "browser", "actions", ADD_ACTION, "availability", "nodeTypes"));
        assertEquals(map("$type", "jcrIsDeletedRule", "negate", true),
                get(result, "subApps", "browser", "actions", ADD_ACTION, "availability", "rules", "notDeleted"));
    }

    // Content-types app shape: the button goes in the "addActions" group of the
    // sections of the configured types
    @Test
    void test_transform_addButtonInTypedSections() {
        Map<String, Object> app = map("subApps", map("browser", browser("actionbar", map("sections", map(
                "root", map("availability", map("root", true), "groups", map("addActions", map("items", list(map("name", "addItem"))))),
                "folder", section("mgnl:folder", "addActions", "addFolder"),
                "item", map("availability", map("nodeTypes", map("promo", "lvd:promo")), "groups", map(
                        "editActions", map("items", list(map("name", "editItem"))),
                        "addActions", map("items", list(map("name", "addItem"))))))))));

        Map<String, Object> result = transformer.transform(app, app("lvd:promo"));

        assertEquals(List.of("addItem", ADD_ACTION), itemNames(result, "item", "addActions"), "after the app's own buttons");
        assertEquals(List.of("editItem"), itemNames(result, "item", "editActions"));
        assertEquals(List.of("addFolder"), itemNames(result, "folder", "addActions"), "another type");
        assertEquals(List.of("addItem"), itemNames(result, "root", "addActions"),
                "untyped, but the item section names the type");
    }

    // Hand-written app shape: sections, groups and items as lists, no
    // "addActions" group, so the button goes in the first group
    @Test
    void test_transform_addButtonInFirstGroup() {
        Map<String, Object> app = map("subApps", map("browser", browser("actionbar", map("sections", list(
                listSection("social", "lvd:ctaSocial"), listSection("link", "lvd:ctaLink"),
                listSection("contact", "lvd:ctaContact"), map("name", "empty", "availability", map("nodeTypes", list("lvd:ctaLink"))))))));

        Map<String, Object> result = transformer.transform(app, app("lvd:ctaSocial", "lvd:ctaLink"));

        assertEquals(List.of("edit", ADD_ACTION), itemNames(result, "social", "first"));
        assertEquals(List.of("delete"), itemNames(result, "social", "second"));
        assertEquals(List.of("edit", ADD_ACTION), itemNames(result, "link", "first"));
        assertEquals(List.of("edit"), itemNames(result, "contact", "first"), "not a configured type");
        assertEquals(map(), get(result, "subApps", "browser", "actionbar", "sections", "empty", "groups"), "no group: no button");
    }

    // Pages shape: no section names a node type ("crud" for every page), so the
    // untyped sections get the button, not the ones of deleted items
    @Test
    void test_transform_addButtonInUntypedSections() {
        Map<String, Object> app = map("subApps", map("browser", browser("actionbar", map("sections", list(
                map("name", "crud", "availability", map("rules", map("notDeleted", map("$type", "jcrIsDeletedRule", "negate", true))),
                        "groups", list(map("name", "add-delete", "items", list(map("name", "add"))))),
                map("name", "deletedByType", "availability", map("rules", list(map("$type", "jcrIsDeletedRule"))),
                        "groups", list(map("name", "add-delete", "items", list(map("name", "add"))))),
                map("name", "deletedByClass", "availability",
                        map("rules", map("isDeleted", map("class", "info.magnolia.ui.availability.rule.JcrIsDeletedRuleDefinition"))),
                        "groups", list(map("name", "add-delete", "items", list(map("name", "add"))))))))));

        Map<String, Object> result = transformer.transform(app, app("mgnl:page"));

        assertEquals(List.of("add", ADD_ACTION), itemNames(result, "crud", "add-delete"));
        assertEquals(List.of("add"), itemNames(result, "deletedByType", "add-delete"), "deleted items");
        assertEquals(List.of("add"), itemNames(result, "deletedByClass", "add-delete"), "deleted items");
    }

    // A typed section for deleted items: no button either
    @Test
    @SuppressWarnings("unchecked")
    void test_transform_noAddButtonInTypedDeletedSection() {
        Map<String, Object> deleted = section("lvd:promo", "addActions", "restore");
        ((Map<String, Object>) deleted.get("availability")).put("rules", map("isDeleted", map("$type", "jcrIsDeletedRule")));
        Map<String, Object> app = map("subApps", map("browser", browser("actionbar", map("sections", map(
                "deleted", deleted, "item", section("lvd:promo", "addActions", "addItem"))))));

        Map<String, Object> result = transformer.transform(app, app("lvd:promo"));

        assertEquals(List.of("restore"), itemNames(result, "deleted", "addActions"));
        assertEquals(List.of("addItem", ADD_ACTION), itemNames(result, "item", "addActions"));
    }

    // An app that already defines addLocaleVariant keeps its own definition and button
    @Test
    void test_transform_addActionAlreadyDefined() {
        Map<String, Object> app = map("subApps", map("browser", browser(
                "actions", map(ADD_ACTION, map("class", "my.OwnActionDefinition")),
                "actionbar", map("sections", map("item", section("lvd:promo", "addActions", ADD_ACTION, "addItem"))))));

        Map<String, Object> result = transformer.transform(app, app("lvd:promo"));

        assertEquals("my.OwnActionDefinition", get(result, "subApps", "browser", "actions", ADD_ACTION, "class"));
        assertNull(get(result, "subApps", "browser", "actions", ADD_ACTION, "availability", "nodeTypes"), "the app's own availability");
        assertEquals(List.of(ADD_ACTION, "addItem"), itemNames(result, "item", "addActions"), "not twice, same place");
    }

    // --- Guard: what editors can't do on variants and containers ---

    // Recognized by name, $type, class or the rename dialog
    @Test
    void test_isGuardrail() {
        for (String name : List.of("duplicate", "copy", "paste", "move", "rename", ADD_ACTION)) {
            assertTrue(AppDefinitionTransformer.isGuardrail(name, map("class", "my.OwnActionDefinition")), name);
        }
        for (String type : List.of("duplicateNodeAction", "moveAction", "copyContentAction", "pasteContentAction")) {
            assertTrue(AppDefinitionTransformer.isGuardrail("own", map("$type", type)), type);
        }
        for (String className : List.of("DuplicateNodeActionDefinition", "MoveActionDefinition", "CopyContentActionDefinition",
                "PasteContentActionDefinition")) {
            assertTrue(AppDefinitionTransformer.isGuardrail("own", map("class", "info.magnolia.some." + className)), className);
            assertTrue(AppDefinitionTransformer.isGuardrail("own", map("class", className)), "without package");
        }
        assertTrue(AppDefinitionTransformer.isGuardrail("renamePromo", map("$type", "openDialogAction", "dialogId", "ui-framework-jcr:rename")));

        assertFalse(AppDefinitionTransformer.isGuardrail("editItem", map("$type", "openDetailSubappAction")));
        assertFalse(AppDefinitionTransformer.isGuardrail("own", map("class", "my.NotAMoveActionDefinitionAtAll")));
        assertFalse(AppDefinitionTransformer.isGuardrail("own", map("$type", "openDialogAction", "dialogId", "my:renameAll")));
        assertFalse(AppDefinitionTransformer.isGuardrail("delete", map()), "allowed or not by the action lists");
    }

    // The guardrail actions: never on variants and containers, whatever the lists
    @Test
    void test_transform_guardrailActions() {
        Map<String, Object> app = map("subApps", map("browser", browser("actions", map(
                "duplicate", map("$type", "duplicateNodeAction"),
                "movePromo", map("class", "info.magnolia.ui.contentapp.action.MoveActionDefinition"),
                "renamePromo", map("$type", "openDialogAction", "dialogId", "ui-framework-jcr:rename")))));

        // Listing them changes nothing
        Map<String, Object> result = transformer.transform(app, app(List.of("lvd:promo"),
                List.of("duplicate", "movePromo", "renamePromo", ADD_ACTION), List.of("duplicate", "movePromo")));

        for (String action : List.of("duplicate", "movePromo", "renamePromo", ADD_ACTION)) {
            Map<?, ?> rules = (Map<?, ?>) get(result, "subApps", "browser", "actions", action, "availability", "rules");
            assertEquals(NOT_ON_VARIANTS, rules.get("notLocaleVariant"), action);
            assertEquals(NOT_ON_CONTAINERS, rules.get("notVariantsContainer"), action);
            assertNull(rules.get("allowedOnLocaleVariants"), action + ": not left to the action lists");
        }
    }

    // The other actions: on variants and containers as the app's lists allow,
    // decided by the rule at every check (it gets the app and the action)
    @Test
    void test_transform_otherActions() {
        Map<String, Object> app = map("subApps", map("browser", browser("actions", map(
                "editItem", map("$type", "openDetailSubappAction"),
                "notListed", map("class", "my.OwnActionDefinition")))));

        Map<String, Object> result = transformer.transform(app, app("lvd:promo"));

        for (String action : List.of("editItem", "notListed")) {
            assertEquals(map("allowedOnLocaleVariants", map("class", RULE, "app", "promos", "action", action)),
                    get(result, "subApps", "browser", "actions", action, "availability", "rules"), action);
        }
    }

    // The app's own rules stay, as a map or as a list (without names)
    @Test
    void test_transform_ownRulesKept() {
        Map<String, Object> app = map("subApps", map("browser", browser("actions", map(
                "editItem", map("availability", map("rules", map("own", map("class", "my.Rule")))),
                "export", map("availability", map("rules", list(map("$type", "jcrIsDeletedRule", "negate", true)))),
                "copy", map("availability", map("rules", list(map("class", "my.Rule"))))))));

        Map<String, Object> result = transformer.transform(app, app("lvd:promo"));

        assertEquals(map("own", map("class", "my.Rule"),
                "allowedOnLocaleVariants", map("class", RULE, "app", "promos", "action", "editItem")),
                get(result, "subApps", "browser", "actions", "editItem", "availability", "rules"));
        assertEquals(map("_0", map("$type", "jcrIsDeletedRule", "negate", true),
                "allowedOnLocaleVariants", map("class", RULE, "app", "promos", "action", "export")),
                get(result, "subApps", "browser", "actions", "export", "availability", "rules"));
        assertEquals(map("_0", map("class", "my.Rule"), "notLocaleVariant", NOT_ON_VARIANTS, "notVariantsContainer", NOT_ON_CONTAINERS),
                get(result, "subApps", "browser", "actions", "copy", "availability", "rules"));
    }

    // An app that sets a rule with one of our names keeps its own
    @Test
    void test_transform_ownRuleWithOurName() {
        Map<String, Object> own = map("class", "my.Rule");
        Map<String, Object> app = map("subApps", map("browser", browser("actions", map(
                "editItem", map("availability", map("rules", map("allowedOnLocaleVariants", own))),
                "copy", map("availability", map("rules", map("notLocaleVariant", own)))))));

        Map<String, Object> result = transformer.transform(app, app("lvd:promo"));

        assertEquals(own, get(result, "subApps", "browser", "actions", "editItem", "availability", "rules", "allowedOnLocaleVariants"));
        assertEquals(own, get(result, "subApps", "browser", "actions", "copy", "availability", "rules", "notLocaleVariant"));
        assertEquals(NOT_ON_CONTAINERS, get(result, "subApps", "browser", "actions", "copy", "availability", "rules", "notVariantsContainer"));
    }

    // Containers get their own section: first, as Magnolia shows the first
    // section whose availability matches
    @Test
    void test_transform_containerSection() {
        Map<String, Object> app = map("subApps", map("browser", browser(
                "actions", actions("activate", "export", "deactivate", "editItem"),
                "actionbar", map("sections", map(
                        "root", map("groups", map("addActions", map("items", list(map("name", "export"))))),
                        "item", section("lvd:promo", "editActions", "editItem", "activate", "deactivate", "export"))))));

        // The buttons follow the order of the config, not the app's
        Map<String, Object> result = transformer.transform(app, app(List.of("lvd:promo"), List.of("editItem"),
                List.of("deactivate", "export", "activate")));

        Map<?, ?> sections = (Map<?, ?>) get(result, "subApps", "browser", "actionbar", "sections");
        assertEquals(List.of(CONTAINER_SECTION, "root", "item"), List.copyOf(sections.keySet()));
        assertEquals(map("isVariantsContainer", map("class", RULE, "target", "container")),
                get(result, "subApps", "browser", "actionbar", "sections", CONTAINER_SECTION, "availability", "rules"),
                "only on containers");
        assertEquals(List.of(CONTAINER_GROUP),
                List.copyOf(((Map<?, ?>) get(result, "subApps", "browser", "actionbar", "sections", CONTAINER_SECTION, "groups")).keySet()),
                "one group");
        assertEquals(List.of("deactivate", "export", "activate"), itemNames(result, CONTAINER_SECTION, CONTAINER_GROUP));
        assertEquals(List.of("editItem", "activate", "deactivate", "export", ADD_ACTION), itemNames(result, "item", "editActions"),
                "the app's sections stay as they are (plus the add button)");
        assertEquals(List.of("export"), itemNames(result, "root", "addActions"));
    }

    // Only the allowed actions that the app has, with a button; never a guardrail
    @Test
    void test_transform_containerSectionButtons() {
        Map<String, Object> app = map("subApps", map("browser", browser(
                "actions", actions("activate", "markDeleted", "duplicate", "editItem"),
                "actionbar", map("sections", map(
                        "item", section("lvd:promo", "editActions", "editItem", "activate", "duplicate", "notAnAction"))))));

        Map<String, Object> result = transformer.transform(app, app(List.of("lvd:promo"), List.of(),
                List.of("activate", "markDeleted", "duplicate", "notInTheApp", "notAnAction")));

        // markDeleted: no button in the app (it runs after a confirmation);
        // notAnAction: a button without an action
        assertEquals(List.of("activate"), itemNames(result, CONTAINER_SECTION, CONTAINER_GROUP));
    }

    @Test
    void test_transform_containerSectionWithoutActions() {
        Map<String, Object> app = map("subApps", map("browser", browser(
                "actions", actions("activate"),
                "actionbar", map("sections", map("item", section("lvd:promo", "editActions", "activate"))))));

        Map<String, Object> result = transformer.transform(app, app(List.of("lvd:promo"), List.of("activate"), List.of()));

        assertEquals(List.of(), itemNames(result, CONTAINER_SECTION, CONTAINER_GROUP), "the section is there, and empty");
    }

    // An allowed container action restricted to node types must also run on
    // containers (e.g. content-types' confirmMarkDeletion: [<the content type>])
    @Test
    void test_transform_containerActionsNodeTypes() {
        Map<String, Object> restricted = map("availability", map("nodeTypes", map("promo", "lvd:promo")));
        Map<String, Object> app = map("subApps", map("browser", browser("actions", map(
                "confirmMarkDeletion", normalizedCopy(restricted), // allowed, without a button
                "activate", map(), // allowed, unrestricted
                "editItem", normalizedCopy(restricted), // not allowed on containers
                "duplicate", normalizedCopy(restricted))))); // guardrail

        Map<String, Object> result = transformer.transform(app, app(List.of("lvd:promo"), List.of("editItem"),
                List.of("confirmMarkDeletion", "activate", "duplicate")));

        assertEquals(list("lvd:promo", "lv:localeVariants"),
                get(result, "subApps", "browser", "actions", "confirmMarkDeletion", "availability", "nodeTypes"));
        assertEquals(list(), get(result, "subApps", "browser", "actions", "activate", "availability", "nodeTypes"),
                "unrestricted: it already runs on containers");
        assertEquals(list("lvd:promo"), get(result, "subApps", "browser", "actions", "editItem", "availability", "nodeTypes"));
        assertEquals(list("lvd:promo"), get(result, "subApps", "browser", "actions", "duplicate", "availability", "nodeTypes"));
    }

    // Each tree view's drop constraint is wrapped, keeping its mode
    @Test
    void test_transform_dropConstraints() {
        Map<String, Object> jcr = map("class", "info.magnolia.ui.contentapp.drop.JcrDropConstraintDefinition", "mode", "BETWEEN");
        Map<String, Object> own = map("$type", "ownConstraint", "primaryNodeType", "lvd:promo");
        Map<String, Object> app = map("subApps", map("browser", browser("workbench", map("contentViews", list(
                map("name", "tree", "$type", "treeView", "dropConstraint", jcr),
                map("name", "ownTree", "$type", "treeView", "dropConstraint", own),
                map("name", "list", "$type", "listView"))))));

        Map<String, Object> result = transformer.transform(app, app("lvd:promo"));

        assertEquals(map("class", LocaleVariantDropConstraintDefinition.class.getName(), "mode", "BETWEEN", "delegate", jcr),
                get(result, "subApps", "browser", "workbench", "contentViews", "tree", "dropConstraint"));
        assertEquals(map("class", LocaleVariantDropConstraintDefinition.class.getName(), "delegate", own),
                get(result, "subApps", "browser", "workbench", "contentViews", "ownTree", "dropConstraint"), "no mode: the default");
        assertEquals(map("name", "list", "$type", "listView"), get(result, "subApps", "browser", "workbench", "contentViews", "list"),
                "no drag and drop: left without");
    }

    @Test
    void test_transform_dropConstraintAlreadyWrapped() {
        Map<String, Object> wrapped = map("class", LocaleVariantDropConstraintDefinition.class.getName(),
                "delegate", map("class", "info.magnolia.ui.contentapp.drop.JcrDropConstraintDefinition"));
        Map<String, Object> app = map("subApps", map("browser", browser("workbench", map("contentViews", map(
                "tree", map("dropConstraint", wrapped))))));

        Map<String, Object> result = transformer.transform(app, app("lvd:promo"));

        assertEquals(wrapped, get(result, "subApps", "browser", "workbench", "contentViews", "tree", "dropConstraint"), "not twice");
    }

    // --- Which sub-apps ---

    // Only browsers (an action bar and a workbench). Pages' page editor has an
    // action bar but no workbench
    @Test
    void test_transform_otherSubApps() {
        Map<String, Object> detail = map("actions", map("commit", map(), "duplicate", map()), "form", map("properties", map()));
        Map<String, Object> pageEditor = map("actions", map("editProperties", map()),
                "actionbar", map("sections", map("pageActions", section("mgnl:page", "editActions", "editProperties"))));
        Map<String, Object> chooser = map("actions", map("choose", map()), "workbench", map("contentViews", map("tree", map())));
        Map<String, Object> app = map("subApps", map("detail", detail, "pageEditor", pageEditor, "chooser", chooser));

        Map<String, Object> result = transformer.transform(app, app("mgnl:page"));

        assertEquals(normalizedCopy(app), result, "nothing added");
    }

    @Test
    void test_transform_everyBrowser() {
        Map<String, Object> app = map("subApps", map("browser", browser(), "detail", map("form", map()), "archive", browser()));

        Map<String, Object> result = transformer.transform(app, app("lvd:promo"));

        for (String browser : List.of("browser", "archive")) {
            assertNotNull(get(result, "subApps", browser, "actions", ADD_ACTION), browser);
            assertNotNull(get(result, "subApps", browser, "actionbar", "sections", CONTAINER_SECTION), browser);
        }
    }

    @Test
    void test_transform_appWithoutSubApps() {
        Map<String, Object> result = transformer.transform(map("label", "Promos"), app("lvd:promo"));

        assertEquals("Promos", result.get("label"));
    }

    // The registry's raw definition is shared: the result is always a copy
    @Test
    void test_transform_sourceUnchanged() {
        Map<String, Object> app = map("datasource", map("allowedNodeTypes", list("lvd:promo")),
                "subApps", map("browser", browser(
                        "actions", map("editItem", map("availability", map("nodeTypes", list("lvd:promo"), "rules", list(map("class", "my.Rule")))),
                                "copy", map()),
                        "actionbar", map("sections", list(listSection("item", "lvd:promo"))),
                        "workbench", map("contentViews", list(map("name", "tree", "dropConstraint", map("class", "my.Constraint")))))));
        // A snapshot that shares nothing with the source
        String before = app.toString();

        transformer.transform(app, app(List.of("lvd:promo"), List.of("editItem"), List.of("editItem")));

        assertEquals(before, app.toString(), "same content, in the same shapes");
    }

    private static ResolvedLocaleVariantsAppConfig app(String... nodeTypes) {
        return app(List.of(nodeTypes), List.of("editItem"), List.of("activate"));
    }

    private static ResolvedLocaleVariantsAppConfig app(List<String> nodeTypes, List<String> variantActions,
            List<String> containerActions) {
        return new ResolvedLocaleVariantsAppConfig("promos", nodeTypes, variantActions, containerActions);
    }

    // A browser sub-app: an action bar and a workbench, plus (or replaced by) the given parts
    private static Map<String, Object> browser(Object... keyValues) {
        Map<String, Object> browser = map("workbench", map(), "actionbar", map());
        browser.putAll(map(keyValues));
        return browser;
    }

    private static Map<String, Object> actions(String... names) {
        Map<String, Object> actions = map();
        for (String name : names) {
            actions.put(name, map());
        }
        return actions;
    }

    // Content-types shape: a section of a node type with one group of buttons
    private static Map<String, Object> section(String nodeType, String group, String... items) {
        List<Object> buttons = list();
        for (String item : items) {
            buttons.add(map("name", item));
        }
        return map("availability", map("nodeTypes", map(nodeType.replace(':', '-'), nodeType)),
                "groups", map(group, map("items", buttons)));
    }

    // Hand-written shape, all lists: a section of a node type with two groups
    private static Map<String, Object> listSection(String name, String nodeType) {
        return map("name", name, "availability", map("nodeTypes", list(nodeType)), "groups", list(
                map("name", "first", "items", list(map("name", "edit"))),
                map("name", "second", "items", list(map("name", "delete")))));
    }

    // The buttons of a group of the browser's action bar, in order
    private static List<String> itemNames(Map<String, Object> result, String section, String group) {
        Map<?, ?> items = (Map<?, ?>) get(result, "subApps", "browser", "actionbar", "sections", section, "groups", group, "items");
        return items.keySet().stream().map(String::valueOf).toList();
    }

    // Follows the keys of the maps: null if a part is missing
    private static Object get(Map<String, Object> root, String... path) {
        Object node = root;
        for (String key : path) {
            node = node instanceof Map<?, ?> map ? map.get(key) : null;
        }
        return node;
    }
}
