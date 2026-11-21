package com.giufaz.magnolia.localevariants.app.activation;

import static com.giufaz.magnolia.localevariants.LocaleVariantStructure.VARIANTS_CONTAINER_TYPE;
import static com.giufaz.magnolia.localevariants.app.activation.AppDefinitionTransformerUtils.*;
import static com.giufaz.magnolia.localevariants.app.form.LocaleVariantAwareDetailSubApp.TWIN_SUFFIX;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.giufaz.magnolia.localevariants.app.create.AddLocaleVariantActionDefinition;
import com.giufaz.magnolia.localevariants.app.form.LocaleVariantAwareDetailSubApp;
import com.giufaz.magnolia.localevariants.app.guard.LocaleVariantDropConstraintDefinition;
import com.giufaz.magnolia.localevariants.app.guard.LocaleVariantRuleDefinition;
import com.giufaz.magnolia.localevariants.config.ResolvedLocaleVariantsAppConfig;

/**
 * Decorates an app by applying locale variants to the raw definition (YAML
 * map). Works on content-types apps and hand-written ones.
 *
 * <p>
 * On each browser sub-app:
 * <ul>
 * <li>datasource {@code allowedNodeTypes} + {@code lv:localeVariants}: the
 * variants container and its variants show in the tree;</li>
 * <li>browser action {@code addLocaleVariant}, added
 * for all configured node types (except for deleted nodes);</li>
 * <li>guard on variants and containers: copy, move, duplicate, rename, paste
 * and {@code addLocaleVariant} never; the other actions only if the app's
 * action lists allow them;</li>
 * <li>containers get their own action bar section, with the allowed container
 * actions;</li>
 * <li>variants and containers can't be dragged, and nothing can be dropped on
 * them;</li>
 * <li>"Restore previous version" ({@code restorePreviousJcrVersionAction})
 * becomes Magnolia's {@code recursiveRestorePreviousJcrVersionAction}:
 * restoring a deleted original or container brings back the variants under
 * it too. Module config {@code defaults.recursiveRestore: false} keeps the
 * app's own action.</li>
 * </ul>
 * <p>
 * On each detail sub-app (a sub-app with a form):
 * {@link LocaleVariantAwareDetailSubApp} as {@code subAppClass}, and a twin
 * sub-app {@code <subApp>_lvTwin} it opens on variants, with the node name
 * read-only and no i18n field.
 * <p>
 * The source map is never changed: the result is a copy.
 */
public class AppDefinitionTransformer {

    public static final String ADD_ACTION = "addLocaleVariant";
    public static final String CONTAINER_SECTION = "localeVariants";
    static final String CONTAINER_GROUP = "containerActions";

    // The group of the "add" buttons in content-types apps
    private static final String ADD_ACTIONS_GROUP = "addActions";
    private static final String IS_DELETED_RULE_TYPE = "jcrIsDeletedRule";
    private static final String IS_DELETED_RULE_CLASS = ".JcrIsDeletedRuleDefinition";

    // The form field that holds the node name when the datasource names none
    private static final String JCR_NAME_FIELD = "jcrName";

    // Magnolia's restore action of a deleted node, and its recursive one
    static final String RESTORE_TYPE = "restorePreviousJcrVersionAction";
    static final String RESTORE_CLASS = "info.magnolia.ui.contentapp.action.RestorePreviousJcrVersionActionDefinition";
    static final String RECURSIVE_RESTORE_TYPE = "recursiveRestorePreviousJcrVersionAction";

    // Never on variants and containers: recognized by name, $type, class or the
    // rename dialog
    private static final Set<String> GUARDRAIL_NAMES = Set.of("duplicate", "copy", "paste", "move", "rename",
            ADD_ACTION);
    private static final Set<String> GUARDRAIL_TYPES = Set.of("duplicateNodeAction", "moveAction", "copyContentAction",
            "pasteContentAction");
    private static final Set<String> GUARDRAIL_CLASSES = Set.of("DuplicateNodeActionDefinition", "MoveActionDefinition",
            "CopyContentActionDefinition", "PasteContentActionDefinition");

    public Map<String, Object> transform(Map<String, Object> rawApp, ResolvedLocaleVariantsAppConfig app) {
        Map<String, Object> appToDecorate = normalizedCopy(rawApp);
        addContainerType(datasourceOf(appToDecorate));
        Map<String, Map<String, Object>> subApps = subAppsOf(appToDecorate);
        Map<String, Map<String, Object>> twins = new LinkedHashMap<>();
        subApps.forEach((name, subApp) -> {
            if (isBrowserSubApp(subApp)) {
                decorateBrowser(subApp, app);
            } else if (isDetailSubApp(name, subApp)) {
                twins.put(name + TWIN_SUFFIX, buildVariantTwin(name, subApp, appToDecorate, app));
                subApp.put("subAppClass", LocaleVariantAwareDetailSubApp.class.getName());
            }
        });
        subApps.putAll(twins);
        return appToDecorate;
    }

