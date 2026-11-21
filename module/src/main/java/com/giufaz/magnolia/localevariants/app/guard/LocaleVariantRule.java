package com.giufaz.magnolia.localevariants.app.guard;

import static com.giufaz.magnolia.localevariants.app.guard.LocaleVariantRuleDefinition.CONTAINER;

import info.magnolia.ui.api.availability.AvailabilityDefinition;
import info.magnolia.ui.availability.rule.AbstractAvailabilityRule;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.giufaz.magnolia.localevariants.LocaleVariantStructure;
import com.giufaz.magnolia.localevariants.app.activation.AppActivation;
import com.giufaz.magnolia.localevariants.config.ResolvedLocaleVariantsAppConfig;

import jakarta.inject.Inject;

/**
 * See {@link LocaleVariantRuleDefinition}. JCR items only: other items are
 * neither variants nor containers.
 */
public class LocaleVariantRule extends AbstractAvailabilityRule<Object, LocaleVariantRuleDefinition> {

    private static final Logger log = LoggerFactory.getLogger(LocaleVariantRule.class);

    private final LocaleVariantStructure variantStructure;
    private final AppActivation appActivation;

    @Inject
    public LocaleVariantRule(AvailabilityDefinition availabilityDefinition, LocaleVariantRuleDefinition ruleDefinition,
            LocaleVariantStructure variantStructure, AppActivation appActivation) {
        super(availabilityDefinition, ruleDefinition);
        this.variantStructure = variantStructure;
        this.appActivation = appActivation;
    }

    @Override
    protected boolean isAvailableFor(Object item) {
        try {
            return getRuleDefinition().getAction() == null ? isTarget(item) : isActionAllowedOn(item);
        } catch (RepositoryException e) {
            log.warn("Can't tell whether {} is a locale variant or a variants container: not available", item, e);
            // Fail-closed: Magnolia negates the result, so this is "not available"
            // for a negated rule too
            return getRuleDefinition().getNegate();
        }
    }

    // The item is what the rule targets: a variant, or a variants container
    private boolean isTarget(Object item) throws RepositoryException {
        return CONTAINER.equals(getRuleDefinition().getTarget()) ? isContainer(item) : isVariant(item);
    }

    // On variants and containers, the app's action lists decide; other items pass
    private boolean isActionAllowedOn(Object item) throws RepositoryException {
        boolean variant = isVariant(item);
        if (!variant && !isContainer(item)) {
            return true;
        }
        // Read at every check: a change of the module config applies as soon as
        // the apps are decorated again
        ResolvedLocaleVariantsAppConfig app = appActivation.getAppliedApp(getRuleDefinition().getApp());
        if (app == null) {
            return false; // no longer in the module config: nothing allowed
        }
        String action = getRuleDefinition().getAction();
        return variant ? app.allowsOnVariant(action) : app.allowsOnContainer(action);
    }

    private boolean isVariant(Object item) throws RepositoryException {
        return item instanceof Node node && variantStructure.isValidVariant(node);
    }

    private boolean isContainer(Object item) throws RepositoryException {
        return item instanceof Node node && variantStructure.isVariantsContainer(node);
    }
}
