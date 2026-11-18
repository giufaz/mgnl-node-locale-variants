package com.giufaz.magnolia.localevariants.delivery;

import info.magnolia.config.registry.Registry;
import info.magnolia.jcr.RuntimeRepositoryException;
import info.magnolia.jcr.util.NodeTypes;
import info.magnolia.objectfactory.Components;
import info.magnolia.pages.rest.v2.JcrPagesDeliveryEndpointDefinition;
import info.magnolia.rendering.spa.renderer.AreaNodeGenerator;
import info.magnolia.rest.delivery.DeliveryContext;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Area autogeneration on page variants, as JcrPagesDeliveryEndpoint does.
// Decorated only if Pages REST and SPA rendering exist at runtime: their classes are loaded only when present.
final class PagesSupport {

    private static final Logger log = LoggerFactory.getLogger(PagesSupport.class);

    static final boolean AVAILABLE = isPresent("info.magnolia.pages.rest.v2.JcrPagesDeliveryEndpointDefinition")
            && isPresent("info.magnolia.rendering.spa.renderer.AreaNodeGenerator");

    private PagesSupport() {
    }

    // Returns the node; if it's a page, first creates its missing areas
    static Node withAreaNodes(Node node) {
        try {
            if (AVAILABLE && node.isNodeType(NodeTypes.Page.NAME)) {
                createAreaNodes(node);
            }
        } catch (RepositoryException e) {
            throw new RuntimeRepositoryException(e);
        }
        return node;
    }

    private static void createAreaNodes(Node page) {
        DeliveryContext<?> context = Components.getComponent(DeliveryContext.class);
        if (context.getEndpointDefinition() instanceof JcrPagesDeliveryEndpointDefinition) {
            // Same handling as JcrPagesDeliveryEndpoint: log and serve the page without the missing areas
            try {
                Components.getComponent(AreaNodeGenerator.class).createAreaNodes(page);
            } catch (Registry.NoSuchDefinitionException | Registry.InvalidDefinitionException e) {
                log.error("Can't generate the missing areas of {}, serving the page without them", page, e);
            }
        }
    }

    private static boolean isPresent(String className) {
        try {
            Class.forName(className, false, PagesSupport.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
