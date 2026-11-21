package com.giufaz.magnolia.localevariants.app.guard;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import info.magnolia.ui.api.availability.AvailabilityDefinition;

import java.util.List;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.giufaz.magnolia.localevariants.LocaleVariantStructure;
import com.giufaz.magnolia.localevariants.app.activation.AppActivation;
import com.giufaz.magnolia.localevariants.config.ResolvedLocaleVariantsAppConfig;

class LocaleVariantRuleTest {

    private final LocaleVariantStructure variantStructure = mock(LocaleVariantStructure.class);
    private final AppActivation appActivation = mock(AppActivation.class);

    private final Node item = mock(Node.class);
    private final Node variant = mock(Node.class);
    private final Node container = mock(Node.class);
    private final Node broken = mock(Node.class);

    // The app allows editItem on variants and activate on containers
    @BeforeEach
    void setUp() throws RepositoryException {
        when(variantStructure.isValidVariant(variant)).thenReturn(true);
        when(variantStructure.isVariantsContainer(container)).thenReturn(true);
        when(variantStructure.isValidVariant(broken)).thenThrow(new RepositoryException("simulated"));
        when(variantStructure.isVariantsContainer(broken)).thenThrow(new RepositoryException("simulated"));
        when(appActivation.getAppliedApp("promos")).thenReturn(new ResolvedLocaleVariantsAppConfig("promos",
                List.of("lvd:promo"), List.of("editItem"), List.of("activate")));
    }

    @Test
    void test_definition() {
        assertEquals(LocaleVariantRule.class, new LocaleVariantRuleDefinition().getImplementationClass());
        assertFalse(new LocaleVariantRuleDefinition().getNegate());
    }

    // target: variant | container → the item is one
    @Test
    void test_target() {
        assertTrue(isAvailable(targetRule("variant", false), variant));
        assertFalse(isAvailable(targetRule("variant", false), container));
        assertFalse(isAvailable(targetRule("variant", false), item));

        assertTrue(isAvailable(targetRule("container", false), container));
        assertFalse(isAvailable(targetRule("container", false), variant));
        assertFalse(isAvailable(targetRule("container", false), item));
    }

    // Negated, as on the guardrail actions: everywhere but on the target
    @Test
    void test_target_negated() {
        assertFalse(isAvailable(targetRule("variant", true), variant));
        assertTrue(isAvailable(targetRule("variant", true), container));
        assertTrue(isAvailable(targetRule("variant", true), item));

        assertFalse(isAvailable(targetRule("container", true), container));
        assertTrue(isAvailable(targetRule("container", true), variant));
        assertTrue(isAvailable(targetRule("container", true), item));
    }

    // No target: variant, as the definition says
    @Test
    void test_target_default() {
        assertTrue(isAvailable(targetRule(null, false), variant));
        assertFalse(isAvailable(targetRule(null, false), container));
    }

    // action + app: on variants and containers, the app's action lists decide
    @Test
    void test_action() {
        assertTrue(isAvailable(actionRule("promos", "editItem"), variant));
        assertFalse(isAvailable(actionRule("promos", "editItem"), container), "allowed on variants only");

        assertTrue(isAvailable(actionRule("promos", "activate"), container));
        assertFalse(isAvailable(actionRule("promos", "activate"), variant), "allowed on containers only");

        assertFalse(isAvailable(actionRule("promos", "notListed"), variant));
        assertFalse(isAvailable(actionRule("promos", "notListed"), container));
    }

    // Other items pass, whatever the lists: the rule only guards variants and containers
    @Test
    void test_action_otherItems() {
        assertTrue(isAvailable(actionRule("promos", "notListed"), item));
        assertTrue(isAvailable(actionRule("notApplied", "notListed"), item));
        verifyNoInteractions(appActivation);
    }

    // The lists are read at every check: a config change applies to the same rule
    @Test
    void test_action_configChange() {
        LocaleVariantRule rule = rule(actionRule("promos", "export"));
        assertFalse(rule.isAvailable(List.of(variant)));

        when(appActivation.getAppliedApp("promos")).thenReturn(new ResolvedLocaleVariantsAppConfig("promos",
                List.of("lvd:promo"), List.of("editItem", "export"), List.of()));

        assertTrue(rule.isAvailable(List.of(variant)));
    }

    // The app is no longer in the module config (its definition is about to be
    // rebuilt): nothing is allowed on variants and containers
    @Test
    void test_action_appNotApplied() {
        assertFalse(isAvailable(actionRule("notApplied", "editItem"), variant));
        assertFalse(isAvailable(actionRule("notApplied", "activate"), container));
    }

    // All the selected items must pass
    @Test
    void test_severalItems() {
        assertTrue(rule(actionRule("promos", "editItem")).isAvailable(List.of(item, variant)));
        assertFalse(rule(actionRule("promos", "editItem")).isAvailable(List.of(item, variant, container)));
        assertFalse(rule(targetRule("variant", true)).isAvailable(List.of(item, variant)));
    }

    // Not JCR nodes: neither variants nor containers
    @Test
    void test_notANode() {
        Property property = mock(Property.class);

        assertFalse(isAvailable(targetRule("variant", false), property));
        assertTrue(isAvailable(targetRule("variant", true), property));
        assertTrue(isAvailable(actionRule("promos", "notListed"), property));
        assertTrue(isAvailable(actionRule("promos", "notListed"), "an item of another datasource"));
    }

    // Fail-closed: when the repository can't tell what the item is, the rule
    // doesn't pass, negated or not
    @Test
    void test_repositoryError() {
        assertFalse(isAvailable(targetRule("variant", false), broken));
        assertFalse(isAvailable(targetRule("variant", true), broken), "negated: the guardrail actions stay off");
        assertFalse(isAvailable(targetRule("container", false), broken));
        assertFalse(isAvailable(targetRule("container", true), broken), "negated");
        assertFalse(isAvailable(actionRule("promos", "editItem"), broken));
    }

    private boolean isAvailable(LocaleVariantRuleDefinition definition, Object selected) {
        return rule(definition).isAvailable(List.of(selected));
    }

    private LocaleVariantRule rule(LocaleVariantRuleDefinition definition) {
        return new LocaleVariantRule(mock(AvailabilityDefinition.class), definition, variantStructure, appActivation);
    }

    private static LocaleVariantRuleDefinition targetRule(String target, boolean negate) {
        LocaleVariantRuleDefinition definition = new LocaleVariantRuleDefinition();
        definition.setTarget(target);
        definition.setNegate(negate);
        return definition;
    }

    private static LocaleVariantRuleDefinition actionRule(String app, String action) {
        LocaleVariantRuleDefinition definition = new LocaleVariantRuleDefinition();
        definition.setApp(app);
        definition.setAction(action);
        return definition;
    }
}
