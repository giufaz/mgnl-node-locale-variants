package com.giufaz.magnolia.localevariants.app.form;

import info.magnolia.objectfactory.ComponentProvider;
import info.magnolia.objectfactory.guice.GuiceComponentProvider;
import info.magnolia.ui.api.app.SubAppContext;
import info.magnolia.ui.api.i18n.I18NAuthoringSupport;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.giufaz.magnolia.localevariants.LocaleVariantStructure;
import com.giufaz.magnolia.localevariants.app.activation.AppActivation;
import com.giufaz.magnolia.localevariants.config.ResolvedLocaleVariantsAppConfig;

import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Exploratory, off by default ({@code defaults.experimentalVariantDialogs} of
 * the module config): locale variants authored in one language in the Vaadin
 * dialogs (e.g. opened by an {@code openDialog} action) and in the page
 * editor, which have no detail sub-app to give a twin form to. Known limits:
 * after a variant's dialog, the original's dialog opens in the variant's
 * language; a dialog with a node-name field renames the variant on save.
 *
 * <p>
 * For a variant, or a node under one (areas, components): one available
 * locale, the variant's own, and always the base property names
 * ({@code title}, never {@code title_de}). Any other node goes to Magnolia's
 * own authoring support (the one mapped in the {@code main} component
 * container), unchanged.
 *
 * <p>
 * Mapped in the {@code subapp} component container, so every sub-app gets it,
 * but it only acts in the apps with locale variants applied, and only while
 * the flag is on (checked at every call: a config change applies at once).
 */
public class LocaleVariantI18nAuthoringSupport implements I18NAuthoringSupport<Object> {

    private static final Logger log = LoggerFactory.getLogger(LocaleVariantI18nAuthoringSupport.class);

    private final I18NAuthoringSupport<Object> delegate;
    private final LocaleVariantStructure variantStructure;
    private final BooleanSupplier oneLanguageInDialogs;

    @Inject
    @SuppressWarnings("unchecked")
    public LocaleVariantI18nAuthoringSupport(@Named("main") GuiceComponentProvider mainComponentProvider,
            LocaleVariantStructure variantStructure, ComponentProvider componentProvider,
            AppActivation appActivation) {
        this(mainComponentProvider.getComponent(I18NAuthoringSupport.class), variantStructure,
                oneLanguageInDialogs(componentProvider, appActivation));
    }

    LocaleVariantI18nAuthoringSupport(I18NAuthoringSupport<Object> delegate, LocaleVariantStructure variantStructure,
            BooleanSupplier oneLanguageInDialogs) {
        this.delegate = delegate;
        this.variantStructure = variantStructure;
        this.oneLanguageInDialogs = oneLanguageInDialogs;
    }

    // The app of this sub-app is resolved once, its config is read at every call
    static BooleanSupplier oneLanguageInDialogs(ComponentProvider componentProvider, AppActivation appActivation) {
        String appName = appName(componentProvider);
        return () -> {
            ResolvedLocaleVariantsAppConfig app = appName == null ? null : appActivation.getAppliedApp(appName);
            return app != null && app.experimentalVariantDialogs();
        };
    }

    private static String appName(ComponentProvider componentProvider) {
        try {
            return componentProvider.getComponent(SubAppContext.class).getAppContext().getName();
        } catch (RuntimeException e) {
            log.warn("[node-locale-variants] Can't tell the app of this sub-app: locale variants authored as any node",
                    e);
            return null;
        }
    }

    @Override
    public List<Locale> getAvailableLocales(Object item) {
        // A list that accepts contains(null): Magnolia checks the current locale,
        // which may be null
        return variantLocale(item).map(Collections::singletonList).orElseGet(() -> delegate.getAvailableLocales(item));
    }

    @Override
    public Locale getDefaultLocale(Object item) {
        return variantLocale(item).orElseGet(() -> delegate.getDefaultLocale(item));
    }

    @Override
    public String deriveLocalisedPropertyName(Object item, String base, Locale locale) {
        return variantLocale(item).isPresent() ? base : delegate.deriveLocalisedPropertyName(item, base, locale);
    }

    @Override
    public boolean isDefaultLocale(Locale locale, Object item) {
        return variantLocale(item).map(own -> own.equals(locale))
                .orElseGet(() -> delegate.isDefaultLocale(locale, item));
    }

    // The locale of the variant the item is, or is in, if any
    Optional<Locale> variantLocale(Object item) {
        if (!(item instanceof Node node) || !oneLanguageInDialogs.getAsBoolean()) {
            return Optional.empty();
        }
        try {
            for (Node current = node; current.getDepth() > 0; current = current.getParent()) {
                if (variantStructure.isValidVariant(current)) {
                    return Optional.of(variantStructure.getVariantLocale(current));
                }
            }
        } catch (RepositoryException | RuntimeException e) {
            log.warn("[node-locale-variants] Can't tell whether {} is in a locale variant: authored as any node", node,
                    e);
        }
        return Optional.empty();
    }
}