    private boolean isBrowserSubApp(Map<String, Object> subApp) {
        return subApp.get("actionbar") instanceof Map<?, ?> && subApp.get("workbench") instanceof Map<?, ?>;
    }

    // A sub-app with a form, not a twin already
    private boolean isDetailSubApp(String name, Map<String, Object> subApp) {
        return subApp.get("form") instanceof Map<?, ?> && !name.endsWith(TWIN_SUFFIX);
    }

    private void decorateBrowser(Map<String, Object> browser, ResolvedLocaleVariantsAppConfig app) {
        addContainerType(datasourceOf(browser));
        addAddVariantAction(browser, app);
        guardVariantsAndContainers(browser, app);
        if (app.recursiveRestore()) {
            actionsOf(browser).values().forEach(this::makeRestoreRecursive);
        }
    }

    // --- Variants and containers show in the tree ---

    private void addContainerType(Map<String, Object> datasource) {
        List<String> allowed = allowedNodeTypesOf(datasource);
        // allowedNodeTypes missing or empty: no restriction, every type is already
        // shown
        if (!allowed.isEmpty() && !allowed.contains(VARIANTS_CONTAINER_TYPE)) {
            allowed.add(VARIANTS_CONTAINER_TYPE);
        }
    }

    // Build a twin of a detail sub-app compatible with variants nodes:
    // - Node name read-only
    // - No i18n field to force single-language editing
    private Map<String, Object> buildVariantTwin(String name, Map<String, Object> detail, Map<String, Object> rawApp,
            ResolvedLocaleVariantsAppConfig app) {
        Map<String, Object> twin = normalizedCopy(detail);
        twin.put("name", name + TWIN_SUFFIX);
        // The generated i18n key of a sub-app's label contains its name: the twin
        // uses its original's
        twin.putIfAbsent("label", app.name() + "." + name + ".label");
        Map<String, Object> form = formOf(twin);
        String nodeNameProperty = nodeNameProperty(detail, rawApp);
        fieldsOf(form).forEach((fieldName, field) -> {
            if (JCR_NAME_FIELD.equals(fieldName) || fieldName.equals(nodeNameProperty)) {
                field.put("readOnly", true);
            }
        });
        removeI18n(form);
        return twin;
    }

    // The sub-app's own datasource, else the app's
    private String nodeNameProperty(Map<String, Object> detail, Map<String, Object> rawApp) {
        Object own = datasourceOf(detail).get("nodeNameProperty");
        Object shared = datasourceOf(rawApp).get("nodeNameProperty");
        return own != null ? String.valueOf(own) : shared != null ? String.valueOf(shared) : null;
    }

    // Wherever a definition says "i18n: true", at any depth: switched to false.
    // No need to know the kind of field or where it keeps its nested fields:
    // false is Magnolia's default, so only an i18n written as true matters
    private void removeI18n(Object node) {
        if (node instanceof List<?> list) {
            list.forEach(this::removeI18n);
        } else if (node instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> definition = (Map<String, Object>) map;
            if (isTrue(definition.get("i18n"))) {
                definition.put("i18n", false);
            }
            definition.values().forEach(this::removeI18n);
        }
    }

    // true as YAML reads it: the boolean, or the text "true"
    private static boolean isTrue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    // --- Recursive restore ---

    // Magnolia's restore action (by $type or by its exact class) becomes
    // Magnolia's recursive one, which restores every node under the restored
    // one too. A project's own subclass of the definition is left as it is
    private void makeRestoreRecursive(Map<String, Object> action) {
        boolean isMagnoliaRestore = RESTORE_TYPE.equals(action.get("$type"))
                || RESTORE_CLASS.equals(action.get("class"));
        if (!isMagnoliaRestore) {
            return;
        }
        action.remove("class");
        action.put("$type", RECURSIVE_RESTORE_TYPE);
        // A property of the replaced definition only: unknown to the recursive one
        action.remove("parentNodeTypeOnly");
        // Defaults of the replaced definition, which the recursive one doesn't set
        action.putIfAbsent("successMessage", "notification.item.restored");
        action.putIfAbsent("alwaysShowSuccessMessage", true);
    }

    // --- The addLocaleVariant action ---

