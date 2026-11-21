package com.giufaz.magnolia.localevariants.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Module config entry {@code defaults}, for every enabled app:
 * <ul>
 * <li>the browser actions allowed on locale variants and on variants
 * containers (each app can add its own or replace them, see
 * {@link LocaleVariantsAppConfig});</li>
 * <li>{@code recursiveRestore}: whether "Restore previous version" also
 * restores the nodes under the restored one (the variants container and its
 * variants).</li>
 * </ul>
 */
public class ActionDefaults {

    private List<String> variantActions = new ArrayList<>();
    private List<String> containerActions = new ArrayList<>();
    private boolean recursiveRestore = true;

    public List<String> getVariantActions() {
        return variantActions;
    }

    public void setVariantActions(List<String> variantActions) {
        this.variantActions = variantActions == null ? new ArrayList<>() : variantActions;
    }

    public List<String> getContainerActions() {
        return containerActions;
    }

    public void setContainerActions(List<String> containerActions) {
        this.containerActions = containerActions == null ? new ArrayList<>() : containerActions;
    }

    public boolean isRecursiveRestore() {
        return recursiveRestore;
    }

    public void setRecursiveRestore(boolean recursiveRestore) {
        this.recursiveRestore = recursiveRestore;
    }

}
