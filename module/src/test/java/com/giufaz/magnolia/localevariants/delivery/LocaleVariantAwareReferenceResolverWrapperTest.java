package com.giufaz.magnolia.localevariants.delivery;

import static com.giufaz.magnolia.localevariants.LocaleVariantStructure.VARIANTS_CONTAINER_NAME;
import static com.giufaz.magnolia.localevariants.LocaleVariantStructure.VARIANTS_CONTAINER_TYPE;
import static com.giufaz.magnolia.localevariants.LocaleVariantStructure.VARIANT_BASE_NAME;
import static com.giufaz.magnolia.localevariants.LocaleVariantStructure.VARIANT_MIXIN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import info.magnolia.cms.i18n.I18nContentSupport;
import info.magnolia.module.site.Site;
import info.magnolia.module.site.SiteManager;
import info.magnolia.rest.reference.jcr.JcrReferenceResolver;

import java.util.Map;
import java.util.Optional;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.giufaz.magnolia.localevariants.LocaleVariantStructure;

class LocaleVariantAwareReferenceResolverWrapperTest {

    private final I18nContentSupport i18n = mock(I18nContentSupport.class);
    private final SiteManager siteManager = mock(SiteManager.class);
    private final JcrReferenceResolver jcr = mock(JcrReferenceResolver.class);
    private final LocaleVariantAwareReferenceResolverWrapper wrapper =
            new LocaleVariantAwareReferenceResolverWrapper(jcr, new LocaleVariantStructure(siteManager));

    private Node promo;
    private Node variantDe;
    private Node plain;

    // One site; promo has a "de" variant (what Magnolia resolves for the request locale), plain has none.
    // The JCR resolver answers promo, its variant and plain by UUID.
    @BeforeEach
    void setUp() throws RepositoryException {
        promo = mock(Node.class);
        Node container = mock(Node.class);
        variantDe = mock(Node.class);
        plain = mock(Node.class);
        when(promo.hasNode(VARIANTS_CONTAINER_NAME)).thenReturn(true);
        when(promo.getNode(VARIANTS_CONTAINER_NAME)).thenReturn(container);
        when(container.isNodeType(VARIANTS_CONTAINER_TYPE)).thenReturn(true);
        when(i18n.getNode(container, VARIANT_BASE_NAME)).thenReturn(variantDe);
        when(variantDe.isNodeType(VARIANT_MIXIN)).thenReturn(true);
        when(variantDe.getName()).thenReturn("variant_de");
        when(variantDe.getIdentifier()).thenReturn("uuid-variant-de");
        Site site = mock(Site.class);
        when(site.getName()).thenReturn("site");
        when(site.getI18n()).thenReturn(i18n);
        when(siteManager.getDefaultSite()).thenReturn(site);
        when(siteManager.getCurrentSite()).thenReturn(site);
        when(siteManager.getAssignedSite(any(Node.class))).thenReturn(site);

        Map<String, Node> byUuid = Map.of("uuid-promo", promo, "uuid-variant-de", variantDe, "uuid-plain", plain);
        when(jcr.resolve(anyString())).thenAnswer(i -> Optional.ofNullable(byUuid.get(i.<String>getArgument(0))));
        when(jcr.resolve(anyString(), anyString())).thenAnswer(i -> Optional.ofNullable(byUuid.get(i.<String>getArgument(0))));
    }

    @Test
    void test_resolve_originalWithVariant() {
        assertEquals(Optional.of(variantDe), wrapper.resolve("uuid-promo", "related"));
        verify(jcr).resolve("uuid-variant-de", "related"); // second resolution, property name passed on
    }

    @Test
    void test_resolve_withoutPropertyName() {
        assertEquals(Optional.of(variantDe), wrapper.resolve("uuid-promo"));
        verify(jcr).resolve("uuid-variant-de");
    }

    @Test
    void test_resolve_originalWithoutVariant() {
        assertEquals(Optional.of(plain), wrapper.resolve("uuid-plain", "related"));
    }

    @Test
    void test_resolve_directReferenceToVariant() {
        assertEquals(Optional.of(variantDe), wrapper.resolve("uuid-variant-de", "related"), "as is");
    }

    @Test
    void test_resolve_linkOrEmpty() {
        when(jcr.resolve("uuid-link", "related")).thenReturn(Optional.of("/promos/promo-a")); // expand: false
        assertEquals(Optional.of("/promos/promo-a"), wrapper.resolve("uuid-link", "related"), "links untouched");
        assertEquals(Optional.empty(), wrapper.resolve("uuid-missing", "related"), "nothing resolved");
    }

    @Test
    void test_resolve_withError() throws RepositoryException {
        Node repositoryError = mock(Node.class);
        when(repositoryError.hasNode(anyString())).thenThrow(new RepositoryException("boom"));
        Node runtimeError = mock(Node.class);
        when(runtimeError.hasNode(anyString())).thenThrow(new IllegalStateException("boom"));
        when(jcr.resolve("uuid-repository-error", "related")).thenReturn(Optional.of(repositoryError));
        when(jcr.resolve("uuid-runtime-error", "related")).thenReturn(Optional.of(runtimeError));

        assertEquals(Optional.of(repositoryError), wrapper.resolve("uuid-repository-error", "related"), "the original");
        assertEquals(Optional.of(runtimeError), wrapper.resolve("uuid-runtime-error", "related"), "the original");
    }
}