    // Its definition in the browser's actions, its button in the action bar
    // sections of the configured node types
    private void addAddVariantAction(Map<String, Object> browser, ResolvedLocaleVariantsAppConfig app) {
        actionsOf(browser).putIfAbsent(ADD_ACTION, buildAddVariantActionDefinition(app.nodeTypes()));
        findSectionsRelevantForNodes(sectionsOf(actionbarOf(browser)), app.nodeTypes())
                .forEach(this::placeAddVariantActionInSection);
    }

    private Map<String, Object> buildAddVariantActionDefinition(List<String> nodeTypes) {
        Map<String, Object> notDeleted = new LinkedHashMap<>();
        notDeleted.put("$type", IS_DELETED_RULE_TYPE);
        notDeleted.put("negate", true);
        Map<String, Object> availability = new LinkedHashMap<>();
        availability.put("writePermissionRequired", true);
        availability.put("nodeTypes", nodeTypes);
        availability.put("rules", new LinkedHashMap<>(Map.of("notDeleted", notDeleted)));
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("class", AddLocaleVariantActionDefinition.class.getName());
        action.put("availability", availability);
        return action;
    }

    private List<Map<String, Object>> findSectionsRelevantForNodes(Map<String, Map<String, Object>> sections,
            List<String> nodeTypes) {
        List<Map<String, Object>> notDeletedSections = sections.values().stream()
                .filter(section -> !isDeletedSection(section))
                .toList();
        List<Map<String, Object>> sectionsApplicableToNodeTypes = notDeletedSections.stream()
                .filter(section -> sectionNodeTypes(section).stream().anyMatch(nodeTypes::contains))
                .toList();
        if (!sectionsApplicableToNodeTypes.isEmpty()) {
            return sectionsApplicableToNodeTypes;
        }
        // with no type-specific sections, return the catch-all default section
        return notDeletedSections.stream()
                .filter(section -> sectionNodeTypes(section).isEmpty())
                .toList();
    }

    // A section for deleted items (jcrIsDeletedRule, not negated)
    private boolean isDeletedSection(Map<String, Object> section) {
        return rulesOf(availabilityOf(section)).values().stream()
                .anyMatch(rule -> (IS_DELETED_RULE_TYPE.equals(rule.get("$type"))
                        || String.valueOf(rule.get("class")).endsWith(IS_DELETED_RULE_CLASS))
                        && !Boolean.parseBoolean(String.valueOf(rule.get("negate"))));
    }

    private List<String> sectionNodeTypes(Map<String, Object> section) {
        return nodeTypesOf(availabilityOf(section));
    }

    // In the "addActions" group, as content-types apps name it, else in the
    // first group
    private void placeAddVariantActionInSection(Map<String, Object> section) {
        Map<String, Map<String, Object>> groups = groupsOf(section);
        Map<String, Object> group = groups.containsKey(ADD_ACTIONS_GROUP) ? groups.get(ADD_ACTIONS_GROUP)
                : groups.values().stream().findFirst().orElse(null);
        if (group != null) {
            itemsOf(group).putIfAbsent(ADD_ACTION, new LinkedHashMap<>());
        }
    }

    // --- Guard: what editors can't do on variants and containers ---

    private void guardVariantsAndContainers(Map<String, Object> browser, ResolvedLocaleVariantsAppConfig app) {
        Map<String, Map<String, Object>> actions = actionsOf(browser);
        addContainerSection(actionbarOf(browser), actions, app);
        actions.forEach((name, action) -> guardAction(name, action, app));
        wrapDropConstraints(workbenchOf(browser));
    }

    // Containers get their own section, instead of the root's: first, as
    // Magnolia shows the first section whose availability matches
    private void addContainerSection(Map<String, Object> actionbar, Map<String, Map<String, Object>> actions,
            ResolvedLocaleVariantsAppConfig app) {
        Map<String, Map<String, Object>> appSections = sectionsOf(actionbar);
        Map<String, Object> sections = new LinkedHashMap<>();
        sections.put(CONTAINER_SECTION, buildContainerSection(appSections, actions, app));
        sections.putAll(appSections);
        actionbar.put("sections", sections);
    }

    // One group with the allowed container actions that the app has, in the
    // order of the config. An action without a button in the app (e.g.
    // markDeleted, run after a confirmation) is allowed but gets no button here
    private Map<String, Object> buildContainerSection(Map<String, Map<String, Object>> appSections,
            Map<String, Map<String, Object>> actions, ResolvedLocaleVariantsAppConfig app) {
        Set<String> actionsWithButton = actionsWithButton(appSections);
        Map<String, Object> newSection = new LinkedHashMap<>();
        rulesOf(availabilityOf(newSection)).put("isVariantsContainer", onlyOnContainersRule());
        Map<String, Object> group = new LinkedHashMap<>();
        groupsOf(newSection).put(CONTAINER_GROUP, group);
        Map<String, Object> items = itemsOf(group);
        for (String action : app.containerActions()) {
            if (!actions.containsKey(action) || isGuardrail(action, actions.get(action))) {
                continue;
            }
            allowOnContainer(actions.get(action));
            if (actionsWithButton.contains(action)) {
                items.put(action, new LinkedHashMap<>());
            }
        }
        return newSection;
    }

