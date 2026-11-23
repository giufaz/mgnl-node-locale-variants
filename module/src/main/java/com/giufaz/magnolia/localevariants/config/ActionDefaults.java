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
 * variants);</li>
 * <li>{@code experimentalVariantDialogs}: whether the Vaadin dialogs and the
 * page editor show a variant, and the nodes under it, in one language (see
 * {@link com.giufaz.magnolia.localevariants.app.form.LocaleVariantI18nAuthoringSupport}).
 * Exploratory, off by default: turn it on only to try it out.</li>
 * </ul>
 */
public class ActionDefaults {

    private List<String> variantActions = new ArrayList<>();
    private List<String> containerActions = new ArrayList<>();
    private boolean recursiveRestore = true;
    private boolean experimentalVariantDialogs;

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

    public boolean isExperimentalVariantDialogs() {
        return experimentalVariantDialogs;
    }

    public void setExperimentalVariantDialogs(boolean experimentalVariantDialogs) {
        this.experimentalVariantDialogs = experimentalVariantDialogs;
    }

}
