package com.giufaz.magnolia.localevariants.app.activation;

import static com.giufaz.magnolia.localevariants.LocaleVariantStructure.VARIANTS_CONTAINER_TYPE;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.giufaz.magnolia.localevariants.app.create.AddLocaleVariantActionDefinition;

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
 * for all configured node types (except for deleted nodes).</li>
 * </ul>
 * <p>
 * The source map is never changed: the result is a copy.
 */
public class AppDefinitionTransformer {

    public static final String ADD_ACTION = "addLocaleVariant";

    public Map<String, Object> transform(Map<String, Object> rawApp, LocaleVariantsAppConfig app) {
        Map<String, Object> appToDecorate = deepCopy(rawApp);
        Set<String> types = Set.copyOf(app.getNodeTypes());
        addContainerType(appToDecorate.get("datasource"));
        for (Map<String, Object> subApp : namedMaps(appToDecorate.get("subApps")).values()) {
            if (isBrowserSubApp(subApp)) {
                addContainerType(subApp.get("datasource"));

                // Create addLocaleVariant action and place it in the apps actions, and
                // actionbar
                Map<String, Map<String, Object>> sections = namedMaps(
                        ((Map<?, ?>) subApp.get("actionbar")).get("sections"));
                addNamedIfAbsent(subApp, "actions", ADD_ACTION, buildAddVariantActionDefinition(types));
                findSectionsRelevantForNodes(sections, types).forEach(this::placeAddVariantActionInSection);
            }
        }
        return appToDecorate;
    }

    private boolean isBrowserSubApp(Map<String, Object> subApp) {
        return subApp.get("actionbar") instanceof Map<?, ?> && subApp.get("workbench") != null;
    }

    private void addContainerType(Object datasource) {
        // allowedNodeTypes missing or empty: no restriction, every type is already
        // shown
        if (!(datasource instanceof Map<?, ?> ds) || values(ds.get("allowedNodeTypes")).isEmpty()) {
            return;
        }
        addTo(ds.get("allowedNodeTypes"), VARIANTS_CONTAINER_TYPE);
    }

    private Map<String, Object> buildAddVariantActionDefinition(Set<String> types) {
        Map<String, Object> notDeleted = new LinkedHashMap<>();
        notDeleted.put("name", "notDeleted");
        notDeleted.put("$type", "jcrIsDeletedRule");
        notDeleted.put("negate", true);
        Map<String, Object> availability = new LinkedHashMap<>();
        availability.put("writePermissionRequired", true);
        availability.put("nodeTypes", new ArrayList<>(types));
        availability.put("rules", new ArrayList<>(List.of(notDeleted)));
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("class", AddLocaleVariantActionDefinition.class.getName());
        action.put("availability", availability);
        return action;
    }

    private List<Map<String, Object>> findSectionsRelevantForNodes(Map<String, Map<String, Object>> sections,
            Set<String> nodeTypes) {
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
        Object rules = section.get("availability") instanceof Map<?, ?> a ? a.get("rules") : null;
        return namedMaps(rules).values().stream()
                .anyMatch(rule -> ("jcrIsDeletedRule".equals(rule.get("$type"))
                        || String.valueOf(rule.get("class")).endsWith(".JcrIsDeletedRuleDefinition"))
                        && !Boolean.parseBoolean(String.valueOf(rule.get("negate"))));
    }

    private List<String> sectionNodeTypes(Map<String, Object> section) {
        return values(section.get("availability") instanceof Map<?, ?> a ? a.get("nodeTypes") : null);
    }

    // In the "addActions" group, as content-types apps name it, else in the
    // first group
    private void placeAddVariantActionInSection(Map<String, Object> section) {
        Map<String, Map<String, Object>> groups = namedMaps(section.get("groups"));
        Map<String, Object> group = groups.containsKey("addActions") ? groups.get("addActions")
                : groups.values().stream().findFirst().orElse(null);
        if (group != null) {
            addNamedIfAbsent(group, "items", ADD_ACTION, new LinkedHashMap<>());
        }
    }

    // Adds a named item under parent.key, unless one with that name is there:
    // to a map ({name: {...}}) or to a list ([{name: ..., ...}]), as the app
    // wrote it; a missing key becomes a map
    private static void addNamedIfAbsent(Map<String, Object> parent, String key, String name,
            Map<String, Object> item) {
        Object items = parent.get(key);
        if (namedMaps(items).containsKey(name)) {
            return;
        }
        item.put("name", name);
        if (items instanceof List<?>) {
            addTo(items, item);
        } else if (items instanceof Map<?, ?> map) {
            cast(map).put(name, item);
        } else {
            Map<String, Object> created = new LinkedHashMap<>();
            created.put(name, item);
            parent.put(key, created);
        }
    }

    // Appends a value to a list, or to a map (key derived from the value)
    @SuppressWarnings("unchecked")
    private static void addTo(Object items, Object value) {
        if (items instanceof List<?> list && !list.contains(value)) {
            ((List<Object>) list).add(value);
        } else if (items instanceof Map<?, ?> map && !map.containsValue(value)) {
            cast(map).put(String.valueOf(value).replace(':', '-'), value);
        }
    }

    // Named children of a map ({key: {...}}) or of a list ([{name: key, ...}]),
    // in order; same instances
    @SuppressWarnings("unchecked")
    static Map<String, Map<String, Object>> namedMaps(Object node) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        if (node instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                if (value instanceof Map<?, ?> child) {
                    result.put(String.valueOf(key), (Map<String, Object>) child);
                }
            });
        } else if (node instanceof List<?> list) {
            for (Object value : list) {
                if (value instanceof Map<?, ?> child && child.get("name") != null) {
                    result.put(String.valueOf(child.get("name")), (Map<String, Object>) child);
                }
            }
        }
        return result;
    }

    // Plain string values of a list or map (nodeTypes, allowedNodeTypes)
    private static List<String> values(Object node) {
        List<?> raw = node instanceof Map<?, ?> map ? new ArrayList<>(map.values())
                : node instanceof List<?> list ? list : List.of();
        return raw.stream().filter(Objects::nonNull).map(String::valueOf).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    // YAML anchors share instances, so copy before changing anything
    @SuppressWarnings("unchecked")
    static <T> T deepCopy(T node) {
        if (node instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, value) -> copy.put(key, deepCopy(value)));
            return (T) copy;
        }
        if (node instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(value -> copy.add(deepCopy(value)));
            return (T) copy;
        }
        return node;
    }
}
