package com.giufaz.magnolia.localevariants;

import static com.giufaz.magnolia.localevariants.LocaleVariantStructure.VARIANTS_CONTAINER_NAME;
import static com.giufaz.magnolia.localevariants.LocaleVariantStructure.VARIANTS_CONTAINER_TYPE;
import static com.giufaz.magnolia.localevariants.LocaleVariantStructure.VARIANT_MIXIN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import info.magnolia.cms.i18n.DefaultI18nContentSupport;
import info.magnolia.cms.i18n.I18nContentSupport;
import info.magnolia.cms.i18n.IetfI18nContentSupport;
import info.magnolia.cms.i18n.LocaleDefinition;
import info.magnolia.module.site.Site;
import info.magnolia.module.site.SiteManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.commons.iterator.NodeIteratorAdapter;
import org.junit.jupiter.api.Test;

class LocaleVariantStructureTest {

    private static final Locale DE_CH = new Locale("de", "CH");

    private final SiteManager siteManager = mock(SiteManager.class);
    private final LocaleVariantStructure variantStructure = new LocaleVariantStructure(siteManager);

    @Test
    void test_deriveVariantName_withAnyI18nContentSupport() {
        Node promo = mock(Node.class);
        singleSite(new DefaultI18nContentSupport());
        assertEquals("variant_de", variantStructure.deriveVariantName(promo, Locale.GERMAN));
        assertEquals("variant_de_CH", variantStructure.deriveVariantName(promo, DE_CH));
        singleSite(new IetfI18nContentSupport());
        assertEquals("variant_de-CH", variantStructure.deriveVariantName(promo, DE_CH));
        singleSite(null);
        assertEquals("variant_de_CH", variantStructure.deriveVariantName(promo, DE_CH), "site without i18n → Magnolia's default naming");
    }

    @Test
    void test_deriveVariantName_withoutLocale() {
        Node promo = mock(Node.class);
        assertThrows(IllegalArgumentException.class, () -> variantStructure.deriveVariantName(promo, null));
        assertThrows(IllegalArgumentException.class, () -> variantStructure.deriveVariantName(promo, Locale.ROOT));
    }

    @Test
    void test_getSiteI18n_sitePriority() {
        Node promo = mock(Node.class);
        I18nContentSupport defaultI18n = new DefaultI18nContentSupport();
        I18nContentSupport assignedI18n = new IetfI18nContentSupport();
        I18nContentSupport requestI18n = new DefaultI18nContentSupport();
        Site defaultSite = site("default", defaultI18n);
        Site defaultSiteCopy = site("default", new DefaultI18nContentSupport()); // same site, other instance
        Site assignedSite = site("assigned", assignedI18n);
        Site requestSite = site("request", requestI18n);
        when(siteManager.getDefaultSite()).thenReturn(defaultSite);
        when(siteManager.getCurrentSite()).thenReturn(requestSite);

        when(siteManager.getAssignedSite(promo)).thenReturn(assignedSite);
        assertSame(assignedI18n, variantStructure.getSiteI18n(promo), "assigned site wins over the request's site");

        when(siteManager.getAssignedSite(promo)).thenReturn(defaultSiteCopy);
        assertSame(requestI18n, variantStructure.getSiteI18n(promo), "node on the default site → request's site");

        when(siteManager.getAssignedSite(promo)).thenReturn(null);
        assertSame(requestI18n, variantStructure.getSiteI18n(promo), "no assigned site → request's site");

        when(siteManager.getCurrentSite()).thenThrow(new IllegalStateException("no web context"));
        assertSame(defaultI18n, variantStructure.getSiteI18n(promo), "outside a web request → default site");
    }

    @Test
    void test_getSiteI18n_siteWithoutI18n() {
        singleSite(null);
        I18nContentSupport i18n = variantStructure.getSiteI18n(mock(Node.class));
        assertTrue(i18n instanceof DefaultI18nContentSupport && !i18n.isEnabled());
    }

    // Site locales: en (fallback), de, de_CH, fr
    @Test
    void test_findCurrentLocaleVariant_followsMagnoliaLocaleChain() throws RepositoryException {
        Node promo = original(variant("variant_de"));
        singleSite(i18n(Locale.GERMAN));
        assertEquals("variant_de", currentVariantName(promo));
        singleSite(i18n(DE_CH));
        assertEquals("variant_de", currentVariantName(promo), "de_CH → de");
        singleSite(i18n(Locale.FRENCH));
        assertNull(currentVariantName(promo), "fr → original");

        singleSite(i18n(Locale.GERMAN));
        assertEquals("variant_de_CH", currentVariantName(original(variant("variant_de_CH"))), "de → same-language country");
        singleSite(i18n(Locale.FRENCH));
        assertEquals("variant_en", currentVariantName(original(variant("variant_en"))), "fr → site fallback locale");
    }

