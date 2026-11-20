package com.giufaz.magnolia.localevariants.app.activation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Module config entry {@code apps.<appName>}: a content app with locale
 * variants.
 * <ul>
 * <li>{@code enabled} (default {@code true}): {@code false} leaves the app as
 * it is, as if it weren't listed;</li>
 * <li>{@code nodeTypes} (required, at least one): the item types that can have
 * variants; without it the app is left as it is.</li>
 * </ul>
 * A new field must be added to {@link #equals} and {@link #hashCode} too: an
 * app is decorated again only when its config changes.
 */
public class LocaleVariantsAppConfig {

    private boolean enabled = true;
    private List<String> nodeTypes = new ArrayList<>();

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

    @Override
    public boolean equals(Object other) {
        return other instanceof LocaleVariantsAppConfig config
                && enabled == config.enabled
                && nodeTypes.equals(config.nodeTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, nodeTypes);
    }

    @Override
    public String toString() {
        return "LocaleVariantsAppConfig" + nodeTypes;
    }
}