    private Set<String> actionsWithButton(Map<String, Map<String, Object>> sections) {
        Set<String> actions = new HashSet<>();
        for (Map<String, Object> section : sections.values()) {
            groupsOf(section).values().forEach(group -> actions.addAll(itemsOf(group).keySet()));
        }
        return actions;
    }

    // An allowed container action restricted to node types also runs on
    // containers (e.g. content-types' confirmMarkDeletion has nodeTypes
    // [<the content type>]); unrestricted actions already do
    private void allowOnContainer(Map<String, Object> action) {
        List<String> nodeTypes = nodeTypesOf(availabilityOf(action));
        if (!nodeTypes.isEmpty() && !nodeTypes.contains(VARIANTS_CONTAINER_TYPE)) {
            nodeTypes.add(VARIANTS_CONTAINER_TYPE);
        }
    }

    // A rule on every path to the action (action bar, double-click, Enter, delete
    // key): the guardrail actions never on variants and containers, the others
    // as the app's lists allow
    private void guardAction(String name, Map<String, Object> action, ResolvedLocaleVariantsAppConfig app) {
        Map<String, Map<String, Object>> rules = rulesOf(availabilityOf(action));
        if (isGuardrail(name, action)) {
            rules.putIfAbsent("notLocaleVariant", notOnVariantsRule());
            rules.putIfAbsent("notVariantsContainer", notOnContainersRule());
        } else {
            rules.putIfAbsent("allowedOnLocaleVariants", allowedByConfigRule(app.name(), name));
        }
    }

    // Copy, move, duplicate, rename, paste, addLocaleVariant: by name, $type,
    // class or the rename dialog
    static boolean isGuardrail(String name, Map<String, Object> action) {
        if (GUARDRAIL_NAMES.contains(name)) {
            return true;
        }
        String type = String.valueOf(action.get("$type"));
        String className = String.valueOf(action.get("class"));
        return GUARDRAIL_TYPES.contains(type)
                || GUARDRAIL_CLASSES.contains(className.substring(className.lastIndexOf('.') + 1))
                || String.valueOf(action.get("dialogId")).endsWith(":rename");
    }

    // Each tree view's drop constraint, wrapped: variants and containers can't
    // be dragged, nothing is dropped on or next to them
    private void wrapDropConstraints(Map<String, Object> workbench) {
        for (Map<String, Object> view : contentViewsOf(workbench).values()) {
            if (view.get("dropConstraint") instanceof Map<?, ?> constraint
                    && !LocaleVariantDropConstraintDefinition.class.getName().equals(constraint.get("class"))) {
                Map<String, Object> wrapper = new LinkedHashMap<>();
                wrapper.put("class", LocaleVariantDropConstraintDefinition.class.getName());
                if (constraint.get("mode") != null) {
                    wrapper.put("mode", constraint.get("mode")); // the row dragger reads the outer definition's mode
                }
                wrapper.put("delegate", constraint);
                view.put("dropConstraint", wrapper);
            }
        }
    }

    // --- The module's availability rules (LocaleVariantRuleDefinition) ---

    private static Map<String, Object> notOnVariantsRule() {
        return targetRule(LocaleVariantRuleDefinition.VARIANT, true);
    }

    private static Map<String, Object> notOnContainersRule() {
        return targetRule(LocaleVariantRuleDefinition.CONTAINER, true);
    }

    private static Map<String, Object> onlyOnContainersRule() {
        return targetRule(LocaleVariantRuleDefinition.CONTAINER, false);
    }

    // On variants and containers only if the app's action lists allow the action
    private static Map<String, Object> allowedByConfigRule(String appName, String actionName) {
        Map<String, Object> rule = rule();
        rule.put("app", appName);
        rule.put("action", actionName);
        return rule;
    }

    private static Map<String, Object> targetRule(String target, boolean negate) {
        Map<String, Object> rule = rule();
        rule.put("target", target);
        if (negate) {
            rule.put("negate", true);
        }
        return rule;
    }

    private static Map<String, Object> rule() {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("class", LocaleVariantRuleDefinition.class.getName());
        return rule;
    }
}
