package com.giufaz.magnolia.localevariants.app.guard;

import info.magnolia.ui.contentapp.browser.drop.DropConstraintDefinition;

/**
 * Wraps the tree's own drop constraint ({@code delegate}): see
 * {@link LocaleVariantDropConstraint}.
 */
public class LocaleVariantDropConstraintDefinition extends DropConstraintDefinition {

    private DropConstraintDefinition delegate;

    public LocaleVariantDropConstraintDefinition() {
        setImplementationClass(LocaleVariantDropConstraint.class);
    }

    public DropConstraintDefinition getDelegate() {
        return delegate;
    }

    public void setDelegate(DropConstraintDefinition delegate) {
        this.delegate = delegate;
    }
}
