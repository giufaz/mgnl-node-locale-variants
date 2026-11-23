package com.giufaz.magnolia.localevariants.demo;

import info.magnolia.cms.i18n.I18nContentSupport;
import info.magnolia.context.Context;
import info.magnolia.context.WebContext;
import info.magnolia.pages.spa.routing.DefaultSpaRouter;
import info.magnolia.rendering.spa.renderer.SpaRenderableDefinition;

import java.net.URL;
import java.util.Optional;

import javax.jcr.Node;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Demo only: opens the demo's pages in the demo's own front end
 * ({@code webresources/frontend/index.html}), served by the Magnolia instance
 * of the current request. The page template then needs no {@code baseUrl},
 * whatever host and port Magnolia runs on.
 */
public class DemoFrontEndRouter extends DefaultSpaRouter {

    static final String FRONT_END = "/.resources/node-locale-variants-demo/webresources/frontend/index.html";

    private final Provider<WebContext> webContext;

    @Inject
    public DemoFrontEndRouter(SpaRenderableDefinition definition, I18nContentSupport i18nContentSupport,
            Provider<WebContext> webContext) {
        super(definition, i18nContentSupport);
        this.webContext = webContext;
    }

    @Override
    public Optional<URL> getBaseUrl(Node pageNode, Context context) {
        HttpServletRequest request = webContext.get().getRequest();
        String origin = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
        return createURL(origin + request.getContextPath() + FRONT_END);
    }
}
