package com.giufaz.magnolia.localevariants.delivery;

import info.magnolia.rest.delivery.jcr.v2.JcrDeliveryEndpoint;
import info.magnolia.rest.reference.ReferenceResolver;
import info.magnolia.rest.reference.jcr.JcrReferenceResolver;

import java.util.Arrays;
import java.util.Optional;

/**
 * Tells which Delivery reference resolvers are JCR resolvers (UUID as input,
 * node as output). Those are the ones decorated with the locale variants
 * functionality.
 *
 * <p>
 * Relies on a Magnolia internal: at runtime, every
 * {@link JcrReferenceResolver} (or subclass) is unintuitively wrapped in the
 * private {@code JcrDeliveryEndpoint$JcrReferenceResolverWrapper}, which hides
 * its real class. {@link #checkMagnoliaInternals()} verifies it at startup
 * ({@code NodeLocaleVariantsModule}) and in a unit test.
 *
 * <p>
 * <b>If the startup check fails</b> (ERROR "Delivery Endpoint node references
 * won't be served as locale variants"),
 * <b>update {@code mgnl-node-locale-variants}</b> to a version that supports
 * your Magnolia version.
 *
 * <p>
 * If that's not possible, write an updated implementation of this class and
 * register it in your module descriptor as a {@code <component>} whose
 * {@code <type>} is this class. Your module must depend on
 * {@code node-locale-variants}.
 */
public class JcrReferenceResolverDetector {

    public static final String JCR_RESOLVER_WRAPPER = JcrDeliveryEndpoint.class.getName()
            + "$JcrReferenceResolverWrapper";

    // Fast class-name check: called for every resolver of every localized request
    public boolean isJcrReferenceResolver(ReferenceResolver<?, ?> resolver) {
        return resolver instanceof JcrReferenceResolver || JCR_RESOLVER_WRAPPER.equals(resolver.getClass().getName());
    }

    // Checks that Magnolia still wraps JCR resolvers as isJcrReferenceResolver
    // expects; empty if fine
    public Optional<String> checkMagnoliaInternals() {
        try {
            Class<?> wrapper = Class.forName(JCR_RESOLVER_WRAPPER, false, getClass().getClassLoader());
            if (!ReferenceResolver.class.isAssignableFrom(wrapper)) {
                return Optional.of(JCR_RESOLVER_WRAPPER + " is no longer a ReferenceResolver");
            }
            boolean wrapsJcrResolver = Arrays.stream(wrapper.getDeclaredFields())
                    .anyMatch(field -> JcrReferenceResolver.class.isAssignableFrom(field.getType()));
            return wrapsJcrResolver ? Optional.empty()
                    : Optional.of(JCR_RESOLVER_WRAPPER + " no longer holds a JcrReferenceResolver");
        } catch (ClassNotFoundException | LinkageError e) {
            return Optional.of(JCR_RESOLVER_WRAPPER + " not found");
        }
    }
}
