package com.giufaz.magnolia.localevariants.app.guard;

import info.magnolia.ui.api.availability.ConfiguredAvailabilityRuleDefinition;

/**
 * Availability rule on locale variants and variants containers, added by the
 * decorated app to its action bar sections and browser actions. Referenced by
 * class, so no alias registration is needed.
 * <ul>
 * <li>{@code target: variant | container}, no {@code action}: the item is a
 * variant / a variants container (negate it to keep an action off them);</li>
 * <li>{@code action} and {@code app}: the action is allowed on variants and
 * containers by the module's action lists, read at every check; other items
 * pass.</li>
 * </ul>
 */
public class LocaleVariantRuleDefinition extends ConfiguredAvailabilityRuleDefinition {

    public static final String VARIANT = "variant";
    public static final String CONTAINER = "container";

    private String target;
    private String action;
    private String app;

    public LocaleVariantRuleDefinition() {
        setImplementationClass(LocaleVariantRule.class);
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }
}
