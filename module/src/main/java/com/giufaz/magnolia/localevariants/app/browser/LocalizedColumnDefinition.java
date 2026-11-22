package com.giufaz.magnolia.localevariants.app.browser;

import info.magnolia.i18nsystem.SimpleTranslator;
import info.magnolia.ui.contentapp.configuration.column.AbstractSafeHtmlColumnDefinition;

import java.util.ArrayList;
import java.util.List;

import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.giufaz.magnolia.localevariants.LocaleVariantStructure;
import com.vaadin.ui.DescriptionGenerator;

import jakarta.inject.Inject;

/**
 * Browser column "Localized" of the apps with locale variants, placed after
 * the first column of every view by the app definition transformer:
 * <ul>
 * <li>an original with variants: an icon, with the variants' locales as
 * tooltip;</li>
 * <li>a variant: its locale (a variant's row shows the same name as its
 * original);</li>
 * <li>any other row: empty.</li>
 * </ul>
 */
public class LocalizedColumnDefinition extends AbstractSafeHtmlColumnDefinition<Item> {

    public static final String NAME = "lvLocalized";
    public static final String ICON = "icon-language-app";

    private static final Logger log = LoggerFactory.getLogger(LocalizedColumnDefinition.class);

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public LocalizedColumnDefinition() {
        setName(NAME);
        setLabel("node-locale-variants.column.label");
        setValueProvider((Class) LocalizedValueProvider.class);
        setDescriptionGenerator((Class) VariantLocalesTooltip.class);
        setWidth(110d);
        setSortable(false);
    }

    /** The cell, as HTML: every text in it is escaped. */
    public static class LocalizedValueProvider extends AbstractValueProvider<Item> {

        private final LocaleVariantStructure variantStructure;
        // The same on every original with variants: the icon, and its meaning
        // as text for screen readers
        private final String hasVariantsHtml;

        @Inject
        public LocalizedValueProvider(LocaleVariantStructure variantStructure, SimpleTranslator i18n) {
            this.variantStructure = variantStructure;
            String hasVariants = i18n.translate("node-locale-variants.column.aria");
            this.hasVariantsHtml = "<span class=\"v-table-icon-element " + ICON + "\"></span>"
                    + "<span class=\"hidden-for-aria\">" + escapeHtml(hasVariants) + "</span>";
        }

        @Override
        public String apply(Item item) {
            try {
                if (!(item instanceof Node node)) {
                    return "";
                }
                if (variantStructure.hasVariantsContainer(node)) {
                    return hasVariantsHtml;
                }
                if (variantStructure.isValidVariant(node)) {
                    String locale = variantStructure.getVariantLocale(node).toString();
                    return "<span class=\"lv-locale\">" + escapeHtml(locale) + "</span>";
                }
                return "";
            } catch (RepositoryException e) {
                log.warn("[node-locale-variants] Can't read the locale variants of {}: cell left empty", item, e);
                return "";
            }
        }
    }

    /** Tooltip of an original's cell: the locales of its variants. */
    public static class VariantLocalesTooltip implements DescriptionGenerator<Item> {

        private final LocaleVariantStructure variantStructure;
        private final SimpleTranslator i18n;

        @Inject
        public VariantLocalesTooltip(LocaleVariantStructure variantStructure, SimpleTranslator i18n) {
            this.variantStructure = variantStructure;
            this.i18n = i18n;
        }

        @Override
        public String apply(Item item) {
            try {
                // The cheap check first: most rows have no variants
                if (!(item instanceof Node node) || !variantStructure.hasVariantsContainer(node)) {
                    return null;
                }
                List<String> locales = new ArrayList<>();
                for (Node variant : variantStructure.getVariants(node)) {
                    locales.add(variantStructure.getVariantLocale(variant).toString());
                }
                if (locales.isEmpty()) {
                    return null;
                }
                // Shown as HTML: escaped
                String tooltip = i18n.translate("node-locale-variants.column.tooltip", String.join(", ", locales));
                return StringEscapeUtils.escapeHtml4(tooltip);
            } catch (RepositoryException e) {
                return null;
            }
        }
    }
}
