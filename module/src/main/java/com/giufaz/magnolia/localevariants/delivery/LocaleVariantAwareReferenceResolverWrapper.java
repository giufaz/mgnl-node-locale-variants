package com.giufaz.magnolia.localevariants.delivery;

import info.magnolia.jcr.util.NodeUtil;
import info.magnolia.rest.reference.ReferenceResolver;

import java.util.Optional;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.giufaz.magnolia.localevariants.LocaleVariantStructure;

// Wraps a JCR reference resolver (or Magnolia's private wrapper around it) so that a referenced original
// is served as its locale variant. No other reference resolver reaches this class (see
// JcrReferenceResolverDetector); links (expand: false) and nodes without a variant are left as they are.
final class LocaleVariantAwareReferenceResolverWrapper implements ReferenceResolver<String, Object> {

    private static final Logger log = LoggerFactory.getLogger(LocaleVariantAwareReferenceResolverWrapper.class);

    // Input: the referenced UUID; output: a node, or a link with expand: false
    private final ReferenceResolver<String, Object> delegate;
    private final LocaleVariantStructure variantStructure;

    @SuppressWarnings("unchecked") // JCR resolvers and Magnolia's wrapper are ReferenceResolver<String, Object>
    LocaleVariantAwareReferenceResolverWrapper(ReferenceResolver<?, ?> jcrResolver, LocaleVariantStructure variantStructure) {
        this.delegate = (ReferenceResolver<String, Object>) jcrResolver;
        this.variantStructure = variantStructure;
    }

    @Override
    public Optional<Object> resolve(String uuid) {
        return localize(delegate.resolve(uuid), null);
    }

    @Override
    public Optional<Object> resolve(String uuid, String propertyName) {
        return localize(delegate.resolve(uuid, propertyName), propertyName);
    }

    // On error: WARN and keep the original reference, as out of the box
    private Optional<Object> localize(Optional<Object> resolved, String propertyName) {
        if (resolved.isEmpty() || !(resolved.get() instanceof Node node)) {
            return resolved;
        }
        try {
            // Unwrapped: the resolver's depth filter could hide localeVariants/variant_xx
            Node raw = NodeUtil.unwrap(node);
            Optional<Node> variant = variantStructure.findCurrentLocaleVariant(raw);
            if (variant.isEmpty()) {
                return resolved;
            }
            // Second resolution, on the variant id: costs one more resolve, but the variant gets exactly
            // the endpoint's settings (system context, depth, properties, i18n, links)
            String variantId = variant.get().getIdentifier();
            Optional<Object> localized = propertyName == null ? delegate.resolve(variantId)
                    : delegate.resolve(variantId, propertyName);
            return localized.isPresent() ? localized : resolved;
        } catch (RepositoryException | RuntimeException e) {
            log.warn("Can't resolve the locale variant of the reference {}, serving the original", node, e);
            return resolved;
        }
    }
}
