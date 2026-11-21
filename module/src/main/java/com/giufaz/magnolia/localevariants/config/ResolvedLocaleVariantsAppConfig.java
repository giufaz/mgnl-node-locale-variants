package com.giufaz.magnolia.localevariants.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * An app with locale variants as the module applies it: the config of an
 * eligible app ({@link LocaleVariantsAppConfig}) with its action lists
 * and its other settings resolved against the module config {@code defaults}.
 * Immutable: rebuilt at every module start.
 *
 * <p>
 * The guardrail actions (copy, move, duplicate, rename, paste,
 * {@code addLocaleVariant}) are never allowed, whatever the lists: see
 * {@link com.giufaz.magnolia.localevariants.app.activation.AppDefinitionTransformer}.
 *
 * @param name             the app name
 * @param nodeTypes        the item types that can have variants
 * @param variantActions   the browser actions allowed on its variants
 * @param containerActions the browser actions allowed on its variants
 *                         containers, in the order of the config
 * @param recursiveRestore whether its "Restore previous version" also restores
 *                         the nodes under the restored one
 */
public record ResolvedLocaleVariantsAppConfig(String name, List<String> nodeTypes, List<String> variantActions,
        List<String> containerActions, boolean recursiveRestore) {

    public ResolvedLocaleVariantsAppConfig {
        nodeTypes = List.copyOf(nodeTypes);
        variantActions = List.copyOf(variantActions);
        containerActions = List.copyOf(containerActions);
    }

    public static ResolvedLocaleVariantsAppConfig of(String name, LocaleVariantsAppConfig config,
            ActionDefaults defaults) {
        return new ResolvedLocaleVariantsAppConfig(name, config.getNodeTypes(),
                resolve(defaults.getVariantActions(), config.getVariantActions(),
                        config.isReplaceDefaultVariantActions()),
                resolve(defaults.getContainerActions(), config.getContainerActions(),
                        config.isReplaceDefaultContainerActions()),
                defaults.isRecursiveRestore());
    }

    // The app's own list added to the defaults, or replacing them (even empty)
    static List<String> resolve(List<String> defaults, List<String> own, boolean replaceDefaults) {
        if (replaceDefaults) {
            return own;
        }
        LinkedHashSet<String> merged = new LinkedHashSet<>(defaults);
        merged.addAll(own);
        return new ArrayList<>(merged);
    }

    public boolean allowsOnVariant(String action) {
        return variantActions.contains(action);
    }

    public boolean allowsOnContainer(String action) {
        return containerActions.contains(action);
    }
}
