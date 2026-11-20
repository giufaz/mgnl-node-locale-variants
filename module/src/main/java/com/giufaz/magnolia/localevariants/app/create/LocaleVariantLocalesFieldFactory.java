package com.giufaz.magnolia.localevariants.app.create;

import info.magnolia.context.MgnlContext;
import info.magnolia.i18nsystem.SimpleTranslator;
import info.magnolia.objectfactory.ComponentProvider;
import info.magnolia.ui.ValueContext;
import info.magnolia.ui.field.factory.AbstractFieldFactory;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.giufaz.magnolia.localevariants.LocaleVariantStructure;
import com.vaadin.ui.CheckBoxGroup;
import com.vaadin.ui.Component;

import jakarta.inject.Inject;

/**
 * One checkbox per locale of the selected item's site. The default locale (the
 * original) and the locales with a variant are checked and disabled; the
 * others can be checked, several at once. The locked ones stay checked
 * whatever the form does (defaults, reset).
 */
public class LocaleVariantLocalesFieldFactory
        extends AbstractFieldFactory<Set<Locale>, LocaleVariantLocalesFieldDefinition> {

    private static final Logger log = LoggerFactory.getLogger(LocaleVariantLocalesFieldFactory.class);

    private final ValueContext<Node> valueContext;
    private final LocaleVariantStructure variantStructure;
    private final SimpleTranslator i18n;

    @Inject
    public LocaleVariantLocalesFieldFactory(LocaleVariantLocalesFieldDefinition definition,
            ComponentProvider componentProvider, ValueContext<Node> valueContext,
            LocaleVariantStructure variantStructure, SimpleTranslator i18n) {
        super(definition, componentProvider);
        this.valueContext = valueContext;
        this.variantStructure = variantStructure;
        this.i18n = i18n;
    }

    @Override
    protected Component createFieldComponent() {
        CheckBoxGroup<Locale> group = new CheckBoxGroup<>();
        Node original = valueContext.getSingle().orElse(null);
        if (original == null) {
            return group;
        }
        Locale defaultLocale = variantStructure.getSiteI18n(original).getDefaultLocale();
        List<Locale> variantLocales = variantStructure.getSupportedVariantLocales(original);
        Set<Locale> all = new LinkedHashSet<>(); // all locales to show in the dialog
        // Locale variants already created, to be shown as locked in the dialog
        Set<Locale> locked = new HashSet<>();
        if (defaultLocale != null) {
            all.add(defaultLocale);
            locked.add(defaultLocale);
        }
        all.addAll(variantLocales);
        variantLocales.stream().filter(locale -> exists(original, locale)).forEach(locked::add);

        Locale userLocale = MgnlContext.getLocale();
        group.setItems(all);
        group.setItemCaptionGenerator(
                locale -> locale.getDisplayName(userLocale) + suffix(locale, defaultLocale, locked));
        group.setItemEnabledProvider(locale -> !locked.contains(locale));
        group.setValue(locked); // locked locales load as "selected"
        group.addValueChangeListener(event -> {
            if (!event.getValue().containsAll(locked)) {
                Set<Locale> value = new HashSet<>(event.getValue());
                value.addAll(locked);
                group.setValue(value);
            }
        });
        return group;
    }

    private String suffix(Locale locale, Locale defaultLocale, Set<Locale> locked) {
        if (locale.equals(defaultLocale)) {
            return " — " + i18n.translate("node-locale-variants.dialog.locales.default");
        }
        return locked.contains(locale) ? " — " + i18n.translate("node-locale-variants.dialog.locales.exists") : "";
    }

    private boolean exists(Node original, Locale locale) {
        try {
            return variantStructure.hasVariant(original, locale);
        } catch (RepositoryException e) {
            log.warn("Can't read the locale variants of {}, not offering {}", original, locale, e);
            return true; // not offered
        }
    }
}
