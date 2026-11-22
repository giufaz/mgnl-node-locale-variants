package com.giufaz.magnolia.localevariants;

import info.magnolia.cms.i18n.DefaultI18nContentSupport;
import info.magnolia.cms.i18n.I18nContentSupport;
import info.magnolia.module.site.Site;
import info.magnolia.module.site.SiteManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;

import jakarta.inject.Inject;

/**
 * Data model of locale variants: names, node types, lookup and eligibility.
 *
 * <pre>
 * original               mgnl:versionable
 * ├── title, title_de
 * └── localeVariants     lv:localeVariants
 *     ├── variant_de     + lv:localeVariant
 *     └── variant_de_CH  + lv:localeVariant
 * </pre>
 * <ul>
 * <li>{@code original}: any {@code mgnl:versionable} node, e.g. a page or a
 * content item.</li>
 * <li>{@code localeVariants}: the variants container, fixed name and primary
 * type.</li>
 * <li>{@code variant_<locale>}: a copy of the original (same primary type) with
 * the mixin {@code lv:localeVariant}.</li>
 * </ul>
 *
 * <p>
 * Variants are resolved like Magnolia i18n properties or nodes.
 * E.g. for {@code de_CH} locale, the first that exists wins:
 * {@code variant_de_CH} → {@code variant_de} → the original.
 *
 * <p>
 * Only nodes that stand on their own can have variants:
 * {@code mgnl:versionable} nodes
 * (a page or a content item, not a component or a nested
 * {@code mgnl:contentNode}).
 * Variants are never nested: a variant or a variants container, or anything
 * inside them, can't have variants.
 */
public class LocaleVariantStructure {

    // Fixed name and type for the variants container (under the original node)
    public static final String VARIANTS_CONTAINER_NAME = "localeVariants";
    public static final String VARIANTS_CONTAINER_TYPE = "lv:localeVariants";

    // Fixed base name and mixin type for node variants (clones of an original node)
    public static final String VARIANT_MIXIN = "lv:localeVariant";
    public static final String VARIANT_BASE_NAME = "variant";
    public static final String VARIANT_PREFIX = VARIANT_BASE_NAME + "_";

    private static final String VERSIONABLE = "mgnl:versionable";

    private final SiteManager siteManager;

    @Inject
    public LocaleVariantStructure(SiteManager siteManager) {
        this.siteManager = siteManager;
    }

    public String deriveVariantName(Node node, Locale locale) {
        if (locale == null || locale.getLanguage().isEmpty()) {
            throw new IllegalArgumentException("A locale is required");
        }
        I18nContentSupport i18n = getSiteI18n(node);
        return VARIANT_PREFIX + i18n.getLanguageString(locale);
    }

    // Get the right i18n support from best site candidate
    // for current node and context.
    // Priority: node assigned site > request site > default site
    public I18nContentSupport getSiteI18n(Node node) {
        Site defaultSite = siteManager.getDefaultSite();
        Site site = siteManager.getAssignedSite(node);
        if (site == null || isSameSite(site, defaultSite)) {
            site = getCurrentSiteOrNull();
        }

        return Optional.ofNullable(site)
                .or(() -> Optional.ofNullable(defaultSite))
                .map(Site::getI18n)
                .orElseGet(DefaultI18nContentSupport::new);
    }

    public Optional<Node> findCurrentLocaleVariant(Node original) throws RepositoryException {
        Optional<Node> container = getVariantsContainer(original);
        if (container.isEmpty()) {
            return Optional.empty();
        }
        try {
            Node variant = getSiteI18n(original).getNode(container.get(), VARIANT_BASE_NAME);
            return isValidVariant(variant) ? Optional.of(variant) : Optional.empty();
        } catch (PathNotFoundException e) {
            return Optional.empty();
        }
    }

    public List<Node> getVariants(Node original) throws RepositoryException {
        Optional<Node> container = getVariantsContainer(original);
        if (container.isEmpty()) {
            return Collections.emptyList();
        }
        List<Node> variants = new ArrayList<>();
        for (NodeIterator it = container.get().getNodes(); it.hasNext();) {
            Node child = it.nextNode();
            if (isValidVariant(child)) {
                variants.add(child);
            }
        }
        return variants;
    }

    /** Whether a variant exists for exactly this locale: no i18n fallback. */
    public boolean hasVariant(Node original, Locale locale) throws RepositoryException {
        String name = deriveVariantName(original, locale);
        Optional<Node> container = getVariantsContainer(original);
        return container.isPresent() && container.get().hasNode(name) && isValidVariant(container.get().getNode(name));
    }

    // variant_de_CH, or variant_de-CH on IETF sites → Locale("de", "CH")
    public Locale getVariantLocale(Node variant) throws RepositoryException {
        String suffix = variant.getName().substring(VARIANT_PREFIX.length());
        return Locale.forLanguageTag(suffix.replace('_', '-'));
    }

    public boolean canHaveVariants(Node original) throws RepositoryException {
        if (!original.isNodeType(VERSIONABLE)) {
            return false;
        }

        // No variants allowed inside other variants
        for (Node n = original; n.getDepth() > 0; n = n.getParent()) {
            if (isValidVariant(n) || isVariantsContainer(n)) {
                return false;
            }
        }
        return true;
    }

    /** Locales the node's site supports for variants: its enabled locales without the default one, existing variants included. */
    public List<Locale> getSupportedVariantLocales(Node original) {
        I18nContentSupport i18n = getSiteI18n(original);
        if (!i18n.isEnabled()) {
            return List.of();
        }
        Locale defaultLocale = i18n.getDefaultLocale();
        return i18n.getLocales().stream().filter(locale -> !locale.equals(defaultLocale)).toList();
    }

    public boolean isVariantsContainer(Node node) throws RepositoryException {
        return node.isNodeType(VARIANTS_CONTAINER_TYPE);
    }

    public boolean isValidVariant(Node node) throws RepositoryException {
        return node.isNodeType(VARIANT_MIXIN) && node.getName().startsWith(VARIANT_PREFIX);
    }

    /** Whether the node has a child named as the variants container: the name only, cheap enough for a browser row. */
    public boolean hasVariantsContainer(Node original) throws RepositoryException {
        return original.hasNode(VARIANTS_CONTAINER_NAME);
    }

    Optional<Node> getVariantsContainer(Node original) throws RepositoryException {
        if (!original.hasNode(VARIANTS_CONTAINER_NAME)) {
            return Optional.empty();
        }
        Node container = original.getNode(VARIANTS_CONTAINER_NAME);
        return isVariantsContainer(container) ? Optional.of(container) : Optional.empty();
    }

    // getCurrentSite() reads the request state: it throws IllegalStateException
    // outside a web request (jobs, commands)
    private Site getCurrentSiteOrNull() {
        try {
            return siteManager.getCurrentSite();
        } catch (IllegalStateException e) {
            return null;
        }
    }

    // Site has no equals(): compare by name, as Magnolia does
    private static boolean isSameSite(Site a, Site b) {
        if (a == null || b == null) {
            return a == b;
        }
        return Objects.equals(a.getName(), b.getName());
    }
}