    @Test
    void test_findCurrentLocaleVariant_withI18nDisabled() throws RepositoryException {
        Node promo = original(variant("variant_de"));
        singleSite(disabledI18n(Locale.GERMAN));
        assertNull(currentVariantName(promo), "i18n off → original");
        singleSite(null);
        assertNull(currentVariantName(promo), "site without i18n → original");
    }

    @Test
    void test_findCurrentLocaleVariant_withoutValidContainerOrVariant() throws RepositoryException {
        singleSite(i18n(Locale.GERMAN));
        assertNull(currentVariantName(node(1, node(0, null), "mgnl:versionable")), "no container");
        assertNull(currentVariantName(originalWithUntypedContainer(variant("variant_de"))), "container without its type");
        assertNull(currentVariantName(original(child("variant_de"))), "variant without mixin");
        assertNull(currentVariantName(original(variant("variant"))), "plain 'variant' child");
    }

    @Test
    void test_getVariants_returnsOnlyValidVariants() throws RepositoryException {
        Node promo = original(variant("variant_de"), child("teaser"), variant("variant_de_CH"));
        assertEquals(List.of("variant_de", "variant_de_CH"), names(variantStructure.getVariants(promo)));
    }

    @Test
    void test_getVariants_withoutValidContainer() throws RepositoryException {
        assertEquals(List.of(), variantStructure.getVariants(node(1, node(0, null), "mgnl:versionable")), "no container");
        assertEquals(List.of(), variantStructure.getVariants(originalWithUntypedContainer(variant("variant_de"))),
                "container without its type");
    }

    // The name only: an empty container counts too
    @Test
    void test_hasVariantsContainer() throws RepositoryException {
        assertTrue(variantStructure.hasVariantsContainer(original(variant("variant_de"))));
        assertTrue(variantStructure.hasVariantsContainer(original()), "empty container");
        assertFalse(variantStructure.hasVariantsContainer(node(1, node(0, null), "mgnl:versionable")), "no container");
    }

    @Test
    void test_hasVariant_onlyValidVariantOfThatLocale() throws RepositoryException {
        singleSite(i18n(Locale.GERMAN));
        Node promo = original(variant("variant_de"), child("variant_fr"));
        assertTrue(variantStructure.hasVariant(promo, Locale.GERMAN));
        assertFalse(variantStructure.hasVariant(promo, DE_CH), "another locale, no fallback");
        assertFalse(variantStructure.hasVariant(promo, Locale.FRENCH), "node without mixin");
        assertFalse(variantStructure.hasVariant(node(1, node(0, null), "mgnl:versionable"), Locale.GERMAN), "no container");
    }

    @Test
    void test_hasVariant_withoutLocale() throws RepositoryException {
        Node withoutContainer = node(1, node(0, null), "mgnl:versionable");
        assertThrows(IllegalArgumentException.class, () -> variantStructure.hasVariant(withoutContainer, null), "no container");
        assertThrows(IllegalArgumentException.class, () -> variantStructure.hasVariant(original(variant("variant_de")), null), "with container");
    }

    @Test
    void test_getVariantLocale_withDefaultAndIetfNames() throws RepositoryException {
        assertEquals(Locale.GERMAN, variantStructure.getVariantLocale(variant("variant_de")));
        assertEquals(DE_CH, variantStructure.getVariantLocale(variant("variant_de_CH")));
        assertEquals(DE_CH, variantStructure.getVariantLocale(variant("variant_de-CH")));
    }

    @Test
    void test_canHaveVariants_withVersionableNode() throws RepositoryException {
        assertTrue(variantStructure.canHaveVariants(node(1, node(0, null), "mgnl:versionable")));
    }

    @Test
    void test_canHaveVariants_withNotVersionableNode() throws RepositoryException {
        Node root = node(0, null);
        assertFalse(variantStructure.canHaveVariants(node(1, root, "mgnl:contentNode")), "area/component");
        assertFalse(variantStructure.canHaveVariants(node(1, root, "mgnl:folder")), "folder");
    }

    @Test
    void test_canHaveVariants_insideVariantsOrContainer() throws RepositoryException {
        Node root = node(0, null);
        Node promo = node(1, root, "mgnl:versionable");
        Node container = node(2, promo, "mgnl:versionable", VARIANTS_CONTAINER_TYPE);
        Node variant = node(3, container, "mgnl:versionable", VARIANT_MIXIN);
        when(variant.getName()).thenReturn("variant_de");
        Node insideVariant = node(4, variant, "mgnl:versionable");
        assertFalse(variantStructure.canHaveVariants(container), "container");
        assertFalse(variantStructure.canHaveVariants(variant), "variant");
        assertFalse(variantStructure.canHaveVariants(insideVariant), "inside a variant");
    }

