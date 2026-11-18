package com.giufaz.magnolia.localevariants.delivery;

import static com.giufaz.magnolia.localevariants.LocaleVariantStructure.VARIANTS_CONTAINER_NAME;
import static com.giufaz.magnolia.localevariants.LocaleVariantStructure.VARIANTS_CONTAINER_TYPE;
import static com.giufaz.magnolia.localevariants.LocaleVariantStructure.VARIANT_BASE_NAME;
import static com.giufaz.magnolia.localevariants.LocaleVariantStructure.VARIANT_MIXIN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import info.magnolia.cms.i18n.I18nContentSupport;
import info.magnolia.context.MgnlContext;
import info.magnolia.module.site.Site;
import info.magnolia.module.site.SiteManager;
import info.magnolia.rest.delivery.jcr.NodesResult;
import info.magnolia.test.mock.MockWebContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.commons.iterator.NodeIteratorAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.giufaz.magnolia.localevariants.LocaleVariantStructure;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;

class LocaleVariantResponseFilterTest {

    private static final Map<String, String> LANG_DE = Map.of("lang", "de");

    private final I18nContentSupport i18n = mock(I18nContentSupport.class);
    private final SiteManager siteManager = mock(SiteManager.class);
    private final LocaleVariantResponseFilter publicFilter = filter(false);

    private Node promo;
    private Node variantDe;
    private Node plain;

    // One site (default, current and assigned to every node); promo has a "de" variant, plain has none
    @BeforeEach
    void setUp() throws RepositoryException {
        promo = mock(Node.class);
        Node container = mock(Node.class);
        variantDe = mock(Node.class);
        plain = mock(Node.class);
        when(promo.hasNode(VARIANTS_CONTAINER_NAME)).thenReturn(true);
        when(promo.getNode(VARIANTS_CONTAINER_NAME)).thenReturn(container);
        when(container.isNodeType(VARIANTS_CONTAINER_TYPE)).thenReturn(true);
        when(i18n.getNode(container, VARIANT_BASE_NAME)).thenReturn(variantDe); // what Magnolia resolves for "de"
        when(variantDe.isNodeType(VARIANT_MIXIN)).thenReturn(true);
        when(variantDe.getName()).thenReturn("variant_de");
        Site site = mock(Site.class);
        when(site.getName()).thenReturn("site");
        when(site.getI18n()).thenReturn(i18n);
        when(siteManager.getDefaultSite()).thenReturn(site);
        when(siteManager.getCurrentSite()).thenReturn(site);
        when(siteManager.getAssignedSite(any(Node.class))).thenReturn(site);
    }

    @AfterEach
    void tearDown() {
        MgnlContext.setInstance(null);
    }

    @Test
    void test_filter_singleNode() {
        assertSame(variantDe, filtered(publicFilter, promo, LANG_DE, null), "?lang");
        assertSame(variantDe, filtered(publicFilter, promo, Map.of(), "de-DE,de;q=0.9"), "Accept-Language");
        assertSame(plain, filtered(publicFilter, plain, LANG_DE, null), "no variant: the original");
    }

    @Test
    void test_filter_singleNodeUpdatesAggregationState() {
        MockWebContext webContext = new MockWebContext();
        MgnlContext.setInstance(webContext);

        filtered(publicFilter, promo, LANG_DE, null);

        assertSame(variantDe, webContext.getAggregationState().getMainContentNode());
    }

    @Test
    void test_filter_nodeList() {
        Object served = filtered(publicFilter, new NodeIteratorAdapter(List.of(promo, plain)), LANG_DE, null);
        assertEquals(List.of(variantDe, plain), nodes(served));
    }

    @Test
    void test_filter_queryResults() {
        NodesResult results = new NodesResult(new NodeIteratorAdapter(List.of(promo)), 1);
        assertSame(results, filtered(publicFilter, results, LANG_DE, null), "out of scope: unchanged");
    }

    @Test
    void test_filter_withoutExplicitLanguage() {
        assertSame(promo, filtered(publicFilter, promo, Map.of(), null), "no preference");
        assertSame(promo, filtered(publicFilter, promo, Map.of(), "*"), "Accept-Language: *");
        assertSame(promo, filtered(publicFilter, promo, Map.of("lang", "all"), "de"), "lang=all");
    }

    @Test
    void test_filter_versionRequest() {
        assertSame(promo, filtered(publicFilter, promo, Map.of("lang", "de", "version", "1.0"), null));
    }

    @Test
    void test_filter_pageEditorOnAuthor() {
        LocaleVariantResponseFilter authorFilter = filter(true);
        assertSame(promo, filtered(authorFilter, promo, Map.of("lang", "de", "mgnlPreview", "false"), null), "edit mode");
        assertSame(promo, filtered(authorFilter, promo, Map.of("lang", "de", "mgnlPreview", "true"), null), "preview");
        assertSame(promo, filtered(authorFilter, promo, Map.of("lang", "de", "mgnlPreview", ""), null), "parameter without value");
        Object list = new NodeIteratorAdapter(List.of(promo, plain));
        assertSame(list, filtered(authorFilter, list, Map.of("lang", "de", "mgnlPreview", "false"), null), "@nodes");
        assertSame(variantDe, filtered(authorFilter, promo, LANG_DE, null), "author, no editor: swapped");
    }

    @Test
    void test_filter_pageEditorParameterOnPublic() {
        assertSame(variantDe, filtered(publicFilter, promo, Map.of("lang", "de", "mgnlPreview", "false"), null));
    }

    @Test
    void test_filter_errorOnSingleNode() throws RepositoryException {
        Node broken = brokenNode();
        assertSame(broken, filtered(publicFilter, broken, LANG_DE, null), "unchanged, as out of the box");
    }

    @Test
    void test_filter_errorOnNodeList() throws RepositoryException {
        Node broken = brokenNode();
        Object served = filtered(publicFilter, new NodeIteratorAdapter(List.of(promo, broken)), LANG_DE, null);
        assertEquals(List.of(promo, broken), nodes(served), "all or nothing: promo is not swapped either");
    }

    private LocaleVariantResponseFilter filter(boolean isAuthorInstance) {
        return new LocaleVariantResponseFilter(new LocaleVariantStructure(siteManager), isAuthorInstance);
    }

    // Runs the filter and returns the entity it leaves on the response
    private static Object filtered(LocaleVariantResponseFilter filter, Object entity, Map<String, String> query,
            String acceptLanguage) {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        MultivaluedHashMap<String, String> params = new MultivaluedHashMap<>();
        query.forEach(params::putSingle);
        when(uriInfo.getQueryParameters()).thenReturn(params);
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(request.getHeaderString(HttpHeaders.ACCEPT_LANGUAGE)).thenReturn(acceptLanguage);

        ContainerResponseContext response = mock(ContainerResponseContext.class);
        when(response.getEntity()).thenReturn(entity);
        filter.filter(request, response);

        ArgumentCaptor<Object> set = ArgumentCaptor.forClass(Object.class);
        verify(response, atMost(1)).setEntity(set.capture());
        return set.getAllValues().isEmpty() ? entity : set.getValue();
    }

    private static Node brokenNode() throws RepositoryException {
        Node broken = mock(Node.class);
        when(broken.hasNode(anyString())).thenThrow(new RepositoryException("boom"));
        return broken;
    }

    private static List<Node> nodes(Object nodeIterator) {
        List<Node> nodes = new ArrayList<>();
        for (NodeIterator it = (NodeIterator) nodeIterator; it.hasNext(); ) {
            nodes.add(it.nextNode());
        }
        return nodes;
    }
}
