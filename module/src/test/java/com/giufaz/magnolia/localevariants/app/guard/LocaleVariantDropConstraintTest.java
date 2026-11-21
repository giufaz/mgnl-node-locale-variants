package com.giufaz.magnolia.localevariants.app.guard;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import info.magnolia.objectfactory.ComponentProvider;
import info.magnolia.ui.contentapp.browser.drop.DropConstraint;
import info.magnolia.ui.contentapp.browser.drop.DropConstraintDefinition;

import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.giufaz.magnolia.localevariants.LocaleVariantStructure;
import com.vaadin.shared.ui.grid.DropLocation;

class LocaleVariantDropConstraintTest {

    private final LocaleVariantStructure variantStructure = mock(LocaleVariantStructure.class);
    private final ComponentProvider componentProvider = mock(ComponentProvider.class);
    @SuppressWarnings("unchecked")
    private final DropConstraint<Item> treeConstraint = mock(DropConstraint.class);
    private final DropConstraintDefinition treeConstraintDefinition = new DropConstraintDefinition();

    private final Node item = mock(Node.class);
    private final Node folder = mock(Node.class);
    private final Node variant = mock(Node.class);
    private final Node container = mock(Node.class);
    private final Node broken = mock(Node.class);

    // The tree's own constraint allows everything
    @BeforeEach
    void setUp() throws RepositoryException {
        when(variantStructure.isValidVariant(variant)).thenReturn(true);
        when(variantStructure.isVariantsContainer(container)).thenReturn(true);
        when(variantStructure.isValidVariant(broken)).thenThrow(new RepositoryException("simulated"));
        treeConstraintDefinition.setImplementationClass(treeConstraint.getClass());
        doReturn(treeConstraint).when(componentProvider).newInstance(treeConstraint.getClass(), treeConstraintDefinition);
        when(treeConstraint.isAllowedAt(any(Item.class), any(Item.class), any())).thenReturn(true);
    }

    @Test
    void test_definition() {
        assertEquals(LocaleVariantDropConstraint.class, new LocaleVariantDropConstraintDefinition().getImplementationClass());
    }

    // Variants and containers can't be dragged
    @Test
    void test_isAllowedAt_draggedVariantOrContainer() {
        LocaleVariantDropConstraint constraint = constraint(treeConstraintDefinition);

        assertFalse(constraint.isAllowedAt(variant, folder, DropLocation.ON_TOP));
        assertFalse(constraint.isAllowedAt(container, folder, DropLocation.ON_TOP));
        verifyNoInteractions(treeConstraint);
    }

    // Nothing is dropped on or next to them
    @Test
    void test_isAllowedAt_droppedOnVariantOrContainer() {
        LocaleVariantDropConstraint constraint = constraint(treeConstraintDefinition);

        for (DropLocation location : DropLocation.values()) {
            assertFalse(constraint.isAllowedAt(item, variant, location), "variant, " + location);
            assertFalse(constraint.isAllowedAt(item, container, location), "container, " + location);
        }
        verifyNoInteractions(treeConstraint);
    }

    // The rest goes to the tree's own constraint
    @Test
    void test_isAllowedAt_otherItems() {
        LocaleVariantDropConstraint constraint = constraint(treeConstraintDefinition);
        Property property = mock(Property.class);
        assertTrue(constraint.isAllowedAt(item, folder, DropLocation.ON_TOP));
        assertTrue(constraint.isAllowedAt(property, folder, DropLocation.ON_TOP), "not a node: neither variant nor container");

        when(treeConstraint.isAllowedAt(item, folder, DropLocation.BELOW)).thenReturn(false);
        assertFalse(constraint.isAllowedAt(item, folder, DropLocation.BELOW));

        verify(treeConstraint).isAllowedAt(item, folder, DropLocation.ON_TOP);
        verify(treeConstraint).isAllowedAt(property, folder, DropLocation.ON_TOP);
    }

    // Without a constraint of its own Magnolia gives the tree no drag and drop:
    // nothing can be dropped
    @Test
    void test_isAllowedAt_withoutTreeConstraint() {
        assertFalse(constraint(null).isAllowedAt(item, folder, DropLocation.ON_TOP), "no delegate");
        assertFalse(constraint(new DropConstraintDefinition()).isAllowedAt(item, folder, DropLocation.ON_TOP),
                "a delegate without implementation class");
        verify(componentProvider, never()).newInstance(any(), any());
    }

    // Fail-closed: when the repository can't tell what the item is, no drop
    @Test
    void test_isAllowedAt_repositoryError() {
        LocaleVariantDropConstraint constraint = constraint(treeConstraintDefinition);

        assertFalse(constraint.isAllowedAt(broken, folder, DropLocation.ON_TOP));
        assertFalse(constraint.isAllowedAt(item, broken, DropLocation.ON_TOP));
    }

    private LocaleVariantDropConstraint constraint(DropConstraintDefinition delegate) {
        LocaleVariantDropConstraintDefinition definition = new LocaleVariantDropConstraintDefinition();
        definition.setDelegate(delegate);
        return new LocaleVariantDropConstraint(definition, variantStructure, componentProvider);
    }
}
