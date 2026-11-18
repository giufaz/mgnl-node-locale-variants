package com.giufaz.magnolia.localevariants.delivery;

import info.magnolia.cms.beans.config.ServerConfiguration;
import info.magnolia.context.MgnlContext;
import info.magnolia.context.WebContext;
import info.magnolia.objectfactory.Components;
import info.magnolia.rest.delivery.DeliveryContext;
import info.magnolia.rest.delivery.jcr.v2.ConfiguredFiltering;
import info.magnolia.rest.reference.ReferenceResolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

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
 * <li>JCR references of the served nodes, at any depth: resolved to their
 * locale variant when applicable;</li>
 * <li>requests with a locale from the {@code ?lang} parameter or the browser's
 * {@code Accept-Language} header;
 * the variant follows Magnolia's i18n fallback ({@code de_CH} →
 * {@code de}).</li>
 * </ul>
 *
 * <p>
 * Out of scope, delivery response as out-of-the-box for:
 * <ul>
 * <li>query requests for searches and filtered lists;</li>
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
    private final JcrReferenceResolverDetector resolverDetector;
    // Delivery-only request context: to be looked up on each request
    private final Supplier<DeliveryContext<?>> deliveryContext;
    private final boolean isAuthorInstance;

    // Created by RESTEasy (META-INF/services), so its dependencies come from the
    // component provider
    public LocaleVariantResponseFilter() {
        this(Components.getComponent(LocaleVariantStructure.class),
                Components.getComponent(JcrReferenceResolverDetector.class),
                () -> Components.getComponent(DeliveryContext.class),
                Components.getComponent(ServerConfiguration.class).isAdmin());
    }

    LocaleVariantResponseFilter(LocaleVariantStructure variantStructure, JcrReferenceResolverDetector resolverDetector,
            Supplier<DeliveryContext<?>> deliveryContext, boolean isAuthorInstance) {
        this.variantStructure = variantStructure;
        this.resolverDetector = resolverDetector;
        this.deliveryContext = deliveryContext;
        this.isAuthorInstance = isAuthorInstance;
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        Object entity = response.getEntity();
        if (entity == null || !hasExplicitLanguage(request) || isVersionRequest(request)
                || isPageEditorRequest(request)) {
            return;
        }
        // References only after the entity: if the entity fails, the whole response stays out of the box
        if (entity instanceof Node node) {
            if (localizeNode(node, response)) {
                localizeReferences();
            }
        } else if (entity instanceof NodeIterator nodes) {
            if (localizeNodes(nodes, response)) {
                localizeReferences();
            }
        }
        // We don't handle NodesResult entities (search results) by design
    }

    // The NodeWriter takes the reference resolvers from the DeliveryContext while
    // writing the JSON, so wrapping them here covers every reference it expands
    // On error: WARN and leave the endpoint's resolvers unchanged, as out of the
    // box
    @SuppressWarnings("rawtypes")
    private void localizeReferences() {
        try {
            DeliveryContext<?> context = deliveryContext.get();
            Map<Predicate, ReferenceResolver<?, ?>> resolvers = context.getResolvers();
            if (resolvers == null || resolvers.isEmpty()) {
                return;
            }
            // New map, same order: never change the endpoint's own map
            Map<Predicate, ReferenceResolver<?, ?>> localized = new LinkedHashMap<>();
            resolvers.forEach((predicate, resolver) -> {
                ReferenceResolver<?, ?> served = resolverDetector.isJcrReferenceResolver(resolver)
                        ? new LocaleVariantAwareReferenceResolverWrapper(resolver, variantStructure)
                        : resolver;
                localized.put(predicate, served);
            });
            context.setResolvers(localized);
        } catch (RuntimeException e) {
            log.warn("Can't localize the references of this request, serving them as the endpoint resolves them", e);
        }
    }

    // On error: WARN, leave the response unchanged, as out of the box, and return false
    private boolean localizeNode(Node node, ContainerResponseContext response) {
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
            return true;
        } catch (RepositoryException | RuntimeException e) {
            log.warn("Can't resolve the locale variant of {}, serving the original", node, e);
            return false;
        }
    }

    // Children lists (@nodes). All or nothing: if one node fails,
    // WARN, leave the whole list unchanged, as out of the box, and return false
    private boolean localizeNodes(NodeIterator nodes, ContainerResponseContext response) {
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
            return true;
        } catch (RepositoryException | RuntimeException e) {
            log.warn("Can't resolve the locale variants of a node list, serving the originals", e);
            response.setEntity(new NodeIteratorAdapter(originals));
            return false;
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
