package com.giufaz.magnolia.localevariants.app.activation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Helpers of {@link AppDefinitionTransformer} on the raw definition of an app
 * (the YAML map): a normalized deep copy, so the transformer handles one shape
 * only, and typed access to its parts.
 */
final class AppDefinitionTransformerUtils {

    private AppDefinitionTransformerUtils() {
    }

    // --- Normalized copy ---

    // Collections of named items, written as a map ({name: {...}}) or as a list
    // ([{name: ..., ...}]): normalized to maps. Map2Bean reads both the same way
    // (a map's values in order, the name from the key), so the definition means
    // the same and the transformer handles one shape only
    static final Set<String> NAMED_COLLECTIONS = Set.of("subApps", "actions", "sections", "groups", "items",
            "rules", "contentViews");
    // Collections of strings, written as a list, a map or "a,b": normalized to
    // lists
    static final Set<String> STRING_COLLECTIONS = Set.of("nodeTypes", "allowedNodeTypes");

    // A deep copy (YAML anchors share instances, so copy before changing
    // anything) with the named collections as maps and the string collections
    // as lists
    @SuppressWarnings("unchecked")
    static <T> T normalizedCopy(T node) {
        return (T) copyOf(node, null);
    }

    // key: the key the node is under, which tells what kind of collection it is
    private static Object copyOf(Object node, String key) {
        if (key != null && NAMED_COLLECTIONS.contains(key) && node instanceof List<?> list) {
            return copyOfNamedCollection(list);
        }
        if (key != null && STRING_COLLECTIONS.contains(key)) {
            return copyOfStringCollection(node);
        }
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((childKey, value) -> copy.put(String.valueOf(childKey), copyOf(value, String.valueOf(childKey))));
            return copy;
        }
        if (node instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(copyOf(item, null)));
            return copy;
        }
        return node;
    }

    // [{name: a, ...}] → {a: {...}}. An item without a name (e.g. a rule: rule
    // definitions have no name) gets the key "_<position>", harmless as for
    // rules written as a map
    private static Map<String, Object> copyOfNamedCollection(List<?> list) {
        Map<String, Object> named = new LinkedHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            Object name = item instanceof Map<?, ?> child ? child.get("name") : null;
            named.put(name != null ? String.valueOf(name) : "_" + i, copyOf(item, null));
        }
        return named;
    }

    // A list, the values of a map, or "a,b" (as Map2Bean reads it) → a new list
    private static List<Object> copyOfStringCollection(Object node) {
        List<?> values = node instanceof List<?> list ? list
                : node instanceof Map<?, ?> map ? new ArrayList<>(map.values())
                        : node instanceof String string ? List.of(string.split(",")) : List.of();
        return new ArrayList<>(values.stream().filter(Objects::nonNull).map(String::valueOf).toList());
    }

    // --- The parts of a normalized app definition ---
    // They are the definition's own maps and lists: changing them changes the
    // definition. A missing part is added empty (an empty one means the
    // default), except the datasource

    // Unlike the others, a missing datasource is not added: a sub-app without
    // one uses the app's, and an empty one would replace it
    static Map<String, Object> datasourceOf(Map<String, Object> appOrSubApp) {
        return appOrSubApp.get("datasource") instanceof Map<?, ?> ? getChildMap(appOrSubApp, "datasource")
                : new LinkedHashMap<>();
    }

    static Map<String, Object> actionbarOf(Map<String, Object> subApp) {
        return getChildMap(subApp, "actionbar");
    }

    static Map<String, Object> workbenchOf(Map<String, Object> subApp) {
        return getChildMap(subApp, "workbench");
    }

    static Map<String, Map<String, Object>> subAppsOf(Map<String, Object> app) {
        return getChildMap(app, "subApps");
    }

    static Map<String, Map<String, Object>> actionsOf(Map<String, Object> subApp) {
        return getChildMap(subApp, "actions");
    }

    static Map<String, Map<String, Object>> sectionsOf(Map<String, Object> actionbar) {
        return getChildMap(actionbar, "sections");
    }

    static Map<String, Map<String, Object>> groupsOf(Map<String, Object> section) {
        return getChildMap(section, "groups");
    }

    static Map<String, Object> itemsOf(Map<String, Object> group) {
        return getChildMap(group, "items");
    }

    static Map<String, Map<String, Object>> contentViewsOf(Map<String, Object> workbench) {
        return getChildMap(workbench, "contentViews");
    }

    static Map<String, Object> availabilityOf(Map<String, Object> actionOrSection) {
        return getChildMap(actionOrSection, "availability");
    }

    static Map<String, Map<String, Object>> rulesOf(Map<String, Object> availability) {
        return getChildMap(availability, "rules");
    }

    static List<String> nodeTypesOf(Map<String, Object> availability) {
        return getChildList(availability, "nodeTypes");
    }

    static List<String> allowedNodeTypesOf(Map<String, Object> datasource) {
        return getChildList(datasource, "allowedNodeTypes");
    }

    // --- Generic access ---

    // The map under parent.key, added if missing. The value type is the
    // caller's: the definition is raw YAML
    @SuppressWarnings("unchecked")
    private static <V> Map<String, V> getChildMap(Map<String, Object> parent, String key) {
        if (!(parent.get(key) instanceof Map<?, ?>)) {
            parent.put(key, new LinkedHashMap<>());
        }
        return (Map<String, V>) parent.get(key);
    }

    // The list under parent.key, added if missing
    @SuppressWarnings("unchecked")
    private static List<String> getChildList(Map<String, Object> parent, String key) {
        if (!(parent.get(key) instanceof List<?>)) {
            parent.put(key, new ArrayList<>());
        }
        return (List<String>) parent.get(key);
    }
}
