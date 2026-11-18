package com.giufaz.magnolia.localevariants.delivery;

import info.magnolia.cms.beans.config.ServerConfiguration;
import info.magnolia.context.MgnlContext;
import info.magnolia.context.WebContext;
import info.magnolia.jcr.RuntimeRepositoryException;
import info.magnolia.objectfactory.Components;
import info.magnolia.rest.delivery.jcr.v2.ConfiguredFiltering;

import java.util.ArrayList;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.commons.iterator.NodeIteratorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.giufaz.magnolia.localevariants.LocaleVariantStructure;

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;

/**
 * Delivery API v2 filter: serves the locale variant of the requested node in
 * place of the original.
 *
 * <p>
 * In scope:
 * <ul>
 * <li>It ONLY applies to Delivery API requests;</li>
 * <li>single node ({@code readNode}) and children ({@code @nodes});</li>
 * <li>requests with a locale from the {@code ?lang} parameter or the browser's
 * {@code Accept-Language} header;
 * the variant follows Magnolia's i18n fallback ({@code de_CH} →
 * {@code de}).</li>
 * </ul>
 *
 * <p>
 * Out of scope, delivery response as out-of-the-box for:
 * <ul>
 * <li>query results (searched or filtered lists);</li>
 * <li>requests with no locale, or with {@code lang=all};</li>
 * <li>requests for a specific version ({@code ?version=});</li>
 * <li>page editor requests on the author instance ({@code mgnlPreview}, edit or
 * preview): the editor edits the
 * requested page itself, a variant is edited by opening the variant page.</li>
 * </ul>
 *
 * <p>
 * Registered in {@code META-INF/services/jakarta.ws.rs.ext.Providers}.
 */
@Provider
@ConfiguredFiltering // Applies to delivery v2 endpoints only
@Priority(LocaleVariantResponseFilter.PRIORITY)
public class LocaleVariantResponseFilter implements ContainerResponseFilter {

    /**
     * After DX personalization (6000), before Magnolia's i18n (5001) and node
     * wrappers (5000).
     */
    public static final int PRIORITY = 5500;

    private static final String LANG_PARAM = "lang";
    private static final String ALL_LANGUAGES = "all";
    private static final String VERSION_PARAM = "version";
    // Marks page editor requests
    private static final String EDITOR_PARAM = "mgnlPreview";

    private static final Logger log = LoggerFactory.getLogger(LocaleVariantResponseFilter.class);

    private final LocaleVariantStructure variantStructure;
    private final boolean isAuthorInstance;

    // Created by RESTEasy (META-INF/services), so its dependencies come from the
    // component provider
    public LocaleVariantResponseFilter() {
        this(Components.getComponent(LocaleVariantStructure.class),
                Components.getComponent(ServerConfiguration.class).isAdmin());
    }

    LocaleVariantResponseFilter(LocaleVariantStructure variantStructure, boolean isAuthorInstance) {
        this.variantStructure = variantStructure;
        this.isAuthorInstance = isAuthorInstance;
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        Object entity = response.getEntity();
        if (entity == null || !hasExplicitLanguage(request) || isVersionRequest(request)
                || isPageEditorRequest(request)) {
            return;
        }
        if (entity instanceof Node node) {
            localizeNode(node, response);
        } else if (entity instanceof NodeIterator nodes) {
            localizeNodes(nodes, response);
        }
        // We don't handle NodesResult entities (search results) by design
    }

    // On error: WARN and leave the response unchanged, as out of the box
    private void localizeNode(Node node, ContainerResponseContext response) {
        try {
            Node resolved = resolveLocalizedVariant(node);
            if (resolved != node) {
                // Keep Magnolia's request state on the served node
                WebContext webContext = MgnlContext.getWebContextOrNull();
                if (webContext != null) {
                    webContext.getAggregationState().setMainContentNode(resolved);
                }
                response.setEntity(resolved);
            }
        } catch (RepositoryException | RuntimeRepositoryException e) {
            log.warn("Can't resolve the locale variant of {}, serving the original", node, e);
        }
    }

    // Children lists (@nodes). All or nothing: if one node fails, WARN and leave the
    // whole list unchanged, as out of the box
    private void localizeNodes(NodeIterator nodes, ContainerResponseContext response) {
        List<Node> originals = new ArrayList<>();
        while (nodes.hasNext()) {
            originals.add(nodes.nextNode());
        }
        List<Node> resolved = new ArrayList<>(originals.size());
        try {
            for (Node node : originals) {
                resolved.add(resolveLocalizedVariant(node));
            }
            response.setEntity(new NodeIteratorAdapter(resolved));
        } catch (RepositoryException | RuntimeRepositoryException e) {
            log.warn("Can't resolve the locale variants of a node list, serving the originals", e);
            response.setEntity(new NodeIteratorAdapter(originals));
        }
    }

    private Node resolveLocalizedVariant(Node node) throws RepositoryException {
        return variantStructure.findCurrentLocaleVariant(node)
                .map(PagesSupport::withAreaNodes)
                .orElse(node);
    }

    // Same rules as Magnolia's i18n filters:
    // Locales from ?lang (not "all"), else Accept-Language (not blank, not "*").
    // Otherwise, returns false
    private boolean hasExplicitLanguage(ContainerRequestContext request) {
        String lang = request.getUriInfo().getQueryParameters().getFirst(LANG_PARAM);
        if (StringUtils.isNotBlank(lang)) {
            return !ALL_LANGUAGES.equalsIgnoreCase(lang);
        }
        String acceptLanguage = request.getHeaderString(HttpHeaders.ACCEPT_LANGUAGE);
        return StringUtils.isNotBlank(acceptLanguage) && !"*".equals(acceptLanguage.trim());
    }

    private boolean isVersionRequest(ContainerRequestContext request) {
        return StringUtils.isNotBlank(request.getUriInfo().getQueryParameters().getFirst(VERSION_PARAM));
    }

    private boolean isPageEditorRequest(ContainerRequestContext request) {
        return isAuthorInstance && request.getUriInfo().getQueryParameters().containsKey(EDITOR_PARAM);
    }
}
