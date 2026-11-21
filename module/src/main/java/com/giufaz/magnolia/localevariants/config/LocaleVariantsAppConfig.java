package com.giufaz.magnolia.localevariants.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Module config entry {@code apps.<appName>}: a content app with locale
 * variants.
 * <ul>
 * <li>{@code enabled} (default {@code true}): {@code false} leaves the app as
 * it is, as if it weren't listed;</li>
 * <li>{@code nodeTypes} (required, at least one): the item types that can have
 * variants; without it the app is left as it is;</li>
 * <li>{@code variantActions} / {@code containerActions} (optional): browser
 * actions allowed on this app's variants / variants containers, added to the
 * module config {@code defaults};</li>
 * <li>{@code replaceDefaultVariantActions} /
 * {@code replaceDefaultContainerActions} (optional, default {@code false}):
 * {@code true} = exactly this app's list instead of the defaults, even
 * empty.</li>
 * </ul>
 */
public class LocaleVariantsAppConfig {

    private boolean enabled = true;
    private List<String> nodeTypes = new ArrayList<>();
    private List<String> variantActions = new ArrayList<>();
    private boolean replaceDefaultVariantActions;
    private List<String> containerActions = new ArrayList<>();
    private boolean replaceDefaultContainerActions;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getNodeTypes() {
        return nodeTypes;
    }

    public void setNodeTypes(List<String> nodeTypes) {
        this.nodeTypes = nodeTypes == null ? new ArrayList<>() : nodeTypes;
    }

    public List<String> getVariantActions() {
        return variantActions;
    }

    public void setVariantActions(List<String> variantActions) {
        this.variantActions = variantActions == null ? new ArrayList<>() : variantActions;
    }

    public boolean isReplaceDefaultVariantActions() {
        return replaceDefaultVariantActions;
    }

    public void setReplaceDefaultVariantActions(boolean replaceDefaultVariantActions) {
        this.replaceDefaultVariantActions = replaceDefaultVariantActions;
    }

    public List<String> getContainerActions() {
        return containerActions;
    }

    public void setContainerActions(List<String> containerActions) {
        this.containerActions = containerActions == null ? new ArrayList<>() : containerActions;
    }

    public boolean isReplaceDefaultContainerActions() {
        return replaceDefaultContainerActions;
    }

    public void setReplaceDefaultContainerActions(boolean replaceDefaultContainerActions) {
        this.replaceDefaultContainerActions = replaceDefaultContainerActions;
    }


    @Override
    public String toString() {
        return "LocaleVariantsAppConfig" + nodeTypes;
    }
}
