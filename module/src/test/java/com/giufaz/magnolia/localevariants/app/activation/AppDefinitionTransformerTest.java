package com.giufaz.magnolia.localevariants.app.activation;

import static com.giufaz.magnolia.localevariants.app.activation.AppDefinitionTransformer.ADD_ACTION;
import static com.giufaz.magnolia.localevariants.app.activation.AppDefinitionTransformer.CONTAINER_GROUP;
import static com.giufaz.magnolia.localevariants.app.activation.AppDefinitionTransformer.CONTAINER_SECTION;
import static com.giufaz.magnolia.localevariants.app.activation.AppDefinitionTransformer.RECURSIVE_RESTORE_TYPE;
import static com.giufaz.magnolia.localevariants.app.activation.AppDefinitionTransformer.RESTORE_CLASS;
import static com.giufaz.magnolia.localevariants.app.activation.AppDefinitionTransformer.RESTORE_TYPE;
import static com.giufaz.magnolia.localevariants.app.activation.AppDefinitionTransformerUtils.normalizedCopy;
import static com.giufaz.magnolia.localevariants.app.activation.AppDefinitionTransformerUtilsTest.list;
import static com.giufaz.magnolia.localevariants.app.activation.AppDefinitionTransformerUtilsTest.map;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.giufaz.magnolia.localevariants.app.create.AddLocaleVariantActionDefinition;
import com.giufaz.magnolia.localevariants.app.form.LocaleVariantAwareDetailSubApp;
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

    // --- Detail sub-apps: the twin for locale variants ---

    @Test
    void test_transform_detailTwin() {
        Map<String, Object> detail = map("class", "info.magnolia.ui.contentapp.detail.DetailDescriptor",
                "subAppClass", "my.DetailSubApp", "actions", map("commit", map()),
                "form", map("properties", map("jcrName", map("$type", "textField"), "title", map("i18n", true))));
        Map<String, Object> app = map("subApps", map("browser", browser(), "detail", detail));

        Map<String, Object> result = transformer.transform(app, app("lvd:promo"));

        assertEquals(List.of("browser", "detail", "detail_lvTwin"), List.copyOf(((Map<?, ?>) result.get("subApps")).keySet()));
        assertEquals(LocaleVariantAwareDetailSubApp.class.getName(), get(result, "subApps", "detail", "subAppClass"),
                "the sub-app opens through the module's class");
        assertEquals(map("jcrName", map("$type", "textField"), "title", map("i18n", true)),
                get(result, "subApps", "detail", "form", "properties"), "its form is left as written");

        assertEquals("detail_lvTwin", get(result, "subApps", "detail_lvTwin", "name"));
        assertEquals("my.DetailSubApp", get(result, "subApps", "detail_lvTwin", "subAppClass"), "keeps the original class");
        assertEquals("info.magnolia.ui.contentapp.detail.DetailDescriptor", get(result, "subApps", "detail_lvTwin", "class"));
        assertEquals(map("commit", map()), get(result, "subApps", "detail_lvTwin", "actions"), "same actions");
        assertEquals(true, twinField(result, "jcrName").get("readOnly"), "node name");
        assertEquals("textField", twinField(result, "jcrName").get("$type"));
        assertNotI18n(twinField(result, "title"));
        assertNull(twinField(result, "title").get("readOnly"), "other fields stay editable");
    }

    // No subAppClass written: the twin has none either, so Magnolia gives it
    // the default class of its definition, as it did for the original
    @Test
    void test_transform_detailTwin_withoutSubAppClass() {
        Map<String, Object> app = map("subApps", map("detail", map("form", map("properties", map("title", map())))));

        Map<String, Object> result = transformer.transform(app, app("lvd:promo"));

        assertEquals(LocaleVariantAwareDetailSubApp.class.getName(), get(result, "subApps", "detail", "subAppClass"));
        assertFalse(((Map<?, ?>) get(result, "subApps", "detail_lvTwin")).containsKey("subAppClass"));
    }

    // The generated i18n key of a sub-app's label contains the sub-app name:
    // the twin points to its original's key
    @Test
    void test_transform_detailTwin_label() {
        Map<String, Object> app = map("subApps", map(
                "detail", map("form", map()),
                "edit", map("label", "Edit promo", "form", map())));

        Map<String, Object> result = transformer.transform(app, app("lvd:promo"));

        assertEquals("promos.detail.label", get(result, "subApps", "detail_lvTwin", "label"), "key of the original");
        assertEquals("Edit promo", get(result, "subApps", "edit_lvTwin", "label"), "a written label is kept");
        assertNull(get(result, "subApps", "detail", "label"), "the original gets none");
    }

    // The field that renames the node: "jcrName", or the one named by the
    // datasource's nodeNameProperty (the sub-app's own, else the app's)
    @Test
    void test_transform_detailTwin_nodeNameProperty() {
        Map<String, Object> fields = map("jcrName", map(), "name", map(), "slug", map(), "title", map());
        Map<String, Object> app = map("datasource", map("nodeNameProperty", "name"), "subApps", map(
                "detail", map("form", map("properties", normalizedCopy(fields))),
                "other", map("datasource", map("nodeNameProperty", "slug"), "form", map("properties", normalizedCopy(fields)))));

        Map<String, Object> result = transformer.transform(app, app("lvd:promo"));

        assertEquals(List.of("jcrName", "name"), readOnlyFields(result, "detail_lvTwin"), "the app's datasource");
        assertEquals(List.of("jcrName", "slug"), readOnlyFields(result, "other_lvTwin"), "the sub-app's own datasource");
    }

    @Test
    void test_transform_detailTwin_noNodeNameProperty() {
        Map<String, Object> app = map("datasource", map("workspace", "promos"), "subApps", map(
                "detail", map("form", map("properties", map("name", map(), "title", map())))));

        Map<String, Object> result = transformer.transform(app, app("lvd:promo"));

        assertEquals(List.of(), readOnlyFields(result, "detail_lvTwin"), "a field called name is a normal field");
        assertNull(get(result, "subApps", "detail", "datasource"), "the sub-app stays without a datasource");
        assertNull(get(result, "subApps", "detail_lvTwin", "datasource"));
    }

    // "properties" written as a list of named fields
    @Test
    void test_transform_detailTwin_fieldsAsList() {
        Map<String, Object> app = map("subApps", map("detail", map("form", map("properties", list(
                map("name", "jcrName"), map("name", "title", "i18n", true), map("name", "url"))))));

        Map<String, Object> result = transformer.transform(app, app("lvd:promo"));

        List<?> fields = (List<?>) get(result, "subApps", "detail_lvTwin", "form", "properties");
        assertEquals(3, fields.size(), "still a list");
        assertEquals(true, ((Map<?, ?>) fields.get(0)).get("readOnly"));
        assertNotI18n((Map<?, ?>) fields.get(1));
        assertNull(((Map<?, ?>) fields.get(2)).get("readOnly"));
    }

    // Nested fields: composite ("properties"), multi ("field"), switchable
    // ("field" and the "properties" of its "forms")
    @Test
    void test_transform_detailTwin_nestedI18nFields() {
        Map<String, Object> composite = map("$type", "compositeField", "i18n", true,
                "properties", map("street", map("i18n", true), "city", map()));
        Map<String, Object> multi = map("$type", "jcrMultiField", "i18n", true,
                "field", map("$type", "compositeField", "i18n", true, "properties", list(map("name", "text", "i18n", true))));
        Map<String, Object> switchable = map("$type", "switchableField", "field", map("$type", "radioButtonGroupField", "i18n", true),
                "forms", list(map("name", "internal", "properties", map("page", map("i18n", true)))));
        Map<String, Object> app = map("subApps", map("detail", map("form", map(
                "properties", map("address", composite, "links", multi, "target", switchable)))));
        String originalForm = String.valueOf(get(app, "subApps", "detail", "form"));

        Map<String, Object> result = transformer.transform(app, app("lvd:promo"));

        Object twinForm = get(result, "subApps", "detail_lvTwin", "form");
        assertFalse(twinForm.toString().contains("i18n=true"), "no i18n field at any depth: " + twinForm);
        assertEquals("compositeField", twinField(result, "address").get("$type"), "the rest is kept");
        assertEquals(originalForm, String.valueOf(get(result, "subApps", "detail", "form")), "the original keeps its i18n fields");
    }

    // Only an i18n written as true is switched off, wherever it is: no need to
    // know how a field keeps its nested fields
    @Test
    void test_transform_detailTwin_i18nOnlyWhereTrue() {
        Map<String, Object> custom = map("class", "my.CustomFieldDefinition", "i18n", "true",
                "tabs", list(map("name", "first", "fields", list(map("name", "claim", "i18n", true)))));
        Map<String, Object> app = map("subApps", map("detail", map("form", map("properties", map(
                "custom", custom,
                "plain", map("$type", "textField"),
                "notI18n", map("i18n", false),
                "configured", map("i18n", map("fallback", "en")))))));

        Map<String, Object> result = transformer.transform(app, app("lvd:promo"));

        assertEquals(false, twinField(result, "custom").get("i18n"), "written as text");
        assertEquals(list(map("name", "first", "fields", list(map("name", "claim", "i18n", false)))),
                twinField(result, "custom").get("tabs"), "nested under any name");
        assertEquals(map("$type", "textField"), twinField(result, "plain"), "nothing added where i18n is not written");
        assertEquals(map("i18n", false), twinField(result, "notI18n"));
        assertEquals(map("i18n", map("fallback", "en")), twinField(result, "configured"), "an i18n that is not a flag is left");
    }

    @Test
    void test_transform_everyDetailSubApp() {
        Map<String, Object> app = map("subApps", map("browser", browser(), "detail", map("form", map()),
                "quickEdit", map("form", map())));

        Map<String, Object> result = transformer.transform(app, app("lvd:promo"));

        assertEquals(List.of("browser", "detail", "quickEdit", "detail_lvTwin", "quickEdit_lvTwin"),
                List.copyOf(((Map<?, ?>) result.get("subApps")).keySet()), "the twins come last");
    }

    // A definition that already has a twin (decorated twice): no twin of a twin
    @Test
    void test_transform_detailTwin_notOfATwin() {
        Map<String, Object> app = map("subApps", map("detail", map("form", map()),
                "detail_lvTwin", map("label", "written by hand", "form", map())));

        Map<String, Object> result = transformer.transform(app, app("lvd:promo"));

        assertEquals(List.of("detail", "detail_lvTwin"), List.copyOf(((Map<?, ?>) result.get("subApps")).keySet()));
        assertNull(get(result, "subApps", "detail_lvTwin", "subAppClass"), "a twin never opens through the module's class");
    }

    // --- Recursive restore ---

    @Test
    void test_transform_recursiveRestore() {
        Map<String, Object> app = map("subApps", map("browser", browser("actions", map(
                "restorePreviousVersion", map("$type", RESTORE_TYPE, "icon", "icon-undo", "parentNodeTypeOnly", true,
                        "availability", map("rules", map("isDeleted", map("$type", "jcrIsDeletedRule")))),
                "restoreByClass", map("class", RESTORE_CLASS, "successMessage", "my.message", "alwaysShowSuccessMessage", false)))));

        Map<String, Object> result = transformer.transform(app, app("lvd:promo"));

        Map<?, ?> restore = (Map<?, ?>) get(result, "subApps", "browser", "actions", "restorePreviousVersion");
        assertEquals(RECURSIVE_RESTORE_TYPE, restore.get("$type"));
        assertEquals("icon-undo", restore.get("icon"), "the rest of the action is kept");
        assertNotNull(get(result, "subApps", "browser", "actions", "restorePreviousVersion", "availability", "rules", "isDeleted"));
        assertFalse(restore.containsKey("parentNodeTypeOnly"), "unknown to the recursive action");
        assertEquals("notification.item.restored", restore.get("successMessage"), "the replaced action's defaults");
        assertEquals(true, restore.get("alwaysShowSuccessMessage"));

        Map<?, ?> byClass = (Map<?, ?>) get(result, "subApps", "browser", "actions", "restoreByClass");
        assertEquals(RECURSIVE_RESTORE_TYPE, byClass.get("$type"));
        assertFalse(byClass.containsKey("class"));
        assertEquals("my.message", byClass.get("successMessage"), "what the app wrote is kept");
        assertEquals(false, byClass.get("alwaysShowSuccessMessage"));
    }

    // A project's own restore, other actions, and the restore of a version
    @Test
    void test_transform_recursiveRestore_otherActions() {
        Map<String, Object> actions = map(
                "restorePreviousVersion", map("class", "my.project.RestoreActionDefinition"),
                "restoreVersion", map("$type", "restoreJcrVersionAction"),
                "editItem", map("$type", "openDetailSubappAction"));
        Map<String, Object> app = map("subApps", map("browser", browser("actions", normalizedCopy(actions))));

        Map<String, Object> result = transformer.transform(app, app("lvd:promo"));

        assertEquals("my.project.RestoreActionDefinition", get(result, "subApps", "browser", "actions", "restorePreviousVersion", "class"));
        assertNull(get(result, "subApps", "browser", "actions", "restorePreviousVersion", "$type"));
        assertEquals("restoreJcrVersionAction", get(result, "subApps", "browser", "actions", "restoreVersion", "$type"));
        assertEquals("openDetailSubappAction", get(result, "subApps", "browser", "actions", "editItem", "$type"));
    }

    @Test
    void test_transform_recursiveRestore_switchedOff() {
        Map<String, Object> app = map("subApps", map("browser", browser("actions", map(
                "restorePreviousVersion", map("$type", RESTORE_TYPE, "parentNodeTypeOnly", true)))));
        ResolvedLocaleVariantsAppConfig withoutRecursiveRestore = new ResolvedLocaleVariantsAppConfig("promos",
                List.of("lvd:promo"), List.of("editItem"), List.of("activate"), false);

        Map<String, Object> result = transformer.transform(app, withoutRecursiveRestore);

        Map<?, ?> restore = (Map<?, ?>) get(result, "subApps", "browser", "actions", "restorePreviousVersion");
        assertEquals(RESTORE_TYPE, restore.get("$type"), "the app's own action");
        assertEquals(true, restore.get("parentNodeTypeOnly"));
        assertFalse(restore.containsKey("successMessage"));
    }

    // --- Which sub-apps ---

    // Browsers (an action bar and a workbench) and detail sub-apps (a form).
    // Pages' page editor has an action bar but no workbench, a chooser no form
    @Test
    void test_transform_otherSubApps() {
        Map<String, Object> pageEditor = map("actions", map("editProperties", map()),
                "actionbar", map("sections", map("pageActions", section("mgnl:page", "editActions", "editProperties"))));
        Map<String, Object> chooser = map("actions", map("choose", map()), "workbench", map("contentViews", map("tree", map())));
        Map<String, Object> formWithoutValue = map("actions", map("commit", map()), "form", null);
        Map<String, Object> app = map("subApps", map("pageEditor", pageEditor, "chooser", chooser, "empty", formWithoutValue));

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
                        "workbench", map("contentViews", list(map("name", "tree", "dropConstraint", map("class", "my.Constraint"))))),
                        "detail", map("subAppClass", "my.DetailSubApp", "form", map("properties", list(
                                map("name", "jcrName"), map("name", "title", "i18n", true))))));
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
        return new ResolvedLocaleVariantsAppConfig("promos", nodeTypes, variantActions, containerActions, true);
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

    // A field of the form of the twin of "detail" (fields written as a map)
    private static Map<?, ?> twinField(Map<String, Object> result, String field) {
        return (Map<?, ?>) get(result, "subApps", "detail_lvTwin", "form", "properties", field);
    }

    private static List<String> readOnlyFields(Map<String, Object> result, String subApp) {
        Map<?, ?> fields = (Map<?, ?>) get(result, "subApps", subApp, "form", "properties");
        return fields.entrySet().stream().filter(field -> Boolean.TRUE.equals(((Map<?, ?>) field.getValue()).get("readOnly")))
                .map(field -> String.valueOf(field.getKey())).toList();
    }

    // Not written or false: both mean "not i18n" to Magnolia
    private static void assertNotI18n(Map<?, ?> field) {
        assertFalse(Boolean.TRUE.equals(field.get("i18n")), "i18n: " + field);
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
