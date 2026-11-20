package com.giufaz.magnolia.localevariants.app.create;

import info.magnolia.ui.field.ConfiguredFieldDefinition;

import java.util.Locale;
import java.util.Set;

/**
 * Field of the "Add language variants" dialog: one checkbox per locale of the
 * selected item's site (see {@link LocaleVariantLocalesFieldFactory}).
 */
public class LocaleVariantLocalesFieldDefinition extends ConfiguredFieldDefinition<Set<Locale>> {

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public LocaleVariantLocalesFieldDefinition() {
        setType(Set.class);
        setFactoryClass((Class) LocaleVariantLocalesFieldFactory.class);
    }
}
