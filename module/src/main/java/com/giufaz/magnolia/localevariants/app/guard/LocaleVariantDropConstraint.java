package com.giufaz.magnolia.localevariants.app.guard;

import info.magnolia.objectfactory.ComponentProvider;
import info.magnolia.ui.contentapp.browser.drop.DropConstraint;
import info.magnolia.ui.contentapp.browser.drop.DropConstraintDefinition;

import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import com.giufaz.magnolia.localevariants.LocaleVariantStructure;
import com.vaadin.shared.ui.grid.DropLocation;

import jakarta.inject.Inject;

/**
 * Drag and drop: variants and variants containers can't be dragged, and
 * nothing can be dropped on or next to them; the rest goes to the tree's own
 * constraint.
 */
public class LocaleVariantDropConstraint implements DropConstraint<Item> {

    private final LocaleVariantStructure variantStructure;
    private final DropConstraint<Item> delegate;

    @Inject
    public LocaleVariantDropConstraint(LocaleVariantDropConstraintDefinition definition,
            LocaleVariantStructure variantStructure, ComponentProvider componentProvider) {
        this.variantStructure = variantStructure;
        this.delegate = newDelegate(definition.getDelegate(), componentProvider);
    }

    // The tree's own constraint, or null if it has none
    @SuppressWarnings("unchecked")
    private static DropConstraint<Item> newDelegate(DropConstraintDefinition definition,
            ComponentProvider componentProvider) {
        if (definition == null || definition.getImplementationClass() == null) {
            return null;
        }
        return (DropConstraint<Item>) componentProvider.newInstance(definition.getImplementationClass(), definition);
    }

    @Override
    public boolean isAllowedAt(Item source, Item target, DropLocation location) {
        if (isVariantOrContainer(source) || isVariantOrContainer(target)) {
            return false;
        }
        // No constraint of its own: Magnolia gives the tree no drag and drop
        return delegate != null && delegate.isAllowedAt(source, target, location);
    }

    private boolean isVariantOrContainer(Item item) {
        try {
            return item instanceof Node node
                    && (variantStructure.isValidVariant(node) || variantStructure.isVariantsContainer(node));
        } catch (RepositoryException e) {
            return true; // fail-closed: no drop
        }
    }
}