    @Test
    void test_getSupportedVariantLocales_siteLocalesWithoutDefault() {
        Node promo = mock(Node.class);
        singleSite(i18n(Locale.GERMAN));
        assertEquals(List.of(Locale.GERMAN, DE_CH, Locale.FRENCH), variantStructure.getSupportedVariantLocales(promo), "en is the default");
        singleSite(disabledI18n(Locale.GERMAN));
        assertEquals(List.of(), variantStructure.getSupportedVariantLocales(promo), "i18n off");
    }

    @Test
    void test_isValidVariant_requiresMixinAndNamePrefix() throws RepositoryException {
        assertTrue(variantStructure.isValidVariant(variant("variant_de")));
        assertFalse(variantStructure.isValidVariant(variant("variant")), "no locale suffix");
        assertFalse(variantStructure.isValidVariant(variant("teaser")), "wrong name");
        assertFalse(variantStructure.isValidVariant(child("variant_de")), "no mixin");
    }

    private String currentVariantName(Node original) throws RepositoryException {
        Optional<Node> variant = variantStructure.findCurrentLocaleVariant(original);
        return variant.isPresent() ? variant.get().getName() : null;
    }

    // One site: default, current and assigned to every node
    private void singleSite(I18nContentSupport i18n) {
        Site site = site("site", i18n);
        when(siteManager.getDefaultSite()).thenReturn(site);
        when(siteManager.getCurrentSite()).thenReturn(site);
        when(siteManager.getAssignedSite(any(Node.class))).thenReturn(site);
    }

    private static Site site(String name, I18nContentSupport i18n) {
        Site site = mock(Site.class);
        when(site.getName()).thenReturn(name);
        when(site.getI18n()).thenReturn(i18n);
        return site;
    }

    private static I18nContentSupport i18n(Locale current) {
        DefaultI18nContentSupport i18n = new DefaultI18nContentSupport() {
            @Override
            public Locale getLocale() {
                return current; // what Magnolia negotiated for the request
            }
        };
        i18n.setEnabled(true);
        i18n.setFallbackLocale(Locale.ENGLISH);
        i18n.setLocales(List.of(LocaleDefinition.make("en", null, true), LocaleDefinition.make("de", null, true),
                LocaleDefinition.make("de", "CH", true), LocaleDefinition.make("fr", null, true)));
        return i18n;
    }

    private static I18nContentSupport disabledI18n(Locale current) {
        DefaultI18nContentSupport i18n = (DefaultI18nContentSupport) i18n(current);
        i18n.setEnabled(false);
        return i18n;
    }

    private static Node original(Node... children) throws RepositoryException {
        return originalWith(container(VARIANTS_CONTAINER_TYPE, children));
    }

    // A child named localeVariants, but without the container type
    private static Node originalWithUntypedContainer(Node... children) throws RepositoryException {
        return originalWith(container("mgnl:content", children));
    }

    private static Node originalWith(Node container) throws RepositoryException {
        Node original = node(1, node(0, null), "mgnl:versionable");
        when(original.hasNode(VARIANTS_CONTAINER_NAME)).thenReturn(true);
        when(original.getNode(VARIANTS_CONTAINER_NAME)).thenReturn(container);
        return original;
    }

    private static Node container(String type, Node... children) throws RepositoryException {
        Map<String, Node> byName = new LinkedHashMap<>();
        for (Node child : children) {
            byName.put(child.getName(), child);
        }
        Node container = node(2, null, type);
        when(container.hasNode(anyString())).thenAnswer(i -> byName.containsKey(i.<String>getArgument(0)));
        when(container.getNode(anyString())).thenAnswer(i -> {
            Node child = byName.get(i.<String>getArgument(0));
            if (child == null) {
                throw new PathNotFoundException(i.<String>getArgument(0));
            }
            return child;
        });
        when(container.getNodes()).thenAnswer(i -> new NodeIteratorAdapter(byName.values()));
        return container;
    }

    private static Node variant(String name) throws RepositoryException {
        return named(name, VARIANT_MIXIN);
    }

    private static Node child(String name) throws RepositoryException {
        return named(name);
    }

    private static Node named(String name, String... types) throws RepositoryException {
        Node node = node(1, node(0, null), types);
        when(node.getName()).thenReturn(name);
        return node;
    }

    private static Node node(int depth, Node parent, String... types) throws RepositoryException {
        Node node = mock(Node.class);
        when(node.getDepth()).thenReturn(depth);
        when(node.getParent()).thenReturn(parent);
        when(node.isNodeType(anyString())).thenReturn(false);
        for (String type : types) {
            when(node.isNodeType(type)).thenReturn(true);
        }
        return node;
    }

    private static List<String> names(List<Node> nodes) throws RepositoryException {
        List<String> names = new ArrayList<>();
        for (Node node : nodes) {
            names.add(node.getName());
        }
        return names;
    }
}
