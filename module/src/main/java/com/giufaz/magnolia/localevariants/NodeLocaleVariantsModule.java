package com.giufaz.magnolia.localevariants;

import static com.giufaz.magnolia.localevariants.delivery.JcrReferenceResolverDetector.JCR_RESOLVER_WRAPPER;

import info.magnolia.module.ModuleLifecycle;
import info.magnolia.module.ModuleLifecycleContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

import com.giufaz.magnolia.localevariants.delivery.JcrReferenceResolverDetector;

import jakarta.inject.Inject;

/**
 * Module class of {@code node-locale-variants}: startup checks.
 */
public class NodeLocaleVariantsModule implements ModuleLifecycle {

    private static final Logger log = LoggerFactory.getLogger(NodeLocaleVariantsModule.class);

    private final JcrReferenceResolverDetector detector;

    @Inject
    public NodeLocaleVariantsModule(JcrReferenceResolverDetector detector) {
        this.detector = detector;
    }

    // Also called after every config change: Magnolia restarts the module
    @Override
    public void start(ModuleLifecycleContext moduleLifecycleContext) {
        // Early warning if a Magnolia update changed the internals the reference
        // localization relies on
        detector.checkMagnoliaInternals().ifPresent(problem -> log.error(referencesDisabledMessage(problem)));
    }

    static String referencesDisabledMessage(String problem) {
        String component = JcrReferenceResolverDetector.class.getSimpleName();
        var msgArgs = new Object[] { JCR_RESOLVER_WRAPPER, component, problem };
        return MessageFormatter.arrayFormat(REFERENCES_DISABLED_MSG, msgArgs).getMessage();
    }

    private static final String REFERENCES_DISABLED_MSG = """
            [node-locale-variants] Delivery Endpoint node references won't be served as locale variants:
              Magnolia changed or removed its internal class ({}).
              Fix: update mgnl-node-locale-variants module if there is a new version,
              or override the component {} in your module descriptor with a custom version.
              Check the javadoc of the component to see how to override its methods.
              More info about the issue: {}""";
}
