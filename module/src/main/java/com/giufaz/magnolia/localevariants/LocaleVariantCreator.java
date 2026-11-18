package com.giufaz.magnolia.localevariants;

import static com.giufaz.magnolia.localevariants.LocaleVariantStructure.VARIANTS_CONTAINER_NAME;
import static com.giufaz.magnolia.localevariants.LocaleVariantStructure.VARIANTS_CONTAINER_TYPE;
import static com.giufaz.magnolia.localevariants.LocaleVariantStructure.VARIANT_MIXIN;

import info.magnolia.cms.i18n.I18nContentSupport;
import info.magnolia.jcr.util.NodeTypes;
import info.magnolia.jcr.util.NodeUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;

import jakarta.inject.Inject;

/**
 * Creates a locale variant: a mono-lingual copy of the original in
 * {@code <original>/localeVariants/variant_<locale>}. The session is not saved (the caller does).
 * <ul>
 * <li>Copied: properties, mixins and child nodes, recursively.</li>
 * <li>Not copied: children of type {@code mgnl:content} or a subtype (pages, content items) or
 * {@code mgnl:folder}; {@code jcr:} and {@code rep:} nodes and properties; Magnolia metadata
 * ({@code mgnl:*} except {@code mgnl:template}).</li>
 * <li>Mono-lingual: the value of the variant's locale replaces the base value ({@code title_de_CH},
 * else {@code title_de}, else {@code title}); every {@code *_<site locale>} property is dropped.
 * A localized child ({@code address_de}) replaces the base one as is.</li>
 * </ul>
 * A project can replace this component in the module descriptor to change the copy rules.
 */
public class LocaleVariantCreator {

    private static final String TEMPLATE = NodeTypes.Renderable.TEMPLATE;
    private static final List<String> NOT_COPIED_TYPES = List.of(NodeTypes.Content.NAME, NodeTypes.Folder.NAME);

    private final LocaleVariantStructure variantStructure;

    @Inject
    public LocaleVariantCreator(LocaleVariantStructure variantStructure) {
        this.variantStructure = variantStructure;
    }

    /**
     * Creates the variant (unsaved).
     *
     * @throws IllegalArgumentException the node can't have variants, or the locale
     *                                  is not one of
     *                                  {@link LocaleVariantStructure#getSupportedVariantLocales}
     * @throws IllegalStateException    the variant exists already
     */
    public Node create(Node original, Locale locale) throws RepositoryException {
        if (!variantStructure.canHaveVariants(original)) {
            throw new IllegalArgumentException(original.getPath() + " can't have locale variants");
        }
        if (!variantStructure.getSupportedVariantLocales(original).contains(locale)) {
            throw new IllegalArgumentException(locale + " is not a supported locale for " + original.getPath());
        }
        if (variantStructure.hasVariant(original, locale)) {
            throw new IllegalStateException(original.getPath() + " already has a variant for locale " + locale);
        }
        I18nContentSupport i18n = variantStructure.getSiteI18n(original);
        Node container = getOrCreateContainer(original);
        Node variant = container.addNode(variantStructure.deriveVariantName(original, locale),
                original.getPrimaryNodeType().getName());
        copy(original, variant, new Localization(i18n, locale));
        variant.addMixin(VARIANT_MIXIN);
        // Current user from the context, as Magnolia's session wrapper does for the container
        NodeTypes.Created.set(variant);
        NodeTypes.LastModified.update(variant, NodeTypes.Created.getCreated(variant));
        return variant;
    }

    // The original's container, created first among its children if missing
    private Node getOrCreateContainer(Node original) throws RepositoryException {
        Optional<Node> existing = variantStructure.getVariantsContainer(original);
        if (existing.isPresent()) {
            return existing.get();
        }
        Node container = original.addNode(VARIANTS_CONTAINER_NAME, VARIANTS_CONTAINER_TYPE);
        if (original.getPrimaryNodeType().hasOrderableChildNodes()) {
            NodeUtil.orderFirst(container);
        }
        return container;
    }

