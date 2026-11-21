package com.giufaz.magnolia.localevariants.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Module config entry {@code defaults}: the browser actions allowed on locale
 * variants and on variants containers, for every enabled app (each app can add
 * its own or replace them, see {@link LocaleVariantsAppConfig}).
 */
public class ActionDefaults {

    private List<String> variantActions = new ArrayList<>();
    private List<String> containerActions = new ArrayList<>();

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

}