    // Properties and children of source into target; localization null = as is
    // (inside a localized child)
    private void copy(Node source, Node target, Localization localization) throws RepositoryException {
        for (NodeType mixin : source.getMixinNodeTypes()) {
            if (!target.isNodeType(mixin.getName())) {
                target.addMixin(mixin.getName());
            }
        }
        Map<String, Property> properties = new LinkedHashMap<>();
        for (PropertyIterator it = source.getProperties(); it.hasNext();) {
            Property property = it.nextProperty();
            if (isCopied(property.getName())) {
                properties.put(property.getName(), property);
            }
        }
        for (Map.Entry<String, Property> entry : localize(properties, localization).entrySet()) {
            Property property = entry.getValue();
            if (property.isMultiple()) {
                target.setProperty(entry.getKey(), property.getValues()); // type taken from the values
            } else {
                target.setProperty(entry.getKey(), property.getValue());
            }
        }
        Map<String, Node> children = new LinkedHashMap<>();
        for (NodeIterator it = source.getNodes(); it.hasNext();) {
            Node child = it.nextNode();
            if (isCopied(child)) {
                children.put(child.getName(), child);
            }
        }
        for (Map.Entry<String, Node> entry : localize(children, localization).entrySet()) {
            Node child = entry.getValue();
            Node copy = target.addNode(entry.getKey(), child.getPrimaryNodeType().getName());
            // A localized child replaces the base one as is; a base child follows the same
            // rules
            copy(child, copy, child.getName().equals(entry.getKey()) ? localization : null);
        }
    }

    // Base name → item to copy: the first localized item of the chain, else the
    // base item; every localized item dropped
    private static <T> Map<String, T> localize(Map<String, T> items, Localization localization) {
        if (localization == null) {
            return items;
        }
        Set<String> bases = new LinkedHashSet<>();
        items.keySet().forEach(name -> bases.add(localization.baseName(name).orElse(name)));
        Map<String, T> result = new LinkedHashMap<>();
        for (String base : bases) {
            localization.chain.stream().map(suffix -> items.get(base + "_" + suffix)).filter(item -> item != null)
                    .findFirst()
                    .or(() -> Optional.ofNullable(items.get(base))
                            .filter(item -> localization.baseName(base).isEmpty()))
                    .ifPresent(item -> result.put(base, item));
        }
        return result;
    }

    private static boolean isCopied(String propertyName) {
        return !propertyName.startsWith("jcr:") && !propertyName.startsWith("rep:") && (!propertyName.startsWith("mgnl:") || TEMPLATE.equals(propertyName));
    }

    private boolean isCopied(Node child) throws RepositoryException {
        String name = child.getName();
        if (name.startsWith("jcr:") || name.startsWith("rep:") || variantStructure.isVariantsContainer(child)) {
            return false;
        }
        for (String type : NOT_COPIED_TYPES) {
            if (child.isNodeType(type)) {
                return false;
            }
        }
        return true;
    }

    // Site locale suffixes (longest first) and the variant's chain: de_CH → [de_CH,
    // de]
    private static final class Localization {
        private final List<String> suffixes;
        private final List<String> chain;

        private Localization(I18nContentSupport i18n, Locale locale) {
            List<String> all = new ArrayList<>();
            i18n.getLocales().forEach(siteLocale -> all.add(i18n.getLanguageString(siteLocale)));
            all.sort(Comparator.comparingInt(String::length).reversed());
            this.suffixes = all;
            Set<String> chain = new LinkedHashSet<>();
            chain.add(i18n.getLanguageString(locale));
            if (!locale.getCountry().isEmpty()) {
                chain.add(i18n.getLanguageString(new Locale(locale.getLanguage())));
            }
            this.chain = List.copyOf(chain);
        }

        // title_de_CH → title; title → empty
        Optional<String> baseName(String name) {
            return suffixes.stream()
                    .filter(suffix -> name.length() > suffix.length() + 1 && name.endsWith("_" + suffix))
                    .findFirst()
                    .map(suffix -> name.substring(0, name.length() - suffix.length() - 1));
        }
    }
}
